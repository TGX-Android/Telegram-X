package org.thunderdog.challegram.util.text;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Strings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.CounterAnimator;
import me.vkryl.android.animator.CounterAnimator.TextDrawable;
import me.vkryl.android.animator.ListAnimator;
import me.vkryl.core.StringUtils;

public class FormattedCounterAnimator<T extends TextDrawable> extends CounterAnimator<T> {
  public static class Part<T extends TextDrawable> extends CounterAnimator.Part<T> {
    private final boolean isCounter; // true if this is part of counter

    public Part (int index, @NonNull T text, boolean isCounter) {
      super(index, text);
      this.isCounter = isCounter;
    }

    public boolean isCounter () {
      return isCounter;
    }

    public String getText () {
      return text.getText();
    }

    public void setVerticalPositionTo (int position) {
      this.position = position;
    }

    public void setVerticalPositionFrom (int position) {
      verticalPosition.setFrom(position);
    }
  }

  public interface Callback<T extends TextDrawable> extends CounterAnimator.Callback<T> {
    default boolean shouldAnimatePartVerticalPosition (@NonNull Part<T> part, long oldCount, long newCount) {
      return part.isCounter();
    }
  }

  private final Callback<T> callback;
  private final ListAnimator<CounterAnimator.Part<T>> animator;

  private long count;
  private boolean hasCounter;

  public FormattedCounterAnimator (Callback<T> callback, long duration) {
    super(callback);
    this.callback = callback;
    this.animator = new ListAnimator<>(listAnimator -> callback.onItemsChanged(this), AnimatorUtils.DECELERATE_INTERPOLATOR, duration);
  }

  @Override public float getWidth () {
    return animator.getMetadata().getTotalWidth();
  }

  @Override public void setCounter (long count, String textRepresentation, boolean animated) {
    setCounterImpl(count, toParts(textRepresentation), animated);
  }

  @Override public void hideCounter (boolean animated) {
    setCounterImpl(0, null, animated);
  }

  public void setCounter (@StringRes int pluralRes, long count, boolean animated) {
    String counter = Strings.buildCounter(count);
    String textRepresentation = Lang.plural(pluralRes, count, counter);
    int counterStart = textRepresentation.indexOf(counter);
    if (counterStart != -1) {
      int counterEnd = counterStart + counter.length();
      List<CounterAnimator.Part<T>> parts = new ArrayList<>(counter.length() + 2);
      String prefix = counterStart > 0 ? textRepresentation.substring(0, counterStart) : null;
      String suffix = counterEnd < textRepresentation.length() ? textRepresentation.substring(counterEnd) : null;
      boolean isRtl = Strings.getTextDirection(textRepresentation) == Strings.DIRECTION_RTL;
      String left = isRtl ? suffix : prefix;
      if (left != null) {
        parts.add(new Part<>(0, callback.onCreateTextDrawable(left), false));
      }
      for (int i = counterStart; i < counterEnd; ) {
        int codePoint = textRepresentation.codePointAt(i);
        int charCount = Character.charCount(codePoint);
        String part = textRepresentation.substring(i, i + charCount);
        parts.add(new Part<>(i - counterStart, callback.onCreateTextDrawable(part), true));
        i += charCount;
      }
      String right = isRtl ? prefix : suffix;
      if (right != null) {
        parts.add(new Part<>(1, callback.onCreateTextDrawable(right), false));
      }
      setCounterImpl(count, parts, animated);
    } else {
      setCounterImpl(count, toParts(textRepresentation), animated);
    }
  }

  protected List<CounterAnimator.Part<T>> toParts (String textRepresentation) {
    if (StringUtils.isEmpty(textRepresentation)) {
      return null;
    }
    return Collections.singletonList(new Part<>(0, callback.onCreateTextDrawable(textRepresentation), true));
  }

  private void setCounterImpl (long count, @Nullable List<CounterAnimator.Part<T>> parts, boolean animated) {
    final boolean hasCounter = parts != null && !parts.isEmpty();
    if (this.count != count || this.hasCounter != hasCounter) {
      final long prevCount = this.count;
      this.count = count;
      this.hasCounter = hasCounter;
      if (hasCounter) {
        this.animator.reset(parts, animated, new ListAnimator.ResetCallback<>() {
          @Override public void onItemAdded (CounterAnimator.Part<T> item, boolean isReturned) {
            onPartAdded((Part<T>) item, prevCount, count, isReturned);
          }

          @Override public void onItemRemoved (CounterAnimator.Part<T> item) {
            onPartRemoved((Part<T>) item, prevCount, count);
          }
        });
      } else {
        this.animator.reset(null, animated);
      }
    }
  }

  @NonNull @Override public Iterator<ListAnimator.Entry<CounterAnimator.Part<T>>> iterator () {
    return animator.iterator();
  }

  protected void onPartAdded (@NonNull Part<T> part, long oldCount, long newCount, boolean isReturned) {
    if (callback.shouldAnimatePartVerticalPosition(part, oldCount, newCount)) {
      if (oldCount < newCount) {
        part.setVerticalPositionFrom(CounterAnimator.Part.POSITION_UP);
      } else if (oldCount > newCount) {
        part.setVerticalPositionFrom(CounterAnimator.Part.POSITION_BOTTOM);
      }
    }
    part.setVerticalPositionTo(CounterAnimator.Part.POSITION_NORMAL);
  }

  protected void onPartRemoved (@NonNull Part<T> part, long oldCount, long newCount) {
    if (callback.shouldAnimatePartVerticalPosition(part, oldCount, newCount)) {
      if (oldCount < newCount) {
        part.setVerticalPositionTo(CounterAnimator.Part.POSITION_BOTTOM);
      } else if (oldCount > newCount) {
        part.setVerticalPositionTo(CounterAnimator.Part.POSITION_UP);
      }
    } else {
      part.setVerticalPositionTo(CounterAnimator.Part.POSITION_NORMAL);
    }
  }
}
