package org.thunderdog.challegram.data

import org.junit.Assert.*
import org.junit.Test

class ForumRailScrollTest {
  @Test fun waitsForViewportBelowAnchorNotJustAnchorEntry() {
    assertFalse(ForumRailLayout.canRestoreScroll(30, 28, -16, 64, 768, false))
    assertTrue(ForumRailLayout.canRestoreScroll(60, 28, -16, 64, 768, false))
  }

  @Test fun partialRowAndInsetsUseExactAvailableHeight() {
    assertFalse(ForumRailLayout.canRestoreScroll(40, 28, -1, 64, 768, false))
    assertTrue(ForumRailLayout.canRestoreScroll(41, 28, -1, 64, 768, false))
    assertTrue(ForumRailLayout.canRestoreScroll(40, 28, 0, 64, 768, false))
  }

  @Test fun genuineEndAllowsNormalEndGapCorrection() {
    assertTrue(ForumRailLayout.canRestoreScroll(30, 28, -16, 64, 768, true))
    assertTrue(ForumRailLayout.canRestoreScroll(30, 99, 0, 64, 768, true))
  }

  @Test fun waitsForMeasurementAndData() {
    assertFalse(ForumRailLayout.canRestoreScroll(60, 28, 0, 64, 0, false))
    assertFalse(ForumRailLayout.canRestoreScroll(0, 0, 0, 64, 768, true))
    assertFalse(ForumRailLayout.canRestoreScroll(30, 30, 0, 64, 768, false))
  }
}
