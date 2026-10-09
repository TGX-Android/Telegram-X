package org.thunderdog.challegram.data

import org.junit.Assert.*
import org.junit.Test

class ForumRailLayoutTest {
  @Test fun `progress clamps finite and infinite values to the unit interval`() {
    val values = floatArrayOf(Float.NEGATIVE_INFINITY, -2f, -.01f, 0f, .25f, .5f, 1f, 1.01f, 2f, Float.POSITIVE_INFINITY)
    val expected = floatArrayOf(0f, 0f, 0f, 0f, .25f, .5f, 1f, 1f, 1f, 1f)
    for (index in values.indices) {
      assertEquals("progress ${values[index]}", expected[index], ForumRailLayout.progress(values[index]), 0f)
    }
  }

  @Test fun `interpolation clamps and preserves ascending descending and stationary endpoints`() {
    val progress = floatArrayOf(-1f, 0f, .25f, .5f, .75f, 1f, 2f)
    val ascending = floatArrayOf(-20f, -20f, 0f, 20f, 40f, 60f, 60f)
    val descending = floatArrayOf(60f, 60f, 40f, 20f, 0f, -20f, -20f)
    for (index in progress.indices) {
      assertEquals(ascending[index], ForumRailLayout.interpolate(-20f, 60f, progress[index]), 0f)
      assertEquals(descending[index], ForumRailLayout.interpolate(60f, -20f, progress[index]), 0f)
      assertEquals(42f, ForumRailLayout.interpolate(42f, 42f, progress[index]), 0f)
    }
    var previousAscending = -20f
    var previousDescending = 60f
    for (step in 0..100) {
      val up = ForumRailLayout.interpolate(-20f, 60f, step / 100f)
      val down = ForumRailLayout.interpolate(60f, -20f, step / 100f)
      assertTrue(up >= previousAscending && up <= 60f)
      assertTrue(down <= previousDescending && down >= -20f)
      previousAscending = up
      previousDescending = down
    }
  }

  @Test fun `topic body starts fully offscreen including its rail margin and finishes in place`() {
    for (width in intArrayOf(320, 360, 412, 600, 840)) {
      val rail = ForumRailLayout.widthDp(width.toFloat())
      val body = (width - rail).toFloat()
      val closedLtr = ForumRailLayout.topicTranslation(width, rail, 0f, false)
      val closedRtl = ForumRailLayout.topicTranslation(width, rail, 0f, true)
      assertEquals(body, closedLtr, 0f)
      assertEquals(-body, closedRtl, 0f)
      assertEquals("LTR body left must start at the viewport right", width.toFloat(), rail + closedLtr, 0f)
      assertEquals("RTL body right must start at the viewport left", 0f, body + closedRtl, 0f)
      for (rtl in booleanArrayOf(false, true)) {
        assertEquals(0f, ForumRailLayout.topicTranslation(width, rail, 1f, rtl), 0f)
        assertEquals(0f, ForumRailLayout.topicTranslation(width, rail, 2f, rtl), 0f)
        assertEquals(if (rtl) -body else body, ForumRailLayout.topicTranslation(width, rail, -1f, rtl), 0f)
      }
      for (step in 0..8) {
        val ltrLeft = rail + ForumRailLayout.topicTranslation(width, rail, step / 8f, false)
        val rtlLeft = ForumRailLayout.topicTranslation(width, rail, step / 8f, true)
        assertEquals("Mirrored body left", width - (ltrLeft + body), rtlLeft, .0001f)
        assertEquals("Mirrored body right", width - ltrLeft, rtlLeft + body, .0001f)
      }
    }
  }

  @Test fun `topic motion is bounded monotonic and mirrored throughout opening`() {
    for (width in intArrayOf(320, 360, 412, 600, 840)) {
      val rail = ForumRailLayout.widthDp(width.toFloat())
      val distance = (width - rail).toFloat()
      var previousLtr = distance
      var previousRtl = -distance
      for (step in 0..100) {
        val ltr = ForumRailLayout.topicTranslation(width, rail, step / 100f, false)
        val rtl = ForumRailLayout.topicTranslation(width, rail, step / 100f, true)
        assertTrue(ltr >= 0f && ltr <= previousLtr)
        assertTrue(rtl <= 0f && rtl >= previousRtl)
        assertEquals(-ltr, rtl, 0f)
        previousLtr = ltr
        previousRtl = rtl
      }
    }
  }

