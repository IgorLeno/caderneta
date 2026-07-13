package com.example.caderneta.helpers

import android.os.SystemClock
import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.idling.CountingIdlingResource
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.platform.app.InstrumentationRegistry
import com.example.caderneta.util.EspressoIdlingResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.hamcrest.Matcher

object WaitConditions {
    private val countingIdlingResource = CountingIdlingResource("caderneta-async-work")
    private val backend =
        object : EspressoIdlingResource.Backend {
            override fun increment() {
                countingIdlingResource.increment()
            }

            override fun decrement() {
                if (!countingIdlingResource.isIdleNow) {
                    countingIdlingResource.decrement()
                }
            }
        }

    fun registerIdlingResource() {
        EspressoIdlingResource.installBackend(backend)
        IdlingRegistry.getInstance().register(countingIdlingResource)
    }

    fun unregisterIdlingResource() {
        IdlingRegistry.getInstance().unregister(countingIdlingResource)
        EspressoIdlingResource.installBackend(null)
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

    fun awaitView(
        matcher: Matcher<View>,
        timeoutMillis: Long = 5_000,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        var lastError: Throwable? = null

        while (SystemClock.uptimeMillis() <= deadline) {
            try {
                onView(matcher).check(matches(isDisplayed()))
                return
            } catch (error: NoMatchingViewException) {
                lastError = error
            } catch (error: AssertionError) {
                lastError = error
            }
            instrumentation.waitForIdleSync()
        }

        throw AssertionError("View did not become displayed within ${timeoutMillis}ms", lastError)
    }
}
