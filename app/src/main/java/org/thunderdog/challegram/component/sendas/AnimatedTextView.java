package org.thunderdog.challegram.component.sendas;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.TextPaint;
import android.view.View;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;

public class AnimatedTextView extends View {

  private @Nullable String prefix;
  private @Nullable String text;
  private @Nullable String oldText;

  private final TextPaint prefixPaint;
  private final TextPaint textPaint;
  private final TextPaint oldTextPaint;

  public AnimatedTextView (Context context) {
    super(context);

    //setBackgroundColor(Color.RED);

    prefixPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    prefixPaint.setTypeface(Fonts.getRobotoRegular());
    prefixPaint.setTextSize(Screen.dp(11f));
    prefixPaint.setColor(Theme.textPlaceholderColor());

    textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    textPaint.setTypeface(Fonts.getRobotoRegular());
    textPaint.setTextSize(Screen.dp(11f));
    textPaint.setFakeBoldText(true);
    textPaint.setColor(Theme.textPlaceholderColor());

    oldTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    oldTextPaint.setTypeface(Fonts.getRobotoRegular());
    oldTextPaint.setTextSize(Screen.dp(11f));
    oldTextPaint.setFakeBoldText(true);
    oldTextPaint.setColor(Theme.textPlaceholderColor());
  }

  private FactorAnimator animator;
  private float factor = 1f;
  private boolean up = true;

  public void setPrefix(@Nullable String prefix) {
    this.prefix = prefix;
  }

  public void setText(String text, boolean animated) {
    this.up = text != null;

    if (animated) {
      this.oldText = this.text;
      this.text = text;

      if (animator == null) {
        animator = new FactorAnimator(1, new FactorAnimator.Target() {
          @Override
          public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
            AnimatedTextView.this.factor = factor;
            invalidate();
          }

          @Override
          public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
            AnimatedTextView.this.oldText = null;
          }
        }, AnimatorUtils.DECELERATE_INTERPOLATOR, 150L);
      }

      animator.forceFactor(0f, false);
      animator.animateTo(1f);
    } else {
      this.factor = 1f;
      this.oldText = null;
      this.text = text;
      invalidate();
    }
  }

  public void setNewText(String text) {
    this.oldText = this.text;
    this.text = text;

    this.factor = 0f;
    this.up = text != null;
  }

  public void setFactor(float factor) {
    this.factor = factor;
    if (factor == 1f) {
      this.oldText = null;
    }
    invalidate();
  }

  @Override
  protected void onDraw (Canvas canvas) {
    // TODO: paddings
    var x = 0;
    final var cy = getHeight() / 2;

    final var wholeText = text == null || oldText == null;
    final var dy = (int) (getHeight() * factor);

    if (prefix != null) {
      if (wholeText) {
        final var y = up ? getHeight() + cy - dy : cy + dy;
        prefixPaint.setAlpha((int) (255f * (up ? factor : 1f - factor)));
        canvas.drawText(prefix, x, y, prefixPaint);
      } else {
        prefixPaint.setAlpha(255);
        canvas.drawText(prefix, x, cy, prefixPaint);
      }
      x += prefixPaint.measureText(prefix);
    }
    if (oldText != null) {
      final var y = up ? cy - dy : cy + dy;
      oldTextPaint.setAlpha((int) (255f * (1f - factor)));
      canvas.drawText(oldText, x, y, oldTextPaint);
    }
    if (text != null) {
      final var y = up ? getHeight() + cy - dy : -cy + dy;
      textPaint.setAlpha((int) (255f * factor));
      canvas.drawText(text, x, y, textPaint);
    }
  }
}
