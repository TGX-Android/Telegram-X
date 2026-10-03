package org.thunderdog.challegram.widget;

import org.junit.Test;

import static org.junit.Assert.*;

/** Synthetic widget geometry only; no Android, TDLib, device or account is required. */
public class ForumTopicsTabsLayoutTest {
  @Test public void horizontalControlsAreOutsideTheScrollViewport () {
    ForumTopicsTabsLayout.Parts p = ForumTopicsTabsLayout.partition(320, 44, 48, false, false, true);
    assertEquals(224, p.tabs.width());
    assertEquals(p.tabs.right, p.state.left);
    assertEquals(p.state.right, p.placement.left);
    assertEquals(48, p.placement.width());
    assertEquals(320, p.placement.right);
  }

  @Test public void rtlMirrorsControlsWithoutChangingAvailableTabSpace () {
    ForumTopicsTabsLayout.Parts ltr = ForumTopicsTabsLayout.partition(320, 44, 48, false, false, true);
    ForumTopicsTabsLayout.Parts rtl = ForumTopicsTabsLayout.partition(320, 44, 48, false, true, true);
    assertEquals(ltr.tabs.width(), rtl.tabs.width());
    assertEquals(0, rtl.placement.left);
    assertEquals(rtl.placement.right, rtl.state.left);
    assertEquals(rtl.state.right, rtl.tabs.left);
    assertEquals(320 - ltr.tabs.right, rtl.tabs.left);
  }

  @Test public void sideControlsStayBelowTheVerticalScrollViewport () {
    ForumTopicsTabsLayout.Parts p = ForumTopicsTabsLayout.partition(64, 400, 48, true, false, true);
    assertEquals(304, p.tabs.height());
    assertEquals(p.tabs.bottom, p.state.top);
    assertEquals(p.state.bottom, p.placement.top);
    assertEquals(48, p.placement.height());
    assertEquals(400, p.placement.bottom);
  }

  @Test public void hidingStatusReturnsOnlyItsOwnSpace () {
    ForumTopicsTabsLayout.Parts a = ForumTopicsTabsLayout.partition(320, 44, 48, false, false, false);
    ForumTopicsTabsLayout.Parts b = ForumTopicsTabsLayout.partition(320, 44, 48, false, false, true);
    assertEquals(0, a.state.width());
    assertEquals(48, a.tabs.width() - b.tabs.width());
    assertEquals(a.placement.left, b.placement.left);
  }

  @Test public void placementWinsOnVerySmallHosts () {
    ForumTopicsTabsLayout.Parts p = ForumTopicsTabsLayout.partition(35, 44, 48, false, false, true);
    assertEquals(35, p.placement.width());
    assertEquals(0, p.state.width());
    assertEquals(0, p.tabs.width());
    p = ForumTopicsTabsLayout.partition(64, 35, 48, true, true, true);
    assertEquals(35, p.placement.height());
    assertEquals(0, p.state.height());
    assertEquals(0, p.tabs.height());
  }

  @Test public void allPartitionsAreBoundedAndNonOverlapping () {
    for (int width : new int[] {0, 1, 35, 48, 64, 72, 320, 900}) {
      for (int height : new int[] {0, 1, 35, 44, 48, 64, 76, 300, 900}) {
        for (boolean side : new boolean[] {false, true}) for (boolean rtl : new boolean[] {false, true}) {
          for (boolean status : new boolean[] {false, true}) {
            ForumTopicsTabsLayout.Parts p = ForumTopicsTabsLayout.partition(width, height, 48, side, rtl, status);
            for (ForumTopicsTabsLayout.Bounds r : new ForumTopicsTabsLayout.Bounds[] {p.tabs, p.state, p.placement}) {
              assertTrue(r.left >= 0 && r.top >= 0 && r.right <= width && r.bottom <= height);
              assertTrue(r.width() >= 0 && r.height() >= 0);
            }
            assertEquals(width * height, area(p.tabs) + area(p.state) + area(p.placement));
          }
        }
      }
    }
  }

  private int area (ForumTopicsTabsLayout.Bounds b) { return b.width() * b.height(); }

  @Test public void dimensionsFollowDensityAndLargeFonts () {
    assertEquals(44, ForumTopicsTabsLayout.horizontalHeight(1, 1));
    assertEquals(64, ForumTopicsTabsLayout.sideWidth(1, 1));
    assertEquals(88, ForumTopicsTabsLayout.horizontalHeight(2, 1));
    assertEquals(44, ForumTopicsTabsLayout.horizontalHeight(1, 2));
    assertEquals(72, ForumTopicsTabsLayout.sideWidth(1, 2));
    assertEquals(44, ForumTopicsTabsLayout.horizontalHeight(1, .8f));
    assertEquals(116, ForumTopicsTabsLayout.horizontalHeight(2.625f, 1));
  }

  @Test public void lowDensityAndInvalidConfigurationAreSafe () {
    assertEquals(33, ForumTopicsTabsLayout.horizontalHeight(.75f, 1));
    assertEquals(48, ForumTopicsTabsLayout.sideWidth(.75f, 1));
    assertEquals(44, ForumTopicsTabsLayout.horizontalHeight(Float.NaN, Float.NaN));
    assertEquals(44, ForumTopicsTabsLayout.horizontalHeight(0, Float.POSITIVE_INFINITY));
  }

  @Test public void horizontalStaysOneCompactLineIncludingLargeFonts () {
    for (float scale : new float[] {1, 1.3f, 2, 3}) {
      // Only the title line controls height; badges stay beside it, never below it.
      int height = ForumTopicsTabsLayout.horizontalHeight(1, scale);
      assertTrue(height >= 14 * scale * 1.2f + 8);
      if (scale <= 2) assertEquals(44, height);
    }
  }

