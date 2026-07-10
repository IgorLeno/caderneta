#!/usr/bin/env python3
"""Generate autonomous Android audit reports from collected artifacts."""

from __future__ import annotations

import argparse
import html
import json
import os
import pathlib
import xml.etree.ElementTree as ET
from datetime import datetime, timezone


def rel(path: pathlib.Path, root: pathlib.Path) -> str:
    return path.relative_to(root).as_posix()


def collect_junit(root: pathlib.Path) -> dict:
    tests = failures = errors = skipped = 0
    cases = []
    for xml_path in sorted((root / "test-results").rglob("*.xml")):
        try:
            tree = ET.parse(xml_path)
        except ET.ParseError:
            continue
        for suite in tree.iter("testsuite"):
            tests += int(suite.attrib.get("tests", "0"))
            failures += int(suite.attrib.get("failures", "0"))
            errors += int(suite.attrib.get("errors", "0"))
            skipped += int(suite.attrib.get("skipped", "0"))
        for case in tree.iter("testcase"):
            status = "passed"
            if case.find("failure") is not None:
                status = "failed"
            elif case.find("error") is not None:
                status = "error"
            elif case.find("skipped") is not None:
                status = "skipped"
            cases.append(
                {
                    "class": case.attrib.get("classname", ""),
                    "name": case.attrib.get("name", ""),
                    "time": case.attrib.get("time", ""),
                    "status": status,
                    "file": rel(xml_path, root),
                }
            )
    return {"tests": tests, "failures": failures, "errors": errors, "skipped": skipped, "cases": cases}


def collect_files(root: pathlib.Path, directory: str, patterns: tuple[str, ...]) -> list[str]:
    base = root / directory
    if not base.exists():
        return []
    files: list[pathlib.Path] = []
    for pattern in patterns:
        files.extend(base.rglob(pattern))
    return [rel(path, root) for path in sorted(set(files))]


def read_json_files(root: pathlib.Path, directory: str) -> list[dict]:
    result = []
    for path in sorted((root / directory).rglob("*.json")) if (root / directory).exists() else []:
        try:
            result.append({"path": rel(path, root), "content": json.loads(path.read_text(encoding="utf-8"))})
        except json.JSONDecodeError:
            result.append({"path": rel(path, root), "content": {"parseError": True}})
    return result


