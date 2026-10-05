package org.thunderdog.challegram.stage8;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.component.chat.ForumTopicView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.ForumTabsState;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.AppContext;
import org.thunderdog.challegram.widget.ForumTopicsTabsLayout;
import org.thunderdog.challegram.widget.ForumTopicsTabsView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.thunderdog.challegram.stage8.SyntheticEnvironment.allocate;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.equal;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.get;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.measure;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.require;
import static org.thunderdog.challegram.stage8.SyntheticEnvironment.set;

/**
 * Three finite, off-window production-component crops, NOT full app screenshots or goldens.
 * Call on the main thread from an isolated Stage8 case; the caller owns the environment.
 * Rows use the existing presentation-field seam, never account-dependent setTopic(). Tabs
 * use their real setTopics() with a constructor-free, null-TDLib owner. All fixture content
 * is invented here; topic icons are production color/initial fallbacks, never downloaded emoji.
 *
 * Returns files under targetContext.filesDir/forum-upstream-demo (run-as readable). Re-running
 * overwrites only these three fixed PNG names. No Activity, account, network or user-data input.
 * The API 21+ guard below bounds this harness, not the production minimum SDK. Synthetic
 * runs use the dedicated API 24+ target; they do not establish API 16 device compatibility.
 */
public final class ForumUpstreamDemo {
  private ForumUpstreamDemo () { }

  private static final String[] NAMES = {
    "Lobby Lab", "Paper Kites", "Tiny Gardens", "Night Maps", "Puzzle Bench", "Old Plans"
  };
  private static final String[] PREVIEWS = {
    "New ideas for the next workshop", "Folded wings are ready for a test",
    "Two seedlings reached the window", "Draft: Add a trail by the pond",
    "Vote for a shape to try next", "The sample checklist is complete"
  };
  private static final String[] TIMES = {"09:10", "09:25", "10:05", "10:40", "11:15", "11:30"};
  private static final int[] ICON_COLORS = {0x5c83b5, 0x568f78, 0xe7a75b, 0x846dc1, 0xc56b8c, 0x7b8694};
  private static final int MAX_BITMAP_EDGE = 1600;

  public static File[] render (Instrumentation instrumentation, SyntheticEnvironment environment) throws Exception {
    require(Looper.myLooper() == Looper.getMainLooper(), "Demo must run in a main-thread Stage8 case");
    require(Build.VERSION.SDK_INT >= 21, "Production topic-row raster demo requires API 21+");
    Context target = instrumentation.getTargetContext();
    require(target.getPackageName().endsWith(".stage8synthetic"), "Demo requires the isolated synthetic target");
    environment.assertNoAccountInitialization();
    File files = target.getFilesDir().getCanonicalFile();
    File directory = new File(files, "forum-upstream-demo").getCanonicalFile();
    require(files.equals(directory.getParentFile()), "Demo output must stay in target filesDir");
    require(directory.isDirectory() || directory.mkdir(), "Cannot create demo output directory");

    Object previousContext = get(AppContext.class, "context");
    Object previousRtl = get(Lang.class, "languageRtl");
    Object previousListeners = get(Lang.class, "languageListeners");
    Object previousDensity = get(Screen.class, "_lastDensity");
    Object manager = get(ThemeManager.class, "instance");
    Object previousTheme = get(manager, "_currentTheme");
    try {
      List<TdApi.ForumTopic> topics = topics();
      File rows = renderRows(environment.configure(1f, false, ThemeId.BLUE), topics, directory);
      environment.configure(1f, false, ThemeId.NIGHT_BLUE);
      File horizontal = renderTabs(topics, directory, false);
      environment.configure(1.8f, true, ThemeId.NIGHT_BLUE);
      File side = renderTabs(topics, directory, true);
      return new File[] {rows, horizontal, side};
    } finally {
      set(manager, "_currentTheme", previousTheme);
      set(Lang.class, "languageRtl", previousRtl);
      set(Lang.class, "languageListeners", previousListeners);
      set(Screen.class, "_lastDensity", previousDensity);
      set(AppContext.class, "context", previousContext);
      environment.assertNoAccountInitialization();
    }
  }

