/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 09/05/2015 at 12:47
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.FillingDrawable;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.telegram.GlobalAccountListener;
import org.thunderdog.challegram.telegram.GlobalCountersListener;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibBadgeCounter;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.widget.ExpanderView;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ScrimUtil;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ClickHelper;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;

public class DrawerHeaderView extends View implements Destroyable, GlobalAccountListener, GlobalCountersListener, FactorAnimator.Target, ClickHelper.Delegate, TGLegacyManager.EmojiLoadListener {
  private static final int DRAWER_ALPHA = 90;

  // private final TextPaint namePaint, phonePaint;

  private DoubleImageReceiver receiver;
  private DoubleImageReceiver receiver2;

  private DrawerController parent;

  private final Drawable gradient;
  private final ExpanderView expanderView;
  private final ClickHelper clickHelper;

  public DrawerHeaderView (Context context, DrawerController parent) {
    super(context);

    this.parent = parent;

    clickHelper = new ClickHelper(this);
    clickHelper.setNoSound(true);

    gradient = ScrimUtil.makeCubicGradientScrimDrawable(0xff000000, 2, Gravity.BOTTOM, false);
    gradient.setAlpha(DRAWER_ALPHA);

    expanderView = new ExpanderView(this);

    /// setTextColor(Theme.getColor(parent.getHeaderTextColorId()));
    this.receiver = new DoubleImageReceiver(this, 1);
    this.receiver2 = new DoubleImageReceiver(this, 1);
    this.receiver2.setAnimationDisabled(true);
    this.receiver.setRadius(0);
    this.receiver2.setRadius(0);

    TdlibAccount account = TdlibManager.instance().currentAccount();
    TdlibManager.instance().global().addAccountListener(this);
    TdlibManager.instance().global().addCountersListener(this);
    updateCounter();
    setUser(account);

    TGLegacyManager.instance().addEmojiListener(this);

    ViewUtils.setBackground(this, new FillingDrawable(R.id.theme_color_headerBackground) {
      @Override
      protected int getFillingColor () {
        return ColorUtils.compositeColor(super.getFillingColor(), Theme.getColor(R.id.theme_color_drawer));
      }
    });
  }

  @Override
  public void onEmojiUpdated (boolean isPackSwitch) {
    invalidate();
  }

  private int getTextColor (float factor) {
    return ColorUtils.fromToArgb(ColorUtils.compositeColor(Theme.headerTextColor(), Theme.getColor(R.id.theme_color_drawerText)), Theme.getColor(R.id.theme_color_white), factor);
  }

  // Clicks


  public ExpanderView getExpanderView () {
    return expanderView;
  }

  @Override
  public boolean needClickAt (View view, float x, float y) {
    return y >= getMeasuredHeight() - Screen.dp(54f);
  }

