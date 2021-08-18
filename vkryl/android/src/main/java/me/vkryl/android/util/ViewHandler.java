package me.vkryl.android.util;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;

import androidx.annotation.NonNull;

/**
 * Date: 3/10/18
 * Author: default
 */

public final class ViewHandler extends Handler {
  public ViewHandler () {
    super(Looper.getMainLooper());
  }

  private static final int ACTION_INVALIDATE = 0;

  public void invalidate (View view, long delay) {
    Message msg = Message.obtain(this, ACTION_INVALIDATE, view);
    if (delay > 0) {
      sendMessageDelayed(msg, delay);
    } else {
      sendMessage(msg);
    }
  }

  public void cancelInvalidate (View view) {
    removeMessages(ACTION_INVALIDATE, view);
  }

  private static final int ACTION_REQUEST_LAYOUT = 1;



  @Override
  public void handleMessage (@NonNull Message msg) {
    switch (msg.what) {
      case ACTION_INVALIDATE: {
        if (msg.obj instanceof View) {
          ((View) msg.obj).invalidate();
        } else if (msg.obj instanceof ViewProvider) {
          ((ViewProvider) msg.obj).invalidate();
        }
        break;
      }
    }
  }
}
