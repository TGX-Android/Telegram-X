package org.thunderdog.challegram.stage8;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.component.dialogs.ForumEmojiSlotChecks;
import org.thunderdog.challegram.data.ForumNavigation;
import org.thunderdog.challegram.data.ForumTabsState;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.ForumTopicsTabsLayout;
import org.thunderdog.challegram.widget.ForumTopicsTabsView;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.thunderdog.challegram.stage8.SyntheticEnvironment.allocate;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.equal;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.get;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.invoke;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.measure;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.require;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.set;

/**
 * Framework-only, finite instrumentation suite. No JUnit/AndroidX Test dependency is required.
 *
 * Integration (parent-owned build file; -Pstage8.synthetic=true): defaultConfig.testInstrumentationRunner =
 * "org.thunderdog.challegram.stage8.Stage8SyntheticInstrumentation".
 * Use a dedicated, debuggable applicationId ending in .stage8synthetic and pass
 * -e stage8Synthetic true. A fresh separate install on the same phone is supported; no global
 * offline mode/account action is needed. The synthetic target manifest MUST remove INTERNET
 * permission and all providers, services, receivers and activities/activity-aliases. The
 * pre-Application guard below rejects an ordinary manifest, including provider startup paths.
 * Never install over the base application or launch MainActivity. No account data is needed.
 *
 * Deliberately not an ActivityScenario, account-binding, real emoji-download, screenshot
 * golden, animated-navigation, or full Stage8 acceptance suite. Reflection seams fail loudly
 * when production fields/lambda shape change. See SyntheticEnvironment for their scope.
 */
public final class Stage8SyntheticInstrumentation extends Instrumentation {
  public interface Check { void run (SyntheticEnvironment environment) throws Exception; }

  public static final class Case {
    final String name;
    final Check check;
    public Case (String name, Check check) { this.name = name; this.check = check; }
  }

