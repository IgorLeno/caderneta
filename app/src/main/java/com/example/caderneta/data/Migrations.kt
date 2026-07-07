package com.example.caderneta.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v5 -> v6: migração estruturante da estabilização (SPEC 0).
 *
 * - Dinheiro passa de REAL (reais) para INTEGER (centavos): valorCentavos,
 *   saldoCentavos, *Centavos em configuracoes. Conversão ROUND(valor*100).
 * - Tipos de domínio deixam de ser strings livres: vendas.transacao vira
 *   A_VISTA/A_PRAZO/PAGAMENTO e operacoes.tipoOperacao vira
 *   VENDA/PROMOCAO/PAGAMENTO (nomes dos enums Kotlin).
 * - Exclusões deixam de apagar histórico: FKs de vendas (cliente/local) e a
 *   nova FK de operacoes.clienteId usam RESTRICT; cliente.localId idem;
 *   soft-delete via coluna `arquivado` em clientes e locais.
 * - vendas.localId passa a ser NULLABLE (pagamentos sem local selecionado
 *   eram gravados com localId=0, uma FK inválida); valores órfãos viram NULL.
 * - locais ganha FK real em parentId (órfãos viram raiz) e perde isExpanded
 *   (estado de UI não pertence ao domínio).
 * - operacoes órfãs (cliente inexistente) são removidas — antes não havia FK.
 * - Tabelas mortas produtos/itens_venda são removidas (nunca usadas no fluxo).
 */
