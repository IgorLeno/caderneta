package com.example.caderneta.data.backup

import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.Configuracoes
import com.example.caderneta.data.entity.Conta
import com.example.caderneta.data.entity.Local
import com.example.caderneta.data.entity.ModoOperacao
import com.example.caderneta.data.entity.Operacao
import com.example.caderneta.data.entity.TransacaoVenda
import com.example.caderneta.data.entity.Venda
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

@Suppress("TooManyFunctions")
class BackupSerializer {
    fun toJson(snapshot: BackupSnapshot): String =
        JSONObject()
            .put("formatVersion", snapshot.formatVersion)
            .put("dbVersion", snapshot.dbVersion)
            .put("app", snapshot.app)
            .put("geradoEmMillis", snapshot.geradoEmMillis)
            .put("locais", JSONArray(snapshot.locais.map(::localJson)))
            .put("clientes", JSONArray(snapshot.clientes.map(::clienteJson)))
            .put("operacoes", JSONArray(snapshot.operacoes.map(::operacaoJson)))
            .put("vendas", JSONArray(snapshot.vendas.map(::vendaJson)))
            .put("contas", JSONArray(snapshot.contas.map(::contaJson)))
            .put("configuracoes", JSONArray(snapshot.configuracoes.map(::configJson)))
            .toString(2)

    fun fromJson(json: String): BackupSnapshot {
        val root = JSONObject(json)
        return BackupSnapshot(
            formatVersion = root.getInt("formatVersion"),
            dbVersion = root.getInt("dbVersion"),
            app = root.getString("app"),
            geradoEmMillis = root.getLong("geradoEmMillis"),
            locais = root.getJSONArray("locais").mapObjects(::localFromJson),
            clientes = root.getJSONArray("clientes").mapObjects(::clienteFromJson),
            operacoes = root.getJSONArray("operacoes").mapObjects(::operacaoFromJson),
            vendas = root.getJSONArray("vendas").mapObjects(::vendaFromJson),
            contas = root.getJSONArray("contas").mapObjects(::contaFromJson),
            configuracoes = root.getJSONArray("configuracoes").mapObjects(::configFromJson),
        )
    }

    private fun localJson(local: Local) =
        JSONObject()
            .put("id", local.id)
            .put("nome", local.nome)
            .putOpt("endereco", local.endereco)
            .putOpt("parentId", local.parentId)
            .put("level", local.level)
            .put("arquivado", local.arquivado)

    private fun localFromJson(json: JSONObject) =
        Local(
            id = json.getLong("id"),
            nome = json.getString("nome"),
            endereco = json.optStringOrNull("endereco"),
            parentId = json.optLongOrNull("parentId"),
            level = json.getInt("level"),
            arquivado = json.getBoolean("arquivado"),
        )

    private fun clienteJson(cliente: Cliente) =
        JSONObject()
            .put("id", cliente.id)
            .put("nome", cliente.nome)
            .putOpt("telefone", cliente.telefone)
            .put("localId", cliente.localId)
            .putOpt("sublocal1Id", cliente.sublocal1Id)
            .putOpt("sublocal2Id", cliente.sublocal2Id)
            .putOpt("sublocal3Id", cliente.sublocal3Id)
            .put("arquivado", cliente.arquivado)

    private fun clienteFromJson(json: JSONObject) =
        Cliente(
            id = json.getLong("id"),
            nome = json.getString("nome"),
            telefone = json.optStringOrNull("telefone"),
            localId = json.getLong("localId"),
            sublocal1Id = json.optLongOrNull("sublocal1Id"),
            sublocal2Id = json.optLongOrNull("sublocal2Id"),
            sublocal3Id = json.optLongOrNull("sublocal3Id"),
            arquivado = json.getBoolean("arquivado"),
        )

    private fun operacaoJson(operacao: Operacao) =
        JSONObject()
            .put("id", operacao.id)
            .put("clienteId", operacao.clienteId)
            .put("tipoOperacao", operacao.tipoOperacao.name)
            .put("valorCentavos", operacao.valorCentavos)
            .put("data", operacao.data.time)

    private fun operacaoFromJson(json: JSONObject) =
        Operacao(
            id = json.getLong("id"),
            clienteId = json.getLong("clienteId"),
            tipoOperacao = ModoOperacao.valueOf(json.getString("tipoOperacao")),
            valorCentavos = json.getLong("valorCentavos"),
            data = Date(json.getLong("data")),
        )

