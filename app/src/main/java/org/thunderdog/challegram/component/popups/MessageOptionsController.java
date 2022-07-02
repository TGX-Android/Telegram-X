package org.thunderdog.challegram.component.popups;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.core.util.ObjectsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.MediaBottomBaseController;
import org.thunderdog.challegram.component.attach.MediaLayout;
import org.thunderdog.challegram.component.reaction.ReactionAdapter;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.navigation.OptionsLayout;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.navigation.ViewPagerTopView;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.widget.ScrollToTopDelegate;
import org.thunderdog.challegram.widget.ShadowView;
import org.thunderdog.challegram.widget.ViewPager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.vkryl.android.ViewUtils;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

// FIXME landscape orientation, back button, scroll
public class MessageOptionsController extends MediaBottomBaseController<Void> implements View.OnClickListener {

  private static final float REACTION_BAR_HEIGHT = 54f;
  private static final float OPTION_HEIGHT = 54f;

  private final Options options;
  private final TGMessage message;
  private final List<TdApi.Reaction> availableReactions;

  private OptionsLayout optionsLayout;
  private FrameLayoutFix contentLayout;

  private ViewPager viewPager;
  private ViewPagerAdapter viewPagerAdapter;
  @Nullable
  private ViewPagerTopView viewPagerTopView;

  private RecyclerView reactionBarRecyclerView;

  @Px
  private final int reactionBarHeight;

  private boolean canExpandHeight;

  private int topViewVisibleItemCount;

  private final String targetReaction;

  public MessageOptionsController (@NonNull MediaLayout context, @NonNull TGMessage message, @NonNull Options options, @NonNull List<TdApi.Reaction> availableReactions, @Nullable String targetReaction) {
    super(context, null);
    this.message = message;
    this.options = options;
    this.availableReactions = availableReactions;
    this.targetReaction = targetReaction;

    this.reactionBarHeight = Screen.dp(REACTION_BAR_HEIGHT);
  }

  @Override
  protected View onCreateView (Context context) {
    buildContentView(false);

    int targetItem = 0;

    contentLayout = new FrameLayoutFix(context);
    contentLayout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    viewPagerAdapter = new ViewPagerAdapter(this, message);
    viewPager = new ViewPager(context);

    contentLayout.addView(viewPager, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP, 0, reactionBarHeight, 0, 0));

    optionsLayout = new OptionsLayout(context, this);
    optionsLayout.setInfo(this, tdlib(), options.info, false);
    optionsLayout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    ViewUtils.setBackground(optionsLayout, null);

    viewPagerAdapter.add(new MessageOptionsPage(optionsLayout));
    if (message.canGetAddedReactions() && message.hasReactions()) {
      topViewVisibleItemCount++;
      viewPagerAdapter.add(new MessageAddedReactionsPage(message.getTotalReactionCount()));

      TdApi.MessageReaction[] messageReactions = message.getReactions();
      if (messageReactions != null) {
        for (TdApi.MessageReaction messageReaction : messageReactions) {
          viewPagerAdapter.add(new MessageAddedReactionsPage(messageReaction.reaction, messageReaction.totalCount));
          if (targetReaction != null && targetReaction.equals(messageReaction.reaction)) {
            targetItem = viewPagerAdapter.getPageCount() - 1;
          }
        }
      }
    }
    if (message.canGetViewers() && !(message.isUnread() && !message.noUnread())) {
      topViewVisibleItemCount++;
      if (targetItem != 0) {
        targetItem++; // FIXME
      }
      int index = Math.min(viewPagerAdapter.getPageCount(), 2);
      viewPagerAdapter.pageList.add(index, new MessageViewersPage());
    }

    insertReactionBar();

    View.OnClickListener onClickListener = v -> {
      mediaLayout.hide(false);
      MessagesController target = mediaLayout.getTarget();
      if (target != null) {
        target.onOptionItemPressed(v, v.getId());
      }
    };

