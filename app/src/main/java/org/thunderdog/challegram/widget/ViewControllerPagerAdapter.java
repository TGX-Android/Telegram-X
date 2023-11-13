/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 25/12/2016
 */
package org.thunderdog.challegram.widget;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.SparseArrayCompat;
import androidx.viewpager.widget.PagerAdapter;

import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;

import java.util.HashSet;
import java.util.Set;

import me.vkryl.core.lambda.Destroyable;

public class ViewControllerPagerAdapter extends PagerAdapter implements Destroyable, ViewController.AttachListener {
  public interface ControllerProvider {
    int getControllerCount ();
    ViewController<?> createControllerForPosition (int position);
    void onPrepareToShow (int position, ViewController<?> controller);
    void onAfterHide (int position, ViewController<?> controller);
    ViewController<?> getParentOrSelf ();
  }

  private final ControllerProvider provider;
  private final SparseArrayCompat<ViewController<?>> controllers;

  public ViewControllerPagerAdapter (@NonNull ControllerProvider provider) {
    this.provider = provider;
    this.controllers = new SparseArrayCompat<>();
    provider.getParentOrSelf().addAttachStateListener(this);
  }

  @Override
  public void onAttachStateChanged (ViewController<?> context, NavigationController navigation, boolean isAttached) {
    for (ViewController<?> c : visibleControllers) {
      c.onAttachStateChanged(navigation, isAttached);
    }
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

  private final Set<ViewController<?>> visibleControllers = new HashSet<>();

  @Override
  @NonNull
  public Object instantiateItem (@NonNull ViewGroup container, int position) {
    ViewController<?> c = controllers.get(position);
    if (c == null) {
      c = provider.createControllerForPosition(position);
      controllers.put(position, c);
    }
    View view = c.getValue();
    if (view.getParent() != null) {
      ((ViewGroup) view.getParent()).removeView(view);
    }
    provider.onPrepareToShow(position, c);
    c.onPrepareToShow();
    container.addView(view);
    visibleControllers.add(c);
    if (!c.getAttachState()) {
      c.onAttachStateChanged(provider.getParentOrSelf().navigationController(), true);
    }
    return c;
  }

  @Override
  public void destroyItem (ViewGroup container, int position, @NonNull Object object) {
    ViewController<?> c = (ViewController<?>) object;
    container.removeView(c.getValue());
    visibleControllers.remove(c);
    if (c.getAttachState()) {
      c.onAttachStateChanged(provider.getParentOrSelf().navigationController(), false);
    }
    provider.onAfterHide(position, c);
    c.onCleanAfterHide();
  }

  @Override
  public boolean isViewFromObject (@NonNull View view, @NonNull Object object) {
    return object instanceof ViewController && ((ViewController<?>) object).getWrapUnchecked() == view;
  }
}
