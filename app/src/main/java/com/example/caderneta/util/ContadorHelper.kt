package com.example.caderneta.util

import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import com.example.caderneta.R

class ContadorHelper(view: View) {
    private val btnMenos = view.findViewById<ImageButton>(R.id.btn_menos)
    private val btnMais = view.findViewById<ImageButton>(R.id.btn_mais)
    private val tvQuantidade = view.findViewById<TextView>(R.id.tv_quantidade)
    private var quantidade = 0
    private var onQuantidadeChangedListener: ((Int) -> Unit)? = null

    init {
        tvQuantidade.text = "0"
        setupListeners()
    }

    private fun setupListeners() {
        btnMais.setOnClickListener {
            setQuantidade(quantidade + 1)
        }

        btnMenos.setOnClickListener {
            if (quantidade > 0) {
                setQuantidade(quantidade - 1)
            }
        }
    }

    fun setQuantidade(novaQuantidade: Int) {
        quantidade = novaQuantidade.coerceAtLeast(0)
        tvQuantidade.text = quantidade.toString()
        onQuantidadeChangedListener?.invoke(quantidade)
    }

    fun getQuantidade(): Int = quantidade

    fun setOnQuantidadeChangedListener(listener: (Int) -> Unit) {
        onQuantidadeChangedListener = listener
    }

    fun reset() {
        setQuantidade(0)
    }
}