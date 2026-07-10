package com.example.caderneta.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.caderneta.data.AppDatabase
import com.example.caderneta.data.entity.Configuracoes
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ConfiguracoesPersistenceFileDbTest {
    @Test
    fun configuracoesPersistemAposFecharEReabrirBancoEmArquivo() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbFile = File(context.cacheDir, "configuracoes-persistence-${System.nanoTime()}.db")

            val firstDb = openDb(context, dbFile)
            try {
                ConfiguracoesRepository(firstDb.configuracoesDao()).salvarConfiguracoes(config())
            } finally {
                firstDb.close()
            }

            val secondDb = openDb(context, dbFile)
            try {
                assertEquals(config(), ConfiguracoesRepository(secondDb.configuracoesDao()).getConfiguracoesOnce())
            } finally {
                secondDb.close()
            }

            dbFile.delete()
        }

    private fun openDb(
        context: Context,
        dbFile: File,
    ): AppDatabase =
        Room
            .databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
            .allowMainThreadQueries()
            .build()

    private fun config() =
        Configuracoes(
            precoSalgadoVistaCentavos = 500,
            precoSalgadoPrazoCentavos = 600,
            precoSucoVistaCentavos = 300,
            precoSucoPrazoCentavos = 400,
            promocoesAtivadas = false,
            promo1Nome = "",
            promo1Salgados = 0,
            promo1Sucos = 0,
            promo1VistaCentavos = 0,
            promo1PrazoCentavos = 0,
            promo2Nome = "",
            promo2Salgados = 0,
            promo2Sucos = 0,
            promo2VistaCentavos = 0,
            promo2PrazoCentavos = 0,
        )
}
