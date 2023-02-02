package org.thunderdog.challegram.util;

import androidx.annotation.IntDef;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.unsorted.Settings;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
@IntDef({
  DeviceTokenType.FIREBASE_CLOUD_MESSAGING
})
public @interface DeviceTokenType {
  // TODO more push services. When adding new types, check usages to confirm support in other places
  int FIREBASE_CLOUD_MESSAGING = 0;
}
