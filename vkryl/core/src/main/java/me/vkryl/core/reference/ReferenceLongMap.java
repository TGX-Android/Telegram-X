package me.vkryl.core.reference;

import androidx.annotation.Nullable;

/**
 * Date: 2/26/18
 * Author: default
 */

// TODO maybe something more efficient?
public class ReferenceLongMap<T> extends ReferenceMap<Long, T> {
  public ReferenceLongMap () { }

  public ReferenceLongMap (boolean isThreadSafe) {
    super(isThreadSafe);
  }

  public ReferenceLongMap (boolean isThreadSafe, @Nullable FullnessListener<Long, T> fullnessListener) {
    super(isThreadSafe, true, fullnessListener);
  }
}
