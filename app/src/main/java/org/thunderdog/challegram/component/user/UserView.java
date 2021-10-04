/**
 * File created on 15/08/15 at 22:46
 * Copyright Vyacheslav Krylov, 2014
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
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibContactManager;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.widget.BaseView;
import org.thunderdog.challegram.widget.SimplestCheckBoxHelper;

import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;

public class UserView extends BaseView implements Destroyable, RemoveHelper.RemoveDelegate, TooltipOverlayView.LocationProvider {
  /*private static final int ACCENT_COLOR = 0xff569ace;
  private static final int DECENT_COLOR = 0xff8a8a8a;*/

  private static TextPaint statusPaint;
  // private static Bitmap icon;

  private static int paddingRight, avatarRadius, textLeftMargin;
  private static int nameTop, statusTop, lettersTop;

  private @Nullable TGUser user;
  private final ImageReceiver receiver;

  private int offsetLeft;

  public static final float HEIGHT = 72f;

  private RemoveHelper removeHelper;

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

    receiver = new ImageReceiver(this, avatarRadius);
    layoutReceiver();

    removeHelper = new RemoveHelper(this, R.drawable.baseline_remove_circle_24);
  }

  private void layoutReceiver () {
    int centerY = Screen.dp(HEIGHT) / 2;
    if (Lang.rtl()) {
      int viewWidth = getMeasuredWidth();
      receiver.setBounds(viewWidth - offsetLeft - avatarRadius - avatarRadius, centerY - avatarRadius, viewWidth - offsetLeft, centerY + avatarRadius);
    } else {
      receiver.setBounds(offsetLeft, centerY - avatarRadius, offsetLeft + avatarRadius * 2, centerY + avatarRadius);
    }
  }

  public void setOffsetLeft (int offset) {
    if (this.offsetLeft != offset) {
      this.offsetLeft = offset;
      int centerY = Screen.dp(HEIGHT) / 2;
      receiver.setBounds(offsetLeft, centerY - avatarRadius, offsetLeft + avatarRadius * 2, centerY + avatarRadius);
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec), MeasureSpec.makeMeasureSpec(Screen.dp(72f /*64f*/), MeasureSpec.EXACTLY));
    layoutReceiver();
  }

  public void attachReceiver () {
    receiver.attach();
  }

  public void detachReceiver () {
    receiver.detach();
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
    if (availWidth > 0) {
      sourceName = name;
      trimmedName = StringUtils.isEmpty(name) ? null : new Text.Builder(name, (int) availWidth, Paints.robotoStyleProvider(16), TextColorSets.Regular.NORMAL).singleLine().allBold().build();
    }
  }

  public void updateAll () {
    updateName();
    updateSubtext();
    updateLetters();
    receiver.requestFile(user != null ? user.getAvatar() : null);
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
    }
    receiver.requestFile(null);
  }

  public void setUser (@NonNull TGUser user) {
    if (unregisteredContact != null || !user.compare(this.user)) {
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
    receiver.requestFile(user.getAvatar());
  }

  public void setUserForced (@NonNull TGUser user) {
    this.user = user;
    this.unregisteredContact = null;
    buildLayout();
    receiver.requestFile(user.getAvatar());
  }

  public @Nullable TGUser getUser () {
    return user;
  }

  private AvatarPlaceholder avatarPlaceholder;
  private String sourceName;
  private Text trimmedName;
  private String sourceStatus, trimmedStatus;
  private float trimmedStatusWidth;

  private void updateLetters () {
    if (user != null && user.getAvatarPlaceholderMetadata() != null) {
      avatarPlaceholder = new AvatarPlaceholder(25f, user.getAvatarPlaceholderMetadata(), null);
    } else if (unregisteredContact != null) {
      avatarPlaceholder = new AvatarPlaceholder(25f, new AvatarPlaceholder.Metadata(R.id.theme_color_avatarInactive, unregisteredContact.letters.text), null);
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
    receiver.requestFile(null);
  }

  @Override
  protected void onDraw (Canvas c) {
    removeHelper.save(c);
    int centerY = Screen.dp(HEIGHT) / 2;
    boolean rtl = Lang.rtl();
    int viewWidth = getMeasuredWidth();
    if (trimmedName != null) {
      trimmedName.draw(c, offsetLeft + textLeftMargin, Screen.dp(17f));
    }
    if (trimmedStatus != null) {
      statusPaint.setColor(Theme.getColor(user != null && user.isOnline() ? R.id.theme_color_textNeutral : R.id.theme_color_textLight));
      c.drawText(trimmedStatus, rtl ? viewWidth - offsetLeft - textLeftMargin - trimmedStatusWidth : offsetLeft + textLeftMargin, statusTop, statusPaint);
    }
    if (user != null) {
      layoutReceiver();
      if (user.hasAvatar()) {
        if (receiver.needPlaceholder()) {
          receiver.drawPlaceholderRounded(c, avatarRadius);
        }
        receiver.draw(c);
      } else if (avatarPlaceholder != null) {
        avatarPlaceholder.draw(c, receiver.centerX(), receiver.centerY());
      }
    } else if (unregisteredContact != null) {
      layoutReceiver();
      if (avatarPlaceholder != null) {
        avatarPlaceholder.draw(c, receiver.centerX(), receiver.centerY());
      }

      int x = getMeasuredWidth() - Screen.dp(21f);
      int width = Screen.dp(14f);
      int height = Screen.dp(2f);
      Paint paint = Paints.fillingPaint(Theme.getColor(R.id.theme_color_iconActive));

      if (rtl) {
        int x1, x2;
        x1 = x - width;
        x2 = x;
        c.drawRect(viewWidth - x2, centerY - height / 2, viewWidth - x1, centerY + height / 2 + height % 2, paint);
        x1 = x - width / 2 - height / 2;
        x2 = x - width / 2 + height / 2 + height % 2;
        c.drawRect(viewWidth - x2, centerY - width / 2, viewWidth - x1, centerY + width / 2 + width % 2, paint);
      } else {
        c.drawRect(x - width, centerY - height / 2, x, centerY + height / 2 + height % 2, paint);
        c.drawRect(x - width / 2 - height / 2, centerY - width / 2, x - width / 2 + height / 2 + height % 2, centerY + width / 2 + width % 2, paint);
      }
    }

    if (checkBoxHelper != null) {
      DrawAlgorithms.drawSimplestCheckBox(c, receiver, checkBoxHelper.getCheckFactor());
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
  }

  // Selection

  private SimplestCheckBoxHelper checkBoxHelper;

  public void setChecked (boolean isChecked, boolean animated) {
    if (checkBoxHelper == null) {
      checkBoxHelper = new SimplestCheckBoxHelper(this, receiver);
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
    ThemeManager.addThemeListener(statusPaint, R.id.theme_color_textLight);
  }

  public static TextPaint getStatusPaint () {
    if (statusPaint == null) {
      initPaints();
    }
    return statusPaint;
  }
}
