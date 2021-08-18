package org.thunderdog.challegram.util;

import android.net.Uri;

/**
 * Date: 20/11/2016
 * Author: default
 */

public class RingtoneItem {
  private final int id;
  private final String name;
  private final Uri uri;
  private final boolean isDefault;

  public RingtoneItem (int id, String name, Uri uri, boolean isDefault) {
    this.id = id;
    this.name = name;
    this.uri = uri;
    this.isDefault = isDefault;
  }

  public int getId () {
    return id;
  }

  public String getName () {
    return name;
  }

  public Uri getUri () {
    return uri;
  }

  public boolean isDefault () {
    return isDefault;
  }
}
