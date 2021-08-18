package me.vkryl.core.util;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.lambda.FutureBool;

/**
 * Date: 25/03/2019
 * Author: default
 */
public final class JobList {
  private final FutureBool canExecute;
  private final List<Runnable> list, pending;

  private volatile boolean value;

  public JobList (FutureBool state) {
    this.canExecute = state;
    this.list = new ArrayList<>();
    this.pending = new ArrayList<>(0);
    synchronized (list) {
      this.value = state.get();
    }
  }

  @Nullable
  private Runnable onAdd, onRemove;

  public JobList onAddRemove (Runnable onAdd, Runnable onRemove) {
    this.onAdd = onAdd;
    this.onRemove = onRemove;
    return this;
  }

  private boolean value () {
    boolean newValue = canExecute.get();
    if (this.value != newValue) {
      this.value = newValue;
      return newValue;
    }
    if (newValue && !list.isEmpty())
      throw new IllegalStateException();
    return newValue;
  }

  public void add (Runnable runnable) {
    int removedCount = 0;
    boolean added = false;
    synchronized (list) {
      if (value()) {
        runnable.run();
        removedCount = execute();
      } else {
        list.add(runnable);
        added = true;
      }
    }
    if (added) {
      if (onAdd != null)
        onAdd.run();
    } else if (onRemove != null) {
      for (int i = 0; i < removedCount; i++)
        onRemove.run();
    }
  }

  public void trigger () {
    trigger(false);
  }

  public void trigger (boolean force) {
    int removedCount;
    synchronized (list) {
      removedCount = value() || force ? execute() : 0;
    }
    if (onRemove != null) {
      for (int i = 0; i < removedCount; i++)
        onRemove.run();
    }
  }

  public boolean isEmpty () {
    synchronized (list) {
      return list.isEmpty();
    }
  }

  private int execute () {
    int count = list.size();
    if (count == 0)
      return 0;
    pending.clear();
    pending.addAll(list);
    list.clear();
    for (int i = count - 1; i >= 0; i--) {
      pending.get(i).run();
    }
    pending.clear();
    return count;
  }
}
