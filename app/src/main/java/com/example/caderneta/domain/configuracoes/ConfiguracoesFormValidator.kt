package com.example.caderneta.domain.configuracoes

import com.example.caderneta.data.entity.Configuracoes
import com.example.caderneta.util.ParseDinheiro
import com.example.caderneta.util.parseDinheiro

data class ConfiguracoesInput(
    val precoSalgadoVista: String,
    val precoSalgadoPrazo: String,
    val precoSucoVista: String,
    val precoSucoPrazo: String,
    val promocoesAtivadas: Boolean,
    val promo1Nome: String = "",
    val promo1Salgados: String = "",
    val promo1Sucos: String = "",
    val promo1Vista: String = "",
    val promo1Prazo: String = "",
    val promo2Nome: String = "",
    val promo2Salgados: String = "",
    val promo2Sucos: String = "",
    val promo2Vista: String = "",
    val promo2Prazo: String = "",
)

enum class ConfiguracoesCampo {
    SALGADO_VISTA,
    SALGADO_PRAZO,
    SUCO_VISTA,
    SUCO_PRAZO,
    PROMO1_NOME,
    PROMO1_SALGADOS,
    PROMO1_SUCOS,
    PROMO1_VISTA,
    PROMO1_PRAZO,
    PROMO2_NOME,
    PROMO2_SALGADOS,
    PROMO2_SUCOS,
    PROMO2_VISTA,
    PROMO2_PRAZO,
}

sealed class ConfiguracoesFormResult {
    data class Valida(
        val configuracoes: Configuracoes,
    ) : ConfiguracoesFormResult()

    data class Invalida(
        val erros: Map<ConfiguracoesCampo, String>,
    ) : ConfiguracoesFormResult()
}

