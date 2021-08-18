package org.thunderdog.challegram.theme;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Date: 19/01/2017
 * Author: default
 */
@Retention(RetentionPolicy.SOURCE)
@IntDef({ThemeManager.CHAT_STYLE_MODERN, ThemeManager.CHAT_STYLE_BUBBLES})
public @interface ChatStyle {}
