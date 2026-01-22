/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 26/12/2016
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.CheckResult;
import androidx.annotation.Dimension;
import androidx.annotation.DrawableRes;
import androidx.annotation.FloatRange;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.core.view.ViewCompat;

import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGReaction;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.PropertyId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.IconSpan;
import org.thunderdog.challegram.util.text.Text;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;

public class ViewPagerTopView extends FrameLayoutFix implements RtlCheckListener, View.OnClickListener, View.OnLongClickListener, Destroyable, TGLegacyManager.EmojiLoadListener, FactorAnimator.Target {
  public static final @Dimension(unit = Dimension.DP) float SELECTION_HEIGHT = 2f;
  public static final @Dimension(unit = Dimension.DP) float ICON_SIZE = 24f;
  public static final @Dimension(unit = Dimension.DP) float DEFAULT_ITEM_PADDING = 19f;
  public static final @Dimension(unit = Dimension.DP) float COMPACT_ITEM_PADDING = 10f;
  public static final @Dimension(unit = Dimension.DP) float DEFAULT_ITEM_SPACING = 6f;
  public static final @Dimension(unit = Dimension.DP) float COMPACT_ITEM_SPACING = 4f;

  private static final int ITEM_ANIMATOR = 1;
  private static final boolean APPLY_HORIZONTAL_MARGIN = false; // BuildConfig.DEBUG

  public static class Item {
    public final CharSequence string;
    public final boolean needFakeBold;
    public final @DrawableRes int iconRes;
    public ImageReceiver imageReceiver;
    public int imageReceiverSize = 0;
    public float imageReceiverScale = 0f;
    public TGReaction reaction;
    public final Counter counter;
    public final DrawableProvider provider;
    public final boolean hidden;

    public Item (CharSequence string) {
      this(string, 0, null, null, false);
    }

    public Item (@DrawableRes int iconRes) {
      this(null, iconRes, null, null, false);
    }

    public Item (@DrawableRes int iconRes, Counter counter) {
      this(null, iconRes, counter, null, false);
    }

    public Item (CharSequence string, Counter counter) {
      this(string, 0, counter, null, false);
    }

    public Item (CharSequence string, @DrawableRes int iconRes, Counter counter) {
      this(string, iconRes, counter, null, false);
    }

    public Item (Counter counter, DrawableProvider provider, int addWidth) {
      this(null, 0, counter, provider, false);
      this.addWidth = addWidth;
    }

    public Item (TGReaction reaction, Counter counter, DrawableProvider provider, int addWidth) {
      this(null, 0, counter, provider, false);
      this.addWidth = addWidth;
      this.reaction = reaction;
    }

    public Item () {
      this(null, 0, null, null, true);
    }

    private Item (CharSequence string, @DrawableRes int iconRes, Counter counter, DrawableProvider provider, boolean hidden) {
      this.string = string;
      this.needFakeBold = string != null && Text.needFakeBold(string);
      this.iconRes = iconRes;
      this.counter = counter;
      this.provider = provider;
      this.hidden = hidden;
    }

    private Drawable icon;

    public Drawable getIcon () {
      if (icon == null && iconRes != 0)
        icon = Drawables.get(iconRes);
      return icon;
    }

    @Override
    public boolean equals (Object obj) {
      return obj instanceof Item && ((Item) obj).iconRes == iconRes && StringUtils.equalsOrBothEmpty(((Item) obj).string, string) && (((Item) obj).counter == counter);
    }

    private int width, contentWidth;
    private int collapsedWidth, collapsedContentWidth;
    private int addWidth = 0;
    private int minWidth = 0;
    private int staticWidth = -1;
    private int translationX = 0;

    public void setMinWidth (@Px int minWidth) {
      this.minWidth = minWidth;
    }

    public void setStaticWidth (@Px int staticWidth) {
      this.staticWidth = staticWidth;
    }

    public int calculateWidth (TextPaint paint, @Px int horizontalSpacing) {
      int measuredWidth = measureWidth(paint, horizontalSpacing, /* labelFactor */ 1f);
      int measuredCollapsedWidth = measureWidth(paint, horizontalSpacing, /* labelFactor */ 0f);
      this.contentWidth = measuredWidth + addWidth;
      this.collapsedContentWidth = measuredCollapsedWidth + addWidth;
      this.width = Math.max(contentWidth, minWidth);
      this.collapsedWidth = Math.max(collapsedContentWidth, minWidth);
      return this.width;
    }

    public int getExpandedWidth () {
      return getWidth(/* labelFactor */ 1f);
    }

    public int getCollapsedWidth () {
      return getWidth(/* labelFactor */ 0f);
    }

    public int getWidth (float labelFactor) {
      final int expandedWidth = ellipsizedWidth != 0 ? ellipsizedWidth : width; // FIXME
      final int value;
      if (labelFactor == 1f) {
        value = expandedWidth;
      } else if (labelFactor == 0f) {
        value = collapsedWidth;
      } else {
        value = MathUtils.fromTo(collapsedWidth, expandedWidth, labelFactor);
      }
      if (oldItem != null && animator != null && animator.getFactor() < 1f) {
        return MathUtils.fromTo(oldItem.getWidth(labelFactor), value, animator.getFactor());
      }
      return value;
    }

    public int getContentWidth (float labelFactor) {
      final int value;
      if (labelFactor == 1f) {
        value = contentWidth;
      } else if (labelFactor == 0f) {
        value = collapsedContentWidth;
      } else {
        value = MathUtils.fromTo(collapsedContentWidth, contentWidth, labelFactor);
      }
      if (oldItem != null && animator != null && animator.getFactor() < 1f) {
        return MathUtils.fromTo(oldItem.getContentWidth(labelFactor), value, animator.getFactor());
      }
      return value;
    }

