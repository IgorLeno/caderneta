# Caderneta

Aplicativo Android em Kotlin para controle de vendas de salgados/sucos, clientes por hierarquia de locais, vendas à vista/a prazo, pagamentos, extratos e balanço financeiro.

## Estado Técnico

- `applicationId`: `com.example.caderneta`
- Min SDK: 23
- Target SDK: 35
- Compile SDK: 36
- Kotlin: 2.1.21
- Room: 2.8.4
- Banco Room: `version = 1`, schema em `app/schemas/com.example.caderneta.data.AppDatabase/1.json`
- Dinheiro: sempre `Long` em centavos no domínio e no banco
- Backup primário: JSON via SAF em Configurações

## Banco de Dados

Entidades principais:

- `Local`: hierarquia de locais com `parentId`, `level` e soft-delete por `arquivado`.
- `Cliente`: cliente ligado a local principal e até três sublocais opcionais.
- `Venda`: lançamento do extrato, incluindo `A_VISTA`, `A_PRAZO` e `PAGAMENTO`.
- `Operacao`: livro financeiro de vendas, promoções e pagamentos.
- `Conta`: cache materializado do saldo devedor em centavos.
- `Configuracoes`: preços e promoções.

O app ainda não entrou em produção. A base foi rebaselineada para Room v1 após estabilização; instalações antigas de desenvolvimento com banco v6 devem ser desinstaladas antes de instalar builds desta branch.

## Backup

O backup portável fica em Configurações:

- Exporta JSON versionado com locais, clientes, operações, vendas, contas e configurações.
- Valida versão, `applicationId`, enums, valores e FKs antes de restaurar.
- Restauração ocorre em transação única.
- `Conta` é recalculada a partir do ledger após restauração.

Detalhes do formato: `docs/backup.md`.

## Arquitetura

- MVVM com `ViewModel`, `StateFlow`, `SharedFlow` e eventos de UI via `Channel<UiEvento>` nas telas principais.
- DI manual em `CadernetaApplication`.
- `FinanceiroService` é a porta transacional para gravações financeiras.
- Repositórios encapsulam DAOs e regras de arquivamento/exclusão.

## Qualidade e Testes

Testes locais rodam em JVM com Robolectric e Room in-memory:

```bash
JAVA_HOME=~/.jdks/jbr-17.0.14 ./gradlew testDebugUnitTest
```

Verificação completa esperada:

```bash
JAVA_HOME=~/.jdks/jbr-17.0.14 ./gradlew clean assembleDebug testDebugUnitTest lintDebug detekt ktlintCheck assembleRelease
```

Testes instrumentados (`connectedDebugAndroidTest`) ficam em workflow manual porque o ambiente local não possui adb/emulador.

## CI

- `.github/workflows/ci.yml`: build, testes unitários, lint, detekt e ktlint em push/PR para `master`.
- `.github/workflows/instrumented-tests.yml`: testes instrumentados manuais via emulador.
