package org.thunderdog.challegram.telegram;

/**
 * Date: 2/25/18
 * Author: default
 */

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
@IntDef({
  TdlibManager.SWITCH_REASON_UNAUTHORIZED,
  TdlibManager.SWITCH_REASON_NAVIGATION,
  TdlibManager.SWITCH_REASON_USER_CLICK,
  TdlibManager.SWITCH_REASON_CHAT_OPEN,
  TdlibManager.SWITCH_REASON_CHAT_FOCUS,
  TdlibManager.SWITCH_REASON_EXISTING_NUMBER
})
public @interface AccountSwitchReason { }