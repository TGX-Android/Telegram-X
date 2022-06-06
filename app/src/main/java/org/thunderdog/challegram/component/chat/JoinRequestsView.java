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
 */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.widget.BaseView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.animator.ListAnimator;
import me.vkryl.android.animator.ReplaceAnimator;
import me.vkryl.android.util.SingleViewProvider;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.Destroyable;

public class JoinRequestsView extends BaseView implements Destroyable, ComplexReceiver.KeyFilter {
  private static final float AVATAR_RADIUS = 12f;
  private static final float AVATAR_OUTLINE = 4f;
  private static final float AVATAR_SPACING = 4f;

  private ListAnimator<UserEntry> joinRequestEntries;
  private FactorAnimator animator;
  private TdApi.ChatJoinRequestsInfo info;
  private Runnable onDismissRunnable;

  private final ComplexReceiver megaReceiver = new ComplexReceiver(this);
  private final RectF dismissRect = new RectF();

  private final Bitmap closeIcon;
  private final ReplaceAnimator<Text> title = new ReplaceAnimator<>(ignored -> invalidate(), AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

  public JoinRequestsView (Context context, Tdlib tdlib) {
    super(context, tdlib);
    setWillNotDraw(false);
    closeIcon = Drawables.toBitmap(Drawables.get(R.drawable.baseline_close_18));
    megaReceiver.attach();
  }

  @Override
  protected void onDraw (Canvas canvas) {
    super.onDraw(canvas);

    int textStartX = Screen.dp(AVATAR_RADIUS) + Screen.dp(24f);

    if (joinRequestEntries != null) {
      int cy = getMeasuredHeight() / 2;
      int baseCx = Screen.dp(AVATAR_RADIUS) + Screen.dp(12f);
      int spacing = Screen.dp(AVATAR_RADIUS) * 2 - Screen.dp(AVATAR_SPACING);
      canvas.saveLayerAlpha(0, 0, getMeasuredWidth(), getMeasuredHeight(), 255, Canvas.ALL_SAVE_FLAG);
      for (int index = joinRequestEntries.size() - 1; index >= 0; index--) {
        ListAnimator.Entry<UserEntry> item = joinRequestEntries.getEntry(index);
        float cx = baseCx + (item.getPosition() * spacing);
        item.item.draw(canvas, megaReceiver, cx, cy, item.getVisibility());
        textStartX += item.item.getWidth(item.getVisibility());
      }
      canvas.restore();
    }

    drawBitmap(canvas, closeIcon, getMeasuredWidth() - Screen.dp(20f), getMeasuredHeight() / 2);

    for (ListAnimator.Entry<Text> entry : title) {
      entry.item.draw(canvas, textStartX,  getMeasuredWidth(), 0, (getMeasuredHeight() / 2) - Screen.dp(9), null, entry.getVisibility());
    }
  }

  private static void drawBitmap (Canvas c, Bitmap d, int cx, int cy) {
    cx -= d.getWidth() / 2;
    cy -= d.getHeight() / 2;
    c.drawBitmap(d, cx, cy, Paints.getIconGrayPorterDuffPaint());
  }

  public void setOnDismissRunnable (Runnable onDismissRunnable) {
    this.onDismissRunnable = onDismissRunnable;
  }

  public void setInfo (TdApi.ChatJoinRequestsInfo info, boolean animated) {
    long[] ids = info.userIds;

    this.info = info;
    title.replace(new Text.Builder(Lang.plural(R.string.xJoinRequests, info.totalCount), Screen.dp(300f), Paints.robotoStyleProvider(16), TextColorSets.Regular.NEUTRAL).allBold().singleLine().build(), animated);
    updateTitleMaxWidth();
    setRequestInfo(ids, animated);

    if (animator == null) {
      animator = new FactorAnimator(0, (id, factor, fraction, callee) -> {
        if (joinRequestEntries != null) {
          joinRequestEntries.applyAnimation(factor);
        }
        invalidate();
      }, AnimatorUtils.DECELERATE_INTERPOLATOR, 280l);
    }

    animator.forceFactor(0f);
    animator.animateTo(1f);
  }

  public TdApi.ChatJoinRequestsInfo getInfo () {
    return info;
  }

  private void setRequestInfo (long[] userIds, boolean animated) {
    if (userIds != null && userIds.length > 0) {
      List<UserEntry> entries = new ArrayList<>(userIds.length);
      for (long userId : userIds) {
        UserEntry ue = new UserEntry(tdlib, userId);
        entries.add(ue);
        if (ue.avatarFile != null) {
          megaReceiver.getImageReceiver(userId).requestFile(ue.avatarFile);
        }
      }
      if (this.joinRequestEntries == null)
        this.joinRequestEntries = new ListAnimator<>(new SingleViewProvider(this));
      joinRequestEntries.reset(entries, animated);
    } else if (joinRequestEntries != null) {
      joinRequestEntries.clear(animated);
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    updateTitleMaxWidth();
    dismissRect.set(getMeasuredWidth() - Screen.dp(48f), 0, getMeasuredWidth(), getMeasuredHeight());
  }

  private void updateTitleMaxWidth () {
    if (!title.isEmpty() && getMeasuredWidth() > 0) {
      for (ListAnimator.Entry<Text> entry : title) {
        entry.item.changeMaxWidth(getMeasuredWidth() - Screen.dp(AVATAR_RADIUS * 4f) - Screen.dp(48f));
      }
    }
  }

  @Override
  public void performDestroy () {
    megaReceiver.performDestroy();
  }

  @Override
  public boolean filterKey (int receiverType, Receiver receiver, long key) {
    if (joinRequestEntries != null) {
      for (ListAnimator.Entry<UserEntry> recentVoter : joinRequestEntries) {
        if (recentVoter.item.userId == key) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public boolean needClickAt (View view, float x, float y) {
    return dismissRect.contains(x, y) || super.needClickAt(view, x, y);
  }

  @Override
  public void onClickAt (View view, float x, float y) {
    if (dismissRect.contains(x, y) && onDismissRunnable != null) {
      onDismissRunnable.run();
    } else {
      super.onClickAt(view, x, y);
    }
  }

  private static class UserEntry {
    private final long userId;
    private final ImageFile avatarFile;
    private final AvatarPlaceholder avatarPlaceholder;
    private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public UserEntry (Tdlib tdlib, long userId) {
      this.userId = userId;
      this.avatarFile = tdlib.cache().userAvatar(userId);
      if (this.avatarFile != null) {
        this.avatarFile.setSize(Screen.dp(AVATAR_RADIUS) * 2);
      }
      this.avatarPlaceholder = tdlib.cache().userPlaceholder(userId, false, AVATAR_RADIUS, null);

      clearPaint.setColor(0);
      clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    @Override
    public boolean equals (@Nullable Object obj) {
      return obj instanceof UserEntry && ((UserEntry) obj).userId == this.userId;
    }

    @Override
    public int hashCode() {
      return (int) (userId ^ (userId >>> 32));
    }

    public float getWidth (final float alpha) {
      if (alpha == 1f) {
        return Screen.dp(AVATAR_RADIUS) + Screen.dp(AVATAR_OUTLINE);
      }

      return (Screen.dp(AVATAR_RADIUS) + Screen.dp(AVATAR_OUTLINE)) * alpha;
    }

    public void draw (Canvas c, ComplexReceiver complexReceiver, float cx, float cy, final float alpha) {
      if (alpha == 0f)
        return;

      ImageReceiver receiver = avatarFile != null ? complexReceiver.getImageReceiver(userId) : null;
      int radius = Screen.dp(AVATAR_RADIUS);

      //c.drawRect(cx - radius, cy - radius, cx + radius, cy + radius, Paints.fillingPaint(Theme.getColor(R.id.theme_color_textNegative)));

      boolean needRestore = alpha != 1f;
      int restoreToCount;
      if (needRestore) {
        float scale = .5f + .5f * alpha;
        restoreToCount = Views.save(c);
        c.scale(scale, scale, cx, cy);
      } else {
        restoreToCount = -1;
      }

      c.drawCircle(cx, cy, radius + Screen.dp(AVATAR_OUTLINE) * alpha * .5f, clearPaint);

      if (receiver != null) {
        if (alpha != 1f)
          receiver.setPaintAlpha(receiver.getPaintAlpha() * alpha);
        receiver.setBounds((int) (cx - radius), (int) (cy - radius), (int) (cx + radius), (int) (cy + radius));
        if (receiver.needPlaceholder())
          receiver.drawPlaceholderRounded(c, radius, ColorUtils.alphaColor(alpha, Theme.placeholderColor()));
        receiver.setRadius(radius);
        receiver.draw(c);
        if (alpha != 1f)
          receiver.restorePaintAlpha();
      } else if (avatarPlaceholder != null) {
        avatarPlaceholder.draw(c, cx, cy, alpha);
      }

      if (needRestore) {
        Views.restore(c, restoreToCount);
      }
    }
  }
}
