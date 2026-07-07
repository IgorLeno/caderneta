package com.example.caderneta.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration5To6Test {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "migration-5-6-test.db"
    private var database: AppDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migratesFixtureFromV5ToV6PreservingFinancialHistory() {
        context.deleteDatabase(databaseName)

        database =
            Room
                .databaseBuilder(context, AppDatabase::class.java, databaseName)
                .createFromAsset("caderneta_v5_fixture.db")
                .addMigrations(MIGRATION_5_6)
                .allowMainThreadQueries()
                .build()

        val db = requireNotNull(database).openHelper.writableDatabase

        assertEquals(6, queryLong(db, "PRAGMA user_version"))
        assertEquals(4, queryLong(db, "SELECT COUNT(*) FROM vendas"))
        assertEquals(4, queryLong(db, "SELECT COUNT(*) FROM operacoes"))
        assertEquals(2, queryLong(db, "SELECT COUNT(*) FROM contas"))
        assertEquals(0, queryLong(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='produtos'"))
        assertEquals(0, queryLong(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='itens_venda'"))

        assertFalse(hasRows(db, "PRAGMA foreign_key_check"))
        assertNull(queryNullableString(db, "SELECT localId FROM vendas WHERE id = 3"))
        assertNull(queryNullableString(db, "SELECT parentId FROM locais WHERE id = 4"))

        assertEquals("A_VISTA", queryString(db, "SELECT transacao FROM vendas WHERE id = 1"))
        assertEquals("A_PRAZO", queryString(db, "SELECT transacao FROM vendas WHERE id = 2"))
        assertEquals("PAGAMENTO", queryString(db, "SELECT transacao FROM vendas WHERE id = 3"))
        assertEquals("PROMOCAO", queryString(db, "SELECT tipoOperacao FROM operacoes WHERE id = 4"))

        assertEquals(950, queryLong(db, "SELECT valorCentavos FROM vendas WHERE id = 1"))
        assertEquals(1800, queryLong(db, "SELECT valorCentavos FROM vendas WHERE id = 2"))
        assertEquals(500, queryLong(db, "SELECT valorCentavos FROM vendas WHERE id = 3"))
        assertEquals(1235, queryLong(db, "SELECT valorCentavos FROM vendas WHERE id = 4"))

        assertEquals(550, queryLong(db, "SELECT precoSalgadoVistaCentavos FROM configuracoes WHERE id = 1"))
        assertEquals(2400, queryLong(db, "SELECT promo2PrazoCentavos FROM configuracoes WHERE id = 1"))

        assertSaldoMatchesLedger(db, clienteId = 1)
        assertSaldoMatchesLedger(db, clienteId = 2)
    }

    private fun assertSaldoMatchesLedger(
        db: SupportSQLiteDatabase,
        clienteId: Long,
    ) {
        val saldoCache = queryLong(db, "SELECT saldoCentavos FROM contas WHERE clienteId = $clienteId")
        val saldoLivro =
            queryLong(
                db,
                """
                SELECT COALESCE(SUM(
                    CASE transacao
                        WHEN 'A_PRAZO' THEN valorCentavos
                        WHEN 'PAGAMENTO' THEN -valorCentavos
                        ELSE 0
                    END
                ), 0)
                FROM vendas
                WHERE clienteId = $clienteId
                """.trimIndent(),
            )
        assertEquals(saldoLivro, saldoCache)
    }

    private fun queryLong(
        db: SupportSQLiteDatabase,
        sql: String,
    ): Long {
        db.query(sql).use { cursor ->
            assertTrue("Expected one row for query: $sql", cursor.moveToFirst())
            return cursor.getLong(0)
        }
    }

    private fun queryString(
        db: SupportSQLiteDatabase,
        sql: String,
    ): String {
        db.query(sql).use { cursor ->
            assertTrue("Expected one row for query: $sql", cursor.moveToFirst())
            return cursor.getString(0)
        }
    }

    private fun queryNullableString(
        db: SupportSQLiteDatabase,
        sql: String,
    ): String? {
        db.query(sql).use { cursor ->
            assertTrue("Expected one row for query: $sql", cursor.moveToFirst())
            return if (cursor.isNull(0)) null else cursor.getString(0)
        }
    }

    private fun hasRows(
        db: SupportSQLiteDatabase,
        sql: String,
    ): Boolean {
        db.query(sql).use { cursor ->
            return cursor.moveToFirst()
        }
    }
}
