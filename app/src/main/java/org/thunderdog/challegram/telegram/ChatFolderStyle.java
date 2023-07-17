package org.thunderdog.challegram.telegram;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
@IntDef({ChatFolderStyle.LABEL_ONLY, ChatFolderStyle.ICON_ONLY, ChatFolderStyle.LABEL_AND_ICON})
public @interface ChatFolderStyle {
  int LABEL_ONLY = 0, ICON_ONLY = 1, LABEL_AND_ICON = 2;
}
