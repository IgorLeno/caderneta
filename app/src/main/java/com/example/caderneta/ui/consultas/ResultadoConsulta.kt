package com.example.caderneta.ui.consultas

import com.example.caderneta.data.entity.Cliente as ClienteEntity
import com.example.caderneta.data.entity.Local as LocalEntity

sealed class ResultadoConsulta {
    data class Local(
        val local: LocalEntity,
    ) : ResultadoConsulta()

    data class Cliente(
        val cliente: ClienteEntity,
        val saldoCentavos: Long,
    ) : ResultadoConsulta()
}
