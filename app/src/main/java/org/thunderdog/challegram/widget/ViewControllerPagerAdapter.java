package org.thunderdog.challegram.widget;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.SparseArrayCompat;
import androidx.viewpager.widget.PagerAdapter;

import org.thunderdog.challegram.navigation.ViewController;

import me.vkryl.core.lambda.Destroyable;

/**
 * Date: 25/12/2016
 * Author: default
 */

public class ViewControllerPagerAdapter extends PagerAdapter implements Destroyable {
  public interface ControllerProvider {
    int getControllerCount ();
    ViewController<?> createControllerForPosition (int position);
    void onPrepareToShow (int position, ViewController<?> controller);
    void onAfterHide (int position, ViewController<?> controller);
  }

  private final ControllerProvider provider;
  private final SparseArrayCompat<ViewController<?>> controllers;

  public ViewControllerPagerAdapter (@NonNull ControllerProvider provider) {
    this.provider = provider;
    this.controllers = new SparseArrayCompat<>();
  }

  public void notifyItemInserted (int index) {
    int size = controllers.size();
    for (int i = size - 1; i >= 0; i--) {
      int key = controllers.keyAt(i);
      if (key < index)
        break;
      ViewController<?> item = controllers.valueAt(i);
      controllers.removeAt(i);
      controllers.put(key + 1, item);
    }
  }

  public void notifyItemRemoved (int index) {
    int i = controllers.indexOfKey(index);
    if (i < 0) {
      return;
    }
    ViewController<?> c = controllers.valueAt(i);
    controllers.removeAt(i);
    c.destroy();
    int count = controllers.size();
    for (; i < count; i++) {
      int key = controllers.keyAt(i);
      ViewController<?> item = controllers.valueAt(i);
      controllers.removeAt(i);
      controllers.put(key - 1, item);
    }
  }

  @Override
  public void performDestroy () {
    final int size = controllers.size();
    for (int i = 0; i < size; i++) {
      ViewController<?> c = controllers.valueAt(i);
      if (!c.isDestroyed())
        c.destroy();
    }
    controllers.clear();
  }

  @Override
  public int getCount () {
    return provider.getControllerCount();
  }

  public @Nullable ViewController<?> findViewControllerById (int id) {
    final int size = controllers.size();
    for (int i = 0; i < size; i++) {
      ViewController<?> c = controllers.valueAt(i);
      if (c.getId() == id) {
        return c;
      }
    }
    return null;
  }

  public @Nullable ViewController<?> findCachedControllerByPosition (int position) {
    return controllers.get(position);
  }

  @Override
  public int getItemPosition (@NonNull Object object) {
    for (int i = 0; i < controllers.size(); i++) {
      ViewController<?> c = controllers.valueAt(i);
      if (c == object) {
        return controllers.keyAt(i);
      }
    }
    return POSITION_NONE;
  }

  @Override
  @NonNull
  public Object instantiateItem (@NonNull ViewGroup container, int position) {
    ViewController<?> c = controllers.get(position);
    if (c == null) {
      c = provider.createControllerForPosition(position);
      controllers.put(position, c);
    }
    View view = c.get();
    if (view.getParent() != null) {
      ((ViewGroup) view.getParent()).removeView(view);
    }
    provider.onPrepareToShow(position, c);
    c.onPrepareToShow();
    container.addView(view);
    return c;
  }

  @Override
  public void destroyItem (ViewGroup container, int position, @NonNull Object object) {
    ViewController<?> c = (ViewController<?>) object;
    container.removeView(c.get());
    provider.onAfterHide(position, c);
    c.onCleanAfterHide();
  }

  @Override
  public boolean isViewFromObject (@NonNull View view, @NonNull Object object) {
    return object instanceof ViewController && ((ViewController<?>) object).getWrapUnchecked() == view;
  }
}
