package org.thunderdog.challegram.component.chat;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibSender;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.BaseView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.lambda.Destroyable;

@SuppressLint("ViewConstructor")
public class ChatSendersView extends BaseView implements Destroyable, FactorAnimator.Target {

  public static final int RADIUS_DEFAULT = 12;
  public static final int RADIUS_SMALL = 8;

  private FactorAnimator animator;
  private final ComplexReceiver megaReceiver = new ComplexReceiver(this);
  private final List<ChatSenderEntity> entries;
  private final int radius;
  private float outlineFactor;
  private float factor;

  public ChatSendersView (Context context, Tdlib tdlib, int radius) {
    super(context, tdlib);
    this.radius = radius;
    this.entries = new ArrayList<>(2);
    this.entries.add(null);
    this.entries.add(null);

    megaReceiver.attach();
  }

  @Override
  public void performDestroy () {
    megaReceiver.performDestroy();
    entries.set(0, null);
    entries.set(1, null);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }

  @Override
  protected void onDraw (Canvas canvas) {
    super.onDraw(canvas);
    if (entries != null) {
      int baseCy = getMeasuredHeight() / 2;
      int baseCx = getMeasuredWidth() / 2;

      ChatSenderEntity newItem = entries.get(0);
      if (newItem != null && newItem.isVisible) {
        int cy = (int) (baseCy + ((getMeasuredHeight() / 2) + Screen.dp(radius)) * (1 - factor));
        float alpha = .5f + 0.5f * factor;
        newItem.draw(canvas, megaReceiver.getImageReceiver(newItem.senderId), baseCx, cy, alpha);
      }

      ChatSenderEntity oldItem = entries.get(1);
      if (oldItem != null && oldItem.isVisible) {
        int cy = (int) (baseCy - ((getMeasuredHeight() / 2) + Screen.dp(radius)) * factor);
        float alpha = 1f - .5f * factor;
        oldItem.draw(canvas, megaReceiver.getImageReceiver(oldItem.senderId), baseCx, cy, alpha);
      }
    }
  }

  public void setNewSender (TdlibSender newSender) {
    boolean animated = false;
    ChatSenderEntity current = entries.get(0);
    if (current != null) {
      entries.set(1, current);
      animated = true;
    }

    ChatSenderEntity newChatSenderEntity = new ChatSenderEntity(context(), newSender, radius);
    newChatSenderEntity.setBackgroundColor(Theme.getColor(R.id.theme_color_filling));
    newChatSenderEntity.outlineFactor = outlineFactor;
    if (newChatSenderEntity.avatarFile != null) {
      megaReceiver.getImageReceiver(newSender.getSenderId()).requestFile(newChatSenderEntity.avatarFile);
    }
    entries.set(0, newChatSenderEntity);

    if (animator == null) {
      animator = new FactorAnimator(0, this, AnimatorUtils.ACCELERATE_DECELERATE_INTERPOLATOR, 150l);
    }
    if (animated) {
      animator.forceFactor(0);
      animator.animateTo(1f);
    } else {
      factor = 1f;
      hideOldEntity();
      invalidate();
    }
  }

  public void setOutlineFactor (float factor) {
    this.outlineFactor = factor;
    for (int i = 0; i < entries.size(); i++) {
      if (entries.get(i) != null)
        entries.get(i).setOutlineFactor(factor);
    }
    invalidate();
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    this.factor = factor;
    invalidate();
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    FactorAnimator.Target.super.onFactorChangeFinished(id, finalFactor, callee);
    if (finalFactor == 1f) {
      hideOldEntity();
      invalidate();
    }
  }

  private void hideOldEntity () {
    ChatSenderEntity old = entries.get(1);
    if (old != null) {
      old.isVisible = false;
    }
  }

  public static class ChatSenderEntity {
    private final long senderId;
    private final ImageFile avatarFile;
    private final AvatarPlaceholder avatarPlaceholder;
    private final int radiusDp;
    private boolean isVisible;
    private Paint iconBackgroundPaint;

    private Drawable customIcon;
    private Drawable customIconOutline;

