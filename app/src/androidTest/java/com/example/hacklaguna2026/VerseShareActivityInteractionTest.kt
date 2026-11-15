package com.example.hacklaguna2026

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VerseShareActivityInteractionTest {
    @Test
    fun expressionPaletteAndBackgroundUpdateWithoutLosingTheVersePreview() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, VerseShareActivity::class.java).apply {
            putExtra("ciel.share.verse", "The LORD is my shepherd; I shall not want.")
            putExtra("ciel.share.reference", "Psalms 23:1")
            putExtra("ciel.share.theme", "Trust")
            putExtra("ciel.share.reflection", "The shepherd image frames the psalm's trust in God's care.")
            putExtra("ciel.share.expression", "happy")
        }
        ActivityScenario.launch<VerseShareActivity>(intent).use {
            onView(withText("Share a verse")).check(matches(isDisplayed()))
            onView(withText("Worried")).perform(click())
            onView(withText("Nervous or concerned")).check(matches(isDisplayed()))
            onView(withText("Cream")).perform(scrollTo(), click())
            onView(withText("Nervous or concerned")).check(matches(isDisplayed()))
            onView(withText("Share image")).perform(scrollTo()).check(matches(isDisplayed()))
        }
    }
}
