package com.example.caderneta.data.backup

import com.example.caderneta.data.entity.TransacaoVenda

class BackupValidator(
    private val applicationId: String,
) {
    fun validar(snapshot: BackupSnapshot) {
        require(snapshot.formatVersion == 1) { "Versão de backup não suportada" }
        require(snapshot.app == applicationId) { "Backup pertence a outro aplicativo" }
        require(snapshot.configuracoes.size <= 1) { "Backup possui mais de uma configuração" }

        val localIds = snapshot.locais.map { it.id }.toSet()
        val clienteIds = snapshot.clientes.map { it.id }.toSet()
        val operacaoIds = snapshot.operacoes.map { it.id }.toSet()

        snapshot.locais.forEach { local ->
            require(local.id > 0) { "Local inválido" }
            require(local.level >= 0) { "Nível de local inválido" }
            local.parentId?.let { parentId ->
                require(parentId in localIds) { "Local pai inexistente" }
            }
        }

        snapshot.clientes.forEach { cliente ->
            require(cliente.id > 0) { "Cliente inválido" }
            require(cliente.localId in localIds) { "Cliente com local inexistente" }
            listOf(cliente.sublocal1Id, cliente.sublocal2Id, cliente.sublocal3Id).forEach { localId ->
                localId?.let { require(it in localIds) { "Cliente com sublocal inexistente" } }
            }
        }

        snapshot.operacoes.forEach { operacao ->
            require(operacao.id > 0) { "Operação inválida" }
            require(operacao.clienteId in clienteIds) { "Operação com cliente inexistente" }
            require(operacao.valorCentavos >= 0) { "Operação com valor negativo" }
        }

        snapshot.vendas.forEach { venda ->
            require(venda.id > 0) { "Lançamento inválido" }
            require(venda.operacaoId in operacaoIds) { "Lançamento com operação inexistente" }
            require(venda.clienteId in clienteIds) { "Lançamento com cliente inexistente" }
            venda.localId?.let { require(it in localIds) { "Lançamento com local inexistente" } }
            require(venda.valorCentavos >= 0) { "Lançamento com valor negativo" }
            require(venda.quantidadeSalgados >= 0 && venda.quantidadeSucos >= 0) { "Quantidade inválida" }
            if (venda.transacao == TransacaoVenda.PAGAMENTO) {
                require(venda.quantidadeSalgados == 0 && venda.quantidadeSucos == 0) { "Pagamento com itens" }
            }
        }

        snapshot.contas.forEach { conta ->
            require(conta.clienteId in clienteIds) { "Conta com cliente inexistente" }
            require(conta.saldoCentavos >= 0) { "Conta com saldo negativo" }
        }

        snapshot.configuracoes.forEach { config ->
            require(config.isValid()) { "Configurações inválidas" }
        }
    }
}
