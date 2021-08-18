package me.vkryl.core.collection;

import me.vkryl.core.ArrayUtils;

/**
 * Date: 05/11/2016
 * Author: default
 */

public final class SparseLongArray implements Cloneable {
  private int[] mKeys;
  private long[] mValues;
  private int mSize;

  /**
   * Creates a new SparseLongArray containing no mappings.
   */
  public SparseLongArray() {
    this(10);
  }

  /**
   * Creates a new SparseLongArray containing no mappings that will not
   * require any additional memory allocation to store the specified
   * number of mappings.  If you supply an initial capacity of 0, the
   * sparse array will be initialized with a light-weight representation
   * not requiring any additional array allocations.
   */
  public SparseLongArray(int initialCapacity) {
    if (initialCapacity == 0) {
      mKeys = ArrayUtils.EMPTY_INTS;
      mValues = ArrayUtils.EMPTY_LONGS;
    } else {
      initialCapacity = ContainerHelpers.idealLongArraySize(initialCapacity);
      mKeys = new int[initialCapacity];
      mValues = new long[initialCapacity];
    }
    mSize = 0;
  }

  @Override
  public SparseLongArray clone() {
    SparseLongArray clone = null;
    try {
      clone = (SparseLongArray) super.clone();
      clone.mKeys = mKeys.clone();
      clone.mValues = mValues.clone();
    } catch (CloneNotSupportedException cnse) {
            /* ignore */
    }
    return clone;
  }

  /**
   * Gets the long mapped from the specified key, or <code>0</code>
   * if no such mapping has been made.
   */
  public long get(int key) {
    return get(key, 0);
  }

  /**
   * Gets the long mapped from the specified key, or the specified value
   * if no such mapping has been made.
   */
  public long get(int key, long valueIfKeyNotFound) {
    int i = ContainerHelpers.binarySearch(mKeys, mSize, key);

    if (i < 0) {
      return valueIfKeyNotFound;
    } else {
      return mValues[i];
    }
  }

  /**
   * Removes the mapping from the specified key, if there was any.
   */
  public void delete(int key) {
    int i = ContainerHelpers.binarySearch(mKeys, mSize, key);

    if (i >= 0) {
      removeAt(i);
    }
  }

  /**
   * Removes the mapping at the given index.
   */
  public void removeAt(int index) {
    System.arraycopy(mKeys, index + 1, mKeys, index, mSize - (index + 1));
    System.arraycopy(mValues, index + 1, mValues, index, mSize - (index + 1));
    mSize--;
  }

  /**
   * Adds a mapping from the specified key to the specified value,
   * replacing the previous mapping from the specified key if there
   * was one.
   */
  public void put(int key, long value) {
    int i = ContainerHelpers.binarySearch(mKeys, mSize, key);

    if (i >= 0) {
      mValues[i] = value;
    } else {
      i = ~i;

      if (mSize >= mKeys.length) {
        growKeyAndValueArrays(mSize + 1);
      }

      if (mSize - i != 0) {
        System.arraycopy(mKeys, i, mKeys, i + 1, mSize - i);
        System.arraycopy(mValues, i, mValues, i + 1, mSize - i);
      }

      mKeys[i] = key;
      mValues[i] = value;
      mSize++;
    }
  }

  /**
   * Returns the number of key-value mappings that this SparseIntArray
   * currently stores.
   */
  public int size() {
    return mSize;
  }

  /**
   * Given an index in the range <code>0...size()-1</code>, returns
   * the key from the <code>index</code>th key-value mapping that this
   * SparseLongArray stores.
   *
   * <p>The keys corresponding to indices in ascending order are guaranteed to
   * be in ascending order, e.g., <code>keyAt(0)</code> will return the
   * smallest key and <code>keyAt(size()-1)</code> will return the largest
   * key.</p>
   */
  public int keyAt(int index) {
    return mKeys[index];
  }

  public void setValueAt (int index, long value) {
    mValues[index] = value;
  }

  /**
   * Given an index in the range <code>0...size()-1</code>, returns
   * the value from the <code>index</code>th key-value mapping that this
   * SparseLongArray stores.
   *
   * <p>The values corresponding to indices in ascending order are guaranteed
   * to be associated with keys in ascending order, e.g.,
   * <code>valueAt(0)</code> will return the value associated with the
   * smallest key and <code>valueAt(size()-1)</code> will return the value
   * associated with the largest key.</p>
   */
  public long valueAt(int index) {
    return mValues[index];
  }

  /**
   * Returns the index for which {@link #keyAt} would return the
   * specified key, or a negative number if the specified
   * key is not mapped.
   */
  public int indexOfKey(int key) {
    return ContainerHelpers.binarySearch(mKeys, mSize, key);
  }

  /**
   * Returns an index for which {@link #valueAt} would return the
   * specified key, or a negative number if no keys map to the
   * specified value.
   * Beware that this is a linear search, unlike lookups by key,
   * and that multiple keys can map to the same value and this will
   * find only one of them.
   */
  public int indexOfValue(long value) {
    for (int i = 0; i < mSize; i++)
      if (mValues[i] == value)
        return i;

    return -1;
  }

  /**
   * Removes all key-value mappings from this SparseIntArray.
   */
  public void clear() {
    mSize = 0;
  }

  /**
   * Puts a key/value pair into the array, optimizing for the case where
   * the key is greater than all existing keys in the array.
   */
  public void append(int key, long value) {
    if (mSize != 0 && key <= mKeys[mSize - 1]) {
      put(key, value);
      return;
    }

    int pos = mSize;
    if (pos >= mKeys.length) {
      growKeyAndValueArrays(pos + 1);
    }

    mKeys[pos] = key;
    mValues[pos] = value;
    mSize = pos + 1;
  }

  private void growKeyAndValueArrays(int minNeededSize) {
    int n = ContainerHelpers.idealLongArraySize(minNeededSize);

    int[] nkeys = new int[n];
    long[] nvalues = new long[n];

    System.arraycopy(mKeys, 0, nkeys, 0, mKeys.length);
    System.arraycopy(mValues, 0, nvalues, 0, mValues.length);

    mKeys = nkeys;
    mValues = nvalues;
  }

  /**
   * {@inheritDoc}
   *
   * <p>This implementation composes a string by iterating over its mappings.
   */
  @Override
  public String toString() {
    if (size() <= 0) {
      return "{}";
    }

    StringBuilder buffer = new StringBuilder(mSize * 28);
    buffer.append('{');
    for (int i=0; i<mSize; i++) {
      if (i > 0) {
        buffer.append(", ");
      }
      int key = keyAt(i);
      buffer.append(key);
      buffer.append('=');
      long value = valueAt(i);
      buffer.append(value);
    }
    buffer.append('}');
    return buffer.toString();
  }
}