    for (OptionItem item : options.items) {
      TextView optionView = OptionsLayout.genOptionView(context, item.id, item.name, item.color, item.icon, onClickListener, getThemeListeners(), null);
      RippleSupport.setTransparentSelector(optionView);
      optionView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(OPTION_HEIGHT)));
      optionsLayout.addView(optionView);
    }

    recyclerView.setLayoutManager(new LinearLayoutManager(context));
    recyclerView.setAdapter(new SingleItemAdapter(contentLayout));

    viewPager.setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? View.OVER_SCROLL_IF_CONTENT_SCROLLS : View.OVER_SCROLL_NEVER);
    viewPager.setAdapter(viewPagerAdapter);
    viewPager.setOffscreenPageLimit(2);

    List<String> items = new ArrayList<>();
    for (Page page : viewPagerAdapter.pageList) {
      if (page instanceof MessageAddedReactionsPage) {
        MessageAddedReactionsPage typedPage = (MessageAddedReactionsPage) page;
        String reaction = typedPage.reaction != null ? typedPage.reaction : "\uD83E\uDD0D";
        items.add(reaction + " " + typedPage.totalCount); // FIXME reaction staticIcon
      } else if (page instanceof MessageViewersPage) {
        items.add("\uD83D\uDC41️ "); // FIXME eye icon, totalCount
      }
    }
    if (!items.isEmpty()) {
      viewPagerTopView = new ViewPagerTopView(context);
      viewPagerTopView.setSelectionColorId(R.id.theme_color_text);
      viewPagerTopView.setTextFromToColorId(R.id.theme_color_textLight, R.id.theme_color_text);
      addThemeInvalidateListener(viewPagerTopView);

      viewPagerTopView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
        @Override
        public void onLayoutChange (View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
          int topViewMinimumWidth = getTopViewMinimumWidth();
          viewPagerTopView.removeOnLayoutChangeListener(this);
          if (viewPager.getCurrentItem() == 0) {
            viewPagerTopView.setTranslationX(v.getWidth() - topViewMinimumWidth);
            ViewSupport.setThemedBackground(viewPagerTopView, R.id.theme_color_background, MessageOptionsController.this);
          } else {
            viewPagerTopView.setTranslationX(0f);
            ViewSupport.setThemedBackground(viewPagerTopView, R.id.theme_color_headerLightBackground, MessageOptionsController.this);
          }
          if (reactionBarRecyclerView != null) {
            reactionBarRecyclerView.setPadding(Screen.dp(8f), 0, 0, 0);
            ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) reactionBarRecyclerView.getLayoutParams();
            layoutParams.rightMargin = topViewMinimumWidth;
            reactionBarRecyclerView.setLayoutParams(layoutParams);
            reactionBarRecyclerView.setFadingEdgeLength(Screen.dp(54f));
            reactionBarRecyclerView.setHorizontalFadingEdgeEnabled(true);
          }
        }
      });

      ShadowView viewPagerTopShadowView = new ShadowView(context);
      viewPagerTopShadowView.setSimpleBottomTransparentShadow(true);
      viewPagerTopShadowView.setAlpha(0f);
      addThemeInvalidateListener(viewPagerTopShadowView);

      ViewSupport.setThemedBackground(viewPagerTopView, R.id.theme_color_headerLightBackground, this);
      contentLayout.addView(viewPagerTopView, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, reactionBarHeight, Gravity.TOP));
      contentLayout.addView(viewPagerTopShadowView, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(10f), Gravity.TOP, 0, reactionBarHeight, 0, 0));

      viewPagerTopView.setItems(items.toArray(new String[0]));
      viewPagerTopView.setOnItemClickListener(index -> {
        int currentItem = viewPager.getCurrentItem();
        int currentIndex = currentItem - 1;
        if (index == currentIndex) {
          View view = viewPagerAdapter.getCachedView(currentItem);
          if (view instanceof ScrollToTopDelegate) {
            ((ScrollToTopDelegate) view).onScrollToTopRequested();
          }
        } else {
          viewPagerTopView.setFromTo(currentIndex, index);
          viewPager.setCurrentItem(index + 1);
        }
      });

      viewPager.addOnPageChangeListener(new androidx.viewpager.widget.ViewPager.SimpleOnPageChangeListener() {
        @Override
        public void onPageScrolled (int position, float positionOffset, int positionOffsetPixels) {
          if (Float.isNaN(positionOffset)) {
            return;
          }
          if (viewPagerTopView != null) {
            viewPagerTopView.setSelectionFactor((float) (position - 1) + positionOffset);
          }
          float topViewWidth = viewPagerTopView.getWidth();
          if (position == 0) {
            float translationX = (topViewWidth - getTopViewMinimumWidth()) * MathUtils.clamp(1f - positionOffset);
            viewPagerTopView.setTranslationX(translationX);
            viewPagerTopShadowView.setAlpha(positionOffset);
            if (positionOffset == 0f) {
              ViewSupport.setThemedBackground(viewPagerTopView, R.id.theme_color_background, MessageOptionsController.this);
            } else {
              viewPagerTopView.setBackgroundColor(
                ColorUtils.fromToArgb(
                  Theme.getColor(R.id.theme_color_background),
                  Theme.getColor(R.id.theme_color_headerLightBackground),
                  positionOffset
                )
              );
            }
          } else {
            viewPagerTopView.setTranslationX(0f);
            viewPagerTopShadowView.setAlpha(1f);
            ViewSupport.setThemedBackground(viewPagerTopView, R.id.theme_color_headerLightBackground, MessageOptionsController.this);
          }
        }

        @Override
        public void onPageSelected (int position) {
          if (position > 0) {
            canExpandHeight = true;
          }
        }
      });
    }

    initMetrics();

    viewPager.setCurrentItem(targetItem);

    return contentView;
  }

  private int getTopViewMinimumWidth () {
    int minimumWidth = 0;
    if (viewPagerTopView != null) {
      for (int i = 0; i < topViewVisibleItemCount; i++) {
        View child = viewPagerTopView.getChildAt(i);
        if (child != null) {
          minimumWidth += child.getMeasuredWidth();
        }
      }
    }
    return minimumWidth;
  }

  @Override
  protected int getHeaderSize (boolean includeOffset) {
    return 0;
  }

  @Override
  protected int getInitialContentHeight () {
    if (options == null) {
      return super.getInitialContentHeight();
    }
    int contentHeight = options.items.length * Screen.dp(OPTION_HEIGHT);
    if (optionsLayout != null) {
      contentHeight += optionsLayout.getTextHeight();
    }
    if (availableReactions != null && !availableReactions.isEmpty()) {
      contentHeight += reactionBarHeight;
    }
    return contentHeight;
  }

  @Override
  public boolean ignoreStartHeightLimits () {
    return true;
  }

  @Override
  public int getId () {
    return R.id.controller_messageOptions;
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    mediaLayout.hide(false);
    return true;
  }

  @Override
  protected boolean canExpandHeight () {
    return canExpandHeight;
  }

  private void insertReactionBar () {
    if (availableReactions == null || availableReactions.isEmpty()) {
      return;
    }
    FrameLayoutFix reactionBarLayout = new FrameLayoutFix(context());
    ViewSupport.setThemedBackground(reactionBarLayout, R.id.theme_color_background, this);

    ReactionAdapter reactionAdapter = new ReactionAdapter(tdlib(), this::onReactionClick);
    reactionAdapter.setItems(availableReactions);
    LinearLayoutManager layoutManager = new LinearLayoutManager(context(), LinearLayoutManager.HORIZONTAL, false);

    reactionBarRecyclerView = new RecyclerView(context());
    reactionBarRecyclerView.setLayoutManager(layoutManager);
    reactionBarRecyclerView.setAdapter(reactionAdapter);
    reactionBarRecyclerView.setPadding(Screen.dp(8f), 0, Screen.dp(8f), 0);
    reactionBarRecyclerView.setClipToPadding(false);

    reactionBarLayout.addView(reactionBarRecyclerView, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    contentLayout.addView(reactionBarLayout, 0, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, reactionBarHeight, Gravity.TOP));
  }

  private void onReactionClick (@NonNull View itemView) {
    Object tag = itemView.getTag();
    if (!(tag instanceof TdApi.Reaction)) {
      return;
    }
    TdApi.Reaction pressedReaction = (TdApi.Reaction) tag;
    TdApi.MessageReaction messageReaction = TD.findMessageReaction(message, pressedReaction.reaction);
    if (messageReaction == null || !messageReaction.isChosen) {
      View currentView = message.findCurrentView();
      if (currentView != null) {
        UI.hapticVibrate(currentView, false);
      }
    }
    String newReaction = pressedReaction.reaction;
    tdlib().send(new TdApi.SetMessageReaction(message.getChatId(), message.getId(), newReaction, false), tdlib().okHandler());
    mediaLayout.hide(false);
  }

  @Override
  public void onClick (View view) {
    if (view.getId() == R.id.user) {
      Object tag = view.getTag();
      if (tag instanceof ListItem) {
        ListItem listItem = (ListItem) tag;
        TGUser user;
        if (listItem.getData() instanceof TGUser) {
          user = (TGUser) listItem.getData();
        } else if (listItem.getLongId() != 0L) {
          TdApi.User chatUser = tdlib().chatUser(listItem.getLongId());
          user = chatUser != null ? new TGUser(tdlib(), chatUser) : null;
        } else {
          user = null;
        }
        if (user != null) {
          mediaLayout.hide(false);
          openProfile(view, user);
        }
      }
    }
  }

  private void openProfile (@NonNull View view, @NonNull TGUser user) {
    TooltipOverlayView.TooltipBuilder tooltipBuilder = context().tooltipManager().builder(view);
    TdlibUi.UrlOpenParameters openParameters = new TdlibUi.UrlOpenParameters().tooltip(tooltipBuilder);
    if (user.getUserId() != 0L) {
      tdlib.ui().openPrivateProfile(this, user.getUserId(), openParameters);
    } else if (user.getChatId() != 0L) {
      tdlib.ui().openChatProfile(this, user.getChatId(), null, openParameters);
    }
  }

  private abstract static class Page {

  }

  private static class MessageOptionsPage extends Page {
    @NonNull
    private final View view;

    private MessageOptionsPage (@NonNull View view) {
      this.view = view;
    }

    @Override
    public boolean equals (Object o) {
      return o instanceof MessageOptionsPage && ((MessageOptionsPage) o).view.equals(view);
    }

    @Override
    public int hashCode () {
      return ObjectsCompat.hashCode(view);
    }
  }

  private static class MessageViewersPage extends Page {
    @Override
    public boolean equals (@Nullable Object obj) {
      return obj instanceof MessageViewersPage;
    }
  }

  private static class MessageAddedReactionsPage extends Page {
    private final int totalCount;
    @Nullable
    private final String reaction;

    private MessageAddedReactionsPage (int totalCount) {
      this(null, totalCount);
    }

    private MessageAddedReactionsPage (@Nullable String reaction, int totalCount) {
      this.reaction = reaction;
      this.totalCount = totalCount;
    }

    @Override
    public boolean equals (Object o) {
      return o instanceof MessageAddedReactionsPage && ObjectsCompat.equals(((MessageAddedReactionsPage) o).reaction, reaction);
    }

    @Override
    public int hashCode () {
      return ObjectsCompat.hashCode(reaction);
    }
  }

  private static class ViewPagerAdapter extends PagerAdapter {
    private final List<Page> pageList = new ArrayList<>();
    private final Map<Page, View> views = new HashMap<>();

    private final TGMessage message;
    private final ViewController<?> controller;

    private ViewPagerAdapter (ViewController<?> controller, TGMessage message) {
      this.controller = controller;
      this.message = message;
    }

    @Override
    public int getCount () {
      return pageList.size();
    }

    @Override
    public boolean isViewFromObject (@NonNull View view, @NonNull Object object) {
      return view == getView((Page) object);
    }

    @NonNull
    @Override
    public Object instantiateItem (@NonNull ViewGroup container, int position) {
      Page page = pageList.get(position);
      container.addView(getView(page));
      return page;
    }

    @Override
    public void destroyItem (@NonNull ViewGroup container, int position, @NonNull Object object) {
      if (!(object instanceof Page)) {
        return;
      }
      Page page = (Page) object;
      View view = views.remove(page);
      if (view != null) {
        container.removeView(view);
      }
    }

    @Nullable
    private View getCachedView (int position) {
      Page page = pageList.get(position);
      return views.get(page);
    }

    @NonNull
    private View getView (Page page) {
      View view = views.get(page);
      if (view == null) {
        view = createView(page);
        views.put(page, view);
      }
      return view;
    }

    @NonNull
    private View createView (Page page) {
      if (page instanceof MessageOptionsPage) {
        return ((MessageOptionsPage) page).view;
      }
      if (page instanceof MessageAddedReactionsPage) {
        MessageAddedReactionsPage addedReactionsPage = (MessageAddedReactionsPage) page;
        return new MessageAddedReactionsComponent(controller, message, addedReactionsPage.reaction);
      }
      if (page instanceof MessageViewersPage) {
        return new MessageViewersComponent(controller, message);
      }
      throw new UnsupportedOperationException();
    }

    public void add (@NonNull Page page) {
      pageList.add(page);
    }

    public int getPageCount () {
      return pageList.size();
    }
  }

  // sorry
  private static class SingleItemAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    @NonNull
    private final View itemView;

    private SingleItemAdapter (@NonNull View itemView) {
      this.itemView = itemView;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder (@NonNull RecyclerView.ViewHolder holder, int position) {
      // sorry
    }

    @Override
    public int getItemCount () {
      return 1;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
      public ViewHolder (@NonNull View itemView) {
        super(itemView);
      }
    }
  }
}
