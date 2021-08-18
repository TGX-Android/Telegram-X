/**
 * File created on 01/03/16 at 11:36
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.loader.gif;

public class GifStorage {
  private static GifStorage instance;

  public static GifStorage instance () {
    if (instance == null) {
      instance = new GifStorage();
    }
    return instance;
  }

  private GifStorage () {

  }
}
