package com.example.caderneta.ui.consultas

import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.Local

sealed class ResultadoConsulta {
    data class Local(val local: com.example.caderneta.data.entity.Local) : ResultadoConsulta()
    data class Cliente(val cliente: com.example.caderneta.data.entity.Cliente, val saldo: Double) : ResultadoConsulta()
}