package me.vkryl.core.collection;

/**
 * Date: 25/02/2017
 * Author: default
 */

public class FloatList {
  private float[] data;
  private int size;

  public FloatList (int initialCapacity) {
    data = new float[initialCapacity];
  }

  public FloatList (float[] source) {
    data = source;
    size = source.length;
  }

  public void ensureCapacity (int x, int increaseCount) {
    if (data.length < x) {
      float[] dest = new float[Math.max(x, data.length + increaseCount)];
      System.arraycopy(data, 0, dest, 0, data.length);
      data = dest;
    }
  }

  public void trim () {
    if (size < data.length) {
      float[] dest = new float[size];
      System.arraycopy(data, 0, dest, 0, size);
      data = dest;
    }
  }

  public void clear () {
    size = 0;
  }

  public void append (float x) {
    ensureCapacity(size + 1, data.length == 0 ? 10 : data.length << 1);
    data[size++] = x;
  }

  public boolean contains (float x) {
    int i = 0;
    for (float cx : data) {
      if (i++ == size) {
        break;
      }
      if (cx == x) {
        return true;
      }
    }
    return false;
  }

  public void appendAll (float[] x) {
    ensureCapacity(size + x.length, 10);
    System.arraycopy(x, 0, data, size, x.length);
    size += x.length;
  }

  public void appendAll (FloatList list) {
    if (list.size > 0) {
      ensureCapacity(size + list.size, 0);
      list.trim();
      System.arraycopy(list.data, 0, data, size, list.data.length);
      size += list.size;
    }
  }

  public float sum () {
    float x = 0;
    for (int i = 0; i < size; i++) {
      x += data[i];
    }
    return x;
  }

  public float[] get () {
    trim();
    return data;
  }

  public int size () {
    return size;
  }

  public float get (int i) {
    return data[i];
  }

  public float last () {
    return data[size - 1];
  }

  public boolean isEmpty () {
    return size == 0;
  }
}
