package org.thunderdog.challegram.telegram;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
@IntDef(value = {ChatFolderOptions.DISPLAY_AT_TOP}, flag = true)
public @interface ChatFolderOptions {
  int DISPLAY_AT_TOP = 1;
}
