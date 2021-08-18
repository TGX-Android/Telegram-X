package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.Text;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;

/**
 * Date: 26/12/2016
 * Author: default
 */
public class ViewPagerTopView extends FrameLayoutFix implements RtlCheckListener, View.OnClickListener, View.OnLongClickListener {
  private static class Item {
    public final String string;
    public final boolean needFakeBold;
    public final @DrawableRes int iconRes;

    public Item (String string) {
      this.string = string;
      this.needFakeBold = Text.needFakeBold(string);
      this.iconRes = 0;
    }

    public Item (int iconRes) {
      this.string = null;
      this.needFakeBold = false;
      this.iconRes = iconRes;
    }

    private Drawable icon;

    public Drawable getIcon () {
      if (icon == null && iconRes != 0)
        icon = Drawables.get(iconRes);
      return icon;
    }

    @Override
    public boolean equals (Object obj) {
      return obj instanceof Item && ((Item) obj).iconRes == iconRes && StringUtils.equalsOrBothEmpty(((Item) obj).string, string);
    }

    private int width;

    public void calculateWidth (TextPaint paint) {
      final int width;
      if (string != null) {
        width = (int) U.measureText(string, paint);
      } else if (iconRes != 0) {
        width = Screen.dp(24f) + Screen.dp(6f);
      } else {
        width = 0;
      }
      this.width = width;
    }

    private String ellipsizedString;
    private int actualWidth;

    public void trimString (int availWidth, TextPaint paint) {
      if (string != null) {
        ellipsizedString = TextUtils.ellipsize(string, paint, availWidth, TextUtils.TruncateAt.END).toString();
        actualWidth = (int) U.measureText(ellipsizedString, paint);
      } else {
        ellipsizedString = null;
        actualWidth = width;
      }
    }

    public void untrimString () {
      ellipsizedString = string;
      actualWidth = width;
    }
  }
  private List<Item> items;
  private int maxItemWidth;

  private int textPadding;

  private @ThemeColorId int fromTextColorId, toTextColorId = R.id.theme_color_headerText;
  private @ThemeColorId int selectionColorId;

  public ViewPagerTopView (Context context) {
    super(context);
    this.textPadding = Screen.dp(19f);
    setWillNotDraw(false);
  }

  @Override
  public void checkRtl () {
    int count = getChildCount();
    for (int i = 0; i < count; i++) {
      View view = getChildAt(i);
      if (view != null) {
        view.requestLayout();
      }
    }
  }

  public void setTextPadding (int textPadding) {
    this.textPadding = textPadding;
  }

  private boolean fitsParentWidth;

  public void setFitsParentWidth (boolean fits) {
    this.fitsParentWidth = fits;
  }

  private OnItemClickListener listener;

  public void setOnItemClickListener (OnItemClickListener listener) {
    this.listener = listener;
  }

  @Override
  public void onClick (View v) {
    if (listener != null && v instanceof BackgroundView) {
      int i = ((BackgroundView) v).index;
      listener.onPagerItemClick(i);
    }
  }

  private boolean isDark;

  public void setUseDarkBackground () {
    isDark = true;
  }

  private BackgroundView newBackgroundView (int i) {
    BackgroundView backgroundView = new BackgroundView(getContext());
    if (isDark) {
      RippleSupport.setTransparentBlackSelector(backgroundView);
    } else {
      RippleSupport.setTransparentWhiteSelector(backgroundView);
    }
    backgroundView.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    backgroundView.setOnClickListener(this);
    backgroundView.setOnLongClickListener(this);
    backgroundView.setBoundView(this);
    backgroundView.setIndex(i);
    return backgroundView;
  }

  @Override
  public boolean onLongClick (View v) {
    return false;
  }

  private int totalWidth;

  public void setItems (String[] stringItems) {
    List<Item> items = new ArrayList<>(stringItems.length);
    for (String stringItem : stringItems) {
      items.add(new Item(stringItem));
    }
    setItems(items);
  }

