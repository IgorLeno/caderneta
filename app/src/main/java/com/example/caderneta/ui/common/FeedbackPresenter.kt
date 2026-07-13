package com.example.caderneta.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.core.content.ContextCompat
import com.example.caderneta.R
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar

/**
 * Ponto único de feedback transitório (Snackbar) do app. Centraliza para
 * garantir duas coisas que os call sites espalhados não garantiam sozinhos:
 * a mensagem anterior é descartada antes de mostrar a próxima (evita
 * empilhar/mensagem obsoleta ao trocar de tela rápido) e o Snackbar é
 * ancorado acima da bottom navigation (nunca cobre os ícones de navegação).
 */
object FeedbackPresenter {
    private var snackbarAtual: Snackbar? = null

    fun sucesso(
        view: View,
        mensagem: String,
    ) = mostrar(view, mensagem, Snackbar.LENGTH_SHORT, R.color.green)

    fun erro(
        view: View,
        mensagem: String,
    ) = mostrar(view, mensagem, Snackbar.LENGTH_LONG, R.color.red)

    private fun mostrar(
        view: View,
        mensagem: String,
        duracaoMs: Int,
        corFundo: Int,
    ) {
        if (!view.isAttachedToWindow) return
        snackbarAtual?.dismiss()
        val snackbar = Snackbar.make(view, mensagem, duracaoMs)
        snackbar.setBackgroundTint(ContextCompat.getColor(view.context, corFundo))
        bottomNavigationDe(view)?.let { snackbar.anchorView = it }
        snackbar.addCallback(
            object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                override fun onDismissed(
                    transientBottomBar: Snackbar?,
                    event: Int,
                ) {
                    if (snackbarAtual === transientBottomBar) {
                        snackbarAtual = null
                    }
                }
            },
        )
        snackbarAtual = snackbar
        snackbar.show()
    }

    private fun bottomNavigationDe(view: View): View? =
        view.context.findActivity()?.findViewById(R.id.bottom_navigation)

    private tailrec fun Context.findActivity(): Activity? =
        when (this) {
            is Activity -> this
            is ContextWrapper -> baseContext.findActivity()
            else -> null
        }
}