object ConfiguracoesFormValidator {
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun validar(input: ConfiguracoesInput): ConfiguracoesFormResult {
        val erros = linkedMapOf<ConfiguracoesCampo, String>()

        fun lerCentavos(
            campo: ConfiguracoesCampo,
            texto: String,
            mensagemVazio: String,
            obrigatorio: Boolean,
            exigirMaiorQueZero: Boolean = obrigatorio,
        ): Long? {
            if (!obrigatorio && texto.isBlank()) return null

            return when (val resultado = texto.parseDinheiro()) {
                is ParseDinheiro.Valido ->
                    if (!exigirMaiorQueZero || resultado.centavos > 0) {
                        resultado.centavos
                    } else {
                        erros[campo] = "Informe um valor maior que zero"
                        null
                    }
                ParseDinheiro.Vazio -> {
                    erros[campo] = mensagemVazio
                    null
                }
                ParseDinheiro.Invalido -> {
                    erros[campo] = "Valor inválido"
                    null
                }
                ParseDinheiro.Negativo -> {
                    erros[campo] = "Valor não pode ser negativo"
                    null
                }
                ParseDinheiro.MuitoGrande -> {
                    erros[campo] = "Valor muito grande"
                    null
                }
            }
        }

        fun lerInteiro(
            campo: ConfiguracoesCampo,
            textoOriginal: String,
            obrigatorio: Boolean,
        ): Int? {
            val texto = textoOriginal.trim()
            if (!obrigatorio && texto.isBlank()) return null

            val valor = texto.toIntOrNull()
            return when {
                texto.isBlank() -> {
                    erros[campo] = "Informe a quantidade"
                    null
                }
                valor == null -> {
                    erros[campo] = "Quantidade inválida"
                    null
                }
                valor < 0 -> {
                    erros[campo] = "Quantidade não pode ser negativa"
                    null
                }
                else -> valor
            }
        }

        fun temCampoPromocionalPreenchido(vararg campos: String): Boolean =
            campos.any { campo ->
                val texto = campo.trim()
                texto.isNotBlank() && texto != "0"
            }

        val salgadoVista =
            lerCentavos(ConfiguracoesCampo.SALGADO_VISTA, input.precoSalgadoVista, "Informe o preço à vista", true)
        val salgadoPrazo =
            lerCentavos(ConfiguracoesCampo.SALGADO_PRAZO, input.precoSalgadoPrazo, "Informe o preço a prazo", true)
        val sucoVista =
            lerCentavos(ConfiguracoesCampo.SUCO_VISTA, input.precoSucoVista, "Informe o preço à vista", true)
        val sucoPrazo =
            lerCentavos(ConfiguracoesCampo.SUCO_PRAZO, input.precoSucoPrazo, "Informe o preço a prazo", true)

        val promo1Nome = input.promo1Nome.trim()
        val promo2Nome = input.promo2Nome.trim()
        if (input.promocoesAtivadas && promo1Nome.isBlank()) {
            erros[ConfiguracoesCampo.PROMO1_NOME] = "Informe o nome da promoção"
        }
        val promo2Preenchida =
            input.promocoesAtivadas &&
                temCampoPromocionalPreenchido(
                    input.promo2Nome,
                    input.promo2Salgados,
                    input.promo2Sucos,
                    input.promo2Vista,
                    input.promo2Prazo,
                )
        if (promo2Preenchida && promo2Nome.isBlank()) {
            erros[ConfiguracoesCampo.PROMO2_NOME] = "Informe o nome da promoção"
        }

        val promo1Obrigatoria = input.promocoesAtivadas
        val promo1Salgados = lerInteiro(ConfiguracoesCampo.PROMO1_SALGADOS, input.promo1Salgados, promo1Obrigatoria)
        val promo1Sucos = lerInteiro(ConfiguracoesCampo.PROMO1_SUCOS, input.promo1Sucos, promo1Obrigatoria)
        val promo1Vista =
            lerCentavos(
                ConfiguracoesCampo.PROMO1_VISTA,
                input.promo1Vista,
                "Informe o valor à vista",
                promo1Obrigatoria,
            )
        val promo1Prazo =
            lerCentavos(
                ConfiguracoesCampo.PROMO1_PRAZO,
                input.promo1Prazo,
                "Informe o valor a prazo",
                promo1Obrigatoria,
            )
        val promo2Salgados = lerInteiro(ConfiguracoesCampo.PROMO2_SALGADOS, input.promo2Salgados, promo2Preenchida)
        val promo2Sucos = lerInteiro(ConfiguracoesCampo.PROMO2_SUCOS, input.promo2Sucos, promo2Preenchida)
        val promo2Vista =
            lerCentavos(ConfiguracoesCampo.PROMO2_VISTA, input.promo2Vista, "Informe o valor à vista", promo2Preenchida)
        val promo2Prazo =
            lerCentavos(ConfiguracoesCampo.PROMO2_PRAZO, input.promo2Prazo, "Informe o valor a prazo", promo2Preenchida)

        if (input.promocoesAtivadas && (promo1Salgados ?: 0) + (promo1Sucos ?: 0) <= 0) {
            erros.putIfAbsent(ConfiguracoesCampo.PROMO1_SALGADOS, "Informe ao menos um item")
        }
        if (promo2Preenchida && (promo2Salgados ?: 0) + (promo2Sucos ?: 0) <= 0) {
            erros.putIfAbsent(ConfiguracoesCampo.PROMO2_SALGADOS, "Informe ao menos um item")
        }

        if (erros.isNotEmpty()) return ConfiguracoesFormResult.Invalida(erros)

        return ConfiguracoesFormResult.Valida(
            Configuracoes(
                precoSalgadoVistaCentavos = requireNotNull(salgadoVista),
                precoSalgadoPrazoCentavos = requireNotNull(salgadoPrazo),
                precoSucoVistaCentavos = requireNotNull(sucoVista),
                precoSucoPrazoCentavos = requireNotNull(sucoPrazo),
                promocoesAtivadas = input.promocoesAtivadas,
                promo1Nome = promo1Nome,
                promo1Salgados = promo1Salgados ?: 0,
                promo1Sucos = promo1Sucos ?: 0,
                promo1VistaCentavos = promo1Vista ?: 0,
                promo1PrazoCentavos = promo1Prazo ?: 0,
                promo2Nome = promo2Nome,
                promo2Salgados = promo2Salgados ?: 0,
                promo2Sucos = promo2Sucos ?: 0,
                promo2VistaCentavos = promo2Vista ?: 0,
                promo2PrazoCentavos = promo2Prazo ?: 0,
            ),
        )
    }
}

object ConfiguracoesRules {
    fun isValid(configuracoes: Configuracoes): Boolean = validar(configuracoes).isEmpty()

    fun validar(configuracoes: Configuracoes): Map<ConfiguracoesCampo, String> =
        (
            ConfiguracoesFormValidator.validar(
                ConfiguracoesInput(
                    precoSalgadoVista = configuracoes.precoSalgadoVistaCentavos.toString(),
                    precoSalgadoPrazo = configuracoes.precoSalgadoPrazoCentavos.toString(),
                    precoSucoVista = configuracoes.precoSucoVistaCentavos.toString(),
                    precoSucoPrazo = configuracoes.precoSucoPrazoCentavos.toString(),
                    promocoesAtivadas = configuracoes.promocoesAtivadas,
                    promo1Nome = configuracoes.promo1Nome,
                    promo1Salgados = configuracoes.promo1Salgados.toString(),
                    promo1Sucos = configuracoes.promo1Sucos.toString(),
                    promo1Vista = configuracoes.promo1VistaCentavos.toString(),
                    promo1Prazo = configuracoes.promo1PrazoCentavos.toString(),
                    promo2Nome = configuracoes.promo2Nome,
                    promo2Salgados = configuracoes.promo2Salgados.toString(),
                    promo2Sucos = configuracoes.promo2Sucos.toString(),
                    promo2Vista = configuracoes.promo2VistaCentavos.toString(),
                    promo2Prazo = configuracoes.promo2PrazoCentavos.toString(),
                ),
            ) as? ConfiguracoesFormResult.Invalida
        )?.erros.orEmpty()
}
