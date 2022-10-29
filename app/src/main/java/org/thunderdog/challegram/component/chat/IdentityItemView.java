package org.thunderdog.challegram.component.chat;


import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.core.graphics.drawable.DrawableCompat;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.data.Identity;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.widget.BaseView;

import me.vkryl.core.StringUtils;

public class IdentityItemView extends BaseView {
  private static final float AVATAR_RADIUS = 10f;
  private static final float LOCK_ICON_RADIUS = 7f;
  private static final float TEXT_PADDING_X = 20f;
  private static final float TEXT_PADDING_Y = 0f;

  private Identity identity;

  protected String title;
  protected Text trimmedTitle;
  protected String subtitle;
  protected Text trimmedSubtitle;

  protected ImageReceiver receiver;
  protected ImageFile avatarFile;
  protected AvatarPlaceholder avatarPlaceholder;
  protected Drawable userPlaceholder;
  protected Drawable anonymousPlaceholder;
  protected Drawable lockedIcon;

  public IdentityItemView (@NonNull Context context, Tdlib tdlib) {
    super(context, tdlib);
    this.receiver = new ImageReceiver(this, Screen.dp(AVATAR_RADIUS));
  }

  public void setIdentity (Identity identity) {
    this.identity = identity;

    title = identity.getName();
    String chatUserName = "@" + identity.getUsername();
    String userHint = Lang.getString(R.string.your_account);
    subtitle = identity.getType() == Identity.Type.USER ? userHint : chatUserName;
    avatarFile = identity.getAvatar();
    receiver.requestFile(avatarFile);
    avatarPlaceholder = new AvatarPlaceholder(AVATAR_RADIUS, identity.getAvatarPlaceholderData(), null);
    userPlaceholder = Drawables.get(R.drawable.baseline_acc_personal_24);
    anonymousPlaceholder = Drawables.get(R.drawable.baseline_acc_anon_24);
    lockedIcon = Drawables.get(R.drawable.baseline_lock_24);
  }

  public Identity getIdentity () {
    return identity;
  }


  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    final int avatarRadius = Screen.dp(AVATAR_RADIUS);
    final int lockRadius = Screen.dp(LOCK_ICON_RADIUS);
    final int centerY = getMeasuredHeight() / 2;
    receiver.setBounds(
      getPaddingLeft(),
      centerY - avatarRadius,
      getPaddingLeft() + avatarRadius * 2,
      centerY + avatarRadius
    );
    userPlaceholder.setBounds(
      getPaddingLeft(),
      centerY - avatarRadius,
      getPaddingLeft() +avatarRadius * 2,
      centerY + avatarRadius
    );
    anonymousPlaceholder.setBounds(
      getPaddingLeft(),
      centerY - avatarRadius,
      getPaddingLeft() + avatarRadius * 2,
      centerY + avatarRadius
    );
    lockedIcon.setBounds(
      getMeasuredWidth() - getPaddingRight() - lockRadius * 2,
      centerY - lockRadius,
      getMeasuredWidth() - getPaddingRight(),
      centerY + lockRadius);
    buildTexts();
  }

  private void buildTexts () {
    final int totalPaddingLeft = getPaddingLeft() + Screen.dp(AVATAR_RADIUS * 2) + Screen.dp(TEXT_PADDING_X);
    final int lockIconPadding = identity.isLocked() ? Screen.dp(LOCK_ICON_RADIUS * 2) + Screen.dp(TEXT_PADDING_X) : 0;
    final int totalPaddingRight = getPaddingRight() + lockIconPadding;
    final int availWidth = getMeasuredWidth() - totalPaddingLeft - totalPaddingRight;
    if (availWidth > 0 && !StringUtils.isEmpty(title)) {
      trimmedTitle = new Text.Builder(
        title, availWidth, Paints.robotoStyleProvider(16), Theme::textAccentColor
      ).singleLine().build();
    } else {
      trimmedTitle = null;
    }
    if (availWidth > 0 && !StringUtils.isEmpty(subtitle)) {
      trimmedSubtitle = new Text.Builder(
        subtitle, availWidth, Paints.robotoStyleProvider(11), Theme::textDecentColor
      ).singleLine().build();
    } else {
      trimmedSubtitle = null;
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    if (identity == null) {
      return;
    }

    if (identity.getType() == Identity.Type.USER) {
      DrawableCompat.setTint(userPlaceholder, Theme.iconColor());
      userPlaceholder.draw(c);
    } else if (identity.getType() == Identity.Type.ANONYMOUS) {
      DrawableCompat.setTint(anonymousPlaceholder, Theme.iconColor());
      anonymousPlaceholder.draw(c);
    } else if (avatarFile != null) {
      if (receiver.needPlaceholder()) {
        receiver.drawPlaceholderRounded(c, Screen.dp(AVATAR_RADIUS));
      }
      receiver.draw(c);
    } else if (avatarPlaceholder != null) {
      avatarPlaceholder.draw(c, receiver.centerX(), receiver.centerY());
    }
    if (identity.isLocked()) {
      DrawableCompat.setTint(lockedIcon, Theme.textAccentColor());
      lockedIcon.draw(c);
    }

    int totalTextHeight = trimmedTitle.getHeight() + trimmedSubtitle.getHeight() + Screen.dp(TEXT_PADDING_Y) * 2;
    int textPadding = (getMeasuredHeight() - totalTextHeight) / 2;
    if (trimmedTitle != null) {
      int startX = getPaddingLeft() + Screen.dp(AVATAR_RADIUS * 2) + Screen.dp(TEXT_PADDING_X);
      int startY = textPadding;
      trimmedTitle.draw(c, startX, startY);
    }
    if (trimmedSubtitle != null) {
      int startX = getPaddingLeft() + Screen.dp(AVATAR_RADIUS * 2) + Screen.dp(TEXT_PADDING_X);
      int startY = getMeasuredHeight() - textPadding - trimmedSubtitle.getHeight();
      trimmedSubtitle.draw(c, startX, startY);
    }
  }
}
