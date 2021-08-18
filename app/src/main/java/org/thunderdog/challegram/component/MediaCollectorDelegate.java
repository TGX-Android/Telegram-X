/**
 * File created on 01/09/15 at 06:27
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.component;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.mediaview.MediaViewController;
import org.thunderdog.challegram.mediaview.data.MediaStack;

public interface MediaCollectorDelegate {
  MediaStack collectMedias (long fromMessageId, @Nullable TdApi.SearchMessagesFilter filter);
  void modifyMediaArguments (Object cause, MediaViewController.Args args);
}
