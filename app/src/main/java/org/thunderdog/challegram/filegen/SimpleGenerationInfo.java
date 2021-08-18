package org.thunderdog.challegram.filegen;

/**
 * Date: 04/04/2017
 * Author: default
 */

public class SimpleGenerationInfo extends GenerationInfo {
  public SimpleGenerationInfo (long generationId, String originalPath, String destinationPath, String conversion) {
    super(generationId, originalPath, destinationPath, conversion, false);
  }

  public static String makeConversion (String path) {
    return GenerationInfo.TYPE_AVATAR;
  }
}
