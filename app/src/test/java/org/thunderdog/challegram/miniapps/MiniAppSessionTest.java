package org.thunderdog.challegram.miniapps;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class MiniAppSessionTest {
  @Test public void closesLeaseOnlyOnce () {
    List<Long> released = new ArrayList<>();
    MiniAppSession session = new MiniAppSession(released::add);
    assertTrue(session.accept(17));
    assertTrue(released.isEmpty());
    session.close();
    session.close();
    assertEquals(Arrays.asList(17L), released);
  }

  @Test public void canceledLaunchReleasesLateResult () {
    List<Long> released = new ArrayList<>();
    MiniAppSession session = new MiniAppSession(released::add);
    session.close();
    assertFalse(session.accept(17));
    assertFalse(session.accept(17));
    assertEquals(Arrays.asList(17L), released);
  }

  @Test public void simpleLaunchNeverClosesZeroId () {
    List<Long> released = new ArrayList<>();
    MiniAppSession session = new MiniAppSession(released::add);
    assertTrue(session.accept(0));
    session.close();
    assertTrue(released.isEmpty());
  }
}