    private fun vendaJson(venda: Venda) =
        JSONObject()
            .put("id", venda.id)
            .put("operacaoId", venda.operacaoId)
            .put("clienteId", venda.clienteId)
            .putOpt("localId", venda.localId)
            .put("data", venda.data.time)
            .put("transacao", venda.transacao.name)
            .put("quantidadeSalgados", venda.quantidadeSalgados)
            .put("quantidadeSucos", venda.quantidadeSucos)
            .put("isPromocao", venda.isPromocao)
            .putOpt("promocaoDetalhes", venda.promocaoDetalhes)
            .put("valorCentavos", venda.valorCentavos)

    private fun vendaFromJson(json: JSONObject) =
        Venda(
            id = json.getLong("id"),
            operacaoId = json.getLong("operacaoId"),
            clienteId = json.getLong("clienteId"),
            localId = json.optLongOrNull("localId"),
            data = Date(json.getLong("data")),
            transacao = TransacaoVenda.valueOf(json.getString("transacao")),
            quantidadeSalgados = json.getInt("quantidadeSalgados"),
            quantidadeSucos = json.getInt("quantidadeSucos"),
            isPromocao = json.getBoolean("isPromocao"),
            promocaoDetalhes = json.optStringOrNull("promocaoDetalhes"),
            valorCentavos = json.getLong("valorCentavos"),
        )

    private fun contaJson(conta: Conta) =
        JSONObject().put("clienteId", conta.clienteId).put("saldoCentavos", conta.saldoCentavos)

    private fun contaFromJson(json: JSONObject) =
        Conta(clienteId = json.getLong("clienteId"), saldoCentavos = json.getLong("saldoCentavos"))

    private fun configJson(config: Configuracoes) =
        JSONObject()
            .put("id", config.id)
            .put("precoSalgadoVistaCentavos", config.precoSalgadoVistaCentavos)
            .put("precoSalgadoPrazoCentavos", config.precoSalgadoPrazoCentavos)
            .put("precoSucoVistaCentavos", config.precoSucoVistaCentavos)
            .put("precoSucoPrazoCentavos", config.precoSucoPrazoCentavos)
            .put("promocoesAtivadas", config.promocoesAtivadas)
            .put("promo1Nome", config.promo1Nome)
            .put("promo1Salgados", config.promo1Salgados)
            .put("promo1Sucos", config.promo1Sucos)
            .put("promo1VistaCentavos", config.promo1VistaCentavos)
            .put("promo1PrazoCentavos", config.promo1PrazoCentavos)
            .put("promo2Nome", config.promo2Nome)
            .put("promo2Salgados", config.promo2Salgados)
            .put("promo2Sucos", config.promo2Sucos)
            .put("promo2VistaCentavos", config.promo2VistaCentavos)
            .put("promo2PrazoCentavos", config.promo2PrazoCentavos)

    private fun configFromJson(json: JSONObject) =
        Configuracoes(
            id = json.getInt("id"),
            precoSalgadoVistaCentavos = json.getLong("precoSalgadoVistaCentavos"),
            precoSalgadoPrazoCentavos = json.getLong("precoSalgadoPrazoCentavos"),
            precoSucoVistaCentavos = json.getLong("precoSucoVistaCentavos"),
            precoSucoPrazoCentavos = json.getLong("precoSucoPrazoCentavos"),
            promocoesAtivadas = json.getBoolean("promocoesAtivadas"),
            promo1Nome = json.getString("promo1Nome"),
            promo1Salgados = json.getInt("promo1Salgados"),
            promo1Sucos = json.getInt("promo1Sucos"),
            promo1VistaCentavos = json.getLong("promo1VistaCentavos"),
            promo1PrazoCentavos = json.getLong("promo1PrazoCentavos"),
            promo2Nome = json.getString("promo2Nome"),
            promo2Salgados = json.getInt("promo2Salgados"),
            promo2Sucos = json.getInt("promo2Sucos"),
            promo2VistaCentavos = json.getLong("promo2VistaCentavos"),
            promo2PrazoCentavos = json.getLong("promo2PrazoCentavos"),
        )

    private fun <T> JSONArray.mapObjects(mapper: (JSONObject) -> T): List<T> =
        (0 until length()).map { index -> mapper(getJSONObject(index)) }

    private fun JSONObject.optLongOrNull(name: String): Long? = if (isNull(name)) null else getLong(name)

    private fun JSONObject.optStringOrNull(name: String): String? = if (isNull(name)) null else getString(name)
}
