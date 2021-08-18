package org.thunderdog.challegram.telegram;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Date: 2/21/18
 * Author: default
 */

@Retention(RetentionPolicy.SOURCE)
@IntDef({Tdlib.STATE_UNKNOWN, Tdlib.STATE_CONNECTED, Tdlib.STATE_CONNECTING_TO_PROXY, Tdlib.STATE_CONNECTING, Tdlib.STATE_UPDATING, Tdlib.STATE_WAITING})
public @interface ConnectionState { }
