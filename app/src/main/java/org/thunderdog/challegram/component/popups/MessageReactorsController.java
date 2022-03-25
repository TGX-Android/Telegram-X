package org.thunderdog.challegram.component.popups;

import android.content.Context;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.SparseArrayCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.MediaBottomBaseController;
import org.thunderdog.challegram.component.attach.MediaLayout;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.navigation.ViewPagerController;
import org.thunderdog.challegram.navigation.ViewPagerHeaderViewCompact;
import org.thunderdog.challegram.navigation.ViewPagerTopView;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.SettingHolder;
import org.thunderdog.challegram.widget.RtlViewPager;

import me.vkryl.android.widget.FrameLayoutFix;

public class MessageReactorsController extends MediaBottomBaseController<Void> implements ViewPager.OnPageChangeListener, ViewPagerTopView.OnItemClickListener {
  private final long chatId;
  private final long msgId;
  private final int reactionCount;
  private final TdApi.MessageReaction[] reactions;

  private ViewPagerHeaderViewCompact headerCell;
  private ViewPagerAdapter pagerAdapter;
  private RtlViewPager pager;

  public static CharSequence getViewString (int count) {
    return Lang.pluralBold(R.string.MessageXReacted, count);
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_CLOSE;
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    mediaLayout.hide(false);
    return true;
  }

  @Override
  public void dispatchRecyclerTouchEvent (MotionEvent e) {
    super.dispatchRecyclerTouchEvent(e);
    if (pager != null) {
      ((MessageReactionsUserListController) pagerAdapter.getCachedItemByPosition(pager.getCurrentItem())).dispatchEventToRecycler(e);
    }
  }

  public MessageReactorsController (MediaLayout context, long chatId, long msgId, int reactionCount, TdApi.MessageReaction[] reactions) {
    super(context, getViewString(reactionCount).toString());
    this.reactions = reactions;
    this.reactionCount = reactionCount;
    this.chatId = chatId;
    this.msgId = msgId;
  }

  private boolean allowExpand;

  @Override
  protected View onCreateView (Context context) {
    buildContentView(false);
    setLayoutManager(new LinearLayoutManager(context(), RecyclerView.VERTICAL, false));

    // Build ViewPagers + header
    headerCell = new ViewPagerHeaderViewCompact(context);
    FrameLayoutFix.LayoutParams params = (FrameLayoutFix.LayoutParams) (headerCell).getRecyclerView().getLayoutParams();
    if (getBackButton() != BackHeaderButton.TYPE_NONE) {
      if (Lang.rtl()) {
        params.rightMargin = Screen.dp(56f);
      } else {
        params.leftMargin = Screen.dp(56f);
      }
    }
    headerCell.getTopView().checkRtl();
    headerCell.getTopView().setItems(new String[0]);
    for (TdApi.MessageReaction r: reactions) {
      headerCell.getTopView().addItem(String.valueOf(r.totalCount), r.reaction, createStaticFile(tdlib.getReaction(r.reaction).staticIcon));
    }
    headerCell.getTopView().setTextFromToColorId(R.id.theme_color_textLight, R.id.theme_color_text);
    headerCell.getTopView().setSelectionColorId(R.id.theme_color_text);
    headerCell.getTopView().setSelectionFactor(0f);
    headerCell.getTopView().setOnItemClickListener(this);

    initMetrics();
    this.allowExpand = getInitialContentHeight() == super.getInitialContentHeight();
    setAdapter(new RecyclerView.Adapter<>() {
      @NonNull
      @Override
      public RecyclerView.ViewHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
        return new VpWrap(new RtlViewPager(parent.getContext()));
      }

      @Override
      public void onBindViewHolder (@NonNull RecyclerView.ViewHolder holder, int position) {
        bindViewPager((RtlViewPager) holder.itemView);
      }

      @Override
      public int getItemCount () {
        return 1;
      }

      class VpWrap extends RecyclerView.ViewHolder {
        public VpWrap (@NonNull View itemView) {
          super(itemView);
        }
      }
    });

