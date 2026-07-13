package com.example.caderneta.util

object EspressoIdlingResource {
    interface Backend {
        fun increment()

        fun decrement()
    }

    private object NoOpBackend : Backend {
        override fun increment() = Unit

        override fun decrement() = Unit
    }

    @Volatile
    private var backend: Backend = NoOpBackend

    fun installBackend(backend: Backend?) {
        this.backend = backend ?: NoOpBackend
    }

    fun increment() {
        backend.increment()
    }

    fun decrement() {
        backend.decrement()
    }
}
