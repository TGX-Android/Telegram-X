package org.thunderdog.challegram.stage8;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.widget.FrameLayout;

import org.thunderdog.challegram.data.ForumRailLayout;
import org.thunderdog.challegram.navigation.ForumNavigationContainer;
import org.thunderdog.challegram.navigation.ForumRailTransition;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.tool.Screen;

import java.util.Collections;
import java.util.List;

import static org.thunderdog.challegram.stage8.SyntheticEnvironment.*;

/** Samples production container composition with opaque departing content, not only final margins. */
final class ForumRailCompositionChecks {
  private static final int AVATAR = 0xffe03030, MAIN = 0xffe5ce50, TOPICS = 0xff30a050, HEADER = 0xff3655d0;

  static void register (List<Stage8SyntheticInstrumentation.Case> cases) {
    for (boolean rtl : new boolean[] {false, true}) {
      for (boolean back : new boolean[] {false, true}) {
        cases.add(new Stage8SyntheticInstrumentation.Case("rail_composition_rtl" + rtl + "_back" + back,
          env -> composition(env, rtl, back)));
      }
    }
  }

  private static void composition (SyntheticEnvironment env, boolean rtl, boolean back) throws Exception {
    Context context = env.configure(1f, rtl, ThemeId.BLUE);
    int w = Screen.dp(360), h = Screen.dp(640), railWidth = Screen.dp(64), headerHeight = HeaderView.getSize(true);
    float x = rtl ? w - railWidth / 2f : railWidth / 2f;
    float sourceY = headerHeight + Screen.dp(96), targetY = headerHeight + Screen.dp(32);
    float radius = Screen.dp(14);
    Paint paint = new Paint(); paint.setColor(AVATAR);
    boolean[] hidden = {false};
    FrameLayout content = new FrameLayout(context);
    View main = new View(context) {
      @Override protected void onDraw (Canvas c) {
        c.drawColor(MAIN);
        if (!hidden[0]) c.drawCircle(x, sourceY, radius, paint);
      }
    };
    main.setWillNotDraw(false);
    main.setElevation(1f); // Source roots may draw after the incoming body despite child order.
    content.addView(main, new FrameLayout.LayoutParams(-1, -1));
    View topics = new View(context); topics.setBackgroundColor(TOPICS);
    FrameLayout.LayoutParams body = new FrameLayout.LayoutParams(-1, -1); body.topMargin = headerHeight;
    content.addView(topics, body);
    View header = new View(context); header.setBackgroundColor(HEADER);
    header.setElevation(2f);
    content.addView(header, new FrameLayout.LayoutParams(-1, headerHeight));
    ForumNavigationContainer root = new ForumNavigationContainer(context, new NavigationController(context), content);
    View staticRail = new View(context) {
      @Override protected void onDraw (Canvas c) {
        c.drawColor(Theme.fillingColor());
        if (!hidden[0]) c.drawCircle(x, targetY, radius, paint);
      }
    };
    staticRail.setWillNotDraw(false);
    root.addView(staticRail, 0, new FrameLayout.LayoutParams(-1, -1));
    try {
      set(root, "session", true);
      invoke(root, "addTopicView", new Class<?>[] {View.class}, topics);
      measure(root, w, h);
      // This account-free sibling stands in for ChatRailView; the production container
      // measures its own rail field explicitly, so lay out the synthetic sibling too.
      measure(staticRail, w, h);
      set(root, "transitionMain", main); set(root, "transitionTopics", topics); set(root, "transitionMainAlpha", 1f);
      ForumRailTransition.Drawing drawing = (c, decorations) -> c.drawCircle(0, 0, 1, paint);
      ForumRailTransition morph = new ForumRailTransition(
        Collections.singletonList(new ForumRailTransition.Avatar(1, x, sourceY, radius, drawing, value -> hidden[0] = value)),
        Collections.singletonList(new ForumRailTransition.Avatar(1, x, targetY, radius, drawing, null)));
      set(root, "avatarMorph", morph);
      // Include reversed samples: a gesture may change its mind before settling.
      float[] trajectory = back ? new float[] {1f, .8f, .4f, .7f, .2f, 0f} : new float[] {0f, .25f, .5f, .9f, .99f, 1f};
      for (float p : trajectory) {
        invoke(root, "setTransitionProgress", new Class<?>[] {float.class}, p);
        topics.setTranslationX(ForumRailLayout.topicTranslation(w, railWidth, p, rtl));
        try (RecordingCanvas canvas = new RecordingCanvas(w, h)) {
          root.draw(canvas);
          int y = Math.round(ForumRailLayout.interpolate(sourceY, targetY, p));
          equal(AVATAR, canvas.bitmap.getPixel(Math.round(x), y), "Moving avatar must not remain behind the opaque departing screen at " + p);
          if (Math.abs(y - targetY) > radius * 2) require(canvas.bitmap.getPixel(Math.round(x), Math.round(targetY)) != AVATAR, "No second static rail avatar during the morph");
          equal(HEADER, canvas.bitmap.getPixel(Math.round(x), headerHeight / 2), "Morph cannot paint over the full-width header");
          equal(w, header.getWidth(), "Header width must not animate");
          equal(w - railWidth, topics.getWidth(), "Topic body width remains measured once");
          if (p > 0f) {
            int insideTopics = rtl ? Math.max(0, Math.round((w - railWidth) * p / 2f)) :
              Math.min(w - 1, w - Math.round((w - railWidth) * p / 2f));
            equal(TOPICS, canvas.bitmap.getPixel(insideTopics, headerHeight + 5), "Elevated source cannot bleed over the incoming topic pane");
          }
        }
      }
      try (RecordingCanvas before = new RecordingCanvas(w, h); RecordingCanvas after = new RecordingCanvas(w, h)) {
        root.draw(before);
        View removed = back ? topics : main;
        content.removeView(removed);
        invoke(root, "onControllerViewRemoved", new Class<?>[] {View.class}, removed);
        root.draw(after);
        for (int y = 0; y < h; y++) for (int col = 0; col < w; col++) {
          int expected = before.bitmap.getPixel(col, y), actual = after.bitmap.getPixel(col, y);
          require(expected == actual, "Endpoint differs at " + col + "," + y + ": " + Integer.toHexString(expected) + " vs " + Integer.toHexString(actual));
        }
      }
      require(!hidden[0], "Completion must restore the source avatar for subsequent Back");
      require(main.getAlpha() == 1f && main.getTranslationX() == 0f, "Completion restores source view properties");
      require(main.getClipBounds() == null, "Completion restores source clipping");
      require(get(root, "avatarMorph") == null && get(root, "transitionMain") == null, "No transient renderer retains the source");
    } finally { root.destroy(); }
  }
}