  public void setItems (int[] iconItems) {
    List<Item> items = new ArrayList<>(iconItems.length);
    for (int iconItem : iconItems) {
      items.add(new Item(iconItem));
    }
    setItems(items);
  }

  public void setItemAt (int index, String text) {
    Item oldItem = this.items.get(index);
    Item item = new Item(text);
    this.items.set(index, item);
    totalWidth -= oldItem.width + textPadding * 2;

    int textColor = Theme.headerTextColor();
    TextPaint paint = Paints.getViewPagerTextPaint(textColor, item.needFakeBold);
    item.calculateWidth(paint);
    totalWidth += item.width + textPadding * 2;
    maxItemWidth = totalWidth / items.size();

    this.lastMeasuredWidth = 0;
    requestLayout();
    invalidate();
  }

  public void setItems (@NonNull List<Item> items) {
    if (this.items != null && this.items.size() == items.size()) {
      boolean foundDiff = false;
      int i = 0;
      for (Item item : items) {
        if (!item.equals(this.items.get(i++))) {
          foundDiff = true;
          break;
        }
      }
      if (!foundDiff) {
        return;
      }
    }
    removeAllViews();
    this.items = items;
    this.totalWidth = 0;
    this.lastMeasuredWidth = 0;
    int i = 0;
    int textColor = Theme.headerTextColor();
    for (Item item : items) {
      TextPaint paint = Paints.getViewPagerTextPaint(textColor, item.needFakeBold);
      item.calculateWidth(paint);
      totalWidth += item.width + textPadding * 2;
      addView(newBackgroundView(i));
      i++;
    }
    maxItemWidth = items.isEmpty() ? 0 : totalWidth / items.size();
  }

  public void addItem (String item) {
    addItemAtIndex(new Item(item), -1);
  }

  public void addItemAtIndex (String item, int index) {
    addItemAtIndex(new Item(item),  index);
  }

  public void addItem (int item) {
    addItemAtIndex(new Item(item), -1);
  }

  public void addItemAtIndex (int item, int index) {
    addItemAtIndex(new Item(item), index);
  }

  public void addItemAtIndex (Item item, int index) {
    if (index == -1) {
      index = items.size();
    }
    boolean append = index == items.size();
    if (append) {
      items.add(item);
    } else {
      items.add(index, item);
    }

    int textColor = Theme.headerTextColor();
    TextPaint paint = Paints.getViewPagerTextPaint(textColor, item.needFakeBold);

    item.calculateWidth(paint);
    int width = item.width;
    totalWidth += width + textPadding * 2;
    maxItemWidth = totalWidth / items.size();

    commonItemWidth = calculateCommonItemWidth(width);

    if (index <= (int) selectionFactor) {
      selectionFactor++;
    }

    final int availTextWidth = commonItemWidth - textPadding * 2;
    if (!shouldWrapContent() && width < availTextWidth) {
      item.trimString(availTextWidth, paint);
    } else {
      item.untrimString();
    }
    addView(newBackgroundView(items.size() - 1));
    invalidate();
  }

  public void removeLastItem () {
    if (!items.isEmpty()) {
      removeItemAt(items.size() - 1);
    }
  }

  public void removeItemAt (int index) {
    if (index < 0 || index >= items.size()) {
      throw new IllegalArgumentException(index + " is out of range 0.." + items.size());
    }

    items.remove(index);

    if ((int) selectionFactor >= items.size()) {
      selectionFactor--;
    }

    removeViewAt(index);
    invalidate();
  }

  private boolean shouldWrapContent () {
    return getLayoutParams().width == ViewGroup.LayoutParams.WRAP_CONTENT;
  }

