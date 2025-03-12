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
 * File created on 15/08/2015 at 22:46
 */
package org.thunderdog.challegram.component.user;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Looper;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ComplexReceiverProvider;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccentColor;
import org.thunderdog.challegram.telegram.TdlibContactManager;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.DrawModifier;
import org.thunderdog.challegram.util.EmojiStatusHelper;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSetOverride;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.widget.BaseView;
import org.thunderdog.challegram.widget.SimplestCheckBoxHelper;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;

public class UserView extends BaseView implements Destroyable, RemoveHelper.RemoveDelegate, TooltipOverlayView.LocationProvider, ComplexReceiverProvider {
  /*private static final int ACCENT_COLOR = 0xff569ace;
  private static final int DECENT_COLOR = 0xff8a8a8a;*/

  private static TextPaint statusPaint;
  // private static Bitmap icon;

  private static int paddingRight, avatarRadius, textLeftMargin;
  private static int nameTop, statusTop, lettersTop;
  private int height = Screen.dp(72);

  private @Nullable TGUser user;
  private final AvatarReceiver avatarReceiver;
  private final ComplexReceiver complexReceiver;

  private final EmojiStatusHelper emojiStatusHelper;
  private int offsetLeft;

  public static final float DEFAULT_HEIGHT = 72f;

  private final RemoveHelper removeHelper;

  public UserView (Context context, Tdlib tdlib) {
    super(context, tdlib);
    offsetLeft = Screen.dp(68f);

    if (statusPaint == null) {
      initPaints();
    }

    if (avatarRadius == 0) {
      avatarRadius = Screen.dp(25f);

      paddingRight = Screen.dp(16f);
      textLeftMargin = avatarRadius * 2 + Screen.dp(11f);

      nameTop = Screen.dp(20f) + Screen.dp(12f);
      statusTop = Screen.dp(40f) + Screen.dp(12f);
      lettersTop = Screen.dp(30f) + Screen.dp(12f);
    }

    avatarReceiver = new AvatarReceiver(this);
    complexReceiver = new ComplexReceiver(this);
    emojiStatusHelper = new EmojiStatusHelper(tdlib, this, null);
    layoutReceiver();

    removeHelper = new RemoveHelper(this, R.drawable.baseline_remove_circle_24);
  }

  public void setHeight (int height) {
    this.height = height;
  }

  private void layoutReceiver () {
    int centerY = height / 2;
    if (Lang.rtl()) {
      int viewWidth = getMeasuredWidth();
      avatarReceiver.setBounds(viewWidth - offsetLeft - avatarRadius - avatarRadius, centerY - avatarRadius, viewWidth - offsetLeft, centerY + avatarRadius);
    } else {
      avatarReceiver.setBounds(offsetLeft, centerY - avatarRadius, offsetLeft + avatarRadius * 2, centerY + avatarRadius);
    }
  }

