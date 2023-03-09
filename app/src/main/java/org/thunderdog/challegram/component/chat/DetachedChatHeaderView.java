/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.Gravity;

import androidx.annotation.NonNull;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageFileLocal;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.widget.SparseDrawableView;

import me.vkryl.android.ScrimUtil;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.Destroyable;

public class DetachedChatHeaderView extends SparseDrawableView implements Destroyable {
  private final DoubleImageReceiver avatar = new DoubleImageReceiver(this, 0);

  private int avatarPlaceholderColor;
  private Drawable avatarPlaceholderDrawable;

  private final Drawable topShadow;
  private final Drawable bottomShadow;

  private Text title;
  private Text subtitle;

  public static int getViewHeight () {
    return (int) (HeaderView.getBigSize(false) * 0.7f);
  }

  public DetachedChatHeaderView (@NonNull Context context) {
    super(context);
    setWillNotDraw(false);
    topShadow = ScrimUtil.makeCubicGradientScrimDrawable(0x77000000, 2, Gravity.TOP, false);
    bottomShadow = ScrimUtil.makeCubicGradientScrimDrawable(0x99000000, 2, Gravity.BOTTOM, false);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    avatar.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
    title.changeMaxWidth(getMeasuredWidth() - Screen.dp(13f));
    subtitle.changeMaxWidth(getMeasuredWidth() - Screen.dp(13f));
    topShadow.setBounds(0, 0, getMeasuredWidth(), (int) ((float) HeaderView.getSize(false)) + Size.getHeaderPlayerSize());
    bottomShadow.setBounds(0, 0, getMeasuredWidth(), getBottomShadowSize());
  }

  private int getBottomShadowSize () {
    return (int) (((Screen.dp(28f) + Screen.dp(5f) + ((title.getHeight() + subtitle.getHeight()) * 1.3f) + Screen.dp(8f)) + Screen.dp(14f)) * (1f / .9f));
  }

  public void bindWith (Tdlib tdlib, TdApi.ChatInviteLinkInfo linkInfo) {
    boolean isChannel = TD.isChannel(linkInfo.type);

    title = new Text.Builder(linkInfo.title, getMeasuredWidth(), Paints.robotoStyleProvider(18), TextColorSets.WHITE)
      .singleLine()
      .clipTextArea()
      .allBold()
      .build();

    subtitle = new Text.Builder(Lang.pluralMembers(linkInfo.memberCount, 0, isChannel).toString(), getMeasuredWidth(), Paints.robotoStyleProvider(14), TextColorSets.WHITE).singleLine().build();

    if (linkInfo.photo != null) {
      ImageFileLocal file;

      if (linkInfo.photo.minithumbnail != null) {
        file = new ImageFileLocal(linkInfo.photo.minithumbnail);
        file.setSize(getHeight());
        file.setDecodeSquare(true);
        file.setScaleType(ImageFile.CENTER_CROP);
      } else {
        file = null;
      }

      ImageFile fileNetwork = new ImageFile(tdlib, linkInfo.photo.big);
      fileNetwork.setSize(getHeight());
      fileNetwork.setScaleType(ImageFile.CENTER_CROP);

      avatarPlaceholderDrawable = null;
      avatarPlaceholderColor = 0;
      avatar.requestFile(file, fileNetwork);
    } else {
      avatarPlaceholderDrawable = getSparseDrawable(isChannel ? R.drawable.baseline_bullhorn_56 : R.drawable.baseline_group_56, 0);
      avatarPlaceholderColor = TD.getAvatarColorId(linkInfo.chatId, tdlib.myUserId());
      avatar.clear();
    }
  }

  public void attach() {
    avatar.attach();
  }

  public void detach() {
    avatar.detach();
  }

  @Override
  public void performDestroy () {
    avatar.destroy();
  }

  @Override
  protected void onDraw (Canvas canvas) {
    if (avatarPlaceholderDrawable != null) {
      int cx = avatar.centerX();
      int cy = avatar.centerY();

      canvas.drawColor(Theme.getColor(avatarPlaceholderColor));
      float placeholderScale = (avatar.getWidth() / 2f / ((float) getMeasuredWidth() / 2f));
      canvas.save();
      canvas.scale(placeholderScale, placeholderScale, cx, cy);
      Drawables.draw(canvas, avatarPlaceholderDrawable, cx - avatarPlaceholderDrawable.getMinimumWidth() / 2f, cy - avatarPlaceholderDrawable.getMinimumHeight() / 2f, Paints.getPorterDuffPaint(ColorUtils.alphaColor(.3f, Color.WHITE)));
      canvas.restore();
    } else {
      avatar.draw(canvas);

      topShadow.setAlpha((int) (255f * .8f));
      topShadow.draw(canvas);

      canvas.save();
      int shadowTop = getMeasuredHeight() - getBottomShadowSize();
      canvas.translate(0, Math.max(shadowTop, topShadow.getBounds().bottom - Screen.dp(28f)));
      bottomShadow.setAlpha((int) (255f * .8f));
      bottomShadow.draw(canvas);
      canvas.restore();
    }

    float baseTextLeft = Screen.dp(13f);
    float baseSubtitleTop = title.getHeight() + Screen.dp(4f);
    float baseTitleTop = getMeasuredHeight() - (title.getHeight() + subtitle.getHeight() + Screen.dp(13f));

    canvas.save();
    canvas.translate(baseTextLeft, baseTitleTop);
    title.draw(canvas, 0, 0, null, 1f);
    subtitle.draw(canvas, 0, (int) baseSubtitleTop, null, 1f);
    canvas.restore();
  }
}
