package org.thunderdog.challegram.navigation;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
@IntDef({
  BackPressMode.SYSTEM_ACTION_REQUIRED,
  BackPressMode.CUSTOM_ACTION_PERFORMED,
  BackPressMode.NAVIGATE_BACK_IN_STACK,
  BackPressMode.CLOSE_NAVIGATION_DRAWER
})
public @interface BackPressMode {
  int
    SYSTEM_ACTION_REQUIRED = 0,
    CUSTOM_ACTION_PERFORMED = 1,
    NAVIGATE_BACK_IN_STACK = 2,
    CLOSE_NAVIGATION_DRAWER = 3;
}
