package org.thunderdog.challegram.reactions;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.text.TextUtils;
import android.util.Property;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.charts.CubicBezierInterpolator;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.OptionsLayout;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.ui.SettingHolder;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.widget.ListInfoView;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.ShadowView;
import org.thunderdog.challegram.widget.ViewPager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class ReactionListViewController {
  private final Context context;
  private final PopupLayout popup;
  private final TGMessage message;
  private final MessagesController chatController;
  private final Tdlib tdlib;
  private final ShadowView topShadow;
  private Runnable dismissCallback;

  private LinearLayout topBar;
  private NestedScrollView scrollView;
  private LinearLayout scrollContent;
  private FrameLayout shadowWrap;
  private View statusBarBackground;
  private ShadowView shadow;
  private View topPadding;
  private ViewPager pager;
  private ReactionsTabBar tabBar;
  private BackHeaderButton backBtn;

  private ArrayList<TabViewController> viewControllers = new ArrayList<>();
  private TabViewController allReactionsTab, viewersTab;
  private HashMap<String, TabViewController> viewControllersByType = new HashMap<>();
  private TdApi.Users viewers;

  private boolean scrolledToBottom;
  private Animator statusBarBgAnimator;
  private boolean loadingMore = true;
  private boolean isFromOptions;

  private ThemeListenerList themeListeners = new ThemeListenerList();

  private static final Property<View, Integer> SCROLL_Y = new Property<>(Integer.class, "fadshjkfdsa") {
    @Override
    public Integer get (View object) {
      return object.getScrollY();
    }

    @Override
    public void set (View object, Integer value) {
      object.scrollTo(0, value);
    }
  };

  @SuppressLint("ClickableViewAccessibility")
  public ReactionListViewController (Context context, PopupLayout popup, TGMessage message, MessagesController chatController, TdApi.Users viewers, Runnable dismissCallback) {
    this.context = context;
    this.popup = popup;
    this.message = message;
    this.chatController = chatController;
    this.viewers = viewers;
    this.dismissCallback = dismissCallback;
    tdlib = chatController.tdlib();

    scrollView = new NestedScrollView2(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        pager.getLayoutParams().height = MeasureSpec.getSize(heightMeasureSpec) - Screen.dp(48) - HeaderView.getTopOffset();
        shadowWrap.getLayoutParams().height = HeaderView.getTopOffset();
        if (!scrolledToBottom) {
          statusBarBackground.setTranslationY(HeaderView.getTopOffset());
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
      }

      @Override
      public boolean dispatchTouchEvent (MotionEvent ev) {
        if (!isEnabled())
          return false;
        return super.dispatchTouchEvent(ev);
      }

      @Override
      protected void onScrollChanged (int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        boolean atBottom = t == getChildAt(0).getHeight() - getHeight();
        if (atBottom != scrolledToBottom) {
          scrolledToBottom = atBottom;
          scrolledToBottomChanged();
        }
      }

      @Override
      public void onNestedPreScroll (View target, int dx, int dy, int[] consumed, int type) {
        final RecyclerView rv = (RecyclerView) target;
        if ((dy < 0 && isRvScrolledToTop(rv)) || (dy > 0 && !isNsvScrolledToBottom(this))) {
          scrollBy(0, dy);
          consumed[1] = dy;
          return;
        }
        super.onNestedPreScroll(target, dx, dy, consumed, type);
      }

      /**
       * Returns true iff the NestedScrollView is scrolled to the bottom of its
       * content (i.e. if the card's inner RecyclerView is completely visible).
       */
      private boolean isNsvScrolledToBottom (NestedScrollView nsv) {
        return !nsv.canScrollVertically(1);
      }

      /**
       * Returns true iff the RecyclerView is scrolled to the top of its
       * content (i.e. if the RecyclerView's first item is completely visible).
       */
      private boolean isRvScrolledToTop (RecyclerView rv) {
        final LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
        return lm.findFirstVisibleItemPosition() == 0
          && lm.findViewByPosition(0).getTop() == 0;
      }
    };
    scrollContent = new LinearLayout(context);
    scrollContent.setOrientation(LinearLayout.VERTICAL);
    scrollView.addView(scrollContent);
//		scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);

    topPadding = new View(context);
    topPadding.setOnTouchListener((v, ev) -> {
      popup.hideWindow(true);
      scrollView.setEnabled(false);
      topPadding.setOnTouchListener(null);
      return true;
    });
    scrollContent.addView(topPadding, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0));

    shadowWrap = new FrameLayout(context);
    shadow = new ShadowView(context);
    shadow.setSimpleTopShadow(true);
    shadowWrap.addView(shadow, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, shadow.getLayoutParams().height, Gravity.BOTTOM));
    statusBarBackground = new View(context);
    ShapeDrawable statusBarBg;
    statusBarBackground.setBackground(new LayerDrawable(new Drawable[]{ // yes there may be better ways to alpha blend two colors
      statusBarBg = new ShapeDrawable(new RectShape()),
      new ColorDrawable(HeaderView.DEFAULT_STATUS_COLOR)
    }));
    statusBarBg.getPaint().setColor(Theme.getColor(R.id.theme_color_headerLightBackground));
    themeListeners.addThemePaintColorListener(statusBarBg.getPaint(), R.id.theme_color_headerLightBackground);
    themeListeners.addThemeInvalidateListener(statusBarBackground);

    statusBarBackground.setVisibility(View.INVISIBLE);
    shadowWrap.addView(statusBarBackground, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    scrollContent.addView(shadowWrap, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0));

    topBar = new LinearLayout(context);
    topBar.setOrientation(LinearLayout.HORIZONTAL);
    topBar.setBackgroundColor(Theme.backgroundColor());
    scrollContent.addView(topBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(48)));
    backBtn = new BackHeaderButton(context);
    backBtn.setColor(Theme.getColor(R.id.theme_color_text));
    backBtn.setButtonFactor(BackHeaderButton.TYPE_BACK);
    backBtn.setOnClickListener(v -> dismissCallback.run());
    topBar.addView(backBtn, Screen.dp(48), Screen.dp(48));
    themeListeners.addThemeColorListener(backBtn, R.id.theme_color_text);
    themeListeners.addThemeBackgroundColorListener(topBar, R.id.theme_color_background);

    int total = message.getTotalReactionCount();
    if (total > 0) {
      viewControllers.add(allReactionsTab = new TabViewController(null));
      allReactionsTab.total = total;
    }
    if (viewers != null) {
      viewControllers.add(viewersTab = new TabViewController(true, null));
      viewersTab.listItems.addAll(Arrays.stream(viewers.userIds).mapToObj(id -> new ListItem(ListItem.TYPE_USER_REACTION).setLongId(id)).collect(Collectors.toList()));
      viewersTab.adapter.notifyItemRangeInserted(0, viewersTab.listItems.size());
    }
    if (message.getMessageForReactions().interactionInfo != null) {
      for (TdApi.MessageReaction mr : message.getMessageForReactions().interactionInfo.reactions) {
        TabViewController tab = new TabViewController(mr.reaction);
        tab.total = mr.totalCount;
        viewControllers.add(tab);
        viewControllersByType.put(mr.reaction, tab);
        appendReactions(Arrays.stream(mr.recentSenderIds).map(sender -> new TdApi.AddedReaction(mr.reaction, sender)).collect(Collectors.toList()), true);
      }
    }

    pager = new ViewPager(context);
    FrameLayout pagerAndShadow = new FrameLayout(context);
    pagerAndShadow.addView(pager, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0));
    topShadow = new ShadowView(context);
    topShadow.setSimpleBottomTransparentShadow(true);
    topShadow.setAlpha(0f);
    pagerAndShadow.addView(topShadow, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, topShadow.getLayoutParams().height, Gravity.TOP));
    scrollContent.addView(pagerAndShadow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    pager.setAdapter(new PagerAdapter());
    tabBar = new ReactionsTabBar(context, pager, message, viewers != null ? viewers.totalCount : 0, tdlib, themeListeners);
    topBar.addView(tabBar, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

    if (total > 0) {
      tdlib.sendOnUiThread(new TdApi.GetMessageAddedReactions(message.getChatId(), message.getMessageForReactions().id, null, null, 100), res -> {
        if (res instanceof TdApi.AddedReactions) {
          TdApi.AddedReactions ar = (TdApi.AddedReactions) res;
          for (TabViewController vc : viewControllers) {
            if (!vc.isViewers) {
              vc.clearItems();
              vc.next = ar.nextOffset;
            }
          }
          appendReactions(Arrays.asList(ar.reactions), true);
          for (TabViewController vc : viewControllers) {
            vc.list.scrollToPosition(0);
          }
          loadingMore = false;
        }
      });
    }

    scrollView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
      @Override
      public void onViewAttachedToWindow (View v) {
        chatController.context().addGlobalThemeListeners(themeListeners);
      }

      @Override
      public void onViewDetachedFromWindow (View v) {
        chatController.context().removeGlobalThemeListeners(themeListeners);
      }
    });
  }

  public void showFromOptionsSheet (CounterView reactionsCounter, CounterView seenCounter, OptionsLayout optionsLayout, View countersButton, Runnable onTransitionDone) {
    isFromOptions = true;
    int sheetContentH = optionsLayout.getHeight() - optionsLayout.getChildAt(0).getHeight();
    View bg = new View(context);
    bg.setBackgroundColor(Theme.fillingColor());
    popup.addView(bg, 1, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, sheetContentH, Gravity.BOTTOM));
    popup.addView(scrollView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    topPadding.getLayoutParams().height = popup.getHeight() - sheetContentH - HeaderView.getTopOffset();
    countersButton.setEnabled(false);
    countersButton.setAlpha(0f);
    popup.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
      @Override
      public boolean onPreDraw () {
        popup.getViewTreeObserver().removeOnPreDrawListener(this);

        popup.setClipChildren(false);
        scrollView.setClipChildren(false);
        scrollContent.setClipChildren(false);
        topBar.setClipChildren(false);

        AnimatorSet set = new AnimatorSet();
        ArrayList<Animator> anims = new ArrayList<>();
        anims.add(ObjectAnimator.ofFloat(optionsLayout, View.TRANSLATION_X, -optionsLayout.getWidth()));
        anims.add(ObjectAnimator.ofFloat(scrollView, View.TRANSLATION_X, optionsLayout.getWidth(), 0f));

        CounterView counterForOffset = reactionsCounter.getVisibility() == View.VISIBLE ? reactionsCounter : seenCounter;
        int[] loc = {0, 0};
        counterForOffset.getLocationOnScreen(loc);
        int counterX = loc[0];
        tabBar.getLocationOnScreen(loc);
        anims.add(ObjectAnimator.ofFloat(backBtn, View.TRANSLATION_X, -popup.getWidth() + counterX - loc[0] - Screen.dp(10), 0f));
        anims.add(ObjectAnimator.ofFloat(tabBar, View.TRANSLATION_X, -popup.getWidth() + counterX - loc[0] - Screen.dp(10), 0f));
        anims.add(ObjectAnimator.ofInt(tabBar, ReactionsTabBar.SELECTOR_ALPHA, 0, 255));
        if (tabBar.reactionsTab != null && tabBar.viewersTab != null) {
          anims.add(ObjectAnimator.ofFloat(tabBar.viewersTab, View.TRANSLATION_X, Screen.dp(-8), 0f));
        }
        anims.add(ObjectAnimator.ofFloat(backBtn, View.ALPHA, 0f, 1f));

        set.playTogether(anims);
        set.setDuration(400);
        set.setInterpolator(CubicBezierInterpolator.DEFAULT);
        set.addListener(new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd (Animator animation) {
            popup.removeView(bg);
            popup.removeView(scrollView);
            popup.addView(scrollView, 1);
            optionsLayout.setVisibility(View.INVISIBLE);
            popup.setClipChildren(true);
            scrollView.setClipChildren(true);
            scrollContent.setClipChildren(true);
            topBar.setClipChildren(true);
            onTransitionDone.run();
          }
        });
        set.start();
        return true;
      }
    });
  }

  public void dismissFromOptionsSheet (CounterView reactionsCounter, CounterView seenCounter, OptionsLayout optionsLayout, View countersButton) {
    optionsLayout.setVisibility(View.VISIBLE);
    int sheetContentH = optionsLayout.getHeight() - optionsLayout.getChildAt(0).getHeight();
    View bg = new View(context);
    bg.setBackgroundColor(Theme.fillingColor());
    popup.addView(bg, 1, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, sheetContentH + scrollView.getScrollY(), Gravity.BOTTOM));
    // reorder views so scrollView is drawn above optionsLayout
    popup.removeView(scrollView);
    popup.addView(scrollView);
    optionsLayout.setTranslationX(0);
    popup.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
      @Override
      public boolean onPreDraw () {
        popup.getViewTreeObserver().removeOnPreDrawListener(this);

        popup.setClipChildren(false);
        scrollView.setClipChildren(false);
        scrollContent.setClipChildren(false);
        topBar.setClipChildren(false);

        AnimatorSet set = new AnimatorSet();
        ArrayList<Animator> anims = new ArrayList<>();
        anims.add(ObjectAnimator.ofFloat(optionsLayout, View.TRANSLATION_X, -optionsLayout.getWidth(), 0f));
        anims.add(ObjectAnimator.ofFloat(scrollView, View.TRANSLATION_X, optionsLayout.getWidth()));

        CounterView counterForOffset = reactionsCounter.getVisibility() == View.VISIBLE ? reactionsCounter : seenCounter;
        int[] loc = {0, 0};
        counterForOffset.getLocationOnScreen(loc);
        int counterX = loc[0];
        tabBar.getLocationOnScreen(loc);
        anims.add(ObjectAnimator.ofFloat(backBtn, View.TRANSLATION_X, -popup.getWidth() + counterX - loc[0] - Screen.dp(10)));
        anims.add(ObjectAnimator.ofFloat(tabBar, View.TRANSLATION_X, -popup.getWidth() + counterX - loc[0] - Screen.dp(10)));
        anims.add(ObjectAnimator.ofInt(tabBar, ReactionsTabBar.SELECTOR_ALPHA, 0));
        if (tabBar.reactionsTab != null && tabBar.viewersTab != null) {
          anims.add(ObjectAnimator.ofFloat(tabBar.viewersTab, View.TRANSLATION_X, Screen.dp(-8)));
        }
        anims.add(ObjectAnimator.ofFloat(backBtn, View.ALPHA, 0f));
        if (scrollView.getScrollY() > 0) {
          anims.add(ObjectAnimator.ofFloat(optionsLayout, View.TRANSLATION_Y, -scrollView.getScrollY(), 0f));
          anims.add(ObjectAnimator.ofInt(scrollView, SCROLL_Y, 0));
          anims.add(ObjectAnimator.ofFloat(bg, View.TRANSLATION_Y, scrollView.getScrollY()));
        }

        set.playTogether(anims);
        set.setDuration(400);
        set.setInterpolator(CubicBezierInterpolator.DEFAULT);
        set.addListener(new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd (Animator animation) {
            popup.removeView(bg);
            popup.removeView(scrollView);
            popup.setClipChildren(true);
            countersButton.setAlpha(1f);
            countersButton.setEnabled(true);
          }
        });
        set.start();

        return true;
      }
    });
  }

  public void showForSingleReaction (String reaction) {
    isFromOptions = false;
    popup.showSimplePopupView(scrollView, Screen.currentHeight());
    topPadding.getLayoutParams().height = Screen.currentHeight() / 3 - HeaderView.getTopOffset();
    for (int i = 0; i < viewControllers.size(); i++) {
      if (reaction.equals(viewControllers.get(i).reaction)) {
        pager.setCurrentItem(i);
        break;
      }
    }
    backBtn.setButtonFactor(BackHeaderButton.TYPE_CLOSE);
  }

  private void scrolledToBottomChanged () {
    if (statusBarBgAnimator != null)
      statusBarBgAnimator.cancel();
    themeListeners.removeThemeListenerByTarget(topBar);
    themeListeners.addThemeBackgroundColorListener(topBar, scrolledToBottom ? R.id.theme_color_headerLightBackground : R.id.theme_color_background);
    AnimatorSet set = new AnimatorSet();
    if (scrolledToBottom) {
      statusBarBackground.setVisibility(View.VISIBLE);
      set.playTogether(
        ObjectAnimator.ofFloat(statusBarBackground, View.TRANSLATION_Y, 0f),
        ReactionUtils.animateColor(ObjectAnimator.ofInt(topBar, "backgroundColor", Theme.backgroundColor(), Theme.getColor(R.id.theme_color_headerLightBackground))),
        ObjectAnimator.ofFloat(topShadow, View.ALPHA, 1f)
      );
      set.addListener(new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd (Animator animation) {
          statusBarBgAnimator = null;
        }
      });
    } else {
      set.playTogether(
        ObjectAnimator.ofFloat(statusBarBackground, View.TRANSLATION_Y, statusBarBackground.getHeight()),
        ReactionUtils.animateColor(ObjectAnimator.ofInt(topBar, "backgroundColor", Theme.getColor(R.id.theme_color_headerLightBackground), Theme.backgroundColor())),
        ObjectAnimator.ofFloat(topShadow, View.ALPHA, 0f)
      );
      set.addListener(new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd (Animator animation) {
          statusBarBgAnimator = null;
          statusBarBackground.setVisibility(View.INVISIBLE);
        }
      });
    }
    set.setDuration(200);
    set.setInterpolator(CubicBezierInterpolator.DEFAULT);
    set.start();
    statusBarBgAnimator = set;
  }

  private void appendReactions (List<TdApi.AddedReaction> reactions, boolean addToAll) {
    for (TdApi.AddedReaction ar : reactions) {
      ListItem item = new ListItem(ListItem.TYPE_USER_REACTION).setLongId(((TdApi.MessageSenderUser) ar.senderId).userId).setStringValue(ar.reaction);
      int pos;
      if (addToAll) {
        pos = allReactionsTab.listItems.size();
        allReactionsTab.listItems.add(item);
        allReactionsTab.adapter.notifyItemInserted(pos);
      }
      TabViewController tab = viewControllersByType.get(ar.reaction);
      if (tab == null || tab.knownUserIDs.contains(((TdApi.MessageSenderUser) ar.senderId).userId))
        continue;
      pos = tab.listItems.size();
      tab.listItems.add(item);
      tab.adapter.notifyItemInserted(pos);
      tab.knownUserIDs.add(((TdApi.MessageSenderUser) ar.senderId).userId);
    }
  }

  private void loadMoreReactions (TabViewController tab) {
    loadingMore = true;
    tdlib.sendOnUiThread(new TdApi.GetMessageAddedReactions(message.getChatId(), message.getMessageForReactions().id, tab.reaction, tab.next, 100), res -> {
      if (res instanceof TdApi.AddedReactions) {
        TdApi.AddedReactions ar = (TdApi.AddedReactions) res;
        if (tab == allReactionsTab) {
          for (TabViewController vc : viewControllers) {
            vc.next = ar.nextOffset;
          }
        } else {
          tab.next = ar.nextOffset;
        }
        appendReactions(Arrays.asList(ar.reactions), tab == allReactionsTab);
        loadingMore = false;
      }
    });
  }

  public static CharSequence getViewString (TGMessage msg, int count) {
    switch (msg.getMessage().content.getConstructor()) {
      case TdApi.MessageVoiceNote.CONSTRUCTOR: {
        return Lang.pluralBold(R.string.MessageSeenXListened, count);
      }
      case TdApi.MessageVideoNote.CONSTRUCTOR: {
        return Lang.pluralBold(R.string.MessageSeenXPlayed, count);
      }
      default: {
        return Lang.pluralBold(R.string.xViews, count);
      }
    }
  }

  private class PagerAdapter extends androidx.viewpager.widget.PagerAdapter {
    @Override
    public int getCount () {
      return viewControllers.size();
    }

    @Override
    public boolean isViewFromObject (@NonNull View view, @NonNull Object object) {
      return view == ((TabViewController) object).list;
    }

    @NonNull
    @Override
    public Object instantiateItem (@NonNull ViewGroup container, int position) {
      TabViewController vc = viewControllers.get(position);
      container.addView(vc.list);
      return vc;
    }

    @Override
    public void destroyItem (@NonNull ViewGroup container, int position, @NonNull Object object) {
      TabViewController vc = (TabViewController) object;
      container.removeView(vc.list);
    }
  }

  private class TabViewController {
    private RecyclerView list;

    public String next;
    public int total;
    public List<ListItem> listItems;
    public HashSet<Long> knownUserIDs = new HashSet<>();
    public SettingsAdapter adapter;
    public boolean isViewers;
    public String reaction;

    public TabViewController (String reaction) {
      this(false, reaction);
    }

    public TabViewController (boolean isViewers, String reaction) {
      this.isViewers = isViewers;
      this.reaction = reaction;
      list = new RecyclerView(context);
      adapter = new SettingsAdapter(chatController) {
        @Override
        protected void setUserAndReaction (ListItem item, int position, UserReactionView userView, boolean isUpdate) {
          userView.setUser(item.getLongId());
          userView.setNeedSeparator(position < listItems.size() - 1);
          if (!isViewers && reaction == null) {
            ((UserReactionView) userView).setReaction(item.getStringValue());
          }
        }

        @Override
        protected void setInfo (ListItem item, int position, ListInfoView infoView) {
          if (isViewers)
            infoView.showInfo(getViewString(message, listItems.size()));
          else
            infoView.showInfo(Lang.pluralBold(R.string.xReactions, total));
        }

        @Override
        public SettingHolder onCreateViewHolder (ViewGroup parent, int viewType) {
          SettingHolder holder = super.onCreateViewHolder(parent, viewType);
          if (viewType == ListItem.TYPE_USER_REACTION || viewType == ListItem.TYPE_USER) {
            holder.itemView.setOnClickListener(TabViewController.this::onViewClick);
          }
          return holder;
        }
      };

      List<ListItem> items = adapter.getItems();
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      if (isViewers)
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, R.id.description, 0, R.string.MessageSeenPrivacy));
      items.add(new ListItem(ListItem.TYPE_LIST_INFO_VIEW));
      listItems = items.subList(0, 0);

      list.setLayoutManager(new LinearLayoutManager(context));
      list.setAdapter(adapter);
      list.setItemAnimator(null);
      list.addOnScrollListener(new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
          if (!loadingMore && !isViewers && recyclerView.getChildCount() > 0 && !TextUtils.isEmpty(next)) {
            View last = list.getChildAt(recyclerView.getChildCount() - 1);
            if (list.getChildAdapterPosition(last) == adapter.getItemCount() - 1) {
              loadMoreReactions(TabViewController.this);
            }
          }
        }
      });
      ViewSupport.setThemedBackground(list, R.id.theme_color_background);
      themeListeners.addThemeBackgroundColorListener(list, R.id.theme_color_background);
    }

    public void clearItems () {
      int size = listItems.size();
      listItems.clear();
      adapter.notifyItemRangeRemoved(0, size);
      knownUserIDs.clear();
    }

    private void onViewClick (View v) {
      if (!isFromOptions) {
        popup.hideWindow(true);
      }
      tdlib.ui().openPrivateProfile(chatController, ((ListItem) v.getTag()).getLongId(), new TdlibUi.UrlOpenParameters());
    }
  }
}
