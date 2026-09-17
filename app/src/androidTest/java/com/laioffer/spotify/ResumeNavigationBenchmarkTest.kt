package com.laioffer.spotify

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ResumeNavigationBenchmarkTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun visible(text: String) {
        compose.waitUntil(15000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    @Test fun repeatedThreeScreenNavigation() {
        visible("Still Fantasy")
        val transitions = JSONArray()
        repeat(20) { cycle ->
            compose.onNodeWithText("Still Fantasy").performClick()
            visible("Still Fantasy Demo")
            transitions.put(JSONObject().put("cycle", cycle + 1).put("route", "home_to_playlist").put("passed", true))
            onView(withId(R.id.favoriteFragment)).perform(click())
            visible("Hexagonal")
            transitions.put(JSONObject().put("cycle", cycle + 1).put("route", "playlist_to_favorites").put("passed", true))
            compose.onNodeWithText("Hexagonal").performClick()
            visible("Let's Meet Now Demo")
            transitions.put(JSONObject().put("cycle", cycle + 1).put("route", "favorites_to_playlist").put("passed", true))
            onView(withId(R.id.homeFragment)).perform(click())
            visible("Still Fantasy")
            transitions.put(JSONObject().put("cycle", cycle + 1).put("route", "playlist_to_home").put("passed", true))
        }
        File(compose.activity.filesDir, "resume-metrics/navigation.json").apply {
            parentFile!!.mkdirs()
            writeText(JSONObject().put("cycles", 20).put("transitions", transitions)
                .put("screen_types", 3).put("measurement", "actual Compose album clicks and Espresso bottom-navigation clicks; destination content asserted visible each time").toString(2))
        }
    }
}