  private static List<TdApi.ForumTopic> topics () {
    List<TdApi.ForumTopic> result = new ArrayList<>();
    for (int i = 0; i < NAMES.length; i++) {
      TdApi.ForumTopic topic = new TdApi.ForumTopic();
      topic.info = new TdApi.ForumTopicInfo();
      topic.info.chatId = -900004201L; // Arbitrary in-memory identity, never looked up.
      topic.info.forumTopicId = i == 0 ? 1 : 200 + i;
      topic.info.name = NAMES[i];
      topic.info.isGeneral = i == 0;
      topic.info.isClosed = i == 5;
      topic.info.icon = new TdApi.ForumTopicIcon(ICON_COLORS[i], 0);
      topic.isPinned = i == 0;
      topic.unreadCount = i == 0 ? 3 : i == 1 ? 12 : i == 2 ? 7 : 0;
      topic.unreadMentionCount = i == 1 ? 2 : 0;
      topic.unreadReactionCount = i == 3 ? 3 : 0;
      topic.unreadPollVoteCount = i == 4 ? 4 : 0;
      topic.notificationSettings = new TdApi.ChatNotificationSettings();
      topic.notificationSettings.muteFor = i == 2 ? 3600 : 0;
      result.add(topic);
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private static File renderRows (Context context, List<TdApi.ForumTopic> topics, File directory) throws Exception {
    LinearLayout list = new LinearLayout(context);
    list.setOrientation(LinearLayout.VERTICAL);
    list.setBackgroundColor(Theme.fillingColor());
    List<ForumTopicView> rows = new ArrayList<>();
    try {
      for (int i = 0; i < topics.size(); i++) {
        TdApi.ForumTopic topic = topics.get(i);
        ForumTopicView row = new ForumTopicView(context, null);
        rows.add(row);
        list.addView(row, new LinearLayout.LayoutParams(-1, -2));
        set(row, "topic", topic);
        set(row, "preview", PREVIEWS[i]);
        set(row, "time", TIMES[i]);
        set(row, "hasDraft", i == 3);
        set(row, "muted", topic.notificationSettings.muteFor > 0);
        List<Drawable> states = (List<Drawable>) get(row, "stateIcons");
        if (topic.isPinned) states.add((Drawable) get(row, "pin"));
        if (topic.info.isClosed) states.add((Drawable) get(row, "closed"));
        if (topic.notificationSettings.muteFor > 0) states.add((Drawable) get(row, "mute"));
        if (i == 1) set(row, "delivery", get(row, "read"));
        List<String> counters = (List<String>) get(row, "counters");
        if (topic.unreadCount > 0) counters.add(Integer.toString(topic.unreadCount));
        if (topic.unreadMentionCount > 0) counters.add("@" + topic.unreadMentionCount);
        if (topic.unreadReactionCount > 0) counters.add("\u2665" + topic.unreadReactionCount);
        if (topic.unreadPollVoteCount > 0) counters.add("\u2713" + topic.unreadPollVoteCount);
        row.setContentDescription(topic.info.name + ". " + PREVIEWS[i]);
      }
      int width = Screen.dp(412);
      list.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
      list.layout(0, 0, list.getMeasuredWidth(), list.getMeasuredHeight());
      int bottom = 0;
      for (ForumTopicView row : rows) {
        equal(width, row.getWidth(), "Row must fill the list width");
        equal(bottom, row.getTop(), "Rows must not overlap or leave gaps");
        require(row.getHeight() >= Screen.dp(64) && row.getHeight() <= Screen.dp(160), "Bounded row height");
        bottom = row.getBottom();
      }
      equal(bottom, list.getHeight(), "List must not clip its last row");
      return writePng(list, directory, "01-topic-rows-light.png");
    } finally {
      for (ForumTopicView row : rows) {
        row.clear();
        ComplexReceiver receiver = (ComplexReceiver) get(row, "receiver");
        receiver.detach();
        receiver.performDestroy();
        require(row.getTopic() == null, "Row teardown must clear the fixture");
      }
      list.removeAllViews();
    }
  }

  private static final class DemoOwner extends ViewController<Void> {
    private DemoOwner (Context context) { super(context, null); }
    @Override protected View onCreateView (Context context) { throw new AssertionError("No demo controller lifecycle"); }
    @Override public int getId () { return 0; }
  }

  private static File renderTabs (List<TdApi.ForumTopic> topics, File directory, boolean side) throws Exception {
    DemoOwner owner = allocate(DemoOwner.class);
    require(owner.context() == null && owner.tdlib() == null, "Demo owner must not acquire an Activity or account");
    ForumTopicsTabsView view = new ForumTopicsTabsView(owner, new ForumTopicsTabsView.Listener() {
      @Override public void onSelectTopic (int id) { throw new AssertionError("Unexpected demo interaction"); }
      @Override public void onNewTopic () { throw new AssertionError("Unexpected demo interaction"); }
      @Override public void onCyclePlacement () { throw new AssertionError("Unexpected demo interaction"); }
      @Override public void onLoadMore () { throw new AssertionError("Demo must not request data"); }
      @Override public void onRetry () { throw new AssertionError("Demo must not request data"); }
    });
    try {
      view.setPlacement(side ? ForumTabsState.START : ForumTabsState.TOP, ForumTabsState.BOTTOM);
      view.setTopics(topics, null, 201, true, false, null, true);
      int width = side ? view.recommendedSideWidth() : Screen.dp(800);
      int height = side ? (topics.size() + 2) * ForumTopicsTabsLayout.sideRowHeight(
        view.getResources().getDisplayMetrics().density, view.getResources().getConfiguration().fontScale) + Screen.dp(48)
        : view.recommendedHorizontalHeight();
      measure(view, width, height);
      RecyclerView list = (RecyclerView) get(view, "list");
      equal(width, view.getWidth(), "Selector width");
      equal(height, view.getHeight(), "Selector height");
      equal(topics.size() + 2, list.getAdapter().getItemCount(), "All, six topics, and New Topic");
      require(list.getChildCount() >= 3 && list.getWidth() > 0 && list.getHeight() > 0, "Selector must lay out real tabs");
      RecyclerView.ViewHolder selected = list.findViewHolderForItemId(201);
      require(selected != null && selected.itemView.isSelected(), "Selected fixture must be visible");
      for (int i = 0; i < list.getChildCount(); i++) {
        View tab = list.getChildAt(i);
        require(tab.getWidth() > 0 && tab.getHeight() > 0, "Nonempty tab geometry");
        require(side ? tab.getLeft() == 0 && tab.getRight() == list.getWidth()
          : tab.getTop() == 0 && tab.getBottom() == list.getHeight(), "Tabs must fit the cross-axis");
      }
      if (side) equal(topics.size() + 2, list.getChildCount(), "Side crop must fit every fixture");
      return writePng(view, directory, side ? "03-selector-side-dark-rtl-font180.png" : "02-selector-horizontal-dark.png");
    } finally {
      view.destroy();
      require(((RecyclerView) get(view, "list")).getAdapter() == null && owner.getThemeListeners().isEmpty(),
        "Selector teardown must release its adapter and theme listener");
      Object listeners = get(Lang.class, "languageListeners");
      if (listeners != null) for (Object listener : (Iterable<?>) listeners) {
        require(listener != view, "Selector teardown must release its language listener");
      }
    }
  }

  private static File writePng (View view, File directory, String name) throws Exception {
    int width = view.getWidth(), height = view.getHeight();
    require(width > 0 && height > 0 && width <= 8192 && height <= 8192, "Bounded, nonempty source layout");
    float scale = Math.min(1f, (float) MAX_BITMAP_EDGE / Math.max(width, height));
    int bitmapWidth = Math.max(1, Math.round(width * scale)), bitmapHeight = Math.max(1, Math.round(height * scale));
    Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
    try {
      Canvas canvas = new Canvas(bitmap);
      int background = Theme.fillingColor();
      canvas.drawColor(background);
      canvas.scale((float) bitmapWidth / width, (float) bitmapHeight / height);
      view.draw(canvas);
      int ink = 0;
      for (int y = 0; y < bitmapHeight; y += Math.max(1, bitmapHeight / 128)) {
        for (int x = 0; x < bitmapWidth; x += Math.max(1, bitmapWidth / 128)) {
          if (bitmap.getPixel(x, y) != background) ink++;
        }
      }
      require(ink > 32, "Demo crop must contain rendered content");
      File output = new File(directory, name).getCanonicalFile();
      require(directory.equals(output.getParentFile()), "PNG must stay inside the demo directory");
      try (FileOutputStream stream = new FileOutputStream(output)) {
        require(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream), "PNG encoding failed");
      }
      BitmapFactory.Options bounds = new BitmapFactory.Options();
      bounds.inJustDecodeBounds = true;
      BitmapFactory.decodeFile(output.getPath(), bounds);
      equal(bitmapWidth, bounds.outWidth, "Saved PNG width");
      equal(bitmapHeight, bounds.outHeight, "Saved PNG height");
      require(output.length() > 0 && "image/png".equals(bounds.outMimeType), "Saved output must be a PNG");
      return output;
    } finally {
      bitmap.recycle();
    }
  }
}
