package com.example.caderneta.data.backup

import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.Local
import com.example.caderneta.data.entity.ModoOperacao
import com.example.caderneta.data.entity.Operacao
import com.example.caderneta.data.entity.TransacaoVenda
import com.example.caderneta.data.entity.Venda

class BackupValidator(
    private val applicationId: String,
) {
    fun validar(snapshot: BackupSnapshot) {
        require(snapshot.formatVersion == 1) { "Versão de backup não suportada" }
        require(snapshot.dbVersion == 1) { "Versão de banco não suportada" }
        require(snapshot.app == applicationId) { "Backup pertence a outro aplicativo" }
        require(snapshot.configuracoes.size <= 1) { "Backup possui mais de uma configuração" }

        requireSemDuplicados(snapshot.locais.map { it.id }, "Local duplicado")
        requireSemDuplicados(snapshot.clientes.map { it.id }, "Cliente duplicado")
        requireSemDuplicados(snapshot.operacoes.map { it.id }, "Operação duplicada")
        requireSemDuplicados(snapshot.vendas.map { it.id }, "Lançamento duplicado")
        requireSemDuplicados(snapshot.contas.map { it.clienteId }, "Conta duplicada")
        requireSemDuplicados(snapshot.vendas.map { it.operacaoId }, "Operação vinculada a mais de um lançamento")

        val localIds = snapshot.locais.map { it.id }.toSet()
        val clienteIds = snapshot.clientes.map { it.id }.toSet()
        val operacaoIds = snapshot.operacoes.map { it.id }.toSet()
        val locaisPorId = snapshot.locais.associateBy { it.id }
        val operacoesPorId = snapshot.operacoes.associateBy { it.id }
        val vendasPorOperacaoId = snapshot.vendas.associateBy { it.operacaoId }

        validarLocais(snapshot, locaisPorId)
        validarClientes(snapshot, locaisPorId)
        validarOperacoes(snapshot, clienteIds, vendasPorOperacaoId)
        validarVendas(snapshot, localIds, clienteIds, operacaoIds, operacoesPorId)
        validarContas(snapshot, clienteIds)
        validarSaldosReconstruidos(snapshot)
        validarConfiguracoes(snapshot)
    }

    private fun validarLocais(
        snapshot: BackupSnapshot,
        locaisPorId: Map<Long, Local>,
    ) {
        snapshot.locais.forEach { local ->
            require(local.id > 0) { "Local inválido" }
            if (local.parentId == null) {
                require(local.level == 0) { "Local raiz com nível inválido" }
            } else {
                val parent =
                    requireNotNull(locaisPorId[local.parentId]) {
                        "Local pai inexistente"
                    }
                require(local.level == parent.level + 1) { "Nível de local incompatível com o pai" }
                require(!parent.arquivado || local.arquivado) { "Local ativo sob local arquivado" }
            }
        }
        validarSemCiclos(locaisPorId)
    }

    private fun validarClientes(
        snapshot: BackupSnapshot,
        locaisPorId: Map<Long, Local>,
    ) {
        snapshot.clientes.forEach { cliente ->
            require(cliente.id > 0) { "Cliente inválido" }
            val cadeia = validarCadeiaCliente(cliente, locaisPorId)
            if (!cliente.arquivado) {
                require(cadeia.none { it.arquivado }) { "Cliente ativo associado a local arquivado" }
            }
        }
    }

    private fun validarCadeiaCliente(
        cliente: Cliente,
        locaisPorId: Map<Long, Local>,
    ): List<Local> {
        val local =
            requireNotNull(locaisPorId[cliente.localId]) {
                "Cliente com local inexistente"
            }
        require(local.parentId == null) { "Local principal do cliente não é raiz da cadeia" }

        val ids =
            listOfNotNull(
                cliente.localId,
                cliente.sublocal1Id,
                cliente.sublocal2Id,
                cliente.sublocal3Id,
            )
        require(ids.size == ids.toSet().size) { "Cliente com local repetido na hierarquia" }

        val cadeia = mutableListOf(local)

        cliente.sublocal1Id?.let { sublocal1Id ->
            val sublocal1 =
                requireNotNull(locaisPorId[sublocal1Id]) {
                    "Cliente com sublocal inexistente"
                }
            require(sublocal1.parentId == cliente.localId) { "Sublocal 1 incompatível com local principal" }
            cadeia += sublocal1
        }

        cliente.sublocal2Id?.let { sublocal2Id ->
            val sublocal1Id =
                requireNotNull(cliente.sublocal1Id) {
                    "Cliente com sublocal 2 sem sublocal 1"
                }
            val sublocal2 =
                requireNotNull(locaisPorId[sublocal2Id]) {
                    "Cliente com sublocal inexistente"
                }
            require(sublocal2.parentId == sublocal1Id) { "Sublocal 2 incompatível com sublocal 1" }
            cadeia += sublocal2
        }

        cliente.sublocal3Id?.let { sublocal3Id ->
            val sublocal2Id =
                requireNotNull(cliente.sublocal2Id) {
                    "Cliente com sublocal 3 sem sublocal 2"
                }
            val sublocal3 =
                requireNotNull(locaisPorId[sublocal3Id]) {
                    "Cliente com sublocal inexistente"
                }
            require(sublocal3.parentId == sublocal2Id) { "Sublocal 3 incompatível com sublocal 2" }
            cadeia += sublocal3
        }

        return cadeia
    }

    private fun validarOperacoes(
        snapshot: BackupSnapshot,
        clienteIds: Set<Long>,
        vendasPorOperacaoId: Map<Long, Venda>,
    ) {
        snapshot.operacoes.forEach { operacao ->
            require(operacao.id > 0) { "Operação inválida" }
            require(operacao.clienteId in clienteIds) { "Operação com cliente inexistente" }
            require(operacao.valorCentavos >= 0) { "Operação com valor negativo" }
            require(operacao.id in vendasPorOperacaoId) { "Operação sem lançamento correspondente" }
        }
    }

    private fun validarVendas(
        snapshot: BackupSnapshot,
        localIds: Set<Long>,
        clienteIds: Set<Long>,
        operacaoIds: Set<Long>,
        operacoesPorId: Map<Long, Operacao>,
    ) {
        snapshot.vendas.forEach { venda ->
            require(venda.id > 0) { "Lançamento inválido" }
            require(venda.operacaoId in operacaoIds) { "Lançamento com operação inexistente" }
            require(venda.clienteId in clienteIds) { "Lançamento com cliente inexistente" }
            venda.localId?.let { require(it in localIds) { "Lançamento com local inexistente" } }
            require(venda.valorCentavos >= 0) { "Lançamento com valor negativo" }
            require(venda.quantidadeSalgados >= 0 && venda.quantidadeSucos >= 0) { "Quantidade inválida" }
            validarVendaOperacao(venda, requireNotNull(operacoesPorId[venda.operacaoId]))
        }
    }

    private fun validarContas(
        snapshot: BackupSnapshot,
        clienteIds: Set<Long>,
    ) {
        snapshot.contas.forEach { conta ->
            require(conta.clienteId in clienteIds) { "Conta com cliente inexistente" }
            require(conta.saldoCentavos >= 0) { "Conta com saldo negativo" }
        }
    }

    private fun validarSaldosReconstruidos(snapshot: BackupSnapshot) {
        val saldos = snapshot.clientes.associate { it.id to 0L }.toMutableMap()
        snapshot.vendas.forEach { venda ->
            val delta =
                when (venda.transacao) {
                    TransacaoVenda.A_PRAZO -> venda.valorCentavos
                    TransacaoVenda.PAGAMENTO -> -venda.valorCentavos
                    TransacaoVenda.A_VISTA -> 0L
                }
            saldos[venda.clienteId] = (saldos[venda.clienteId] ?: 0L) + delta
        }
        require(saldos.values.all { it >= 0L }) { "Backup geraria saldo negativo" }
    }

    private fun validarConfiguracoes(snapshot: BackupSnapshot) {
        snapshot.configuracoes.forEach { config ->
            require(config.id == 1) { "Configurações com ID inválido" }
            require(config.isValid()) { "Configurações inválidas" }
        }
    }

    private fun requireSemDuplicados(
        ids: List<Long>,
        mensagem: String,
    ) {
        require(ids.size == ids.toSet().size) { mensagem }
    }

    private fun validarSemCiclos(locaisPorId: Map<Long, Local>) {
        locaisPorId.values.forEach { origem ->
            val visitados = mutableSetOf<Long>()
            var atual = origem.parentId
            while (atual != null) {
                require(visitados.add(atual)) { "Ciclo na hierarquia de locais" }
                atual = locaisPorId[atual]?.parentId
            }
        }
    }

    private fun validarVendaOperacao(
        venda: Venda,
        operacao: Operacao,
    ) {
        require(venda.clienteId == operacao.clienteId) { "Lançamento e operação de clientes diferentes" }
        require(venda.valorCentavos == operacao.valorCentavos) { "Lançamento e operação com valores diferentes" }
        require(venda.data.time == operacao.data.time) { "Lançamento e operação com datas diferentes" }
        when (operacao.tipoOperacao) {
            ModoOperacao.PAGAMENTO -> {
                require(venda.transacao == TransacaoVenda.PAGAMENTO) { "Pagamento com tipo de lançamento inválido" }
                require(!venda.isPromocao) { "Pagamento marcado como promoção" }
                require(venda.quantidadeSalgados == 0 && venda.quantidadeSucos == 0) { "Pagamento com itens" }
            }
            ModoOperacao.VENDA -> {
                require(venda.transacao != TransacaoVenda.PAGAMENTO) { "Venda registrada como pagamento" }
                require(!venda.isPromocao) { "Venda normal marcada como promoção" }
            }
            ModoOperacao.PROMOCAO -> {
                require(venda.transacao != TransacaoVenda.PAGAMENTO) { "Promoção registrada como pagamento" }
                require(venda.isPromocao) { "Promoção sem marcador promocional" }
            }
        }
    }
}
