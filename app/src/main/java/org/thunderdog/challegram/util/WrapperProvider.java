package org.thunderdog.challegram.util;

/**
 * Date: 2019-10-20
 * Author: default
 */
public interface WrapperProvider<T,V> {
  T getWrap (V v);
}