  @Override
  public void onClickAt (View view, float x, float y) {
    if (y >= getMeasuredHeight() - Screen.dp(54f)) {
      ViewUtils.onClick(this);
      parent.setShowAccounts(expanderView.toggleExpanded());
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    return clickHelper.onTouchEvent(this, event);
  }

  // Other

  @Override
  public void performDestroy () {
    TdlibManager.instance().global().removeAccountListener(this);
    TdlibManager.instance().global().removeCountersListener(this);
    TGLegacyManager.instance().removeEmojiListener(this);
  }

  @Override
  public void onTotalUnreadCounterChanged (@NonNull TdApi.ChatList chatList, boolean isReset) {
    updateCounter();
  }

  public void updateCounter () {
    TdlibBadgeCounter counter = TdlibManager.instance().getTotalUnreadBadgeCounter(TdlibManager.instance().currentAccount().id);
    expanderView.setUnreadCount(counter.getCount(), counter.isMuted(), parent.getShowFactor() > 0f);
  }

  @Override
  public void onAccountProfileChanged (TdlibAccount account, TdApi.User profile, boolean isCurrent, boolean isLoaded) {
    if (displayInfo != null && displayInfo.compareTo(account, false)) {
      setUser(account);
    }
  }

  @Override
  public void onAccountProfilePhotoChanged (TdlibAccount account, boolean big, boolean isCurrent) {
    if (displayInfo != null && displayInfo.compareTo(account, false)) {
      setUser(account);
    }
  }

  @Override
  public void onAccountSwitched (TdlibAccount newAccount, TdApi.User profile, int reason, TdlibAccount oldAccount) {
    setUser(newAccount);
    updateCounter();
  }

  private static int contentLeft () {
    return Screen.dp(16f);
  }

  private static int avatarTop () {
    return Screen.dp(17f) + HeaderView.getTopOffset();
  }

  private static int lettersTop () {
    return Screen.dp(57f) + HeaderView.getTopOffset();
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    int bottom = getMeasuredHeight();
    int top = bottom - Screen.dp(72f);
    int right = getMeasuredWidth();
    Rect rect = gradient.getBounds();
    if (rect.top != top || rect.bottom != bottom || rect.right != right) {
      gradient.setBounds(0, top, right, bottom);
    }
  }

  private DisplayInfo displayInfo;

  private static class DisplayInfo implements TextColorSet {
    private final DrawerHeaderView context;
    private final TdlibAccount account;

    private final long userId;
    private final String name, phone;
    private ImageFile avatar, avatarFull;
    private final AvatarPlaceholder avatarPlaceholder;

    private void setAvatar () {
      ImageFile imageFile = account.getAvatarFile(false);
      if (imageFile != null) {
        avatar = ImageFile.copyOf(imageFile);
        avatar.setSize(ChatView.getDefaultAvatarCacheSize());
        avatar.setScaleType(ImageFile.CENTER_CROP);

        ImageFile bigFile = account.getAvatarFile(true);
        bigFile = ImageFile.copyOf(bigFile != null ? bigFile : imageFile);
        avatarFull = bigFile;
        avatarFull.setScaleType(ImageFile.CENTER_CROP);
        int drawerWidth = context.parent.getWidth();
        if (drawerWidth < 512) {
          avatarFull.setSize(drawerWidth);
        }
      } else {
        avatar = avatarFull = null;
      }
    }

    public DisplayInfo (DrawerHeaderView context, TdlibAccount account) {
      this.context = context;
      this.account = account;

      userId = account.getKnownUserId();
      if (account.hasUserInfo()) {
        name = account.getName();
        if (Settings.instance().needHidePhoneNumber()) {
          phone = Strings.replaceNumbers(Strings.formatPhone(account.getPhoneNumber()));
        } else {
          phone = Strings.formatPhone(account.getPhoneNumber());
        }
      } else {
        name = Lang.getString(R.string.LoadingUser);
        phone = Lang.getString(R.string.LoadingPhone);
      }
      avatarPlaceholder = new AvatarPlaceholder(32f, account.getAvatarPlaceholderMetadata(), null);
      setAvatar();
    }

    private int trimmedWidth;
    private Text trimmedName, trimmedPhone;

    public void trim (int width) {
      int availWidth = width - contentLeft() * 2;
      if (availWidth <= 0) {
        return;
      }
      if (trimmedWidth == availWidth) {
        return;
      }
      this.trimmedWidth = availWidth;
      this.trimmedName = new Text.Builder(name, availWidth, Paints.robotoStyleProvider(15), this).singleLine().allBold().build();
      this.trimmedPhone = new Text.Builder(phone, availWidth, Paints.robotoStyleProvider(13), this).singleLine().allBold().build();
    }

    private static final int FLAG_EQUAL_NUMBERS = 1;
    private static final int FLAG_EQUAL_NAMES = 1 << 1;
    private int equalFlags;

    public void calculateDiff (DisplayInfo info) {
      int flags = 0;
      if (StringUtils.equalsOrBothEmpty(info.phone, this.phone))
        flags |= FLAG_EQUAL_NUMBERS;
      if (StringUtils.equalsOrBothEmpty(info.name, this.name))
        flags |= FLAG_EQUAL_NAMES;
      equalFlags = flags;
    }

    public boolean compareTo (TdlibAccount account, boolean checkUserId) {
      return account.isSameAs(this.account) && (!checkUserId || userId == account.getKnownUserId());
    }

    public static final int DRAW_MODE_REGULAR = 0;
    public static final int DRAW_MODE_IMAGES = 1;
    public static final int DRAW_MODE_TEXTS = 2;

    public void draw (Canvas c, DoubleImageReceiver receiver, int viewWidth, int viewHeight, float factor, float avatarFactor, float avatarAlphaFactor, int drawMode, boolean rtl, int equalFlags, boolean drawEqual) {
      int contentLeft = contentLeft();
      final int startRadius = Screen.dp(32f);
      final int startCx = rtl ? viewWidth - contentLeft - startRadius : contentLeft + startRadius;
      final int startCy = avatarTop() + startRadius;

      int targetCx = viewWidth / 2;
      int targetCy = viewHeight / 2;
      int targetRadius = viewWidth / 2 + viewWidth % 2;

      int radius = startRadius + (int) ((float) (targetRadius - startRadius) * avatarFactor);
      int cx = startCx + (int) ((float) (targetCx - startCx) * avatarFactor);
      int cy = startCy + (int) ((float) (targetCy - startCy) * avatarFactor);

      if (drawMode == DRAW_MODE_IMAGES || drawMode == DRAW_MODE_REGULAR) {
        final int cornerRadius = (int) ((float) startRadius * (1f - avatarFactor));
        final int avatarAlpha = (int) (255f * avatarAlphaFactor);

        int left = cx - radius;
        int top = cy - radius;
        int right = cx + radius;
        int bottom = cy + radius;

        if (avatar == null) {
          if (avatarPlaceholder != null) {
            if (radius == cornerRadius) {
              avatarPlaceholder.draw(c, cx, cy, avatarAlphaFactor, radius);
            } else {
              RectF rectF = Paints.getRectF();
              rectF.set(left, top, right, bottom);
              c.drawRoundRect(rectF, cornerRadius, cornerRadius, Paints.fillingPaint(ColorUtils.alphaColor(avatarAlphaFactor, Theme.getColor(avatarPlaceholder.metadata.colorId))));
              avatarPlaceholder.draw(c, cx, cy, avatarAlphaFactor, radius, false);
            }
          }
        } else {
          if (avatarAlphaFactor > 0f) {
            receiver.setPaintAlpha(avatarAlphaFactor);
            receiver.setBounds(left, top, right, bottom);
            receiver.setRadius(cornerRadius);
            receiver.draw(c);
            receiver.restorePaintAlpha();
          } else {
            receiver.setRadius(0);
          }
          if (drawMode != DRAW_MODE_IMAGES) {
            if (avatarFactor != 1f) {
              c.save();
              c.clipRect(left, top, right, bottom);
            }
            int alpha = (int) ((float) DRAWER_ALPHA * avatarFactor);
            Drawables.setAlpha(context.gradient, alpha);
            context.gradient.draw(c);
            if (avatarFactor != 1f) {
              c.restore();
            }
          }
        }
      }
      if (drawMode == DRAW_MODE_TEXTS || drawMode == DRAW_MODE_REGULAR) {
        this.lastAvatarFactor = avatarFactor;
        if (trimmedName != null) {
          trimmedName.draw(c, contentLeft, contentLeft + trimmedName.getWidth(), 0,  Screen.dp(97f) + HeaderView.getTopOffset(), null, (equalFlags & FLAG_EQUAL_NAMES) != 0 ? (drawEqual ? 1f : 0f) : factor);
        }
        if (trimmedPhone != null) {
          trimmedPhone.draw(c, contentLeft, contentLeft + trimmedPhone.getWidth(), 0, Screen.dp(119f) + HeaderView.getTopOffset(), null, (equalFlags & FLAG_EQUAL_NUMBERS) != 0 ? (drawEqual ? 1f : 0f) : factor);
        }
        /*c.drawText(trimmedName != null ? trimmedName : name.text, rtl ? (viewWidth - contentLeft - (trimmedName != null ? trimmedNameWidth : nameWidth)) : contentLeft, nameTop, context.namePaint(name.needFakeBold, avatarFactor, (equalFlags & FLAG_EQUAL_NAMES) != 0 ? (drawEqual ? 1f : 0f) : factor));
        c.drawText(trimmedPhone != null ? trimmedPhone : phone, rtl ? (viewWidth - contentLeft - (trimmedPhone != null ? trimmedPhoneWidth : phoneWidth)) : contentLeft, phoneTop, context.phonePaint(avatarFactor, (equalFlags & FLAG_EQUAL_NUMBERS) != 0 ? (drawEqual ? 1f : 0f) : factor));*/
      }
    }

    private float lastAvatarFactor;

    @Override
    public int defaultTextColor () {
      return context.getTextColor(lastAvatarFactor);
    }
  }

  private DisplayInfo displayInfoFuture;
  private float futureFactor;

  private static final long SWITCH_DURATION = 240l;
  public static final long SWITCH_CLOSE_DURATION = 290l;

  private FactorAnimator animator;

  public synchronized void setUser (final TdlibAccount account) {
    boolean animate = parent.getShowFactor() > 0f && this.displayInfo != null /*&& !displayInfo.compareTo(account, true)*/;
    DisplayInfo info = new DisplayInfo(this, account);
    info.trim(getMeasuredWidth());

    if (this.animator != null) {
      this.animator.cancel();
      applyFuture();
    }

    if (animate) {
      this.displayInfoFuture = info;
      this.displayInfoFuture.calculateDiff(this.displayInfo);
      receiver2.requestFile(info.avatar, info.avatarFull);

      animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, SWITCH_DURATION);
      animator.animateTo(1f);
    } else {
      this.displayInfo = info;
      receiver.requestFile(info.avatar, info.avatarFull);
      invalidate();
    }
  }

