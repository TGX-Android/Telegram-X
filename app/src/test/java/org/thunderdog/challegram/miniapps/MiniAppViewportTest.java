package org.thunderdog.challegram.miniapps;

import org.junit.Test;
import static org.junit.Assert.*;

public class MiniAppViewportTest {
  @Test public void expandedSheetFitsBetweenSystemBars () {
    // Pixel 6 portrait: status/cutout 128px, gesture bar 63px.
    assertEquals(2209, MiniAppViewport.sheetHeight(2400, 128, 63, true, false));
  }

  @Test public void keyboardAndRotationDoNotExtendPastVisibleWindow () {
    assertEquals(1372, MiniAppViewport.sheetHeight(2400, 128, 900, true, false));
    assertEquals(969, MiniAppViewport.sheetHeight(1080, 48, 63, true, false));
    assertEquals(0, MiniAppViewport.sheetHeight(100, 128, 63, true, false));
  }

  @Test public void compactAndFullscreenHaveDifferentInsetContracts () {
    assertEquals(1325, MiniAppViewport.sheetHeight(2400, 128, 63, false, false));
    // Fullscreen web content owns its system safe areas; only the keyboard reduces its viewport.
    assertEquals(2400, MiniAppViewport.sheetHeight(2400, 128, 0, true, true));
    assertEquals(1563, MiniAppViewport.sheetHeight(2400, 128, 837, true, true));
  }
}
