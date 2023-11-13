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
 * File created on 06/01/2023
 */
package org.thunderdog.challegram.util;

import android.graphics.Rect;
import android.graphics.RectF;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import me.vkryl.android.animator.Animatable;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.animator.VariableFloat;
import me.vkryl.android.animator.VariableRect;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.lambda.Destroyable;

public final class FlowListAnimator<T extends FlowListAnimator.Measurable> implements Iterable<FlowListAnimator.Entry<T>> {
  public static class Entry<T> implements Comparable<Entry<T>> {
    public final T item;
    private int index;

    private final VariableFloat position;
    private final VariableFloat visibility;
    private final VariableRect measuredPositionRect;

    public Entry (T item, int index, boolean isVisible) {
      this.item = item;
      this.index = index;
      this.visibility = new VariableFloat(isVisible ? 1f : 0f);
      this.position = new VariableFloat(index);
      this.measuredPositionRect = new VariableRect();
      finishAnimation(false);
    }

    public boolean isJunk () {
      return getVisibility() == 0f && !isAffectingList();
    }

    private boolean isBeingRemoved = false;

    private void onPrepareRemove () {
      visibility.setTo(0f);
      isBeingRemoved = true;
    }

    private void onPrepareAppear () {
      visibility.setTo(1f);
      isBeingRemoved = false;
    }

    @Override
    public int compareTo(Entry<T> o) {
      return Integer.compare(index, o.index);
    }

    public float getPosition () {
      return position.get();
    }

    public int getIndex () {
      return index;
    }

    public float getVisibility () {
      return MathUtils.clamp(visibility.get());
    }

    // State

    public boolean isAffectingList () {
      return !isBeingRemoved;
    }

    private void onRecycled () {
      if (item instanceof Destroyable) {
        ((Destroyable) item).performDestroy();
      }
    }

    // Measured

    public RectF getRectF () {
      return measuredPositionRect.toRectF();
    }

    public void getBounds (Rect outRect) {
      outRect.set((int) measuredPositionRect.getLeft(), (int) measuredPositionRect.getTop(), (int) measuredPositionRect.getRight(), (int) measuredPositionRect.getBottom());
    }

    // Animation

    private void finishAnimation (boolean applyFutureState) {
      this.position.finishAnimation(applyFutureState);
      this.visibility.finishAnimation(applyFutureState);
      this.measuredPositionRect.finishAnimation(applyFutureState);
      if (item instanceof Animatable) {
        ((Animatable) this.item).finishAnimation(applyFutureState);
      }
    }

    private boolean applyAnimation (float factor) {
      boolean haveChanges;
      haveChanges = position.applyAnimation(factor);
      haveChanges = visibility.applyAnimation(factor) || haveChanges;
      haveChanges = measuredPositionRect.applyAnimation(factor) || haveChanges;
      if (item instanceof Animatable) {
        haveChanges = ((Animatable) item).applyAnimation(factor) || haveChanges;
      }
      return haveChanges;
    }
  }

  public interface Measurable {
    int getWidth ();
    int getHeight ();
  }

  public interface Callback {
    void onItemsChanged (FlowListAnimator<?> animator);
  }

  public static class Metadata {
    private final VariableFloat size = new VariableFloat(0);
    private final VariableFloat visibility = new VariableFloat(0);
    private final VariableFloat totalWidth = new VariableFloat(0f);
    private final VariableFloat totalHeight = new VariableFloat(0f);
    private final VariableFloat lastLineWidth = new VariableFloat(0f);
    private final VariableFloat lastLineHeight = new VariableFloat(0f);

    private Metadata () { }

    public boolean applyAnimation (float factor) {
      boolean haveChanges;
      haveChanges = size.applyAnimation(factor);
      haveChanges = totalWidth.applyAnimation(factor) || haveChanges;
      haveChanges = totalHeight.applyAnimation(factor) || haveChanges;
      haveChanges = visibility.applyAnimation(factor) || haveChanges;
      haveChanges = lastLineWidth.applyAnimation(factor) || haveChanges;
      haveChanges = lastLineHeight.applyAnimation(factor) || haveChanges;
      return haveChanges;
    }

