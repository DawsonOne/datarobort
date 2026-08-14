#!/usr/bin/env python3
"""沙箱并发压测：并发 N 个「分析」类问题触发 Python 图表，验证：

  1. 沙箱并发信号量（默认 2）生效 —— 超过时返回"沙箱繁忙"且不排队过久
  2. 无容器泄漏 —— 压测前后 docker ps -a 中 datarobort-sandbox-* 容器数不变
  3. 正常图表生成成功率

用法:
  python sandbox-concurrency.py [--base http://localhost:8080]
                                [--levels 2,4,6]
"""

import argparse
import json
import statistics
import subprocess
import sys
import time
import urllib.request
from collections import Counter
from concurrent.futures import ThreadPoolExecutor

BASE = "http://localhost:8080"
QUESTION = "分析每月销售总额趋势并画图"


def count_sandbox_containers():
    try:
        out = subprocess.run(["docker", "ps", "-a", "--filter", "name=datarobort-sandbox",
                              "--format", "{{.Names}}"], capture_output=True, text=True, timeout=30)
        return len([n for n in out.stdout.splitlines() if n.strip()])
    except Exception:
        return -1


def one_chat(_):
    body = json.dumps({"question": QUESTION, "agentId": 1}, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(f"{BASE}/api/chat", data=body,
                                 headers={"Content-Type": "application/json; charset=utf-8"})
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=240) as r:
            resp = json.loads(r.read().decode("utf-8"))
        ms = (time.time() - t0) * 1000
        has_chart = bool(resp.get("chartImages")) or bool(resp.get("chartOption"))
        err = resp.get("errorMessage") or ""
        return {"ok": has_chart, "ms": ms, "err": err[:80], "hasChart": has_chart,
                "busy": "沙箱繁忙" in err}
    except Exception as e:
        return {"ok": False, "ms": (time.time() - t0) * 1000, "err": str(e)[:80],
                "hasChart": False, "busy": False}


def run_level(level):
    before = count_sandbox_containers()
    with ThreadPoolExecutor(max_workers=level) as pool:
        results = list(pool.map(one_chat, range(level)))
    time.sleep(2)
    after = count_sandbox_containers()

    ok = sum(1 for r in results if r["ok"])
    busy = sum(1 for r in results if r["busy"])
    times = [r["ms"] for r in results]
    errs = Counter(r["err"] or "?" for r in results if not r["ok"])
    p50 = statistics.median(times) if times else 0
    p95 = sorted(times)[int(len(times) * 0.95)] if times else 0

    print(f"--- sandbox concurrency={level} ---")
    print(f"  requests={level} chart-ok={ok} ({ok / level * 100:.1f}%)")
    print(f"  沙箱繁忙(信号量): {busy}")
    print(f"  latency p50={p50:.0f}ms p95={p95:.0f}ms max={max(times):.0f}ms")
    print(f"  容器泄漏检查: before={before} after={after} {'✅' if before == after else '❌ LEAK'}")
    for e, c in errs.items():
        print(f"  error[{c}]: {e}")
    return {"level": level, "ok": ok, "total": level, "busy": busy,
            "p50": p50, "p95": p95, "containers_before": before, "containers_after": after}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://localhost:8080")
    ap.add_argument("--levels", default="2,4,6")
    args = ap.parse_args()
    global BASE
    BASE = args.base

    levels = [int(x) for x in args.levels.split(",") if x.strip()]
    results = [run_level(lv) for lv in levels]
    print("\n=== 汇总 ===")
    for r in results:
        print(f"  {r['level']} 并发: 图表成功 {r['ok']}/{r['total']} ({r['ok'] / r['total'] * 100:.1f}%)"
              f" | 繁忙 {r['busy']} | 容器 {r['containers_before']}→{r['containers_after']}")


if __name__ == "__main__":
    main()
