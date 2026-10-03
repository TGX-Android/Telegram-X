package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.ForumRailLayout;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.telegram.CleanupStartupDelegate;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.ui.ChatsController;
import org.thunderdog.challegram.ui.ForumTopicsController;
import org.thunderdog.challegram.ui.MainController;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.widget.ChatRailView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import me.vkryl.android.widget.FrameLayoutFix;

/**
 * Places the rail behind the full-width navigation header. Only attached topic-list bodies
 * reserve its width; messages, profiles and editors retain normal full-width navigation.
 * Keeping the rail behind the stack also lets Back reveal it without resizing either screen.
 */
public final class ForumNavigationContainer extends FrameLayoutFix implements NavigationStack.ChangeListener {
  private final NavigationController navigation;
  private final View content;
  private ChatRailView rail;
  private Tdlib tdlib;
  private TdApi.ChatList source;
  private boolean session, destroyed, switching, closing;
  private final ArrayList<View> topicViews = new ArrayList<>();
  private int bottomInset;
  private CleanupStartupDelegate cleanupListener;
  private final LinkedHashMap<Long, Bundle> savedTopics = new LinkedHashMap<>(16, .75f, true);
  private View transitionMain, transitionTopics;
  private ChatsController transitionChats;
  private float transitionProgress, transitionMainAlpha;
  private Rect transitionMainClip;
  private final Rect transitionClip = new Rect();
  private boolean allowAvatarMorph;
  private ForumRailTransition avatarMorph;
  private Runnable releaseTransitionSource;

  public ForumNavigationContainer (Context context, NavigationController navigation, View content) {
    super(context);
    this.navigation = navigation;
    this.content = content;
    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    addView(content);
    navigation.getStack().addChangeListener(this);
  }

  public void enable (Tdlib account, @Nullable TdApi.ChatList chatList) {
    TdApi.ChatList requested = chatList != null ? chatList : resolveSource(account);
    if (tdlib != account || source == null || !TD.makeChatListKey(source).equals(TD.makeChatListKey(requested))) {
      closeSession();
      tdlib = account;
      source = requested;
    }
    session = true;
    closing = false;
    if (rail == null) {
      // The existing header owns Back (including selection/search/IME); do not duplicate it.
      rail = new ChatRailView(getContext(), tdlib, source, this::openChat);
      for (ViewController<?> item : navigation.getStack().getAll()) {
        if (item.tdlib() != tdlib) continue;
        ChatsController chats = item instanceof ChatsController ? (ChatsController) item :
          item instanceof MainController ? ((MainController) item).getCurrentChatsController() : null;
        if (chats != null && TD.makeChatListKey(chats.chatList()).equals(TD.makeChatListKey(source))) {
          ChatView anchor = chats.getForumRailAnchor(this, headerBottom());
          rail.restoreScrollAnchor(anchor != null ? anchor.getChatId() : 0, chats.getForumRailPosition(),
            chats.getForumRailAnchorOffset(this, headerBottom(), anchor));
          break;
        }
      }
      addView(rail, 0, FrameLayoutFix.newParams(Screen.dp(64), ViewGroup.LayoutParams.MATCH_PARENT));
      final Tdlib boundAccount = tdlib;
      cleanupListener = new CleanupStartupDelegate() {
        private void clear () { post(() -> {
          if (tdlib != boundAccount) return;
          closeSession();
          requestLayout();
        }); }
        @Override public void onPerformUserCleanup () { clear(); }
        @Override public void onPerformRestart () { clear(); }
        @Override public void onPerformStartup (boolean afterRestart) { }
      };
      tdlib.listeners().addCleanupListener(cleanupListener);
    }
  }

  private TdApi.ChatList resolveSource (Tdlib account) {
    if (session && tdlib == account && source != null) return source;
    for (ViewController<?> c : navigation.getStack().getAll()) {
      if (c.tdlib() != account) continue;
      if (c instanceof ChatsController) return ((ChatsController) c).chatList();
      if (c instanceof MainController) {
        ChatsController chats = ((MainController) c).getCurrentChatsController();
        if (chats != null) return chats.chatList();
      }
    }
    return new TdApi.ChatListMain();
  }

