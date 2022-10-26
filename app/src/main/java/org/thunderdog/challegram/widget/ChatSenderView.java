package org.thunderdog.challegram.widget;

import static org.thunderdog.challegram.theme.Theme.getColor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibSender;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeInvalidateListener;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.Counter;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;

public class ChatSenderView extends FrameLayoutFix implements FactorAnimator.Target, ThemeInvalidateListener {
  private final Counter counter;
  private final BoolAnimator checkedFactor;

  private Tdlib tdlib;
  public TdlibSender sender;

  private final float cx = Screen.dp(54f);
  private final float cy = Screen.dp(49f);

  private final float r1 = Screen.dp(11.5f);
  private final float r2 = Screen.dp(10f);
  private int number = -2;

  private final float lineSize;

  public AvatarView avatarView;
  private final TextView titleView;
  private final TextView subtitleView;
  private final ImageView imageView;

  public ChatSenderView (@NonNull Context context) {
    super(context);

    lineSize = Screen.dp(2f);
    checkedFactor = new BoolAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 165L);
    checkedFactor.setValue(true, false);

    counter = new Counter.Builder()
      .callback(this)
      .noBackground()
      .allBold(true)
      .textSize(20f)
      .build();

    avatarView = new AvatarView(getContext());
    FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(Screen.dp(50), Screen.dp(50), Lang.gravity(Gravity.CENTER_VERTICAL));
    flp.leftMargin = Screen.dp(11f);
    addView(avatarView, flp);

    LinearLayout linearLayout = new LinearLayout(getContext());
    linearLayout.setOrientation(LinearLayout.VERTICAL);
    linearLayout.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    lp.setMargins(Lang.rtl() ? Screen.dp(48f): Screen.dp(72f), 0, Lang.rtl() ? Screen.dp(72f) : Screen.dp(48f), 0);
    linearLayout.setLayoutParams(lp);

    titleView = new NoScrollTextView(getContext());
    titleView.setTypeface(Fonts.getRobotoRegular());
    titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    titleView.setSingleLine(true);
    titleView.setEllipsize(TextUtils.TruncateAt.END);
    linearLayout.addView(titleView);

    subtitleView = new NoScrollTextView(getContext());
    subtitleView.setTypeface(Fonts.getRobotoRegular());
    subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    subtitleView.setSingleLine(true);
    subtitleView.setEllipsize(TextUtils.TruncateAt.END);
    linearLayout.addView(subtitleView);

    imageView = new ImageView(getContext());
    flp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
    flp.leftMargin = 0;
    flp.rightMargin = Screen.dp(11);
    addView(imageView, flp);

    Views.setClickable(this);
    RippleSupport.setSimpleWhiteBackground(this);
    recolor();
    addView(linearLayout);
  }

  public void attach () {
    avatarView.attach();
  }

  public void detach() {
    avatarView.detach();
  }

  public void performDestroy () {
    avatarView.performDestroy();
    setSender(null);
  }

  public void init (Tdlib tdlib) {
    this.tdlib = tdlib;
  }

  public void setSender (@Nullable TdlibSender sender) {
    long oldSenderId = (this.sender != null ? this.sender.getSenderId() : 0);
    long newSenderId = (sender != null ? sender.getSenderId() : 0);
    if (oldSenderId != newSenderId) {
      if (sender != null) {
        this.sender = sender;
        avatarView.setMessageSender(tdlib, sender, false);
        titleView.setText(sender.getName());
        subtitleView.setText(sender.getDescription());
        setTag(sender.getSenderId());

        int iconRes = 0;
        int iconPadding = 0;
        switch (sender.getIconType()) {
          case TdlibSender.ICON_TYPE_PROFILE: {
            iconRes = R.drawable.baseline_sender_account_24;
            break;
          }
          case TdlibSender.ICON_TYPE_ANONYMOUS_ADMIN: {
            iconRes = R.drawable.baseline_sender_anonymus_24;
            break;
          }
          case TdlibSender.ICON_TYPE_LOCK: {
            iconRes = R.drawable.baseline_lock_16;
            iconPadding = Screen.dp(4);
            break;
          }
        }
        Drawable icon = Drawables.get(getContext().getResources(), iconRes);
        if (icon != null) {
          imageView.setColorFilter(Paints.getColorFilter(getIconColor()));
          imageView.setVisibility(View.VISIBLE);
          imageView.setImageDrawable(icon);
          imageView.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);
        } else {
          imageView.setVisibility(View.INVISIBLE);
        }
      } else {
        titleView.setText("");
        subtitleView.setText("");
      }
    }
  }

  private int getIconColor() {
    if (sender.getIconType() == TdlibSender.ICON_TYPE_LOCK) {
      return getColor(R.id.theme_color_themeBlackWhite);
    } else {
      return Theme.iconColor();
    }
  }

  @Override
  protected void dispatchDraw (Canvas canvas) {
    super.dispatchDraw(canvas);
    canvas.drawCircle(cx, cy, r1, Paints.fillingPaint(ColorUtils.alphaColor(checkedFactor.getFloatValue(), Theme.fillingColor())));
    canvas.drawCircle(cx, cy, r2, Paints.fillingPaint(ColorUtils.alphaColor(checkedFactor.getFloatValue(), Theme.radioFillingColor())));

    if (counter.getVisibility() > 0f) {
      counter.draw(canvas, cx, cy, Gravity.CENTER, counter.getVisibility());
    } else {
      float x1 = cx - Screen.dp(1.5f);
      float y1 = cy + Screen.dp(4.5f);
      float w2 = Screen.dp(10f) * checkedFactor.getFloatValue();
      float h1 = Screen.dp(6f) * checkedFactor.getFloatValue();

      canvas.rotate(-45f, x1, y1);
      canvas.drawRect(x1, y1 - h1, x1 + lineSize, y1, Paints.fillingPaint(ColorUtils.alphaColor(checkedFactor.getFloatValue(), Theme.radioCheckColor())));
      canvas.drawRect(x1, y1 - lineSize, x1 + w2, y1, Paints.fillingPaint(ColorUtils.alphaColor(checkedFactor.getFloatValue(), Theme.radioCheckColor())));
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    invalidate();
  }

  public void setChecked (boolean checked, boolean animated) {
    this.setNumber(checked ? 0 : -1, animated);
  }

  private void setNumber (int number, boolean animated) {
    boolean checked = number >= 0;
    if (number == this.number) {
      return;
    }

    this.number = number;

    counter.setCount(Math.max(0, number), animated);
    checkedFactor.setValue(checked, animated);
  }

  private void recolor () {
    titleView.setTextColor(Theme.textAccentColor());
    subtitleView.setTextColor(Theme.textDecentColor());
    if (imageView.getDrawable() != null) {
      imageView.setColorFilter(Paints.getColorFilter(getIconColor()));
    }
  }

  @Override
  public void onThemeInvalidate (boolean isTempUpdate) {
    recolor();
  }
}
