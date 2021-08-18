package me.vkryl.core.collection;

import java.util.Arrays;

import me.vkryl.core.ArrayUtils;

/**
 * Date: 13/12/2016
 * Author: default
 */

public final class LongSparseLongArray implements Cloneable {
  private long[] mKeys;
  private long[] mValues;
  private int mSize;

  /**
   * Creates a new SparseIntArray containing no mappings.
   */
  public LongSparseLongArray() {
    this(10);
  }

  /**
   * Creates a new SparseIntArray containing no mappings that will not
   * require any additional memory allocation to store the specified
   * number of mappings.  If you supply an initial capacity of 0, the
   * sparse array will be initialized with a light-weight representation
   * not requiring any additional array allocations.
   */
  public LongSparseLongArray(int initialCapacity) {
    if (initialCapacity == 0) {
      mKeys = ArrayUtils.EMPTY_LONGS;
      mValues = ArrayUtils.EMPTY_LONGS;
    } else {
      initialCapacity = ContainerHelpers.idealLongArraySize(initialCapacity);
      mKeys = new long[initialCapacity];
      mValues = new long[initialCapacity];
    }
    mSize = 0;
  }

  @Override
  public LongSparseLongArray clone() {
    LongSparseLongArray clone = null;
    try {
      clone = (LongSparseLongArray) super.clone();
      clone.mKeys = mKeys.clone();
      clone.mValues = mValues.clone();
    } catch (CloneNotSupportedException cnse) {
            /* ignore */
    }
    return clone;
  }

  /**
   * Gets the int mapped from the specified key, or <code>0</code>
   * if no such mapping has been made.
   */
  public long get(long key) {
    return get(key, 0);
  }

  /**
   * Gets the int mapped from the specified key, or the specified value
   * if no such mapping has been made.
   */
  public long get(long key, long valueIfKeyNotFound) {
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
  public void delete(long key) {
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
  public void put(long key, long value) {
    int i = ContainerHelpers.binarySearch(mKeys, mSize, key);

    if (i >= 0) {
      mValues[i] = value;
    } else {
      i = ~i;

      mKeys = ContainerHelpers.insert(mKeys, mSize, i, key);
      mValues = ContainerHelpers.insert(mValues, mSize, i, value);
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
   * SparseIntArray stores.
   *
   * <p>The keys corresponding to indices in ascending order are guaranteed to
   * be in ascending order, e.g., <code>keyAt(0)</code> will return the
   * smallest key and <code>keyAt(size()-1)</code> will return the largest
   * key.</p>
   */
  public long keyAt(int index) {
    return mKeys[index];
  }

  /**
   * Given an index in the range <code>0...size()-1</code>, returns
   * the value from the <code>index</code>th key-value mapping that this
   * SparseIntArray stores.
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
   * Directly set the value at a particular index.
   * @hide
   */
  public void setValueAt(int index, long value) {
    mValues[index] = value;
  }

  /**
   * Returns the index for which {@link #keyAt} would return the
   * specified key, or a negative number if the specified
   * key is not mapped.
   */
  public int indexOfKey(long key) {
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
  public void append(long key, long value) {
    if (mSize != 0 && key <= mKeys[mSize - 1]) {
      put(key, value);
      return;
    }

    mKeys = ContainerHelpers.append(mKeys, mSize, key);
    mValues = ContainerHelpers.append(mValues, mSize, value);
    mSize++;
  }

  /**
   * Provides a copy of keys.
   *
   * @hide
   * */
  public long[] copyKeys() {
    if (size() == 0) {
      return null;
    }
    return Arrays.copyOf(mKeys, size());
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
      long key = keyAt(i);
      buffer.append(key);
      buffer.append('=');
      long value = valueAt(i);
      buffer.append(value);
    }
    buffer.append('}');
    return buffer.toString();
  }
}
