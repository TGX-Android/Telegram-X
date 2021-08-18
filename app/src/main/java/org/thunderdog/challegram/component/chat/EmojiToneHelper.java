package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.emoji.EmojiInfo;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.EmojiData;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.widget.BubbleLayout;
import org.thunderdog.challegram.widget.NoScrollTextView;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;

/**
 * Date: 3/6/18
 * Author: default
 */

public class EmojiToneHelper implements FactorAnimator.Target {
  public interface Delegate {
    int[] displayBaseViewWithAnchor (EmojiToneHelper context, View anchorView, View viewToDisplay, int viewWidth, int viewHeight, int horizontalMargin, int horizontalOffset, int verticalOffset);
    void removeView (EmojiToneHelper context, View displayedView);
  }

  public static int[] defaultDisplay (EmojiToneHelper context, View anchorView, View viewToDisplay, int viewWidth, int viewHeight, int horizontalMargin, int horizontalOffset, int verticalOffset, ViewGroup contentView, ViewGroup bottomWrap, ViewGroup emojiLayout) {
    final int x = anchorView.getLeft();
    final int y = anchorView.getTop() + bottomWrap.getTop() + emojiLayout.getTop();

    final int desiredLeft = x + horizontalOffset;
    final int left = Math.max(horizontalMargin, Math.min(contentView.getMeasuredWidth() - Math.max(0, viewWidth) - horizontalMargin, desiredLeft));

    final int top = y - viewHeight + verticalOffset;

    RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(viewWidth, viewHeight);
    params.topMargin = top;
    params.leftMargin = left;
    viewToDisplay.setLayoutParams(params);

    contentView.addView(viewToDisplay);

    return new int[] {left - x, top - y};
  }

  private final Context context;
  private final Delegate delegate;
  private final @Nullable ViewController themeProvider;

  public EmojiToneHelper (Context context, Delegate delegate, @Nullable ViewController themeProvider) {
    this.context = context;
    this.delegate = delegate;
    this.themeProvider = themeProvider;
  }

  // Entry point

  private String emoji;
  private int emojiColorState;
  private String currentTone;
  private String[] currentOtherTones;
  private boolean startedMoving;

  private float startX;
  private int offsetLeft, offsetTop;
  private int buttonOffsetLeft, buttonOffsetTop;

  private static final int ANIMATOR_POSITION = 1;

  public boolean openForEmoji (View anchorView, float startX, float startY, String emoji, int emojiColorState, String currentTone, String[] currentOtherTones) {
    this.emoji = emoji;
    this.emojiColorState = emojiColorState;
    this.currentTone = currentTone;
    int colorCount = emojiColorState >= EmojiData.STATE_HAS_TWO_COLORS ? (emojiColorState - EmojiData.STATE_HAS_TWO_COLORS + 1) : 0;
    if (colorCount > 0) {
      if (currentOtherTones != null) {
        if (currentOtherTones.length < colorCount) {
          String[] result = new String[colorCount];
          System.arraycopy(currentOtherTones, 0, result, 0, currentOtherTones.length);
          currentOtherTones = result;
        }
      } else {
        currentOtherTones = new String[colorCount];
      }
    }
    this.currentOtherTones = currentOtherTones;
    this.startedMoving = this.showingApplyButton = this.applyButtonPressed = this.toneChanged = this.applyButtonClicked = false;
    this.startX = startX;
    this.offsetLeft = offsetTop = 0;
    this.buttonOffsetLeft = buttonOffsetTop = 0;
    setIsVisible(anchorView, true);
    return true;
  }

  public void hide (View anchorView) {
    setIsVisible(anchorView, false);
  }

  private boolean toneChanged;

  public void processMovement (View anchorView, MotionEvent e, float x, float y) {
    if (emojiTonePicker == null) {
      return;
    }
    int viewWidth = emojiTonePicker.getMeasuredWidth();
    if (viewWidth == 0) {
      return;
    }
    if (!startedMoving) {
      startedMoving = Math.abs(x - startX) > Screen.getTouchSlop() || y < 0;
    }
    if (startedMoving) {
      float relativeX = x - offsetLeft;
      float relativeY = y - offsetTop;
      switch (emojiColorState) {
        case EmojiData.STATE_NO_COLORS:
          // Do nothing
          break;
        case EmojiData.STATE_HAS_ONE_COLOR:
          // Offer to apply tone to all emoji
          processSingleToneMovement(anchorView, e, x, y, relativeX, relativeY);
          break;
        case EmojiData.STATE_HAS_TWO_COLORS:
        default:
          // Show picker of the more tones, no apply to all
          processMultipleToneMovement(anchorView, e, x, y, relativeX, relativeY, emojiColorState - EmojiData.STATE_HAS_TWO_COLORS + 1);
          break;
      }
    }
  }

