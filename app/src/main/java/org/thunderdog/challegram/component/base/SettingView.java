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
 * File created on 06/08/2015 at 17:33
 */
package org.thunderdog.challegram.component.base;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.StringRes;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.user.RemoveHelper;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ComplexReceiverProvider;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.PorterDuffColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.DrawModifier;
import org.thunderdog.challegram.util.EmojiStatusHelper;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.util.text.TextColorSetOverride;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.util.text.TextWrapper;
import org.thunderdog.challegram.widget.AttachDelegate;
import org.thunderdog.challegram.widget.CheckBoxView;
import org.thunderdog.challegram.widget.ProgressComponent;
import org.thunderdog.challegram.widget.RadioView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.SingleViewProvider;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;

public class SettingView extends FrameLayoutFix implements FactorAnimator.Target, TGLegacyManager.EmojiLoadListener, AttachDelegate, Destroyable, RemoveHelper.RemoveDelegate, TextColorSet, TooltipOverlayView.LocationProvider, ComplexReceiverProvider {
  public static final int TYPE_INFO = 0x01;
  public static final int TYPE_SETTING = 0x02;
  public static final int TYPE_RADIO = 0x03;
  public static final int TYPE_SETTING_INACTIVE = 0x04;
  public static final int TYPE_INFO_MULTILINE = 0x05;
  public static final int TYPE_INFO_COMPACT = 0x07;
  public static final int TYPE_INFO_SUPERCOMPACT = 0x08;

  private static final int FLAG_CENTER_ICON = 1 << 3;
  private static final int FLAG_DATA_SUBTITLE = 1 << 5;
  private static final int FLAG_ATTACHED = 1 << 6;

  public interface IconOverlay {
    void drawIconOverlay (Canvas c, Drawable drawable, int width, int height);
  }

  private int type;
  private int flags;

  private Drawable icon;

  private CharSequence itemName;
  private CharSequence displayItemName;
  private Layout displayItemNameLayout;
  private Text displayItemNameText;

  private CharSequence itemData;
  private CharSequence displayItemData;
  private Layout displayItemDataLayout;
  private EmojiStatusHelper emojiStatusHelper;

  private TogglerView togglerView;

  private float pLeft, pTop;
  private float pDataLeft, pDataTop;
  private float pIconLeft, pIconTop;

  private int displayItemNameWidth;
  private int displayItemDataWidth;

  private @ColorId
  int textColorId = ColorId.text;
  private @ColorId
  int dataColorId;

  private final Tdlib tdlib;

  private TextWrapper text;
  private IconOverlay overlay;
  private final ComplexReceiver complexReceiver;

  public SettingView (Context context, Tdlib tdlib) {
    super(context);
    this.tdlib = tdlib;
    this.complexReceiver = new ComplexReceiver(this);
    this.emojiStatusHelper = new EmojiStatusHelper(tdlib, this, null);
    setWillNotDraw(false);
  }

