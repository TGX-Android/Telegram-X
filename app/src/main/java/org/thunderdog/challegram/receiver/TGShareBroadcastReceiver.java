package org.thunderdog.challegram.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;

import me.vkryl.core.StringUtils;

/**
 * Date: 8/14/17
 * Author: default
 */

public class TGShareBroadcastReceiver extends BroadcastReceiver {
  @Override
  public void onReceive (Context context, Intent intent) {
    String url = intent.getDataString();
    if (!StringUtils.isEmpty(url)) {
      Intent shareIntent = new Intent(Intent.ACTION_SEND);
      shareIntent.setType("text/plain");
      shareIntent.putExtra(Intent.EXTRA_TEXT, url);
      Intent chooserIntent = Intent.createChooser(shareIntent, Lang.getString(R.string.ShareLink));
      chooserIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      context.startActivity(chooserIntent);
    }
  }
}