    public void finishAnimation (boolean applyFuture) {
      size.finishAnimation(applyFuture);
      totalWidth.finishAnimation(applyFuture);
      totalHeight.finishAnimation(applyFuture);
      visibility.finishAnimation(applyFuture);
      lastLineWidth.finishAnimation(applyFuture);
      lastLineHeight.finishAnimation(applyFuture);
    }

    private void setSize (int size, boolean animated) {
      if (animated) {
        this.size.setTo(size);
        this.visibility.setTo(size > 0 ? 1.0f : 0.0f);
      } else {
        this.size.set(size);
        this.visibility.set(size > 0 ? 1.0f : 0.0f);
      }
    }

    public float getTotalWidth () {
      return totalWidth.get();
    }

    public float getTotalHeight () {
      return totalHeight.get();
    }

    public float getSize () {
      return size.get();
    }

    public float getVisibility () {
      return visibility.get();
    }

    public float getLastLineWidth () {
      return lastLineWidth.get();
    }

    public float getLastLineHeight () {
      return lastLineHeight.get();
    }
  }

  private final Callback callback;
  private final ArrayList<Entry<T>> entries;
  private final @Nullable FactorAnimator animator;
  private final Metadata metadata;
  private final ArrayList<Entry<T>> actualList; // list after all animations finished

  private @Px int itemSpacing = 0;
  private @Px int lineSpacing = 0;
  private @Px int maxWidth = Integer.MAX_VALUE;
  private @Px int maxItemsInRow = Integer.MAX_VALUE;

