package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.view.MotionEvent;
import android.view.View;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarInfo;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.ui.MainController;

import java.util.Arrays;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ClickHelper;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;

/**
 * Date: 12/14/17
 * Author: default
 */

public class JoinedUsersView extends View implements Destroyable, FactorAnimator.Target, ClickHelper.Delegate {
  private final Tdlib tdlib;

  private static final int ANIMATOR_BASE = 0;

  private final FactorAnimator animator;
  private final ImageReceiver[] receivers;

  private Drawable icon;

  private float factor;

  private CharSequence inviteText;
  private CharSequence joinedText;

  private StaticLayout inviteLayout, joinedLayout;

  private int lastAvailWidth;

  private final ClickHelper helper;

  public JoinedUsersView (Context context, Tdlib tdlib) {
    super(context);
    this.tdlib = tdlib;
    this.helper = new ClickHelper(this);
    this.helper.setNoSound(true);
    this.animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, ICON_DURATION);
    this.icon = Drawables.get(getResources(), R.drawable.baseline_forum_96);
    this.receivers = new ImageReceiver[5];

    long[] userIds = tdlib.contacts().getRegisteredUserIds();
    int count = tdlib.contacts().getAvailableRegisteredCount();

    if (userIds != null) {
      setUserIdsImpl(userIds, count, false);
    }

    this.inviteText = Strings.replaceBoldTokens(Lang.getString(R.string.NoChatsText), ThemeColorId.NONE);