  public void setOffsetLeft (int offset) {
    if (this.offsetLeft != offset) {
      this.offsetLeft = offset;
      int centerY = height / 2;
      avatarReceiver.setBounds(offsetLeft, centerY - avatarRadius, offsetLeft + avatarRadius * 2, centerY + avatarRadius);
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    layoutReceiver();
  }

  public void attachReceiver () {
    complexReceiver.attach();
    emojiStatusHelper.attach();
    avatarReceiver.attach();
  }

  public void detachReceiver () {
    complexReceiver.detach();
    emojiStatusHelper.detach();
    avatarReceiver.detach();
  }

  @Override
  public ComplexReceiver getComplexReceiver () {
    return complexReceiver;
  }

  @Override
  public void getTargetBounds (View targetView, Rect outRect) {
    if (user != null && trimmedName != null) {
      trimmedName.toRect(outRect);
    }
  }

  public void updateSubtext () {
    if (user != null) {
      user.updateStatus();
    }
    String status;
    float statusWidth;
    if (user != null) {
      status = user.getStatus();
      statusWidth = user.getStatusWidth();
    } else if (unregisteredContact != null) {
      status = unregisteredContact.getStatus();
      statusWidth = U.measureText(status, statusPaint);
    } else {
      status = null;
      statusWidth = 0;
    }
    float availWidth = getMeasuredWidth() - textLeftMargin - offsetLeft - paddingRight - (unregisteredContact != null ? Screen.dp(32f) : 0);
    if (drawModifiers != null) {
      int maxWidth = 0;
      for (DrawModifier modifier : drawModifiers) {
        maxWidth = Math.max(maxWidth, modifier.getWidth());
      }
      availWidth -= maxWidth;
    }
    if (availWidth > 0) {
      sourceStatus = status;
      if (statusWidth > availWidth) {
        trimmedStatus = TextUtils.ellipsize(sourceStatus, statusPaint, availWidth, TextUtils.TruncateAt.END).toString();
        trimmedStatusWidth = U.measureText(trimmedStatus, statusPaint);
      } else {
        trimmedStatus = sourceStatus;
        trimmedStatusWidth = statusWidth;
      }
    }
    if (Looper.myLooper() == Looper.getMainLooper()) {
      invalidate();
    } else {
      postInvalidate();
    }
  }

  public void updateName () {
    if (user != null) {
      user.updateName();
    }
    String name;
    if (user != null) {
      name = user.getName();
    } else if (unregisteredContact != null) {
      name = unregisteredContact.getName();
    } else {
      name = null;
    }
    float availWidth = getMeasuredWidth() - textLeftMargin - offsetLeft - paddingRight - (unregisteredContact != null ? Screen.dp(32f) : 0);
    if (drawModifiers != null) {
      int maxWidth = 0;
      for (DrawModifier modifier : drawModifiers) {
        maxWidth = Math.max(maxWidth, modifier.getWidth());
      }
      availWidth -= maxWidth - Screen.dp(12);
    }
    emojiStatusHelper.updateEmoji(user != null ? user.getUser() : null, new TextColorSetOverride(TextColorSets.Regular.NORMAL) {
      @Override
      public long mediaTextComplexColor () {
        return Theme.newComplexColor(true, ColorId.iconActive);
      }
    });
    if (emojiStatusHelper.needDrawEmojiStatus()) {
      availWidth -= emojiStatusHelper.getWidth() + Screen.dp(6);
    }

    if (availWidth > 0) {
      sourceName = name;
      trimmedName = StringUtils.isEmpty(name) ? null : new Text.Builder(name, (int) availWidth, Paints.robotoStyleProvider(16), TextColorSets.Regular.NORMAL).singleLine().allBold().build();
    }
  }

  public void updateAll () {
    updateName();
    updateSubtext();
    updateLetters();
    if (Looper.getMainLooper() == Looper.myLooper()) {
      invalidate();
    } else {
      postInvalidate();
    }
  }

  private @Nullable TdlibContactManager.UnregisteredContact unregisteredContact;

  public void setContact (TdlibContactManager.UnregisteredContact contact) {
    if (this.user != null || unregisteredContact != contact) {
      this.user = null;
      this.unregisteredContact = contact;
      buildLayout();
      avatarReceiver.requestPlaceholder(tdlib, avatarPlaceholder, AvatarReceiver.Options.NONE);
    }
  }

  public void setUser (@NonNull TGUser user) {
    if (unregisteredContact != null || !user.equals(this.user)) {
      this.user = user;
      this.unregisteredContact = null;
      buildLayout();
    } else {
      if (sourceName == null || user.updateName() || !sourceName.equals(user.getName())) {
        updateName();
      }
      if (sourceStatus == null || user.updateStatus() || !sourceStatus.equals(user.getStatus())) {
        updateSubtext();
      }
    }
    if (user.isChat()) {
      avatarReceiver.requestChat(tdlib, user.getChatId(), AvatarReceiver.Options.SHOW_ONLINE);
    } else {
      avatarReceiver.requestUser(tdlib, user.getUserId(), AvatarReceiver.Options.SHOW_ONLINE);
    }
  }

  public @Nullable TGUser getUser () {
    return user;
  }

  private AvatarPlaceholder.Metadata avatarPlaceholder;
  private String sourceName;
  private Text trimmedName;
  private String sourceStatus, trimmedStatus;
  private float trimmedStatusWidth;

  private void updateLetters () {
    if (unregisteredContact != null) {
      avatarPlaceholder = new AvatarPlaceholder.Metadata(tdlib.accentColor(TdlibAccentColor.InternalId.INACTIVE), unregisteredContact.letters);
    } else {
      avatarPlaceholder = null;
    }
  }

  private void buildLayout () {
    int totalWidth = getMeasuredWidth();
    if (totalWidth > 0) {
      updateName();
      updateSubtext();
      updateLetters();
    }
  }

  @Override
  protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
    if (changed) {
      buildLayout();
    }
  }

  @Override
  public void performDestroy () {
    complexReceiver.performDestroy();
    emojiStatusHelper.performDestroy();
    avatarReceiver.destroy();
  }