  private void processSingleToneMovement (View anchorView, MotionEvent e, float x, float y, float relativeX, float relativeY) {
    boolean updatePosition = false;

    if (relativeY >= 0 && !applyButtonPressed && emojiTonePicker.changeIndex(relativeX)) {
      toneChanged = true;
      currentTone = EmojiData.emojiColors[emojiTonePicker.toneIndex];
      updatePosition = true;
      if (!needPositionAnimation()) {
        updatePosition = false;
        animatePosition(false);
      }
    }

    boolean canShowApply = Emoji.instance().canApplyDefaultTone(currentTone);
    if (showingApplyButton) {
      if (!canShowApply || (relativeY > Screen.dp(46f) && !(toneChanged && (Config.SHOW_EMOJI_TONE_PICKER_ALWAYS || Settings.instance().needTutorial(Settings.TUTORIAL_EMOJI_TONE_ALL))))) {
        setApplyButtonPressed(anchorView, false, e, x - buttonOffsetLeft - applyButton.getTranslationX(), y - buttonOffsetTop);
        setShowingApplyButton(anchorView, false);
        updatePosition = false;
      }
    } else if (canShowApply) {
      if (relativeY < Screen.dp(46f) * .95f || (toneChanged && (Config.SHOW_EMOJI_TONE_PICKER_ALWAYS || Settings.instance().needTutorial(Settings.TUTORIAL_EMOJI_TONE_ALL)))) {
        setShowingApplyButton(anchorView, true);
        // UI.forceVibrate(anchorView, false);
      }
    }
    if (showingApplyButton) {
      float buttonX = x - buttonOffsetLeft - applyButton.getTranslationX();
      float buttonY = y - buttonOffsetTop;
      if (!setApplyButtonPressed(anchorView, buttonX >= 0 && buttonX < applyButton.getMeasuredWidth() && (buttonY >= (applyButtonPressed ? -Screen.dp(92f) : 0)) && buttonY < applyButton.getMeasuredHeight(), e, buttonX, buttonY) && applyButtonPressed) {
        sendView.dispatchTouchEvent(MotionEvent.obtain(e.getDownTime(), e.getEventTime(), MotionEvent.ACTION_MOVE, Math.max(0, Math.min(buttonX, sendView.getMeasuredWidth())), Math.max(0, Math.min(buttonY, sendView.getMeasuredHeight())), e.getMetaState()));
      }
    }

    if (updatePosition) {
      animatePosition(needPositionAnimation());
    }
  }

  private void processMultipleToneMovement (View anchorView, MotionEvent e, float x, float y, float relativeX, float relativeY, int toneCount) {
    processSingleToneMovement(anchorView, e, x, y, relativeX, relativeY);
    // TODO
    /*int canShowCount;
    if (Strings.isEmpty(currentTone)) {
      canShowCount = 1;
    } else {
      canShowCount = 2;
      for (String otherTone : currentOtherTones) {
        if (Strings.isEmpty(otherTone))
          break;
        canShowCount++;
      }
    }*/
  }

  private float positionFactor;
  private FactorAnimator positionAnimator;

  private void setPositionFactor (float factor, boolean animated) {
    if (animated) {
      if (positionAnimator == null) {
        positionAnimator = new FactorAnimator(ANIMATOR_POSITION, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.positionFactor);
      }
      positionAnimator.animateTo(factor);
    } else {
      if (positionAnimator != null) {
        positionAnimator.forceFactor(factor);
      }
      setPositionFactorImpl(factor);
    }
  }

  private void setPositionFactorImpl (float factor) {
    if (this.positionFactor != factor) {
      this.positionFactor = factor;
      updateApplyPosition();
    }
  }

  public String getSelectedEmoji () {
    return emoji;
  }

  public String getSelectedTone () {
    return currentTone;
  }