  private int getTotalWidth () {
    int sum = 0;
    for (Item item : items) {
      sum += item.width;
    }
    return sum;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    if (shouldWrapContent()) {
      int totalWidth = textPadding * 2 * items.size() + getTotalWidth();
      super.onMeasure(MeasureSpec.makeMeasureSpec(totalWidth, MeasureSpec.EXACTLY), heightMeasureSpec);
      layout(totalWidth, true);
    } else {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
      layout(getMeasuredWidth(), false);
    }
  }

  private int lastMeasuredWidth;
  private int commonItemWidth;

  private int calculateCommonItemWidth (int parentWidth) {
    int itemWidth = Math.min(parentWidth / items.size(), maxItemWidth);
    if (parentWidth - itemWidth * items.size() < itemWidth / 2) {
      itemWidth = parentWidth / items.size();
    } else if (fitsParentWidth) {
      itemWidth = Math.max(itemWidth, Math.min((int) ((float) itemWidth * 2f), parentWidth / items.size()));
    }
    return itemWidth;
  }

  private void layout (int width, boolean wrapContent) {
    if (width == 0 || lastMeasuredWidth == width || items == null) {
      return;
    }

    boolean relayout = lastMeasuredWidth != 0;

    lastMeasuredWidth = width;
    commonItemWidth = calculateCommonItemWidth(width);
    int textColor = Theme.headerTextColor();

    final int availTextWidth = commonItemWidth - textPadding * 2;

    for (Item item : items) {
      if (!wrapContent && item.width < availTextWidth) {
        item.trimString(availTextWidth, Paints.getViewPagerTextPaint(textColor, item.needFakeBold));
      } else {
        item.untrimString();
      }
    }

    if (relayout) {
      postDelayed(() -> {
        recalculateSelection(selectionFactor, true);
        invalidate();
      }, 10);
    } else {
      recalculateSelection(selectionFactor, true);
    }
  }

  private float selectionFactor;

  private void recalculateSelection (float selectionFactor, boolean set) {
    if (items == null || items.isEmpty()) {
      return;
    }
    int selectionWidth, selectionLeft;
    if (shouldWrapContent()) {
      float remainFactor = selectionFactor - (float) ((int) selectionFactor);
      if (remainFactor == 0f) {
        selectionWidth = items.get((int) selectionFactor).actualWidth + textPadding * 2;
      } else {
        int fromWidth = items.get((int) selectionFactor).actualWidth + textPadding * 2;
        int toWidth = items.get((int) selectionFactor + 1).actualWidth + textPadding * 2;
        selectionWidth = fromWidth + (int) ((float) (toWidth - fromWidth) * remainFactor);
      }
      selectionLeft = 0;
      for (int i = 0; i < (int) selectionFactor; i++) {
        selectionLeft += items.get(i).actualWidth + textPadding * 2;
      }
      if (remainFactor != 0f) {
        selectionLeft += (int) ((float) (items.get((int) selectionFactor).actualWidth + textPadding * 2) * remainFactor);
      }
    } else {
      selectionLeft = (int) (selectionFactor * (float) commonItemWidth);
      selectionWidth = commonItemWidth;
    }

    boolean callListener;
    if (set) {
      if (this.selectionLeft != selectionLeft || this.selectionWidth != selectionWidth) {
        this.selectionLeft = selectionLeft;
        this.selectionWidth = selectionWidth;
      }
      callListener = (fromIndex == -1 && toIndex == -1) || (fromIndex != -1 && toIndex != -1 && Math.abs(toIndex - fromIndex) == 1);
    } else {
      callListener = fromIndex != -1 && toIndex != -1 && Math.abs(toIndex - fromIndex) > 1;
    }
    float totalFactor = items.size() > 1 ? selectionFactor / (float) (items.size() - 1) : 0;
    if (callListener && selectionChangeListener != null && (lastCallSelectionLeft != selectionLeft || lastCallSelectionWidth != selectionWidth || lastCallSelectionFactor != totalFactor)) {
      selectionChangeListener.onSelectionChanged(lastCallSelectionLeft = selectionLeft, lastCallSelectionWidth = selectionWidth, items.get(0).actualWidth, items.get(items.size() - 1).actualWidth, lastCallSelectionFactor = totalFactor, !set);
    }
  }

