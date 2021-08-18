package org.thunderdog.challegram.telegram;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Date: 3/4/18
 * Author: default
 */

@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface TdlibThread {
}
