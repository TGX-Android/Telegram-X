package me.vkryl.core.reference;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Date: 2/26/18
 * Author: default
 */

public class ReferenceMap<K, T> {
  public interface FullnessListener <KK,TT> {
    void onFullnessStateChanged (ReferenceMap<KK,TT> list, boolean isFull);
  }

  private final boolean isThreadSafe, cacheIterator;
  private final @Nullable ReferenceList.FullnessListener fullnessListenerHelper;
  private int fullnessCounter;

  private ReferenceList<T> reuse;

  public ReferenceMap () {
    this(false, true, null);
  }

  public ReferenceMap (final boolean isThreadSafe) {
    this(isThreadSafe, true, null);
  }

  public ReferenceMap (final boolean isThreadSafe, boolean cacheIterator, final @Nullable FullnessListener<K, T> fullnessListener) {
    this.isThreadSafe = isThreadSafe;
    this.cacheIterator = cacheIterator;
    if (fullnessListener != null) {
      fullnessListenerHelper = (list, isFull) -> {
        synchronized (fullnessListener) {
          if (isFull) {
            if (fullnessCounter++ == 0) {
              fullnessListener.onFullnessStateChanged(ReferenceMap.this, true);
            }
          } else {
            if (--fullnessCounter == 0) {
              fullnessListener.onFullnessStateChanged(ReferenceMap.this, false);
            }
          }
        }
      };
    } else {
      fullnessListenerHelper = null;
    }
  }

  protected final Map<K, ReferenceList<T>> map = new HashMap<>();

  public final boolean add (K key, @NonNull T item) {
    synchronized (map) {
      ReferenceList<T> list = map.get(key);
      if (list == null) {
        if (reuse != null) {
          list = reuse;
          reuse = list.next;
          list.next = null;
        } else {
          list = new ReferenceList<>(isThreadSafe, cacheIterator, fullnessListenerHelper);
        }
        map.put(key, list);
      }
      return list.add(item);
    }
  }

  public final boolean has (K key) {
    synchronized (map) {
      ReferenceList<T> list = map.get(key);
      return list != null && !list.isEmpty();
    }
  }

  public final void remove (K key, @NonNull T item) {
    synchronized (map) {
      ReferenceList<T> list = map.get(key);
      if (list != null) {
        list.remove(item);
        if (list.isEmpty()) {
          map.remove(key);
          list.next = reuse;
          reuse = list;
        }
      }
    }
  }

  public final void move (K oldKey, K newKey) {
    synchronized (map) {
      ReferenceList<T> oldList = map.remove(oldKey);
      if (oldList == null) {
        return;
      }
      ReferenceList<T> newList = map.get(newKey);
      if (newList != null) {
        newList.addAll(oldList);
        oldList.clear();
        oldList.next = reuse;
        reuse = oldList;
      } else {
        map.put(newKey, oldList);
      }
    }
  }

  public final void clear () {
    synchronized (map) {
      for (Map.Entry<K, ReferenceList<T>> entry : map.entrySet()) {
        ReferenceList<T> list = entry.getValue();
        list.clear();
        list.next = reuse;
        reuse = list;
      }
      map.clear();
    }
  }

  public final @Nullable Iterator<T> iterator (K key) {
    synchronized (map) {
      ReferenceList<T> list = map.get(key);
      return list != null ? list.iterator() : null;
    }
  }

  /**
   * Requires prior synchronization on ReferenceMap instance
   */
  public final @Nullable Set<K> keySetUnchecked () {
    return map.isEmpty() ? null : map.keySet();
  }

  public final Object mapUnchecked () {
    return map;
  }
}
