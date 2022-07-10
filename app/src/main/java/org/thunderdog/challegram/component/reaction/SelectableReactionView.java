package org.thunderdog.challegram.component.reaction;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.content.Context;
import android.graphics.Canvas;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeInvalidateListener;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.AttachDelegate;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.SimplestCheckBox;
import org.thunderdog.challegram.widget.SimplestCheckBoxHelper;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;

public class SelectableReactionView extends FrameLayoutFix implements AttachDelegate, SimplestCheckBoxHelper.Listener, ThemeInvalidateListener {

  private static final float UNSELECTED_ALPHA = 0.5f;
  private static final float CHECKBOX_SCALE = 0.7f;

  @NonNull
  private final ReactionView reactionView;

  @NonNull
  private final NoScrollTextView titleView;

  @NonNull
  private final SimplestCheckBox checkBox;
  @NonNull
  private final SimplestCheckBoxHelper checkBoxHelper;
  @ColorInt
  private int checkCheckColor, checkFillingColor;

  public SelectableReactionView (@NonNull Context context, @NonNull Tdlib tdlib, ViewController<?> themeProvider) {
    super(context);

    RippleSupport.setSimpleWhiteBackground(this, themeProvider);
    Views.setClickable(this);
    setClipChildren(false);

    checkCheckColor = Theme.fillingColor();
    checkFillingColor = Theme.checkFillingColor();

    checkBoxHelper = new SimplestCheckBoxHelper(this, null);
    checkBoxHelper.setIsChecked(false, false);

    checkBox = SimplestCheckBox.newInstance(checkBoxHelper.getCheckFactor(), null);

    reactionView = new ReactionView(context, tdlib);

    titleView = new NoScrollTextView(context);
    titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
    titleView.setTypeface(Fonts.getRobotoRegular());
    titleView.setTextColor(Theme.textAccentColor());
    titleView.setMaxLines(3);
    titleView.setEllipsize(TextUtils.TruncateAt.END);
    titleView.setGravity(Gravity.CENTER_HORIZONTAL);

    int reactionSize = Screen.dp(28f);
    addView(titleView, newParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, Screen.dp(6f), Screen.dp(64f), Screen.dp(6f), 0));
    addView(reactionView, newParams(reactionSize, reactionSize, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, Screen.dp(24f), 0, 0));

    setWillNotDraw(false);

    if (themeProvider != null) {
      themeProvider.addThemeTextAccentColorListener(titleView);
      themeProvider.addThemeInvalidateListener(this);
    }
    onCheckFactorChanged(0f);
  }

  public void setReaction (@Nullable TdApi.Reaction reaction) {
    reactionView.setReaction(reaction, true);
    reactionView.preloadActivateAnimation();
    if (reaction != null) {
      titleView.setText(reaction.title);
    } else {
      titleView.setText(null);
    }
  }

  public void playActivateAnimation () {
    reactionView.playActivateAnimation();
  }

  @Nullable
  public TdApi.Reaction getReaction () {
    return reactionView.getReaction();
  }

  @NonNull
  public ReactionView getReactionView () {
    return reactionView;
  }

  public void attach () {
    reactionView.attach();
  }

  public void detach () {
    reactionView.detach();
  }

  @Override
  protected boolean drawChild (Canvas canvas, View child, long drawingTime) {
    boolean result;
    float checkFactor = checkBoxHelper.getCheckFactor();
    if (child == reactionView && reactionView.getReaction() != null && checkFactor > 0f) {
      int saveCount = canvas.saveLayer(0, 0, getWidth(), getHeight(), null, Canvas.ALL_SAVE_FLAG);

      result = super.drawChild(canvas, child, drawingTime);

      int cx = reactionView.getRight() - Screen.dp(1f);
      int cy = reactionView.getBottom() + Screen.dp(1f);

      canvas.scale(CHECKBOX_SCALE, CHECKBOX_SCALE, cx, cy);

      float radius = SimplestCheckBox.size() / 2f * checkFactor;
      canvas.drawCircle(cx, cy, radius, Paints.getErasePaint());
      SimplestCheckBox.draw(canvas, cx, cy, checkFactor, null, checkBox, checkFillingColor, checkCheckColor, false, 0f);

      canvas.restoreToCount(saveCount);
    } else {
      result = super.drawChild(canvas, child, drawingTime);
    }
    return result;
  }

  @Override
  public void onCheckFactorChanged (float factor) {
    float alpha = MathUtils.clamp(UNSELECTED_ALPHA + (1f - UNSELECTED_ALPHA) * factor);
    reactionView.setAlpha(alpha);
    titleView.setAlpha(alpha);
  }

  @Override
  public void onThemeInvalidate (boolean isTempUpdate) {
    int checkCheckColor = Theme.fillingColor();
    int checkFillingColor = Theme.checkFillingColor();
    if (this.checkCheckColor != checkCheckColor || this.checkFillingColor != checkFillingColor) {
      this.checkCheckColor = checkCheckColor;
      this.checkFillingColor = checkFillingColor;
      invalidate();
    }
  }

  public void setChecked (boolean isChecked, boolean animated) {
    checkBoxHelper.setIsChecked(isChecked, animated);
  }

  public boolean toggle () {
    boolean isChecked = !checkBoxHelper.isChecked();
    checkBoxHelper.setIsChecked(isChecked, true);
    return isChecked;
  }
}
