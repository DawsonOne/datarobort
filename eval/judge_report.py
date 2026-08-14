#!/usr/bin/env python3
"""对已落盘的评测报告跑 LLM judge 复评（无需重跑评测）。

用法:
  python judge_report.py [--report docs/eval/eval-report-v4.md]

从 eval-report 的结果表格解析出每条用例的 {问题/意图/行数/图表/失败/说明(SQL)}
结构，调 judge.judge_all 追加 LLM judge 复评表。
"""

import argparse
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, Path(__file__).parent.as_posix())


def parse_report(report_path):
    """Parse the per-case table rows into judge-friendly dicts,
    joining expected intent / SQL patterns from docs/eval/cases.json."""
    cases = {c["id"]: c for c in
             json.loads(Path(__file__).parent.parent.joinpath("docs/eval/cases.json").read_text(encoding="utf-8"))}
    text = Path(report_path).read_text(encoding="utf-8")
    results = []
    # table rows: | id | 类别 | 问题 | 结果 | 意图 | 行数 | 图表 | 耗时 | SQL（完整） | 说明 |
    # SQL 列可能含换行（多行格式 SQL）把行拆成续行 → 按"数字开头"行分组为块再拼 SQL
    blocks, cur = [], []
    for line in text.splitlines():
        cells = [c.strip() for c in line.strip().strip("|").split("|")] if line.startswith("| ") else None
        if cells and cells[0].isdigit():
            if cur:
                blocks.append(cur)
            cur = [cells]
        elif cur:
            cur.append(cells if cells else [line.strip()])
    if cur:
        blocks.append(cur)

    for block in blocks:
        head = block[0]
        if len(head) < 9:
            continue
        cid, category, question, verdict, intent, rows, chart, ms = head[:8]
        sql = head[8] if len(head) >= 10 else ""
        note = head[9] if len(head) >= 10 else ""
        for extra in block[1:]:
            sql += " " + " ".join(extra)
        if sql in ("-", ""):
            sql = ""
            m = re.search(r"sql[:=]?\s*(.+?)(?:; rows=|$)", note)
            if m:
                sql = m.group(1)[:400]
        case = cases.get(int(cid), {})
        results.append({
            "id": int(cid), "category": category, "question": question,
            "pass": verdict == "✅",
            "intent": intent if intent != "-" else None,
            "expectedIntent": case.get("expectedIntent"),
            "expectedSqlPattern": case.get("expectedSqlPattern", []),
            "sql": sql or "(无 SQL)",
            "rows": int(rows) if rows != "-" else None,
            "chart": chart == "✓",
            "failed": False,
        })
    return results


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--report", default="docs/eval/eval-report-v4.md")
    args = ap.parse_args()
    results = parse_report(args.report)
    print(f"parsed {len(results)} cases from {args.report}")
    if not results:
        sys.exit(1)
    from judge import judge_all
    judge_all(results, args.report)


if __name__ == "__main__":
    main()