  @Override
  protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    if (displayInfo != null) {
      displayInfo.trim(getMeasuredWidth());
    }
  }

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    receiver.attach();
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    receiver.detach();
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (futureFactor != factor) {
      futureFactor = factor;
      invalidate();
    }
  }

  private void applyFuture () {
    displayInfo = displayInfoFuture;
    displayInfoFuture = null;
    DoubleImageReceiver temp = receiver2;
    receiver2 = receiver;
    receiver = temp;
    receiver2.requestFile(null, null);
    futureFactor = 0f;
    animator = null;
    invalidate();
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (finalFactor == 1f) {
      applyFuture();
    }
  }

  @Override
  public void onDraw (Canvas c) {
    boolean rtl = Lang.rtl();

    /* float darkFactor = Theme.getDarkFactor();
    if (darkFactor > 0f) {
      c.drawColor(U.alphaColor(darkFactor, 0x40000000));
    }*/

    final int viewWidth = getMeasuredWidth();
    final int viewHeight = getMeasuredHeight();
    // int textColor = getTextColor();
    float avatarFactor;
    if (displayInfoFuture == null || displayInfo == null) {
      if (displayInfo != null) {
        avatarFactor = displayInfo.avatar != null ? 1f : 0f;
        displayInfo.draw(c, receiver, viewWidth, viewHeight, 1f, avatarFactor, 1f, DisplayInfo.DRAW_MODE_REGULAR, rtl, 0, true);
      } else {
        avatarFactor = 0f;
      }
    } else {
      boolean avatarChanged = (displayInfo.avatar == null) != (displayInfoFuture.avatar == null);
      boolean allowGradient = avatarChanged || displayInfo.avatar == null;

      int drawMode = DisplayInfo.DRAW_MODE_REGULAR;
      if (!allowGradient) {
        displayInfo.draw(c, receiver, viewWidth, viewHeight, 1f - futureFactor, 1f, 1f, DisplayInfo.DRAW_MODE_IMAGES, rtl, 0, true);
        displayInfoFuture.draw(c, receiver2, viewWidth, viewHeight, futureFactor, 1f, futureFactor, DisplayInfo.DRAW_MODE_IMAGES, rtl, 0, true);
        drawMode = DisplayInfo.DRAW_MODE_TEXTS;
        gradient.setAlpha(DRAWER_ALPHA);
        gradient.draw(c);
      }

      avatarFactor = displayInfo.avatar != null ? (avatarChanged ? 1f - futureFactor : 1f) : (avatarChanged ? futureFactor : 0f);
      displayInfo.draw(c, receiver, viewWidth, viewHeight, 1f - futureFactor, avatarFactor, 1f, drawMode, rtl, displayInfoFuture.equalFlags, false);

      avatarFactor = displayInfoFuture.avatar != null ? (avatarChanged ? futureFactor : 1f) : (avatarChanged ? 1f - futureFactor : 0f);
      displayInfoFuture.draw(c, receiver2, viewWidth, viewHeight, futureFactor, avatarFactor, futureFactor, drawMode, rtl, displayInfoFuture.equalFlags, true);
    }

    expanderView.draw(c, rtl ? Screen.dp(54f) / 2 : viewWidth - Screen.dp(54f) / 2, viewHeight - Screen.dp(54f) / 2, getTextColor(avatarFactor));
  }
}
