package com.example.caderneta.helpers

import androidx.test.espresso.IdlingRegistry
import com.example.caderneta.util.EspressoIdlingResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

object WaitConditions {
    fun registerIdlingResource() {
        IdlingRegistry.getInstance().register(EspressoIdlingResource.countingIdlingResource)
    }

    fun unregisterIdlingResource() {
        IdlingRegistry.getInstance().unregister(EspressoIdlingResource.countingIdlingResource)
    }

    fun awaitDb(
        timeoutMillis: Long = 5_000,
        predicate: suspend () -> Boolean,
    ) {
        runBlocking {
            withTimeout(timeoutMillis) {
                while (!predicate()) {
                    delay(50)
                }
            }
        }
    }
}
