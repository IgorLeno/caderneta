package com.example.caderneta.data.backup

import com.example.caderneta.data.entity.Configuracoes
import com.example.caderneta.data.entity.Local
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupSerializerTest {
    private val serializer = BackupSerializer()

    @Test
    fun serializaEDesserializaSnapshot() {
        val snapshot =
            BackupSnapshot(
                app = "com.example.caderneta",
                geradoEmMillis = 123,
                locais = listOf(Local(id = 1, nome = "Local")),
                clientes = emptyList(),
                operacoes = emptyList(),
                vendas = emptyList(),
                contas = emptyList(),
                configuracoes = listOf(config()),
            )

        val restaurado = serializer.fromJson(serializer.toJson(snapshot))

        assertEquals(snapshot.app, restaurado.app)
        assertEquals(snapshot.locais, restaurado.locais)
        assertEquals(snapshot.configuracoes, restaurado.configuracoes)
    }

    private fun config() =
        Configuracoes(
            precoSalgadoVistaCentavos = 500,
            precoSalgadoPrazoCentavos = 600,
            precoSucoVistaCentavos = 300,
            precoSucoPrazoCentavos = 400,
            promocoesAtivadas = false,
            promo1Nome = "Promo 1",
            promo1Salgados = 1,
            promo1Sucos = 1,
            promo1VistaCentavos = 700,
            promo1PrazoCentavos = 800,
            promo2Nome = "Promo 2",
            promo2Salgados = 2,
            promo2Sucos = 2,
            promo2VistaCentavos = 900,
            promo2PrazoCentavos = 1000,
        )
}
