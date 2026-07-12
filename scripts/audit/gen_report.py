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


LOGCAT_CRASH_MARKERS = ("FATAL EXCEPTION", "E AndroidRuntime", "ANR in ", "signal 11 (SIGSEGV)")


def finding(
    severity: str,
    category: str,
    screen: str,
    component: str,
    description: str,
    impact: str,
    recommendation: str,
    confidence: str,
    evidence: str = "",
    origin: str = "product",
    cause: str = "confirmed",
) -> dict:
    return {
        "severity": severity,
        "category": category,
        "origin": origin,
        "cause": cause,
        "screen": screen,
        "component": component,
        "description": description,
        "impact": impact,
        "recommendation": recommendation,
        "confidence": confidence,
        "nature": "determinística",
        "evidence": evidence,
    }


def scan_logcat(root: pathlib.Path, logcat_paths: list[str]) -> list[tuple[str, int]]:
    hits = []
    for rel_path in logcat_paths:
        path = root / rel_path
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        count = sum(text.count(marker) for marker in LOGCAT_CRASH_MARKERS)
        if count:
            hits.append((rel_path, count))
    return hits


def scenario_key(value: str) -> str:
    return "".join(char for char in value.lower() if char.isalnum())


def database_summary_by_scenario(summary: dict) -> dict[str, dict]:
    indexed: dict[str, dict] = {}
    for entry in summary["databaseSummary"]:
        content = entry.get("content", {})
        scenario = str(content.get("scenario") or pathlib.Path(entry["path"]).stem.replace("_db", ""))
        indexed[scenario_key(scenario)] = entry
    return indexed


def database_summary_is_clean(entry: dict | None) -> bool:
    if not entry:
        return False
    content = entry.get("content", {})
    if content.get("parseError"):
        return False
    if content.get("foreignKeyViolations"):
        return False

    cache = content.get("saldoCache") or []
    saldos = [item.get("saldoCentavos") for item in cache if item.get("saldoCentavos") is not None]
    if any(saldo < 0 for saldo in saldos):
        return False

    historico = content.get("saldoHistoricoCentavos")
    if historico is not None and saldos:
        if len(saldos) == 1 and historico != saldos[0]:
            return False
        if len(saldos) > 1 and historico not in saldos:
            return False
    return True


def find_database_summary_for_case(case: dict, summary: dict) -> dict | None:
    indexed = database_summary_by_scenario(summary)
    haystack = scenario_key(f"{case['class']} {case['name']} {case['file']}")
    for key, entry in indexed.items():
        if key and key in haystack:
            return entry
    for failure_path in summary["failures"]:
        if scenario_key(pathlib.Path(failure_path).stem.replace("_failure", "")) in haystack:
            try:
                failure_text = (pathlib.Path(summary["root"]) / failure_path).read_text(
                    encoding="utf-8",
                    errors="replace",
                )
            except OSError:
                continue
            for key, entry in indexed.items():
                if key and key in scenario_key(failure_text):
                    return entry
    return None


def classify_test_failure(case: dict, summary: dict) -> dict:
    db_entry = find_database_summary_for_case(case, summary)
    if database_summary_is_clean(db_entry):
        return {
            "severity": "P1",
            "category": "test-flakiness",
            "origin": "test",
            "cause": "hypothesis",
            "impact": "Suíte de auditoria instável apesar de evidência determinística de DB íntegro.",
            "recommendation": "Isolar a sincronização do teste e manter DB/ledger como prova funcional.",
            "confidence": "média",
            "evidence": db_entry["path"],
        }
    return {
        "severity": "P0",
        "category": "product-functional",
        "origin": "product",
        "cause": "hypothesis",
        "impact": "Fluxo verificado pode estar quebrado ou sem evidência determinística suficiente para descartar defeito funcional.",
        "recommendation": "Inspecionar failure, screenshot, hierarquia, logcat e database-summary do cenário.",
        "confidence": "média",
        "evidence": case["file"],
    }