  /*public void resendSectionChangeEvent (boolean animated) {
    if (items != null && !items.isEmpty()) {
      selectionChangeListener.onSelectionChanged(lastCallSelectionLeft, lastCallSelectionWidth, items.get(0).actualWidth, items.get(items.size() - 1).actualWidth, lastCallSelectionFactor, animated);
    }
  }*/

  public interface SelectionChangeListener {
    void onSelectionChanged (int selectionLeft, int selectionRight, int firstItemWidth, int lastItemWidth, float totalFactor, boolean animated);
  }

  private SelectionChangeListener selectionChangeListener;

  public ViewPagerTopView setSelectionChangeListener (SelectionChangeListener selectionChangeListener) {
    this.selectionChangeListener = selectionChangeListener;
    return this;
  }

  public void setSelectionFactor (float factor) {
    if (this.selectionFactor != factor) {
      this.selectionFactor = factor;
      if (toIndex != -1 && (int) factor == toIndex && factor % 1f == 0) {
        fromIndex = toIndex = -1;
      }

      recalculateSelection(selectionFactor, true);
      invalidate();
    }
  }

  private int fromIndex = -1;
  private int toIndex = -1;

  public void setFromTo (int fromIndex, int toIndex) {
    if (fromIndex != toIndex || fromIndex == -1) {
      this.fromIndex = fromIndex;
      this.toIndex = toIndex;
      if (toIndex != -1) {
        recalculateSelection(toIndex, false);
      }
    }
  }

  public boolean setTextFromToColorId (@ThemeColorId int fromColorId, @ThemeColorId int toColorId) {
    if (this.fromTextColorId != fromColorId || this.toTextColorId != toColorId) {
      this.fromTextColorId = fromColorId;
      this.toTextColorId = toColorId;
      invalidate();
      return true;
    }
    return false;
  }

  public boolean setSelectionColorId (@ThemeColorId int colorId) {
    if (this.selectionColorId != colorId) {
      this.selectionColorId = colorId;
      invalidate();
      return true;
    }
    return false;
  }

  private boolean disabled;

  public void setTouchDisabled (boolean disabled) {
    if (this.disabled != disabled) {
      this.disabled = disabled;
      final int childCount = getChildCount();
      for (int i = 0; i < childCount; i++) {
        View view = getChildAt(i);
        if (view != null && view instanceof BackgroundView) {
          view.setEnabled(!disabled);
        }
      }
    }
  }

  private float disabledFactor;

  public void setDisabledFactor (float factor) {
    if (this.disabledFactor != factor) {
      this.disabledFactor = factor;
      invalidate();
    }
  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent ev) {
    return disabledFactor != 0f;
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    return disabled || disabledFactor != 0f || super.onTouchEvent(event);
  }

  private float overlayFactor;

  public void setOverlayFactor (float factor) {
    if (this.overlayFactor != factor) {
      this.overlayFactor = factor;
      invalidate();
    }
  }

  private int selectionLeft, selectionWidth;
  private int lastCallSelectionLeft, lastCallSelectionWidth;
  private float lastCallSelectionFactor;

