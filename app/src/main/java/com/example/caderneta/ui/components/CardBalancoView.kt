package com.example.caderneta.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import com.example.caderneta.databinding.ViewCardBalancoBinding
import com.google.android.material.card.MaterialCardView

class CardBalancoView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : MaterialCardView(context, attrs, defStyleAttr) {
        private val binding: ViewCardBalancoBinding

        init {
            val inflater = LayoutInflater.from(context)
            binding = ViewCardBalancoBinding.inflate(inflater, this)
        }

        fun setTitulo(titulo: String) {
            binding.tvTitulo.text = titulo
        }

        fun setTotalVendas(total: String) {
            binding.tvTotalVendas.text = total
        }

        fun setTotalRecebimentos(total: String) {
            binding.tvTotalRecebimentos.text = total
        }

        fun setQuantidadeOperacoes(texto: String) {
            binding.tvQuantidadeOperacoes.text = texto
        }

        fun setVazio(vazio: Boolean) {
            binding.layoutDados.visibility = if (vazio) View.GONE else View.VISIBLE
            binding.tvVazio.visibility = if (vazio) View.VISIBLE else View.GONE
        }
    }