  @Override public Application newApplication (ClassLoader loader, String className, Context context)
      throws ClassNotFoundException, IllegalAccessException, InstantiationException {
    // This hook runs before Application.onCreate; never construct BaseApplication at all.
    assertIsolatedPackage(context);
    StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectNetwork().penaltyDeathOnNetwork().build());
    return super.newApplication(loader, Application.class.getName(), context);
  }

  @SuppressWarnings("deprecation")
  private static void assertIsolatedPackage (Context context) {
    if (!context.getPackageName().endsWith(".stage8synthetic")) {
      throw new IllegalStateException("Stage8 requires a separate .stage8synthetic applicationId");
    }
    PackageManager manager = context.getPackageManager();
    try {
      PackageInfo info = manager.getPackageInfo(context.getPackageName(), PackageManager.GET_PROVIDERS |
        PackageManager.GET_SERVICES | PackageManager.GET_RECEIVERS | PackageManager.GET_ACTIVITIES);
      if (info.sharedUserId != null || (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0 ||
          manager.checkPermission(android.Manifest.permission.INTERNET, context.getPackageName()) == PackageManager.PERMISSION_GRANTED ||
          manager.checkPermission(android.Manifest.permission.ACCESS_NETWORK_STATE, context.getPackageName()) == PackageManager.PERMISSION_GRANTED ||
          (info.providers != null && info.providers.length != 0) ||
          (info.services != null && info.services.length != 0) ||
          (info.receivers != null && info.receivers.length != 0) ||
          (info.activities != null && info.activities.length != 0)) {
        throw new IllegalStateException("Unsafe synthetic target: require private debug UID, no network permissions, and no app components");
      }
    } catch (PackageManager.NameNotFoundException failure) {
      throw new IllegalStateException("Cannot validate synthetic target manifest", failure);
    }
  }

  @Override public void onCreate (Bundle arguments) {
    super.onCreate(arguments);
    if (arguments == null || !"true".equals(arguments.getString("stage8Synthetic"))) {
      Bundle result = new Bundle();
      result.putString("shortMsg", "Explicit -e stage8Synthetic true acknowledgement required");
      finish(Activity.RESULT_CANCELED, result);
      return;
    }
    start();
  }

  @Override public void onStart () {
    List<Case> cases = new ArrayList<>();
    ForumTopicRowRenderChecks.register(cases);
    ForumNavigationRenderChecks.register(cases);
    ForumRailTransitionChecks.register(cases);
    ForumRailCompositionChecks.register(cases);
    ForumListRefreshChecks.register(cases);
    ForumRailBadgeChecks.register(cases);
    ForumMediaSizingChecks.register(cases);
    ForumEmojiSlotChecks.register(cases);
    org.thunderdog.challegram.ui.ForumTopicEditorSearchChecks.register(cases);
    registerForumTabsChecks(cases);
    cases.add(new Case("forum_upstream_component_demo", env -> ForumUpstreamDemo.render(this, env)));
    cases.add(new Case("notification_read_scope_survives_android_bundle", Stage8SyntheticInstrumentation::notificationReadScope));
    Handler main = new Handler(Looper.getMainLooper());
    int failed = 0, completed = 0;
    long deadline = SystemClock.elapsedRealtime() + 90000;
    for (int index = 0; index < cases.size(); index++) {
      Case test = cases.get(index);
      Bundle status = new Bundle();
      status.putString("id", "Stage8Synthetic");
      status.putString("class", getClass().getName());
      status.putString("test", test.name);
      status.putInt("current", index + 1);
      status.putInt("numtests", cases.size());
      sendStatus(1, status);
      AtomicReference<Throwable> error = new AtomicReference<>();
      CountDownLatch done = new CountDownLatch(1);
      Runnable work = () -> {
        try (SyntheticEnvironment environment = new SyntheticEnvironment(getTargetContext())) {
          try {
            test.check.run(environment);
          } finally {
            environment.assertNoAccountInitialization();
          }
        } catch (Throwable failure) {
          error.set(failure);
        } finally {
          done.countDown();
        }
      };
      main.post(work);
      boolean finished = false;
      try {
        long remaining = deadline - SystemClock.elapsedRealtime();
        finished = remaining > 0 && done.await(Math.min(8000, remaining), TimeUnit.MILLISECONDS);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        error.set(interrupted);
      }
      if (!finished) {
        main.removeCallbacks(work);
        error.set(new AssertionError("Bound exceeded; aborting suite without queuing more UI work"));
      }
      Throwable failure = error.get();
      if (failure != null) {
        failed++;
        StringWriter trace = new StringWriter();
        failure.printStackTrace(new PrintWriter(trace));
        status.putString("stack", trace.toString());
        status.putString("stream", "\nFAIL " + test.name + "\n" + trace);
      } else {
        status.putString("stream", "\nPASS " + test.name + "\n");
      }
      sendStatus(failure == null ? 0 : -2, status);
      completed++;
      if (!finished) break;
    }
    Bundle result = new Bundle();
    result.putInt("numtests", completed);
    result.putInt("numfailures", failed);
    result.putString("stream", "\nStage8 synthetic: " + completed + "/" + cases.size() +
      " completed, " + failed + " failed. Synthetic presentation checks only.\n");
    finish(failed == 0 && completed == cases.size() ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
  }

  private static void notificationReadScope (SyntheticEnvironment env) throws Exception {
    for (boolean legacy : new boolean[] {false, true}) {
      android.content.Intent intent = new android.content.Intent();
      intent.putExtra("account_id", 0);
      intent.putExtra("category", 0);
      intent.putExtra("chat_id", -900000001L);
      intent.putExtra("max_notification_id", 42);
      intent.putExtra("notification_group_id", 3);
      intent.putExtra("need_reply", true);
      intent.putExtra("mentions", true);
      intent.putExtra("message_ids", new long[] {101, 102});
      tgx.td.Td.put(intent, "topic_id", new TdApi.MessageTopicForum(9));
      if (!legacy) intent.putExtra("read_forum_topic_ids", new int[] {7, 9});
      android.os.Parcel parcel = android.os.Parcel.obtain();
      try {
        parcel.writeBundle(intent.getExtras());
        parcel.setDataPosition(0);
        org.thunderdog.challegram.telegram.TdlibNotificationExtras extras =
          org.thunderdog.challegram.telegram.TdlibNotificationExtras.parse(parcel.readBundle(Stage8SyntheticInstrumentation.class.getClassLoader()));
        require(extras != null && extras.areMentions, "Notification extras survive real Android parceling");
        equal(9, ((TdApi.MessageTopicForum) extras.topicId).forumTopicId, "Latest topic remains the open/reply destination");
        int[] scope = (int[]) get(extras, "readForumTopicIds");
        require(legacy ? scope == null : Arrays.equals(new int[] {7, 9}, scope),
          "Aggregate read scope stays independent; legacy intents never borrow the latest topic");
      } finally { parcel.recycle(); }
    }
  }

  // These additions leave the original registrations and pre-Application security guards
  // intact. No real Activity, Settings, TDLib, account or custom-emoji download is initialized.
  private static void registerForumTabsChecks (List<Case> cases) {
    for (int placement : new int[] {ForumTabsState.TOP, ForumTabsState.START, ForumTabsState.BOTTOM}) {
      for (float scale : new float[] {1f, 2f}) for (boolean rtl : new boolean[] {false, true}) {
        for (int theme : new int[] {ThemeId.BLUE, ThemeId.NIGHT_BLUE}) {
          cases.add(new Case("tabs_render_p" + placement + "_font" + scale + "_rtl" + rtl + "_theme" + theme,
            env -> tabsGeometry(env, placement, scale, rtl, theme)));
        }
      }
    }
    cases.add(new Case("tabs_server_order_hidden_and_off_page_selection", Stage8SyntheticInstrumentation::tabsOrder));
    cases.add(new Case("tabs_independent_general_plain_receivers_and_recycle", Stage8SyntheticInstrumentation::tabsIcons));
    cases.add(new Case("tabs_large_list_rename_mute_diff_keeps_scroll", Stage8SyntheticInstrumentation::tabsIncremental));
    cases.add(new Case("tabs_controller_replacement_bundle_and_selection", Stage8SyntheticInstrumentation::tabsRestore));
    for (float scale : new float[] {1f, 2f}) for (boolean rtl : new boolean[] {false, true}) {
      cases.add(new Case("tabs_placement_reveals_selection_font" + scale + "_rtl" + rtl,
        env -> tabsPlacementReveal(env, scale, rtl)));
    }
    cases.add(new Case("tabs_pagination_geometry_loading_retry_and_actions", Stage8SyntheticInstrumentation::tabsStates));
    cases.add(new Case("tabs_theme_repaint_and_idempotent_destroy", Stage8SyntheticInstrumentation::tabsTheme));
  }

  private static TdApi.ForumTopic tabTopic (int id, String name) {
    TdApi.ForumTopic topic = new TdApi.ForumTopic();
    topic.info = new TdApi.ForumTopicInfo();
    topic.info.chatId = -900000001L;
    topic.info.forumTopicId = id;
    topic.info.name = name;
    topic.info.icon = new TdApi.ForumTopicIcon(0x3e63d7, 0);
    topic.notificationSettings = new TdApi.ChatNotificationSettings();
    return topic;
  }

  private static List<TdApi.ForumTopic> tabTopics (int count) {
    List<TdApi.ForumTopic> result = new ArrayList<>();
    for (int i = 0; i < count; i++) result.add(tabTopic(i + 2, "Topic " + (i + 2)));
    return result;
  }

  private static final class TabsOwner extends ViewController<Void> {
    private TabsOwner (Context context) { super(context, null); }
    @Override protected View onCreateView (Context context) { throw new AssertionError("No controller lifecycle in synthetic tabs"); }
    @Override public int getId () { return 0; }
  }

  private static final class TabsFixture implements AutoCloseable, ForumTopicsTabsView.Listener {
    final ForumTopicsTabsView view;
    final RecyclerView list;
    final LinearLayoutManager layout;
    int selected = -1, creates, cycles, loads, retries;

    TabsFixture () throws Exception {
      // Same constructor-free controller seam as the existing synthetic views. Null context
      // falls back to SyntheticEnvironment's scoped appContext; tdlib remains genuinely null.
      TabsOwner owner = allocate(TabsOwner.class);
      view = new ForumTopicsTabsView(owner, this);
      list = (RecyclerView) get(view, "list");
      layout = (LinearLayoutManager) list.getLayoutManager();
      require(owner.tdlib() == null && owner.context() == null, "No Activity or TDLib was attached to the synthetic owner");
    }

    void layout () throws Exception {
      boolean side = (Integer) get(view, "placement") == ForumTabsState.START;
      measure(view, side ? view.recommendedSideWidth() : Screen.dp(348), side ? Screen.dp(360) : view.recommendedHorizontalHeight());
    }

    void settle () throws Exception {
      // Drive the real layout/anchor code synchronously without attaching to a Window or
      // running a Looper. This boolean opens only the widget's deferred-layout guard.
      set(view, "attached", true);
      for (int i = 0; i < 4; i++) { layout(); invoke(view, "settle", new Class<?>[0]); }
      layout();
    }

    int index (int id) {
      RecyclerView.Adapter<?> adapter = list.getAdapter();
      for (int i = 0; i < adapter.getItemCount(); i++) if (adapter.getItemId(i) == id) return i;
      return -1;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    RecyclerView.ViewHolder holder (int position) throws Exception {
      RecyclerView.Adapter adapter = list.getAdapter();
      RecyclerView.ViewHolder holder = adapter.createViewHolder(list, adapter.getItemViewType(position));
      adapter.bindViewHolder(holder, position);
      View row = holder.itemView;
      row.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        View.MeasureSpec.makeMeasureSpec(view.getHeight(), View.MeasureSpec.AT_MOST));
      row.layout(0, 0, row.getMeasuredWidth(), row.getMeasuredHeight());
      return holder;
    }

    @Override public void onSelectTopic (int id) { selected = id; }
    @Override public void onNewTopic () { creates++; }
    @Override public void onCyclePlacement () { cycles++; }
    @Override public void onLoadMore () { loads++; }
    @Override public void onRetry () { retries++; }
    @Override public void close () { view.destroy(); }
  }

  private static void tabsGeometry (SyntheticEnvironment env, int placement, float scale, boolean rtl, int theme) throws Exception {
    env.configure(scale, rtl, theme);
    try (TabsFixture fixture = new TabsFixture()) {
      fixture.view.setPlacement(placement, ForumTabsState.BOTTOM);
      TdApi.ForumTopic decorated = tabTopic(42, "Synthetic topic with a very long readable label");
      decorated.unreadCount = 123456789; decorated.isPinned = true; decorated.info.isClosed = true;
      decorated.notificationSettings.muteFor = 60;
      fixture.view.setTopics(Arrays.asList(tabTopic(ForumNavigation.GENERAL_TOPIC_ID, "General"), decorated), null, 0, true, true, null, false);
      fixture.layout();
      View button = (View) get(fixture.view, "placementButton"), state = (View) get(fixture.view, "stateButton");
      boolean side = placement == ForumTabsState.START;
      if (side) equal(Screen.dp(scale > 1.6f ? 72 : 64), fixture.view.getWidth(), "Side rail stays narrow at normal/large fonts");
      else equal(Screen.dp(44), fixture.view.getHeight(), "Horizontal strip remains compact and single-line through 2x fonts");
      require(button.getVisibility() == View.VISIBLE && button.isClickable() && button.isFocusable(), "Placement is always available");
      equal(Screen.dp(48), side ? button.getHeight() : button.getWidth(), "Fixed placement touch target");
      require(side ? fixture.list.getBottom() <= state.getTop() && state.getBottom() <= button.getTop() : rtl
        ? button.getRight() <= state.getLeft() && state.getRight() <= fixture.list.getLeft()
        : fixture.list.getRight() <= state.getLeft() && state.getRight() <= button.getLeft(), "Controls stay outside the scroll viewport");
      require(fixture.layout.getOrientation() == (side ? RecyclerView.VERTICAL : RecyclerView.HORIZONTAL), "Correct scrolling axis");
      require(fixture.layout.getReverseLayout() == (!side && rtl), "RTL reverses only the horizontal data axis");
      try (RecordingCanvas canvas = new RecordingCanvas(fixture.view.getWidth(), fixture.view.getHeight())) {
        fixture.view.draw(canvas);
        int ink = 0;
        for (int y = button.getTop(); y < button.getBottom(); y++) for (int x = button.getLeft(); x < button.getRight(); x++) {
          if (canvas.bitmap.getPixel(x, y) != Theme.fillingColor()) ink++;
        }
        require(ink > 8, "Placement glyph must reach the raster in every theme/orientation");
      }
      RecyclerView.ViewHolder holder = fixture.holder(fixture.index(42));
      TextView label = (TextView) ((ViewGroup) holder.itemView).getChildAt(0);
      require(label.getEllipsize() == TextUtils.TruncateAt.END && label.getMaxLines() >= 1 && label.getMaxLines() <= (side ? 2 : 1), "Side has up to two short lines; horizontal never has a second line");
      require(label.getWidth() > 0 && label.getHeight() >= label.getLineHeight(), "Large-font label has a readable text slot");
      equal(Screen.dp(side ? 24 : 20), (Integer) invoke(holder.itemView, "topicIconSize", new Class<?>[0]), "Small icons do not grow with font scale");
      require(Math.abs(label.getTextSize() - Screen.sp(side ? 11 : 14)) <= 1, "Compact native text sizes still honor font scale");
      if (side) {
        require(holder.itemView.getHeight() >= Screen.dp(64) && holder.itemView.getHeight() <= Screen.dp(76), "Side rows remain 64-76dp without a separate status row");
        equal(holder.itemView.getHeight() - Screen.dp(4), label.getBottom(), "Status indicators do not reserve space after the short label");
      } else {
        require(Math.abs(label.getTop() + label.getHeight() / 2f - holder.itemView.getHeight() / 2f) <= 1,
          "Horizontal label shares the center line with icon, mute/closed/pin and unread");
      }
      require(holder.itemView.getContentDescription().toString().contains(decorated.info.name), "Full title remains available to TalkBack");
      try (RecordingCanvas canvas = new RecordingCanvas(holder.itemView.getWidth(), holder.itemView.getHeight())) { holder.itemView.draw(canvas); }
      if (!side) {
        RecyclerView.ViewHolder all = fixture.holder(0);
        TextView allLabel = (TextView) ((ViewGroup) all.itemView).getChildAt(0);
        require(allLabel.getLayout() != null && allLabel.getLayout().getEllipsisCount(0) == 0,
          "All must retain its complete localized label beside the icon, also at large fonts/RTL");
        require(allLabel.getWidth() >= Math.ceil(allLabel.getPaint().measureText(allLabel.getText().toString())),
          "All width includes the real text advance, independent of the minimum cell width");
      }
      button.performClick(); equal(1, fixture.cycles, "Placement callback is independent of scrolling/loading");
    }
  }

  private static void tabsOrder (SyntheticEnvironment env) throws Exception {
    env.configure(1f, false, ThemeId.BLUE);
    try (TabsFixture fixture = new TabsFixture()) {
      TdApi.ForumTopic general = tabTopic(1, "General"), plain = tabTopic(42, "Plain"), hidden = tabTopic(43, "Hidden"), selected = tabTopic(99, "Off-page");
      hidden.info.isHidden = true; plain.isPinned = true;
      fixture.view.setTopics(Arrays.asList(general, plain, hidden, plain), selected, 99, true, false, null, true);
      fixture.layout();
      assertTabIds(fixture, 0, 1, 42, 99, Long.MIN_VALUE);
      fixture.view.setTopics(Arrays.asList(plain, selected, general), selected, 99, false, false, null, true);
      fixture.layout();
      assertTabIds(fixture, 0, 42, 99, 1);
      hidden.info.forumTopicId = 99;
      fixture.view.setTopics(Collections.singletonList(general), hidden, 99, false, false, null, true);
      fixture.layout(); assertTabIds(fixture, 0, 1);
    }
  }

  private static void assertTabIds (TabsFixture fixture, long... ids) {
    RecyclerView.Adapter<?> adapter = fixture.list.getAdapter();
    equal(ids.length, adapter.getItemCount(), "Expected visible tabs only");
    for (int i = 0; i < ids.length; i++) equal(ids[i], adapter.getItemId(i), "Server order at " + i);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void tabsIcons (SyntheticEnvironment env) throws Exception {
    env.configure(1f, false, ThemeId.BLUE);
    try (TabsFixture fixture = new TabsFixture()) {
      TdApi.ForumTopic general = tabTopic(ForumNavigation.GENERAL_TOPIC_ID, "Same"), plain = tabTopic(42, "Same");
      // No isGeneral flag: the central int32 identity must still choose General's own glyph.
      fixture.view.setTopics(Arrays.asList(general, plain), null, 42, false, false, null, true);
      fixture.layout();
      RecyclerView.ViewHolder a = fixture.holder(1), b = fixture.holder(2);
      require(get(a.itemView, "receiver") != get(b.itemView, "receiver"), "Each holder owns its own receiver");
      int generalHash = tabIconHash(a.itemView), plainHash = tabIconHash(b.itemView);
      require(generalHash != plainHash, "General and plain-topic glyphs are visually distinct");
      fixture.list.getAdapter().onViewRecycled(a);
      require(get(a.itemView, "item") == null && get(b.itemView, "item") != null, "Recycling clears only its own identity");
      equal(plainHash, tabIconHash(b.itemView), "Recycling another holder cannot change this icon");
      AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
      try {
        b.itemView.onInitializeAccessibilityNodeInfo(info);
        require(info.isSelected() && info.isChecked() && info.isCheckable(), "TalkBack exposes selected topic state");
      } finally { info.recycle(); }
    }
  }

  private static int tabIconHash (View row) throws Exception {
    int size = Screen.dp(48);
    try (RecordingCanvas canvas = new RecordingCanvas(size, size)) {
      invoke(row, "drawTopicIcon", new Class<?>[] {android.graphics.Canvas.class, float.class, float.class}, canvas, size / 2f, size / 2f);
      int[] pixels = new int[size * size]; canvas.bitmap.getPixels(pixels, 0, size, 0, 0, size, size);
      return Arrays.hashCode(pixels);
    }
  }

  private static void tabsIncremental (SyntheticEnvironment env) throws Exception {
    env.configure(1f, false, ThemeId.BLUE);
    try (TabsFixture fixture = new TabsFixture()) {
      List<TdApi.ForumTopic> topics = tabTopics(200);
      fixture.view.setTopics(topics, null, 100, true, false, null, true);
      fixture.settle();
      fixture.layout.scrollToPositionWithOffset(25, -Screen.dp(7)); fixture.settle();
      int[] before = fixture.view.saveState().getIntArray("current");
      require(before != null && before[0] != 100, "Fixture browses away from the selection");
      final int[] updates = {0, 0};
      fixture.list.getAdapter().registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
        @Override public void onChanged () { updates[0]++; }
        @Override public void onItemRangeChanged (int start, int count, Object payload) { updates[1] += count; }
      });
      TdApi.ForumTopic selected = topics.get(98);
      selected.info.name = "Renamed synthetic topic"; selected.notificationSettings.muteFor = 60; selected.unreadCount = 12;
      fixture.view.setTopics(topics, null, 100, true, false, null, true);
      fixture.settle();
      int[] after = fixture.view.saveState().getIntArray("current");
      equal(before[0], after[0], "Rename/mute preserves viewport identity");
      equal(before[2], after[2], "Rename/mute preserves viewport offset");
      equal(0, updates[0], "No full notifyDataSetChanged");
      require(updates[1] > 0 && updates[1] < 10, "Only changed presentation rows are rebound");
      RecyclerView.ViewHolder holder = fixture.holder(fixture.index(100));
      require(holder.itemView.isSelected() && holder.itemView.getContentDescription().toString().contains(selected.info.name), "Selection survives a mutable input rename/mute");
      require((Boolean) get(get(holder.itemView, "item"), "muted"), "Explicit mute updates presentation");
      equal(202, fixture.list.getAdapter().getItemCount(), "Large list plus All/New remains intact");
    }
  }

  private static void tabsRestore (SyntheticEnvironment env) throws Exception {
    env.configure(1f, true, ThemeId.NIGHT_BLUE);
    List<TdApi.ForumTopic> topics = tabTopics(200);
    for (int placement : new int[] {ForumTabsState.TOP, ForumTabsState.START, ForumTabsState.BOTTOM}) {
      Bundle saved;
      try (TabsFixture old = new TabsFixture()) {
        old.view.setPlacement(placement, ForumTabsState.TOP);
        old.view.setTopics(topics, null, 100, false, false, null, true); old.settle();
        saved = old.view.saveState();
        require(saved.getIntArray("current") != null, "Laid-out panel has a transferable identity anchor");
      }
      try (TabsFixture replacement = new TabsFixture()) {
        replacement.view.restoreState(saved);
        replacement.view.setTopics(topics, null, 100, false, false, null, true); replacement.settle();
        Bundle restored = replacement.view.saveState();
        equal(placement, restored.getInt("placement"), "Controller replacement preserves placement");
        equal(saved.getIntArray("current")[0], restored.getIntArray("current")[0], "Controller replacement preserves visible anchor");
        View selected = replacement.layout.findViewByPosition(replacement.index(100));
        require(selected != null && selected.isSelected(), "Selected tab is visible after restoration");
        require(placement == ForumTabsState.START ? selected.getTop() >= 0 && selected.getBottom() <= replacement.list.getHeight()
          : selected.getLeft() >= 0 && selected.getRight() <= replacement.list.getWidth(), "Restored selection is not clipped");
        // A new selection after restoring the previous controller must be revealed only once.
        replacement.view.setTopics(topics, null, 180, false, false, null, true); replacement.settle();
        require(replacement.layout.findViewByPosition(replacement.index(180)) != null, "New destination selection supersedes the old controller anchor");
      }
    }
  }

  private static void tabsPlacementReveal (SyntheticEnvironment env, float scale, boolean rtl) throws Exception {
    env.configure(scale, rtl, ThemeId.BLUE);
    List<TdApi.ForumTopic> topics = tabTopics(200);
    topics.get(98).info.name = "A long selected topic title that fills the horizontal viewport";
    try (TabsFixture fixture = new TabsFixture()) {
      fixture.view.setTopics(topics, null, 100, false, false, null, true);
      fixture.settle();
      for (int horizontal : new int[] {ForumTabsState.TOP, ForumTabsState.BOTTOM, ForumTabsState.TOP}) {
        fixture.view.setPlacement(ForumTabsState.START, horizontal);
        fixture.settle();
        fixture.layout.scrollToPositionWithOffset(fixture.index(100), -Screen.dp(15));
        fixture.settle();
        View before = fixture.layout.findViewByPosition(fixture.index(100));
        require(before != null && before.getTop() < 0, "The saved side anchor deliberately clips the selected row");
        fixture.view.setPlacement(horizontal, ForumTabsState.BOTTOM);
        fixture.settle();
        View selected = fixture.layout.findViewByPosition(fixture.index(100));
        require(selected != null && selected.isSelected(), "Placement keeps the selected topic visible");
        require(selected.getLeft() >= 0 && selected.getRight() <= fixture.list.getWidth(),
          "The entire selected tab fits before the placement control in both directions");

        // Once revealed, ordinary snapshots must not snap back from a manually browsed page.
        fixture.layout.scrollToPositionWithOffset(25, -Screen.dp(7)); fixture.settle();
        int[] anchor = fixture.view.saveState().getIntArray("current");
        fixture.view.setTopics(topics, null, 100, false, false, null, true); fixture.settle();
        int[] updated = fixture.view.saveState().getIntArray("current");
        require(anchor != null && updated != null, "Manual browsing retains an anchor");
        equal(anchor[0], updated[0], "One-shot reveal does not override subsequent manual browsing");
        equal(anchor[2], updated[2], "Manual browsing offset survives a topic snapshot");
      }
      fixture.view.setPlacement(ForumTabsState.START, ForumTabsState.TOP);
      invoke(fixture.list, "dispatchOnScrollStateChanged", new Class<?>[] {int.class}, RecyclerView.SCROLL_STATE_DRAGGING);
      require(!(Boolean) get(fixture.view, "revealSelection") && get(fixture.view, "pendingAnchor") == null,
        "A drag before deferred layout cancels selection reveal and anchor restoration");
    }
  }

  private static void tabsStates (SyntheticEnvironment env) throws Exception {
    env.configure(1f, false, ThemeId.BLUE);
    try (TabsFixture fixture = new TabsFixture()) {
      List<TdApi.ForumTopic> topics = tabTopics(200);
      fixture.view.setTopics(topics, null, 0, true, false, new TdApi.Error(500, "Synthetic failure"), false);
      fixture.layout();
      View state = (View) get(fixture.view, "stateButton");
      require(state.getVisibility() == View.VISIBLE && state.isClickable(), "Error has a separate retry affordance");
      state.performClick(); state.performClick(); equal(1, fixture.retries, "Repeated retry taps coalesce until the next snapshot");
      fixture.view.setTopics(topics, null, 0, true, true, null, false); fixture.layout();
      require(!state.isClickable(), "Loading cannot activate retry");
      fixture.holder(fixture.list.getAdapter().getItemCount() - 1).itemView.performClick(); equal(1, fixture.creates, "New Topic remains last and actionable");
      fixture.holder(1).itemView.performClick(); equal(2, fixture.selected, "Topic click preserves the int32 topic ID");
      require(!ForumTopicsTabsLayout.nearEnd(-1, 200) && !ForumTopicsTabsLayout.nearEnd(195, 200) && ForumTopicsTabsLayout.nearEnd(197, 200), "Production pagination threshold respects the server prefix");
      equal(0, fixture.loads, "Detached render checks never issue list callbacks or network work");
      fixture.view.setTopics(topics, null, 0, false, false, null, true); fixture.layout();
      equal(View.GONE, state.getVisibility(), "Loading/error controls do not become topic rows");
      equal(201, fixture.list.getAdapter().getItemCount(), "Create-right removal removes only New Topic");
    }
  }

  private static void tabsTheme (SyntheticEnvironment env) throws Exception {
    env.configure(1f, false, ThemeId.BLUE);
    try (TabsFixture fixture = new TabsFixture()) {
      fixture.view.setTopics(tabTopics(2), null, 2, false, false, null, true); fixture.layout();
      RecyclerView.ViewHolder holder = fixture.holder(1);
      TextView label = (TextView) ((ViewGroup) holder.itemView).getChildAt(0);
      int color = label.getCurrentTextColor(), width = holder.itemView.getWidth();
      env.configure(1f, false, ThemeId.NIGHT_BLUE); fixture.view.onThemeInvalidate(false);
      require(color != label.getCurrentTextColor(), "Existing holder follows light/dark accent changes");
      equal(width, holder.itemView.getWidth(), "Theme change does not move the viewport");
      fixture.view.destroy(); fixture.view.destroy();
      require(fixture.list.getAdapter() == null && get(holder.itemView, "item") == null && get(holder.itemView, "customEmoji") == null, "Destroy releases adapter and recycled presentation/emoji state");
    }
  }
}