def write_markdown(root: pathlib.Path, summary: dict) -> None:
    lines = [
        "# Caderneta Autonomous Android Audit",
        "",
        f"Generated: {summary['generatedAt']}",
        f"Mode: {summary['mode']}",
        f"Suite: {summary['suite']}",
        f"Gradle task: `{summary['gradleTask']}`",
        f"Exit code: `{summary['testExitCode']}`",
        "",
        "## Build",
        "",
        f"APK: `{summary['apk']['path']}`",
        f"APK SHA-256: `{summary['apk']['sha256']}`",
        f"APK size: `{summary['apk']['sizeBytes']}` bytes",
        "",
        "## Test Summary",
        "",
        f"Tests: {summary['junit']['tests']}",
        f"Failures: {summary['junit']['failures']}",
        f"Errors: {summary['junit']['errors']}",
        f"Skipped: {summary['junit']['skipped']}",
        "",
        "## Screenshots",
        "",
    ]
    if summary["screenshots"]:
        lines.extend(f"- [{path}]({path})" for path in summary["screenshots"])
    else:
        lines.append("- No screenshots collected.")
    lines.extend(["", "## Failure Evidence", ""])
    failure_files = summary["failures"] + summary["uiHierarchy"]
    if failure_files:
        lines.extend(f"- [{path}]({path})" for path in failure_files)
    else:
        lines.append("- No failure artifacts collected.")
    lines.extend(["", "## JUnit Cases", ""])
    if summary["junit"]["cases"]:
        lines.extend(
            f"- `{case['status']}` {case['class']}#{case['name']} ({case['time']}s)"
            for case in summary["junit"]["cases"]
        )
    else:
        lines.append("- No JUnit XML parsed.")
    (root / "report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_html(root: pathlib.Path, summary: dict) -> None:
    screenshot_items = "\n".join(
        f'<figure><img src="{html.escape(path)}" loading="lazy"><figcaption>{html.escape(path)}</figcaption></figure>'
        for path in summary["screenshots"]
    )
    case_rows = "\n".join(
        "<tr>"
        f"<td>{html.escape(case['status'])}</td>"
        f"<td>{html.escape(case['class'])}</td>"
        f"<td>{html.escape(case['name'])}</td>"
        f"<td>{html.escape(case['time'])}</td>"
        "</tr>"
        for case in summary["junit"]["cases"]
    )
    document = f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>Caderneta Android Audit</title>
<style>
body {{ font-family: sans-serif; margin: 32px; color: #17202a; }}
code {{ background: #f1f3f5; padding: 2px 4px; }}
.grid {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px; }}
figure {{ margin: 0; border: 1px solid #ddd; padding: 8px; }}
img {{ max-width: 100%; height: auto; }}
table {{ border-collapse: collapse; width: 100%; }}
th, td {{ border: 1px solid #ddd; padding: 6px; text-align: left; }}
</style>
</head>
<body>
<h1>Caderneta Autonomous Android Audit</h1>
<p>Generated: {html.escape(summary['generatedAt'])}</p>
<p>Mode: <code>{html.escape(summary['mode'])}</code> Suite: <code>{html.escape(summary['suite'])}</code></p>
<p>Gradle task: <code>{html.escape(summary['gradleTask'])}</code> Exit code: <code>{summary['testExitCode']}</code></p>
<h2>Build</h2>
<p>APK SHA-256: <code>{html.escape(summary['apk']['sha256'])}</code></p>
<h2>Screenshots</h2>
<div class="grid">{screenshot_items}</div>
<h2>JUnit Cases</h2>
<table><tr><th>Status</th><th>Class</th><th>Name</th><th>Time</th></tr>{case_rows}</table>
</body>
</html>
"""
    (root / "report.html").write_text(document, encoding="utf-8")


def write_templates(root: pathlib.Path, summary: dict) -> None:
    visual = ["# Visual Review", "", "## Screenshot Index", ""]
    for path in summary["screenshots"]:
        visual.extend(
            [
                f"### {path}",
                f"![{path}]({path})",
                "- Severity: ",
                "- Screen: ",
                "- Finding: ",
                "- Impact: ",
                "- Recommendation: ",
                "",
            ]
        )
    (root / "visual-review.md").write_text("\n".join(visual), encoding="utf-8")

    action = ["# Action Plan", "", "## P0", "", "- ", "", "## P1", "", "- ", "", "## P2", "", "- ", ""]
    (root / "action-plan.md").write_text("\n".join(action), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", required=True)
    parser.add_argument("--mode", required=True)
    parser.add_argument("--suite", required=True)
    parser.add_argument("--gradle-task", required=True)
    parser.add_argument("--test-exit-code", required=True, type=int)
    parser.add_argument("--apk-path", required=True)
    parser.add_argument("--apk-sha256", required=True)
    parser.add_argument("--apk-size", required=True, type=int)
    args = parser.parse_args()

    root = pathlib.Path(args.root).resolve()
    summary = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "mode": args.mode,
        "suite": args.suite,
        "gradleTask": args.gradle_task,
        "testExitCode": args.test_exit_code,
        "apk": {"path": args.apk_path, "sha256": args.apk_sha256, "sizeBytes": args.apk_size},
        "junit": collect_junit(root),
        "screenshots": collect_files(root, "screenshots", ("*.png",)),
        "failures": collect_files(root, "failures", ("*.txt",)),
        "uiHierarchy": collect_files(root, "ui-hierarchy", ("*.xml",)),
        "logcat": collect_files(root, "logcat", ("*.txt",)),
        "databaseSummary": read_json_files(root, "database-summary"),
        "metadata": read_json_files(root, "metadata"),
    }
    (root / "execution-summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    (root / "app-build.json").write_text(json.dumps(summary["apk"], indent=2), encoding="utf-8")
    write_markdown(root, summary)
    write_html(root, summary)
    write_templates(root, summary)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
