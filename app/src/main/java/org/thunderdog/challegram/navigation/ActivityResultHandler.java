/**
 * File created on 11/05/15 at 20:43
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.navigation;

import android.content.Intent;

public interface ActivityResultHandler {
  void onActivityResult (int requestCode, int resultCode, Intent data);
}
