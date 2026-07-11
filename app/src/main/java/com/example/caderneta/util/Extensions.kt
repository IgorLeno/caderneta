package com.example.caderneta.util

fun Throwable.rethrowCancellation() {
    if (this is kotlinx.coroutines.CancellationException) throw this
}