    tdlib.contacts().addAvatarExpector(this);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    int width = MeasureSpec.getSize(widthMeasureSpec);
    int availWidth = Math.max(0, width - Screen.dp(12f) * 2);
    if (this.lastAvailWidth != availWidth) {
      this.lastAvailWidth = availWidth;
      layoutTexts(availWidth);
    }
    int height = calculateHeight();
    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
  }

  private int calculateHeight () {
    return Screen.dp(92f) + Math.max(Screen.dp(84f), Math.max(inviteLayout != null ? inviteLayout.getHeight() + Screen.dp(26f) : 0, joinedLayout != null ? joinedLayout.getHeight() + Screen.dp(36f) : 0));
  }

  public void setJoinedText (CharSequence text) {
    if ((this.joinedText == null && text != null) || !(this.joinedText != null && !this.joinedText.equals(text))) {
      this.joinedText = text;
      if (lastAvailWidth > 0) {
        setJoinedTextImpl(lastAvailWidth);
        if (getMeasuredHeight() != calculateHeight()) {
          requestLayout();
        }
        invalidate();
      }
    }
  }

  private void setJoinedTextImpl (int availWidth) {
    this.joinedLayout = joinedText != null ? (new StaticLayout(joinedText, Paints.getTextPaint15(), availWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, Screen.dp(2f), false)) : null;
  }

  private void layoutTexts (int availWidth) {
    if (availWidth > 0) {
      this.inviteLayout = inviteText != null ? (new StaticLayout(inviteText, Paints.getTextPaint15(), availWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, Screen.dp(2f), false)) : null;
      setJoinedTextImpl(availWidth);
    } else {
      this.inviteLayout = this.joinedLayout = null;
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_BASE: {
        this.factor = factor;
        invalidate();
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_BASE: {
        if (finalFactor == 0f) {
          for (ImageReceiver receiver : receivers) {
            if (receiver != null) {
              receiver.requestFile(null);
            }
          }
          userIds = null;
        }
        break;
      }
    }
  }

  private long[] userIds;
  private AvatarInfo[] avatarInfos;
  private String moreCounter;

  private static final float AVATAR_RADIUS = 18f;
  private static final float AVATAR_SPACING = 6f;

  private boolean isAttached = true;

  public void attach () {
    isAttached = true;
    for (ImageReceiver receiver : receivers) {
      if (receiver == null) {
        break;
      }
      receiver.attach();
    }
  }

  public void detach () {
    isAttached = false;
    for (ImageReceiver receiver : receivers) {
      if (receiver == null) {
        break;
      }
      receiver.detach();
    }
  }

  private ImageReceiver newReceiver () {
    ImageReceiver receiver = new ImageReceiver(this, Screen.dp(AVATAR_RADIUS));
    if (!isAttached) {
      receiver.detach();
    }
    return receiver;
  }

  private void invalidateAvatars () {
    if (userIds == null) {
      return;
    }
    int maxIndex = userIds.length - (moreCounter != null ? 1 : 0);
    for (int i = 0; i < maxIndex; i++) {
      AvatarInfo info = avatarInfos[i];
      ImageReceiver receiver = receivers[i];
      if (info != null && info.imageFile != null) {
        if (receiver == null) {
          receiver = receivers[i] = newReceiver();
        }
        receiver.requestFile(info.imageFile);
      } else if (receiver != null) {
        receiver.requestFile(null);
      }
    }
  }

  private static final float COUNTER_MEDIUM_DP = 17f;

  private float counterWidth;

  private void setCounter (String counter) {
    this.moreCounter = counter;
    this.counterWidth = U.measureText(counter, Paints.whiteMediumPaint(COUNTER_MEDIUM_DP, false, true));
  }

  private boolean isVisible;

  public void setUserIds (long[] userIds, int totalCount, boolean animated) {
    setUserIdsImpl(userIds, totalCount, animated && getMeasuredWidth() > 0 && getMeasuredHeight() > 0 && parent != null && parent.isFocused());
  }

  private void setUserIdsImpl (long[] userIds, int totalCount, boolean animated) {
    String counter;
    if (totalCount > 5) {
      counter = "+" + (totalCount - userIds.length + 1);
    } else {
      counter = null;
    }
    if (userIds != null && userIds.length == 0) {
      userIds = null;
    }
    boolean isVisible = userIds != null;
    if (this.isVisible != isVisible || !Arrays.equals(this.userIds, userIds)) {
      if (userIds == null) {
        this.isVisible = false;
        animator.setDuration(calculateDuration(this.userIds, true));
        if (animated) {
          animator.animateTo(0f);
        } else {
          animator.forceFactor(0f);
          this.factor = 0f;
          invalidate();
        }
        return;
      }
      long duration = calculateDuration(userIds, true);
      this.userIds = userIds;
      if (this.avatarInfos == null || this.avatarInfos.length != userIds.length) {
        this.avatarInfos = new AvatarInfo[userIds.length];
      }
      int i = 0;
      for (long userId : userIds) {
        avatarInfos[i] = new AvatarInfo(tdlib, userId);
        i++;
      }
      setCounter(counter);
      invalidateAvatars();
      this.isVisible = true;
      animator.setDuration(duration);
      if (animated) {
        if (factor != 0f) {
          final float startFactor = (float) ((double) ICON_DURATION / (double) duration);
          animator.forceFactor(startFactor);
          this.factor = startFactor;
          invalidate();
        }
        animator.animateTo(1f);
      } else {
        animator.forceFactor(1f);
        this.factor = 1f;
        invalidate();
      }
    } else if (!StringUtils.equalsOrBothEmpty(this.moreCounter, counter)) {
      setCounter(counter);
      invalidate();
    }
  }

  private static long ICON_DURATION = 120l;
  private static long START_DELAY = 28l;
  private static long AVATAR_DURATION = 120l;

  private static long calculateDuration (long[] userIds, boolean includeIcon) {
    return (includeIcon ? ICON_DURATION : 0) + ((userIds.length - 1) * START_DELAY + AVATAR_DURATION);
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    /*if (e.getAction() == MotionEvent.ACTION_DOWN) {
      if (factor == 0f) {
        ContactManager.instance().startSyncIfNeeded(UI.getContext(getContext()), true, null);
        // animator.animateTo(1f);
      } else {
        ContactManager.instance().reset(true, null);
        // animator.animateTo(0f);
      }
    }*/
    return factor > 0f && helper.onTouchEvent(this, e);
  }

  @Override
  public boolean needClickAt (View view, float x, float y) {
    return avatarInfos != null && factor > 0f;
  }

  @Override
  public void onClickAt (View view, float x, float y) {
    int size = Screen.dp(AVATAR_RADIUS) * 2;
    if (avatarInfos == null || factor == 0f) {
      return;
    }

    int avatarRadius = Screen.dp(AVATAR_RADIUS);
    int avatarSpacing = Screen.dp(AVATAR_SPACING);
    int avatarCount = avatarInfos.length;
    int totalWidth = (avatarRadius * 2 * avatarCount) + (avatarSpacing * (avatarCount - 1));
    final int centerX = getMeasuredWidth() / 2;
    final int centerY = Screen.dp(92f) / 2 + Screen.dp(16f);
    int cx = centerX - totalWidth / 2 + avatarRadius;
    int maxAvatarCount = avatarCount - (moreCounter != null ? 1 : 0);

    for (int i = 0; i < maxAvatarCount; i++) {
      if (avatarInfos[i] == null) {
        continue;
      }
      long userId = avatarInfos[i].userId;
      if (x >= cx - avatarRadius && x <= cx + avatarRadius && y >= centerY - avatarRadius && y <= centerY + avatarRadius) {
        tdlib.ui().openPrivateChat(ViewController.findRoot(this), userId, null);
        ViewUtils.onClick(this);
        break;
      }
      cx += avatarRadius * 2 + avatarSpacing;
    }
  }

  // Drawing

  private void drawPlaceholder (Canvas c, AvatarInfo info, int cx, int cy, float factor) {
    c.drawCircle(cx, cy, Screen.dp(AVATAR_RADIUS), Paints.fillingPaint(ColorUtils.alphaColor(factor, Theme.getColor(info.avatarColorId))));
    Paint paint = Paints.whiteMediumPaint(15f, info.letters.needFakeBold, false);
    paint.setAlpha((int) (255f * factor));
    c.drawText(info.letters.text, cx - info.lettersWidth15dp / 2, cy + Screen.dp(5.5f), paint);
    paint.setAlpha(255);
  }

  @Override
  protected void onDraw (Canvas c) {
    if (icon == null) {
      return;
    }

    final int viewWidth = getMeasuredWidth();
    final int viewHeight = Screen.dp(92f);

    final int centerX = viewWidth / 2;
    int centerY = viewHeight / 2;
    int offsetY = Screen.dp(16f);

    if (factor < .5f) {
      if (inviteLayout != null) {
        c.save();
        c.translate(Screen.dp(12f), viewHeight + Screen.dp(14f));
        Paints.getTextPaint15(ColorUtils.alphaColor(1f - factor / .5f, Theme.textDecent2Color()));
        inviteLayout.draw(c);
        c.restore();
      }
    } else {
      if (joinedLayout != null) {
        c.save();
        c.translate(Screen.dp(12f), viewHeight + Screen.dp(20f));
        Paints.getTextPaint15(ColorUtils.alphaColor((factor - .5f) / .5f, Theme.textDecent2Color()));
        joinedLayout.draw(c);
        c.restore();
      }
    }

    if (factor == 0f) {
      Drawables.draw(c, icon, centerX - icon.getMinimumWidth() / 2, centerY - icon.getMinimumHeight() / 2, Paints.getBackgroundIconPorterDuffPaint());
      return;
    }

    final long totalDuration = animator.getDuration();
    final long time = (long) (factor * (float) totalDuration);

    if (time < ICON_DURATION) {
      float factor = 1f - ((float) time / (float) ICON_DURATION);
      Paint paint = Paints.getBackgroundIconPorterDuffPaint();
      final boolean saved = factor != 1f;
      if (saved) {
        paint.setAlpha((int) (255f * factor));
        c.save();
        float scale = .6f + .4f * factor;
        c.scale(scale, scale, centerX, centerY + offsetY);
      }
      Drawables.draw(c, icon, centerX - icon.getMinimumWidth() / 2, centerY - icon.getMinimumHeight() / 2, paint);
      if (saved) {
        c.restore();
        paint.setAlpha(255);
      }
      return;
    }

    if (avatarInfos == null) {
      return;
    }

    centerY += offsetY;

    int avatarRadius = Screen.dp(AVATAR_RADIUS);
    int avatarSpacing = Screen.dp(AVATAR_SPACING);
    int avatarCount = avatarInfos.length;
    int totalWidth = (avatarRadius * 2 * avatarCount) + (avatarSpacing * (avatarCount - 1));
    int cx = centerX - totalWidth / 2 + avatarRadius;

    int i = 0;
    for (AvatarInfo info : avatarInfos) {
      long startTime = ICON_DURATION + (START_DELAY * i);
      if (time < startTime) {
        break;
      }
      float factor = time < (startTime + AVATAR_DURATION) ? (float) (time - startTime) / (float) AVATAR_DURATION : 1f;
      float scale = .6f + .4f * factor;
      if (factor < 1f) {
        c.save();
        c.scale(scale, scale, cx, centerY);
      }
      if (i == 4 && moreCounter != null) {
        c.drawCircle(cx, centerY, avatarRadius, Paints.fillingPaint(ColorUtils.alphaColor(factor, Theme.getColor(R.id.theme_color_avatarSavedMessages))));
        Paint paint = Paints.whiteMediumPaint(COUNTER_MEDIUM_DP, false, false);
        paint.setAlpha((int) (255f * factor));
        int padding = Screen.dp(3f);
        float textScale = counterWidth > avatarRadius * 2 - padding ? (float) (avatarRadius * 2 - padding) / counterWidth : 1f;
        if (textScale != 1f) {
          c.save();
          c.scale(textScale, textScale, cx, centerY);
        }
        c.drawText(moreCounter, cx - counterWidth / 2, centerY + Screen.dp(7f), paint);
        if (textScale != 1f) {
          c.restore();
        }
        paint.setAlpha(255);
      } else if (info.imageFile == null) {
        drawPlaceholder(c, info, cx, centerY, factor);
      } else {
        ImageReceiver receiver = receivers[i];
        receiver.setBounds(cx - avatarRadius, centerY - avatarRadius, cx + avatarRadius, centerY + avatarRadius);
        if (receiver.needPlaceholder()) {
          if (info.letters != null && !TD.isFileLoaded(info.imageFile.getFile())) {
            drawPlaceholder(c, info, cx, centerY, factor);
          } else {
            Paint paint = Paints.getPlaceholderPaint();
            int alpha = paint.getAlpha();
            if (factor < 1f) {
              paint.setAlpha((int) ((float) alpha * factor));
            }
            c.drawCircle(cx, centerY, avatarRadius, paint);
            if (factor < 1f) {
              paint.setAlpha(alpha);
            }
          }
        }

        if (factor < 1f) {
          receiver.setPaintAlpha(receiver.getAlpha() * factor);
        }
        receiver.draw(c);
        if (factor < 1f) {
          receiver.restorePaintAlpha();
        }
      }
      if (factor < 1f) {
        c.restore();
      }
      cx += avatarRadius * 2 + avatarSpacing;
      i++;
    }

  }


  // MainController access

  private MainController parent;

  public void setParent (MainController c) {
    this.parent = c;
  }

  // Destructor

  @Override
  public void performDestroy () {
    if (icon != null) {
      icon = null;
    }
    if (receivers != null) {
      int i = 0;
      for (ImageReceiver receiver : receivers) {
        if (receiver == null) {
          break;
        }
        receivers[i].destroy();
        i++;
      }
    }
    tdlib.contacts().removeAvatarExpector(this);
  }
}