  @Test public void narrowSideHasCompactRowsAndFixedSmallIcons () {
    for (float scale : new float[] {.8f, 1, 1.3f, 1.6f, 2, 3, 4}) {
      int width = ForumTopicsTabsLayout.sideWidth(1, scale);
      int height = ForumTopicsTabsLayout.sideRowHeight(1, scale);
      assertTrue(width >= 64 && width <= 72);
      assertTrue(height >= 64 && height <= 76);
      if (scale <= 1.6f) assertEquals(64, width);
    }
    assertEquals(64, ForumTopicsTabsLayout.sideRowHeight(1, 1));
    assertEquals(76, ForumTopicsTabsLayout.sideRowHeight(1, 2));
    assertEquals(20, ForumTopicsTabsLayout.topicIconSize(1, false));
    assertEquals(24, ForumTopicsTabsLayout.topicIconSize(1, true));
    assertEquals(53, ForumTopicsTabsLayout.topicIconSize(2.625f, false));
    assertEquals(63, ForumTopicsTabsLayout.topicIconSize(2.625f, true));
  }

  @Test public void paginationUsesVisibleServerPrefixAndNeverAnUnlaidOutList () {
    assertFalse(ForumTopicsTabsLayout.nearEnd(-1, 0));
    assertTrue(ForumTopicsTabsLayout.nearEnd(0, 0));
    assertTrue(ForumTopicsTabsLayout.nearEnd(0, 2));
    assertFalse(ForumTopicsTabsLayout.nearEnd(195, 200));
    assertTrue(ForumTopicsTabsLayout.nearEnd(197, 200));
    assertTrue(ForumTopicsTabsLayout.nearEnd(201, 200));
  }

  @Test public void topAndBottomHistoryKeepTheFullHostWidth () {
    // Both horizontal placements use side=false; a remembered rail width is irrelevant.
    for (int hostWidth : new int[] {320, 360, 412}) {
      for (int sideWidth : new int[] {0, 64, 72, 400}) {
        assertEquals("Horizontal history: host=" + hostWidth + ", rail=" + sideWidth,
          hostWidth, ForumTopicsTabsLayout.historyWidth(hostWidth, false, sideWidth));
      }
    }
  }

  @Test public void startHistoryReservesExactlyOneRailAtNormalAndDoubleFontScale () {
    // Full host width, expected history at 1x, expected history at 2x.
    int[][] widths = {{320, 256, 248}, {360, 296, 288}, {412, 348, 340}};
    float[] scales = {1f, 2f};
    for (int[] sample : widths) {
      for (int i = 0; i < scales.length; i++) {
        int sideWidth = ForumTopicsTabsLayout.sideWidth(1f, scales[i]);
        int historyWidth = ForumTopicsTabsLayout.historyWidth(sample[0], true, sideWidth);
        assertEquals("START history: host=" + sample[0] + ", font=" + scales[i], sample[i + 1], historyWidth);
        assertEquals("Reserve the rail once, not at both edges", sample[0], sideWidth + historyWidth);
      }
    }
  }

  @Test public void historyWidthConsumesLocalPixelsWithoutAnotherDensityConversion () {
    int normalRail = ForumTopicsTabsLayout.sideWidth(2.625f, 1f);
    int largeFontRail = ForumTopicsTabsLayout.sideWidth(2.625f, 2f);
    assertEquals(672, ForumTopicsTabsLayout.historyWidth(840, true, normalRail));
    assertEquals(651, ForumTopicsTabsLayout.historyWidth(840, true, largeFontRail));
    assertEquals(840, ForumTopicsTabsLayout.historyWidth(840, false, largeFontRail));
  }

  @Test public void repeatedHostWidthsAndPlacementsNeverAccumulateSideInsets () {
    // Full host width, side flag, rail width, expected history width.
    int[][] samples = {
      {320, 0, 64, 320}, {320, 1, 64, 256}, {412, 1, 64, 348}, {360, 1, 72, 288},
      {360, 0, 72, 360}, {320, 1, 64, 256}, {320, 0, 64, 320}
    };
    for (int repeat = 0; repeat < 3; repeat++) {
      for (int[] sample : samples) {
        assertEquals("History resize cycle " + repeat + ", host=" + sample[0] + ", side=" + sample[1],
          sample[3], ForumTopicsTabsLayout.historyWidth(sample[0], sample[1] == 1, sample[2]));
      }
    }
  }

  @Test public void historyWidthBoundsUnmeasuredTinyAndInvalidSideInsets () {
    for (boolean side : new boolean[] {false, true}) {
      assertEquals(0, ForumTopicsTabsLayout.historyWidth(0, side, 64));
      assertEquals(0, ForumTopicsTabsLayout.historyWidth(-1, side, 64));
      assertEquals(1, ForumTopicsTabsLayout.historyWidth(1, side, 64));
      assertEquals(320, ForumTopicsTabsLayout.historyWidth(320, side, 0));
      assertEquals(320, ForumTopicsTabsLayout.historyWidth(320, side, -64));
    }
    assertEquals(1, ForumTopicsTabsLayout.historyWidth(64, true, 64));
    assertEquals(1, ForumTopicsTabsLayout.historyWidth(64, true, 72));
    assertEquals(1, ForumTopicsTabsLayout.historyWidth(320, true, Integer.MAX_VALUE));
  }
}
