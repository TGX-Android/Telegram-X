package org.thunderdog.challegram.voip.annotation;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
@IntDef({
  AudioState.MUTED,
  AudioState.ACTIVE
})
public @interface AudioState {
  int
    MUTED = 0,
    ACTIVE = 1;
}