  public boolean isActiveFor (Tdlib account) { return session && tdlib == account; }

  public void restoreTopics (ForumTopicsController controller) {
    if (!isActiveFor(controller.tdlib())) return;
    Bundle state = savedTopics.get(controller.getChatId());
    if (state != null) controller.restoreInstanceState(state, "rail_");
  }

  private void rememberTopics () {
    for (ViewController<?> c : navigation.getStack().getAll()) {
      if (c instanceof ForumTopicsController && c.tdlib() == tdlib) {
        ForumTopicsController topics = (ForumTopicsController) c;
        topics.clearTopicSelection();
        Bundle state = new Bundle();
        if (topics.saveInstanceState(state, "rail_")) savedTopics.put(c.getChatId(), state);
      }
    }
    while (savedTopics.size() > 16) savedTopics.remove(savedTopics.keySet().iterator().next());
  }

  private void openChat (long chatId) {
    ViewController<?> current = navigation.getCurrentStackItem();
    if (!session || switching || navigation.isAnimating() || current == null || current.tdlib() != tdlib || current.getChatId() == chatId) return;
    rememberTopics();
    current.hideSoftwareKeyboard();
    switching = true;
    tdlib.ui().openChat(current, chatId, new TdlibUi.ChatOpenParameters().chatList(source).onDone(() -> switching = false));
  }

  @Override public void onStackChanged (NavigationStack stack) {
    if (destroyed) return;
    ViewController<?> current = stack.getCurrent();
    // A cleared stack during initController is transient. Evaluate after that transaction.
    if (current == null) { post(this::refresh); return; }
    refresh();
  }

  public void refresh () {
    if (destroyed) return;
    ViewController<?> current = navigation.getCurrentStackItem();
    if (current == null) {
      closeSession();
      requestLayout();
      return;
    }
    if (current instanceof ForumTopicsController) {
      ForumTopicsController topics = (ForumTopicsController) current;
      enable(topics.tdlib(), topics.getArgumentsStrict().chatList);
    } else if (current instanceof MessagesController && current.getChatId() != 0 && current.tdlib().isForum(current.getChatId()) && !current.tdlib().hasForumTabs(current.getChatId())) {
      enable(current.tdlib(), ((MessagesController) current).chatList());
    }
    if (session && current.tdlib() != tdlib) {
      closeSession();
      return;
    }
    if (session && (current instanceof MainController || current instanceof ChatsController)) {
      closing = true;
      if (topicViews.isEmpty()) closeSession();
      requestLayout();
      return;
    }
    if (rail != null) {
      rail.setSelectedChat(current.getChatId());
      rail.updateTheme();
    }
    requestLayout();
  }

  void addTopicView (View view) {
    if (!topicViews.contains(view)) topicViews.add(view);
    requestLayout();
  }

  void removeTopicView (View view) {
    onControllerViewRemoved(view);
    topicViews.remove(view);
    setTopicInset(view, 0);
    if (closing && topicViews.isEmpty()) closeSession();
    requestLayout();
  }

  boolean beginTransition (ViewController<?> left, ViewController<?> right, boolean morph, float progress) {
    endTransition();
    if (!session || rail == null || !(right instanceof ForumTopicsController) || left.tdlib() != tdlib || right.tdlib() != tdlib) return false;
    ChatsController chats = left instanceof ChatsController ? (ChatsController) left :
      left instanceof MainController ? ((MainController) left).getCurrentChatsController() : null;
    if (chats == null || !TD.makeChatListKey(chats.chatList()).equals(TD.makeChatListKey(source))) return false;
    transitionMain = left.getValue();
    transitionTopics = right.getValue();
    transitionChats = chats;
    transitionMainAlpha = transitionMain.getAlpha();
    transitionMainClip = transitionMain.getClipBounds();
    allowAvatarMorph = morph;
    setTransitionProgress(progress);
    return true;
  }

