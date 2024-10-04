package com.example.caderneta.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import com.example.caderneta.databinding.ViewCardBalancoBinding

class CardBalancoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

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
}