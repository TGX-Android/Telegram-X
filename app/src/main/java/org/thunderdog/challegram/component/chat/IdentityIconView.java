package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.core.graphics.drawable.DrawableCompat;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.data.Identity;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.BaseView;
import org.thunderdog.challegram.widget.SimplestCheckBoxHelper;

public class IdentityIconView extends BaseView {
  private Identity identity;

  public static final float NORMAL_RADIUS = 12f;
  public static final float LITTLE_RADIUS = 8f;

  private float avatarRadius = 12f;

  private boolean useIcons = true;
  private boolean useStroke = true;

  private ImageReceiver receiver;
  private ImageFile avatarFile;
  private AvatarPlaceholder avatarPlaceholder;
  private Drawable userPlaceholder;
  private Drawable anonymousPlaceholder;

  public static IdentityIconView createLittleView (@NonNull Context context, Tdlib tdlib) {
    IdentityIconView iconView = createNormalView(context, tdlib, true, true);
    iconView.scaleFromLittleToNormal(0f);
    return iconView;
  }

  public static IdentityIconView createNormalView (@NonNull Context context, Tdlib tdlib) {
    return createNormalView(context, tdlib, true, false);
  }

  public static IdentityIconView createNormalView (@NonNull Context context, Tdlib tdlib, boolean useIcons, boolean useStroke) {
    ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(Screen.dp(49f), Screen.dp(49f));
    IdentityIconView iconView = new IdentityIconView(context, tdlib);
    iconView.init(12f, useIcons, useStroke);
    iconView.setId(R.id.sendIdentityIcon);
    iconView.setClickable(false);
    iconView.setLayoutParams(params);
    return iconView;
  }

  public IdentityIconView (@NonNull Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  public void init (float avatarRadius) {
    init(avatarRadius, true, true);
  }

  public void init (float avatarRadius, boolean useIcons, boolean useStroke) {
    this.avatarRadius = avatarRadius;
    this.useIcons = useIcons;
    this.useStroke = useStroke;

    receiver = new ImageReceiver(this, Screen.dp(avatarRadius));
    userPlaceholder = Drawables.get(R.drawable.baseline_acc_personal_24);
    anonymousPlaceholder = Drawables.get(R.drawable.baseline_acc_anon_24);
  }

  public void scaleFromLittleToNormal (float factor) {
    float scale = (LITTLE_RADIUS + (NORMAL_RADIUS - LITTLE_RADIUS) * factor) / NORMAL_RADIUS;
    setScaleX(scale);
    setScaleY(scale);
  }

  public void setIdentity (Identity identity) {
    this.identity = identity;
    if (identity == null) {
      return;
    }

    avatarFile = identity.getAvatar();
    receiver.requestFile(avatarFile);
    avatarPlaceholder = new AvatarPlaceholder(this.avatarRadius, identity.getAvatarPlaceholderData(), null);
  }

  public Identity getIdentity () {
    return identity;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    final int radius = Screen.dp(avatarRadius);
    final int centerX = getMeasuredWidth() / 2;
    final int centerY = getMeasuredHeight() / 2;
    receiver.setBounds(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
    userPlaceholder.setBounds(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
    anonymousPlaceholder.setBounds(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
  }

  @Override
  protected void onDraw (Canvas c) {
    if (identity == null) {
      return;
    }

    if (useStroke) {
      Paint strokePaint = Paints.getOuterCheckPaint(Theme.fillingColor());
      strokePaint.setStyle(Paint.Style.FILL);
      c.drawCircle(
        receiver.getCenterX(),
        receiver.getCenterY(),
        Screen.dp(avatarRadius + 3f),
        strokePaint
      );
      strokePaint.setStyle(Paint.Style.STROKE);
    }
    if (useIcons && identity.getType() == Identity.Type.USER) {
      DrawableCompat.setTint(userPlaceholder, Theme.iconColor());
      userPlaceholder.draw(c);
    } else if (useIcons && identity.getType() == Identity.Type.ANONYMOUS) {
      DrawableCompat.setTint(anonymousPlaceholder, Theme.iconColor());
      anonymousPlaceholder.draw(c);
    } else if (avatarFile != null) {
      if (receiver.needPlaceholder()) {
        receiver.drawPlaceholderRounded(c, Screen.dp(avatarRadius));
      }
      receiver.draw(c);
    } else if (avatarPlaceholder != null) {
      avatarPlaceholder.draw(c, receiver.centerX(), receiver.centerY());
    }
  }
}
