# Backup JSON

O mecanismo primário de backup do Caderneta é exportação/restauração JSON via SAF, acessível em Configurações.

- `formatVersion`: versão do formato, atualmente `1`.
- `dbVersion`: versão Room autoritativa, atualmente `1`.
- `app`: `applicationId` esperado (`com.example.caderneta`).
- `geradoEmMillis`: timestamp de geração.
- Listas exportadas: `locais`, `clientes`, `operacoes`, `vendas`, `contas`, `configuracoes`.

A restauração valida versão, app, enums, IDs duplicados, hierarquia de locais, relação 1:1 `Venda`/`Operacao`, valores não negativos e integridade referencial antes de escrever. A escrita ocorre em uma única transação: apaga filhos antes dos pais, insere pais antes dos filhos, recalcula `contas` a partir de `vendas` e executa `PRAGMA foreign_key_check` antes do commit.

O Auto Backup do Google mantém includes explícitos para `caderneta_database` e `backup_prefs.xml`, mas é best-effort. O JSON exportado pelo usuário é a fonte recomendada para backup portátil.
