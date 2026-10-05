package org.thunderdog.challegram.stage8;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Looper;
import android.os.StrictMode;
import android.view.View;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.theme.ThemeSet;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.AppContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * In-memory presentation globals only. Never call AppContext.init, Settings.instance,
 * ThemeManager's constructor, TdlibManager.instance, or ForumTopicView.setTopic here.
 * ThemeManager's constructor subscribes to the account manager; constructor-free allocation
 * is intentionally confined to these tests. All reflected globals are restored per case.
 * Unsafe is accessed reflectively (no compile dependency); unsupported runtimes fail, not skip.
 */
public final class SyntheticEnvironment implements AutoCloseable {
  private final Context target;
  private final List<Runnable> restorations = new ArrayList<>();
  private final StrictMode.ThreadPolicy originalPolicy;
  private final ThemeManager themeManager;

  public SyntheticEnvironment (Context target) throws Exception {
    require(Looper.myLooper() == Looper.getMainLooper(), "Checks must run on the Android main thread");
    require(target.getPackageName().endsWith(".stage8synthetic"), "Not an isolated test install");
    this.target = target;
    originalPolicy = StrictMode.getThreadPolicy();
    assertNoAccountInitialization();
    themeManager = allocate(ThemeManager.class);
    try {
      replace(AppContext.class, "context", target);
      replace(ThemeManager.class, "instance", themeManager);
      replace(Lang.class, "languageSettingsLoaded", true);
      replace(Lang.class, "languageRtl", false);
      replace(Screen.class, "_lastDensity", target.getResources().getDisplayMetrics().density);
      replace(Fonts.class, "needSystemFonts", false);
      replace(Fonts.class, "robotoRegular", Typeface.createFromAsset(target.getAssets(), "fonts/Roboto-Regular.ttf"));
      replace(Fonts.class, "robotoMedium", Typeface.createFromAsset(target.getAssets(), "fonts/Roboto-Medium.ttf"));
      StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder(originalPolicy).detectNetwork().penaltyDeathOnNetwork().build());
    } catch (Exception | Error failure) {
      close();
      throw failure;
    }
  }

  public Context configure (float fontScale, boolean rtl, int themeId) throws Exception {
    Configuration configuration = new Configuration(target.getResources().getConfiguration());
    configuration.fontScale = fontScale;
    Context context = target.createConfigurationContext(configuration);
    set(AppContext.class, "context", context);
    set(Lang.class, "languageRtl", rtl);
    set(themeManager, "_currentTheme", ThemeSet.getBuiltinTheme(themeId));
    return context;
  }

  public void assertNoAccountInitialization () throws Exception {
    require(get(Class.forName("org.thunderdog.challegram.telegram.TdlibManager"), "instance") == null,
      "Account manager was initialized: stop synthetic execution");
    require(get(Class.forName("org.thunderdog.challegram.unsorted.Settings"), "instance") == null,
      "Persistent Settings were initialized: stop synthetic execution");
  }

  private void replace (Class<?> type, String name, Object value) throws Exception {
    Field field = field(type, name);
    Object previous = field.get(null);
    restorations.add(() -> {
      try { field.set(null, previous); } catch (IllegalAccessException error) { throw new AssertionError(error); }
    });
    field.set(null, value);
  }

  @Override public void close () {
    try {
      for (int i = restorations.size() - 1; i >= 0; i--) restorations.get(i).run();
    } finally {
      StrictMode.setThreadPolicy(originalPolicy);
    }
  }

  public static void require (boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  public static void equal (long expected, long actual, String message) {
    require(expected == actual, message + ": expected " + expected + ", was " + actual);
  }

  public static void measure (View view, int width, int height) {
    view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
  }

  private static Field field (Class<?> type, String name) throws NoSuchFieldException {
    for (Class<?> current = type; current != null; current = current.getSuperclass()) {
      try {
        Field field = current.getDeclaredField(name);
        field.setAccessible(true);
        return field;
      } catch (NoSuchFieldException ignored) { }
    }
    throw new NoSuchFieldException(type.getName() + "." + name + " (update synthetic seam)");
  }

  public static Object get (Object owner, String name) throws Exception {
    return field(owner instanceof Class<?> ? (Class<?>) owner : owner.getClass(), name)
      .get(owner instanceof Class<?> ? null : owner);
  }

  public static void set (Object owner, String name, Object value) throws Exception {
    field(owner instanceof Class<?> ? (Class<?>) owner : owner.getClass(), name)
      .set(owner instanceof Class<?> ? null : owner, value);
  }

  public static Object invoke (Object owner, String name, Class<?>[] parameters, Object... arguments) throws Exception {
    Method method = owner.getClass().getDeclaredMethod(name, parameters);
    method.setAccessible(true);
    return method.invoke(owner, arguments);
  }

  public static <T> T allocate (Class<T> type) throws Exception {
    Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
    Object unsafe;
    try {
      unsafe = get(unsafeType, "theUnsafe");
    } catch (NoSuchFieldException androidFieldName) {
      unsafe = get(unsafeType, "THE_ONE");
    }
    return type.cast(unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe, type));
  }
}
