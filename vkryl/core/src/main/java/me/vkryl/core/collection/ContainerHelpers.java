package me.vkryl.core.collection;

/**
 * Date: 21/10/2016
 * Author: default
 */

final class ContainerHelpers {
  public static int idealIntArraySize(int need) {
    return idealByteArraySize(need * 4) / 4;
  }

  public static int idealLongArraySize(int need) {
    return idealByteArraySize(need * 8) / 8;
  }

  public static int idealByteArraySize(int need) {
    for (int i = 4; i < 32; i++)
      if (need <= (1 << i) - 12)
        return (1 << i) - 12;

    return need;
  }

  // This is Arrays.binarySearch(), but doesn't do any argument validation.
  public static int binarySearch (int[] array, int size, int value) {
    int lo = 0;
    int hi = size - 1;

    while (lo <= hi) {
      int mid = (lo + hi) >>> 1;
      int midVal = array[mid];

      if (midVal < value) {
        lo = mid + 1;
      } else if (midVal > value) {
        hi = mid - 1;
      } else {
        return mid;  // value found
      }
    }
    return ~lo;  // value not present
  }

  public static int binarySearch (long[] array, int size, long value) {
    int lo = 0;
    int hi = size - 1;

    while (lo <= hi) {
      final int mid = (lo + hi) >>> 1;
      final long midVal = array[mid];

      if (midVal < value) {
        lo = mid + 1;
      } else if (midVal > value) {
        hi = mid - 1;
      } else {
        return mid;  // value found
      }
    }
    return ~lo;  // value not present
  }

  /**
   * Given the current size of an array, returns an ideal size to which the array should grow.
   * This is typically double the given size, but should not be relied upon to do so in the
   * future.
   */
  private static int growSize (int currentSize) {
    return currentSize <= 4 ? 8 : currentSize * 2;
  }

  public static int[] append (int[] array, int currentSize, int element) {
    if (currentSize + 1 > array.length) {
      int[] newArray = new int[growSize(currentSize)];
      System.arraycopy(array, 0, newArray, 0, currentSize);
      array = newArray;
    }
    array[currentSize] = element;
    return array;
  }

  public static long[] append(long[] array, int currentSize, long element) {
    if (currentSize + 1 > array.length) {
      long[] newArray = new long[growSize(currentSize)];
      System.arraycopy(array, 0, newArray, 0, currentSize);
      array = newArray;
    }
    array[currentSize] = element;
    return array;
  }

  public static int[] insert(int[] array, int currentSize, int index, int element) {
    if (currentSize + 1 <= array.length) {
      System.arraycopy(array, index, array, index + 1, currentSize - index);
      array[index] = element;
      return array;
    }
    int[] newArray = new int[growSize(currentSize)];
    System.arraycopy(array, 0, newArray, 0, index);
    newArray[index] = element;
    System.arraycopy(array, index, newArray, index + 1, array.length - index);
    return newArray;
  }

  public static long[] insert(long[] array, int currentSize, int index, long element) {
    if (currentSize + 1 <= array.length) {
      System.arraycopy(array, index, array, index + 1, currentSize - index);
      array[index] = element;
      return array;
    }
    long[] newArray = new long[growSize(currentSize)];
    System.arraycopy(array, 0, newArray, 0, index);
    newArray[index] = element;
    System.arraycopy(array, index, newArray, index + 1, array.length - index);
    return newArray;
  }
}
