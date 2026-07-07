package com.example.caderneta.domain

import androidx.room.withTransaction
import com.example.caderneta.data.AppDatabase
import com.example.caderneta.data.entity.Conta
import com.example.caderneta.data.entity.ModoOperacao
import com.example.caderneta.data.entity.Operacao
import com.example.caderneta.data.entity.TipoTransacao
import com.example.caderneta.data.entity.TransacaoVenda
import com.example.caderneta.data.entity.Venda
import java.util.Date

/**
 * Única porta de escrita das operações financeiras. Cada comando roda em uma
 * transação Room: Operacao + Venda + Conta são gravados todos-ou-nenhum, o que
 * elimina os estados parciais (operação sem venda, saldo sem histórico etc.).
 *
 * Fonte de verdade do saldo: o livro de lançamentos (vendas/operacoes).
 * `Conta.saldoCentavos` é cache materializado, escrito SOMENTE aqui.
 *
 * Falhas de validação lançam IllegalArgumentException/IllegalStateException
 * com mensagem para o usuário — sempre ANTES de qualquer escrita.
 */
class FinanceiroService(
    private val db: AppDatabase,
) {
    private val vendaDao = db.vendaDao()
    private val operacaoDao = db.operacaoDao()
    private val contaDao = db.contaDao()

    @Suppress("LongParameterList")
    suspend fun registrarVenda(
        clienteId: Long,
        localId: Long,
        tipoTransacao: TipoTransacao,
        isPromocao: Boolean,
        quantidadeSalgados: Int,
        quantidadeSucos: Int,
        valorCentavos: Long,
        promocaoDetalhes: String?,
        data: Date = Date(),
    ): Venda {
        require(valorCentavos > 0) { "Valor da venda deve ser maior que zero" }
        require(quantidadeSalgados > 0 || quantidadeSucos > 0) {
            "Venda precisa de pelo menos um item"
        }
        return db.withTransaction {
            val operacaoId =
                operacaoDao.insertOperacao(
                    Operacao(
                        clienteId = clienteId,
                        tipoOperacao = if (isPromocao) ModoOperacao.PROMOCAO else ModoOperacao.VENDA,
                        valorCentavos = valorCentavos,
                        data = data,
                    ),
                )
            val venda =
                Venda(
                    operacaoId = operacaoId,
                    clienteId = clienteId,
                    localId = localId,
                    data = data,
                    transacao =
                        when (tipoTransacao) {
                            TipoTransacao.A_VISTA -> TransacaoVenda.A_VISTA
                            TipoTransacao.A_PRAZO -> TransacaoVenda.A_PRAZO
                        },
                    quantidadeSalgados = quantidadeSalgados,
                    quantidadeSucos = quantidadeSucos,
                    isPromocao = isPromocao,
                    promocaoDetalhes = promocaoDetalhes,
                    valorCentavos = valorCentavos,
                )
            val vendaId = vendaDao.insertVenda(venda)
            if (venda.transacao == TransacaoVenda.A_PRAZO) {
                ajustarSaldo(clienteId, valorCentavos)
            }
            venda.copy(id = vendaId)
        }
    }

    suspend fun registrarPagamento(
        clienteId: Long,
        localId: Long?,
        valorCentavos: Long,
        data: Date = Date(),
    ): Venda {
        require(valorCentavos > 0) { "Valor do pagamento deve ser maior que zero" }
        return db.withTransaction {
            // Validação dentro da transação: nenhuma escrita acontece se falhar.
            val conta =
                checkNotNull(contaDao.getContaByCliente(clienteId)) {
                    "Cliente não possui saldo devedor"
                }
            check(valorCentavos <= conta.saldoCentavos) {
                "Valor do pagamento maior que o saldo devedor"
            }
            val operacaoId =
                operacaoDao.insertOperacao(
                    Operacao(
                        clienteId = clienteId,
                        tipoOperacao = ModoOperacao.PAGAMENTO,
                        valorCentavos = valorCentavos,
                        data = data,
                    ),
                )
            val venda =
                Venda(
                    operacaoId = operacaoId,
                    clienteId = clienteId,
                    localId = localId,
                    data = data,
                    transacao = TransacaoVenda.PAGAMENTO,
                    quantidadeSalgados = 0,
                    quantidadeSucos = 0,
                    valorCentavos = valorCentavos,
                )
            val vendaId = vendaDao.insertVenda(venda)
            ajustarSaldo(clienteId, -valorCentavos)
            venda.copy(id = vendaId)
        }
    }

    /**
     * Substitui um lançamento pelo estado editado, ajustando o saldo apenas
     * pela diferença de efeito entre o original e o novo.
     */
    suspend fun editarOperacao(
        vendaOriginal: Venda,
        vendaEditada: Venda,
    ) {
        require(vendaOriginal.id == vendaEditada.id) { "Edição deve manter o mesmo lançamento" }
        require(vendaEditada.valorCentavos > 0) { "Valor deve ser maior que zero" }
        db.withTransaction {
            vendaDao.updateVenda(vendaEditada)
            operacaoDao.updateOperacao(
                Operacao(
                    id = vendaOriginal.operacaoId,
                    clienteId = vendaEditada.clienteId,
                    tipoOperacao =
                        when {
                            vendaEditada.transacao == TransacaoVenda.PAGAMENTO -> ModoOperacao.PAGAMENTO
                            vendaEditada.isPromocao -> ModoOperacao.PROMOCAO
                            else -> ModoOperacao.VENDA
                        },
                    valorCentavos = vendaEditada.valorCentavos,
                    data = vendaEditada.data,
                ),
            )
            val delta = efeitoNoSaldo(vendaEditada) - efeitoNoSaldo(vendaOriginal)
            if (delta != 0L) ajustarSaldo(vendaOriginal.clienteId, delta)
        }
    }

    /** Exclui o lançamento aplicando o inverso do seu efeito no saldo. */
    suspend fun excluirOperacao(venda: Venda) {
        db.withTransaction {
            vendaDao.deleteVenda(venda)
            operacaoDao.getOperacaoById(venda.operacaoId)?.let { operacaoDao.deleteOperacao(it) }
            val reverso = -efeitoNoSaldo(venda)
            if (reverso != 0L) ajustarSaldo(venda.clienteId, reverso)
        }
    }

    /** Corrige a data do lançamento (venda e operação juntas). */
    suspend fun atualizarDataOperacao(
        venda: Venda,
        novaData: Date,
    ) {
        db.withTransaction {
            vendaDao.updateVenda(venda.copy(data = novaData))
            operacaoDao.getOperacaoById(venda.operacaoId)?.let {
                operacaoDao.updateOperacao(it.copy(data = novaData))
            }
        }
    }

    /**
     * Efeito do lançamento no saldo devedor: venda a prazo aumenta, pagamento
     * diminui, venda à vista não altera.
     */
    private fun efeitoNoSaldo(venda: Venda): Long =
        when (venda.transacao) {
            TransacaoVenda.A_PRAZO -> venda.valorCentavos
            TransacaoVenda.PAGAMENTO -> -venda.valorCentavos
            TransacaoVenda.A_VISTA -> 0
        }

    /** Só pode ser chamado dentro de db.withTransaction. */
    private suspend fun ajustarSaldo(
        clienteId: Long,
        deltaCentavos: Long,
    ) {
        val conta = contaDao.getContaByCliente(clienteId)
        if (conta == null) {
            contaDao.insertConta(Conta(clienteId = clienteId, saldoCentavos = deltaCentavos))
        } else {
            contaDao.atualizarSaldo(clienteId, deltaCentavos)
        }
    }
}
