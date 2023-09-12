package org.thunderdog.challegram.util;

import android.graphics.RectF;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.data.TGReactions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import me.vkryl.android.animator.Animatable;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.animator.VariableFloat;
import me.vkryl.android.animator.VariableRect;
import me.vkryl.android.util.ViewProvider;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.lambda.Destroyable;

public final class ReactionsListAnimator implements Iterable<ReactionsListAnimator.Entry> {
  public static class Entry implements Comparable<Entry> {
    public final TGReactions.MessageReactionEntry item;
    private int index;

    private final VariableFloat position;
    private final VariableFloat visibility;
    private final VariableRect measuredPositionRect;

    public Entry (TGReactions.MessageReactionEntry item, int index, boolean isVisible) {
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
    public int compareTo(Entry o) {
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

  public interface Callback {
    void onItemsChanged (ReactionsListAnimator animator);
  }

  public static class Metadata {
    private final VariableFloat size = new VariableFloat(0);
    private final VariableFloat visibility = new VariableFloat(0);
    private final VariableFloat totalWidth = new VariableFloat(0f);
    private final VariableFloat totalHeight = new VariableFloat(0f);
    private final VariableFloat lastLineWidth = new VariableFloat(0f);
    private final VariableFloat timeHeightExpand = new VariableFloat(0f);

    private Metadata () { }

    public boolean applyAnimation (float factor) {
      boolean haveChanges;
      haveChanges = size.applyAnimation(factor);
      haveChanges = totalWidth.applyAnimation(factor) || haveChanges;
      haveChanges = lastLineWidth.applyAnimation(factor) || haveChanges;
      haveChanges = totalHeight.applyAnimation(factor) || haveChanges;
      haveChanges = visibility.applyAnimation(factor) || haveChanges;
      haveChanges = timeHeightExpand.applyAnimation(factor) || haveChanges;
      return haveChanges;
    }

    public void finishAnimation (boolean applyFuture) {
      size.finishAnimation(applyFuture);
      totalWidth.finishAnimation(applyFuture);
      lastLineWidth.finishAnimation(applyFuture);
      totalHeight.finishAnimation(applyFuture);
      visibility.finishAnimation(applyFuture);
      timeHeightExpand.finishAnimation(applyFuture);
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

    public float getLastLineWidth () {
      return lastLineWidth.get();
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

    public float getTimeHeightExpand () {
      return timeHeightExpand.get();
    }
  }

  private final Callback callback;
  private final ArrayList<Entry> entries;
  private final @Nullable
  FactorAnimator animator;
  private final Metadata metadata;
  private final ArrayList<Entry> actualList; // list after all animations finished

  public ReactionsListAnimator (@NonNull ViewProvider provider) {
    this(animator -> provider.invalidate());
  }

  public ReactionsListAnimator (@NonNull Callback callback) {
    this(callback, null, 0);
  }

  public ReactionsListAnimator (@NonNull Callback callback, @Nullable Interpolator interpolator, long duration) {
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

  public int size () {
    return entries.size();
  }

  public Entry getEntry (int index) {
    return entries.get(index);
  }

  public Metadata getMetadata () {
    return metadata;
  }

  public void applyAnimation (float factor) {
    boolean haveChanges = metadata.applyAnimation(factor);
    for (Entry entry : entries) {
      haveChanges = entry.applyAnimation(factor) || haveChanges;
    }
    if (haveChanges) {
      callback.onItemsChanged(ReactionsListAnimator.this);
      if (factor == 1f) {
        removeJunk(true);
      }
    }
  }

  @NonNull
  @Override
  public Iterator<Entry> iterator() {
    return entries.iterator();
  }

  private void removeJunk (boolean applyFuture) {
    boolean haveRemovedEntries = false;
    for (int i = entries.size() - 1; i >= 0; i--) {
      Entry entry = entries.get(i);
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

  private int indexOfItem (TGReactions.MessageReactionEntry item) {
    int index = 0;
    if (item == null) {
      for (Entry entry : entries) {
        if (entry.item == null)
          return index;
        index++;
      }
    } else {
      for (Entry entry : entries) {
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
        for (Entry entry : entries) {
          entry.visibility.setFrom(entry.visibility.get());
          entry.position.setFrom(entry.position.get());
        }
      }
    }
  }

  private int reactionsMaxWidth, timeWidth;
  private boolean forceExpand;

  public void setLayoutParams (int reactionsMaxWidth, int timeWidth, boolean forceExpand) {
    this.reactionsMaxWidth = reactionsMaxWidth;
    this.timeWidth = timeWidth;
    this.forceExpand = forceExpand;
  }

  public int getReactionsMaxWidth () {
    return reactionsMaxWidth;
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

  public void measureImpl (boolean animated) {
    int totalWidth = 0, totalHeight = 0;
    int maxTop = -1;
    int lastLineWidth = 0;
    for (Entry entry : actualList) {
      TGReactions.MessageReactionEntry item = entry.item;

      int itemWidth = item.getBubbleTargetWidth(); // item.getBubbleWidth();
      int itemHeight = item.getBubbleHeight();

      int left = item.getX();
      int top = item.getY();

      int width = itemWidth;
      int height = itemHeight;
      int right, bottom;

      right = left + width;
      bottom = top + height;
      totalWidth = Math.max(totalWidth, right);
      totalHeight = Math.max(totalHeight, bottom);

      if (top == maxTop) {
        lastLineWidth = Math.max(lastLineWidth, right);
      } else if (top > maxTop) {
        lastLineWidth = width;
        maxTop = top;
      }

      if (animated && entry.getVisibility() > 0f) {
        if (entry.measuredPositionRect.differs(left, top, right, bottom)) {
          onBeforeListChanged();
          entry.measuredPositionRect.setTo(left, top, right, bottom);
        }
      } else {
        entry.measuredPositionRect.set(left, top, right, bottom);
      }
    }

    for (Entry entry : entries) {
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

    final float timeHeightExpand;
    if ((lastLineWidth + timeWidth > reactionsMaxWidth) || forceExpand) {
      timeHeightExpand = 1f;
    } else {
      timeHeightExpand = 0f;
      totalWidth = Math.max(totalWidth, lastLineWidth + timeWidth);
    }

    if (animated) {
      if (metadata.totalWidth.differs(totalWidth)) {
        onBeforeListChanged();
        metadata.totalWidth.setTo(totalWidth);
      }
      if (metadata.lastLineWidth.differs(lastLineWidth)) {
        onBeforeListChanged();
        metadata.lastLineWidth.setTo(lastLineWidth);
      }
      if (metadata.totalHeight.differs(totalHeight)) {
        onBeforeListChanged();
        metadata.totalHeight.setTo(totalHeight);
      }
      if (metadata.timeHeightExpand.differs(timeHeightExpand)) {
        onBeforeListChanged();
        metadata.timeHeightExpand.setTo(timeHeightExpand);
      }
    } else {
      metadata.totalWidth.set(totalWidth);
      metadata.lastLineWidth.set(lastLineWidth);
      metadata.totalHeight.set(totalHeight);
      metadata.timeHeightExpand.set(timeHeightExpand);
    }
  }

  public interface ResetCallback {
    void onItemRemoved (TGReactions.MessageReactionEntry item); // item is now removing
    void onItemAdded (TGReactions.MessageReactionEntry item, boolean isReturned); // item is now adding
  }

  public void reset (@Nullable List<TGReactions.MessageReactionEntry> newItems, boolean animated) {
    reset(newItems, animated, null);
  }

  public boolean compareContents (@Nullable List<TGReactions.MessageReactionEntry> items) {
    if (items == null || items.isEmpty()) {
      return this.actualList.isEmpty();
    } else {
      if (this.actualList.size() != items.size())
        return false;
      for (int i = 0; i < items.size(); i++) {
        if (!this.actualList.get(i).equals(items.get(i)))
          return false;
      }
      return true;
    }
  }

  public void reset (@Nullable List<TGReactions.MessageReactionEntry> newItems, boolean animated, @Nullable ResetCallback resetCallback) {
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
        for (TGReactions.MessageReactionEntry item : newItems) {
          Entry entry = new Entry(item, actualList.size(), true);
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
        Entry entry = entries.get(i);
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
        for (TGReactions.MessageReactionEntry newItem : newItems) {
          int existingIndex = indexOfItem(newItem);
          if (existingIndex == -1) {
            if (index != entries.size()) {
              needSort = true;
            }
            onBeforeListChanged();
            Entry entry = new Entry(newItem, index, false);
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
        for (Entry entry : entries) {
          if (entry.visibility.differs(0f)) {
            onBeforeListChanged();
            break;
          }
        }
      }
      if (foundListChanges) {
        for (Entry entry : entries) {
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

