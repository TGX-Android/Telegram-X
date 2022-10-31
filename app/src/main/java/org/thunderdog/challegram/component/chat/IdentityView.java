package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;

import androidx.core.graphics.drawable.DrawableCompat;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.data.Identity;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.BaseView;
import org.thunderdog.challegram.widget.SimplestCheckBoxHelper;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

public class IdentityView extends BaseView implements FactorAnimator.Target {
  public static final float AVATAR_RADIUS = 25f;
  public static final float ICON_RADIUS = 12f;
  public static final float OUT_X_PADDING = 20f;
  private static final float TEXT_PADDING = 10f;

  private Identity identity;
  private ImageReceiver avatarReceiver;
  private ImageFile avatarFile;
  private AvatarPlaceholder avatarPlaceholder;
  private Drawable userIcon;
  private Drawable anonymousIcon;
  private Drawable lockedIcon;

  private String name;
  private String username;

  private TextPaint namePaint;
  private float nameHeight;
  private TextPaint usernamePaint;
  private float usernameHeight;
  private float textAvailWidth;

  private SimplestCheckBoxHelper checkBoxHelper;

  private boolean isLast = false;
  private Paint linePaint;

  private Runnable onChecked;

  private BoolAnimator waitCheckAnimator = new BoolAnimator(
    0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, SimplestCheckBoxHelper.CHECK_ANIMATION_DURATION
  );

