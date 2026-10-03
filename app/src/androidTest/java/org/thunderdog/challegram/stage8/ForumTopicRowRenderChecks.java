package org.thunderdog.challegram.stage8;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.component.chat.ForumTopicView;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.tool.Screen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.thunderdog.challegram.stage8.SyntheticEnvironment.equal;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.get;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.require;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.set;

/** Real ForumTopicView measure/draw/clear; account-dependent setTopic binding is NOT covered. */
final class ForumTopicRowRenderChecks {
  private static final int IDENTITY_COLOR = 0xff3e63d7;
  private static final String TITLE = "Synthetic topic with a deliberately long name";
  private static final String PREVIEW = "Synthetic preview with a deliberately long second line";
  private static final String[] COUNTERS = { "999", "@23", "\u266512", "\u27139" };

  static void register (List<Stage8SyntheticInstrumentation.Case> cases) {
    // Phone content widths after the real rail: 320-56, 360-64, 412-64, plus a wide pane.
    for (int width : new int[] {264, 296, 348, 600}) {
      for (float scale : new float[] {1f, 1.3f, 2f}) {
        for (boolean rtl : new boolean[] {false, true}) {
          for (int theme : new int[] {ThemeId.BLUE, ThemeId.NIGHT_BLUE}) {
            String name = "row_geometry_w" + width + "_font" + scale + "_rtl" + rtl + "_theme" + theme;
            cases.add(new Stage8SyntheticInstrumentation.Case(name, env -> geometry(env, width, scale, rtl, theme)));
          }
        }
      }
    }
    cases.add(new Stage8SyntheticInstrumentation.Case("row_general_and_supplementary_identity", ForumTopicRowRenderChecks::identity));
    cases.add(new Stage8SyntheticInstrumentation.Case("row_selection_accessibility_and_clear", ForumTopicRowRenderChecks::selectionAndClear));
    cases.add(new Stage8SyntheticInstrumentation.Case("row_theme_repaint_preserves_geometry", ForumTopicRowRenderChecks::themeRepaint));
  }

  @SuppressWarnings("unchecked")
  private static ForumTopicView fixture (Context context, String title, boolean general, boolean decorated) throws Exception {
    ForumTopicView row = new ForumTopicView(context, null);
    row.setBackground(null);
    TdApi.ForumTopic topic = new TdApi.ForumTopic();
    topic.info = new TdApi.ForumTopicInfo();
    topic.info.chatId = -900000001L;
    topic.info.forumTopicId = general ? 1 : 42;
    topic.info.name = title;
    topic.info.isGeneral = general;
    topic.info.icon = new TdApi.ForumTopicIcon(IDENTITY_COLOR & 0xffffff, 0);
    // Synthetic presentation injection is deliberately separate from production binding.
    set(row, "topic", topic);
    set(row, "preview", PREVIEW);
    set(row, "time", "12:34");
    if (decorated) {
      ((List<String>) get(row, "counters")).addAll(Arrays.asList(COUNTERS));
      List<Drawable> states = (List<Drawable>) get(row, "stateIcons");
      for (int i = 0; i < 4; i++) states.add(new MarkerDrawable("state" + i));
      set(row, "delivery", new MarkerDrawable("delivery"));
      row.setSelection(true, true, false);
    }
    return row;
  }

