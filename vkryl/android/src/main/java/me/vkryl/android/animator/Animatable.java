package me.vkryl.android.animator;

public interface Animatable {
  void finishAnimation (boolean applyFutureState);
  boolean applyAnimation (float factor);

  default boolean hasChanges () { return false; }
  default void prepareChanges () { }
  default void applyChanges () { }
}