  public void setType (int type) {
    this.type = type;

    if (type != TYPE_SETTING_INACTIVE) {
      Views.setClickable(this);
      RippleSupport.setSimpleWhiteBackground(this);
    }

    switch (type) {
      case TYPE_INFO: {
        setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(76f)));
        break;
      }
      case TYPE_INFO_COMPACT: {
        setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(64f)));
        break;
      }
      case TYPE_INFO_MULTILINE: {
        setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        break;
      }
      case TYPE_RADIO: {
        addToggler();
        setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(55f)));
        break;
      }
      case TYPE_SETTING:
      case TYPE_SETTING_INACTIVE:
      case TYPE_INFO_SUPERCOMPACT: {
        setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(55f)));
        break;
      }
      default: {
        throw new RuntimeException("Invalid SettingView type " + type);
      }
    }
  }

  public void addToggler () {
    if (togglerView == null) {
      togglerView = new TogglerView(getContext());
      togglerView.init(isEnabled());
      FrameLayout.LayoutParams params = FrameLayoutFix.newParams(Screen.dp(66f), Screen.dp(48f), (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL);
      params.leftMargin = Screen.dp(4f);
      params.bottomMargin = Screen.dp(3f);
      togglerView.setLayoutParams(params);

      addView(togglerView);
    }
  }

  public void checkRtl (boolean allowLayout) {
    if (togglerView != null) {
      togglerView.checkRtl(allowLayout);
    }
  }

  private ImageReceiver receiver;

  @Override
  public void attach () {
    Views.attachDetach(this, true);
    flags |= FLAG_ATTACHED;
    if (receiver != null)
      receiver.attach();
    complexReceiver.attach();
    emojiStatusHelper.attach();
  }

  @Override
  public void detach () {
    Views.attachDetach(this, false);
    flags &= ~FLAG_ATTACHED;
    if (receiver != null)
      receiver.detach();
    complexReceiver.detach();
    emojiStatusHelper.detach();
  }

  @Override
  public void performDestroy () {
    Views.destroy(this);
    if (receiver != null)
      receiver.destroy();
    complexReceiver.performDestroy();
    emojiStatusHelper.performDestroy();
    if (subscribedToEmojiUpdates) {
      TGLegacyManager.instance().removeEmojiListener(this);
      subscribedToEmojiUpdates = false;
    }
  }

  public ImageReceiver getReceiver () {
    if (receiver == null) {
      receiver = new ImageReceiver(this, 0);
      if ((flags & FLAG_ATTACHED) == 0)
        receiver.detach();
    }
    return receiver;
  }

  @Override
  public ComplexReceiver getComplexReceiver () {
    return complexReceiver;
  }

  public @Px float getMeasuredNameTop () {
    return pTop;
  }

  public @Px float getMeasuredNameStart () {
    return pLeft;
  }

  public @Px int getMeasuredNameWidth () {
    return displayItemNameWidth;
  }

  public void setTextColorId (@ColorId int textColorId) {
    if (textColorId == ColorId.NONE)
      textColorId = ColorId.text;
    if (this.textColorId != textColorId) {
      this.textColorId = textColorId;
      invalidate();
    }
  }

  public void setDataColorId (int dataColorId) {
    setDataColorId(dataColorId, false);
  }

  public void setDataColorId (int dataColorId, boolean isSubtitle) {
    if (this.dataColorId != dataColorId || isSubtitle != BitwiseUtils.hasFlag(flags, FLAG_DATA_SUBTITLE)) {
      this.dataColorId = dataColorId;
      this.flags = BitwiseUtils.setFlag(flags, FLAG_DATA_SUBTITLE, isSubtitle);
      invalidate();
    }
  }

  public void addThemeListeners (@Nullable ViewController<?> themeProvider) {
    if (themeProvider != null) {
      if (togglerView != null) {
        themeProvider.addThemeInvalidateListener(togglerView);
      }
      themeProvider.addThemeInvalidateListener(this);
    }
  }

  @Override
  public void onEmojiUpdated (boolean isPackSwitch) {
    invalidate();
  }

  private int forcedPaddingLeft, forcedPaddingRight;

  public void forcePadding (int left, int right) {
    if (this.forcedPaddingLeft != left || this.forcedPaddingRight != right) {
      this.forcedPaddingLeft = left;
      this.forcedPaddingRight = right;
      buildLayout();
    }
  }

  public int getForcedPaddingLeft () {
    return forcedPaddingLeft;
  }

  public int getForcedPaddingRight () {
    return forcedPaddingRight;
  }

  private int measurePaddingLeft () {
    return forcedPaddingLeft != 0 ? forcedPaddingLeft : Screen.dp(icon == null ? 16f : 73f);
  }

  @SuppressWarnings ("Range")
  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    if (type == TYPE_INFO_MULTILINE) {
      if (text != null) {
        int paddingLeft = measurePaddingLeft();
        int paddingRight = Screen.dp(17f);
        text.get(MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight);
      }
      if (lastMeasuredWidth != MeasureSpec.getSize(widthMeasureSpec) || lastMeasuredHeight != getCurrentHeight()) {
        buildLayout(MeasureSpec.getSize(widthMeasureSpec), getCurrentHeight());
      }
      super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(getCurrentHeight(), MeasureSpec.EXACTLY));
    } else {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
    layoutProgress();
  }

  public void setIconOverlay (IconOverlay overlay) {
    this.overlay = overlay;
  }

  public void setCenterIcon (boolean centerIcon) {
    flags = BitwiseUtils.setFlag(flags, FLAG_CENTER_ICON, centerIcon);
  }

  private int lastIconResource;

  public void setIcon (@DrawableRes int rawResource) {
    if (lastIconResource != rawResource) {
      lastIconResource = rawResource;
      setIconInternal(Drawables.get(getResources(), rawResource));
    }
  }

  public void setIcon (Bitmap bitmap) {
    lastIconResource = 0;
    setIconInternal(Drawables.bitmapDrawable(getContext(), bitmap));
  }

  private void setIconInternal (Drawable drawable) {
    icon = drawable;
    if (drawable != null && (flags & FLAG_CENTER_ICON) != 0 && getMeasuredHeight() != 0) {
      pIconTop = getMeasuredHeight() / 2 - drawable.getMinimumHeight() / 2;
    }
  }

  public void setName (CharSequence name) {
    if (this.itemName == null || !this.itemName.equals(name)) {
      boolean rebuild = lastMeasuredWidth > 0;
      this.itemName = !StringUtils.isEmpty(name) ? name : null;
      if (rebuild) {
        buildLayout();
        invalidate();
      }
    }
  }

  public CharSequence getName () {
    return itemName;
  }

  private int getCurrentHeight () {
    int height;
    if (text != null) {
      height = Math.max(text.getHeight() + (int) pDataTop - Screen.dp(13f) + Screen.dp(12f) + Screen.dp(25), Screen.dp(76f));
    } else {
      height = Screen.dp(76f);
    }
    if (displayItemNameText != null) {
      height += displayItemNameText.getHeight() - displayItemNameText.getLineHeight();
    }
    return height;
  }

  public void setName (@StringRes int resId) {
    setName(Lang.getString(resId));
  }

  public void setData (float value) {
    if (value == 1f) {
      setData("1.0");
    } else if (value == 0f) {
      setData("0.0");
    } else if (value == .5f) {
      setData("0.50");
    } else {
      int x = (int) (value * 100f);
      StringBuilder b = new StringBuilder(4);
      b.append('0');
      b.append('.');
      if (x < 10) {
        b.append('0');
      }
      b.append(x);
      setData(b.toString());
    }
  }

  public void setData (CharSequence data) {
    if (this.itemData == null || data == null || !StringUtils.equalsOrBothEmpty(this.itemData, data)) {
      boolean rebuild = lastMeasuredWidth > 0;
      this.itemData = !StringUtils.isEmpty(data) ? data : null;
      if (rebuild) {
        buildLayout();
        invalidate();
      }
    }
  }

  private int colorDataId = ColorId.NONE;

  public void setColorDataId (@ColorId int color) {
    if (this.colorDataId != color) {
      this.colorDataId = color;
      invalidate();
    }
  }

  public void setData (@StringRes int resId) {
    setData(Lang.getString(resId));
  }

  public void setText (TextWrapper text) {
    if (this.text != null) {
      this.text.detachFromView(this);
    }
    this.text = text;
    if (text != null) {
      text.attachToView(this);
      if (lastTextAvailWidth != 0) {
        text.get(lastTextAvailWidth);
      }
    }
    if (getMeasuredHeight() != getCurrentHeight() && getMeasuredHeight() != 0) {
      requestLayout();
    }
    checkEmojiListener();
    invalidate();
  }

  public void setEmojiStatus (@Nullable TdApi.User user) {
    emojiStatusHelper.updateEmoji(user, TextColorSets.Regular.NORMAL);
  }

  public int getType () {
    return type;
  }

  public TogglerView getToggler () {
    return togglerView;
  }

  public RadioView findRadioView () {
    return (RadioView) getChildAt(0);
  }

  public CheckBoxView findCheckBox () {
    return (CheckBoxView) getChildAt(0);
  }

  public boolean toggleRadio () {
    return togglerView.toggle(true);
  }

  @Override
  protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    if (changed) {
      buildLayout();
    }
  }

  private boolean swapDataAndName;

  public void setSwapDataAndName () {
    swapDataAndName = true;
  }

  private int lastMeasuredWidth, lastMeasuredHeight, lastTextAvailWidth;

  private void buildLayoutIfNeeded () {
    if (getMeasuredWidth() != lastMeasuredWidth || getMeasuredHeight() != lastMeasuredHeight) {
      buildLayout();
    }
  }

  private void buildLayout () {
    buildLayout(getMeasuredWidth(), getMeasuredHeight());
  }

  private void setDisplayItemName (CharSequence str, float availWidth, TextPaint paint, float textSize) {
    if (allowMultiLineName) {
      displayItemName = str;
      displayItemNameLayout = null;
      displayItemNameWidth = 0;
      displayItemNameText = new Text.Builder(tdlib, str, null, (int) availWidth, Paints.robotoStyleProvider(textSize), this, null)
        .textFlags(Text.FLAG_CUSTOM_LONG_PRESS)
        .singleLine(!allowMultiLineName)
        .view(this)
        .build();
    } else {
      displayItemNameText = null;
      str = TextUtils.ellipsize(str, paint, availWidth, TextUtils.TruncateAt.END);
      displayItemName = str;
      if (str instanceof String) {
        displayItemNameWidth = (int) U.measureText(displayItemName, paint);
        displayItemNameLayout = null;
      } else {
        displayItemNameLayout = U.createLayout(str, (int) availWidth, paint);
        displayItemNameWidth = displayItemNameLayout.getWidth();
      }
    }
    checkEmojiListener();
  }

  private void setDisplayItemData (CharSequence str, float availWidth, TextPaint paint) {
    str = TextUtils.ellipsize(str, paint, availWidth, TextUtils.TruncateAt.END);
    displayItemData = str;
    if (str instanceof String) {
      displayItemDataWidth = (int) U.measureText(displayItemData, paint);
      displayItemDataLayout = null;
    } else {
      displayItemDataLayout = U.createLayout(str, (int) availWidth, paint);
      displayItemDataWidth = displayItemDataLayout.getWidth();
    }
    checkEmojiListener();
  }

  private boolean allowMultiLineName;

  public void setAllowMultiLineName (boolean allowMultiLineName) {
    if (this.allowMultiLineName != allowMultiLineName) {
      this.allowMultiLineName = allowMultiLineName;
      buildLayout();
      invalidate();
    }
  }

  private void buildLayout (int totalWidth, int totalHeight) {
    if (totalWidth == 0 || totalHeight == 0) return;

    lastMeasuredWidth = totalWidth;
    lastMeasuredHeight = totalHeight;

    int paddingLeft = measurePaddingLeft();
    int paddingRight = Screen.dp(17f) + forcedPaddingRight;

    pLeft = paddingLeft;

    float availWidth;

    if (type == TYPE_RADIO) {
      availWidth = totalWidth - paddingLeft - paddingRight - paddingRight - Screen.dp(38f);
    } else {
      availWidth = totalWidth - paddingLeft - paddingRight;
    }

    if (drawModifiers != null) {
      int maxWidth = 0;
      for (DrawModifier modifier : drawModifiers) {
        maxWidth = Math.max(maxWidth, modifier.getWidth());
      }
      availWidth -= maxWidth;
    }

    if (counter != null) {
      availWidth -= counter.getScaledWidth(Screen.dp(24f) + Screen.dp(8f));
    }

    availWidth -= emojiStatusHelper.getWidth(Screen.dp(6));

    if (type == TYPE_INFO_SUPERCOMPACT) {
      boolean hasName = !StringUtils.isEmpty(swapDataAndName ? itemData : itemName);
      pTop = Screen.dp((hasName ? 10f : 21f) + 13f);
    } else if (type == TYPE_INFO_COMPACT) {
      pTop = Screen.dp(15f + 13f);
    } else {
      pTop = Screen.dp(21f + 13f);
    }

    if (swapDataAndName) {
      displayItemData = itemName;
      displayItemName = itemData;
    } else {
      displayItemData = itemData;
      displayItemName = itemName;
    }

    if (type == TYPE_INFO || type == TYPE_INFO_COMPACT || type == TYPE_INFO_SUPERCOMPACT || type == TYPE_INFO_MULTILINE) {
      pDataLeft = pLeft;
      pDataTop = pTop;
      pTop = pTop + Screen.dp(20f);
      if (displayItemData != null) {
        setDisplayItemData(displayItemData, availWidth, Paints.getTextPaint16());
      }
      if (text != null) {
        text.get(lastTextAvailWidth = (int) availWidth);
      } else {
        lastTextAvailWidth = 0;
      }
      if (displayItemName != null) {
        setDisplayItemName(displayItemName, availWidth, Paints.getRegularTextPaint(13f), 13f);
      }
    } else {
      if (displayItemData != null) {
        setDisplayItemData(displayItemData, availWidth, Paints.getTextPaint16());
        pDataLeft = totalWidth - paddingRight - displayItemDataWidth;
        pDataTop = pTop;
      }
      if (displayItemName != null) {
        setDisplayItemName(displayItemName, availWidth, Paints.getTextPaint16(), 16f);
      }
    }

    pIconTop = (flags & FLAG_CENTER_ICON) != 0 && icon != null ?  (totalHeight / 2f - icon.getMinimumHeight() / 2f) : Screen.dp(type == TYPE_INFO || type == TYPE_INFO_COMPACT || type == TYPE_INFO_MULTILINE ? 20f : 16f);
    pIconLeft = Screen.dp(18f);
  }

  private boolean subscribedToEmojiUpdates;

  private void checkEmojiListener () {
    boolean needEmojiListener = this.displayItemNameLayout != null || this.displayItemDataLayout != null || (this.displayItemNameText != null && this.displayItemNameText.hasBuiltInEmoji()) || (this.text != null && this.text.hasBuiltInEmoji());
    if (this.subscribedToEmojiUpdates != needEmojiListener) {
      this.subscribedToEmojiUpdates = needEmojiListener;
      if (needEmojiListener) {
        TGLegacyManager.instance().addEmojiListener(this);
      } else {
        TGLegacyManager.instance().removeEmojiListener(this);
      }
    }
  }

  private final BoolAnimator iconRotated = new BoolAnimator(this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L, false);

  public void setIconRotated (boolean rotated, boolean animated) {
    iconRotated.setValue(rotated, animated);
  }

  private final BoolAnimator isEnabled = new BoolAnimator(this, AnimatorUtils.DECELERATE_INTERPOLATOR, 168l, true);

  public void setEnabledAnimated (boolean enabled) {
    setEnabledAnimated(enabled, false, true);
  }

  public void setEnabledAnimated (boolean enabled, boolean ignoreRadio, boolean animate) {
    if (isEnabled() != enabled) {
      super.setEnabled(enabled);
      if (type == TYPE_RADIO && !ignoreRadio) {
        togglerView.setDisabled(!enabled, animate);
      }
    }
    isEnabled.setValue(enabled, animate);
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_ID_PROGRESS: {
        progressComponent.setAlpha(factor);
        invalidate();
        break;
      }
    }
  }

  private boolean ignoreEnabled;

  public void setIgnoreEnabled (boolean ignoreEnabled) {
    if (this.ignoreEnabled != ignoreEnabled) {
      this.ignoreEnabled = ignoreEnabled;
      if (!isEnabled()) {
        isEnabled.setValue(ignoreEnabled, false);
      }
    }
  }

  public void setEnabledAnimated (boolean enabled, boolean animated) {
    if (animated) {
      setEnabledAnimated(enabled);
    } else {
      setEnabled(enabled);
    }
  }

  @Override
  public void setEnabled (boolean enabled) {
    super.setEnabled(enabled);
    if (ignoreEnabled) {
      return;
    }
    isEnabled.setValue(enabled, true);
    if (type == TYPE_RADIO) {
      togglerView.setDisabled(!enabled, false);
    }
  }

  public void setVisuallyEnabled (boolean enabled, boolean animated) {
    if (!ignoreEnabled) {
      throw new IllegalStateException();
    }
    isEnabled.setValue(enabled, animated);
  }

  public boolean isVisuallyEnabled () {
    return isEnabled.getValue();
  }

  public float getVisuallyEnabledFactor () {
    return isEnabled.getFloatValue();
  }

  private static void drawText (Canvas c, CharSequence text, Layout layout, float x, float y, int textY, Paint paint, boolean rtl, int viewWidth, float textWidth, Text wrap, TextColorSet textColorSet, EmojiStatusHelper emojiStatusHelper) {
    if (wrap != null) {
      wrap.draw(c, (int) x, (int) (viewWidth - x), 0, textY, textColorSet != null ? textColorSet : TextColorSets.Regular.NEGATIVE);
    } else if (layout != null) {
      c.save();
      c.translate(rtl ? viewWidth - U.maxWidth(layout) - x : x, y - paint.getTextSize() + Screen.dp(.8f));
      layout.draw(c);
      c.restore();
    } else {
      c.drawText((String) text, rtl ? viewWidth - textWidth - x : x, y, paint);
      emojiStatusHelper.draw(c, (int) (x + textWidth + Screen.dp(6)), (int) textY);
    }
  }

  private @PorterDuffColorId int iconColorId = ColorId.NONE;
  private float iconAlpha = 1f;

  public void setIconAlpha (float alpha) {
    if (this.iconAlpha != alpha) {
      this.iconAlpha = alpha;
      if (icon != null)
        invalidate();
    }
  }

  public void setIconColorId (@PorterDuffColorId int colorId) {
    if (this.iconColorId != colorId) {
      this.iconColorId = colorId;
      if (icon != null)
        invalidate();
    }
  }

  private Counter counter;

  public void setUnreadCounter (int unreadCount, boolean muted, boolean animated) {
    if (counter == null && unreadCount == 0)
      return;
    if (counter == null)
      counter = new Counter.Builder().callback((counter, sizeChanged) -> {
        if (sizeChanged) {
          buildLayout();
        }
        invalidate();
      }).build();
    counter.setCount(unreadCount, muted, animated);
  }

  private @Nullable TooltipOverlayView.LocationProvider tooltipLocationProvider;

  public void setTooltipLocationProvider (@Nullable TooltipOverlayView.LocationProvider tooltipLocationProvider) {
    this.tooltipLocationProvider = tooltipLocationProvider;
  }

  @Override
  public void getTargetBounds (View targetView, Rect outRect) {
    if (tooltipLocationProvider != null) {
      tooltipLocationProvider.getTargetBounds(targetView, outRect);
      return;
    }
    if (type == TYPE_INFO || type == TYPE_INFO_COMPACT || type == TYPE_INFO_SUPERCOMPACT || (type == TYPE_INFO_MULTILINE && text == null)) {
      if (itemData != null) {
        int dataTop = (int) (pDataTop - Screen.dp(13f));
        Paint.FontMetricsInt fm = Paints.getTextPaint16().getFontMetricsInt();
        int dataBottom = dataTop + U.getLineHeight(fm);
        outRect.set((int) pDataLeft, dataTop, (int) (pDataLeft + displayItemDataWidth), dataBottom);
      }
    }
  }

  @Override
  public void draw (Canvas c) {
    if (removeHelper != null) {
      removeHelper.save(c);
    }
    super.draw(c);
    if (removeHelper != null) {
      removeHelper.restore(c);
      removeHelper.draw(c);
    }
  }

  private float disabledAlpha = -1f;

  public void setDisabledAlpha (float alpha) {
    if (this.disabledAlpha != alpha) {
      this.disabledAlpha = alpha;
      invalidate();
    }
  }

  @Override
  public int defaultTextColor () {
    if (disabledAlpha != -1f) {
      return ColorUtils.alphaColor(MathUtils.fromTo(disabledAlpha, 1f, isEnabled.getFloatValue()), Theme.getColor(textColorId));
    } else {
      return ColorUtils.fromToArgb(Theme.textDecentColor(), Theme.getColor(textColorId), isEnabled.getFloatValue());
    }
  }

  @Override
  public long backgroundId (boolean isPressed) {
    return isPressed ? ColorId.textLinkPressHighlight : 0;
  }

  @Override
  public int clickableTextColor (boolean isPressed) {
    return Theme.textLinkColor();
  }

  @Override
  public int backgroundColor (boolean isPressed) {
    return isPressed ? Theme.textLinkHighlightColor() : 0;
  }

  private final TextColorSet subtitleColorSet = new TextColorSetOverride(this) {
    @Override
    public int defaultTextColor () {
      int subtitleColor = Theme.getColor(dataColorId != 0 ? dataColorId : ColorId.textLight);
      if ((flags & FLAG_DATA_SUBTITLE) != 0) {
        subtitleColor = ColorUtils.alphaColor(Theme.getSubtitleAlpha(), subtitleColor);
      }
      return subtitleColor;
    }
  };

  @Override
  protected void onDraw (Canvas c) {
    if (drawModifiers != null) {
      for (DrawModifier modifier : drawModifiers) {
        modifier.beforeDraw(this, c);
      }
    }
    boolean rtl = Lang.rtl();
    int width = getMeasuredWidth();
    if (icon != null) {
      int x = (int) (rtl ? width - pIconLeft - icon.getMinimumWidth() : pIconLeft) + Screen.dp(24f) / 2 - icon.getMinimumWidth() / 2;
      float y = pIconTop;
      final boolean needRotateIcon = iconRotated.getFloatValue() > 0;
      if (needRotateIcon) {
        float cx = x + icon.getMinimumWidth() / 2f;
        float cy = y + icon.getMinimumHeight() / 2f;
        c.save();
        c.rotate(MathUtils.fromTo(0, 90, iconRotated.getFloatValue()), cx, cy);
      }
      Drawables.draw(c, icon, x, y, lastIconResource == 0 ? Paints.getBitmapPaint() : iconColorId != 0 ? PorterDuffPaint.get(iconColorId, iconAlpha) : Paints.getIconGrayPorterDuffPaint());
      if (needRotateIcon) {
        c.restore();
      }

      // c.drawBitmap(icon, x, pIconTop, paint);
      if (overlay != null) {
        c.save();
        c.translate(x, pIconTop);
        overlay.drawIconOverlay(c, icon, icon.getMinimumWidth(), icon.getMinimumHeight());
        c.restore();
      }
    }

    float pLeft = this.pLeft;

    if (colorDataId != 0) {
      int radius = Screen.dp(4f);
      pLeft += radius + Screen.dp(8f);
      c.drawCircle(rtl ? width - pDataLeft - radius : pDataLeft + radius, pDataTop + Screen.dp(11f) + radius, radius, Paints.fillingPaint(Theme.getColor(colorDataId)));
    }

    final int dataColor = defaultTextColor();

    if (type == TYPE_INFO || type == TYPE_INFO_COMPACT || type == TYPE_INFO_SUPERCOMPACT || (type == TYPE_INFO_MULTILINE && text == null)) {
      if (displayItemName != null) {
        int subtitleColor = Theme.getColor(dataColorId != 0 ? dataColorId : ColorId.textLight);
        if ((flags & FLAG_DATA_SUBTITLE) != 0) {
          subtitleColor = ColorUtils.alphaColor(Theme.getSubtitleAlpha(), subtitleColor);
        }
        drawText(c, displayItemName, displayItemNameLayout, pLeft, pTop, (int) (pTop - Screen.dp(12f)), Paints.getRegularTextPaint(13f, subtitleColor), rtl, width, displayItemNameWidth, displayItemNameText, subtitleColorSet, emojiStatusHelper);
      }
      if (displayItemData != null) {
        drawText(c, displayItemData, displayItemDataLayout, pDataLeft, pDataTop,  (int) (pDataTop - Screen.dp(15f)), Paints.getTextPaint16(dataColor), rtl, width, displayItemDataWidth, null, null, emojiStatusHelper);
      }
    } else if (type == TYPE_INFO_MULTILINE) {
      if (displayItemName != null) {
        int subtitleColor = Theme.getColor(dataColorId != 0 ? dataColorId : ColorId.textLight);
        if ((flags & FLAG_DATA_SUBTITLE) != 0) {
          subtitleColor = ColorUtils.alphaColor(Theme.getSubtitleAlpha(), subtitleColor);
        }
        float top = (int) pDataTop - Screen.dp(13f) + text.getHeight() + Screen.dp(17f);
        drawText(c, displayItemName, displayItemNameLayout, pLeft, (int) pDataTop - Screen.dp(13f) + text.getHeight() + Screen.dp(15f), (int) (top - Screen.dp(12f)), Paints.getRegularTextPaint(13f, subtitleColor), rtl, width, displayItemNameWidth, displayItemNameText, subtitleColorSet, emojiStatusHelper);
      }
      if (text != null) {
        if (rtl) {
          text.draw(c, (int) (width - pLeft - text.getWidth()), (int) (width - pLeft), 0, (int) pDataTop - Screen.dp(13f), this, 1f);
        } else {
          text.draw(c, (int) pLeft, (int) (pLeft + text.getWidth()), 0, (int) pDataTop - Screen.dp(13f), this, 1f);
        }
      }
    } else {
      if (displayItemData != null) {
        drawText(c, displayItemData, displayItemDataLayout, pDataLeft, pDataTop, (int) (pDataTop - Screen.dp(15)), Paints.getTextPaint16(dataColor), rtl, width, displayItemDataWidth, null, null, emojiStatusHelper);
      }
      if (displayItemName != null) {
        drawText(c, displayItemName, displayItemNameLayout, pLeft, pTop, (int) (pTop - Screen.dp(15f)), Paints.getTextPaint16(dataColor), rtl, width, displayItemNameWidth, displayItemNameText, this, emojiStatusHelper);
      }
    }
    if (progressComponent != null) {
      progressComponent.draw(c);
    }
    if (drawModifiers != null) {
      for (int i = drawModifiers.size() - 1; i >= 0; i--) {
        drawModifiers.get(i).afterDraw(this, c);
      }
    }
    if (counter != null)
      counter.draw(c, rtl ? Screen.dp(24f) : width - Screen.dp(24f), getMeasuredHeight() / 2f, Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT, 1f);
  }

  // touch delegate

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    boolean res = (text != null && text.onTouchEvent(this, e));
    res = (displayItemNameText != null && displayItemNameText.onTouchEvent(this, e)) || res;
    return res || super.onTouchEvent(e);
  }

  // Draw modifier

  @Nullable
  private List<DrawModifier> drawModifiers;

  private int indexOfModifier (DrawModifier modifier) {
    return drawModifiers != null ? drawModifiers.indexOf(modifier) : -1;
  }

  public void addDrawModifier (@NonNull DrawModifier drawModifier, boolean inFront) {
    int i = indexOfModifier(drawModifier);
    if (i == -1) {
      if (drawModifiers == null)
        drawModifiers = new ArrayList<>();
      if (inFront)
        drawModifiers.add(0, drawModifier);
      else
        drawModifiers.add(drawModifier);
      invalidate();
    } else if (inFront && i != 0) {
      drawModifiers.remove(i);
      drawModifiers.add(0, drawModifier);
      invalidate();
    }
  }

  public void removeDrawModifier (@NonNull DrawModifier drawModifier) {
    int i = indexOfModifier(drawModifier);
    if (i != -1) {
      drawModifiers.remove(i);
      invalidate();
    }
  }

  public void setDrawModifier (@Nullable DrawModifier drawModifier) {
    if (drawModifier == null) {
      clearDrawModifiers();
    } else {
      if (drawModifiers != null) {
        if (drawModifiers.size() == 1 && drawModifiers.get(0) == drawModifier)
          return;
        drawModifiers.clear();
      } else {
        drawModifiers = new ArrayList<>();
      }
      drawModifiers.add(drawModifier);
      invalidate();
    }
  }

  public void clearDrawModifiers () {
    if (drawModifiers != null && !drawModifiers.isEmpty()) {
      drawModifiers.clear();
    }
  }

  @Nullable
  public List<DrawModifier> getDrawModifiers () {
    return drawModifiers;
  }

  // Remove delegate

  private RemoveHelper removeHelper;

  @Override
  public void setRemoveDx (float dx) {
    if (removeHelper == null)
      removeHelper = new RemoveHelper(this, R.drawable.baseline_delete_24);
    removeHelper.setDx(dx);
  }

  @Override
  public void onRemoveSwipe () {
    if (removeHelper == null) {
      removeHelper = new RemoveHelper(this, R.drawable.baseline_delete_24);
    }
    removeHelper.onSwipe();
  }

  // Progress

  private ProgressComponent progressComponent;
  private BoolAnimator progressAnimator;
  private static final int ANIMATOR_ID_PROGRESS = 1;

  private void layoutProgress () {
    if (progressComponent != null) {
      int width = getMeasuredWidth();
      int height = getMeasuredHeight();
      if (width > 0 && height > 0) {
        if (Lang.rtl()) {
          progressComponent.setBounds(0, 0, height, height);
        } else {
          progressComponent.setBounds(width - height, 0, width, height);
        }
      }
    }
  }

  public void setInProgress (boolean inProgress, boolean animated) {
    if (!inProgress && progressAnimator == null) {
      return;
    }
    if (progressComponent == null) {
      progressComponent = new ProgressComponent(UI.getContext(getContext()), Screen.dp(4.5f));
      progressComponent.setUseLargerPaint(Screen.dp(1.5f));
      progressComponent.setViewProvider(new SingleViewProvider(this));
      progressComponent.setAlpha(0f);
      layoutProgress();
    }
    if (progressAnimator == null) {
      progressAnimator = new BoolAnimator(ANIMATOR_ID_PROGRESS, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
    }
    progressAnimator.setValue(inProgress, animated);
  }
}
