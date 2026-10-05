package org.thunderdog.challegram.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import androidx.core.widget.TintableCompoundDrawablesView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.stage8.Stage8SyntheticInstrumentation.Case;
import org.thunderdog.challegram.stage8.SyntheticEnvironment;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.theme.ThemeSet;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.thunderdog.challegram.stage8.SyntheticEnvironment.allocate;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.equal;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.get;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.invoke;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.require;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.set;

/** Real profile action views only; no controller lifecycle, account or server calls. */
public final class ForumTopicProfileActionChecks {
  private ForumTopicProfileActionChecks () { }

  public static void register (List<Case> cases) {
    for (int theme : new int[] {ThemeId.BLUE, ThemeId.NIGHT_BLUE}) {
      cases.add(new Case("profile_action_initial_tint_" + theme, env -> initialTint(env, theme)));
    }
    cases.add(new Case("profile_action_legacy_support_tint_and_replacement", ForumTopicProfileActionChecks::supportTint));
    cases.add(new Case("profile_action_live_theme_change", ForumTopicProfileActionChecks::themeChange));
    cases.add(new Case("profile_action_enabled_accessibility_and_click", ForumTopicProfileActionChecks::enabledAndClick));
    cases.add(new Case("profile_action_drawable_tint_isolation", ForumTopicProfileActionChecks::tintIsolation));
  }

  private static void initialTint (SyntheticEnvironment env, int theme) throws Exception {
    Fixture fixture = new Fixture(env.configure(1f, false, theme));
    // This fails for framework TextView even on a modern test device. API <23 uses this interface.
    require(fixture.view instanceof TintableCompoundDrawablesView, "Legacy compound drawable tint is supported");
    for (int icon : new int[] {R.drawable.baseline_chat_bubble_24, R.drawable.baseline_notifications_off_24,
        R.drawable.deproko_baseline_pin_24}) {
      fixture.icon(icon);
      assertIconColor(fixture.view, Theme.getColor(ColorId.textLink));
    }
    equal(Theme.getColor(ColorId.textLink), fixture.view.getCurrentTextColor(), "Label uses semantic action color");
  }

  private static void supportTint (SyntheticEnvironment env) throws Exception {
    Fixture fixture = new Fixture(env.configure(1f, false, ThemeId.BLUE));
    // Exercise the exact AppCompat support path used by TextViewCompat before API 23,
    // without modifying SDK_INT or relying only on the modern framework tint implementation.
    ((TintableCompoundDrawablesView) fixture.view).setSupportCompoundDrawablesTintList(ColorStateList.valueOf(0xff267b91));
    for (int icon : new int[] {R.drawable.baseline_notifications_off_24, R.drawable.baseline_notifications_24,
        R.drawable.deproko_baseline_pin_24, R.drawable.deproko_baseline_pin_undo_24}) {
      fixture.icon(icon);
      assertIconColor(fixture.view, 0xff267b91);
    }
  }

  private static void themeChange (SyntheticEnvironment env) throws Exception {
    Fixture fixture = new Fixture(env.configure(1f, false, ThemeId.BLUE));
    fixture.icon(R.drawable.baseline_chat_bubble_24);
    int previous = Theme.getColor(ColorId.textLink);
    for (int theme : new int[] {ThemeId.NIGHT_BLUE, ThemeId.BLUE}) {
      Object manager = get(ThemeManager.class, "instance");
      ThemeDelegate from = (ThemeDelegate) get(manager, "_currentTheme");
      ThemeDelegate to = ThemeSet.getBuiltinTheme(theme);
      Class<?> temporaryClass = Class.forName("org.thunderdog.challegram.theme.ThemeTemporary");
      Constructor<?> constructor = temporaryClass.getDeclaredConstructor(ThemeDelegate.class, ThemeDelegate.class);
      constructor.setAccessible(true);
      Object transition = constructor.newInstance(from, to);
      invoke(transition, "setFactor", new Class<?>[] {float.class}, 1f);
      set(manager, "_currentTheme", transition);
      int expected = Theme.getColor(ColorId.textLink);
      require(previous != expected, "Test themes have distinct action colors");
      fixture.profile.getThemeListeners().onThemeColorsChanged(false);
      equal(expected, fixture.view.getCurrentTextColor(), "Theme listener updates label");
      assertIconColor(fixture.view, expected);
      fixture.icon(R.drawable.deproko_baseline_pin_undo_24);
      assertIconColor(fixture.view, expected);
      previous = expected;
      set(manager, "_currentTheme", to);
    }
  }

