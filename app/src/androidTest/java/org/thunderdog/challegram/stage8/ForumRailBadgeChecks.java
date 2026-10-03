package org.thunderdog.challegram.stage8;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.Gravity;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.stage8.Stage8SyntheticInstrumentation.Case;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.util.text.counter.CounterTextPart;
import org.thunderdog.challegram.widget.ForumRailBadgeLayout;

import java.util.List;

import static org.thunderdog.challegram.stage8.SyntheticEnvironment.equal;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.require;

/** Actual Counter/DrawAlgorithms rasterization, isolated from chat binding and persistent settings. */
public final class ForumRailBadgeChecks {
  private ForumRailBadgeChecks () { }

  public static void register (List<Case> cases) {
    for (int width : new int[] {56, 64, 72}) {
      for (float fontScale : new float[] {1f, 1.3f, 2f}) {
        cases.add(new Case("rail_badge_bounds_w" + width + "_font" + fontScale,
          env -> geometry(env, width, fontScale)));
      }
    }
    cases.add(new Case("rail_badge_counter_right_gravity_contract", ForumRailBadgeChecks::gravityContract));
    cases.add(new Case("rail_badge_hidden_and_zero_decoration", ForumRailBadgeChecks::hidden));
  }

  private static final class Sample {
    final String name, text;
    final long count;
    final boolean mention, muted;

    Sample (String name, long count, String text, boolean mention, boolean muted) {
      this.name = name; this.count = count; this.text = text; this.mention = mention; this.muted = muted;
    }
  }

  private static final Sample[] SAMPLES = {
    new Sample("single", 1, "1", false, false),
    new Sample("double_muted", 99, "99", false, true),
    new Sample("triple", 999, "999", false, false),
    new Sample("grouped", 12345, "12\u202f345", false, false),
    new Sample("max_unread_muted", Integer.MAX_VALUE, "2\u202f147\u202f483\u202f647", false, true),
    new Sample("wide_counter", Long.MAX_VALUE, "9\u202f223\u202f372\u202f036\u202f854\u202f775\u202f807", false, false),
    new Sample("arabic_digits", 1234567890, "\u0661\u0662\u0663\u0664\u0665\u0666\u0667\u0668\u0669\u0660", false, false),
    new Sample("unread_dot", Tdlib.CHAT_MARKED_AS_UNREAD, "", false, false),
    new Sample("mention_dot_muted", Tdlib.CHAT_MARKED_AS_UNREAD, "", true, true)
  };

  /**
   * Counter's normal Text parts initialize the persistent emoji singleton, even for digits.
   * Keep the actual Counter and its width/outline/draw code, but supply account-free glyphs
   * measured with the same Roboto bold paint. Explicit strings also bypass Strings/Settings.
   * Counter is DP-sized in production; changing system fontScale must not break its bounds.
   */
  private static final class Glyph implements CounterTextPart {
    final String text;
    final Paint paint = new Paint(Paints.robotoStyleProvider(ForumRailBadgeLayout.TEXT_SIZE_DP).getBoldPaint());

    Glyph (String text) { this.text = text; }

    @Override public int getWidth () { return (int) Math.ceil(paint.measureText(text)); }
    @Override public int getHeight () { return (int) Math.ceil(paint.descent() - paint.ascent()); }
    @Override public String getText () { return text; }

    @Override public void draw (Canvas canvas, int startX, int endX, int bottomPadding, int startY, TextColorSet colors, float alpha) {
      int color = colors.defaultTextColor();
      paint.setColor(color);
      paint.setAlpha(Math.round(Color.alpha(color) * alpha));
      canvas.drawText(text, startX, startY - paint.ascent(), paint);
    }
  }

  private static Counter counter (Context context, Sample sample) {
    Counter.Builder builder = new Counter.Builder().textSize(ForumRailBadgeLayout.TEXT_SIZE_DP)
      .outlineColor(ColorId.filling).setCustomTextPartBuilder(Glyph::new);
    if (sample.mention) {
      builder.drawable(context.getDrawable(R.drawable.baseline_at_16).mutate(), 0f, Gravity.CENTER);
    }
    Counter counter = builder.build();
    counter.setCount(sample.count, sample.muted, sample.text, false);
    return counter;
  }

  private static int avatarRadius (int width) {
    return Math.min(Screen.dp(23), width / 2 - Screen.dp(5));
  }

