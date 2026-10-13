package org.thunderdog.challegram.miniapps;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class MiniAppAgeVerificationTest {
  private static TdApi.AgeVerificationParameters parameters (int age, String bot, String country) {
    return new TdApi.AgeVerificationParameters(age, bot, country);
  }

  @Test public void measuredAgeUsesAccountThresholdAndDeliversOnlyOnce () {
    TdApi.AgeVerificationParameters p = parameters(21, "verificationbot", "GB");
    List<Boolean> results = new ArrayList<>();
    MiniAppAgeVerification v = new MiniAppAgeVerification(p, 1, (owner, passed) -> results.add(passed));
    v.report(p, 1, 20.999);
    v.report(p, 1, 25);
    v.cancel();
    assertEquals(java.util.Collections.singletonList(false), results);
    v = new MiniAppAgeVerification(p, 1, (owner, passed) -> results.add(passed));
    v.report(p, 1, 21);
    assertEquals(Boolean.TRUE, results.get(1));
  }

  @Test public void accountAndServerParametersCannotChangeUnderAnOpenVerification () {
    TdApi.AgeVerificationParameters p = parameters(18, "verificationbot", "GB");
    List<Boolean> results = new ArrayList<>();
    MiniAppAgeVerification v = new MiniAppAgeVerification(p, 1, (owner, passed) -> results.add(passed));
    v.report(p, 2, 50);
    v.report(null, 1, 50);
    v.report(parameters(21, "verificationbot", "GB"), 1, 50);
    v.report(parameters(18, "otherbot", "GB"), 1, 50);
    v.report(parameters(18, "verificationbot", "AU"), 1, 50);
    assertTrue(results.isEmpty());
    v.cancel();
    v.report(p, 1, 50);
    assertEquals(1, results.size());
    assertNull(results.get(0));
  }

  @Test public void invalidMeasurementsNeverChangeContentSettings () {
    TdApi.AgeVerificationParameters p = parameters(18, "verificationbot", "GB");
    List<Boolean> results = new ArrayList<>();
    MiniAppAgeVerification v = new MiniAppAgeVerification(p, 1, (owner, passed) -> results.add(passed));
    for (double age : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1}) v.report(p, 1, age);
    assertTrue(results.isEmpty());
    assertTrue(v.isCurrent(p, 1));
    v.report(p, 1, 0);
    assertEquals(java.util.Collections.singletonList(false), results);
  }
}