  void setTransitionProgress (float progress) {
    if (transitionMain == null) return;
    transitionProgress = ForumRailLayout.progress(progress);
    transitionMain.setTranslationX(0f);
    transitionMain.setAlpha(transitionMainAlpha * (1f - transitionProgress));
    updateSourceClip();
    if (rail != null) rail.setAlpha(avatarMorph != null ? 1f : transitionProgress);
    invalidate();
  }

  int transitionRailWidth () { return railWidth(getWidth()); }

  boolean hasTransition () { return transitionMain != null; }

  private void updateSourceClip () {
    if (avatarMorph == null || transitionMain == null) return;
    int edge = Math.round(ForumRailLayout.interpolate(getWidth(), transitionRailWidth(), transitionProgress));
    transitionClip.set(Lang.rtl() ? getWidth() - edge : 0, 0,
      Lang.rtl() ? getWidth() : edge, transitionMain.getHeight());
    if (transitionMainClip != null && !transitionClip.intersect(transitionMainClip)) transitionClip.setEmpty();
    // Some source roots have elevation; they must never paint over the incoming topic pane.
    transitionMain.setClipBounds(transitionClip);
  }

  void startTransitionWhenReady (Runnable start) {
    if (!allowAvatarMorph || transitionMain == null) { start.run(); return; }
    final View owner = transitionMain;
    final long deadline = SystemClock.uptimeMillis() + 80;
    postOnAnimation(new Runnable() {
      @Override public void run () {
        if (destroyed) return;
        if (transitionMain == owner) captureTransition();
        if (transitionMain != owner || avatarMorph != null || SystemClock.uptimeMillis() >= deadline) {
          // Bound the wait to local presentation readiness, never a network response.
          allowAvatarMorph = false;
          start.run();
        } else {
          postOnAnimation(this);
        }
      }
    });
  }

  void onControllerViewRemoved (View view) {
    if (view == transitionMain || view == transitionTopics) endTransition();
  }

  void endTransition () {
    if (avatarMorph != null) { avatarMorph.close(); avatarMorph = null; }
    if (transitionMain != null) {
      transitionMain.setAlpha(transitionMainAlpha);
      transitionMain.setTranslationX(0f);
      transitionMain.setClipBounds(transitionMainClip);
    }
    transitionMainClip = null;
    transitionMain = transitionTopics = null;
    transitionChats = null;
    if (releaseTransitionSource != null) { releaseTransitionSource.run(); releaseTransitionSource = null; }
    allowAvatarMorph = false;
    if (rail != null) { rail.setAlpha(1f); rail.setTransitionPaused(false); }
    invalidate();
  }

  private void captureTransition () {
    if (!allowAvatarMorph || avatarMorph != null || transitionChats == null || rail == null) return;
    // Capture before the animator starts. Late/missing views use the same-progress fade.
    List<ForumRailTransition.Avatar> target = rail.captureAvatars(this);
    if (target.isEmpty()) return;
    List<ForumRailTransition.Avatar> source = transitionChats.captureForumRailAvatars(this);
    if (source.isEmpty()) return;
    allowAvatarMorph = false;
    releaseTransitionSource = transitionChats.holdForumRailSourceLayout();
    rail.setTransitionPaused(true);
    avatarMorph = new ForumRailTransition(source, target);
    updateSourceClip();
    rail.setAlpha(1f);
  }

  @Override protected boolean drawChild (Canvas canvas, View child, long drawingTime) {
    if (child == rail && avatarMorph != null) {
      canvas.drawRect(child.getLeft(), headerBottom(), child.getRight(), getHeight() - bottomInset,
        Paints.fillingPaint(Theme.fillingColor()));
      return true;
    }
    return super.drawChild(canvas, child, drawingTime);
  }

  @Override protected void dispatchDraw (Canvas canvas) {
    captureTransition();
    if (transitionMain != null) {
      canvas.drawRect(0, headerBottom(), getWidth(), getHeight() - bottomInset, Paints.fillingPaint(Theme.fillingColor()));
    }
    super.dispatchDraw(canvas);
    if (avatarMorph != null && transitionTopics != null) {
      int save = canvas.save();
      float edge = Lang.rtl() ? transitionTopics.getRight() + transitionTopics.getTranslationX() :
        transitionTopics.getLeft() + transitionTopics.getTranslationX();
      canvas.clipRect(Lang.rtl() ? edge : 0f, headerBottom(), Lang.rtl() ? getWidth() : edge, getHeight() - bottomInset);
      avatarMorph.draw(canvas, transitionProgress);
      canvas.restoreToCount(save);
    }
  }

