package org.thunderdog.challegram.stage8;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import org.thunderdog.challegram.navigation.ForumNavigationContainer;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ForumEditorTransitionChecks;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.tool.Screen;

import java.util.LinkedHashMap;
import java.util.List;

import static org.thunderdog.challegram.stage8.SyntheticEnvironment.equal;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.get;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.invoke;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.measure;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.require;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.set;

/**
 * Real navigation-container measurement, layout, hit mapping, raster output and teardown.
 * Does NOT recreate ForumRailLayout's pure tests. Rail population/avatar rendering and
 * Activity/IME/predictive-Back/account-switch lifecycle are deliberately outside this seam.
 */
final class ForumNavigationRenderChecks {
  private static final int CONTENT_COLOR = 0xff168c6e;
  private static final int HEADER_COLOR = 0xff4361ee;

  static void register (List<Stage8SyntheticInstrumentation.Case> cases) {
    for (int width : new int[] {320, 360, 412, 600}) {
      for (boolean rtl : new boolean[] {false, true}) {
        cases.add(new Stage8SyntheticInstrumentation.Case("navigation_view_geometry_w" + width + "_rtl" + rtl,
          env -> geometry(env, width, rtl)));
      }
    }
    cases.add(new Stage8SyntheticInstrumentation.Case("navigation_teardown_listener_and_saved_state", ForumNavigationRenderChecks::teardown));
    cases.add(new Stage8SyntheticInstrumentation.Case("navigation_empty_stack_releases_content_width", ForumNavigationRenderChecks::emptyStack));
    ForumEditorTransitionChecks.register(cases);
  }

  private static void geometry (SyntheticEnvironment env, int widthDp, boolean rtl) throws Exception {
    Context context = env.configure(1f, rtl, ThemeId.BLUE);
    NavigationController navigation = new NavigationController(context);
    FrameLayout content = new FrameLayout(context);
    int headerHeight = HeaderView.getSize(true);
    View topics = new View(context);
    topics.setBackgroundColor(CONTENT_COLOR);
    FrameLayout.LayoutParams bodyParams = new FrameLayout.LayoutParams(-1, -1);
    bodyParams.topMargin = headerHeight;
    content.addView(topics, bodyParams);
    View header = new View(context);
    header.setBackgroundColor(HEADER_COLOR);
    content.addView(header, new FrameLayout.LayoutParams(-1, headerHeight));
    ForumNavigationContainer root = new ForumNavigationContainer(context, navigation, content);
    try {
      int width = Screen.dp(widthDp), height = Screen.dp(240);
      int railWidth = Screen.dp(widthDp == 320 ? 56 : widthDp == 600 ? 72 : 64);
      invoke(root, "addTopicView", new Class<?>[] {View.class}, topics);
      for (boolean active : new boolean[] {false, true}) {
        // No enable(): that would create a live TDLib-backed chat-list slice. Only layout
        // state is synthetic; measurement, margins, hit mapping and drawing are production.
        set(root, "session", active);
        root.requestLayout();
        measure(root, width, height);
        int occupied = active ? railWidth : 0;
        equal(width, content.getMeasuredWidth(), "The navigation root must always retain full width");
        equal(width, header.getMeasuredWidth(), "The header spans the rail and topic list");
        equal(height, content.getMeasuredHeight(), "Rail must not shorten the content vertically");
        equal(width - occupied, topics.getMeasuredWidth(), "Only the topic list reserves rail space");
        equal(rtl ? 0 : occupied, topics.getLeft(), "Topic list's physical leading edge");
        equal(rtl ? width - occupied : width, topics.getRight(), "Topic list's physical trailing edge");
        for (int x : new int[] {0, width / 2, width - 1}) {
          boolean outsideContent = x < topics.getLeft() || x >= topics.getRight();
          require(root.isRailTouch(x, headerHeight + 1) == outsideContent, "Hit region must agree with topic body at " + x);
          require(!root.isRailTouch(x, headerHeight - 1), "Header Back/menu touches must never belong to the rail");
          equal(x, Math.round(root.contentX(x, headerHeight - 1)), "Header coordinates stay full width");
        }
        require(!root.isRailTouch(-1, headerHeight + 1) && !root.isRailTouch(width, headerHeight + 1), "Out-of-viewport touches are not rail touches");
        equal(0, Math.round(root.contentX(topics.getLeft(), headerHeight + 1)), "Topic gesture origin maps to local zero");
        equal(topics.getWidth() - 1, Math.round(root.contentX(topics.getRight() - 1, headerHeight + 1)), "Topic gesture right edge must agree");
        try (RecordingCanvas canvas = new RecordingCanvas(width, height)) {
          root.draw(canvas);
          equal(HEADER_COLOR, canvas.bitmap.getPixel(0, headerHeight / 2), "Header covers the left edge above the rail");
          equal(HEADER_COLOR, canvas.bitmap.getPixel(width - 1, headerHeight / 2), "Header covers the right edge above the rail");
          equal(CONTENT_COLOR, canvas.bitmap.getPixel(topics.getLeft(), height / 2), "Topic list paints its first pixel");
          equal(CONTENT_COLOR, canvas.bitmap.getPixel(topics.getRight() - 1, height / 2), "Topic list paints its last pixel");
          if (occupied > 0) {
            int outside = rtl ? topics.getRight() : topics.getLeft() - 1;
            equal(0, canvas.bitmap.getPixel(outside, height / 2), "Topic body leaves rail space below the header");
          }
        }
      }
      View messages = new View(context);
      messages.setBackgroundColor(0xffe76f51);
      content.addView(messages, 1, new FrameLayout.LayoutParams(-1, -1));
      measure(root, width, height);
      equal(width, messages.getWidth(), "Incoming messages are full width even while topics remain attached behind them");
      equal(width, root.targetContentWidth(), "Message width prediction must not inherit a forum-list inset");
      invoke(root, "removeTopicView", new Class<?>[] {View.class}, topics);
      content.removeView(topics);
      measure(root, width, height);
      require(!root.isRailTouch(rtl ? width - 1 : 0, headerHeight + 1), "Completed topic transition leaves no rail hit area");
      equal(0, Math.round(root.contentX(0, headerHeight + 1)), "Full-width messages retain native gesture coordinates");
      // Back preview reattaches the existing topic body under the still-full-width message view.
      content.addView(topics, 0, bodyParams);
      invoke(root, "addTopicView", new Class<?>[] {View.class}, topics);
      measure(root, width, height);
      equal(width, messages.getWidth(), "Back preview never shrinks the departing topic history");
      equal(width - railWidth, topics.getWidth(), "Back preview restores the list inset before transition commit");
      content.removeView(messages);
      measure(root, width, height);
      require(root.isRailTouch(rtl ? width - 1 : 0, headerHeight + 1), "Back restores the list's rail hit area");
      equal(width, header.getWidth(), "Back keeps the header full width");
      root.setBottomInset(Screen.dp(24));
      measure(root, width, height);
      require(!root.isRailTouch(rtl ? width - 1 : 0, height - 1), "System navigation inset is not a chat target");
    } finally { root.destroy(); }
  }

