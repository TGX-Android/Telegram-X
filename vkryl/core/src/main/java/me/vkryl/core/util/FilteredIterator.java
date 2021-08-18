package me.vkryl.core.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Iterator;

import me.vkryl.core.lambda.Filter;

/**
 * Date: 07/01/2019
 * Author: default
 */
public class FilteredIterator<T> implements Iterator<T>, Iterable<T> {
  @Nullable
  private final Iterator<T> itr;

  @NonNull
  private final Filter<T> filter;

  public FilteredIterator (@Nullable Iterator<T> itr, @NonNull Filter<T> filter) {
    this.itr = itr;
    this.filter = filter;
  }

  public FilteredIterator (@Nullable Iterable<T> itr, @NonNull Filter<T> filter) {
    this(itr != null ? itr.iterator() : null, filter);
  }

  @NonNull
  @Override
  public Iterator<T> iterator () {
    return this;
  }

  private T next;

  @Override
  public boolean hasNext () {
    if (itr == null)
      return false;
    this.next = null;
    do {
      boolean hasNext = itr.hasNext();
      if (!hasNext)
        return false;
      T next = itr.next();
      if (!filter.accept(next))
        continue;
      this.next = next;
      return true;
    } while (true);
  }

  @Override
  public T next () {
    return this.next;
  }
}
