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
 * File created on 23/04/2015 at 22:29
 */
package org.thunderdog.challegram.navigation;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NavigationStack {
  private int currentIndex;
  private final ArrayList<ViewController<?>> stack;
  private boolean isLocked;

  public interface ChangeListener {
    void onStackChanged (NavigationStack stack);
  }

  private final List<ChangeListener> changeListeners = new ArrayList<>();

  public NavigationStack () {
    this.stack = new ArrayList<>();
    this.currentIndex = -1;
  }

  public NavigationStack (ViewController<?> initial) {
    if (initial == null) {
      throw new IllegalArgumentException();
    }
    this.stack = new ArrayList<>();
    this.stack.add(initial);
    this.currentIndex = 0;
  }

  public void addChangeListener (ChangeListener listener) {
    changeListeners.add(listener);
  }

  public void removeChangeListener (ChangeListener listener) {
    changeListeners.remove(listener);
  }

  private void notifyStackChanged () {
    if (changeListeners != null) {
      final int size = changeListeners.size();
      for (int i = size - 1; i >= 0; i--) {
        changeListeners.get(i).onStackChanged(this);
      }
    }
  }

  public void set (ViewController<?>[] items) {
    this.stack.clear();
    this.currentIndex = items.length - 1;
    Collections.addAll(this.stack, items);
    notifyStackChanged();
  }

  public boolean isEmpty () {
    return stack.isEmpty();
  }

  public void insert (ViewController<?> item, int index) {
    if (index <= currentIndex) {
      stack.add(index, item);
      currentIndex++;
      notifyStackChanged();
    }
  }

  public void insertBack (ViewController<?> item) {
    insert(item, currentIndex);
  }

  public int size () {
    return stack.size();
  }

  public void setIsLocked (boolean isLocked) {
    this.isLocked = isLocked;
  }

  public boolean isLocked () {
    return isLocked;
  }

  public int getCurrentIndex () {
    if (isLocked) {
      return 0;
    } else {
      return currentIndex;
    }
  }

  public int indexOf (ViewController<?> item) {
    int index = 0;
    for (ViewController<?> stackItem : stack) {
      if (item == stackItem)
        return index;
      index++;
    }
    return -1;
  }

  public ArrayList<ViewController<?>> getAll () {
    return stack;
  }

  public @Nullable ViewController<?> getCurrent () {
    return get(currentIndex);
  }

  public @Nullable ViewController<?> getPrevious () {
    return get(currentIndex - 1);
  }

  public @Nullable ViewController<?> get (int index) {
    return index >= 0 && index < stack.size() ? stack.get(index) : null;
  }

  public ViewController<?> remove (int index) {
    if (index < 0 || index >= stack.size() || index == currentIndex)
      return null;
    ViewController<?> result;
    if (index < currentIndex) {
      currentIndex--;
      result = stack.remove(index);
    } else {
      result = stack.remove(index);
    }
    notifyStackChanged();
    return result;
  }

  public ViewController<?> destroy (int index) {
    ViewController<?> c = remove(index);
    if (c != null) {
      c.destroy();
    }
    return c;
  }

  public void destroyAllButSaveLast (int lastSaveCount) {
    while (size() > lastSaveCount)
      destroy(0);
  }

  public void replace (int index, ViewController<?> c) {
    if (c == null) {
      throw new IllegalArgumentException();
    }
    if (index < 0 || index >= stack.size()) {
      throw new IndexOutOfBoundsException();
    }
    stack.get(index).destroy();
    stack.set(index, c);
  }

  public ViewController<?> removeById (int id) {
    int i = 0;
    for (ViewController<?> c : stack) {
      if (c.getId() == id) {
        return remove(i);
      }
      i++;
    }
    return null;
  }

  public void destroyAllById (int id) {
    final int size = stack.size();
    for (int i = size - 2; i >= 0; i--) {
      ViewController<?> c = stack.get(i);
      if (c != null && c.getId() == id) {
        destroy(i);
      }
    }
  }

  public ViewController<?> destroyById (int id) {
    int i = 0;
    for (ViewController<?> c : stack) {
      if (c.getId() == id) {
        return destroy(i);
      }
      i++;
    }
    return null;
  }

  public ViewController<?> destroyByIdExcludingLast (int id) {
    int i = 0;
    for (ViewController<?> c : stack) {
      if (c.getId() == id && currentIndex != i) {
        return destroy(i);
      }
      i++;
    }
    return null;
  }

  public void destroyAllExceptLast () {
    destroyAllButSaveLast(1);
  }

  public ViewController<?> findLastById (int id) {
    final int size = stack.size();
    for (int i = size - 1; i >= 0; i--) {
      ViewController<?> c = stack.get(i);
      if (c.getId() == id) {
        return c;
      }
    }
    return null;
  }

  public void push (ViewController<?> controller, boolean setAsCurrent) {
    if (controller == null) {
      throw new IllegalArgumentException();
    }
    if (stack.size() > 0 && controller == stack.get(stack.size() - 1)) {
      throw new IllegalArgumentException();
    }
    stack.add(controller);
    if (setAsCurrent)
      currentIndex++;
    notifyStackChanged();
  }

  public ViewController<?> removeLast () {
    if (stack.isEmpty()) {
      return null;
    }
    ViewController<?> controller = stack.remove(currentIndex);
    currentIndex--;
    notifyStackChanged();
    return controller;
  }

  public void clear (NavigationController navigation) {
    if (!stack.isEmpty()) {
      for (ViewController<?> c : stack) {
        if (!c.isDestroyed()) {
          c.attachNavigationController(navigation);
          c.destroy();
          c.detachNavigationController();
        }
      }
      stack.clear();
      currentIndex = -1;
      notifyStackChanged();
    }
  }

  public void reset (NavigationController navigation, boolean saveFirst) {
    ViewController<?> last = removeLast();
    if (saveFirst) {
      if (stack.size() > 1) {
        ViewController<?> first = stack.get(0);
        int i = 0;
        for (ViewController<?> c : stack) {
          if (i != 0 && !c.isDestroyed()) {
            c.attachNavigationController(navigation);
            c.destroy();
            c.detachNavigationController();
          }
          i++;
        }
        stack.clear();
        stack.add(first);
        currentIndex = 0;
      }
    } else {
      clear(navigation);
    }
    push(last, true);
  }

  public void resetSilently (ViewController<?> initial) {
    if (initial == null) {
      throw new IllegalArgumentException();
    }
    stack.clear();
    stack.add(initial);
    currentIndex = 0;
    notifyStackChanged();
  }
}
