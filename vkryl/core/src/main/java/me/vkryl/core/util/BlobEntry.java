package me.vkryl.core.util;

/**
 * Date: 4/21/18
 * Author: default
 */
public interface BlobEntry {
  int estimatedBinarySize ();
  void saveTo (Blob blob);
  void restoreFrom (Blob blob);
}