  private int occupiedWidth () {
    return session && !topicViews.isEmpty() ? railWidth(getWidth()) : 0;
  }

  private int headerBottom () {
    HeaderView header = navigation.getHeaderView();
    return header != null ? Math.round(header.getCurrentHeight()) + HeaderView.getTopOffset() : HeaderView.getSize(true);
  }

  public boolean isRailTouch (float x, float y) {
    return ForumRailLayout.hitRail(getWidth(), occupiedWidth(), x, y, headerBottom(), getHeight() - bottomInset, Lang.rtl());
  }

  public float contentX (float x, float y) {
    return y >= headerBottom() ? ForumRailLayout.contentX(occupiedWidth(), x, Lang.rtl()) : x;
  }

  public int targetContentWidth () {
    // Message layout may be prepared while a topic list is still attached behind it.
    return getWidth() > 0 ? getWidth() : Screen.currentWidth();
  }

  private int railWidth (int width) {
    float dpWidth = width / getResources().getDisplayMetrics().density;
    return Screen.dp(ForumRailLayout.widthDp(dpWidth));
  }

  public void setBottomInset (int value) { bottomInset = value; requestLayout(); }

  private void setTopicInset (View view, int inset) {
    ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
    int left = Lang.rtl() ? 0 : inset, right = Lang.rtl() ? inset : 0;
    if (params.leftMargin != left || params.rightMargin != right) {
      params.leftMargin = left;
      params.rightMargin = right;
      view.setLayoutParams(params);
      content.forceLayout();
    }
  }

  @Override protected void onMeasure (int widthSpec, int heightSpec) {
    int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
    int railWidth = railWidth(width);
    for (View view : topicViews) setTopicInset(view, session || navigation.isAnimating() ? railWidth : 0);
    if (rail != null) {
      rail.setInsets(headerBottom(), bottomInset);
      rail.measure(MeasureSpec.makeMeasureSpec(railWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
    content.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    setMeasuredDimension(width, height);
  }

  @Override protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
    int width = right - left, height = bottom - top;
    int railWidth = railWidth(width);
    boolean rtl = Lang.rtl();
    if (rail != null) {
      int x = rtl ? width - railWidth : 0;
      rail.layout(x, 0, x + railWidth, height);
      rail.setVisibility(session && !topicViews.isEmpty() ? VISIBLE : INVISIBLE);
    }
    content.layout(0, 0, width, height);
  }

  public void saveState (Bundle out) {
    if (!session || tdlib == null || source == null) return;
    out.putInt("forum_rail_account", tdlib.id());
    out.putString("forum_rail_list", TD.makeChatListKey(source));
    if (rail != null) {
      out.putInt("forum_rail_position", rail.scrollPosition());
      out.putInt("forum_rail_offset", rail.scrollOffset());
    }
  }

  public void restoreState (Bundle in, Tdlib account) {
    if (in.containsKey("forum_rail_list") && in.getInt("forum_rail_account", -1) == account.id()) {
      enable(account, TD.chatListFromKey(in.getString("forum_rail_list")));
      if (rail != null) rail.restoreScrollPosition(in.getInt("forum_rail_position", 0), in.getInt("forum_rail_offset", 0));
    }
  }

  private void closeSession () {
    endTransition();
    if (tdlib != null && cleanupListener != null) tdlib.listeners().removeCleanupListener(cleanupListener);
    cleanupListener = null;
    if (rail != null) { rail.destroy(); removeView(rail); rail = null; }
    session = switching = closing = false;
    savedTopics.clear();
    tdlib = null; source = null;
    requestLayout();
  }

  public void destroy () {
    destroyed = true;
    navigation.getStack().removeChangeListener(this);
    closeSession();
    for (View view : topicViews) setTopicInset(view, 0);
    topicViews.clear();
  }
}