  private static void layout (ForumTopicView row, int widthDp) {
    row.measure(View.MeasureSpec.makeMeasureSpec(Screen.dp(widthDp), View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    row.layout(0, 0, row.getMeasuredWidth(), row.getMeasuredHeight());
    equal(Screen.dp(widthDp), row.getWidth(), "Row width must honor parent");
    require(row.getHeight() >= Screen.dp(64) && row.getHeight() <= Screen.dp(260), "Row height must be bounded and usable");
  }

  private static RecordingCanvas render (ForumTopicView row) {
    RecordingCanvas canvas = new RecordingCanvas(row.getWidth(), row.getHeight());
    row.draw(canvas);
    return canvas;
  }

  private static void geometry (SyntheticEnvironment env, int width, float scale, boolean rtl, int theme) throws Exception {
    ForumTopicView row = fixture(env.configure(scale, rtl, theme), TITLE, false, true);
    try {
      layout(row, width);
      try (RecordingCanvas canvas = render(row)) {
        RecordingCanvas.Mark identity = null, title = null, preview = null;
        List<RecordingCanvas.Mark> chips = new ArrayList<>();
        for (RecordingCanvas.Mark mark : canvas.marks) {
          RecordingCanvas.inside(mark, row.getWidth(), row.getHeight());
          if (mark.name.equals("roundRect") && mark.color == IDENTITY_COLOR) identity = mark;
          else if (mark.name.equals("roundRect") && mark.color == Theme.badgeColor()) chips.add(mark);
          // Match the production font role even when Android ellipsizes to a single glyph.
          if (mark.textSize == Screen.dp(17 * scale)) title = mark;
          if (mark.textSize == Screen.dp(14 * scale)) preview = mark;
        }
        require(identity != null, "Topic identity bubble must be drawn");
        equal(4, chips.size(), "Each counter category must keep its own chip");
        List<RecordingCanvas.Mark> firstLine = new ArrayList<>();
        firstLine.add(identity);
        firstLine.add(canvas.one("text:12:34"));
        firstLine.add(canvas.one("delivery"));
        for (int i = 0; i < 4; i++) firstLine.add(canvas.one("state" + i));
        if (title != null) firstLine.add(title);
        for (int i = 0; i < firstLine.size(); i++) {
          RecordingCanvas.separate(canvas.one("circle"), firstLine.get(i));
          for (int j = 0; j < i; j++) RecordingCanvas.separate(firstLine.get(i), firstLine.get(j));
        }
        for (int i = 0; i < chips.size(); i++) {
          for (int j = 0; j < i; j++) RecordingCanvas.separate(chips.get(i), chips.get(j));
          if (preview != null) RecordingCanvas.separate(preview, chips.get(i));
          for (RecordingCanvas.Mark top : firstLine) RecordingCanvas.separate(top, chips.get(i));
        }
        for (String counter : COUNTERS) {
          RecordingCanvas.Mark label = canvas.one("text:" + counter);
          int containing = 0;
          for (RecordingCanvas.Mark chip : chips) if (chip.bounds.contains(label.bounds)) containing++;
          equal(1, containing, "Counter text must fit exactly one chip: " + counter);
          equal(Theme.badgeTextColor(), label.color, "Counter uses current theme text color");
        }
        // Check real raster output, not only a copied layout formula or a draw-call count.
        int x = Math.round(identity.bounds.left + identity.bounds.width() * .2f);
        int y = Math.round(identity.bounds.centerY());
        equal(IDENTITY_COLOR, canvas.bitmap.getPixel(x, y), "Identity slot must reach the bitmap");
        equal(Theme.textDecentColor(), canvas.one("text:12:34").color, "Timestamp uses current theme");
      }
    } finally {
      row.clear();
    }
  }

  private static void identity (SyntheticEnvironment env) throws Exception {
    Context context = env.configure(1f, false, ThemeId.BLUE);
    for (boolean general : new boolean[] {true, false}) {
      ForumTopicView row = fixture(context, "\ud83d\ude80 Synthetic", general, false);
      try {
        layout(row, 360);
        try (RecordingCanvas canvas = render(row)) {
          canvas.one(general ? "text:#" : "text:\ud83d\ude80");
          require(row.getTopic().info.isGeneral == general, "Drawing must preserve General/topic identity");
          equal(general ? 1 : 42, row.getTopic().info.forumTopicId, "Identity does not become common stream");
        }
      } finally { row.clear(); }
    }
  }

  private static void selectionAndClear (SyntheticEnvironment env) throws Exception {
    ForumTopicView row = fixture(env.configure(1f, false, ThemeId.BLUE), TITLE, false, false);
    try {
      row.setContentDescription("Synthetic topic selection");
      row.setSelection(true, true, false);
      AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain();
      try {
        row.onInitializeAccessibilityNodeInfo(node);
        require(node.isCheckable() && node.isChecked() && node.isSelected(), "Selection must be exposed to accessibility");
      } finally { node.recycle(); }
      row.clear();
      require(row.getTopic() == null && !row.isSelected() && row.getContentDescription() == null, "Clear must remove recycled identity");
      node = AccessibilityNodeInfo.obtain();
      try {
        row.onInitializeAccessibilityNodeInfo(node);
        require(!node.isCheckable() && !node.isChecked(), "Cleared row must not retain selection-mode accessibility");
      } finally { node.recycle(); }
      layout(row, 360);
      try (RecordingCanvas canvas = render(row)) {
        require(canvas.marks.isEmpty(), "Cleared row must not draw previous topic data");
      }
      row.setSelection(false, false, false);
      // animate=true reads persistent reduce-motion settings in the current row; do not use
      // it in an account-free fixture. This suite does not claim animation/reduce-motion QA.
      row.setSelection(true, false, false);
      require(get(row, "selectionAnimator") == null, "Non-animated selection must not allocate an animator");
    } finally { row.clear(); }
  }

  private static void themeRepaint (SyntheticEnvironment env) throws Exception {
    ForumTopicView row = fixture(env.configure(1f, false, ThemeId.BLUE), TITLE, false, false);
    try {
      layout(row, 360);
      RectF before;
      int color;
      try (RecordingCanvas canvas = render(row)) {
        before = new RectF(canvas.one("text:12:34").bounds);
        color = canvas.one("text:12:34").color;
      }
      env.configure(1f, false, ThemeId.NIGHT_BLUE);
      try (RecordingCanvas canvas = render(row)) {
        require(before.equals(canvas.one("text:12:34").bounds), "Theme repaint must not move identity/time slots");
        equal(Theme.textDecentColor(), canvas.one("text:12:34").color, "Reused row must read new theme");
        require(color != canvas.one("text:12:34").color, "Selected themes must exercise different text colors");
      }
    } finally { row.clear(); }
  }

  /** Sentinel occupies the actual Drawable transform used for each production status slot. */
  private static final class MarkerDrawable extends Drawable {
    private final String name;
    private final Paint paint = new Paint();
    MarkerDrawable (String name) { this.name = name; paint.setColor(0xffb42c87); }
    @Override public int getIntrinsicWidth () { return 16; }
    @Override public int getIntrinsicHeight () { return 16; }
    @Override public void draw (Canvas canvas) {
      ((RecordingCanvas) canvas).mark(name, new RectF(getBounds()), paint.getColor());
      canvas.drawRect(getBounds(), paint);
    }
    @Override public void setAlpha (int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter (ColorFilter filter) { /* Preserve sentinel raster color. */ }
    @Override public int getOpacity () { return PixelFormat.OPAQUE; }
  }
}
