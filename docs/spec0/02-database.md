# Banco de dados

Data: 2026-07-08

## Estado autoritativo

O app foi rebaselineado como instalação nova em Room `version = 1` depois da incorporação do SPEC 0. O projeto ainda não entrou em produção, então as migrações legadas v5 -> v6, schemas antigos e fixture de migração foram removidos como peso morto.

Schema autoritativo: `app/schemas/com.example.caderneta.data.AppDatabase/1.json`.

## Implicação para builds de desenvolvimento

Instalações locais antigas com banco v6 devem ser desinstaladas antes de instalar esta branch. Como o número de versão foi rebaixado para v1 e não há usuários em produção, não há caminho de downgrade/migração para bancos de desenvolvimento antigos.

## Decisões mantidas do SPEC 0

- Dinheiro é persistido como `INTEGER` em centavos.
- Transações e tipos de operação usam enums canônicos persistidos como texto.
- `Cliente` e `Local` usam soft-delete por `arquivado`.
- `Conta.saldoCentavos` é cache materializado derivado do ledger financeiro.
- `Venda.localId` pode ser `NULL` para pagamentos sem local.
- FKs protegem histórico financeiro contra exclusões físicas indevidas.
- `Venda.operacaoId` é FK `RESTRICT` para `Operacao.id` e possui índice único,
  mantendo a relação 1:1 entre lançamento do extrato e operação financeira.

## Workflows de teste

O workflow instrumentado foi removido enquanto não existir `app/src/androidTest`.
Ele deve ser recriado quando houver testes instrumentados reais para executar no emulador.
