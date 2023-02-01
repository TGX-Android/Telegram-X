package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.view.Gravity;

import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.core.util.ObjectsCompat;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.animator.ListAnimator;
import me.vkryl.android.util.ViewProvider;
import me.vkryl.td.Td;

public final class TGAvatars implements FactorAnimator.Target {
  private static final @Dimension(unit = Dimension.DP) float DEFAULT_AVATAR_RADIUS = 10f;
  private static final @Dimension(unit = Dimension.DP) float DEFAULT_AVATAR_OUTLINE = 2f;
  private static final @Dimension(unit = Dimension.DP) float DEFAULT_AVATAR_SPACING = -4f;

  private static final long ANIMATION_DURATION = 280l;

  private final @NonNull Tdlib tdlib;
  private final @NonNull Callback callback;

  private @Nullable ViewProvider viewProvider;
  private @Nullable List<AvatarEntry> entries;
  private @Nullable Set<Long> entriesIds;
  private @Nullable ListAnimator<AvatarEntry> animator;
  private @Nullable FactorAnimator countAnimator;

  private @Dimension(unit = Dimension.DP) float avatarRadius = DEFAULT_AVATAR_RADIUS;
  private @Dimension(unit = Dimension.DP) float avatarOutline = DEFAULT_AVATAR_OUTLINE;
  private @Dimension(unit = Dimension.DP) float avatarSpacing = DEFAULT_AVATAR_SPACING;

  public interface Callback {
    void onSizeChanged ();
    void onInvalidateMedia (TGAvatars avatars);
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

  public void setChatIds (@Nullable long[] chatIds, boolean animated) {
    if (chatIds != null && chatIds.length > 0) {
      List<AvatarEntry> entries = new ArrayList<>(chatIds.length);
      for (long chatId : chatIds) {
        entries.add(new AvatarEntry(tdlib.sender(chatId)));
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
        entries.add(new AvatarEntry(sender));
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
    this.entriesIds = entries != null && !entries.isEmpty() ? new HashSet<>(entries.size()) : null;

    if (entries != null && !entries.isEmpty()) {
      for (AvatarEntry entry : entries) {
        this.entriesIds.add(entry.id());
      }
      callback.onInvalidateMedia(this);
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

  public void requestFiles (ComplexReceiver complexReceiver, boolean isUpdate) {
    if (complexReceiver != null) {
      if (entries != null && !entries.isEmpty()) {
        for (AvatarEntry entry : entries) {
          AvatarReceiver receiver = complexReceiver.getAvatarReceiver(entry.id());
          receiver.requestMessageSender(tdlib, entry.senderId, AvatarReceiver.Options.NONE);
        }
        if (!isUpdate) {
          complexReceiver.clearReceivers((receiverType, receiver, key) -> receiverType == ComplexReceiver.RECEIVER_TYPE_AVATAR && entriesIds != null && entriesIds.contains(key));
        }
      } else {
        complexReceiver.clear();
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

  public void draw (@NonNull MessageView view, @NonNull Canvas c, int x, int cy, int gravity, float alpha) {
    if (animator == null || animator.size() == 0 || alpha == 0f) {
      return;
    }

    ComplexReceiver avatarsReceiver = view.getAvatarsReceiver();
    if (avatarsReceiver == null) {
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
      entry.item.draw(c, avatarsReceiver, cx, cy, avatarRadius, avatarOutline, visibility, visibility * alpha);
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
    private final TdApi.MessageSender senderId;

    public AvatarEntry (TdApi.MessageSender senderId) {
      this.senderId = senderId;
    }

    public long id () {
      return Td.getSenderId(senderId);
    }

    @Override public int hashCode () {
      long id = id();
      return (int) (id ^ (id >>> 32));
    }

    @Override public boolean equals (Object other) {
      return other instanceof AvatarEntry && ((AvatarEntry) other).id() == this.id();
    }

    public void draw (@NonNull Canvas c, @Nullable ComplexReceiver complexReceiver, int cx, int cy, @Px int radius, @Px int outline, float scale, float alpha) {
      if (alpha == 0f || complexReceiver == null) {
        return;
      }

      if (scale != 1f) {
        c.save();
        c.scale(scale, scale, cx, cy);
      }

      final long id = id();
      AvatarReceiver receiver = complexReceiver.getAvatarReceiver(id);

      receiver.drawPlaceholderRounded(c, receiver.getDisplayRadius(), outline, Paints.getErasePaint());

      if (alpha != 1f) {
        receiver.setPaintAlpha(receiver.getPaintAlpha() * alpha);
      }
      receiver.setBounds(cx - radius, cy - radius, cx + radius, cy + radius);
      if (receiver.needPlaceholder()) {
        receiver.drawPlaceholder(c);
      }
      receiver.draw(c);
      if (alpha != 1f) {
        receiver.restorePaintAlpha();
      }

      if (scale != 1f) {
        c.restore();
      }
    }
  }
}