package org.thunderdog.challegram.voip.annotation;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
@IntDef({
  CallState.WAIT_INIT,
  CallState.WAIT_INIT_ACK,
  CallState.ESTABLISHED,
  CallState.FAILED,
  CallState.RECONNECTING
})
public @interface CallState {
  // enum from VoIPController.h:62
  int
    WAIT_INIT = 1,
    WAIT_INIT_ACK = 2,
    ESTABLISHED = 3,
    FAILED = 4,
    RECONNECTING = 5;
}
