/**
 * File created on 07/02/16 at 21:25
 * Copyright Vyacheslav Krylov, 2014
 */
package me.vkryl.core.collection;

public class IntList {
  private int[] data;
  private int size;

  public IntList (int initialCapacity) {
    data = new int[initialCapacity];
  }

  public IntList (int[] source) {
    data = source;
    size = source.length;
  }

  public void ensureCapacity (int x, int increaseCount) {
    if (data.length < x) {
      int[] dest = new int[Math.max(x, data.length + increaseCount)];
      System.arraycopy(data, 0, dest, 0, data.length);
      data = dest;
    }
  }

  private void trim () {
    if (size < data.length) {
      int[] dest = new int[size];
      System.arraycopy(data, 0, dest, 0, size);
      data = dest;
    }
  }

  public void clear () {
    size = 0;
  }

  public void append (int x) {
    ensureCapacity(size + 1, 10);
    data[size++] = x;
  }

  public boolean contains (int x) {
    int i = 0;
    for (int cx : data) {
      if (i++ == size) {
        break;
      }
      if (cx == x) {
        return true;
      }
    }
    return false;
  }

  public void appendAll (int[] x) {
    ensureCapacity(size + x.length, 10);
    System.arraycopy(x, 0, data, size, x.length);
    size += x.length;
  }

  public void appendAll (IntList list) {
    if (list.size > 0) {
      ensureCapacity(size + list.size, 0);
      list.trim();
      System.arraycopy(list.data, 0, data, size, list.data.length);
      size += list.size;
    }
  }

  public int[] get () {
    trim();
    return data;
  }

  public void set (int i, int value) {
    data[i] = value;
  }

  public boolean remove (int value) {
    int i = indexOf(value);
    if (i == -1) {
      return false;
    }
    removeAt(i);
    return true;
  }

  public void removeAt (int i) {
    if (i < 0 || i >= size) {
      throw new IndexOutOfBoundsException();
    }
    if (i + 1 < size) {
      System.arraycopy(data, i + 1, data, i, size - i - 1);
    }
    this.size--;
  }

  public int indexOf (int value) {
    for (int i = 0; i < size; i++) {
      if (data[i] == value) {
        return i;
      }
    }
    return -1;
  }

  public int removeLast () {
    return data[--size];
  }

  public int size () {
    return size;
  }

  public int get (int i) {
    return data[i];
  }

  public int last () {
    return data[size - 1];
  }

  public boolean isEmpty () {
    return size == 0;
  }
}
