# Etapa 0 — Linha de base (SPEC 0)

**Data:** 2026-07-07
**Branch:** `stabilization/spec-0` (a partir de `6010559`)
**Ambiente:** Linux (Fedora 44), JDK 17 (`~/.jdks/jbr-17.0.14`), Android SDK em `~/Android/Sdk` (platforms 35/36.1, build-tools 35/36.1/37), Gradle wrapper 8.11.1, sem AVD/dispositivo.

## Comando executado (sem nenhuma alteração de código)

```
JAVA_HOME=~/.jdks/jbr-17.0.14 ./gradlew assembleDebug testDebugUnitTest lintDebug --stacktrace
```

## Resultado

| Tarefa | Resultado |
| --- | --- |
| `assembleDebug` | ✅ sucesso — `app-debug.apk` gerado |
| `testDebugUnitTest` | ✅ 1 teste, 0 falhas (apenas `ExampleUnitTest`: `2+2=4`) |
| `lintDebug` | ❌ **falhou** — 4 erros, 284 warnings |

`BUILD FAILED in 7m 55s` (falha causada exclusivamente pelo lint; compilação e testes passaram).

## Erros de lint (pré-existentes)

Todos `MissingTranslation` em `app/src/main/res/values/strings.xml` (strings declaradas no default e ausentes em `values-pt/`): `app_name`, `navigation_drawer_open`, `navigation_drawer_close`, `add_new_local`.

## Warnings de lint por categoria (topo)

| Qtde | Categoria |
| --- | --- |
| 91 | HardcodedText |
| 36 | UseTomlInstead |
| 32 | GradleDependency |
| 21 | Autofill |
| 19 | UnusedResources |
| 14 | ContentDescription |
| 11 | DefaultLocale |
| 9 | LabelFor |
| demais | DisableBaselineAlignment, VectorRaster, Overdraw, UseKtx, SpUsage, RtlHardcoded, etc. |

Relatório completo: `app/build/reports/lint-results-debug.{html,txt,xml}` (não versionado).

## Testes existentes

- `ExampleUnitTest.addition_isCorrect` (2+2=4) — único teste unitário.
- `ExampleInstrumentedTest` (verifica nome do pacote) — não executado (sem emulador/dispositivo).

## Limitações do ambiente registradas

- Não há `cmdline-tools`, system images nem AVD; não há dispositivo físico conectado. Instalação do APK e capturas de tela ficam pendentes até haver emulador (Gradle Managed Devices será tentado na Etapa 7) — verificação parcial declarada.
- `java` não está no PATH; builds exigem `JAVA_HOME=~/.jdks/jbr-17.0.14`.

## Fixture v5

Sem emulador, a fixture será construída com `sqlite3` usando o DDL autoritativo do Room: antes de qualquer mudança de schema, o projeto será compilado na versão 5 com `exportSchema = true` para gerar `app/schemas/...5.json`, e o `createAllTables` desse JSON será usado para criar `caderneta_v5_fixture.db` com dados de amostra (locais, sublocais, clientes, vendas à vista/a prazo, promoções, pagamentos, saldos). Ver Etapa 2.

## Build release (R8)

Executado em seguida (`./gradlew assembleRelease`): resultado registrado em `01-build.md`.
