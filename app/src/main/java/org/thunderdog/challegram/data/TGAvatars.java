package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.view.Gravity;

import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.core.util.ObjectsCompat;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.animator.ListAnimator;
import me.vkryl.android.util.ViewProvider;
import me.vkryl.core.ColorUtils;
import me.vkryl.td.ChatId;

public final class TGAvatars implements FactorAnimator.Target {
  private static final @Dimension(unit = Dimension.DP) float DEFAULT_AVATAR_RADIUS = 10f;
  private static final @Dimension(unit = Dimension.DP) float DEFAULT_AVATAR_OUTLINE = 2f;
  private static final @Dimension(unit = Dimension.DP) float DEFAULT_AVATAR_SPACING = -4f;

  private static final long ANIMATION_DURATION = 280l;

  private final @NonNull Tdlib tdlib;
  private final @NonNull Callback callback;

  private @Nullable ViewProvider viewProvider;
  private @Nullable ComplexReceiver complexReceiver;
  private @Nullable List<AvatarEntry> entries;
  private @Nullable ListAnimator<AvatarEntry> animator;
  private @Nullable FactorAnimator countAnimator;

  private @Dimension(unit = Dimension.DP) float avatarRadius = DEFAULT_AVATAR_RADIUS;
  private @Dimension(unit = Dimension.DP) float avatarOutline = DEFAULT_AVATAR_OUTLINE;
  private @Dimension(unit = Dimension.DP) float avatarSpacing = DEFAULT_AVATAR_SPACING;

  public interface Callback {
    void onSizeChanged ();
  }

  public TGAvatars (@NonNull Tdlib tdlib, @NonNull Callback callback, @Nullable ViewProvider viewProvider) {
    this.tdlib = tdlib;
    this.callback = callback;
    this.viewProvider = viewProvider;
  }

  public void setDimensions (float avatarRadius, float avatarOutline, float avatarSpacing) {
    this.avatarRadius = avatarRadius;
    this.avatarOutline = avatarOutline;
    this.avatarSpacing = avatarSpacing;
  }

  public void setViewProvider (@Nullable ViewProvider viewProvider) {
    this.viewProvider = viewProvider;
  }

  public void setComplexReceiver (@Nullable ComplexReceiver complexReceiver) {
    this.complexReceiver = complexReceiver;
    if (complexReceiver != null) {
      requestFiles();
    }
  }

  public void setChatIds (@Nullable long[] chatIds, boolean animated) {
    if (chatIds != null && chatIds.length > 0) {
      List<AvatarEntry> entries = new ArrayList<>(chatIds.length);
      for (long chatId : chatIds) {
        if (ChatId.getType(chatId) == TdApi.ChatTypePrivate.CONSTRUCTOR) {
          entries.add(AvatarEntry.fromUserId(tdlib, chatId, avatarRadius));
        } else {
          entries.add(AvatarEntry.fromChatId(tdlib, chatId, avatarRadius));
        }
      }
      setEntries(entries, animated);
    } else {
      setEntries(null, animated);
    }
  }

  public void setSenders (@Nullable TdApi.MessageSender[] senders, boolean animated) {
    if (senders != null && senders.length > 0) {
      List<AvatarEntry> entries = new ArrayList<>(senders.length);
      for (TdApi.MessageSender sender : senders) {
        entries.add(AvatarEntry.fromSender(tdlib, sender, avatarRadius));
      }
      setEntries(entries, animated);
    } else {
      setEntries(null, animated);
    }
  }