  public String[] getSelectedOtherTones () {
    int count = 0;
    if (currentOtherTones != null) {
      for (String emoji : currentOtherTones) {
        if (!StringUtils.isEmpty(emoji)) {
          count++;
        } else {
          if (count > 0) {
            String[] result = new String[count];
            System.arraycopy(currentOtherTones, 0, result, 0, count);
            return result;
          } else {
            return null;
          }
        }
      }
    }
    return count > 0 ? currentOtherTones : null;
  }

  public boolean needApplyToAll () {
    return applyButtonPressed || applyButtonClicked;
  }

  public boolean needForgetApplyToAll () {
    return !applyButtonClicked;
  }

  //

  public boolean canBeShown () {
    return delegate != null && !isVisible();
  }

  private static final int ANIMATOR_VISIBILITY = 0;
  private BoolAnimator visibilityAnimator;
  private boolean isVisible () {
    return visibilityAnimator != null && visibilityAnimator.getValue();
  }
  private void setIsVisible (View anchorView, boolean isVisible) {
    boolean wasVisible = isVisible();
    if (wasVisible != isVisible) {
      if (visibilityAnimator == null) {
        visibilityAnimator = new BoolAnimator(ANIMATOR_VISIBILITY, this, AnimatorUtils.OVERSHOOT_INTERPOLATOR, 210l);
      } else {
        if (isVisible && visibilityAnimator.getFloatValue() == 0f) {
          visibilityAnimator.setInterpolator(AnimatorUtils.OVERSHOOT_INTERPOLATOR);
          visibilityAnimator.setDuration(210l);
        } else {
          visibilityAnimator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
          visibilityAnimator.setDuration(100l);
        }
      }
      boolean byLayout = prepareWrap(anchorView);
      visibilityAnimator.setValue(isVisible, true, byLayout ? emojiTonePicker : null);
    }
  }
  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_VISIBILITY: {
        if (emojiTonePicker != null) {
          final float scale = .8f + .2f * factor;
          emojiTonePicker.setScaleX(scale);
          emojiTonePicker.setScaleY(scale);
          emojiTonePicker.setAlpha(MathUtils.clamp(factor));
        }
        if (applyButton != null) {
          applyButton.setMaxAllowedVisibility(factor);
        }
        break;
      }
      case ANIMATOR_POSITION: {
        setPositionFactorImpl(factor);
        break;
      }
    }
  }
  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_VISIBILITY: {
        if (finalFactor == 0f && emojiTonePicker != null) {
          destroyWrap();
        }
        break;
      }
    }
  }

  private EmojiToneListView emojiTonePicker;
  private BubbleLayout applyButton;
  private TextView sendView;

  private boolean showingApplyButton, applyButtonPressed;
  private boolean applyButtonClicked;

  private void updateApplyPosition () {
    if (applyButton != null) {
      int paddingHorizontal = Screen.dp(4.5f);
      int totalWidth = (Screen.dp(TONES_WIDTH) - paddingHorizontal * 2);
      int itemWidth = totalWidth / EmojiData.emojiColors.length;

      float factor = positionFactor / (float) (EmojiData.emojiColors.length - 1);
      int buttonWidth = applyButton.getMeasuredWidth();
      int margin = itemWidth / 2;
      applyButton.setCornerCenterX(margin + (int) ((float) (buttonWidth - margin * 2) * factor));
      applyButton.setTranslationX((float) (totalWidth - buttonWidth) * factor);
    }
  }

  private boolean setApplyButtonPressed (View anchorView, boolean isPressed, MotionEvent e, float x, float y) {
    if (this.applyButtonPressed != isPressed) {
      this.applyButtonPressed = isPressed;
      x = Math.max(0, Math.min(sendView.getMeasuredWidth(), x));
      y = Math.max(0, Math.min(sendView.getMeasuredHeight(), y));
      sendView.dispatchTouchEvent(MotionEvent.obtain(e.getDownTime(), e.getEventTime(), isPressed ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_CANCEL, x, y, e.getMetaState()));
      if (isPressed) {
        UI.forceVibrate(anchorView, false);
      }
      return true;
    }
    return false;
  }

  private void setShowingApplyButton (View anchorView, boolean showingApplyButton) {
    if (this.showingApplyButton == showingApplyButton) {
      return;
    }

    this.showingApplyButton = showingApplyButton;

    if (applyButton != null) {
      applyButton.setBubbleVisible(showingApplyButton, null);
      return;
    } else if (!showingApplyButton) {
      return;
    }

    int paddingHorizontal = Screen.dp(4.5f);
    int itemWidth = paddingHorizontal + (Screen.dp(TONES_WIDTH) - paddingHorizontal * 2) / EmojiData.emojiColors.length;

    applyButton = new BubbleLayout(context, themeProvider, false) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        updateApplyPosition();
      }
    };
    applyButton.setCornerCenterX(itemWidth / 2);
    applyButton.setMaxAllowedVisibility(visibilityAnimator != null ? visibilityAnimator.getFloatValue() : 0f);
    applyButton.setBubbleVisible(true, applyButton);

    sendView = new NoScrollTextView(context);
    sendView.setId(R.id.btn_send);
    sendView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    sendView.setTextColor(Theme.getColor(R.id.theme_color_textNeutral));
    if (themeProvider != null) {
      themeProvider.addThemeTextColorListener(sendView, R.id.theme_color_textNeutral);
    }
    sendView.setTypeface(Fonts.getRobotoMedium());
    Views.setMediumText(sendView, Lang.getString(R.string.ApplyToAll).toUpperCase());
    sendView.setOnClickListener(v -> {
      if (emojiTonePicker != null && emojiTonePicker.getAnchorView() instanceof EmojiView && isVisible()) {
        applyButtonClicked = true;
        ((EmojiView) emojiTonePicker.getAnchorView()).completeToneSelection();
      }
    });
    RippleSupport.setTransparentBlackSelector(sendView);
    // FIXME rounded corners when button is pressed
    /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      sendView.setOutlineProvider(applyButton.getOutlineProvider());
    }*/
    sendView.setPadding(Screen.dp(16f), 0, Screen.dp(16f), 0);
    sendView.setGravity(Gravity.CENTER);
    sendView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    applyButton.addView(sendView);

    int tonesHeight = Screen.dp(46f) + Screen.dp(2f) + Screen.dp(4f) + Screen.dp(8f);

    int[] result = delegate.displayBaseViewWithAnchor(this, anchorView, applyButton, ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(48f) + applyButton.getPaddingTop() + applyButton.getPaddingBottom(), Screen.dp(4f), offsetLeft, Screen.dp(8f) - tonesHeight + Screen.dp(6f));

    buttonOffsetLeft = result[0];
    buttonOffsetTop = result[1];
  }

  private int positionIndex;

  private boolean needPositionAnimation () {
    return applyButton != null && applyButton.isBubbleVisible() && isVisible() && visibilityAnimator.getFloatValue() > 0f;
  }

  private void animatePosition (boolean animate) {
    if (positionIndex != emojiTonePicker.toneIndex || !animate) {
      positionIndex = emojiTonePicker.toneIndex;
      setPositionFactor(emojiTonePicker.toneIndex, animate);
    }
  }

  private boolean prepareWrap (View anchorView) {
    if (emojiTonePicker != null) {
      return false;
    }

    emojiTonePicker = new EmojiToneListView(context);
    emojiTonePicker.init(themeProvider, false);
    emojiTonePicker.setEmoji(emoji, currentTone);
    if (themeProvider != null) {
      themeProvider.addThemeInvalidateListener(emojiTonePicker);
    }

    animatePosition(false);

    int tonesWidth = Screen.dp(TONES_WIDTH);
    int tonesHeight = Screen.dp(46f) + Screen.dp(2f) + Screen.dp(4f) + Screen.dp(8f);
    int[] result = delegate.displayBaseViewWithAnchor(this, anchorView, emojiTonePicker, tonesWidth, tonesHeight, Screen.dp(4f), anchorView.getMeasuredWidth() / 2 - Math.min(Screen.dp(23f), tonesWidth / 2), Screen.dp(8f));

    offsetLeft = result[0];
    offsetTop = result[1];

    emojiTonePicker.setAnchorView(anchorView, offsetLeft);

    return true;
  }

  private static final float TONES_WIDTH = 240f;

  private static class EmojiToneListView extends View {
    private EmojiInfo[] tones;
    private Drawable backgroundDrawable, cornerDrawable;

    public EmojiToneListView (Context context) {
      super(context);
    }

    private void init (ViewController themeProvider, boolean is2d) {
      this.tones = new EmojiInfo[EmojiData.emojiColors.length - (is2d ? 1 : 0)];
      this.backgroundDrawable = Theme.filteredDrawable(R.drawable.stickers_back_all, R.id.theme_color_overlayFilling, themeProvider);
      this.cornerDrawable = Theme.filteredDrawable(R.drawable.stickers_back_arrow, R.id.theme_color_overlayFilling, themeProvider);
    }

    private View boundView;
    private int offsetLeft;

    public void setAnchorView (View view, int offsetLeft) {
      this.boundView = view;
      this.offsetLeft = offsetLeft;
      setPivotX(view.getMeasuredWidth() / 2 - offsetLeft);
      setPivotY(Screen.dp(46f) + Screen.dp(3.5f) + Screen.dp(8f) / 2);
    }

    public View getAnchorView () {
      return boundView;
    }

    private int toneIndex = -1;

    public void setEmoji (String emoji, String currentTone) {
      int i = 0;
      for (String tone : EmojiData.emojiColors) {
        if (tone == null && currentTone == null) {
          toneIndex = 0;
        } else if (StringUtils.equalsOrBothEmpty(tone, currentTone)) {
          toneIndex = i;
        }
        tones[i] = Emoji.instance().getEmojiInfo(EmojiData.instance().colorize(emoji, tone));
        i++;
      }
    }

    public boolean changeIndex (float x) {
      int paddingHorizontal = Screen.dp(4.5f);
      x -= paddingHorizontal;
      int viewWidth = (Screen.dp(TONES_WIDTH) - paddingHorizontal * 2);

      int result;
      if (x <= 0) {
        result = 0;
      } else if (x >= viewWidth) {
        result = tones.length - 1;
      } else {
        result = Math.max(0, Math.min(tones.length - 1, (int) ((x / (float) viewWidth) * ((float) tones.length))));
      }

      if (result != -1 && result != toneIndex) {
        toneIndex = result;
        invalidate();
        return true;
      }
      return false;
    }

    @Override
    protected void onDraw (Canvas c) {
      int paddingVertical = Screen.dp(4f);
      int paddingHorizontal = Screen.dp(4.5f);
      int itemWidth = (Screen.dp(TONES_WIDTH) - paddingHorizontal * 2) / tones.length;
      int itemHeight = Screen.dp(46f);

      int x = paddingHorizontal;
      int y = Screen.dp(2.5f);

      backgroundDrawable.setBounds(0, 0, getMeasuredWidth(),  itemHeight + paddingVertical * 2);
      backgroundDrawable.draw(c);

      int cornerWidth = Screen.dp(18f);
      int cornerHeight = Screen.dp(8f);
      int cornerX = 0;
      int cornerY =  itemHeight + Screen.dp(3.5f);
      if (boundView != null) {
        cornerX = boundView.getMeasuredWidth() / 2 - offsetLeft - cornerWidth / 2;
      }
      cornerDrawable.setBounds(cornerX, cornerY, cornerX + cornerWidth, cornerY + cornerHeight);
      cornerDrawable.draw(c);

      int rectX = x + itemWidth * toneIndex;
      int rectY = y + paddingVertical / 2;
      RectF rectF = Paints.getRectF();
      rectF.set(rectX, rectY, rectX + itemWidth, rectY + itemHeight - paddingVertical);
      c.drawRoundRect(rectF, Screen.dp(4f), Screen.dp(4f), Paints.fillingPaint(Theme.HALF_RIPPLE_COLOR));

      for (EmojiInfo info : tones) {
        int cx = x + itemWidth / 2;
        int cy = y + itemHeight / 2;
        int itemSize = Math.min(itemWidth, itemHeight) - Screen.dp(4f);
        Rect rect = Paints.getRect();
        rect.left = cx - itemSize / 2;
        rect.top = cy - itemSize / 2;
        rect.right = rect.left + itemSize;
        rect.bottom = rect.top + itemSize;
        Emoji.instance().draw(c, info, rect);
        x += itemWidth;
      }
    }
  }

  private void destroyWrap () {
    if (emojiTonePicker != null) {
      delegate.removeView(this, emojiTonePicker);
      if (themeProvider != null) {
        themeProvider.removeThemeListenerByTarget(emojiTonePicker);
      }
      emojiTonePicker = null;
    }
    if (sendView != null) {
      if (themeProvider != null) {
        themeProvider.removeThemeListenerByTarget(sendView);
      }
      sendView = null;
    }
    if (applyButton != null) {
      delegate.removeView(this, applyButton);
      applyButton.removeThemeListeners();
      applyButton = null;
    }
  }
}
