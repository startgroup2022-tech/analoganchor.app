package com.analoganchor.app

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.analoganchor.app.viewmodel.AnchorViewModel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Analog Anchor", appName)
  }

  @Test
  fun `normalizes long access token from pasted message text`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = AnchorViewModel(app)
    val token = "AA-ANCHOR-H7Q2M-KAEEE7X2P-P4X8R-Z2M5C-Q9V7B-VGU8T-EXP60"

    val pastedText = "\u200FSupport code:\n${token.replace("-", "\u2011")}"

    assertEquals(token, viewModel.normalizeAccessTokenInput(pastedText))
    assertEquals(true, viewModel.isAccessTokenShape(pastedText))
  }

  @Test
  fun `normalizes follow up access tokens from pasted message text`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = AnchorViewModel(app)
    val secondCode = "AA-ANCHOR-H7Q2M-UKBXXX2345-P4X8R-Z2M5C-Q9V7B-PTXUY-EXP60"
    val thirdCode = "AA-ANCHOR-H7Q2M-VKGLLL2345-P4X8R-Z2M5C-Q9V7B-EJFKP-EXP60"

    val pastedSecondCode = "\u200FSecond support code:\n${secondCode.replace("-", "\u2011")}"
    val pastedThirdCode = "\u200FFinal support code:\n${thirdCode.replace("-", "\u2011")}"

    assertEquals(secondCode, viewModel.normalizeAccessTokenInput(pastedSecondCode))
    assertEquals(true, viewModel.isAccessTokenShape(pastedSecondCode))
    assertEquals(thirdCode, viewModel.normalizeAccessTokenInput(pastedThirdCode))
    assertEquals(true, viewModel.isAccessTokenShape(pastedThirdCode))
  }

  @Test
  fun `legacy short access tokens still normalize`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = AnchorViewModel(app)
    val pastedText = "\u200FSupport code:\nAA\u2011AEEE\u2011EXP\u0666\u0660"

    assertEquals("AA-AEEE-EXP60", viewModel.normalizeAccessTokenInput(pastedText))
    assertEquals(true, viewModel.isAccessTokenShape(pastedText))
  }

  @Test
  fun `anchor tab test tap starts session with expected duration`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = AnchorViewModel(app)

    viewModel.startAnchorTabTapOneSession(25)

    assertEquals(25, viewModel.sessionTotalDurationMinutes.value)
    val expectedSeconds = if (AnchorViewModel.ANCHOR_TAP_ONE_TEST_MODE) {
      AnchorViewModel.ANCHOR_TAP_ONE_TEST_DURATION_SEC
    } else {
      25 * 60
    }
    assertEquals(expectedSeconds, viewModel.sessionTimeLeftSeconds.value)
  }

  @Test
  fun `decodes emergency PIN correctly for manual rescue display`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = AnchorViewModel(app)

    // Legacy token with "AEEE" -> Code 1: A=5, E=2, E=2, E=2 -> PIN "5222"
    val legacyPin = viewModel.getDecodedEmergencyPin("AA-AEEE-EXP60")
    assertEquals("5222", legacyPin)

    // Decoy token -> Decoy PIN "4829"
    val decoyPin = viewModel.getDecodedEmergencyPin("AA-ANCHOR-H7Q2M-KAEEE7X2P-P4X8R-Z2M5C-Q9V7B-VGU8T-DEC60")
    assertEquals("4829", decoyPin)
  }

  @Test
  fun `emergency cooldown starts and syncs wall clock state`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = AnchorViewModel(app)

    viewModel.startEmergencyCoolDown()
    assertEquals("COOLING", viewModel.emergencyState.value)

    viewModel.onAppResumed()
    assertEquals("COOLING", viewModel.emergencyState.value)
  }
}
