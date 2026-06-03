package com.example

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToLog
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Before
  fun setUp() {
    ShadowLog.stream = System.out
  }

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SK Services", appName)
  }

  @Test
  fun `launch MainActivity and switch to Admin Mode should not crash`() {
    // Verify our secure landing portal loads, and we can switch tabs between Hub and Admin entrance
    composeTestRule.onNodeWithTag("portal_tab_admin").assertExists().performClick()
    composeTestRule.waitForIdle()
    
    composeTestRule.onNodeWithTag("portal_tab_customer").assertExists().performClick()
    composeTestRule.waitForIdle()
  }

  @Test
  fun `login and register as Customer and render CustomerBookingScreen`() {
    try {
      // Toggle sign up mode
      composeTestRule.onNodeWithTag("portal_toggle_signup").assertExists().performScrollTo().performClick()
      composeTestRule.waitForIdle()

      // Enter registration fields
      composeTestRule.onNodeWithTag("signup_name_input").assertExists().performScrollTo().performTextInput("Pavan Kranthi")
      composeTestRule.onNodeWithTag("signup_email_input").assertExists().performScrollTo().performTextInput("customer@gmail.com")
      composeTestRule.onNodeWithTag("signup_phone_input").assertExists().performScrollTo().performTextInput("9998887776")
      composeTestRule.onNodeWithTag("signup_password_input").assertExists().performScrollTo().performTextInput("Password123")
      composeTestRule.waitForIdle()

      // Complete sign up and login
      composeTestRule.onNodeWithTag("signup_submit").assertExists().performScrollTo().performClick()
      composeTestRule.waitForIdle()

      // Wait until the registration completes and UI updates to CustomerBookingScreen
      composeTestRule.waitUntil(timeoutMillis = 5000) {
        composeTestRule.onAllNodesWithTag("tab_book_service").fetchSemanticsNodes().isNotEmpty()
      }
      composeTestRule.onNodeWithTag("tab_book_service").assertExists()
      
      // It should log in and show customer service booking screen components
      composeTestRule.onNodeWithTag("cat_AC").assertExists()
    } catch (e: AssertionError) {
      composeTestRule.onRoot().printToLog("RobolectricTest")
      throw e
    }
  }

  @Test
  fun `login as Admin and render AdminDashboardScreen`() {
    // Switch to Admin tab
    composeTestRule.onNodeWithTag("portal_tab_admin").assertExists().performClick()
    composeTestRule.waitForIdle()

    // Email and Password inputs should exist and can perform input
    composeTestRule.onNodeWithTag("admin_email_input").assertExists().performTextInput("admin@gmail.com")
    composeTestRule.onNodeWithTag("admin_password_input").assertExists().performTextInput("admin")
    composeTestRule.waitForIdle()

    // Click submit/enter button
    composeTestRule.onNodeWithTag("admin_login_submit").assertExists().performClick()
    composeTestRule.waitForIdle()

    // It should render admin dashboard
    composeTestRule.onNodeWithTag("admin_tab_stats").assertExists()
    composeTestRule.onNodeWithTag("stats_dashboard_scroll").assertExists()
  }
}
