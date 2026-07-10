package com.example.caderneta.fixtures

import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.Configuracoes
import com.example.caderneta.data.entity.Local

object TestFixtures {
    fun configuracoesBasicas(): Configuracoes =
        Configuracoes(
            precoSalgadoVistaCentavos = 500,
            precoSalgadoPrazoCentavos = 600,
            precoSucoVistaCentavos = 300,
            precoSucoPrazoCentavos = 400,
            promocoesAtivadas = false,
            promo1Nome = "Combo Alfa",
            promo1Salgados = 1,
            promo1Sucos = 1,
            promo1VistaCentavos = 700,
            promo1PrazoCentavos = 800,
            promo2Nome = "Combo Beta",
            promo2Salgados = 2,
            promo2Sucos = 1,
            promo2VistaCentavos = 1200,
            promo2PrazoCentavos = 1400,
        )

    fun localPrincipal(id: Long = 101): Local =
        Local(
            id = id,
            nome = "Audit Local Norte",
            endereco = "Rua Ficticia 100",
            parentId = null,
            level = 0,
            arquivado = false,
        )

    fun sublocal(
        parentId: Long = 101,
        id: Long = 102,
    ): Local =
        Local(
            id = id,
            nome = "Audit Sala 2",
            endereco = "Setor Ficticio",
            parentId = parentId,
            level = 1,
            arquivado = false,
        )

    fun cliente(
        localId: Long = 101,
        id: Long = 201,
    ): Cliente =
        Cliente(
            id = id,
            nome = "Cliente Auditoria",
            telefone = "11999990000",
            localId = localId,
        )
}