  private static void enabledAndClick (SyntheticEnvironment env) throws Exception {
    Fixture fixture = new Fixture(env.configure(1.3f, true, ThemeId.BLUE));
    fixture.icon(R.drawable.baseline_chat_bubble_24);
    fixture.view.setText("Messages");
    final int[] clicks = {0};
    fixture.view.setOnClickListener(v -> clicks[0]++);
    fixture.view.setEnabled(false);
    require(!fixture.view.isEnabled() && Math.abs(fixture.view.getAlpha() - .45f) < .001f,
      "Disabled action retains the existing disabled presentation");
    assertIconColor(fixture.view, Theme.getColor(ColorId.textLink));
    fixture.view.setEnabled(true);
    require(fixture.view.isEnabled() && fixture.view.getAlpha() == 1f, "Re-enabled action restores opacity");
    require(fixture.view.performClick(), "Action still handles clicks");
    equal(1, clicks[0], "Click dispatch is unchanged");
    AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
    try {
      fixture.view.onInitializeAccessibilityNodeInfo(info);
      require("android.widget.Button".contentEquals(info.getClassName()), "Action retains button accessibility role");
    } finally {
      info.recycle();
    }
  }

  private static void tintIsolation (SyntheticEnvironment env) throws Exception {
    Context context = env.configure(1f, false, ThemeId.BLUE);
    Fixture first = new Fixture(context);
    Fixture second = new Fixture(context);
    first.icon(R.drawable.deproko_baseline_pin_24);
    second.icon(R.drawable.deproko_baseline_pin_24);
    ((TintableCompoundDrawablesView) first.view).setSupportCompoundDrawablesTintList(ColorStateList.valueOf(0xff267b91));
    assertIconColor(first.view, 0xff267b91);
    assertIconColor(second.view, Theme.getColor(ColorId.textLink));
  }

  private static void assertIconColor (TextView view, int expected) {
    Drawable icon = view.getCompoundDrawables()[1];
    require(icon != null, "Top action icon is present");
    int width = Math.max(1, icon.getIntrinsicWidth()), height = Math.max(1, icon.getIntrinsicHeight());
    Bitmap pixels = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    try {
      icon.setBounds(0, 0, width, height);
      icon.draw(new Canvas(pixels));
      int opaque = 0;
      for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
          int pixel = pixels.getPixel(x, y);
          if (Color.alpha(pixel) == 255) {
            equal(expected & 0xffffff, pixel & 0xffffff, "Rendered icon has the requested tint");
            opaque++;
          }
        }
      }
      require(opaque > 0, "Rendered icon has visible opaque pixels");
    } finally {
      pixels.recycle();
    }
  }

  private static final class Fixture {
    final ForumTopicProfileController profile;
    final TextView view;

    Fixture (Context context) throws Exception {
      profile = allocate(ForumTopicProfileController.class);
      Class<?> type = Class.forName(ForumTopicProfileController.class.getName() + "$ActionView");
      Constructor<?> constructor = type.getDeclaredConstructor(ForumTopicProfileController.class, Context.class);
      constructor.setAccessible(true);
      view = (TextView) constructor.newInstance(profile, context);
    }

    void icon (int resource) throws Exception {
      invoke(view, "setIcon", new Class<?>[] {int.class}, resource);
    }
  }
}
