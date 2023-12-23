package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.util.HapticMenuHelper;
import org.thunderdog.challegram.widget.AvatarView;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.animator.ListAnimator;
import me.vkryl.android.animator.ReplaceAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.td.Td;

public class MessageSenderButton extends FrameLayout implements ReplaceAnimator.Callback, FactorAnimator.Target, HapticMenuHelper.OnItemMenuListener {
  private static final float ATTACH_BUTTONS_WIDTH = 47f;
  private static final float SEND_TRANSLATION_X = 4f;
  private static final float SEND_TRANSLATION_Y = 10f;

  private final Tdlib tdlib;
  private final MessagesController controller;

  private final FrameLayout frameLayout;
  private ButtonView currentButtonView;
  private ButtonView oldButtonView;

  private static final int VISIBLE_ANIMATOR = 0;
  private static final int QUICK_ANIMATOR = 1;

  private final ReplaceAnimator<ButtonView> replaceAnimator = new ReplaceAnimator<>(this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);
  private final BoolAnimator quickSelected = new BoolAnimator(QUICK_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);
  private final BoolAnimator alphaAnimator = new BoolAnimator(VISIBLE_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);

  public MessageSenderButton (Context context, MessagesController controller) {
    super(context);

    this.tdlib = controller.tdlib();
    this.controller = controller;

    alphaAnimator.setValue(true, false);

    currentButtonView = new ButtonView(context);
    currentButtonView.setOnClickListener(this::onClick);
    currentButtonView.setAlpha(0f);

    oldButtonView = new ButtonView(context);
    oldButtonView.setOnClickListener(this::onClick);
    oldButtonView.setAlpha(0f);

    frameLayout = new FrameLayout(context);
    frameLayout.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(ATTACH_BUTTONS_WIDTH), LayoutParams.MATCH_PARENT, Gravity.RIGHT | Gravity.BOTTOM));
    frameLayout.addView(oldButtonView);
    frameLayout.addView(currentButtonView);

    addView(frameLayout);

    controller.addThemeInvalidateListener(currentButtonView);
    controller.addThemeInvalidateListener(oldButtonView);
    controller.addThemeInvalidateListener(this);
    setWillNotDraw(false);
  }

  public void setAnimateVisible (boolean visible) {
    alphaAnimator.setValue(visible, UI.inUiThread());
  }

  public View getButtonView () {
    return currentButtonView;
  }

  @Override
  protected void onDraw (Canvas c) {
    float cx = getButtonCenterX();
    float cy = getButtonCenterY();
    float alpha = Math.min((1f - sendFactor), quickSelected.getFloatValue());
    float r = (int) (Screen.dp(33) * alpha);

    c.drawCircle(cx, cy, r, Paints.fillingPaint(ColorUtils.alphaColor(0.05f * alpha, Theme.getColor(ColorId.text))));
    super.onDraw(c);
  }

  private float getButtonCenterX () {
    return getMeasuredWidth() - (Screen.dp(ATTACH_BUTTONS_WIDTH) / 2f) + defaultTranslationX;
  }

  private float getButtonCenterY () {
    return getMeasuredHeight() / 2f;
  }

  private HapticMenuHelper hapticMenuHelper;
  public void setHapticMenuHelper (HapticMenuHelper hapticMenuHelper) {
    this.hapticMenuHelper = hapticMenuHelper;
  }

  boolean touchCaptured;

  @Override
  public boolean dispatchTouchEvent (MotionEvent ev) {
    if (sendFactor != 0f) return false;

    float cx = getButtonCenterX();
    float cy = getButtonCenterY();
    float x = ev.getX();
    float y = ev.getY();

    switch (ev.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        touchCaptured = MathUtils.distance(cx, cy, x, y) < Screen.dp(20);
        break;
      }
      case MotionEvent.ACTION_MOVE: {
        if (touchCaptured && y < Screen.dp(-15)) {
          if (hapticMenuHelper != null && !hapticMenuHelper.isMenuOpened()) {
            hapticMenuHelper.openMenu(getButtonView());
          }
          touchCaptured = false;
        }
        break;
      }
      default: {
        touchCaptured = false;
        break;
      }
    }

    return super.dispatchTouchEvent(ev);
  }

  @Override
  public void setOnLongClickListener (@Nullable OnLongClickListener l) {
    currentButtonView.setOnLongClickListener(l);
    oldButtonView.setOnLongClickListener(l);
  }

  @Override
  public void setOnTouchListener (OnTouchListener l) {
    currentButtonView.setOnTouchListener(l);
    oldButtonView.setOnTouchListener(l);
  }

  public void setQuickSelected (boolean value, boolean animated) {
    quickSelected.setValue(value, animated);
  }

  public boolean isAnonymousAdmin () {
    return currentButtonView.getDrawMode() == MODE_ANONYMOUS_BUTTON;
  }

  public boolean isPersonalAccount () {
    return currentButtonView.getDrawMode() == MODE_PERSON_BUTTON;
  }

  private float sendFactor = 0;
  private float defaultTranslationX = 0;

  public void checkPosition () {
    defaultTranslationX = Screen.dp(ATTACH_BUTTONS_WIDTH) - controller.getHorizontalInputPadding();
    checkPositionAndSize();
  }

  public void setSendFactor (float factor) {
    sendFactor = factor;
    checkPositionAndSize();
    checkAlpha();
  }

  public void checkPositionAndSize () {
    currentButtonView.setSendModeFactor(sendFactor);
    oldButtonView.setSendModeFactor(sendFactor);

    frameLayout.setTranslationX(MathUtils.fromTo(defaultTranslationX, Screen.dp(SEND_TRANSLATION_X), sendFactor));
    frameLayout.setTranslationY(MathUtils.fromTo(0, Screen.dp(SEND_TRANSLATION_Y), sendFactor));
    frameLayout.setScaleX(MathUtils.fromTo(1f, 0.625f, sendFactor));
    frameLayout.setScaleY(MathUtils.fromTo(1f, 0.625f, sendFactor));
    frameLayout.invalidate();

    invalidate();
  }

  public void update (TdApi.MessageSender sender, boolean animated) {
    final boolean isUserSender = Td.getSenderId(sender) == tdlib.myUserId();
    final boolean isGroupSender = Td.getSenderId(sender) == controller.getChatId();

    if (sender == null || isUserSender || isGroupSender) {
      update(null, isUserSender, isGroupSender, animated);
    } else {
      update(sender, false, false, animated);
    }
  }

  private void update (TdApi.MessageSender sender, boolean isPersonal, boolean isAnonymous, boolean animated) {
    onItemChanged(replaceAnimator);

    ButtonView swap = currentButtonView;
    currentButtonView = oldButtonView;
    oldButtonView = swap;

    currentButtonView.setDrawMode(tdlib, sender, sender != null ? MODE_CHAT_BUTTON : isAnonymous ? MODE_ANONYMOUS_BUTTON : MODE_PERSON_BUTTON);
    replaceAnimator.replace(currentButtonView, animated);

    invalidate();
  }

  private void onClick (View v) {
    if (delegate != null) {
      delegate.onClick();
    }
  }

  @Override
  public void onItemChanged (ReplaceAnimator animator) {
    currentButtonView.setAlpha(0f);
    currentButtonView.setTranslationY(0);
    oldButtonView.setAlpha(0f);
    oldButtonView.setTranslationY(0);

    for (ListAnimator.Entry<ButtonView> entry : replaceAnimator) {
      entry.item.setAlpha(entry.getVisibility());

      final int offset2 = (int) ((!entry.isAffectingList() ?
        ((entry.getVisibility() - 1f) * Screen.dp(32)):
        ((1f - entry.getVisibility()) * Screen.dp(32))));

      entry.item.setTranslationY(offset2);
    }
  }

  private boolean inSlowMode;

  public void setInSlowMode (boolean inSlowMode) {
    this.inSlowMode = inSlowMode;
    checkAlpha();
  }

  private void checkAlpha () {
    setAlpha(alphaAnimator.getFloatValue() * (inSlowMode ? (1f - sendFactor) : 1f));
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (id == QUICK_ANIMATOR) {
      currentButtonView.setQuickSelectFactor(factor);
      oldButtonView.setQuickSelectFactor(factor);
    } else if (id == VISIBLE_ANIMATOR) {
      checkAlpha();
    }
    invalidate();
  }

  @Override
  public void onHapticMenuOpen () {
    setQuickSelected(true, UI.inUiThread());
  }

  @Override
  public void onHapticMenuClose () {
    setQuickSelected(false, UI.inUiThread());
  }

  public interface Delegate {
    void onClick ();
  }

  private Delegate delegate;

  public void setDelegate (Delegate delegate) {
    this.delegate = delegate;
  }



  public static final int MODE_PERSON_BUTTON = 0;
  public static final int MODE_ANONYMOUS_BUTTON = 1;
  public static final int MODE_CHAT_BUTTON = 2;

  private static class ButtonView extends FrameLayout {
    private final AvatarView avatarView;

    private TdApi.MessageSender sender;
    private int mode = MODE_PERSON_BUTTON;
    private float sendModeFactor = 0f;
    private float quickSelectFactor = 0f;

    public ButtonView (@NonNull Context context) {
      super(context);

      avatarView = new AvatarView(context);
      avatarView.setId(R.id.btn_camera);
      avatarView.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(24), Screen.dp(24), Gravity.CENTER));
      addView(avatarView);

      setLayoutParams(FrameLayoutFix.newParams(Screen.dp(ATTACH_BUTTONS_WIDTH), LayoutParams.MATCH_PARENT, Gravity.RIGHT | Gravity.BOTTOM));
      setWillNotDraw(false);
    }

    public void setDrawMode (Tdlib tdlib, TdApi.MessageSender sender, int mode) {
      this.avatarView.setMessageSender(tdlib, sender);
      this.avatarView.setVisibility(mode == MODE_CHAT_BUTTON ? View.VISIBLE : View.GONE);
      this.sender = sender;
      this.mode = mode;

      invalidate();
    }

    public int getDrawMode () {
      return mode;
    }

    public void setSendModeFactor (float factor) {
      this.sendModeFactor = factor;
      invalidate();
    }

    public void setQuickSelectFactor (float factor) {
      this.quickSelectFactor = factor;
      invalidate();
    }

    @Override
    protected void onDraw (Canvas c) {
      float cx = getMeasuredWidth() / 2f;
      float cy = getMeasuredHeight() / 2f;
      float rb = Screen.dp(15.2f);
      float r = Screen.dp(12);
      float r2 = Screen.dp(10);

      if (mode != MODE_PERSON_BUTTON) {
        c.drawCircle(cx, cy, rb, Paints.fillingPaint(ColorUtils.alphaColor(sendModeFactor, Theme.fillingColor())));
      }

      if (mode != MODE_CHAT_BUTTON) {
        if (sendModeFactor != 1f) {
          int color = ColorUtils.alphaColor(1f - sendModeFactor, ColorUtils.fromToArgb(Theme.iconColor(), Theme.radioFillingColor(), quickSelectFactor));

          Drawable drawable = Drawables.get(getResources(), mode == MODE_ANONYMOUS_BUTTON ? R.drawable.dot_baseline_acc_anon_24 : R.drawable.dot_baseline_acc_personal_24);
          Drawables.draw(c, drawable, cx - r, cy - r, Paints.getPorterDuffPaint(color));
        }
        if (sendModeFactor != 0 && mode == MODE_ANONYMOUS_BUTTON) {
          c.drawCircle(cx, cy, r, Paints.fillingPaint(ColorUtils.alphaColor(sendModeFactor, Theme.iconLightColor())));
          Drawable drawable = Drawables.get(getResources(), R.drawable.infanf_baseline_incognito_20);
          Drawables.draw(c, drawable, cx - r2, cy - r2, Paints.getPorterDuffPaint(ColorUtils.alphaColor(sendModeFactor, Theme.getColor(ColorId.badgeMutedText))));
        }
      }
      super.onDraw(c);
    }

    @Override
    public boolean equals (Object obj) {
      return obj instanceof ButtonView && ((ButtonView ) obj).mode == this.mode && Td.getSenderId(((ButtonView) obj).sender) == Td.getSenderId(this.sender);
    }

    @Override
    public int hashCode () {
      return ((int) Td.getSenderId(sender)) + mode;
    }
  }
}