    return contentView;
  }

  private ImageFile createStaticFile (TdApi.Sticker staticIcon) {
    ImageFile staticIconFile = new ImageFile(tdlib, staticIcon.sticker);
    staticIconFile.setSize(Screen.dp(36f) * 2);
    staticIconFile.setNoBlur();
    return staticIconFile;
  }

  private void bindViewPager (RtlViewPager pager) {
    if (pagerAdapter == null) {
      pagerAdapter = new ViewPagerAdapter(context, this);
    }

    this.pager = pager;
    pager.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.MATCH_PARENT));
    pager.setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? View.OVER_SCROLL_IF_CONTENT_SCROLLS : View.OVER_SCROLL_NEVER);
    pager.addOnPageChangeListener(this);
    pager.setAdapter(pagerAdapter);
    pager.setCurrentItem(0);
  }

  public final int getCurrentPagerItemPosition() {
    return pagerAdapter.reversePosition(pager.getCurrentItem());
  }

  @Override
  public void onPagerItemClick (int index) {
    if (getCurrentPagerItemPosition() == index) {
      ViewController<?> c = pagerAdapter.getCachedItemByPosition(pager.getCurrentItem());
      if (c instanceof ViewPagerController.ScrollToTopDelegate) {
        ((ViewPagerController.ScrollToTopDelegate) c).onScrollToTopRequested();
      }
    } else if (pager.isPagingEnabled()) {
      setCurrentPagerPosition(index, true);
    }
  }

  protected final void setCurrentPagerPosition (int position, boolean animated) {
    if (headerCell != null && animated) {
      headerCell.getTopView().setFromTo(pager.getCurrentItem(), position);
    }
    pager.setCurrentItem(position, animated);
  }

  @Override
  public void onPageScrolled (int position, float positionOffset, int positionOffsetPixels) {
    if (headerCell != null) {
      headerCell.getTopView().setSelectionFactor((float) position + positionOffset);
    }
  }

  @Override
  public void onPageSelected (int position) {

  }

  @Override
  public void onPageScrollStateChanged (int state) {

  }

  @Override
  public void destroy () {
    super.destroy();
    this.pager = null;
    if (pagerAdapter != null) {
      pagerAdapter.destroyCachedItems();
    }
    if (headerCell != null) {
      headerCell.getTopView().performDestroy();
    }
  }

  @Override
  protected void onCompleteShow (boolean isPopup) {
    //mediaLayout.getHeaderView().getFilling().setColor(Theme.getColor(R.id.theme_color_background));
    //mediaLayout.getHeaderView().setVisibility(View.GONE);
  }

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }

  @Override
  public boolean anchorHeaderToContent () {
    return true;
  }

  @Override
  public float getContentTranslationY () {
    return recyclerView.getTranslationY();
  }

  @Override
  protected int getInitialContentHeight () {
    int initialContentHeight = SettingHolder.measureHeightForType(ListItem.TYPE_USER) * reactionCount;
    initialContentHeight += Screen.dp(24f);
    return Math.min(super.getInitialContentHeight(), initialContentHeight);
  }

  @Override
  protected boolean canExpandHeight () {
    return allowExpand;
  }

  @Override
  protected ViewGroup createCustomBottomBar () {
    return new FrameLayout(context);
  }

  @Override
  public int getId () {
    return R.id.controller_messageReacted;
  }

  //

  public static class ViewPagerAdapter extends PagerAdapter {
    private final Context context;
    private final MessageReactorsController parent;
    private final SparseArrayCompat<ViewController<?>> cachedItems;

    public ViewPagerAdapter (Context context, MessageReactorsController parent) {
      this.context = context;
      this.parent = parent;
      this.cachedItems = new SparseArrayCompat(parent.getPagerItemCount());
    }

    public @Nullable
    ViewController<?> getCachedItemByPosition (int position) {
      return cachedItems.get(position);
    }

    public @Nullable ViewController<?> getCachedItemById (int id) {
      int size = cachedItems.size();
      for (int i = 0; i < size; i++) {
        ViewController<?> c = cachedItems.valueAt(i);
        if (c.getId() == id) {
          return c;
        }
      }
      return null;
    }

    @Override
    public int getCount () {
      return parent.getPagerItemCount();
    }

    @Override
    public void destroyItem (ViewGroup container, int position, @NonNull Object object) {
      container.removeView(((ViewController<?>) object).get());
    }

    public void destroyCachedItems () {
      final int count = cachedItems.size();
      for (int i = 0; i < count; i++) {
        ViewController<?> c = cachedItems.valueAt(i);
        if (!c.isDestroyed()) {
          c.destroy();
        }
      }
      cachedItems.clear();
    }

    private int reversePosition (int position) {
      return position; // FIXME RTL Lang.rtl() ? getCount() - position - 1 : position;
    }

    @Override
    public int getItemPosition (@NonNull Object object) {
      int count = cachedItems.size();
      for (int i = 0; i < count; i++) {
        if (cachedItems.valueAt(i) == object) {
          return reversePosition(cachedItems.keyAt(i));
        }
      }
      return POSITION_NONE;
    }

    public ViewController<?> prepareViewController (int position) {
      ViewController<?> c = cachedItems.get(position);
      if (c == null) {
        c = parent.onCreatePagerItemForPosition(context, position);
        c.setParentWrapper(parent);
        c.bindThemeListeners(parent);
        cachedItems.put(position, c);
      }
      return c;
    }

    @Override
    @NonNull
    public Object instantiateItem (@NonNull ViewGroup container, int position) {
      ViewController<?> c = prepareViewController(reversePosition(position));
      container.addView(c.get());
      return c;
    }

    @Override
    public boolean isViewFromObject (@NonNull View view, @NonNull Object object) {
      return object instanceof ViewController && ((ViewController<?>) object).getWrapUnchecked() == view;
    }
  }

  private ViewController<?> onCreatePagerItemForPosition (Context context, int position) {
    MessageReactionsUserListController mrc = new MessageReactionsUserListController(context, tdlib);
    mrc.setArguments(new MessageReactionsUserListController.Args(chatId, msgId, reactions[position].reaction, () -> mediaLayout.hide(false)));
    return mrc;
  }

  private int getPagerItemCount () {
    return reactions.length;
  }
}