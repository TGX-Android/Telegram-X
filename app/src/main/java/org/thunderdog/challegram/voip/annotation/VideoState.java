package org.thunderdog.challegram.voip.annotation;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
@IntDef({
  VideoState.INACTIVE,
  VideoState.PAUSED,
  VideoState.ACTIVE
})
public @interface VideoState {
  int
    INACTIVE = 0,
    PAUSED = 1,
    ACTIVE = 2;
}
