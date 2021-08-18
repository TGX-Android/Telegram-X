/**
 * File created on 13/03/16 at 20:54
 * Copyright Vyacheslav Krylov, 2014
 */
package me.vkryl.core.collection;

public class CharList {
  private char[] data;
  private int size;

  public CharList (int initialCapacity) {
    data = new char[initialCapacity];
  }

  public void ensureCapacity (int x, int increaseCount) {
    if (data.length < x) {
      char[] dest = new char[Math.max(x, data.length + increaseCount)];
      System.arraycopy(data, 0, dest, 0, data.length);
      data = dest;
    }
  }

  public void trim () {
    if (size < data.length) {
      char[] dest = new char[size];
      System.arraycopy(data, 0, dest, 0, size);
      data = dest;
    }
  }

  public void clear () {
    size = 0;
  }

  public void append (char x) {
    ensureCapacity(size + 1, 10);
    data[size++] = x;
  }

  public void appendAll (CharList list) {
    if (list.size > 0) {
      ensureCapacity(size + list.size, 0);
      list.trim();
      System.arraycopy(list.data, 0, data, size, list.data.length);
      size += list.size;
    }
  }

  public char[] get () {
    return data;
  }

  public int size () {
    return size;
  }

  public char get (int i) {
    return data[i];
  }

  public char last () {
    return data[size - 1];
  }
}
