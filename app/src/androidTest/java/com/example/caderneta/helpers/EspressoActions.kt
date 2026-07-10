package com.example.caderneta.helpers

import android.os.IBinder
import android.view.WindowManager
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Root
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher

fun fillField(
    id: Int,
    value: String,
) {
    onView(withId(id)).perform(scrollTo(), clearText(), replaceText(value), closeSoftKeyboard())
}

fun fillVisibleField(
    id: Int,
    value: String,
) {
    onView(withId(id)).perform(clearText(), replaceText(value), closeSoftKeyboard())
}

fun clickTextInputEndIcon(id: Int) {
    onView(
        allOf(
            withId(com.google.android.material.R.id.text_input_end_icon),
            isDescendantOfA(withId(id)),
            isDisplayed(),
        ),
    ).perform(
        androidx.test.espresso.action.ViewActions
            .click(),
    )
}

fun assertToast(text: String) {
    onView(withText(text)).inRoot(ToastMatcher()).check(matches(withText(text)))
}

class ToastMatcher : TypeSafeMatcher<Root>() {
    override fun describeTo(description: Description) {
        description.appendText("is toast")
    }

    override fun matchesSafely(root: Root): Boolean {
        val type = root.windowLayoutParams.get().type
        if (type != WindowManager.LayoutParams.TYPE_TOAST) return false
        val windowToken: IBinder = root.decorView.windowToken
        val appToken: IBinder = root.decorView.applicationWindowToken
        return windowToken === appToken
    }
}

fun snackbarMatcher(text: String): Matcher<android.view.View> = withText(text)
