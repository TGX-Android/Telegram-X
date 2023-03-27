package org.thunderdog.challegram.voip.annotation;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
@IntDef({
  CallNetworkType.UNKNOWN,
  CallNetworkType.MOBILE_GPRS,
  CallNetworkType.MOBILE_EDGE,
  CallNetworkType.MOBILE_3G,
  CallNetworkType.MOBILE_HSPA,
  CallNetworkType.MOBILE_LTE,
  CallNetworkType.WIFI,
  CallNetworkType.ETHERNET,
  CallNetworkType.OTHER_HIGH_SPEED,
  CallNetworkType.OTHER_LOW_SPEED,
  CallNetworkType.DIALUP,
  CallNetworkType.OTHER_MOBILE
})
public @interface CallNetworkType {
  // enum from VoIPController.h:79
  int
    UNKNOWN = 0,
    MOBILE_GPRS = 1,
    MOBILE_EDGE = 2,
    MOBILE_3G = 3,
    MOBILE_HSPA = 4,
    MOBILE_LTE = 5,
    WIFI = 6,
    ETHERNET = 7,
    OTHER_HIGH_SPEED = 8,
    OTHER_LOW_SPEED = 9,
    DIALUP = 10,
    OTHER_MOBILE = 11;
}
