package com.example.caderneta.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
        )

    @Test
    fun migration1To2PreservesClienteAndAddsNullableFotoNome() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO locais (id, nome, endereco, parentId, level, arquivado)
                VALUES (1, 'Local', NULL, NULL, 0, 0)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO clientes (id, nome, telefone, localId, sublocal1Id, sublocal2Id, sublocal3Id, arquivado)
                VALUES (1, 'Cliente', '11999999999', 1, NULL, NULL, NULL, 0)
                """.trimIndent(),
            )
            close()
        }

        val migratedDb =
            helper.runMigrationsAndValidate(
                TEST_DB,
                2,
                true,
                AppDatabase.MIGRATION_1_2,
            )

        migratedDb.query("SELECT nome, telefone, fotoNome FROM clientes WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Cliente", cursor.getString(0))
            assertEquals("11999999999", cursor.getString(1))
            assertTrue(cursor.isNull(2))
        }
        migratedDb.close()
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