  public IdentityView (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  public void init (Runnable onChecked) {
    this.onChecked = onChecked;

    avatarReceiver = new ImageReceiver(this, Screen.dp(AVATAR_RADIUS));
    userIcon = Drawables.get(R.drawable.baseline_acc_personal_24);
    anonymousIcon = Drawables.get(R.drawable.baseline_acc_anon_24);
    lockedIcon = Drawables.get(R.drawable.baseline_lock_24);

    namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    namePaint.setColor(Theme.textAccentColor());
    namePaint.setTextSize(Screen.dp(15f));
    namePaint.setTypeface(Fonts.getRobotoMedium());
    ThemeManager.addThemeListener(namePaint, R.id.theme_color_text);

    usernamePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    usernamePaint.setColor(Theme.textDecentColor());
    usernamePaint.setTextSize(Screen.dp(15f));
    usernamePaint.setTypeface(Fonts.getRobotoRegular());
    ThemeManager.addThemeListener(usernamePaint, R.id.theme_color_textLight);

    Paint.FontMetrics fm = this.namePaint.getFontMetrics();
    nameHeight = fm.descent - fm.ascent;
    fm = this.usernamePaint.getFontMetrics();
    usernameHeight = fm.descent - fm.ascent;
    linePaint = new Paint();
    linePaint.setColor(Theme.backgroundColor());

    waitCheckAnimator.setValue(false, false);
  }

  public void setIdentity (Identity identity) {
    this.identity = identity;

    name = identity.getName();
    String chatUserName = "@" + identity.getUsername();
    String userHint = Lang.getString(R.string.your_account);
    username = identity.getType() == Identity.Type.USER ? userHint : chatUserName;
    avatarFile = identity.getAvatar();
    avatarReceiver.requestFile(avatarFile);
    avatarPlaceholder = new AvatarPlaceholder(AVATAR_RADIUS, identity.getAvatarPlaceholderData(), null);
    invalidate();
  }

  public void setIsChecked (boolean isChecked, boolean animated) {
    if (isChecked != (checkBoxHelper != null && checkBoxHelper.isChecked())) {
      if (checkBoxHelper == null) {
        checkBoxHelper = new SimplestCheckBoxHelper(this, avatarReceiver);
      }
      checkBoxHelper.setIsChecked(isChecked, animated);
      if (isChecked && animated) {
        waitCheckAnimator.setValue(true, true);
      }
    }
  }
  public void setIsLast (boolean isLast) {
    this.isLast = isLast;
    invalidate();
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (finalFactor == 1f) {
      onChecked.run();
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    final int avatarRadius = Screen.dp(AVATAR_RADIUS);
    final int lockRadius = Screen.dp(ICON_RADIUS);
    final int centerY = getMeasuredHeight() / 2;
    avatarReceiver.setBounds(
      getPaddingLeft(),
      centerY - avatarRadius,
      getPaddingLeft() + avatarRadius * 2,
      centerY + avatarRadius
    );
    userIcon.setBounds(
      getMeasuredWidth() - getPaddingRight() - lockRadius * 2,
      centerY - lockRadius,
      getMeasuredWidth() - getPaddingRight(),
      centerY + lockRadius
    );
    anonymousIcon.setBounds(
      getMeasuredWidth() - getPaddingRight() - lockRadius * 2,
      centerY - lockRadius,
      getMeasuredWidth() - getPaddingRight(),
      centerY + lockRadius
    );
    lockedIcon.setBounds(
      getMeasuredWidth() - getPaddingRight() - lockRadius * 2,
      centerY - lockRadius,
      getMeasuredWidth() - getPaddingRight(),
      centerY + lockRadius);

    final int totalPaddingLeft = getPaddingLeft() + Screen.dp(AVATAR_RADIUS * 2) + Screen.dp(TEXT_PADDING) + Screen.dp(OUT_X_PADDING);
    final int lockIconPadding = identity.isLocked() ? Screen.dp(ICON_RADIUS * 2) + Screen.dp(TEXT_PADDING) + Screen.dp(OUT_X_PADDING) : 0;
    final int totalPaddingRight = getPaddingRight() + lockIconPadding;
    textAvailWidth = getMeasuredWidth() - totalPaddingLeft - totalPaddingRight;
  }

  @Override
  protected void onDraw (Canvas c) {
    if (identity == null) {
      return;
    }
    if (!isLast) {
      float lineLeft = getPaddingLeft() + Screen.dp(AVATAR_RADIUS * 2) + Screen.dp(TEXT_PADDING);
      c.drawRect(lineLeft, getMeasuredHeight() - 4f, getMeasuredWidth(), getMeasuredHeight(), linePaint);
    }


    if (identity.getType() == Identity.Type.USER) {
      DrawableCompat.setTint(userIcon, Theme.iconColor());
      userIcon.draw(c);
    } else if (identity.getType() == Identity.Type.ANONYMOUS) {
      DrawableCompat.setTint(anonymousIcon, Theme.iconColor());
      anonymousIcon.draw(c);
    } else if (identity.isLocked()) {
      DrawableCompat.setTint(lockedIcon, Theme.textAccentColor());
      lockedIcon.draw(c);
    }

    if (avatarFile != null) {
      if (avatarReceiver.needPlaceholder()) {
        avatarReceiver.drawPlaceholderRounded(c, Screen.dp(AVATAR_RADIUS));
      }
      avatarReceiver.draw(c);
    } else if (avatarPlaceholder != null) {
      avatarPlaceholder.draw(c, avatarReceiver.centerX(), avatarReceiver.centerY());
    }

    final float checkFactor = checkBoxHelper != null ? checkBoxHelper.getCheckFactor() : 0f;
    if (checkFactor > 0f) {
      DrawAlgorithms.drawSimplestCheckBox(c, avatarReceiver, checkFactor);
    }

    float textPaddingY = (getMeasuredHeight() - nameHeight - usernameHeight) / 2;
    if (name != null) {
      float startX = getPaddingLeft() + Screen.dp(AVATAR_RADIUS * 2) + Screen.dp(TEXT_PADDING);
      float startY = nameHeight + textPaddingY;
      CharSequence trimmedName = TextUtils.ellipsize(name, namePaint, textAvailWidth, TextUtils.TruncateAt.END).toString();
      c.drawText(trimmedName.toString(), startX, startY, namePaint);
    }
    if (username != null) {
      float startX = getPaddingLeft() + Screen.dp(AVATAR_RADIUS * 2) + Screen.dp(TEXT_PADDING);
      float startY = getMeasuredHeight() - textPaddingY;
      CharSequence trimmedUsername = TextUtils.ellipsize(username, usernamePaint, textAvailWidth, TextUtils.TruncateAt.END).toString();
      c.drawText(trimmedUsername.toString(), startX, startY, usernamePaint);
    }
  }
}
