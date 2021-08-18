package me.vkryl.core.reference;

/**
 * Date: 2/26/18
 * Author: default
 */

// TODO maybe something more efficient?
public class ReferenceIntMap<T> extends ReferenceMap<Integer, T> {
  public ReferenceIntMap () {
    super(false);
  }

  public ReferenceIntMap (boolean isThreadSafe) {
    super(isThreadSafe);
  }

  public ReferenceIntMap (boolean isThreadSafe, ReferenceMap.FullnessListener<Integer, T> fullnessListener) {
    super(isThreadSafe, true, fullnessListener);
  }
}