  @Override
  public void draw (Canvas c) {
    super.draw(c);

    if (items == null) {
      return;
    }

    final int viewHeight = getMeasuredHeight();
    final boolean wrapContent = shouldWrapContent();

    if (overlayFactor != 1f) {
      int textToColor = Theme.getColor(toTextColorId);
      int textFromColor = fromTextColorId != 0 ? Theme.getColor(fromTextColorId) : ColorUtils.alphaColor(Theme.getSubtitleAlpha(), Theme.getColor(R.id.theme_color_headerText));
      int selectionColor = selectionColorId != 0 ? Theme.getColor(selectionColorId) : ColorUtils.alphaColor(.9f, Theme.getColor(R.id.theme_color_headerText));

      boolean rtl = Lang.rtl();

      int selectionLeft = rtl ? this.totalWidth - this.selectionLeft - this.selectionWidth : this.selectionLeft;

      c.drawRect(selectionLeft, viewHeight - Screen.dp(2f), selectionLeft + selectionWidth, viewHeight, Paints.fillingPaint(disabledFactor == 0f ? selectionColor : ColorUtils.fromToArgb(selectionColor, textFromColor, disabledFactor)));

      int cx = rtl ? totalWidth : 0;
      int itemIndex = 0;
      int itemCount = items.size();
      for (int i = 0; i < itemCount; i++) {
        Item item = items.get(i);
        float factor;
        if (fromIndex != -1 && toIndex != -1) {
          int diff = Math.abs(toIndex - fromIndex);
          if (itemIndex == toIndex) {
            factor = Math.abs(selectionFactor - fromIndex) / (float) diff;
          } else if (itemIndex == fromIndex) {
            factor = 1f - Math.abs(selectionFactor - fromIndex) / (float) diff;
          } else {
            factor = 0f;
          }
        } else {
          float abs = Math.abs(selectionFactor - (float) itemIndex);
          if (abs <= 1f) {
            factor = 1f - abs;
          } else {
            factor = 0f;
          }
        }

        final int itemWidth;
        if (wrapContent) {
          itemWidth = item.actualWidth + textPadding * 2;
        } else {
          itemWidth = commonItemWidth;
        }
        if (rtl)
          cx -= itemWidth;
        if (item != null) {
          int color = ColorUtils.fromToArgb(textFromColor, textToColor, factor * (1f - disabledFactor));
          if (item.ellipsizedString != null) {
            c.drawText(item.ellipsizedString, cx + itemWidth / 2 - item.actualWidth / 2, viewHeight / 2 + Screen.dp(6f), Paints.getViewPagerTextPaint(color, item.needFakeBold));
          } else if (item.iconRes != 0) {
            Drawable drawable = item.getIcon();
            Drawables.draw(c, drawable, cx + itemWidth / 2 - drawable.getMinimumWidth() / 2, viewHeight / 2 - drawable.getMinimumHeight() / 2, Paints.getPorterDuffPaint(color));
          }
        }
        if (!rtl)
          cx += itemWidth;
        itemIndex++;
      }
    }

    if (overlayFactor != 0f && overlayFactor != 1f) {
      final int viewWidth = getMeasuredWidth();

      c.save();
      c.translate(0, (float) viewHeight * (1f - overlayFactor));
      c.drawRect(0, 0, viewWidth, viewHeight, Paints.fillingPaint(Theme.fillingColor()));
      c.restore();

      /*float fromX = selectionFactor * itemWidth + itemWidth / 2;
      float fromY = viewHeight / 2;

      float x = fromX + (viewWidth / 2 - fromX) * overlayFactor;
      float y = fromY + (viewHeight / 2 - fromY) * overlayFactor;
      float radius = (float) Math.sqrt(viewWidth * viewWidth + viewHeight * viewHeight) * .5f * overlayFactor;

      c.save();
      c.clipRect(0, 0, viewWidth, viewHeight);
      c.drawRect(0, 0, viewWidth, viewHeight, Paints.fillingPaint(Utils.alphaColor(overlayFactor, TGTheme.fillingColor())));
      c.drawCircle(x, y, radius, Paints.fillingPaint(TGTheme.fillingColor())); // Utils.alphaColor(overlayFactor, TGTheme.fillingColor())
      c.restore();*/
    }
  }

  public interface OnItemClickListener {
    void onPagerItemClick (int index);
  }

