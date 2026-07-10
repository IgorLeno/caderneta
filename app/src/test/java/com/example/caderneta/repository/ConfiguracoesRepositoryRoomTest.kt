package com.example.caderneta.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.caderneta.data.AppDatabase
import com.example.caderneta.data.entity.Configuracoes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConfiguracoesRepositoryRoomTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: ConfiguracoesRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room
                .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository = ConfiguracoesRepository(db.configuracoesDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun salvarRelerESubstituirMantemUmaLinhaIdUm() =
        runTest {
            repository.salvarConfiguracoes(config())
            assertEquals(config(), repository.getConfiguracoesOnce())
            assertEquals(config(), repository.getConfiguracoes().first())

            val alterada = config(precoSalgadoPrazoCentavos = 750)
            repository.salvarConfiguracoes(alterada)

            assertEquals(alterada, repository.getConfiguracoesOnce())
            assertEquals(1, db.configuracoesDao().countConfiguracoes())
        }

    @Test
    fun configuracaoInvalidaNaoDeveSerPersistidaPeloChamador() =
        runTest {
            val invalida = config(precoSalgadoVistaCentavos = 0)

            if (invalida.isValid()) repository.salvarConfiguracoes(invalida)

            assertNull(repository.getConfiguracoesOnce())
        }

    private fun config(
        precoSalgadoVistaCentavos: Long = 500,
        precoSalgadoPrazoCentavos: Long = 600,
    ) = Configuracoes(
        precoSalgadoVistaCentavos = precoSalgadoVistaCentavos,
        precoSalgadoPrazoCentavos = precoSalgadoPrazoCentavos,
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