def check_evidence_integrity(summary: dict) -> list[dict]:
    findings: list[dict] = []
    invalid_screenshots = [path for path in summary["screenshots"] if "_INVALID" in pathlib.Path(path).stem]
    for path in invalid_screenshots:
        findings.append(
            finding(
                "P1", "evidence-invalid", "-", path,
                "Screenshot marcada como inválida porque o foreground package não era o app auditado.",
                "Evidência visual pode representar launcher/sistema em vez da tela sob teste.",
                "Usar a captura pré-teardown do app e revisar metadata de foreground.",
                "alta", path, origin="evidence", cause="confirmed",
            )
        )

    for entry in summary["metadata"]:
        content = entry.get("content", {})
        expected = content.get("expectedPackage")
        foreground = content.get("foregroundPackage")
        if expected and foreground and expected != foreground:
            findings.append(
                finding(
                    "P1", "evidence-invalid", "-", entry["path"],
                    f"Metadata de falha registrou foregroundPackage={foreground}, esperado {expected}.",
                    "A evidência do instante da falha pode não representar o estado do app.",
                    "Revisar screenshot/hierarquia correlacionados e corrigir timing de captura se necessário.",
                    "alta", entry["path"], origin="evidence", cause="confirmed",
                )
            )
    return findings


def run_deterministic_checks(root: pathlib.Path, summary: dict) -> list[dict]:
    findings: list[dict] = []
    junit = summary["junit"]

    if summary["testExitCode"] != 0:
        findings.append(
            finding(
                "P1", "test-infrastructure", "-", "instrumentation",
                f"Instrumentation terminou com exit code {summary['testExitCode']}.",
                "Suíte de auditoria não passou integralmente.",
                "Investigar falhas nos artefatos de teste e no logcat.",
                "alta", origin="infra", cause="confirmed",
            )
        )

    for case in junit["cases"]:
        if case["status"] in ("failed", "error"):
            classification = classify_test_failure(case, summary)
            findings.append(
                finding(
                    classification["severity"], classification["category"], "-", f"{case['class']}#{case['name']}",
                    f"Teste {case['status']}: {case['class']}#{case['name']}.",
                    classification["impact"],
                    classification["recommendation"],
                    classification["confidence"], classification["evidence"],
                    origin=classification["origin"], cause=classification["cause"],
                )
            )

    if not summary["screenshots"]:
        findings.append(
            finding(
                "P1", "evidence-invalid", "-", "screenshots",
                "Nenhum screenshot coletado na execução.",
                "Auditoria visual fica sem evidência de estado das telas.",
                "Confirmar que os testes chamam screenshot() e que TestStorage está ativo.",
                "alta", origin="evidence", cause="confirmed",
            )
        )
    findings.extend(check_evidence_integrity(summary))

    for rel_path, count in scan_logcat(root, summary["logcat"]):
        findings.append(
            finding(
                "P0", "crash-anr", "-", rel_path,
                f"{count} marcador(es) de crash/ANR no logcat.",
                "Possível crash, ANR ou exceção fatal durante a auditoria.",
                "Inspecionar o logcat e reproduzir o cenário associado.",
                "média", rel_path, origin="product", cause="hypothesis",
            )
        )

    for entry in summary["databaseSummary"]:
        findings.extend(check_database_summary(entry))

    return findings