  public interface OnSlideOffListener {
    boolean onSlideOffPrepare (View view, MotionEvent event, int index);
    void onSlideOffStart (View view, MotionEvent event, int index);
    void onSlideOffMovement (View view, MotionEvent event, int index);
    void onSlideOffFinish (View view, MotionEvent event, int index, boolean apply);
  }

  private OnSlideOffListener onSlideOffListener;

  public void setOnSlideOffListener (OnSlideOffListener onSlideOffListener) {
    this.onSlideOffListener = onSlideOffListener;
  }

  private static class BackgroundView extends View {
    public BackgroundView (Context context) {
      super(context);

      Views.setClickable(this);
    }

    private boolean inSlideOff, needSlideOff;
    private ViewParent lockedParent;

    @Override
    public boolean onTouchEvent (MotionEvent e) {
      OnSlideOffListener slideOffListener = topView != null ? topView.onSlideOffListener : null;
      if (slideOffListener == null) {
        return ((View) getParent()).getAlpha() >= 1f && super.onTouchEvent(e);
      }
      super.onTouchEvent(e);
      if (e.getAction() == MotionEvent.ACTION_DOWN) {
        if (lockedParent != null) {
          lockedParent.requestDisallowInterceptTouchEvent(false);
          lockedParent = null;
        }
        needSlideOff = slideOffListener.onSlideOffPrepare(this, e, index);
        if (needSlideOff) {
          lockedParent = getParent();
          if (lockedParent != null) {
            lockedParent.requestDisallowInterceptTouchEvent(true);
          }
        }
      }
      if (!needSlideOff) {
        return true;
      }
      switch (e.getAction()) {
        case MotionEvent.ACTION_MOVE: {
          int start = getMeasuredHeight();
          boolean inSlideOff = e.getY() >= start;
          if (this.inSlideOff != inSlideOff) {
            this.inSlideOff = inSlideOff;
            if (inSlideOff) {
              slideOffListener.onSlideOffStart(this, e, index);
            } else {
              slideOffListener.onSlideOffFinish(this, e, index, false);
            }
          }
          if (inSlideOff) {
            slideOffListener.onSlideOffMovement(this, e, index);
          }
          break;
        }
        case MotionEvent.ACTION_CANCEL:
          if (inSlideOff) {
            inSlideOff = false;
            slideOffListener.onSlideOffFinish(this, e, index, false);
          }
          if (lockedParent != null) {
            lockedParent.requestDisallowInterceptTouchEvent(false);
            lockedParent = null;
          }
          break;
        case MotionEvent.ACTION_UP:
          if (inSlideOff) {
            inSlideOff = false;
            slideOffListener.onSlideOffFinish(this, e, index, true);
          }
          if (lockedParent != null) {
            lockedParent.requestDisallowInterceptTouchEvent(false);
            lockedParent = null;
          }
          break;
      }
      return true;
    }

    private ViewPagerTopView topView;

    public void setBoundView (ViewPagerTopView topView) {
      this.topView = topView;
    }

    private int index;

    public void setIndex (int index) {
      this.index = index;
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      if (topView.shouldWrapContent()) {
        int left = 0;
        for (int i = 0; i < index; i++) {
          left += topView.items.get(i).width + topView.textPadding * 2;
        }
        int itemWidth = topView.items.get(index).width + topView.textPadding * 2;
        if (Lang.rtl()) {
          left = MeasureSpec.getSize(widthMeasureSpec) - left - itemWidth;
        }
        super.onMeasure(MeasureSpec.makeMeasureSpec(itemWidth, MeasureSpec.EXACTLY), heightMeasureSpec);
        setTranslationX(left);
      } else {
        int itemWidth = topView.calculateCommonItemWidth(MeasureSpec.getSize(widthMeasureSpec));
        super.onMeasure(MeasureSpec.makeMeasureSpec(itemWidth, MeasureSpec.EXACTLY), heightMeasureSpec);
        setTranslationX(itemWidth * index);
      }
    }
  }
}
