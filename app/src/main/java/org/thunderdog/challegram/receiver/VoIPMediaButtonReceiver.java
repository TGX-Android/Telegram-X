package org.thunderdog.challegram.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

import org.thunderdog.challegram.service.TGCallService;

/**
 * Created by grishka on 21.11.16.
 */

@Deprecated
public class VoIPMediaButtonReceiver extends BroadcastReceiver{
	@Override
	public void onReceive(Context context, Intent intent){
		if(Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())){
			TGCallService service = TGCallService.currentInstance();
			if (service != null) {
				KeyEvent e = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
				service.onMediaButtonEvent(e);
			}
		}
	}
}
