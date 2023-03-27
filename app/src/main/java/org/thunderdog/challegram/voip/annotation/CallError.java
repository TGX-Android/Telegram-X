package org.thunderdog.challegram.voip.annotation;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
@IntDef({
  CallError.UNKNOWN,
  CallError.INCOMPATIBLE,
  CallError.TIMEOUT,
  CallError.AUDIO_IO,
  CallError.PROXY,

  CallError.PEER_OUTDATED,
  CallError.PRIVACY,
  CallError.LOCALIZED,
  CallError.INSECURE_UPGRADE,
  CallError.CONNECTION_SERVICE
})
public @interface CallError {
  // VoIPController.h:70
  int
    UNKNOWN = 0,
    INCOMPATIBLE = 1,
    TIMEOUT = 2,
    AUDIO_IO = 3,
    PROXY = 4,

  // local error codes (unused)

    PEER_OUTDATED = -1,
    PRIVACY = -2,
    LOCALIZED = -3,
    INSECURE_UPGRADE = -4,
    CONNECTION_SERVICE = -5;
}
