package org.thunderdog.challegram.component.reactions;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Counter;

import androidx.annotation.NonNull;
import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.lambda.Destroyable;

public class Reaction implements Destroyable, FactorAnimator.Target {
    private static final long ANIMATION_DURATION = 180L;
    private static final int LONG_PRESS_ANIMATOR = 1;
    private static final int CHOOSE_ANIMATOR = 2;
    private static final int DEFAULT_SIZE = 12;
    private static final int DEFAULT_H_PADDING = 0;
    private static final int DEFAULT_V_PADDING = 0;
    private static final int DEFAULT_TEXT_PADDING_START = 4;
    private static final int DEFAULT_BG_RADIUS = 8;
    private static final int DEFAULT_TEXT_SIZE = 12;
    private static final int STATE_RECEIVER = 0;
    private static final int EFFECT_RECEIVER = 1;

    private boolean isAttached = false;
    private ComplexReceiver complexReceiver;
    private TGStickerObj sticker;
    private TGStickerObj effectSticker;
    private TGReaction reaction;
    private boolean hasBackground = false;
    private final RectF backgroundRect = new RectF();
    private boolean isChosen;
    private int width, height;
    private int vPadding;
    private int hPadding;
    private int textPaddingStart;
    private int bgRadius;
    private int stickerWidth = 0, stickerHeight = 0;
    private int effectStickerWidth = 0, effectStickerHeight = 0;
    private int textSize = DEFAULT_TEXT_SIZE;
    private final Paint backgroundPaint;
    private final Paint selectionPaint;
    private final Paint choosePaint;
    private Counter counter;
    private DisplayMode displayMode = DisplayMode.MINI;
    private int currentX;
    private int currentY;
    private View attachedView;
    private float longPressFactor = 0;
    private float chooseFactor = 0;
    private final FactorAnimator longPressAnimator = new FactorAnimator(LONG_PRESS_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, ANIMATION_DURATION);
    private final FactorAnimator chooseAnimator = new FactorAnimator(CHOOSE_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, ANIMATION_DURATION);
    private boolean isPressed = false;
    private boolean shouldPlayEffect = false;

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        if (id == LONG_PRESS_ANIMATOR) {
            longPressFactor = factor;
        }else if (id == CHOOSE_ANIMATOR) {
            chooseFactor = factor;
        }
        invalidate();
    }

    @Override
    public void onFactorChangeFinished(int id, float finalFactor, FactorAnimator callee) {
        if (id == LONG_PRESS_ANIMATOR) {
            longPressFactor = 0f;
            if (!isPressed) {
                invalidate();
            }
        }
    }

    public enum DisplayMode {
        MINI,
        BIG
    }

    public Reaction(@NonNull TGReaction reaction) {
        this.reaction = reaction;
        sticker = reaction.getStateSticker();
        effectSticker = reaction.getEffectAnimation();
        shouldPlayEffect = reaction.getState() == TGReaction.State.ACTIVATING;
        isChosen  = this.reaction.isChosen();
        chooseFactor = isChosen ? 1f : 0;
        width = Screen.dp(DEFAULT_SIZE);
        height = Screen.dp(DEFAULT_SIZE);
        vPadding = Screen.dp(DEFAULT_V_PADDING);
        hPadding = Screen.dp(DEFAULT_H_PADDING);
        bgRadius = height / 2;
        textPaddingStart = Screen.dp(DEFAULT_TEXT_PADDING_START);
        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        backgroundPaint.setStyle(Paint.Style.FILL);
        selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        selectionPaint.setStyle(Paint.Style.FILL);
        choosePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        choosePaint.setStyle(Paint.Style.FILL);
        buildCounter();
        resetCounter(false);
    }

    public void updateReaction(@NonNull TGReaction reaction, boolean animated) {
        this.reaction = reaction;
        boolean hadChosen = isChosen;
        boolean isChosen = this.reaction.isChosen();
        if (hadChosen != isChosen) {
            this.isChosen  = isChosen;
            if (animated) {
                chooseAnimator.forceFactor(this.isChosen ? 0f : 1f);
                chooseAnimator.animateTo(this.isChosen ? 1f : 0f);
            } else {
                chooseFactor = this.isChosen ? 1f : 0;
            }
        }
        resetCounter(animated);
    }

    public void setDisplayMode(DisplayMode displayMode) {
        this.displayMode = displayMode;
    }

    public TGReaction getTGReaction() {
        return reaction;
    }

    public boolean handleTouch(float x, float y) {
        return x > currentX && x < currentX + getWidth()
                && y > currentY && y < currentY + getHeight();
    }

    public void performTouchDown() {
        isPressed = true;
        cancelStickerAnimation();
    }

    public void performTouchUp() {
        if (isPressed){
            isPressed = false;
            boolean isChosenNow = isChosen;
            isChosen = !isChosen;
            if (isChosen) {
                effectSticker = reaction.getEffectAnimation();
                sticker = reaction.getActivateAnimation();
                shouldPlayEffect = true;
            } else {
                sticker = reaction.getStaticIcon();
            }
            measureStickers();
            update();
            chooseAnimator.forceFactor(isChosenNow ? 1f : 0f);
            chooseAnimator.animateTo(isChosenNow ? 0f : 1f);
        }
    }

    private void cancelStickerAnimation() {
        if (complexReceiver != null) {
            GifFile effectFile = complexReceiver.getGifReceiver(EFFECT_RECEIVER).getCurrentFile();
            GifFile stickerFile = complexReceiver.getGifReceiver(STATE_RECEIVER).getCurrentFile();
            if (effectFile != null && !effectFile.hasLooped()) {
                effectFile.setLooped(true);
            }
            if (stickerFile != null && !stickerFile.hasLooped()) {
                stickerFile.setLooped(true);
            }
            complexReceiver.getGifReceiver(EFFECT_RECEIVER).clear();
            complexReceiver.getGifReceiver(STATE_RECEIVER).clear();
        }
    }

    public boolean performLongPress(float x, float y) {
        if (!handleTouch(x, y)) return false;
        isPressed = false;
        if (longPressAnimator.isAnimating()) {
            longPressAnimator.cancel();
        }
        longPressAnimator.forceFactor(0f);
        longPressAnimator.animateTo(1f);
        return true;
    }

    public void measure() {
       measureStickers();
       int textWidth = shouldDisplayTotalCount() ? ((int) counter.getScaledWidth(0) + textPaddingStart) : 0;
       width = stickerWidth + 2 * hPadding + textWidth;
    }

    private void measureStickers() {
        if (reaction == null || sticker == null) return;
        int[] size = measureSticker(sticker, getHeight(reaction.getState() != TGReaction.State.IDDLE));
        stickerWidth = size[0];
        stickerHeight = size[1];
        if (effectSticker != null) {
            int[] effectSize = measureSticker(sticker, Screen.dp(60));
            effectStickerWidth = effectSize[0];
            effectStickerHeight = effectSize[1];
        }
    }

    private int[] measureSticker(TGStickerObj sticker, float max) {
        float ratio = Math.min(max / (float) sticker.getWidth(), max / (float) sticker.getHeight());
        int stickerHeight = (int) (sticker.getHeight() * ratio);
        int stickerWidth = (int) (sticker.getWidth() * ratio);
        return new int[] { stickerWidth, stickerHeight };
    }

    public int getWidth() {
        return getWidth(true);
    }

    public int getHeight() {
        return getHeight(true);
    }

    public void setHeight(int height) {
        this.height = height;
        this.bgRadius = height / 2;
    }

    public void setHeightDp(int heightDp) {
        setHeight(Screen.dp(heightDp));
    }

    public boolean hasBackground() {
        return hasBackground;
    }

    public void setHasBackground(boolean hasBackground) {
        if (hasBackground != this.hasBackground) {
            this.hasBackground = hasBackground;
        }
    }

    public void setTextSize(int textSize) {
        this.textSize = textSize;
    }

    public void setTextPaddingStart(int textPaddingStartDp) {
        this.textPaddingStart = Screen.dp(textPaddingStartDp);
    }

    public int getVerticalPadding() {
        return vPadding;
    }

    public void setVerticalPadding(int vPaddingDp) {
        this.vPadding = Screen.dp(vPaddingDp);
    }

    public int getHorizontalPadding() {
        return hPadding;
    }

    public void setHorizontalPadding(int hPaddingDp) {
        this.hPadding = Screen.dp(hPaddingDp);
    }

    public int getTotalCount() {
        return this.reaction.getTotalCount();
    }

    public void attachToView(View view) {
        isAttached = true;
        attachedView = view;
        complexReceiver = new ComplexReceiver(view);
        complexReceiver.attach();
        update();
    }

    public void detachFromView() {
        isAttached = false;
        attachedView = null;
        if (complexReceiver != null) {
            complexReceiver.detach();
            complexReceiver.performDestroy();
            complexReceiver = null;
        }
    }

    public void update() {
        if (sticker == null || !isAttached || complexReceiver == null) return;

        complexReceiver.getPreviewReceiver(STATE_RECEIVER).clear();
        if (sticker.isAnimated()) {
            GifFile file = sticker.getFullAnimation();
            file.setPlayOnce(true);
            file.setLooped(false);
            complexReceiver.getGifReceiver(STATE_RECEIVER).requestFile(file);
        } else {
            complexReceiver.getImageReceiver(STATE_RECEIVER)
                    .requestFile(sticker.getFullImage());
        }

        if (shouldPlayEffect && effectSticker != null) {
            complexReceiver.getPreviewReceiver(EFFECT_RECEIVER).clear();
            GifFile effect = effectSticker.getFullAnimation();
            effect.setPlayOnce(true);
            effect.addLoopListener(() -> {
                shouldPlayEffect = false;
            });
            complexReceiver.getGifReceiver(EFFECT_RECEIVER).requestFile(effect);
        }
    }

    public void draw(Canvas canvas, int left, int top) {
        if (sticker == null || !isAttached || complexReceiver == null) return;
        int startX = left;
        int startY = top;
        currentX = startX;
        currentY = startY;
        if (hasBackground) {
            drawBackground(canvas, startX, startY);
        }

        startX += hPadding;
        startY += vPadding;

        if (shouldPlayEffect && effectSticker != null) {
            drawEffect(canvas, startX, startY);
        }
        drawSticker(canvas, startX, startY);
        startX += stickerWidth;

        if (shouldDisplayTotalCount()) {
            startX += textPaddingStart;
            drawTotalCount(canvas, startX, startY);
        }

    }

    @Override
    public void performDestroy() {
        if (complexReceiver != null) {
            complexReceiver.performDestroy();
            complexReceiver = null;
        }
    }

    private void invalidate() {
        if (attachedView != null) {
            Rect dirty = new Rect(
                    (int) backgroundRect.left,
                    (int) backgroundRect.top,
                    (int) backgroundRect.right,
                    (int) backgroundRect.bottom
            );
            attachedView.invalidate(dirty);
        }
    }

    private void resetCounter(boolean animated) {
        this.counter.setCount(this.reaction.getTotalCount(), !isChosen, animated);
    }

    private void buildCounter() {
        this.counter = new Counter.Builder()
                .callback((counter1, sizeChanged) -> this.invalidate())
                .textSize(this.textSize)
                .textColor(R.id.theme_color_badgeText, Theme.isDark() ? R.id.theme_color_badgeText : R.id.theme_color_badge, R.id.theme_color_badgeText)
                .noBackground()
                .build();
    }

    private int getWidth(boolean includePadding) {
        if (includePadding) {
            return width;
        } else {
            return width - 2 * hPadding;
        }
    }

    private int getHeight(boolean includePadding) {
        if (includePadding) {
            return height;
        } else {
            return height - 2 * vPadding;
        }

    }

    private void drawSticker(Canvas canvas, int left, int top){
        int right = left + stickerWidth;
        int bottom = top + stickerHeight;
        DrawAlgorithms.drawReceiver(
                canvas,
                complexReceiver.getPreviewReceiver(STATE_RECEIVER),
                sticker.isAnimated() ? complexReceiver.getGifReceiver(STATE_RECEIVER) : complexReceiver.getImageReceiver(STATE_RECEIVER),
                !sticker.isAnimated(),
                false,
                left,
                top,
                right,
                bottom
        );
    }

    private void drawEffect(Canvas canvas, int left, int top){
        int newLeft = left - effectStickerWidth / 2;
        int newTop = top - effectStickerHeight / 2;
        int right = newLeft + effectStickerWidth;
        int bottom = newTop + effectStickerHeight;
        DrawAlgorithms.drawReceiver(
                canvas,
                complexReceiver.getPreviewReceiver(EFFECT_RECEIVER),
                complexReceiver.getGifReceiver(EFFECT_RECEIVER),
                !effectSticker.isAnimated(),
                false,
                newLeft,
                newTop,
                right,
                bottom
        );
    }

    private void drawBackground(Canvas canvas, int left, int top){
        backgroundRect.top = top;
        backgroundRect.left = left;
        backgroundRect.bottom = top + getHeight();
        backgroundRect.right = left + getWidth();
        backgroundPaint.setColor(Theme.isDark() ? Theme.getColor(R.id.theme_color_badgeMuted) : Theme.getColor(R.id.theme_color_badge));
        backgroundPaint.setAlpha((int) (255 * 0.2f) + (int)(255 * 0.5f * longPressFactor));
        canvas.drawRoundRect(backgroundRect, bgRadius, bgRadius, backgroundPaint);
        choosePaint.setColor(Theme.getColor(R.id.theme_color_badge));
        choosePaint.setAlpha((int)(255 * chooseFactor));
        canvas.drawRoundRect(backgroundRect, bgRadius, bgRadius, choosePaint);
    }

    private void drawTotalCount(Canvas canvas, int left, int top) {
        counter.draw(canvas, left, top + getHeight(false) / 2f, Gravity.LEFT, 1f);
    }

    private boolean shouldDisplayTotalCount() {
        int totalCount = reaction.getTotalCount();
        return displayMode == DisplayMode.BIG || totalCount > 1 && displayMode == DisplayMode.MINI;
    }
}
