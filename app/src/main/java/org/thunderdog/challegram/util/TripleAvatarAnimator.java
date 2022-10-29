package org.thunderdog.challegram.util;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import java.util.ArrayList;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.ListAnimator;
import me.vkryl.android.util.ViewProvider;
import me.vkryl.td.Td;

public class TripleAvatarAnimator {
  private final Tdlib tdlib;
  private final ViewProvider viewProvider;
  private final ListAnimator<Entry> animator;
  private final Paint clearPaint;

  public TripleAvatarAnimator (Tdlib tdlib, ViewProvider viewProvider) {
    this.tdlib = tdlib;
    this.viewProvider = viewProvider;
    animator = new ListAnimator<>((a) -> viewProvider.invalidate(), AnimatorUtils.DECELERATE_INTERPOLATOR, 200L);
    clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    clearPaint.setColor(0);
    clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
  }

  public void setReplyInfo (@Nullable TdApi.MessageReplyInfo replyInfo, boolean animated) {
    if (replyInfo == null) {
      animator.clear(animated);
      viewProvider.invalidate();
      return;
    }

    ArrayList<Entry> entries = new ArrayList<>();
    for (TdApi.MessageSender sender: replyInfo.recentReplierIds) {
      entries.add(new Entry(tdlib, sender));
    }

    animator.reset(entries, animated);
    viewProvider.invalidate();
  }

  public void requestCommentAvatars (ComplexReceiver complexReceiver) {
    for (int i = 0; i < animator.size(); i++) {
      ListAnimator.Entry<Entry> item = animator.getEntry(i);
      item.item.update(complexReceiver.getImageReceiver(item.hashCode()));
    }
    viewProvider.invalidate();
  }

  public void draw (Canvas c, ComplexReceiver complexReceiver, int sx, int sy) {
    c.saveLayerAlpha(sx - Screen.dp(80), sy - Screen.dp(15), sx + Screen.dp(15), sy + Screen.dp(15), 255, Canvas.ALL_SAVE_FLAG);

    final int spacing = Screen.dp(16f);
    for (int i = animator.size() - 1; i >= 0; i--) {
      ListAnimator.Entry<Entry> item = animator.getEntry(i);
      final float alpha = item.getVisibility();
      final float x = item.getPosition() * spacing;

      if (alpha != 1f) {
        c.save();
        c.scale(alpha, alpha, sx - x, sy);
      }
      c.drawCircle(sx - x, sy, Screen.dp(12f), clearPaint);
      item.item.draw(c, complexReceiver.getImageReceiver(item.hashCode()), sx - x, sy);
      if (alpha != 1f) {
        c.restore();
      }
    }

    c.restore();
  }

  public float getWidth () {
    return animator.getMetadata().getTotalWidth();
  }

  public float getVisibility () {
    return animator.getMetadata().getVisibility();
  }

  private static class Entry implements ListAnimator.Measurable {
    public final TdApi.MessageSender sender;
    private AvatarPlaceholder avatarPlaceholder;
    private final Tdlib tdlib;
    private boolean hasPhoto;

    public Entry (Tdlib tdlib, TdApi.MessageSender sender) {
      this.sender = sender;
      this.tdlib = tdlib;
    }

    public void update (ImageReceiver imageReceiver) {
      if (sender.getConstructor() == TdApi.MessageSenderUser.CONSTRUCTOR) {
        TdApi.User user = tdlib.chatUser(((TdApi.MessageSenderUser) sender).userId);
        if (user != null && !TD.isPhotoEmpty(user.profilePhoto)) {
          imageReceiver.requestFile(new ImageFile(tdlib, user.profilePhoto.small));
          hasPhoto = true;
        } else {
          hasPhoto = false;
          imageReceiver.clear();
          AvatarPlaceholder.Metadata metadata = tdlib.cache().userPlaceholderMetadata(user, false);
          if (metadata != null) {
            avatarPlaceholder = new AvatarPlaceholder(10, metadata, null);
          } else {
            avatarPlaceholder = null;
          }
        }
      } else if (sender.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR) {
        TdApi.Chat chat = tdlib.chat(((TdApi.MessageSenderChat) sender).chatId);
        if (chat != null && chat.photo != null && chat.photo.small != null) {
          imageReceiver.requestFile(new ImageFile(tdlib, chat.photo.small));
          hasPhoto = true;
        } else {
          hasPhoto = false;
          imageReceiver.clear();
          AvatarPlaceholder.Metadata metadata = tdlib.chatPlaceholderMetadata(chat, false);
          if (metadata != null) {
            avatarPlaceholder = new AvatarPlaceholder(10, metadata, null);
          } else {
            avatarPlaceholder = null;
          }
        }
      } else {
        hasPhoto = false;
        imageReceiver.clear();
        avatarPlaceholder = null;
      }
    }

    public void draw (Canvas c, ImageReceiver imageReceiver, float x, float y) {
      final float r = Screen.dp(10);
      if (hasPhoto && imageReceiver != null) {
        imageReceiver.setBounds((int) (x - r), (int) (y - r), (int) (x + r), (int) (y + r));
        imageReceiver.setRadius((int) r);
        imageReceiver.draw(c);
      } else if (avatarPlaceholder != null) {
        avatarPlaceholder.draw(c, x, y);
      } else {
        c.drawCircle(x, y, r, Paints.fillingPaint(Theme.placeholderColor()));
      }
    }

    @Override
    public boolean equals (Object obj) {
      return obj instanceof Entry && Td.getSenderId(((Entry) obj).sender) == Td.getSenderId(sender);
    }

    @Override
    public int hashCode () {
      return (int) (Td.getSenderId(sender));
    }

    @Override
    public int getSpacingStart (boolean isFirst) {
      return 0; // isFirst ? 0 : Screen.dp(-4);
    }

    @Override
    public int getSpacingEnd (boolean isLast) {
      return isLast ? 0 : Screen.dp(-4);
    }

    @Override
    public int getWidth () {
      return Screen.dp(20);
    }

    @Override
    public int getHeight () {
      return Screen.dp(20);
    }
  }
}
