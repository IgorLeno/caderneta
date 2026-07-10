package com.example.caderneta.helpers

import android.view.View
import androidx.annotation.IdRes
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.example.caderneta.R
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.Matcher

fun clickRecyclerItem(
    @IdRes recyclerViewId: Int,
    position: Int,
) {
    onView(withId(recyclerViewId)).perform(actionOnItemAtPosition<RecyclerView.ViewHolder>(position, click()))
}

fun longClickRecyclerItem(
    @IdRes recyclerViewId: Int,
    position: Int,
) {
    onView(withId(recyclerViewId)).perform(actionOnItemAtPosition<RecyclerView.ViewHolder>(position, longClickAction()))
}

fun clickRecyclerChild(
    @IdRes recyclerViewId: Int,
    position: Int,
    @IdRes childId: Int,
) {
    onView(withId(recyclerViewId)).perform(
        actionOnItemAtPosition<RecyclerView.ViewHolder>(
            position,
            childClickAction(childId),
        ),
    )
}

fun tapRecyclerCounterPlus(
    @IdRes recyclerViewId: Int,
    position: Int,
    @IdRes counterId: Int,
    times: Int = 1,
) {
    repeat(times) {
        onView(withId(recyclerViewId)).perform(
            actionOnItemAtPosition<RecyclerView.ViewHolder>(
                position,
                counterPlusAction(counterId),
            ),
        )
    }
}

fun replaceRecyclerChildText(
    @IdRes recyclerViewId: Int,
    position: Int,
    @IdRes childId: Int,
    value: String,
) {
    onView(withId(recyclerViewId)).perform(
        actionOnItemAtPosition<RecyclerView.ViewHolder>(
            position,
            childReplaceTextAction(childId, value),
        ),
    )
}

fun tapCounterPlus(
    @IdRes counterId: Int,
    times: Int = 1,
) {
    repeat(times) {
        onView(allOf(withId(R.id.btn_mais), isDescendantOfA(withId(counterId)))).perform(click())
    }
}

private fun longClickAction(): ViewAction =
    object : ViewAction {
        override fun getConstraints(): Matcher<View> = isAssignableFrom(View::class.java)

        override fun getDescription(): String = "perform long click on RecyclerView item"

        override fun perform(
            uiController: UiController,
            view: View,
        ) {
            view.performLongClick()
            uiController.loopMainThreadUntilIdle()
        }
    }

private fun childClickAction(
    @IdRes childId: Int,
): ViewAction =
    object : ViewAction {
        override fun getConstraints(): Matcher<View> = isAssignableFrom(View::class.java)

        override fun getDescription(): String = "click child $childId in RecyclerView item"

        override fun perform(
            uiController: UiController,
            view: View,
        ) {
            view.findViewById<View>(childId).performClick()
            uiController.loopMainThreadUntilIdle()
        }
    }

private fun childReplaceTextAction(
    @IdRes childId: Int,
    value: String,
): ViewAction =
    object : ViewAction {
        override fun getConstraints(): Matcher<View> = isAssignableFrom(View::class.java)

        override fun getDescription(): String = "replace text in child $childId"

        override fun perform(
            uiController: UiController,
            view: View,
        ) {
            val child = view.findViewById<android.widget.TextView>(childId)
            child.text = value
            uiController.loopMainThreadUntilIdle()
        }
    }

private fun counterPlusAction(
    @IdRes counterId: Int,
): ViewAction =
    object : ViewAction {
        override fun getConstraints(): Matcher<View> = isAssignableFrom(View::class.java)

        override fun getDescription(): String = "tap plus in counter $counterId"

        override fun perform(
            uiController: UiController,
            view: View,
        ) {
            val counter = view.findViewById<View>(counterId)
            counter.findViewById<View>(R.id.btn_mais).performClick()
            uiController.loopMainThreadUntilIdle()
        }
    }
