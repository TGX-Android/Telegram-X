package me.vkryl.core.reference;

import androidx.annotation.NonNull;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

/**
 * Date: 6/11/18
 * Author: default
 */
public interface ReferenceCreator<T> {
  default Reference<T> newReference (@NonNull T item) {
    return new WeakReference<>(item);
  }
}
