package com.example.caderneta.data.entity

/**
 * Tipo de registro na tabela `vendas`. Além das vendas propriamente ditas
 * (à vista/a prazo), pagamentos de dívida também são registrados nessa tabela
 * como lançamentos do extrato — por isso PAGAMENTO existe aqui.
 * Persistido pelo Room como TEXT com o nome da constante.
 */
enum class TransacaoVenda {
    A_VISTA,
    A_PRAZO,
    PAGAMENTO,
}