  private void setEntries (@Nullable List<AvatarEntry> entries, boolean animated) {
    if (ObjectsCompat.equals(entries, this.entries)) {
      return;
    }
    this.entries = entries;

    if (entries != null && !entries.isEmpty()) {
      requestFiles();
      if (countAnimator == null) {
        float initialFactor = animated ? 0f : entries.size();
        countAnimator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, ANIMATION_DURATION, initialFactor);
      }
      if (animated) {
        countAnimator.animateTo(entries.size());
      } else {
        countAnimator.forceFactor(entries.size());
      }
      if (animator == null) {
        animator = new ListAnimator<>((animator) -> {
          if (viewProvider != null) {
            viewProvider.invalidate();
          }
        }, AnimatorUtils.DECELERATE_INTERPOLATOR, ANIMATION_DURATION);
      }
      animator.reset(entries, animated);
    } else if (animator != null) {
      animator.clear(animated);
      if (countAnimator != null) {
        if (animated) {
          countAnimator.animateTo(0f);
        } else {
          countAnimator.forceFactor(0f);
        }
      }
    }
  }

  private void requestFiles () {
    if (complexReceiver == null) {
      return;
    }
    if (entries != null && !entries.isEmpty()) {
      for (AvatarEntry entry : entries) {
        ImageFile avatarFile = entry.avatarFile;
        if (avatarFile != null) {
          ImageReceiver imageReceiver = complexReceiver.getImageReceiver(entry.id);
          imageReceiver.setRadius(Screen.dp(avatarRadius));
          imageReceiver.requestFile(entry.avatarFile);
        }
      }
    }
  }

  public float getAnimatedWidth () {
    if (countAnimator == null) {
      return 0f;
    }
    float factor = countAnimator.getFactor();
    int avatarSize = Screen.dp(avatarRadius) * 2;
    if (factor < 1f) {
      return avatarSize * factor;
    }
    return avatarSize + (avatarSize + Screen.dp(avatarSpacing)) * (factor - 1f);
  }

  public void draw (@NonNull Canvas c, int x, int cy, int gravity, float alpha) {
    if (animator == null || animator.size() == 0 || alpha == 0f) {
      return;
    }

    int avatarRadius = Screen.dp(this.avatarRadius);
    int avatarSpacing = Screen.dp(this.avatarSpacing);
    int avatarOutline = Screen.dp(this.avatarOutline);
    float maxWidth = 0;
    for (ListAnimator.Entry<AvatarEntry> entry : animator) {
      float cx = avatarRadius + entry.getPosition() * (avatarRadius * 2 + avatarSpacing);
      maxWidth = Math.max(maxWidth, cx + avatarRadius);
    }
    int saveCount;
    boolean isRightGravity = (gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.RIGHT;
    if (isRightGravity) {
      saveCount = c.saveLayerAlpha(x - maxWidth - avatarOutline, cy - avatarRadius - avatarOutline, x + avatarOutline, cy + avatarRadius + avatarOutline, 255, Canvas.ALL_SAVE_FLAG);
    } else {
      saveCount = c.saveLayerAlpha(x - avatarOutline, cy - avatarRadius - avatarOutline, x + maxWidth + avatarOutline, cy + avatarRadius + avatarOutline, 255, Canvas.ALL_SAVE_FLAG);
    }
    for (int index = animator.size() - 1; index >= 0; index--) {
      ListAnimator.Entry<AvatarEntry> entry = animator.getEntry(index);
      float visibility = entry.getVisibility();
      if (visibility == 0f) {
        continue;
      }
      float dx = avatarRadius + entry.getPosition() * (avatarRadius * 2 + avatarSpacing);
      int cx = Math.round(isRightGravity ? x - dx : x + dx);
      entry.item.draw(c, complexReceiver, cx, cy, avatarRadius, avatarOutline, visibility, visibility * alpha);
    }
    c.restoreToCount(saveCount);
  }

  private float oldWidth;

  @Override public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    float newWidth = getAnimatedWidth();
    if (oldWidth != newWidth) {
      oldWidth = newWidth;
      callback.onSizeChanged();
    }
  }

  private static final class AvatarEntry {
    private final long id;
    private final @Nullable ImageFile avatarFile;
    private final @NonNull AvatarPlaceholder avatarPlaceholder;

    public AvatarEntry (long id, @Nullable ImageFile avatarFile, @NonNull AvatarPlaceholder avatarPlaceholder) {
      this.id = id;
      this.avatarFile = avatarFile;
      this.avatarPlaceholder = avatarPlaceholder;
    }

    @Override public int hashCode () {
      return (int) (id ^ (id >>> 32));
    }

    @Override public boolean equals (Object other) {
      return other instanceof AvatarEntry && ((AvatarEntry) other).id == this.id;
    }

    public void draw (@NonNull Canvas c, @Nullable ComplexReceiver complexReceiver, int cx, int cy, @Px int radius, @Px int outline, float scale, float alpha) {
      if (alpha == 0f) {
        return;
      }

      if (scale != 1f) {
        c.save();
        c.scale(scale, scale, cx, cy);
      }

      c.drawCircle(cx, cy, radius + outline, Paints.getErasePaint());

      ImageReceiver imageReceiver = avatarFile != null && complexReceiver != null ? complexReceiver.getImageReceiver(id) : null;
      if (imageReceiver != null) {
        if (alpha != 1f) {
          imageReceiver.setPaintAlpha(imageReceiver.getPaintAlpha() * alpha);
        }
        imageReceiver.setBounds(cx - radius, cy - radius, cx + radius, cy + radius);
        if (imageReceiver.needPlaceholder()) {
          imageReceiver.drawPlaceholderRounded(c, radius, ColorUtils.alphaColor(alpha, Theme.placeholderColor()));
        }
        imageReceiver.draw(c);
        if (alpha != 1f) {
          imageReceiver.restorePaintAlpha();
        }
      } else {
        avatarPlaceholder.draw(c, cx, cy, alpha);
      }

      if (scale != 1f) {
        c.restore();
      }
    }

    public static @NonNull AvatarEntry fromUserId (@NonNull Tdlib tdlib, long userId, @Dimension(unit = Dimension.DP) float radius) {
      ImageFile avatarFile = tdlib.cache().userAvatar(userId, Screen.dp(radius) * 2);
      AvatarPlaceholder avatarPlaceholder = tdlib.cache().userPlaceholder(userId, false, radius, null);
      return new AvatarEntry(userId, avatarFile, avatarPlaceholder);
    }

    public static @NonNull AvatarEntry fromChatId (@NonNull Tdlib tdlib, long chatId, @Dimension(unit = Dimension.DP) float radius) {
      ImageFile avatarFile = tdlib.chatAvatar(chatId, Screen.dp(radius) * 2);
      AvatarPlaceholder avatarPlaceholder = tdlib.chatPlaceholder(chatId, tdlib.chat(chatId), false, radius, null);
      return new AvatarEntry(chatId, avatarFile, avatarPlaceholder);
    }

    public static @NonNull AvatarEntry fromSender (@NonNull Tdlib tdlib, @NonNull TdApi.MessageSender sender, @Dimension(unit = Dimension.DP) float radius) {
      switch (sender.getConstructor()) {
        case TdApi.MessageSenderChat.CONSTRUCTOR:
          return fromChatId(tdlib, ((TdApi.MessageSenderChat) sender).chatId, radius);
        case TdApi.MessageSenderUser.CONSTRUCTOR:
          return fromUserId(tdlib, ((TdApi.MessageSenderUser) sender).userId, radius);
        default: {
          throw new UnsupportedOperationException(sender.toString());
        }
      }
    }
  }
}