    @CheckResult
    private int measureWidth (TextPaint paint, @Px int horizontalSpacing, float labelFactor) {
      final int width;
      final float labelWidth = string != null ? U.measureEmojiText(string, paint) * labelFactor : 0f;
      final float counterWidthWithSpacing = counter != null ? counter.getScaledWidth(horizontalSpacing) : 0f;
      final float iconWidth = iconRes != 0 ? Screen.dp(ICON_SIZE) : 0;
      final float iconWidthWithLabelSpacing = iconRes != 0 ? (iconWidth + horizontalSpacing * labelFactor) : 0;
      if (staticWidth != -1) {
        width = staticWidth;
      } else if (counter != null) {
        if (string != null) {
          width = (int) (labelWidth + counterWidthWithSpacing + iconWidthWithLabelSpacing);
        } else if (imageReceiver != null) {
          width = (int) counter.getWidth() + imageReceiverSize;
        } else if (iconRes != 0) {
          width = (int) (iconWidth + counterWidthWithSpacing);
        } else {
          width = (int) counter.getWidth()/* + Screen.dp(6f) */; // ???
        }
      } else if (string != null) {
        width = (int) (labelWidth + iconWidthWithLabelSpacing);
      } else if (iconRes != 0) {
        width = (int) iconWidth/* + Screen.dp(6f)*/; // ???
      } else {
        width = 0;
      }
      return width;
    }

    public void setTranslationX (int translationX) {
      this.translationX = translationX;
    }

    private @Nullable IconSpan[] iconSpans;
    private @Nullable Layout ellipsizedStringLayout;
    private int ellipsizedWidth;

    public void trimString (int availWidth, TextPaint paint) {
      if (string != null) {
        CharSequence ellipsizedString = TextUtils.ellipsize(string, paint, availWidth, TextUtils.TruncateAt.END);
        ellipsizedStringLayout = U.createLayout(ellipsizedString, availWidth, paint);
        ellipsizedWidth = ellipsizedStringLayout.getWidth(); // FIXME counter, icon
      } else {
        ellipsizedStringLayout = null;
        ellipsizedWidth = width;
      }
      iconSpans = null;
    }

    public void untrimString (TextPaint textPaint) {
      if (string != null) {
        int textWidth = (int) Math.ceil(U.measureEmojiText(string, textPaint));
        if (ellipsizedStringLayout == null || !ellipsizedStringLayout.getText().equals(string) ||
          ellipsizedStringLayout.getPaint() != textPaint ||
          ellipsizedStringLayout.getWidth() != textWidth) {
          ellipsizedStringLayout = U.createLayout(string, textWidth, textPaint);
          if (string instanceof Spanned) {
            iconSpans = ((Spanned) string).getSpans(0, string.length(), IconSpan.class);
          } else {
            iconSpans = null;
          }
        }
      } else {
        ellipsizedStringLayout = null;
        iconSpans = null;
      }
      ellipsizedWidth = width;
    }

    boolean hasIconSpans () {
      return iconSpans != null && iconSpans.length > 0;
    }

    @Nullable Item oldItem;
    @Nullable FactorAnimator animator;

    boolean isAnimating () {
      return animator != null && animator.isAnimating();
    }

    float getAnimationFactor () {
      return animator != null ? animator.getFactor() : 1f;
    }

    public void animateFrom (Item item, FactorAnimator.Target target) {
      if (item == null) {
        return;
      }
      clearAnimation();
      oldItem = item;
      animator = new FactorAnimator(ITEM_ANIMATOR, target, AnimatorUtils.ACCELERATE_DECELERATE_INTERPOLATOR, 120L);
      animator.setObjValue(this);
      animator.animateTo(1f);
    }

    public boolean clearAnimation () {
      oldItem = null;
      if (animator != null) {
        boolean cancelled = animator.cancel();
        animator = null;
        return cancelled;
      }
      return false;
    }
  }

  private List<Item> items;
  private int maxItemWidth;

  private @Px int itemPadding;
  private @Px int itemSpacing;
  private final ComplexReceiver complexReceiver;
  private CounterAlphaProvider counterAlphaProvider = DEFAULT_COUNTER_ALPHA_PROVIDER;

  private @ColorId int fromTextColorId = ColorId.NONE, toTextColorId = ColorId.headerText;
  private @PropertyId int fromTextColorAlphaId = PropertyId.NONE;

  private @ColorId int selectionColorId = ColorId.NONE;
  private @FloatRange(from = 0.0, to = 1.0) float selectionAlpha = 1f;