  @Test fun `a rail at least as wide as the viewport never produces negative travel`() {
    for (width in intArrayOf(0, 56, 64)) {
      for (rtl in booleanArrayOf(false, true)) {
        for (progress in floatArrayOf(-1f, 0f, .5f, 1f, 2f)) {
          assertEquals(0f, ForumRailLayout.topicTranslation(width, 64, progress, rtl), 0f)
        }
      }
    }
  }

  @Test fun `reversal and cancelled Back continuously retrace the same geometry`() {
    // Open, preview Back halfway, cancel back to Topics, then complete Back to Chats.
    val trajectory = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 7, 6, 5, 4, 4, 5, 6, 7, 8, 7, 6, 5, 4, 3, 2, 1, 0)
    for (width in intArrayOf(320, 360, 600)) {
      val rail = ForumRailLayout.widthDp(width.toFloat())
      for (rtl in booleanArrayOf(false, true)) {
        val signedDistance = (width - rail) * if (rtl) -1f else 1f
        var previousStep = trajectory[0]
        var previousTranslation = signedDistance
        for (step in trajectory) {
          val progress = step / 8f
          val translation = ForumRailLayout.topicTranslation(width, rail, progress, rtl)
          assertEquals(signedDistance * (8 - step) / 8f, translation, .0001f)
          assertEquals("No jump at a direction change or repeated frame",
            signedDistance * (previousStep - step) / 8f, translation - previousTranslation, .0001f)
          assertEquals(96f - 8f * step, ForumRailLayout.interpolate(96f, 32f, progress), 0f)
          assertEquals(24f - step, ForumRailLayout.interpolate(24f, 16f, progress), 0f)
          previousStep = step
          previousTranslation = translation
        }
      }
    }
  }

  @Test fun `phone and tablet keep rail visible and content usable`() {
    for (width in intArrayOf(320, 360, 412, 600, 840)) {
      val rail = ForumRailLayout.widthDp(width.toFloat())
      assertTrue(rail >= 48)
      assertTrue(width - rail >= 264)
      assertEquals(rail, ForumRailLayout.occupied(rail, 1f))
      assertEquals(0, ForumRailLayout.occupied(rail, 0f))
    }
  }
  @Test fun `rtl mirrors rail but never claims content touches`() {
    assertTrue(ForumRailLayout.hitRail(360, 64, 32f, false))
    assertFalse(ForumRailLayout.hitRail(360, 64, 64f, false))
    assertTrue(ForumRailLayout.hitRail(360, 64, 328f, true))
    assertFalse(ForumRailLayout.hitRail(360, 64, 295f, true))
    assertFalse(ForumRailLayout.hitRail(360, 0, 0f, false))
    assertFalse(ForumRailLayout.hitRail(360, 64, -1f, false))
    assertFalse(ForumRailLayout.hitRail(360, 64, 360f, true))
  }
  @Test fun `gesture coordinates follow the measured animation boundary`() {
    for (step in 0..10) {
      val occupied = ForumRailLayout.occupied(64, step / 10f)
      assertEquals(0f, ForumRailLayout.contentX(occupied, occupied.toFloat(), false), 0f)
      assertEquals(24f, ForumRailLayout.contentX(occupied, 24f, true), 0f)
    }
    assertEquals(0, ForumRailLayout.occupied(64, -1f))
    assertEquals(64, ForumRailLayout.occupied(64, 2f))
  }

  @Test fun `rail touch excludes the full width header and system navigation inset`() {
    for (rtl in booleanArrayOf(false, true)) {
      val x = if (rtl) 350f else 10f
      assertFalse(ForumRailLayout.hitRail(360, 64, x, 55f, 56, 780, rtl))
      assertTrue(ForumRailLayout.hitRail(360, 64, x, 56f, 56, 780, rtl))
      assertTrue(ForumRailLayout.hitRail(360, 64, x, 779f, 56, 780, rtl))
      assertFalse(ForumRailLayout.hitRail(360, 64, x, 780f, 56, 780, rtl))
      assertFalse(ForumRailLayout.hitRail(360, 0, x, 100f, 56, 780, rtl))
    }
  }
}
