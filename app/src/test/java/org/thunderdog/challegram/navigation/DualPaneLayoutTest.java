package org.thunderdog.challegram.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.thunderdog.challegram.navigation.DualPaneLayout.MODE_AUTO;
import static org.thunderdog.challegram.navigation.DualPaneLayout.MODE_FORCE;
import static org.thunderdog.challegram.navigation.DualPaneLayout.MODE_OFF;
import static org.thunderdog.challegram.navigation.DualPaneLayout.listPaneWidthDp;
import static org.thunderdog.challegram.navigation.DualPaneLayout.shouldUseDualPane;

import org.junit.Test;

/**
 * Uses synthetic window sizes in dp around the layout breakpoints.
 */
public class DualPaneLayoutTest {
  @Test
  public void narrowWindowsStaySinglePane () {
    assertFalse(shouldUseDualPane(360, 780, MODE_AUTO));   // phone / cover-display class
    assertFalse(shouldUseDualPane(599, 900, MODE_AUTO));   // just below breakpoint
    assertFalse(shouldUseDualPane(599, 900, MODE_FORCE));  // force never goes below usable minimum
  }

  @Test
  public void wideWindowsUseDualPane () {
    assertTrue(shouldUseDualPane(600, 800, MODE_AUTO));    // breakpoint (Medium width)
    assertTrue(shouldUseDualPane(673, 820, MODE_AUTO));    // large foldable inner display, portrait
    assertTrue(shouldUseDualPane(900, 700, MODE_AUTO));    // tablet / inner display landscape
  }

  @Test
  public void compactHeightBlocksAutoButNotForce () {
    assertFalse(shouldUseDualPane(780, 360, MODE_AUTO));   // phone landscape
    assertTrue(shouldUseDualPane(780, 360, MODE_FORCE));
    assertFalse(shouldUseDualPane(780, 479, MODE_AUTO));
    assertTrue(shouldUseDualPane(780, 480, MODE_AUTO));
  }

  @Test
  public void offAlwaysSinglePane () {
    assertFalse(shouldUseDualPane(1280, 800, MODE_OFF));
  }

  @Test
  public void listWidthIsClampedAndLeavesDetailUsable () {
    for (int width = 600; width <= 2000; width += 7) {
      int list = listPaneWidthDp(width);
      assertTrue("list too narrow at " + width, list >= DualPaneLayout.MIN_LIST_WIDTH_DP);
      assertTrue("list too wide at " + width, list <= DualPaneLayout.MAX_LIST_WIDTH_DP);
      assertTrue("detail too narrow at " + width, width - list >= DualPaneLayout.MIN_DETAIL_WIDTH_DP);
    }
    assertEquals(280, listPaneWidthDp(600));
    assertEquals(400, listPaneWidthDp(1280));
  }

  @Test
  public void foldUnfoldSequenceSwitchesEachTime () {
    int[][] windows = { {360, 780}, {673, 820}, {360, 780}, {820, 673}, {420, 820} }; // fold → unfold → fold → rotate → split-screen
    boolean[] expected = { false, true, false, true, false };
    for (int i = 0; i < windows.length; i++) {
      assertEquals("step " + i, expected[i], shouldUseDualPane(windows[i][0], windows[i][1], MODE_AUTO));
    }
  }
}
