package org.thunderdog.challegram.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import androidx.annotation.NonNull;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.Animatable;
import me.vkryl.android.animator.CounterAnimator;
import me.vkryl.android.animator.ListAnimator;
import me.vkryl.android.animator.VariableFloat;
import me.vkryl.core.StringUtils;

public class CustomizableCounterAnimator<T extends CounterAnimator.TextDrawable>
  implements Iterable<ListAnimator.Entry<CustomizableCounterAnimator.Part<T>>>,
  ListAnimator.ResetCallback<CustomizableCounterAnimator.Part<T>> {

  public interface Callback<T extends CounterAnimator.TextDrawable> {
    void onItemsChanged (CustomizableCounterAnimator<?> animator);
    T onCreateTextDrawable (String text);
  }

  public static class CustomResetCallback<T extends CounterAnimator.TextDrawable> {
    public void onItemAdded (Part<T> item, boolean isReturned, long count, long oldCount) {
      if (oldCount != 0 && oldCount < count) {
        item.verticalPosition.setFrom(Part.POSITION_UP);
      } else if (oldCount > count) {
        item.verticalPosition.setFrom(Part.POSITION_BOTTOM);
      }
      item.position = Part.POSITION_NORMAL;
    }
    public void onItemRemoved (Part<T> item, long count, long oldCount) {
      if (count == 0) {
        item.position = Part.POSITION_NORMAL;
      } else if (oldCount < count) {
        item.position = Part.POSITION_BOTTOM;
      } else if (oldCount > count) {
        item.position = Part.POSITION_UP;
      }
    }
  }

  public static class Part<T extends CounterAnimator.TextDrawable> implements ListAnimator.Measurable, Animatable {
    private final int index;
    public final T text;

    private final VariableFloat verticalPosition = new VariableFloat(POSITION_NORMAL); // 0f = center, -1f = top, 1f = bottom
    private int position = POSITION_NORMAL;

    public static final int POSITION_UP = -1;
    public static final int POSITION_NORMAL = 0;
    public static final int POSITION_BOTTOM = 1;

    public Part (int index, @NonNull T text) {
      this.index = index;
      this.text = text;
    }

    public float getVerticalPosition () {
      return verticalPosition.get();
    }

    public int getIndex () {
      return index;
    }

    public void setVerticalPosition(int position) {
      this.verticalPosition.setFrom(position);
    }

    public void setPosition(int position) {
      this.position = position;
    }

    @Override
    public boolean equals (Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Part<?> part = (Part<?>) o;
      return index == part.index && text.equals(part.text);
    }

    @Override
    public int hashCode () {
      return Objects.hash(index, text.getText());
    }

    @Override
    public int getWidth () {
      return text.getWidth();
    }

    @Override
    public int getHeight () {
      return text.getHeight();
    }

    @Override
    public void finishAnimation (boolean applyFutureState) {
      verticalPosition.finishAnimation(applyFutureState);
    }

    @Override
    public boolean applyAnimation (float factor) {
      return verticalPosition.applyAnimation(factor);
    }

    @Override
    public boolean hasChanges () {
      return verticalPosition.differs(position);
    }

    @Override
    public void prepareChanges () {
      verticalPosition.setTo(position);
    }

    @Override
    public void applyChanges () {
      verticalPosition.set(position);
    }
  }

  private final Callback<T> callback;
  private final ListAnimator<Part<T>> animator;

  private long count;
  private long oldCount;
  private boolean hasCounter;
  private CustomResetCallback<T> resetCallback = new CustomResetCallback<>();

  public CustomizableCounterAnimator (Callback<T> callback) {
    this.callback = callback;
    this.animator = new ListAnimator<>(listAnimator -> callback.onItemsChanged(this), AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
  }

  public void setResetCallback (CustomResetCallback<T> resetCallback) {
    this.resetCallback = resetCallback;
  }

  public float getWidth () {
    return animator.getMetadata().getTotalWidth();
  }

  public void setCounter (long count, String textRepresentation, boolean animated) {
    setCounterImpl(count, toParts(textRepresentation), animated);
  }

  public void hideCounter (boolean animated) {
    setCounterImpl(0, null, animated);
  }

  private List<Part<T>> toParts (String textRepresentation) {
    if (!StringUtils.isEmpty(textRepresentation)) {
      List<Part<T>> parts = new ArrayList<>(textRepresentation.length());
      for (int i = 0; i < textRepresentation.length(); ) {
        int codePoint = textRepresentation.codePointAt(i);
        int charCount = Character.charCount(codePoint);
        String part = textRepresentation.substring(i, i + charCount);
        parts.add(new Part<>(parts.size(), callback.onCreateTextDrawable(part)));
        i += charCount;
      }
      return parts;
    }
    return null;
  }

  private void setCounterImpl (long count, List<Part<T>> parts, boolean animated) {
    final boolean hasCounter = parts != null && !parts.isEmpty();
    if (this.count != count || this.hasCounter != hasCounter) {
      this.oldCount = this.count;
      this.count = count;
      this.hasCounter = hasCounter;
      if (hasCounter) {
        this.animator.reset(parts, animated, this);
      } else {
        this.animator.reset(null, animated);
      }
    }
  }

  @Override
  public void onItemAdded (Part<T> item, boolean isReturned) {
    if (resetCallback != null) {
      resetCallback.onItemAdded(item, isReturned, count, oldCount);
    }
  }

  @Override
  public void onItemRemoved (Part<T> item) {
    if (resetCallback != null) {
      resetCallback.onItemRemoved(item, count, oldCount);
    }
  }

  @NonNull
  @Override
  public Iterator<ListAnimator.Entry<Part<T>>> iterator () {
    return animator.iterator();
  }
}
