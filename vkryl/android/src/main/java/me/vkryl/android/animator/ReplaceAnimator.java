package me.vkryl.android.animator;

import android.view.animation.Interpolator;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.Iterator;

public class ReplaceAnimator<T> implements Iterable<ListAnimator.Entry<T>> {
  public interface Callback {
    void onItemChanged (ReplaceAnimator animator);
  }

  private final ListAnimator<T> list;

  public ReplaceAnimator (@NonNull Callback callback) {
    this(callback, null, 0);
  }

  public ReplaceAnimator (@NonNull Callback callback, Interpolator interpolator, long duration) {
    this.list = new ListAnimator<>(list -> {
      callback.onItemChanged(this);
    }, interpolator, duration);
  }

  public void replace (T item, boolean animated) {
    this.list.reset(item != null ? Collections.singletonList(item) : null, animated);
  }

  public ListAnimator.Entry<T> singleton () {
    ListAnimator.Entry<T> singleton = null;
    for (ListAnimator.Entry<T> entry : this.list) {
      if (entry.isAffectingList()) {
        if (singleton == null) {
          singleton = entry;
        } else {
          throw new IllegalStateException(); // Should be always either 0, or 1.
        }
      }
    }
    return singleton;
  }

  public T singletonItem () {
    ListAnimator.Entry<T> entry = singleton();
    return entry != null ? entry.item : null;
  }

  public boolean isEmpty () {
    return singleton() == null;
  }

  public ListAnimator.Metadata getMetadata () {
    return list.getMetadata();
  }

  public void measure (boolean animated) {
    list.measure(animated);
  }

  public void applyAnimation (float factor) {
    this.list.applyAnimation(factor);
  }

  public void clear (boolean animated) {
    this.list.clear(animated);
  }

  public void stopAnimation (boolean applyFuture) {
    this.list.stopAnimation(applyFuture);
  }

  @NonNull
  @Override
  public Iterator<ListAnimator.Entry<T>> iterator() {
    return list.iterator();
  }
}
