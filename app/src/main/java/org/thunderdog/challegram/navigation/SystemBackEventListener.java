package org.thunderdog.challegram.navigation;

import androidx.activity.BackEventCompat;
import androidx.annotation.NonNull;

public interface SystemBackEventListener {
  boolean onSystemBackStarted (@NonNull BackEventCompat backEvent);
  void onSystemBackProgressed (@NonNull BackEventCompat backEvent);
  void onSystemBackCancelled ();
  boolean onSystemBackPressed ();
}