val MIGRATION_5_6 =
    object : Migration(5, 6) {
        @Suppress("LongMethod")
        override fun migrate(db: SupportSQLiteDatabase) {
            // ----- locais: FK em parentId, +arquivado, -isExpanded -----
            db.execSQL(
                "UPDATE locais SET parentId = NULL WHERE parentId IS NOT NULL " +
                    "AND parentId NOT IN (SELECT id FROM locais)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `_new_locais` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`nome` TEXT NOT NULL, `endereco` TEXT, `parentId` INTEGER, " +
                    "`level` INTEGER NOT NULL, `arquivado` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`parentId`) REFERENCES `locais`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE RESTRICT )",
            )
            db.execSQL(
                "INSERT INTO _new_locais (id, nome, endereco, parentId, level, arquivado) " +
                    "SELECT id, nome, endereco, parentId, level, 0 FROM locais",
            )
            db.execSQL("DROP TABLE locais")
            db.execSQL("ALTER TABLE _new_locais RENAME TO locais")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_locais_parentId` ON `locais` (`parentId`)")

            // ----- clientes: localId RESTRICT, +arquivado -----
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `_new_clientes` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`nome` TEXT NOT NULL, `telefone` TEXT, `localId` INTEGER NOT NULL, " +
                    "`sublocal1Id` INTEGER, `sublocal2Id` INTEGER, `sublocal3Id` INTEGER, " +
                    "`arquivado` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`localId`) REFERENCES `locais`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                    "FOREIGN KEY(`sublocal1Id`) REFERENCES `locais`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , " +
                    "FOREIGN KEY(`sublocal2Id`) REFERENCES `locais`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , " +
                    "FOREIGN KEY(`sublocal3Id`) REFERENCES `locais`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            )
            db.execSQL(
                "INSERT INTO _new_clientes " +
                    "(id, nome, telefone, localId, sublocal1Id, sublocal2Id, sublocal3Id, arquivado) " +
                    "SELECT id, nome, telefone, localId, sublocal1Id, sublocal2Id, sublocal3Id, 0 FROM clientes",
            )
            db.execSQL("DROP TABLE clientes")
            db.execSQL("ALTER TABLE _new_clientes RENAME TO clientes")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_clientes_localId` ON `clientes` (`localId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_clientes_sublocal1Id` ON `clientes` (`sublocal1Id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_clientes_sublocal2Id` ON `clientes` (`sublocal2Id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_clientes_sublocal3Id` ON `clientes` (`sublocal3Id`)")

            // ----- operacoes: FK clienteId RESTRICT, tipo canônico, centavos -----
            // Órfãs são removidas: não havia FK e o cascade de clientes nunca as limpou.
            db.execSQL("DELETE FROM operacoes WHERE clienteId NOT IN (SELECT id FROM clientes)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `_new_operacoes` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`clienteId` INTEGER NOT NULL, `tipoOperacao` TEXT NOT NULL, " +
                    "`valorCentavos` INTEGER NOT NULL, `data` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`clienteId`) REFERENCES `clientes`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE RESTRICT )",
            )
            db.execSQL(
                "INSERT INTO _new_operacoes (id, clienteId, tipoOperacao, valorCentavos, data) " +
                    "SELECT id, clienteId, " +
                    "CASE " +
                    "WHEN lower(tipoOperacao) LIKE 'promo%' THEN 'PROMOCAO' " +
                    "WHEN lower(tipoOperacao) = 'pagamento' THEN 'PAGAMENTO' " +
                    "ELSE 'VENDA' END, " +
                    "CAST(ROUND(valor * 100) AS INTEGER), data FROM operacoes",
            )
            db.execSQL("DROP TABLE operacoes")
            db.execSQL("ALTER TABLE _new_operacoes RENAME TO operacoes")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_operacoes_clienteId` ON `operacoes` (`clienteId`)")

            // ----- vendas: FKs RESTRICT, localId nullable, transacao canônica, centavos -----
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `_new_vendas` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`operacaoId` INTEGER NOT NULL, `clienteId` INTEGER NOT NULL, " +
                    "`localId` INTEGER, `data` INTEGER NOT NULL, `transacao` TEXT NOT NULL, " +
                    "`quantidadeSalgados` INTEGER NOT NULL, `quantidadeSucos` INTEGER NOT NULL, " +
                    "`isPromocao` INTEGER NOT NULL, `promocaoDetalhes` TEXT, " +
                    "`valorCentavos` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`clienteId`) REFERENCES `clientes`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                    "FOREIGN KEY(`localId`) REFERENCES `locais`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            )
            db.execSQL(
                "INSERT INTO _new_vendas (id, operacaoId, clienteId, localId, data, transacao, " +
                    "quantidadeSalgados, quantidadeSucos, isPromocao, promocaoDetalhes, valorCentavos) " +
                    "SELECT id, operacaoId, clienteId, " +
                    // localId=0 era gravado em pagamentos sem local selecionado (FK inválida)
                    "CASE WHEN localId IN (SELECT id FROM locais) THEN localId ELSE NULL END, " +
                    "data, " +
                    "CASE lower(transacao) " +
                    "WHEN 'a_vista' THEN 'A_VISTA' " +
                    "WHEN 'a_prazo' THEN 'A_PRAZO' " +
                    "WHEN 'pagamento' THEN 'PAGAMENTO' " +
                    "ELSE upper(transacao) END, " +
                    "quantidadeSalgados, quantidadeSucos, isPromocao, promocaoDetalhes, " +
                    "CAST(ROUND(valor * 100) AS INTEGER) FROM vendas",
            )
            db.execSQL("DROP TABLE vendas")
            db.execSQL("ALTER TABLE _new_vendas RENAME TO vendas")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_vendas_clienteId` ON `vendas` (`clienteId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_vendas_localId` ON `vendas` (`localId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_vendas_data` ON `vendas` (`data`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_vendas_operacaoId` ON `vendas` (`operacaoId`)")

            // ----- contas: FK CASCADE (cache derivado), centavos -----
            db.execSQL("DELETE FROM contas WHERE clienteId NOT IN (SELECT id FROM clientes)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `_new_contas` (" +
                    "`clienteId` INTEGER NOT NULL, `saldoCentavos` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`clienteId`), " +
                    "FOREIGN KEY(`clienteId`) REFERENCES `clientes`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "INSERT INTO _new_contas (clienteId, saldoCentavos) " +
                    "SELECT clienteId, CAST(ROUND(saldo * 100) AS INTEGER) FROM contas",
            )
            db.execSQL("DROP TABLE contas")
            db.execSQL("ALTER TABLE _new_contas RENAME TO contas")

            // ----- configuracoes: preços em centavos -----
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `_new_configuracoes` (" +
                    "`id` INTEGER NOT NULL, " +
                    "`precoSalgadoVistaCentavos` INTEGER NOT NULL, " +
                    "`precoSalgadoPrazoCentavos` INTEGER NOT NULL, " +
                    "`precoSucoVistaCentavos` INTEGER NOT NULL, " +
                    "`precoSucoPrazoCentavos` INTEGER NOT NULL, " +
                    "`promocoesAtivadas` INTEGER NOT NULL, " +
                    "`promo1Nome` TEXT NOT NULL, `promo1Salgados` INTEGER NOT NULL, " +
                    "`promo1Sucos` INTEGER NOT NULL, " +
                    "`promo1VistaCentavos` INTEGER NOT NULL, `promo1PrazoCentavos` INTEGER NOT NULL, " +
                    "`promo2Nome` TEXT NOT NULL, `promo2Salgados` INTEGER NOT NULL, " +
                    "`promo2Sucos` INTEGER NOT NULL, " +
                    "`promo2VistaCentavos` INTEGER NOT NULL, `promo2PrazoCentavos` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "INSERT INTO _new_configuracoes (id, precoSalgadoVistaCentavos, precoSalgadoPrazoCentavos, " +
                    "precoSucoVistaCentavos, precoSucoPrazoCentavos, promocoesAtivadas, " +
                    "promo1Nome, promo1Salgados, promo1Sucos, promo1VistaCentavos, promo1PrazoCentavos, " +
                    "promo2Nome, promo2Salgados, promo2Sucos, promo2VistaCentavos, promo2PrazoCentavos) " +
                    "SELECT id, " +
                    "CAST(ROUND(precoSalgadoVista * 100) AS INTEGER), " +
                    "CAST(ROUND(precoSalgadoPrazo * 100) AS INTEGER), " +
                    "CAST(ROUND(precoSucoVista * 100) AS INTEGER), " +
                    "CAST(ROUND(precoSucoPrazo * 100) AS INTEGER), " +
                    "promocoesAtivadas, " +
                    "promo1Nome, promo1Salgados, promo1Sucos, " +
                    "CAST(ROUND(promo1Vista * 100) AS INTEGER), " +
                    "CAST(ROUND(promo1Prazo * 100) AS INTEGER), " +
                    "promo2Nome, promo2Salgados, promo2Sucos, " +
                    "CAST(ROUND(promo2Vista * 100) AS INTEGER), " +
                    "CAST(ROUND(promo2Prazo * 100) AS INTEGER) FROM configuracoes",
            )
            db.execSQL("DROP TABLE configuracoes")
            db.execSQL("ALTER TABLE _new_configuracoes RENAME TO configuracoes")

            // ----- estruturas mortas (nunca usadas no fluxo do app) -----
            db.execSQL("DROP TABLE IF EXISTS itens_venda")
            db.execSQL("DROP TABLE IF EXISTS produtos")
        }
    }
