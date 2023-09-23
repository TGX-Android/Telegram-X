package org.thunderdog.challegram.telegram;

import androidx.annotation.AnyThread;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import me.vkryl.core.lambda.Filter;
import me.vkryl.core.lambda.RunnableBool;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.core.reference.ReferenceList;

public abstract class SortedList<T extends TdApi.Object> implements Comparator<T> {
  public interface ListListener <T extends TdApi.Object> {
    void onListChanged (SortedList<T> list);
    default void onListLoadStateChanged (SortedList<T> list, @State int state) {
      // Do nothing, as it doesn't affect the list itself
    }
    default void onListAvailabilityChanged (SortedList<T> list, boolean isAvailable) {
      // Do nothing, as it doesn't affect the list itself
    }
    default void onItemRemoved (SortedList<T> list, T oldItem, int oldIndex) {
      onListChanged(list);
    }
    default void onItemAdded (SortedList<T> list, T newItem, int newIndex) {
      onListChanged(list);
    }
    default void onItemMovedAndUpdated (SortedList<T> list, T newItem, int newIndex, T oldItem, int oldIndex) {
      onListChanged(list);
    }
    default void onItemUpdated (SortedList<T> list, T newItem, int atIndex, T oldItem) {
      onListChanged(list);
    }
  }

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    State.END_NOT_REACHED,
    State.LOADING,
    State.END_REACHED
  })
  public @interface State {
    int
      END_NOT_REACHED = 0,
      LOADING = 1,
      END_REACHED = 2;
  }

  protected final Tdlib tdlib;
  private final ArrayList<T> list = new ArrayList<>();
  private final ReferenceList<ListListener<T>> listeners = new ReferenceList<>(true);
  private final List<Runnable> onLoadMore = new ArrayList<>();

  private @State int state = State.END_NOT_REACHED;
  private boolean isAvailable;

  public SortedList (Tdlib tdlib) {
    this.tdlib = tdlib;
  }

  @Override
  public abstract int compare (T a, T b);

  protected abstract void loadMoreItems (int desiredCount, @NonNull RunnableBool after);

  protected abstract int approximateTotalItemCount ();

  // List API

  protected final void notifyApproximateTotalItemCountChanged () {
    checkAvailability();
  }

  public boolean isAvailable () {
    return isAvailable;
  }

  public boolean canLoad () {
    return state == State.END_NOT_REACHED;
  }

  public boolean isEndReached () {
    return state == State.END_REACHED;
  }

  private void checkAvailability () {
    assertTdlibThread();
    boolean isAvailable = totalCount() > 0;
    if (this.isAvailable != isAvailable) {
      this.isAvailable = isAvailable;
      for (ListListener<T> listener : listeners) {
        listener.onListAvailabilityChanged(this, isAvailable);
      }
    }
  }

  private void setStateUnsafe (@State int state) {
    if (this.state != state) {
      int oldState = this.state;
      this.state = state;
      for (ListListener<T> listener : listeners) {
        listener.onListLoadStateChanged(this, state);
      }
      if (state == State.END_REACHED || oldState == State.END_REACHED) {
        checkAvailability();
      }
    }
  }

  public int totalCount () {
    return Math.max(count(null), state != State.END_REACHED ? approximateTotalItemCount() : 0);
  }

  public int count (@Nullable Filter<T> filter) {
    synchronized (list) {
      if (filter == null) {
        return list.size();
      }
      int count = 0;
      for (T item : list) {
        if (filter.accept(item)) {
          count++;
        }
      }
      return count;
    }
  }

  private List<T> listCopyUnsafe (@Nullable Filter<T> filter) {
    List<T> list;
    if (filter != null) {
      list = new ArrayList<>(this.list.size());
      for (T item : this.list) {
        if (filter.accept(item)) {
          list.add(item);
        }
      }
    } else {
      list = new ArrayList<>(this.list);
    }
    return list;
  }

  private void assertTdlibThread () {
    if (!tdlib.inTdlibThread())
      throw new IllegalStateException();
  }

  @TdlibThread
  public void addItem (T item) {
    assertTdlibThread();
    int index = Collections.binarySearch(this.list, item, this);
    if (index >= 0)
      throw new IllegalStateException();
    index = (-index) - 1;
    list.add(index, item);
    for (ListListener<T> listener : listeners) {
      listener.onItemAdded(this, item, index);
    }
    checkAvailability();
  }

  @TdlibThread
  public void moveItem (T newItem, T oldItem) {
    assertTdlibThread();
    int oldIndex = Collections.binarySearch(this.list, oldItem, this);
    if (oldIndex < 0)
      throw new IllegalStateException();
    this.list.remove(oldIndex);
    int newIndex = Collections.binarySearch(this.list, newItem, this);
    if (newIndex >= 0)
      throw new IllegalStateException();
    newIndex = (-newIndex) - 1;
    list.add(newIndex, newItem);
    if (newIndex != oldIndex) {
      for (ListListener<T> listener : listeners) {
        listener.onItemMovedAndUpdated(this, newItem, newIndex, oldItem, oldIndex);
      }
    } else {
      for (ListListener<T> listener : listeners) {
        listener.onItemUpdated(this, newItem, newIndex, oldItem);
      }
    }
  }

  @TdlibThread
  public final void removeItem (T oldItem) {
    assertTdlibThread();
    int oldIndex = Collections.binarySearch(this.list, oldItem, this);
    if (oldIndex < 0)
      throw new IllegalStateException();
    this.list.remove(oldIndex);
    for (ListListener<T> listener : listeners) {
      listener.onItemRemoved(this, oldItem, oldIndex);
    }
    checkAvailability();
  }

  @AnyThread
  public void getList (@Nullable Filter<T> filter, RunnableData<List<T>> callback) {
    if (!tdlib.inTdlibThread()) {
      tdlib.runOnTdlibThread(() -> getList(filter, callback));
      return;
    }
    // No need to sync, as all changes are made on tdlib thread
    List<T> list = listCopyUnsafe(filter);
    callback.runWithData(list);
  }

  public void initializeList (@Nullable Filter<T> filter, @NonNull ListListener<T> listener, @NonNull RunnableData<List<T>> callback, int initialChunkSize, @Nullable Runnable onLoadInitialChunk) {
    getList(filter, list -> {
      callback.runWithData(list);
      listeners.add(listener);
    });
    loadAtLeast(filter, initialChunkSize, onLoadInitialChunk);
  }

  public void loadAtLeast (@Nullable Filter<T> filter, int minimumCount, @Nullable Runnable after) {
    loadAtLeast(filter, minimumCount, minimumCount, after);
  }

  public void loadAtLeast (@Nullable Filter<T> filter, int minimumCount, int desiredCount, @Nullable Runnable after) {
    if (!tdlib.inTdlibThread()) {
      tdlib.runOnTdlibThread(() -> loadAtLeast(filter, minimumCount, desiredCount, after));
      return;
    }

    Runnable act = new Runnable() {
      @Override
      public void run () {
        int count = count(filter);
        if (isEndReached() || count >= minimumCount) {
          if (after != null) {
            after.run();
          }
          return;
        }
        loadMore(desiredCount - count, this);
      }
    };
    act.run();
  }

  @AnyThread
  public void loadMore (int limit, @Nullable Runnable after) {
    if (!tdlib.inTdlibThread()) {
      tdlib.runOnTdlibThread(() -> loadMore(limit, after));
      return;
    }
    if (state == State.END_REACHED) {
      if (after != null) {
        after.run();
      }
      return;
    }
    if (after != null) {
      onLoadMore.add(after);
    }
    if (state == State.LOADING) {
      return;
    }
    setStateUnsafe(State.LOADING);
    loadMoreItems(limit, endReached -> {
      setStateUnsafe(endReached ? State.END_REACHED : State.END_NOT_REACHED);
      if (!onLoadMore.isEmpty()) {
        List<Runnable> callbacks = new ArrayList<>(onLoadMore);
        onLoadMore.clear();
        for (Runnable runnable : callbacks) {
          runnable.run();
        }
      }
    });
  }

  @AnyThread
  public void loadAll (int chunkSize, @Nullable Runnable after) {
    if (!tdlib.inTdlibThread()) {
      tdlib.runOnTdlibThread(() -> loadAll(chunkSize, after));
      return;
    }
    Runnable act = new Runnable() {
      @Override
      public void run () {
        if (isEndReached()) {
          if (after != null) {
            after.run();
          }
          return;
        }
        loadMore(chunkSize, this);
      }
    };
    act.run();
  }
}
