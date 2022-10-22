package org.thunderdog.challegram;

import org.thunderdog.challegram.unsorted.LocationRetriever;

public class LocationRetrieverFactory {
  public static LocationRetriever newRetriever (BaseActivity context) {
    return new GoogleLocationRetriever(context);
  }
}
