package org.thunderdog.challegram.reactions;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.Property;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import androidx.viewpager.widget.ViewPager;

import me.vkryl.core.ColorUtils;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Counter;

import java.util.ArrayList;

public class ReactionsTabBar extends HorizontalScrollView implements ViewPager.OnPageChangeListener {
  private ViewPager pager;
  public ArrayList<View> tabViews = new ArrayList<>();
  public View reactionsTab, viewersTab;
  private TabStripLayout strip;
  private View selectedTab, otherSelectedTab;
  private int currentPosition;
  private float currentPositionOffset;
  private int selectorAlpha;

  public static final Property<ReactionsTabBar, Integer> SELECTOR_ALPHA = new Property<ReactionsTabBar, Integer>(Integer.class, "whatever") {
    @Override
    public Integer get (ReactionsTabBar object) {
      return object.selectorAlpha;
    }

    @Override
    public void set (ReactionsTabBar object, Integer value) {
      object.selectorAlpha = value;
      object.strip.invalidate();
    }
  };

  public ReactionsTabBar (Context context, ViewPager pager, TGMessage msg, int viewersCount, Tdlib tdlib, ThemeListenerList themeListeners) {
    super(context);
    this.pager = pager;
    strip = new TabStripLayout(context);
    addView(strip);
    setHorizontalScrollBarEnabled(false);
    setHorizontalFadingEdgeEnabled(true);
    setFadingEdgeLength(Screen.dp(36));

    int total = msg.getTotalReactionCount();
    if (total > 0) {
      CounterView reactionsTab = new CounterView(context, new Counter.Builder().noBackground().allBold(true).textColor(R.id.theme_color_text).drawable(R.drawable.baseline_favorite_14, 14f, 3f, Gravity.LEFT), R.id.theme_color_text);
      reactionsTab.counter.setCount(total, false);
      FrameLayout wrap = new FrameLayout(context);
      wrap.addView(reactionsTab);
      this.reactionsTab = wrap;
      addTab(wrap);
      themeListeners.addThemeInvalidateListener(reactionsTab);
    }
    if (viewersCount > 0) {
      CounterView viewersTab = new CounterView(context, new Counter.Builder().noBackground().allBold(true).textColor(R.id.theme_color_text).drawable(R.drawable.baseline_visibility_14, 14f, 3f, Gravity.LEFT), R.id.theme_color_text);
      viewersTab.counter.setCount(viewersCount, false);
      FrameLayout wrap = new FrameLayout(context);
      wrap.addView(viewersTab);
      this.viewersTab = wrap;
      addTab(wrap);
      themeListeners.addThemeInvalidateListener(viewersTab);
    }
    if (total > 0) {
      for (TdApi.MessageReaction r : msg.getReactions()) {
        LinearLayout tab = new LinearLayout(context);
        tab.setOrientation(LinearLayout.HORIZONTAL);
        tab.setGravity(Gravity.CENTER_VERTICAL);
        StickerReceiverView icon = new StickerReceiverView(context);
        icon.loadSticker(tdlib, tdlib.getReaction(r.reaction).staticIcon, true);
        tab.addView(icon, new LinearLayout.LayoutParams(Screen.dp(14), Screen.dp(14)));
        CounterView counter = new CounterView(context, new Counter.Builder().noBackground().allBold(true).textColor(R.id.theme_color_text), 0);
        counter.counter.setCount(r.totalCount, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.leftMargin = Screen.dp(4);
        tab.addView(counter, lp);
        addTab(tab);
        themeListeners.addThemeInvalidateListener(counter);
      }
    }
    selectedTab = tabViews.get(0);
    themeListeners.addThemeInvalidateListener(strip);

    pager.addOnPageChangeListener(this);
  }

  private void addTab (View tab) {
    tab.setTag(tabViews.size());
    tabViews.add(tab);
    tab.setPadding(Screen.dp(10), 0, Screen.dp(10), 0);
    tab.setAlpha(.4f);
    strip.addView(tab, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    tab.setOnClickListener(this::onTabClick);
    RippleSupport.setTransparentSelector(tab);
  }

  @Override
  public void onPageScrolled (int position, float positionOffset, int positionOffsetPixels) {
    currentPosition = position;
    currentPositionOffset = positionOffset;

    selectedTab.setAlpha(.4f);
    if (otherSelectedTab != null)
      otherSelectedTab.setAlpha(.4f);

    selectedTab = tabViews.get(position);
    if (positionOffset > 0f) {
      otherSelectedTab = tabViews.get(position + 1);
      selectedTab.setAlpha(.4f + .6f * (1f - positionOffset));
      otherSelectedTab.setAlpha(.4f + .6f * positionOffset);
    } else {
      otherSelectedTab = null;
      selectedTab.setAlpha(1f);
    }

    strip.invalidate();
  }

  @Override
  public void onPageSelected (int position) {
    View tab = tabViews.get(position);
    if (tab.getLeft() < getScrollX()) {
      smoothScrollTo(Math.max(0, tab.getLeft() - Screen.dp(8)), 0);
    } else if (tab.getRight() > getScrollX() + getWidth()) {
      smoothScrollTo(tab.getRight() + Screen.dp(8) - getWidth(), 0);
    }
  }

  @Override
  public void onPageScrollStateChanged (int state) {

  }

  @Override
  protected float getRightFadingEdgeStrength () {
    return 0f;
  }

  private void onTabClick (View v) {
    int index = (Integer) v.getTag();
    pager.setCurrentItem(index);
  }

  private class TabStripLayout extends LinearLayout {
    private Paint paint = new Paint();

    public TabStripLayout (Context context) {
      super(context);
      setOrientation(HORIZONTAL);
      setWillNotDraw(false);
    }

    @Override
    protected void onDraw (Canvas canvas) {
      super.onDraw(canvas);
      paint.setColor(Theme.getColor(R.id.theme_color_text));
      paint.setAlpha(Math.round(selectorAlpha / 255f * paint.getAlpha()));
      float x = selectedTab.getX(), width;
      if (currentPositionOffset > 0f) {
        x += selectedTab.getWidth() * currentPositionOffset;
        width = selectedTab.getWidth() + (otherSelectedTab.getWidth() - selectedTab.getWidth()) * currentPositionOffset;
      } else {
        width = selectedTab.getWidth();
      }
      canvas.drawRect(x, getHeight() - Screen.dp(2), x + width, getHeight(), paint);
    }
  }
}