def check_database_summary(entry: dict) -> list[dict]:
    content = entry.get("content", {})
    if content.get("parseError"):
        return [
            finding(
                "P1", "evidence-invalid", "-", entry["path"],
                "Resumo de banco não pôde ser lido (JSON inválido).",
                "Sem verificação determinística de integridade para o cenário.",
                "Verificar a geração de database-summary no coletor de testes.",
                "alta", entry["path"], origin="evidence", cause="confirmed",
            )
        ]

    findings: list[dict] = []
    scenario = content.get("scenario", "-")
    path = entry["path"]

    violations = content.get("foreignKeyViolations") or []
    if violations:
        findings.append(
            finding(
                "P0", "data-integrity", scenario, "foreign_key_check",
                f"{len(violations)} violação(ões) de chave estrangeira: {violations}.",
                "Banco em estado inconsistente após o cenário.",
                "Corrigir a escrita que deixa referências órfãs.",
                "alta", path, origin="product", cause="confirmed",
            )
        )

    cache = content.get("saldoCache") or []
    saldos = [item.get("saldoCentavos") for item in cache if item.get("saldoCentavos") is not None]
    if any(saldo < 0 for saldo in saldos):
        findings.append(
            finding(
                "P0", "financial-integrity", scenario, "conta.saldoCentavos",
                "Saldo em cache negativo encontrado.",
                "Invariante saldo >= 0 violada.",
                "Revisar FinanceiroService/pagamentos que geram saldo negativo.",
                "alta", path, origin="product", cause="confirmed",
            )
        )

    historico = content.get("saldoHistoricoCentavos")
    if historico is not None and saldos:
        divergente = historico != saldos[0] if len(saldos) == 1 else historico not in saldos
        if divergente:
            findings.append(
                finding(
                    "P0", "financial-integrity", scenario, "saldoCache vs ledger",
                    f"Saldo em cache {saldos} diverge do ledger reconstruído ({historico}).",
                    "Cache de Conta fora de sincronia com o histórico de vendas.",
                    "Reconciliar Conta a partir do ledger e investigar a divergência.",
                    "alta" if len(saldos) == 1 else "média", path,
                    origin="product", cause="confirmed",
                )
            )

    return findings


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
    lines.extend(["", "## Deterministic Findings", ""])
    findings = summary["deterministicFindings"]
    if findings:
        for item in findings:
            lines.append(
                f"- `{item['severity']}` [{item['category']}]"
                f" origin=`{item['origin']}` cause=`{item['cause']}`: {item['description']}"
                f" (tela: {item['screen']}, confiança: {item['confidence']}, evidência: `{item['evidence']}`)"
            )
    else:
        lines.append("- Nenhum apontamento determinístico.")
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
    finding_rows = "\n".join(
        "<tr>"
        f"<td>{html.escape(item['severity'])}</td>"
        f"<td>{html.escape(item['category'])}</td>"
        f"<td>{html.escape(item['origin'])}</td>"
        f"<td>{html.escape(item['cause'])}</td>"
        f"<td>{html.escape(item['description'])}</td>"
        f"<td>{html.escape(item['evidence'])}</td>"
        "</tr>"
        for item in summary["deterministicFindings"]
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
<h2>Deterministic Findings</h2>
<table><tr><th>Severity</th><th>Category</th><th>Origin</th><th>Cause</th><th>Description</th><th>Evidence</th></tr>{finding_rows}</table>
</body>
</html>
"""
    (root / "report.html").write_text(document, encoding="utf-8")


def write_visual_review(root: pathlib.Path, summary: dict) -> None:
    """Visual review is NOT auto-analyzed here; declare it honestly instead of emitting blank fields."""
    lines = [
        "# Visual Review",
        "",
        "> Status: **Análise visual não executada** automaticamente.",
        "> As checagens determinísticas estão em `report.md` (seção Deterministic Findings).",
        "> Para uma análise visual real, revisar as capturas abaixo com um humano ou modelo e",
        "> preencher os campos por apontamento (severidade/tela/componente/impacto/recomendação).",
        "",
        "## Screenshot Index",
        "",
    ]
    if summary["screenshots"]:
        for path in summary["screenshots"]:
            lines.extend([f"### {path}", f"![{path}]({path})", "- Status: não analisado", ""])
    else:
        lines.append("- Nenhum screenshot coletado.")
    (root / "visual-review.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_action_plan(root: pathlib.Path, summary: dict) -> None:
    findings = summary["deterministicFindings"]
    lines = ["# Action Plan", "", "Origem: checagens determinísticas da execução.", ""]
    total = 0
    for severity in ("P0", "P1", "P2"):
        lines.extend([f"## {severity}", ""])
        bucket = [item for item in findings if item["severity"] == severity]
        total += len(bucket)
        if bucket:
            for item in bucket:
                lines.append(
                    f"- [{item['category']}] origin=`{item['origin']}` cause=`{item['cause']}`: "
                    f"{item['description']} — {item['recommendation']}"
                )
        else:
            lines.append("- Nenhum apontamento.")
        lines.append("")
    if total == 0:
        lines.append("Nenhum apontamento determinístico nesta execução.")
    (root / "action-plan.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


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
        "root": str(root),
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "taxonomyVersion": 2,
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
        "visualAnalysisExecuted": False,
    }
    summary["deterministicFindings"] = run_deterministic_checks(root, summary)
    (root / "execution-summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    (root / "app-build.json").write_text(json.dumps(summary["apk"], indent=2), encoding="utf-8")
    write_markdown(root, summary)
    write_html(root, summary)
    write_visual_review(root, summary)
    write_action_plan(root, summary)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
