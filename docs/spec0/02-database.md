# SPEC 0 - Etapa 2: banco de dados seguro

Data: 2026-07-07

## Implementado

- Room atualizado para `version = 6` com `exportSchema = true`.
- Schema v6 exportado em `app/schemas/com.example.caderneta.data.AppDatabase/6.json`.
- `fallbackToDestructiveMigration()` removido para versões recuperáveis.
- Migração `MIGRATION_5_6` adicionada.
- Dinheiro migrado de `REAL` para centavos `INTEGER` em vendas, operações, contas e configurações.
- `Venda.transacao` e `Operacao.tipoOperacao` normalizados para enums persistidos como texto canônico.
- Produto/ItemVenda removidos do schema v6 e da DI, mantendo drop das tabelas na migração.
- `Cliente` e `Local` ganharam `arquivado` para soft-delete.
- `Venda.localId` passou a aceitar `NULL` para pagamentos sem local válido.
- `Operacao.clienteId` passou a ter FK para clientes.
- `Conta.saldoCentavos` tratado como cache materializado do livro financeiro.

## Fixture e teste de migração

- Fixture v5 criada em `app/src/androidTest/assets/caderneta_v5_fixture.db`.
- A fixture contém:
  - locais com hierarquia válida e um parent órfão;
  - clientes;
  - vendas à vista, a prazo, promoção e pagamento;
  - pagamento legado com `localId = 0`;
  - operação órfã intencional;
  - contas e configurações em `Double`.
- Teste instrumentado adicionado em `Migration5To6Test`.
- O teste valida:
  - versão final 6;
  - contagens preservadas;
  - operação órfã removida;
  - tabelas mortas removidas;
  - `PRAGMA foreign_key_check` sem linhas;
  - strings canônicas;
  - conversão de centavos;
  - saldo de `contas` equivalente ao saldo derivado de `vendas`.

## Verificação local

- `./gradlew assembleDebug testDebugUnitTest lint detekt ktlintCheck` passou.
- `./gradlew :app:compileDebugAndroidTestKotlin` passou.
- Teste instrumentado não executado localmente: `adb` não está disponível no PATH neste ambiente.

## Observações

- `ktlint_standard_backing-property-naming` foi desabilitada no `.editorconfig`; a regra conflita com o padrão Android `_binding` privado já usado pelo projeto.
- O lint ainda reporta warnings legados, mas nenhum erro após a correção de traduções em `values-pt`.
