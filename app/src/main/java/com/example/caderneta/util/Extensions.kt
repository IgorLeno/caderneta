package com.example.caderneta.util

import android.content.Context
import android.view.LayoutInflater
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.caderneta.R
import com.google.android.material.snackbar.Snackbar
import android.view.View

fun Context.showSuccessToast(message: String) {
    val activity = this as? androidx.fragment.app.FragmentActivity ?: run {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        return
    }
    val snackbar = Snackbar.make(
        activity.findViewById(android.R.id.content),
        message,
        Snackbar.LENGTH_LONG
    )
    snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.green))
    snackbar.show()
}

fun Context.showErrorToast(message: String) {
    val activity = this as? androidx.fragment.app.FragmentActivity ?: run {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        return
    }
    val snackbar = Snackbar.make(
        activity.findViewById(android.R.id.content),
        message,
        Snackbar.LENGTH_LONG
    )
    snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.red))
    snackbar.show()
}