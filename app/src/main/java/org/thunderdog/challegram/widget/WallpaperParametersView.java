package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.core.ColorUtils;

public class WallpaperParametersView extends View {
    private WallpaperParametersListener listener;
    private boolean isInitialBlur;
    private boolean isBlurClicked;

    private final Paint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final RectF blurRect = new RectF();

    private final BoolAnimator isBlurEnabled = new BoolAnimator(0, (id, factor, fraction, callee) -> {
        if (listener != null) listener.onBlurValueChanged(isInitialBlur ? 1f - factor : factor);
        invalidate();
    }, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

    public WallpaperParametersView (Context context) {
        super(context);
        this.textPaint.setColor(Theme.textAccentColor());
        this.textPaint.setTypeface(Fonts.getRobotoRegular());
        this.textPaint.setTextSize(Screen.sp(14f));
        setWillNotDraw(false);
    }

    public void initWith (TdApi.Background background, @Nullable WallpaperParametersListener listener) {
        this.isBlurEnabled.setValue(((TdApi.BackgroundTypeWallpaper) background.type).isBlurred, false);
        this.isInitialBlur = this.isBlurEnabled.getValue();
        this.listener = listener;
    }

    public boolean isBlurred () {
        return isBlurEnabled.getValue();
    }

    @Override
    protected void onDraw (Canvas c) {
        drawButton(c, getWidth() / 2, getHeight() / 2, blurRect, Lang.getString(R.string.ChatBackgroundBlur), isBlurEnabled);
    }

    private void drawButton (Canvas c, int centerX, int centerY, RectF buttonRect, String text, BoolAnimator selectAnimator) {
        float textWidth = this.textPaint.measureText(text);
        float checkboxScale = .75f;
        float checkboxSize = (SimplestCheckBox.size() * checkboxScale);
        float offset = (textWidth / 2) - checkboxSize;
        int checkboxX = centerX - (int) (checkboxSize) / 2 - Screen.dp(8f) + (int) (Screen.dp(2f) * checkboxScale) - (int) offset;
        int checkboxY = centerY - (int) (Screen.dp(2f) * checkboxScale);

        buttonRect.top = checkboxY - checkboxSize;
        buttonRect.bottom = checkboxY + checkboxSize;
        buttonRect.left = checkboxX - checkboxSize;
        buttonRect.right = centerX + textWidth + (int) (checkboxSize / 1.5) - offset;
        c.drawRoundRect(buttonRect, Screen.dp(16f), Screen.dp(16f), Paints.fillingPaint(Theme.getColor(R.id.theme_color_previewBackground)));

        c.drawText(text, centerX - offset, centerY + Screen.sp(4f), textPaint);

        c.save();
        c.scale(checkboxScale, checkboxScale, checkboxX, centerY);
        c.drawCircle(checkboxX, checkboxY, checkboxSize / 2, Paints.getProgressPaint(Theme.getColor(R.id.theme_color_text), Screen.dp(2f)));
        SimplestCheckBox.draw(c, checkboxX, checkboxY, selectAnimator.getFloatValue(), null);
        c.restore();
    }

    @Override
    public boolean onTouchEvent (MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                return isBlurClicked = blurRect.contains(event.getX(), event.getY());
            }

            case MotionEvent.ACTION_UP: {
                if (isBlurClicked) {
                    isBlurClicked = false;
                    isBlurEnabled.toggleValue(true);
                }

                break;
            }
        }

        return super.onTouchEvent(event);
    }

    public interface WallpaperParametersListener {
        void onBlurValueChanged (float factor);
    }
}
