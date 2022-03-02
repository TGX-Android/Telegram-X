package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageFileLocal;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.lambda.Destroyable;

public class DetachedChatHeaderView extends FrameLayout implements Destroyable {
  private final DoubleImageReceiver avatar = new DoubleImageReceiver(this, 0);
  private final boolean isLarge;

  public static int getHeight (boolean large) {
    return large ? HeaderView.getBigSize(false) : Screen.dp(96f);
  }

  public DetachedChatHeaderView (@NonNull Context context, boolean large) {
    super(context);
    this.isLarge = large;
    setWillNotDraw(false);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    avatar.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
  }

  public void bindWith (Tdlib tdlib, TdApi.ChatInviteLinkInfo linkInfo) {
    ImageFileLocal file = new ImageFileLocal(linkInfo.photo.minithumbnail);
    file.setSize(isLarge ? getHeight(true) : ChatView.getDefaultAvatarCacheSize());
    file.setDecodeSquare(true);
    file.setScaleType(ImageFile.CENTER_CROP);

    ImageFile fileNetwork = new ImageFile(tdlib, isLarge ? linkInfo.photo.big : linkInfo.photo.small);
    fileNetwork.setSize(isLarge ? getHeight(true) : ChatView.getDefaultAvatarCacheSize());
    fileNetwork.setScaleType(ImageFile.CENTER_CROP);

    avatar.requestFile(file, fileNetwork);
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
    avatar.draw(canvas);
  }
}