  public ViewPagerTopView (Context context) {
    super(context);
    this.itemPadding = Screen.dp(DEFAULT_ITEM_PADDING);
    this.itemSpacing = Screen.dp(DEFAULT_ITEM_SPACING);
    this.complexReceiver = new ComplexReceiver(this);
    setWillNotDraw(false);
    TGLegacyManager.instance().addEmojiListener(this);
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

  public void setItemPadding (@Px int itemPadding) {
    if (this.itemPadding != itemPadding) {
      this.itemPadding = itemPadding;
      if (items != null && !items.isEmpty()) {
        relayout();
      }
    }
  }

  public void setItemSpacing (@Px int itemSpacing) {
    if (this.itemSpacing != itemSpacing) {
      this.itemSpacing = itemSpacing;
      if (items != null && !items.isEmpty()) {
        measureItems();
        relayout();
      }
    }
  }

  private boolean fitsParentWidth;

  public void setFitsParentWidth (boolean fits) {
    this.fitsParentWidth = fits;
  }

  private boolean drawSelectionAtTop;

  public void setDrawSelectionAtTop (boolean drawSelectionAtTop) {
    this.drawSelectionAtTop = drawSelectionAtTop;
    if (getItemCount() > 0) {
      invalidate();
    }
  }

  public boolean isDrawSelectionAtTop () {
    return drawSelectionAtTop;
  }

  private boolean showLabelOnActiveOnly;

  public void setShowLabelOnActiveOnly (boolean showLabelOnActiveOnly) {
    if (this.showLabelOnActiveOnly != showLabelOnActiveOnly) {
      this.showLabelOnActiveOnly = showLabelOnActiveOnly;
      if (getItemCount() > 0) {
        relayout();
      }
    }
  }

  private boolean animateItemChanges;

  public void setAnimateItemChanges (boolean animateItemChanges) {
    if (this.animateItemChanges != animateItemChanges) {
      this.animateItemChanges = animateItemChanges;
      if (!animateItemChanges) {
        clearItemAnimations();
      }
    }
  }

  private void clearItemAnimations () {
    if (getItemCount() == 0) return;
    boolean shouldRelayout = false;
    for (Item item : items) {
      shouldRelayout = item.clearAnimation() || shouldRelayout;
    }
    if (shouldRelayout) {
      relayout();
    }
  }

  private OnItemClickListener listener;

  public void setOnItemClickListener (OnItemClickListener listener) {
    this.listener = listener;
  }

  public OnItemClickListener getOnItemClickListener () {
    return listener;
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
    setUseDarkBackground(true);
  }

  public void setUseDarkBackground (boolean useDark) {
    if (isDark != useDark) {
      isDark = useDark;
      int childCount = getChildCount();
      for (int index = 0; index < childCount; index++) {
        View childView = getChildAt(index);
        if (childView instanceof BackgroundView) {
          if (useDark) {
            RippleSupport.setTransparentBlackSelector(childView);
          } else {
            RippleSupport.setTransparentWhiteSelector(childView);
          }
        }
      }
    }
  }

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({SLIDE_OFF_DIRECTION_TOP, SLIDE_OFF_DIRECTION_BOTTOM})
  public @interface SlideOffDirection { }

  public static final int SLIDE_OFF_DIRECTION_TOP = -1;
  public static final int SLIDE_OFF_DIRECTION_BOTTOM = 1;

  private @SlideOffDirection int slideOffDirection = SLIDE_OFF_DIRECTION_BOTTOM;

  public void setSlideOffDirection (@SlideOffDirection int slideOffDirection) {
    this.slideOffDirection = slideOffDirection;
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
    if (listener != null && v instanceof BackgroundView) {
      BackgroundView backgroundView = (BackgroundView) v;
      if (backgroundView.inSlideOff()) {
        return false;
      }
      backgroundView.cancelSlideOff();
      return listener.onPagerItemLongClick(backgroundView.index);
    }
    return false;
  }

  private int totalWidth;
  private int maxStableWidth;

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

  public void setItemAt (int index, String text, boolean animated) {
    setItemAt(index, new Item(text), animated);
  }

  public void setItemAt (int index, Item item, boolean animated) {
    Item oldItem = this.items.set(index, item);
    onUpdateItems();

    measureItem(item, getItemTextPaint(item));
    relayout();
    invalidate();
    if (shouldWrapContent()) {
      View child = getChildAt(index);
      if (child instanceof BackgroundView && ViewCompat.isLaidOut(child)) {
        if (BuildConfig.DEBUG && ((BackgroundView) child).index != index) {
          throw new IllegalStateException("BackgroundView.index = " + index + ", index = " + index);
        }
        int itemWidth = item.getExpandedWidth() + itemPadding * 2;
        if (itemWidth != child.getWidth()) {
          child.requestLayout();
        }
      }
      if (animated && canAnimateItemChange(oldItem, item, index)) {
        item.animateFrom(oldItem, this);
      }
    }
  }

  private boolean canAnimateItemChange (@Nullable Item oldItem, Item newItem, int index) {
    return animateItemChanges && oldItem != null && oldItem != newItem && (!showLabelOnActiveOnly || index == selectionFactor);
  }

  public void setItemTranslationX (int index, int x) {
    if (index < getItemCount()) {
      this.items.get(index).setTranslationX(x);
      invalidate();
    }
  }

  public int getItemCount () {
    return items != null ? items.size() : 0;
  }

  public void setItems (@NonNull List<Item> items) {
    if (getItemCount() == items.size()) {
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
    onUpdateItems();

    measureItems();
    for (int i = 0; i < items.size(); i++) {
      addView(newBackgroundView(i));
    }
    relayout();
  }

  public void addItem (String item) {
    addItemAtIndex(new Item(item), -1);
  }

  public void addItemAtIndex (String item, int index) {
    addItemAtIndex(new Item(item), index);
  }

  public void addItem (int item) {
    addItem(new Item(item));
  }

  public void addItem (Item item) {
    addItemAtIndex(item, -1);
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

    onUpdateItems();

    if (index <= (int) selectionFactor) {
      selectionFactor++;
    }

    TextPaint paint = getItemTextPaint(item);
    measureItem(item, paint);
    int itemWidth = item.width;
    totalWidth += itemWidth + itemPadding * 2;
    maxItemWidth = totalWidth / items.size();

    commonItemWidth = calculateCommonItemWidth(itemWidth);

    final int availTextWidth = commonItemWidth - itemPadding * 2;
    if (!shouldWrapContent() && itemWidth < availTextWidth) {
      item.trimString(availTextWidth, paint);
    } else {
      item.untrimString(paint);
    }
    // We already have backgroundView with insertion index, adding for the last one
    // (for which there is no backgroundView yet)
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
      throw new IllegalArgumentException(index + " is out of range 0.." + (items.size() - 1));
    }

    items.remove(index);

    onUpdateItems();

    if ((int) selectionFactor >= items.size()) {
      selectionFactor--;
    }

    // Removing BackgroundView for the last item only, as other just update dimensions
    removeViewAt(getChildCount() - 1);
    invalidate();
  }

  private void updateFollowingBackgroundViews (int fromIndex, int delta) {
    for (int i = 0; i < getChildCount(); i++) {
      View view = getChildAt(i);
      if (view instanceof BackgroundView) {
        BackgroundView backgroundView = (BackgroundView) view;
        if (backgroundView.index >= fromIndex) {
          backgroundView.index += delta;
        }
      }
    }
  }

  private TextPaint getItemTextPaint (Item item) {
    return Paints.getViewPagerTextPaint(Theme.headerTextColor(), item.needFakeBold);
  }

  private void relayout () {
    this.totalWidth = calculateTotalWidth();
    this.maxStableWidth = calculateMaxStableWidth(totalWidth);
    this.maxItemWidth = items == null || items.isEmpty() ? 0 : (totalWidth / items.size());
    this.lastMeasuredWidth = 0; // force layout
    requestLayout();
  }

  private void measureItems () {
    if (items == null || items.isEmpty()) {
      return;
    }
    for (Item item : items) {
      measureItem(item, getItemTextPaint(item));
    }
  }

  private void measureItem (Item item, TextPaint paint) {
    item.calculateWidth(paint, itemSpacing);
  }

  public boolean shouldWrapContent () {
    return getLayoutParams().width == ViewGroup.LayoutParams.WRAP_CONTENT;
  }

  @CheckResult
  private int calculateTotalWidth () {
    if (items == null || items.isEmpty()) {
      return 0;
    }
    int sum = 0;
    for (Item item : items) {
      sum += item.width + itemPadding * 2;
    }
    return sum;
  }

  @CheckResult
  private int calculateMaxStableWidth (int totalWidth) {
    if (items == null || items.isEmpty() || !showLabelOnActiveOnly) {
      return totalWidth;
    }
    int sum = 0;
    for (Item item : items) {
      sum += item.getCollapsedWidth() + itemPadding * 2;
    }
    int maxStableWidth = 0;
    for (Item item : items) {
      int itemWidthDiff = item.getExpandedWidth() - item.getCollapsedWidth();
      maxStableWidth = Math.max(maxStableWidth, sum + itemWidthDiff);
    }
    return maxStableWidth;
  }

  private void onUpdateItems () {
    for (Item item : items) {
      if (item.reaction != null) {
        TGReaction reaction = item.reaction;
        TGStickerObj stickerObj = reaction.centerAnimationSicker();
        item.imageReceiver = complexReceiver.getImageReceiver(reaction.getId());
        item.imageReceiver.requestFile(stickerObj.getImage());
        item.imageReceiverScale = stickerObj.getDisplayScale();
        item.imageReceiverSize = Screen.dp(34);
      }
    }
  }

  public void requestItemLayoutAt (int index) {
    if (index >= 0 && index < items.size()) {
      setItemAt(index, items.get(index), false);
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    if (shouldWrapContent()) {
      int totalWidth = calculateTotalWidth();
      if (totalWidth != this.totalWidth) {
        if (BuildConfig.DEBUG) {
          throw new IllegalStateException("this.totalWidth = " + this.totalWidth + ", totalWidth = " + totalWidth);
        }
        maxStableWidth = calculateMaxStableWidth(totalWidth);
      }
      layout(totalWidth, /* wrapContent */ true); // must be before super.onMeasure
      super.onMeasure(MeasureSpec.makeMeasureSpec(totalWidth, MeasureSpec.EXACTLY), heightMeasureSpec);
    } else {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
      layout(getMeasuredWidth(),  /* wrapContent */ false); // must be after super.onMeasure
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

    final int availTextWidth = commonItemWidth - itemPadding * 2;

    for (Item item : items) {
      TextPaint textPaint = getItemTextPaint(item);
      if (!wrapContent && item.width < availTextWidth) {
        item.trimString(availTextWidth, textPaint);
      } else {
        item.untrimString(textPaint);
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
      int selectionIndex = MathUtils.clamp((int) selectionFactor, 0, items.size() - 1);
      int itemWidthWithPadding = getItemWidth(selectionIndex, selectionFactor);
      if (remainFactor == 0f) {
        selectionWidth = itemWidthWithPadding;
      } else {
        //noinspection UnnecessaryLocalVariable
        int fromWidth = itemWidthWithPadding;
        int nextIndex = MathUtils.clamp((int) selectionFactor + 1, 0, items.size() - 1);
        int toWidth = getItemWidth(nextIndex, selectionFactor);
        selectionWidth = MathUtils.fromTo(fromWidth, toWidth, remainFactor);
      }
      selectionLeft = 0;
      for (int i = 0; i < selectionIndex; i++) {
        selectionLeft += getItemWidth(i, selectionFactor);
      }
      if (remainFactor != 0f) {
        selectionLeft += Math.round(itemWidthWithPadding * remainFactor);
      }

      int childCount = getChildCount();
      for (int childIndex = 0; childIndex < childCount; childIndex++) {
        View child = getChildAt(childIndex);
        if (child instanceof BackgroundView && ViewCompat.isLaidOut(child)) {
          BackgroundView backgroundView = (BackgroundView) child;
          int itemIndex = backgroundView.index;
          int itemX = calculateItemX(itemIndex, child.getWidth(), getWidth());
          if (APPLY_HORIZONTAL_MARGIN) {
            Views.setLeftMargin(child, itemX);
          } else {
            child.setTranslationX(itemX);
          }
          int itemWidth = getItemWidth(itemIndex, selectionFactor);
          if (itemWidth != backgroundView.lastItemWidth) {
            backgroundView.invalidate();
          }
          int itemExpandedWidth = getItemExpandedWidth(itemIndex);
          if (itemExpandedWidth != backgroundView.getWidth()) {
            backgroundView.requestLayout();
          }
        }
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
      callListener = (fromIndex == -1 && toIndex == -1) || (fromIndex != -1 && toIndex != -1 && Math.abs(toIndex - fromIndex) == 1 && !selectionChangeListener.hasPendingUserInteraction());
    } else {
      callListener = fromIndex != -1 && toIndex != -1 && (Math.abs(toIndex - fromIndex) > 1 || selectionChangeListener.hasPendingUserInteraction());
    }
    float totalFactor = items.size() > 1 ? selectionFactor / (float) (items.size() - 1) : 0;
    if (callListener && selectionChangeListener != null && (lastCallSelectionLeft != selectionLeft || lastCallSelectionWidth != selectionWidth || lastCallSelectionFactor != totalFactor || ((showLabelOnActiveOnly || animateItemChanges) && set))) {
      int firstItemWidth = getItemExpandedWidth(0);
      int lastItemWidth = items.size() > 1 ? getItemExpandedWidth(items.size() - 1) : firstItemWidth;
      selectionChangeListener.onSelectionChanged(lastCallSelectionLeft = selectionLeft, lastCallSelectionWidth = selectionWidth, firstItemWidth, lastItemWidth, lastCallSelectionFactor = totalFactor, !set);
    }
  }

  private int getItemWidth (int itemIndex, float selectionFactor) {
    float labelFactor = getLabelFactor(itemIndex, selectionFactor);
    return itemPadding * 2 + items.get(itemIndex).getWidth(labelFactor);
  }

  private int getItemExpandedWidth (int itemIndex) {
    return itemPadding * 2 + items.get(itemIndex).getExpandedWidth();
  }

  public void updateAnchorPosition (boolean animated) {
    if (selectionChangeListener != null && getItemCount() > 0) {
      int firstItemWidth = getItemExpandedWidth(0);
      int lastItemWidth = items.size() > 1 ? getItemExpandedWidth(items.size() - 1) : firstItemWidth;
      selectionChangeListener.onSelectionChanged(lastCallSelectionLeft, lastCallSelectionWidth, firstItemWidth, lastItemWidth, lastCallSelectionFactor, animated);
    }
  }

  public interface SelectionChangeListener {
    void onSelectionChanged (int selectionLeft, int selectionRight, int firstItemWidth, int lastItemWidth, float totalFactor, boolean animated);
    default boolean hasPendingUserInteraction () { return false; }
    default void resetUserInteraction () { }
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
        fromIndex = toIndex = -1; selectionChangeListener.resetUserInteraction();
      }

      recalculateSelection(selectionFactor, true);
      invalidate();
    }
  }

  public float getSelectionFactor () {
    return selectionFactor;
  }

  private int fromIndex = -1;
  private int toIndex = -1;

  public void setFromTo (int fromIndex, int toIndex) {
    if (fromIndex != toIndex || fromIndex == -1) {
      this.fromIndex = fromIndex;
      this.toIndex = toIndex;
      if (toIndex != -1) {
        clearItemAnimations();
        recalculateSelection(toIndex, false);
      }
    }
  }

  public void resetFromTo () {
    setFromTo(-1, -1);
  }

  public boolean setTextFromToColorId (@ColorId int fromColorId, @ColorId int toColorId) {
    return setTextFromToColorId(fromColorId, toColorId, PropertyId.NONE);
  }

  public boolean setTextFromToColorId (@ColorId int fromColorId, @ColorId int toColorId, @PropertyId int fromColorAlphaId) {
    if (this.fromTextColorId != fromColorId || this.toTextColorId != toColorId || this.fromTextColorAlphaId != fromColorAlphaId) {
      this.fromTextColorId = fromColorId;
      this.fromTextColorAlphaId = fromColorAlphaId;
      this.toTextColorId = toColorId;
      invalidate();
      return true;
    }
    return false;
  }

  public boolean setSelectionColorId (@ColorId int colorId) {
    return setSelectionColorId(colorId, 1f);
  }

  public boolean setSelectionColorId (@ColorId int colorId, @FloatRange(from = 0.0, to = 1.0) float alpha) {
    if (this.selectionColorId != colorId || this.selectionAlpha != alpha) {
      this.selectionColorId = colorId;
      this.selectionAlpha = alpha;
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
        if (view instanceof BackgroundView) {
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
  @SuppressWarnings("deprecation")
  public void draw (@NonNull Canvas c) {
    super.draw(c);

    if (items == null) {
      return;
    }

    final int viewHeight = getMeasuredHeight();
    final boolean wrapContent = shouldWrapContent();

    if (overlayFactor != 1f) {
      int textToColor = Theme.getColor(toTextColorId);
      int textFromColor = fromTextColorId != ColorId.NONE ?
        ColorUtils.alphaColor(fromTextColorAlphaId != PropertyId.NONE ? Theme.getProperty(fromTextColorAlphaId) : 1f, Theme.getColor(fromTextColorId)) :
        ColorUtils.alphaColor(Theme.getSubtitleAlpha(), Theme.getColor(ColorId.headerText));
      int selectionColor = selectionColorId != ColorId.NONE ?
        ColorUtils.alphaColor(selectionAlpha, Theme.getColor(selectionColorId)) :
        ColorUtils.alphaColor(.9f, Theme.getColor(ColorId.headerText));

      boolean rtl = Lang.rtl();

      int selectionHeight = Screen.dp(SELECTION_HEIGHT);
      int selectionLeft = rtl ? this.totalWidth - this.selectionLeft - this.selectionWidth : this.selectionLeft;
      int selectionRight = selectionLeft + this.selectionWidth;
      int selectionTop = this.drawSelectionAtTop ? 0 : viewHeight - selectionHeight;
      int selectionBottom = selectionTop + selectionHeight;

      c.drawRect(selectionLeft, selectionTop, selectionRight, selectionBottom, Paints.fillingPaint(disabledFactor == 0f ? selectionColor : ColorUtils.fromToArgb(selectionColor, textFromColor, disabledFactor)));

      int cx = rtl ? totalWidth : 0;
      int itemIndex = 0;
      int itemCount = items.size();
      for (int i = 0; i < itemCount; i++) {
        Item item = items.get(i);
        boolean hasTranslate = item.translationX != 0;
        if (hasTranslate) {
          c.save();
          c.translate(item.translationX, 0f);
        }

        float itemSelectionFactor = getItemSelectionFactor(itemIndex, selectionFactor);
        float labelFactor = getLabelFactorByItemSelectionFactor(itemSelectionFactor);

        final int itemWidth;
        if (wrapContent) {
          itemWidth = item.getWidth(labelFactor) + itemPadding * 2;
        } else {
          itemWidth = commonItemWidth;
        }
        if (rtl)
          cx -= itemWidth;
        if (!item.hidden) {
          int contentWidth = Math.min(item.getContentWidth(labelFactor), item.getWidth(labelFactor));
          int horizontalPadding = Math.max(itemWidth - contentWidth, 0) / 2;
          int color = ColorUtils.fromToArgb(textFromColor, textToColor, itemSelectionFactor * (1f - disabledFactor));
          if (item.counter != null) {
            float alphaFactor = itemSelectionFactor;
            float imageAlpha = counterAlphaProvider.getDrawableAlpha(item.counter, alphaFactor);
            if (items.get(0).hidden) {
              alphaFactor = Math.max(alphaFactor, 1f - MathUtils.clamp(selectionFactor));
              if (i == 1 && selectionFactor < 1) {
                alphaFactor = 1f;
              }
            }
            float textAlpha = counterAlphaProvider.getTextAlpha(item.counter, alphaFactor);
            float backgroundAlpha = counterAlphaProvider.getBackgroundAlpha(item.counter, alphaFactor);
            if (item.ellipsizedStringLayout != null) {
              int stringX;
              if (item.iconRes != 0) {
                Drawable drawable = item.getIcon();
                Drawables.draw(c, drawable, cx + horizontalPadding, viewHeight / 2 - drawable.getMinimumHeight() / 2, Paints.getPorterDuffPaint(color));
                stringX = cx + horizontalPadding + Screen.dp(ICON_SIZE) + itemSpacing;
              } else {
                stringX = cx + horizontalPadding;
              }
              int stringY = viewHeight / 2 - item.ellipsizedStringLayout.getHeight() / 2;
              drawLabel(c, item, stringX, stringY, color, itemSelectionFactor);
              item.counter.draw(c, cx + itemWidth - horizontalPadding - item.counter.getWidth() / 2f, viewHeight / 2f, Gravity.CENTER, textAlpha, backgroundAlpha, imageAlpha, item.provider, ColorId.NONE);
            } else if (item.imageReceiver != null) {
              int size = item.imageReceiverSize;
              int imgY = (viewHeight - size) / 2;
              item.imageReceiver.setAlpha(imageAlpha);
              item.imageReceiver.setBounds(cx, imgY, cx + size, imgY + size);
              item.imageReceiver.drawScaled(c, item.imageReceiverScale);
              item.counter.draw(c, cx + size, viewHeight / 2f, Gravity.LEFT, textAlpha, backgroundAlpha, imageAlpha, item.provider, ColorId.NONE);
            } else if (item.iconRes != 0) {
              Drawable drawable = item.getIcon();
              Drawables.draw(c, drawable, cx + horizontalPadding, viewHeight / 2 - drawable.getMinimumHeight() / 2, Paints.getPorterDuffPaint(color));
              item.counter.draw(c, cx + itemWidth - horizontalPadding - item.counter.getWidth() / 2f, viewHeight / 2f, Gravity.CENTER, textAlpha, backgroundAlpha, imageAlpha, item.provider, ColorId.NONE);
              if (item.isAnimating() && item.oldItem != null && item.oldItem.ellipsizedStringLayout != null) {
                int stringX = cx + horizontalPadding + Screen.dp(ICON_SIZE) + itemSpacing;
                int stringY = viewHeight / 2 - item.oldItem.ellipsizedStringLayout.getHeight() / 2;
                drawLabel(c, item, stringX, stringY, color, itemSelectionFactor);
              }
            } else {
              float counterWidth = item.counter.getWidth();
              float addX = -Math.min((itemWidth - counterWidth) / 2f + item.translationX, 0);
              item.counter.draw(c, cx + itemWidth / 2f + addX, viewHeight / 2f, Gravity.CENTER, textAlpha, backgroundAlpha, imageAlpha, item.provider, ColorId.NONE);
            }
          } else if (item.ellipsizedStringLayout != null) {
            int stringX;
            if (item.iconRes != 0) {
              Drawable drawable = item.getIcon();
              Drawables.draw(c, drawable, cx + horizontalPadding, viewHeight / 2 - drawable.getMinimumHeight() / 2, Paints.getPorterDuffPaint(color));
              stringX = cx + horizontalPadding + Screen.dp(ICON_SIZE) + itemSpacing;
            } else {
              stringX = cx + itemWidth / 2 - contentWidth / 2;
            }
            int stringY = viewHeight / 2 - item.ellipsizedStringLayout.getHeight() / 2;
            drawLabel(c, item, stringX, stringY, color, itemSelectionFactor);
          } else if (item.iconRes != 0) {
            Drawable drawable = item.getIcon();
            Drawables.draw(c, drawable, cx + itemWidth / 2 - drawable.getMinimumWidth() / 2, viewHeight / 2 - drawable.getMinimumHeight() / 2, Paints.getPorterDuffPaint(color));
            if (item.isAnimating() && item.oldItem != null && item.oldItem.ellipsizedStringLayout != null) {
              int stringX = cx + horizontalPadding + Screen.dp(ICON_SIZE) + itemSpacing;
              int stringY = viewHeight / 2 - item.oldItem.ellipsizedStringLayout.getHeight() / 2;
              drawLabel(c, item, stringX, stringY, color, itemSelectionFactor);
            }
          }
        }
        if (!rtl)
          cx += itemWidth;
        itemIndex++;

        if (hasTranslate) {
          c.restore();
        }
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

  public boolean getItemRect (int position, Rect outRect) {
    View child = getChildAt(position);
    if (child instanceof BackgroundView && ((BackgroundView) child).index == position) {
      int itemWidth = getItemWidth(position, selectionFactor);
      outRect.set(0, 0, itemWidth, child.getHeight());
      float offsetX = child.getX() + (Lang.rtl() ? child.getWidth() - itemWidth : 0);
      float offsetY = child.getY();
      outRect.offset(Math.round(offsetX), Math.round(offsetY));
      return true;
    }
    return false;
  }

  public int getItemWidth (View view) {
    if (view.getParent() == this && view instanceof BackgroundView) {
      return ((BackgroundView) view).lastItemWidth;
    }
    return view.getMeasuredWidth();
  }

  private final @Dimension(unit = Dimension.DP) float labelFadingEdgeLength = 16f;
  private final Paint labelFadingEdgePaint = new Paint();
  private final Matrix labelFadingEdgeMatrix = new Matrix();

  {
    labelFadingEdgePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
    labelFadingEdgePaint.setShader(new LinearGradient(0, 0, 1, 0, Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP));
  }

  private void drawLabel (Canvas c, Item item, float x, float y, int color, float itemSelectionFactor) {
    Layout layout = item.ellipsizedStringLayout;
    Layout oldLayout = item.oldItem != null ? item.oldItem.ellipsizedStringLayout : null;
    if (layout == null && oldLayout == null) {
      return;
    }
    float labelFactor = getLabelFactorByItemSelectionFactor(itemSelectionFactor);
    if (labelFactor <= 0f) {
      return;
    }
    float textWidth = layout != null ? getLabelWidth(item) : getLabelWidth(item.oldItem);
    float clipRight = x + textWidth * labelFactor;
    boolean isVisible = clipRight - x >= 1f;
    boolean clipText = labelFactor < 1f && isVisible;
    int fadingEdgeLength = Screen.dp(labelFadingEdgeLength);
    final int saveCount;
    if (clipText) {
      saveCount = c.save();
      c.clipRect(x, 0, clipRight, getHeight());
      Views.saveLayerAlpha(c, x, 0, clipRight, getHeight(), (int) (0xFF * labelFactor), Canvas.ALL_SAVE_FLAG);
    } else {
      saveCount = -1;
    }
    if (isVisible) {
      if (item.hasIconSpans()) {
        //noinspection DataFlowIssue
        for (IconSpan iconSpan : item.iconSpans) {
          iconSpan.setAlpha(getIconSpanAlpha(item));
          iconSpan.setScale(getIconSpanScale(item));
        }
      }
      if (item.oldItem != null && item.oldItem.hasIconSpans()) {
        //noinspection DataFlowIssue
        for (IconSpan iconSpan : item.oldItem.iconSpans) {
          iconSpan.setOverrideColor(color);
          iconSpan.setAlpha(getOldIconSpanAlpha(item));
          iconSpan.setScale(getOldIconSpanScale(item));
        }
      }
      c.translate(x, y);
      //noinspection DataFlowIssue
      if (oldLayout != null && item.oldItem.hasIconSpans()) {
        oldLayout.getPaint().setAlpha(0x00); // draw old icon spans only
        oldLayout.draw(c);
      }
      if (layout != null) {
        layout.getPaint().setColor(color);
        layout.draw(c);
      }
      c.translate(-x, -y);
    }
    if (clipText) {
      labelFadingEdgeMatrix.setScale((1f - labelFactor) * fadingEdgeLength, 1f);
      labelFadingEdgeMatrix.postRotate(180);
      labelFadingEdgeMatrix.postTranslate(clipRight, 0);
      labelFadingEdgePaint.getShader().setLocalMatrix(labelFadingEdgeMatrix);
      c.drawRect(clipRight - fadingEdgeLength, 0, clipRight, getHeight(), labelFadingEdgePaint);
      c.restoreToCount(saveCount);
    }
  }

  private int getLabelWidth (Item item) {
    if (item.ellipsizedStringLayout == null) {
      return 0;
    }
    return Math.max(item.ellipsizedStringLayout.getWidth(), item.width - item.collapsedWidth);
  }

  private float getIconSpanAlpha (Item item) {
    return shouldAnimateIconSpanAppearance(item) ? item.getAnimationFactor() : 1f;
  }

  private float getIconSpanScale (Item item) {
    return shouldAnimateIconSpanAppearance(item) ? getIconSpanScale(item.getAnimationFactor()) : 1f;
  }

  private float getOldIconSpanAlpha (Item item) {
    return shouldAnimateIconSpanDisappearance(item) ? 1f - item.getAnimationFactor() : 0f;
  }

  private float getOldIconSpanScale (Item item) {
    return shouldAnimateIconSpanDisappearance(item) ? getIconSpanScale(1f - item.getAnimationFactor()) : 1f;
  }

  private float getIconSpanScale (float animationFactor) {
    return MathUtils.fromTo(0.3f, 1f, animationFactor);
  }

  private boolean shouldAnimateIconSpanAppearance (Item item) {
    return animateItemChanges && item.oldItem != null && !item.oldItem.hasIconSpans() && !isCounterVisible(item.oldItem);
  }

  private boolean shouldAnimateIconSpanDisappearance (Item item) {
    return animateItemChanges && item.oldItem != null && !item.hasIconSpans() && !isCounterVisible(item);
  }

  /** @noinspection BooleanMethodIsAlwaysInverted*/
  private boolean isCounterVisible (Item item) {
    return item.counter != null && item.counter.getVisibility() > 0f;
  }

  @FloatRange(from = 0.0, to = 1.0)
  private float getLabelFactor (int itemIndex, @FloatRange(from = 0.0) float selectionFactor) {
    if (showLabelOnActiveOnly) {
      float itemSelectionFactor = getItemSelectionFactor(itemIndex, selectionFactor);
      return getLabelFactorByItemSelectionFactor(itemSelectionFactor);
    }
    return 1f;
  }

  @FloatRange(from = 0.0, to = 1.0)
  private float getLabelFactorByItemSelectionFactor (@FloatRange(from = 0.0, to = 1.0) float itemSelectionFactor) {
    if (showLabelOnActiveOnly) {
      return itemSelectionFactor;
    }
    return 1f;
  }

  @FloatRange(from = 0.0, to = 1.0)
  private float getItemSelectionFactor (int itemIndex, @FloatRange(from = 0.0) float selectionFactor) {
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
    return factor;
  }

  private static final CounterAlphaProvider DEFAULT_COUNTER_ALPHA_PROVIDER = new CounterAlphaProvider() {
  };

  public interface CounterAlphaProvider {
    default float getTextAlpha (Counter counter, @FloatRange(from = 0f, to = 1f) float alphaFactor) {
      return .5f + .5f * alphaFactor;
    }
    default float getDrawableAlpha (Counter counter, @FloatRange(from = 0f, to = 1f) float alphaFactor) {
      return .5f + .5f * alphaFactor;
    }
    default float getBackgroundAlpha (Counter counter, @FloatRange(from = 0f, to = 1f) float alphaFactor) {
      return .5f + .5f * alphaFactor;
    }
  }

  public void setCounterAlphaProvider (CounterAlphaProvider counterAlphaProvider) {
    this.counterAlphaProvider = counterAlphaProvider;
  }

  public interface OnItemClickListener {
    void onPagerItemClick (int index);
    default boolean onPagerItemLongClick (int index) {
      return false;
    }
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

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    complexReceiver.attach();
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    complexReceiver.detach();
  }

  @Override
  public void performDestroy () {
    complexReceiver.performDestroy();
  }

  private static class BackgroundView extends View {
    public BackgroundView (Context context) {
      super(context);

      Views.setClickable(this);
    }

    private long touchDownTime;
    private float touchDownY;
    private float touchX;
    private float touchY;
    private boolean inSlideOff;
    private ViewParent lockedParent;

    @Override
    public boolean onTouchEvent (MotionEvent e) {
      OnSlideOffListener slideOffListener = topView != null ? topView.onSlideOffListener : null;
      if (slideOffListener == null) {
        return ((View) getParent()).getAlpha() >= 1f && super.onTouchEvent(e);
      }
      super.onTouchEvent(e);
      switch (e.getAction()) {
        case MotionEvent.ACTION_DOWN:
          touchX = e.getX();
          touchY = e.getY();
          touchDownY = e.getY();
          touchDownTime = e.getDownTime();
          break;
        case MotionEvent.ACTION_MOVE: {
          if (touchDownTime < 0L) {
            break;
          }
          touchX = e.getX();
          touchY = e.getY();
          if (lockedParent == null) {
            if (Math.abs(touchY - touchDownY) > Screen.getTouchSlop()) {
              boolean needSlideOff = slideOffListener.onSlideOffPrepare(this, e, index);
              if (needSlideOff) {
                lockedParent = getParent();
                if (lockedParent != null) {
                  lockedParent.requestDisallowInterceptTouchEvent(true);
                }
              }
            }
          } else {
            int start = getMeasuredHeight();
            boolean inSlideOff = topView.slideOffDirection == SLIDE_OFF_DIRECTION_TOP ? touchY <= start : touchY >= start;
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
          }
          break;
        }
        case MotionEvent.ACTION_CANCEL:
          finishSlideOff(e, slideOffListener, /* apply */ false);
          break;
        case MotionEvent.ACTION_UP:
          finishSlideOff(e, slideOffListener, /* apply */ true);
          break;
      }
      return true;
    }

    public boolean inSlideOff () {
      return inSlideOff;
    }

    public void cancelSlideOff () {
      MotionEvent e = MotionEvent.obtain(touchDownTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, touchX, touchY, /* metaState */ 0);
      OnSlideOffListener slideOffListener = topView != null ? topView.onSlideOffListener : null;
      finishSlideOff(e, slideOffListener, /* apply */ false);
    }

    public void finishSlideOff (MotionEvent e, @Nullable OnSlideOffListener slideOffListener, boolean apply) {
      touchDownTime = Long.MIN_VALUE;
      if (inSlideOff) {
        inSlideOff = false;
        if (slideOffListener != null) {
          slideOffListener.onSlideOffFinish(this, e, index, apply);
        }
      }
      if (lockedParent != null) {
        lockedParent.requestDisallowInterceptTouchEvent(false);
        lockedParent = null;
      }
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
      float translationX;
      if (topView.shouldWrapContent()) {
        int itemWidth = topView.getItemExpandedWidth(index);
        super.onMeasure(MeasureSpec.makeMeasureSpec(itemWidth, MeasureSpec.EXACTLY), heightMeasureSpec);
        translationX = topView.calculateItemX(index, itemWidth, MeasureSpec.getSize(widthMeasureSpec));
      } else {
        int itemWidth = topView.calculateCommonItemWidth(MeasureSpec.getSize(widthMeasureSpec));
        super.onMeasure(MeasureSpec.makeMeasureSpec(itemWidth, MeasureSpec.EXACTLY), heightMeasureSpec);
        translationX = itemWidth * index;
      }
      if (APPLY_HORIZONTAL_MARGIN) {
        Views.setLeftMargin(this, (int) translationX);
      } else {
        setTranslationX(translationX);
      }
    }

    int lastItemWidth;

    @Override
    public void draw (@NonNull Canvas canvas) {
      int itemWidth = topView.getItemWidth(index, topView.selectionFactor);
      lastItemWidth = itemWidth;
      if (itemWidth == getWidth()) {
        super.draw(canvas);
        return;
      }
      int saveCount = canvas.save();
      if (Lang.rtl()) {
        canvas.clipRect(getWidth() - itemWidth, 0, getWidth(), getHeight());
      } else {
        canvas.clipRect(0, 0, itemWidth, getHeight());
      }
      super.draw(canvas);
      canvas.restoreToCount(saveCount);
    }
  }

  @Override
  public void onEmojiUpdated (boolean isPackSwitch) {
    invalidate();
  }

  private int calculateItemX (int itemIndex, int itemWidth, int parentWidth) {
    int left = 0;
    for (int i = 0; i < itemIndex; i++) {
      left += getItemWidth(i, selectionFactor);
    }
    if (Lang.rtl()) {
      left = parentWidth - left - itemWidth;
    }
    return left;
  }

  public int getTotalWidth () {
    if (shouldWrapContent()) {
      return totalWidth;
    }
    return getMeasuredWidth();
  }

  public int getMaxStableWidth () {
    if (shouldWrapContent()) {
      return showLabelOnActiveOnly ? maxStableWidth : totalWidth;
    }
    return getMeasuredWidth();
  }

  public int getItemsWidth (float selectionFactor) {
    if (!showLabelOnActiveOnly || getChildCount() == 0) {
      return totalWidth;
    }
    int itemsWidth = 0;
    for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
      itemsWidth += getItemWidth(itemIndex, selectionFactor);
    }
    return itemsWidth;
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (id == ITEM_ANIMATOR) {
      relayout();
      recalculateSelection(selectionFactor, true);
      invalidate();
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (id == ITEM_ANIMATOR) {
      Item item = (Item) callee.getObjValue();
      if (item != null) {
        item.clearAnimation();
      }
    }
  }
}
