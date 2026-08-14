#!/usr/bin/env python3
"""SSE 并发压测：并发 N 个客户端同时调用 /api/chat/stream。

用法:
  python sse-concurrency.py [--base http://localhost:8080]
                            [--concurrency 5,10,20]
                            [--question "统计orders表总订单数和总销售额"]

对每个并发档位：
  - 同时发起 N 个 SSE 流（urllib 流式读取 event: 行）
  - 等待 complete 事件 / 超时（180s）
  - 统计：成功率、错误分布、耗时 P50/P95、事件序列完整性
验证目标：20 并发成功率 ≥ 95%；超限时返回"服务器繁忙"错误事件且不拖垮服务。
"""

import argparse
import json
import statistics
import sys
import time
import urllib.request
from collections import Counter

BASE = "http://localhost:8080"


def sse_request(question, agent_id=1, timeout=180):
    """Returns (ok, elapsed_ms, error_kind, events)."""
    body = json.dumps({"question": question, "agentId": agent_id}, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(f"{BASE}/api/chat/stream", data=body,
                                 headers={"Content-Type": "application/json; charset=utf-8",
                                          "Accept": "text/event-stream"})
    events = []
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            buf = b""
            while True:
                chunk = r.read(4096)
                if not chunk:
                    break
                buf += chunk
                while b"\n\n" in buf:
                    raw, buf = buf.split(b"\n\n", 1)
                    text = raw.decode("utf-8", "replace")
                    for line in text.splitlines():
                        if line.startswith("event:"):
                            events.append(line[6:].strip())
        elapsed = (time.time() - t0) * 1000
        if "complete" in events:
            return True, elapsed, None, events
        if any("error" == e for e in events):
            return False, elapsed, "error-event", events
        return False, elapsed, "no-complete", events
    except Exception as e:
        elapsed = (time.time() - t0) * 1000
        return False, elapsed, type(e).__name__, events


def run_level(level, question, timeout):
    results = []
    from concurrent.futures import ThreadPoolExecutor

    def one(_):
        return sse_request(question, timeout=timeout)

    with ThreadPoolExecutor(max_workers=level) as pool:
        results = list(pool.map(one, range(level)))

    ok = sum(1 for r in results if r[0])
    times = [r[1] for r in results]
    errs = Counter(r[2] for r in results if not r[0])
    p50 = statistics.median(times) if times else 0
    p95 = sorted(times)[int(len(times) * 0.95)] if times else 0
    busy = sum(1 for r in results if not r[0] and r[2] == "error-event")

    # 事件序列检查：首个事件应为 connected，末尾应为 complete/error
    seq_ok = 0
    for r in results:
        if r[0] and r[3] and r[3][0] == "connected" and r[3][-1] == "complete":
            seq_ok += 1
        elif not r[0] and r[3] and r[3][-1] == "error":
            seq_ok += 1

    print(f"--- concurrency={level} ---")
    print(f"  requests={level} success={ok} ({ok / level * 100:.1f}%)")
    print(f"  errors: {dict(errs) or 'none'}")
    print(f"  busy-error(服务器繁忙): {busy}")
    print(f"  latency p50={p50:.0f}ms p95={p95:.0f}ms max={max(times):.0f}ms" if times else "  no times")
    print(f"  event-sequence-ok={seq_ok}/{level}")
    return {"level": level, "ok": ok, "total": level, "errs": dict(errs),
            "busy": busy, "p50": p50, "p95": p95, "max": max(times) if times else 0,
            "seq_ok": seq_ok}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default=BASE)
    ap.add_argument("--concurrency", default="5,10,20")
    ap.add_argument("--question", default="统计orders表总订单数和总销售额，并按类目分析")
    ap.add_argument("--timeout", type=int, default=180)
    args = ap.parse_args()
    global BASE
    BASE = args.base

    levels = [int(x) for x in args.concurrency.split(",") if x.strip()]
    all_results = [run_level(lv, args.question, args.timeout) for lv in levels]

    print("\n=== 汇总 ===")
    for r in all_results:
        print(f"  {r['level']} 并发: 成功 {r['ok']}/{r['total']} ({r['ok'] / r['total'] * 100:.1f}%)"
              f" busy={r['busy']} p50={r['p50']:.0f}ms p95={r['p95']:.0f}ms seq_ok={r['seq_ok']}/{r['total']}")
    print("目标: 20 并发成功率 ≥ 95%，超限快速失败不拖垮服务")


if __name__ == "__main__":
    main()