  @SuppressWarnings("deprecation")
  private static RecordingCanvas render (Counter counter, int width, int height, boolean rtl, float alpha, boolean clipped) {
    int guard = Screen.dp(24);
    RecordingCanvas canvas = new RecordingCanvas(width + guard * 2, height + guard * 2);
    try {
      canvas.translate(guard, guard);
      if (clipped) canvas.clipRect(0, 0, width, height);
      int saves = canvas.getSaveCount();
      Matrix matrix = new Matrix();
      canvas.getMatrix(matrix);
      ForumRailBadgeLayout.draw(canvas, counter, width, height, avatarRadius(width), rtl, alpha);
      equal(saves, canvas.getSaveCount(), "Badge draw restores canvas save count");
      Matrix after = new Matrix();
      canvas.getMatrix(after);
      require(matrix.equals(after), "Badge draw must not leave a translation, scale or reflection");
      return canvas;
    } catch (Throwable error) {
      canvas.close();
      throw error;
    }
  }

  private static int[] pixels (RecordingCanvas canvas) {
    int width = canvas.bitmap.getWidth(), height = canvas.bitmap.getHeight();
    int[] pixels = new int[width * height];
    canvas.bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
    return pixels;
  }

  private static Rect rasterBounds (RecordingCanvas canvas) {
    int width = canvas.bitmap.getWidth(), height = canvas.bitmap.getHeight();
    int left = width, top = height, right = -1, bottom = -1;
    int[] pixels = pixels(canvas);
    for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
      if (Color.alpha(pixels[y * width + x]) != 0) {
        left = Math.min(left, x); top = Math.min(top, y);
        right = Math.max(right, x); bottom = Math.max(bottom, y);
      }
    }
    return right < 0 ? new Rect() : new Rect(left, top, right + 1, bottom + 1);
  }

  private static void samePixels (RecordingCanvas expected, RecordingCanvas actual, String message) {
    int[] a = pixels(expected), b = pixels(actual);
    equal(a.length, b.length, message + ": raster size");
    int first = -1, differences = 0;
    for (int i = 0; i < a.length; i++) if (a[i] != b[i]) {
      if (first < 0) first = i;
      differences++;
    }
    if (first >= 0) {
      int width = expected.bitmap.getWidth();
      throw new AssertionError(message + ": " + differences + " changed pixels; first " + first % width + "," + first / width +
        " expected=" + Integer.toHexString(a[first]) + " actual=" + Integer.toHexString(b[first]));
    }
  }

  private static Rect inside (RecordingCanvas canvas, int width, int height, String text, String label) {
    int guard = Screen.dp(24), margin = Screen.dp(2);
    Rect bounds = rasterBounds(canvas);
    require(!bounds.isEmpty(), label + ": visible Counter must reach the bitmap");
    require(bounds.left >= guard + margin && bounds.top >= guard + margin &&
      bounds.right <= guard + width - margin && bounds.bottom <= guard + height - margin,
      label + ": full badge/outline must retain an edge margin: " + bounds);

    StringBuilder drawn = new StringBuilder();
    float previousX = Float.NEGATIVE_INFINITY;
    for (RecordingCanvas.Mark mark : canvas.marks) if (mark.name.startsWith("text:")) {
      RectF r = mark.bounds;
      require(r.left >= guard + margin - .75f && r.right <= guard + width - margin + .75f &&
        r.top >= guard + margin - .75f && r.bottom <= guard + height - margin + .75f,
        label + ": complete glyph must fit the row: " + mark.name + " " + r);
      require(r.left >= previousX, label + ": RTL placement must not mirror/reorder numeric glyphs");
      previousX = r.left;
      drawn.append(mark.name.substring("text:".length()));
    }
    require(text.contentEquals(drawn), label + ": preserve every digit/separator; got " + drawn);
    return bounds;
  }

  private static void near (float expected, float actual, String message) {
    require(Math.abs(expected - actual) <= 1f, message + ": expected " + expected + ", was " + actual);
  }

  private static void rasterExtent (float expected, int actual, String message) {
    // Outward pixel coverage may add one antialiased pixel at EACH fractional edge.
    // This is not the fit assertion: inside() and clipped/unclipped pixel equality above
    // separately require a real edge margin and zero removed pixels (including every glyph).
    require(Math.abs(expected - actual) <= 2f, message + ": expected " + expected + ", was " + actual);
  }

  private static void geometry (SyntheticEnvironment env, int widthDp, float fontScale) throws Exception {
    Context context = env.configure(fontScale, false, ThemeId.BLUE);
    int width = Screen.dp(widthDp), height = Screen.dp(64), guard = Screen.dp(24);
    for (Sample sample : SAMPLES) {
      Counter counter = counter(context, sample);
      float originalWidth = counter.getWidth();
      for (float alpha : new float[] {1f, .35f}) {
        String label = sample.name + " w=" + widthDp + " font=" + fontScale + " alpha=" + alpha;
        env.configure(fontScale, false, ThemeId.BLUE);
        try (RecordingCanvas ltr = render(counter, width, height, false, alpha, false);
             RecordingCanvas ltrClipped = render(counter, width, height, false, alpha, true)) {
          Rect ltrBounds = inside(ltr, width, height, sample.text, label + " LTR");
          samePixels(ltr, ltrClipped, label + ": LTR row clip must remove zero pixels");
          float outerWidth = originalWidth + Screen.dp(1.5f) * 2f;
          if (alpha == 1f) {
            if (outerWidth <= width - Screen.dp(3) * 2f) {
              rasterExtent((Screen.dp(11) + Screen.dp(1.5f)) * 2f, ltrBounds.height(), label + ": fitting counters retain their normal size");
            } else {
              rasterExtent(width - Screen.dp(3) * 2f, ltrBounds.width(), label + ": wide counters use the available width");
            }
          }
          env.configure(fontScale, true, ThemeId.BLUE);
          try (RecordingCanvas rtl = render(counter, width, height, true, alpha, false);
               RecordingCanvas rtlClipped = render(counter, width, height, true, alpha, true)) {
            Rect rtlBounds = inside(rtl, width, height, sample.text, label + " RTL");
            samePixels(rtl, rtlClipped, label + ": RTL row clip must remove zero pixels");
            near(guard * 2 + width - ltrBounds.right, rtlBounds.left, label + ": mirrored left edge");
            near(guard * 2 + width - ltrBounds.left, rtlBounds.right, label + ": mirrored right edge");
            near(ltrBounds.top, rtlBounds.top, label + ": RTL preserves top");
            near(ltrBounds.bottom, rtlBounds.bottom, label + ": RTL preserves bottom");
          }
        }
      }
      require(originalWidth == counter.getWidth(), sample.name + ": fitting must not change the Counter's text/width");
      require(counter.getVisibility() == 1f, sample.name + ": fitting must not alter Counter visibility");
      require(counter.isMuted() == sample.muted, sample.name + ": fitting preserves mute state");
    }
  }

  private static void gravityContract (SyntheticEnvironment env) throws Exception {
    Context context = env.configure(1f, false, ThemeId.BLUE);
    Counter counter = counter(context, SAMPLES[0]);
    int width = Screen.dp(56), height = Screen.dp(64), guard = Screen.dp(24);
    float oldX = width / 2f + avatarRadius(width), oldY = height / 2f + avatarRadius(width) - Screen.dp(3);
    try (RecordingCanvas old = new RecordingCanvas(width + guard * 2, height + guard * 2);
         RecordingCanvas fixed = render(counter, width, height, false, 1f, false)) {
      old.translate(guard, guard);
      RectF body = new RectF();
      counter.draw(old, oldX, oldY, Gravity.RIGHT, 1f, body);
      near(oldX + Screen.dp(11), body.right, "RIGHT anchors the cap center, not the badge edge");
      Rect oldBounds = rasterBounds(old);
      require(oldBounds.right > guard + width, "Fixture must expose the original right-edge overflow");
      require(oldBounds.bottom > guard + height - Screen.dp(3), "Fixture must expose the missing bottom margin, including outline");
      inside(fixed, width, height, "1", "Corrected production placement");
      try (RecordingCanvas clipped = render(counter, width, height, false, 1f, true)) {
        samePixels(fixed, clipped, "Corrected placement needs no clipping");
      }
    }
  }

  private static void hidden (SyntheticEnvironment env) throws Exception {
    Counter counter = counter(env.configure(2f, true, ThemeId.NIGHT_BLUE), SAMPLES[4]);
    int width = Screen.dp(56), height = Screen.dp(64);
    try (RecordingCanvas canvas = render(counter, width, height, true, 0f, false)) {
      require(rasterBounds(canvas).isEmpty(), "Zero morph decoration must draw no badge pixels");
      require(canvas.marks.isEmpty(), "Zero decoration must draw no glyphs");
    }
    counter.setCount(0, false, "", false);
    try (RecordingCanvas canvas = render(counter, width, height, true, 1f, false)) {
      require(rasterBounds(canvas).isEmpty(), "Read chat must draw no badge pixels");
      require(canvas.marks.isEmpty(), "Read chat must draw no glyphs");
    }
  }
}
