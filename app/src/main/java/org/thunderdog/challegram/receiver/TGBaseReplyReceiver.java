package org.thunderdog.challegram.receiver;

import android.annotation.TargetApi;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibNotificationExtras;

/**
 * Date: 8/4/17
 * Author: default
 */

public abstract class TGBaseReplyReceiver extends BroadcastReceiver {
  public static final String EXTRA_VOICE_REPLY = "extra_voice_reply";
  // public static final boolean READ_IMMEDIATELY = Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !BuildConfig.DEBUG;

  @Override
  @TargetApi(Build.VERSION_CODES.KITKAT_WATCH)
  public void onReceive (Context context, Intent intent) {
    final Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
    if (remoteInput == null) {
      return;
    }
    final CharSequence text = remoteInput.getCharSequence(EXTRA_VOICE_REPLY);
    TdlibNotificationExtras extras = TdlibNotificationExtras.parse(intent.getExtras());
    TdlibManager.performExternalReply(context, text, extras);
  }
}