  @Override
  protected void onDraw (Canvas c) {
    removeHelper.save(c);
    int centerY = height / 2;
    boolean rtl = Lang.rtl();
    int viewWidth = getMeasuredWidth();
    if (trimmedName != null) {
      trimmedName.draw(c, offsetLeft + textLeftMargin, (int) ((height - Screen.dp(DEFAULT_HEIGHT)) / 2f + Screen.dp(17f)));
    }
    emojiStatusHelper.draw(c, offsetLeft + textLeftMargin + (trimmedName != null ? trimmedName.getWidth() : 0) + Screen.dp(6f), (int) ((height - Screen.dp(DEFAULT_HEIGHT)) / 2f + Screen.dp(17f)));
    if (trimmedStatus != null) {
      statusPaint.setColor(Theme.getColor(user != null && user.isOnline() ? ColorId.textNeutral : ColorId.textLight));
      c.drawText(trimmedStatus, rtl ? viewWidth - offsetLeft - textLeftMargin - trimmedStatusWidth : offsetLeft + textLeftMargin,
        statusTop + (height - Screen.dp(DEFAULT_HEIGHT)) / 2f, statusPaint);
    }
    if (user != null || unregisteredContact != null) {
      float checkFactor = checkBoxHelper != null ? checkBoxHelper.getCheckFactor() : 0f;
      avatarReceiver.forceAllowOnline(checkBoxHelper == null || !checkBoxHelper.isChecked(), 1f - checkFactor);
      layoutReceiver();
      if (avatarReceiver.needPlaceholder()) {
        avatarReceiver.drawPlaceholder(c);
      }
      avatarReceiver.draw(c);
    }
    if (unregisteredContact != null) {
      int x = getMeasuredWidth() - Screen.dp(21f);
      int width = Screen.dp(14f);
      int height = Screen.dp(2f);
      Paint paint = Paints.fillingPaint(Theme.getColor(ColorId.iconActive));

      if (rtl) {
        int x1, x2;
        x1 = x - width;
        x2 = x;
        c.drawRect(viewWidth - x2, centerY - height / 2f, viewWidth - x1, centerY + height / 2f + height % 2, paint);
        x1 = x - width / 2 - height / 2;
        x2 = x - width / 2 + height / 2 + height % 2;
        c.drawRect(viewWidth - x2, centerY - width / 2f, viewWidth - x1, centerY + width / 2f + width % 2, paint);
      } else {
        c.drawRect(x - width, centerY - height / 2f, x, centerY + height / 2f + height % 2, paint);
        c.drawRect(x - width / 2f - height / 2f, centerY - width / 2f, x - width / 2f + height / 2f + height % 2, centerY + width / 2f + width % 2, paint);
      }
    }

    if (checkBoxHelper != null) {
      DrawAlgorithms.drawSimplestCheckBox(c, avatarReceiver, checkBoxHelper.getCheckFactor());
    }

    removeHelper.restore(c);
    removeHelper.draw(c);

    if (user != null && user.needSeparator()) {
      int top = getMeasuredHeight() - Math.max(1, Screen.dp(.5f));
      int bottom = getMeasuredHeight();
      if (offsetLeft + textLeftMargin > 0) {
        if (rtl) {
          c.drawRect(viewWidth - offsetLeft - textLeftMargin, top, viewWidth, bottom, Paints.fillingPaint(Theme.fillingColor()));
        } else {
          c.drawRect(0, top, offsetLeft + textLeftMargin, bottom, Paints.fillingPaint(Theme.fillingColor()));
        }
      }
      if (rtl) {
        c.drawRect(0,  top, viewWidth - offsetLeft - textLeftMargin, bottom, Paints.fillingPaint(Theme.separatorColor()));
      } else {
        c.drawRect(offsetLeft + textLeftMargin, top, getMeasuredWidth(), bottom, Paints.fillingPaint(Theme.separatorColor()));
      }
    }

    if (drawModifiers != null) {
      for (int i = drawModifiers.size() - 1; i >= 0; i--) {
        drawModifiers.get(i).afterDraw(this, c);
      }
    }
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

  // Selection

  private SimplestCheckBoxHelper checkBoxHelper;

  public void setChecked (boolean isChecked, boolean animated) {
    if (checkBoxHelper == null) {
      checkBoxHelper = new SimplestCheckBoxHelper(this);
    }
    checkBoxHelper.setIsChecked(isChecked, animated);
  }

  @Override
  public void setRemoveDx (float dx) {
    removeHelper.setDx(dx);
  }

  @Override
  public void onRemoveSwipe () {
    removeHelper.onSwipe();
  }

  // Paints

  private static void initPaints () {
    statusPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    statusPaint.setTypeface(Fonts.getRobotoRegular());
    statusPaint.setTextSize(Screen.dp(14f));
    statusPaint.setColor(Theme.textDecentColor());
    ThemeManager.addThemeListener(statusPaint, ColorId.textLight);
  }

  public static TextPaint getStatusPaint () {
    if (statusPaint == null) {
      initPaints();
    }
    return statusPaint;
  }
}