    private float outlineFactor;

    public ChatSenderEntity (Context context, TdlibSender sender, int radius) {
      this.senderId = sender.getSenderId();
      this.radiusDp = Screen.dp(radius);
      this.avatarFile = sender.getAvatar();
      if (this.avatarFile != null) {
        this.avatarFile.setSize(radiusDp * 2);
      }
      this.avatarPlaceholder = new AvatarPlaceholder(radius, sender.getPlaceholderMetadata(), null);

      if (sender.isSelf()) {
        if (radius == RADIUS_SMALL) {
          customIcon = Drawables.get(context.getResources(), R.drawable.baseline_sender_account_16);
        } else {
          customIcon = Drawables.get(context.getResources(), R.drawable.baseline_sender_account_24);
        }
      } else if (sender.isAnonymousGroupAdmin()) {
        if (radius == RADIUS_SMALL) {
          customIcon = Drawables.get(context.getResources(), R.drawable.baseline_sender_anonymus_16);
          customIconOutline = Drawables.get(context.getResources(), R.drawable.baseline_incognito_circle_16);
        } else {
          customIcon = Drawables.get(context.getResources(), R.drawable.baseline_sender_anonymus_24);
          customIconOutline = Drawables.get(context.getResources(), R.drawable.baseline_incognito_circle_24);
        }
      }

      if (customIconOutline != null) {
        iconBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      }
      this.isVisible = true;
    }

    @Override
    public boolean equals (@Nullable Object obj) {
      return obj instanceof ChatSenderEntity && ((ChatSenderEntity) obj).senderId == this.senderId;
    }

    @Override
    public int hashCode () {
      return (int) (senderId ^ (senderId >>> 32));
    }

    public void setBackgroundColor (int backgroundColor) {
      if (iconBackgroundPaint != null) {
        iconBackgroundPaint.setColor(backgroundColor);
      }
    }

    public ImageFile getAvatarFile () {
      return avatarFile;
    }

    public void setOutlineFactor (float factor) {
      this.outlineFactor = factor;
    }

    public void draw (Canvas c, ImageReceiver imageReceiver, float cx, float cy, final float alpha) {
      int radius = (int) (radiusDp - (Screen.dp(1)) * outlineFactor);
      if (customIcon != null) {
        if (outlineFactor > .5f && customIconOutline != null) {
          c.drawCircle(cx, cy, radius, iconBackgroundPaint);
          iconBackgroundPaint.setColor(Theme.getColor(R.id.theme_color_filling));
          customIconOutline.setAlpha((int) (255 * alpha));
          customIconOutline.setBounds(0, 0, radiusDp * 2, radiusDp * 2);
          Drawables.draw(c, customIconOutline, cx - radiusDp, cy - radiusDp, Paints.getPorterDuffPaint(Theme.getColor(R.id.theme_color_iconLight)));
        } else {
          customIcon.setAlpha((int) (255 * alpha));
          customIcon.setBounds(0, 0, radius * 2, radius * 2);
          Drawables.draw(c, customIcon, cx - radius, cy - radius, Paints.getPorterDuffPaint(Theme.getColor(R.id.theme_color_icon)));
        }
      } else {
        ImageReceiver receiver = avatarFile != null ? imageReceiver : null;

        if (receiver != null) {
          receiver.setAlpha(alpha);
          receiver.setBounds((int) (cx - radius), (int) (cy - radius), (int) (cx + radius), (int) (cy + radius));
          if (receiver.needPlaceholder()) {
            receiver.drawPlaceholderRounded(c, radius, Theme.placeholderColor());
          }
          receiver.setRadius(radius);
          receiver.draw(c);
        } else if (avatarPlaceholder != null) {
          avatarPlaceholder.draw(c, cx, cy, alpha, radius, true);
        }
      }

      if (outlineFactor > 0) {
        c.drawCircle(cx, cy, radius + Screen.dp(1), Paints.getOuterCheckPaint(Theme.getColor(R.id.theme_color_filling)));
      }
    }
  }
}