  @SuppressWarnings("unchecked")
  private static void teardown (SyntheticEnvironment env) throws Exception {
    Context context = env.configure(1f, false, ThemeId.BLUE);
    NavigationController navigation = new NavigationController(context);
    View content = new View(context);
    List<?> listeners = (List<?>) get(navigation.getStack(), "changeListeners");
    int before = listeners.size();
    ForumNavigationContainer root = new ForumNavigationContainer(context, navigation, content);
    try {
      equal(before + 1, listeners.size(), "Container registers one stack listener");
      require(listeners.contains(root), "Registered listener must be this container");
      LinkedHashMap<Long, Bundle> states = (LinkedHashMap<Long, Bundle>) get(root, "savedTopics");
      for (long id = 1; id <= 18; id++) {
        Bundle state = new Bundle();
        state.putInt("synthetic_scroll", (int) id);
        states.put(id, state);
      }
      states.get(2L); // A recently accessed topic should survive the LRU trim.
      invoke(root, "rememberTopics", new Class<?>[0]);
      equal(16, states.size(), "Navigation state cache must remain bounded");
      require(states.containsKey(2L) && !states.containsKey(1L) && !states.containsKey(3L), "LRU trim must retain recently accessed identity");
      Bundle output = new Bundle();
      root.saveState(output);
      require(output.isEmpty(), "Inactive synthetic container must not persist an account identity");
      root.destroy();
      root.destroy();
      equal(before, listeners.size(), "Destroy must unregister exactly once");
      require(!listeners.contains(root) && states.isEmpty(), "Destroy releases subscriptions and saved topic state");
      require(!(Boolean) get(root, "session") && get(root, "rail") == null && get(root, "tdlib") == null,
        "Destroy must leave no rail session or account reference");
      require(content.getParent() == root, "Rail teardown must not destroy the existing navigation content");
      navigation.getStack().set(new ViewController<?>[0]);
      require(states.isEmpty(), "Later stack changes must not repopulate a destroyed container");
    } finally { root.destroy(); }
  }

  private static void emptyStack (SyntheticEnvironment env) throws Exception {
    Context context = env.configure(1f, false, ThemeId.BLUE);
    FrameLayout content = new FrameLayout(context);
    View topics = new View(context);
    content.addView(topics, new FrameLayout.LayoutParams(-1, -1));
    ForumNavigationContainer root = new ForumNavigationContainer(context, new NavigationController(context), content);
    try {
      set(root, "session", true);
      invoke(root, "addTopicView", new Class<?>[] {View.class}, topics);
      measure(root, Screen.dp(360), Screen.dp(240));
      require(topics.getWidth() < root.getWidth(), "Fixture must begin with reserved rail width");
      root.refresh(); // Resolves the actual empty stack; never opens a controller or account.
      measure(root, Screen.dp(360), Screen.dp(240));
      equal(root.getWidth(), topics.getWidth(), "An empty stack must release the rail's reserved width");
      require(!root.isRailTouch(0, HeaderView.getSize(true) + 1), "No stale rail hit area after session close");
    } finally { root.destroy(); }
  }
}