  public FlowListAnimator (@NonNull Callback callback, @Nullable Interpolator interpolator, long duration) {
    this.callback = callback;
    this.metadata = new Metadata();
    this.entries = new ArrayList<>();
    this.actualList = new ArrayList<>();
    if (interpolator != null && duration > 0) {
      this.animator = new FactorAnimator(0, new FactorAnimator.Target() {
        @Override
        public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
          applyAnimation(factor);
        }

        @Override
        public void onFactorChangeFinished(int id, float finalFactor, FactorAnimator callee) {
          applyAnimation(finalFactor);
        }
      }, interpolator, duration);
    } else {
      this.animator = null;
    }
  }

  public void setItemSpacing (@Px int itemSpacing) {
    this.itemSpacing = itemSpacing;
  }

  public void setLineSpacing (@Px int lineSpacing) {
    this.lineSpacing = lineSpacing;
  }

  public void setMaxItemsInRow (@Px int maxItemsInRow) {
    this.maxItemsInRow = maxItemsInRow;
  }

  public void setMaxWidth (@Px int maxWidth) {
    this.maxWidth = maxWidth;
  }

  public int getMaxWidth () {
    return maxWidth;
  }

  public int size () {
    return entries.size();
  }

  public Entry<T> getEntry (int index) {
    return entries.get(index);
  }

  public Metadata getMetadata () {
    return metadata;
  }

  public void applyAnimation (float factor) {
    boolean haveChanges = metadata.applyAnimation(factor);
    for (Entry<T> entry : entries) {
      haveChanges = entry.applyAnimation(factor) || haveChanges;
    }
    if (haveChanges) {
      callback.onItemsChanged(FlowListAnimator.this);
      if (factor == 1f) {
        removeJunk(true);
      }
    }
  }

  @NonNull
  @Override
  public Iterator<Entry<T>> iterator() {
    return entries.iterator();
  }

  private void removeJunk (boolean applyFuture) {
    boolean haveRemovedEntries = false;
    for (int i = entries.size() - 1; i >= 0; i--) {
      Entry<T> entry = entries.get(i);
      entry.finishAnimation(applyFuture);
      if (entry.isJunk()) {
        entries.remove(i);
        entry.onRecycled();
        haveRemovedEntries = true;
      }
    }
    if (haveRemovedEntries) {
      entries.trimToSize();
    }
    metadata.finishAnimation(applyFuture);
  }

  public void stopAnimation (boolean applyFuture) {
    if (animator != null) {
      animator.cancel();
      removeJunk(applyFuture);
      animator.forceFactor(0f);
    } else {
      removeJunk(applyFuture);
    }
  }

  private int indexOfItem (T item) {
    int index = 0;
    if (item == null) {
      for (Entry<T> entry : entries) {
        if (entry.item == null)
          return index;
        index++;
      }
    } else {
      for (Entry<T> entry : entries) {
        if (item.equals(entry.item))
          return index;
        index++;
      }
    }
    return -1;
  }

  public void clear (boolean animated) {
    reset(null, animated);
  }

  private boolean foundListChanges;

  private void onBeforeListChanged () {
    if (!foundListChanges) {
      foundListChanges = true;
      stopAnimation(false);
    }
  }

  private void onApplyListChanges () {
    if (foundListChanges) {
      foundListChanges = false;
      if (animator != null) {
        animator.animateTo(1f);
      }
    } else {
      if (animator == null) {
        for (Entry<T> entry : entries) {
          entry.visibility.setFrom(entry.visibility.get());
          entry.position.setFrom(entry.position.get());
        }
      }
    }
  }

  public void measure (boolean animated) {
    if (!animated) {
      stopAnimation(true);
    }
    measureImpl(animated);
    if (animated) {
      onApplyListChanges();
    }
  }

  private void measureImpl (boolean animated) {
    int itemsInRow = 0;
    int itemLeft = 0, itemTop = 0;
    int lineHeight = 0, totalWidth = 0;
    for (Entry<T> entry : actualList) {
      int itemWidth = Math.min(entry.item.getWidth(), maxWidth);
      int itemHeight = entry.item.getHeight();
      if (itemLeft + itemWidth > maxWidth || itemsInRow >= maxItemsInRow) {
        itemsInRow = 0;
        itemLeft = 0;
        itemTop += lineHeight + lineSpacing;
      }
      itemsInRow++;
      int itemRight = itemLeft + itemWidth;
      int itemBottom = itemTop + itemHeight;
      if (animated && entry.getVisibility() > 0f) {
        if (entry.measuredPositionRect.differs(itemLeft, itemTop, itemRight, itemBottom)) {
          onBeforeListChanged();
          entry.measuredPositionRect.setTo(itemLeft, itemTop, itemRight, itemBottom);
        }
      } else {
        entry.measuredPositionRect.set(itemLeft, itemTop, itemRight, itemBottom);
      }
      itemLeft = itemRight + itemSpacing;
      lineHeight = Math.max(lineHeight, itemHeight);
      totalWidth = Math.max(totalWidth, itemRight);
    }
    int totalHeight = itemTop + lineHeight;
    int lineWidth = itemLeft - itemSpacing;
    for (Entry<T> entry : entries) {
      if (entry.item instanceof Animatable) {
        Animatable animatable = (Animatable) entry.item;
        if (animated) {
          if (animatable.hasChanges()) {
            onBeforeListChanged();
            animatable.prepareChanges();
          }
        } else {
          animatable.applyChanges();
        }
      }
    }

    if (animated) {
      if (metadata.totalWidth.differs(totalWidth)) {
        onBeforeListChanged();
        metadata.totalWidth.setTo(totalWidth);
      }
      if (metadata.totalHeight.differs(totalHeight)) {
        onBeforeListChanged();
        metadata.totalHeight.setTo(totalHeight);
      }
      if (metadata.lastLineWidth.differs(lineWidth)) {
        onBeforeListChanged();
        metadata.lastLineWidth.setTo(lineWidth);
      }
      if (metadata.lastLineHeight.differs(lineHeight)) {
        onBeforeListChanged();
        metadata.lastLineHeight.setTo(lineHeight);
      }
    } else {
      metadata.totalWidth.set(totalWidth);
      metadata.totalHeight.set(totalHeight);
      metadata.lastLineWidth.set(lineWidth);
      metadata.lastLineHeight.set(lineHeight);
    }
  }

  public interface ResetCallback<T> {
    void onItemRemoved (T item); // item is now removing
    void onItemAdded (T item, boolean isReturned); // item is now adding
  }

  public void reset (@Nullable List<T> newItems, boolean animated) {
    reset(newItems, animated, null);
  }

  public boolean compareContents (@Nullable List<T> items) {
    if (items == null || items.isEmpty()) {
      return this.actualList.isEmpty();
    } else {
      if (this.actualList.size() != items.size())
        return false;
      for (int i = 0; i < items.size(); i++) {
        if (!this.actualList.get(i).item.equals(items.get(i)))
          return false;
      }
      return true;
    }
  }

  public void reset (@Nullable List<T> newItems, boolean animated, @Nullable ResetCallback<T> resetCallback) {
    if (!animated) {
      stopAnimation(false);
      for (int i = entries.size() - 1; i >= 0; i--) {
        entries.get(i).onRecycled();
      }
      entries.clear();
      actualList.clear();
      int size = newItems != null ? newItems.size() : 0;
      if (size > 0) {
        entries.ensureCapacity(size);
        actualList.ensureCapacity(size);
        for (T item : newItems) {
          Entry<T> entry = new Entry<>(item, actualList.size(), true);
          entries.add(entry);
          actualList.add(entry);
        }
        entries.trimToSize();
        actualList.trimToSize();
      }
      metadata.setSize(size, false);
      measureImpl(false);
      callback.onItemsChanged(this);
      return;
    }

    if (compareContents(newItems))
      return;

    onBeforeListChanged();

    boolean needSort = false;
    if (newItems != null && !newItems.isEmpty()) {
      // First, detect removals & changes

      int foundItemCount = 0;

      boolean needSortActual = false;
      for (int i = 0; i < entries.size(); i++) {
        Entry<T> entry = entries.get(i);
        int newIndex = newItems.indexOf(entry.item);
        if (newIndex != -1) {
          foundItemCount++;
          if (entry.position.differs(newIndex)) {
            onBeforeListChanged();
            entry.position.setTo(newIndex);
          }
          if (entry.index != newIndex) {
            entry.index = newIndex;
            needSort = true;
            needSortActual = needSortActual || entry.isAffectingList();
          }
          if (entry.visibility.differs(1f)) {
            onBeforeListChanged();
            entry.onPrepareAppear();
            actualList.add(entry);
            needSortActual = true;
            metadata.setSize(actualList.size(), true);
            if (resetCallback != null) {
              resetCallback.onItemAdded(entry.item, true);
            }
          }
        } else {
          if (entry.visibility.differs(0f)) {
            onBeforeListChanged();
            entry.onPrepareRemove();
            boolean removed = needSortActual ? actualList.remove(entry) : ArrayUtils.removeSorted(actualList, entry);
            if (!removed) {
              throw new IllegalArgumentException();
            }
            metadata.setSize(actualList.size(), true);
            if (resetCallback != null) {
              resetCallback.onItemRemoved(entry.item);
            }
          }
        }
      }

      if (needSortActual) {
        Collections.sort(actualList);
      }

      // Second, find additions

      if (foundItemCount < newItems.size()) {
        entries.ensureCapacity(entries.size() + (newItems.size() - foundItemCount));
        int index = 0;
        for (T newItem : newItems) {
          int existingIndex = indexOfItem(newItem);
          if (existingIndex == -1) {
            if (index != entries.size()) {
              needSort = true;
            }
            onBeforeListChanged();
            Entry<T> entry = new Entry<>(newItem, index, false);
            entry.onPrepareAppear();
            entries.add(entry);
            ArrayUtils.addSorted(actualList, entry);
            metadata.setSize(actualList.size(), true);
            if (resetCallback != null) {
              resetCallback.onItemAdded(entry.item, false);
            }
          }
          index++;
        }
      }
    } else {
      if (!foundListChanges) {
        // Triggering the removeJunk call
        for (Entry<T> entry : entries) {
          if (entry.visibility.differs(0f)) {
            onBeforeListChanged();
            break;
          }
        }
      }
      if (foundListChanges) {
        for (Entry<T> entry : entries) {
          if (entry.visibility.differs(0f)) {
            onBeforeListChanged();
            entry.onPrepareRemove();
            ArrayUtils.removeSorted(actualList, entry);
            metadata.setSize(actualList.size(), true);
            if (resetCallback != null) {
              resetCallback.onItemRemoved(entry.item);
            }
          }
        }
      }
    }

    // Then, sort and run animation, if needed

    if (needSort) {
      Collections.sort(entries);
    }

    measureImpl(true);

    onApplyListChanges();
  }
}
