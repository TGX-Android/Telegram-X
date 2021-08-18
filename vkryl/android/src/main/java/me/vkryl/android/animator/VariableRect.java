package me.vkryl.android.animator;

import android.graphics.RectF;

public class VariableRect implements Animatable {
  private final VariableFloat left, top, right, bottom;
  private final RectF rectF = new RectF();

  public VariableRect () {
    this(0, 0, 0, 0);
  }

  public VariableRect (float left, float top, float right, float bottom) {
    this.left = new VariableFloat(left);
    this.top = new VariableFloat(top);
    this.right = new VariableFloat(right);
    this.bottom = new VariableFloat(bottom);
  }

  public float getLeft () {
    return left.get();
  }

  public float getTop () {
    return top.get();
  }

  public float getRight () {
    return right.get();
  }

  public float getBottom () {
    return bottom.get();
  }

  public void set (float left, float top, float right, float bottom) {
    this.left.set(left);
    this.top.set(top);
    this.right.set(right);
    this.bottom.set(bottom);
  }

  public boolean differs (float left, float top, float right, float bottom) {
    return this.left.differs(left) || this.top.differs(top) || this.right.differs(right) || this.bottom.differs(bottom);
  }

  public void setTo (float left, float top, float right, float bottom) {
    this.left.setTo(left);
    this.top.setTo(top);
    this.right.setTo(right);
    this.bottom.setTo(bottom);
  }

  public RectF toRectF () {
    rectF.set(getLeft(), getTop(), getRight(), getBottom());
    return rectF;
  }

  @Override
  public void finishAnimation (boolean applyFutureState) {
    this.left.finishAnimation(applyFutureState);
    this.top.finishAnimation(applyFutureState);
    this.right.finishAnimation(applyFutureState);
    this.bottom.finishAnimation(applyFutureState);
  }

  @Override
  public boolean applyAnimation (float factor) {
    boolean hasChanges;
    hasChanges = this.left.applyAnimation(factor);
    hasChanges = this.top.applyAnimation(factor) || hasChanges;
    hasChanges = this.right.applyAnimation(factor) || hasChanges;
    hasChanges = this.bottom.applyAnimation(factor) || hasChanges;
    return hasChanges;
  }
}
