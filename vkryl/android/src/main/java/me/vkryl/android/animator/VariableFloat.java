package me.vkryl.android.animator;

public class VariableFloat {
  private float now;
  private float from, to;

  public VariableFloat (float now) {
    set(now);
  }

  public void set (float value) {
    this.now = this.to = this.from = value;
  }

  public float get () {
    return now;
  }

  public void setFrom (float from) {
    this.from = from;
  }

  public void setTo (float to) {
    this.to = to;
  }

  public boolean differs (float future) {
    return to != future;
  }

  public void finishAnimation (boolean future) {
    if (future) {
      this.from = this.now = this.to;
    } else {
      /*this.to = */this.from = this.now;
    }
  }

  public boolean applyAnimation (float changeFactor) {
    float newValue = from + (to - from) * changeFactor;
    if (this.now != newValue) {
      this.now = newValue;
      return true;
    }
    return false;
  }
}
