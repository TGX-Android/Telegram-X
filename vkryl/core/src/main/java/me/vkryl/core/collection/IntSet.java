package me.vkryl.core.collection;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

public class IntSet implements Iterable<Integer> {
  private final ArrayList<Integer> sortedList;

  public IntSet () {
    this.sortedList = new ArrayList<>();
  }

  public IntSet (int initialCapacity) {
    this.sortedList = new ArrayList<>(initialCapacity);
  }

  public void ensureCapacity (int minCapacity) {
    this.sortedList.ensureCapacity(minCapacity);
  }

  public void addAll (int... items) {
    for (int item : items) {
      add(item);
    }
  }

  public boolean add (int item) {
    int position = Collections.binarySearch(sortedList, item);
    if (position >= 0) {
      return false;
    }
    position = (-position) - 1;
    sortedList.add(position, item);
    return true;
  }

  public boolean remove (int item) {
    int position = Collections.binarySearch(sortedList, item);
    if (position < 0) {
      return false;
    }
    sortedList.remove(position);
    return true;
  }

  public void clear () {
    sortedList.clear();
  }

  public boolean has (int item) {
    return Collections.binarySearch(sortedList, item) >= 0;
  }

  public int size () {
    return sortedList.size();
  }

  @NonNull
  @Override
  public Iterator<Integer> iterator () {
    return sortedList.iterator();
  }
}
