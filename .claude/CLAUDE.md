# Caderneta - Project Notes

## Stack

- Android Kotlin app using XML Views and Navigation Component.
- Kotlin 2.1.21, AGP 8.13.2, Room 2.8.4.
- Min SDK 23, target SDK 35, compile SDK 36.
- Manual DI in `CadernetaApplication`.
- Main package: `com.example.caderneta`.

## Current Data Model

Room is rebaselineed to `version = 1`; authoritative schema is `app/schemas/com.example.caderneta.data.AppDatabase/1.json`.

Entities:

- `Local`
- `Cliente`
- `Venda`
- `Operacao`
- `Conta`
- `Configuracoes`

Removed/dead legacy concepts: `Produto`, `ItemVenda`, `VendaPersonalizadaDialog`, `RegistrarPagamentoDialog`, old v5/v6 migrations and fixtures.

Money is stored as `Long` centavos. Use `util/Dinheiro.kt`; do not introduce `Double` for money.

## Financial Rules

Use `FinanceiroService` for financial writes. It owns transactions that keep `Operacao`, `Venda` and `Conta` consistent.

- `A_PRAZO` increases saldo.
- `PAGAMENTO` decreases saldo and cannot exceed current saldo.
- `A_VISTA` does not affect saldo.
- `Conta` is a materialized cache and can be reconciled from `Venda` ledger.

## Backup

Primary backup is JSON via SAF in Configurações, implemented in `data/backup/`.

- Export is transactional.
- Restore validates before writing.
- Restore writes in one transaction and recalculates `Conta` from ledger.
- Auto Backup XML exists only as best-effort fallback.

## Testing

Use Robolectric unit tests for Room and services. Local emulator/adb is not assumed available.

Recommended command:

```bash
JAVA_HOME=~/.jdks/jbr-17.0.14 ./gradlew testDebugUnitTest
```

Full verification:

```bash
JAVA_HOME=~/.jdks/jbr-17.0.14 ./gradlew clean assembleDebug testDebugUnitTest lintDebug detekt ktlintCheck assembleRelease
```

## Development Notes

- Do not add Room migrations for pre-rebaseline development versions unless production history changes.
- Devices with old dev DB v6 must uninstall before installing this branch.
- UI one-shot messages should use `UiEvento`, not sticky nullable error state.
- Avoid logging names, balances, values, full configs or search terms.
