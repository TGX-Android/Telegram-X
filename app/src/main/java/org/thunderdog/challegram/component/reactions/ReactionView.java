package org.thunderdog.challegram.component.reactions;


import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;

import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.BaseView;

@SuppressLint("ViewConstructor")
class ReactionView extends BaseView {

    private Reaction reaction;
    private final int desiredSize;
    private boolean isAttached = false;
    private boolean needMeasure = false;

    public ReactionView(Context context, Tdlib tdlib) {
        super(context, tdlib);
        this.desiredSize = Screen.dp(40);
    }

    public void setReaction(TGReaction tgReaction) {
        this.reaction = new Reaction(tgReaction);
        this.reaction.setHasBackground(false);
        this.reaction.setVerticalPadding(0);
        this.reaction.setHorizontalPadding(0);
        if (!isAttached) {
            this.reaction.attachToView(this);
            isAttached = true;
        }
        if (needMeasure) {
            needMeasure = false;
            this.reaction.setHeight(getHeight() - getPaddingBottom() - getPaddingTop());
            this.reaction.measure();
        }
        this.invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (this.reaction != null && !isAttached) {
            isAttached = true;
            this.reaction.attachToView(this);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (this.reaction != null && isAttached) {
            isAttached = false;
            this.reaction.detachFromView();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int width;
        int height;

        //Measure Width
        if (widthMode == MeasureSpec.EXACTLY) {
            width = widthSize;
        } else if (widthMode == MeasureSpec.AT_MOST) {
            width = Math.min(desiredSize, widthSize);
        } else {
            width = desiredSize;
        }

        //Measure Height
        if (heightMode == MeasureSpec.EXACTLY) {
            height = heightSize;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            height = Math.min(desiredSize, heightSize);
        } else {
            height = desiredSize;
        }

        setMeasuredDimension(width, height);
        if (reaction != null) {
            reaction.setHeight(height - getPaddingBottom() - getPaddingTop());
            reaction.measure();
            needMeasure = false;
        } else {
            needMeasure = true;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (reaction != null) reaction.draw(canvas, getPaddingLeft(), getPaddingTop());
    }
}
