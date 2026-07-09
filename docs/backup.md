# Backup JSON

O mecanismo primário de backup do Caderneta é exportação/restauração JSON via SAF, acessível em Configurações.

- `formatVersion`: versão do formato, atualmente `1`.
- `dbVersion`: versão Room autoritativa, atualmente `1`.
- `app`: `applicationId` esperado (`com.example.caderneta`).
- `geradoEmMillis`: timestamp de geração.
- Listas exportadas: `locais`, `clientes`, `operacoes`, `vendas`, `contas`, `configuracoes`.

A restauração valida versão, app, enums, IDs duplicados, hierarquia de locais, relação 1:1 `Venda`/`Operacao`, valores não negativos e integridade referencial antes de escrever. A hierarquia completa de cada cliente também é validada antes da restauração: `localId` deve ser a raiz da cadeia, cada sublocal deve apontar diretamente para o pai esperado por `parentId`, níveis pulados ou locais repetidos são rejeitados e cliente ativo não pode apontar para local arquivado em nenhum nível.

As `contas` presentes no arquivo não são fonte de verdade. O saldo é sempre reconstruído a partir de `vendas`: `A_PRAZO` soma, `PAGAMENTO` subtrai e `A_VISTA` não altera saldo. Backup que gere saldo reconstruído negativo é inválido.

A escrita ocorre em uma única transação: apaga filhos antes dos pais, insere pais antes dos filhos, recalcula `contas` a partir de `vendas` e executa `PRAGMA foreign_key_check` antes do commit. Se qualquer etapa falhar, a restauração é revertida e os dados anteriores permanecem intactos.

O Auto Backup do Google mantém includes explícitos para `caderneta_database` e `backup_prefs.xml`, mas é best-effort. O JSON exportado pelo usuário é a fonte recomendada para backup portátil.
