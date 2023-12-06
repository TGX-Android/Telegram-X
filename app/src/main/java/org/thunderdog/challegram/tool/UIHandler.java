/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 25/04/2015 at 15:58
 */
package org.thunderdog.challegram.tool;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.view.Gravity;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.MainActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Device;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGAudio;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.TdlibDelegate;
import org.thunderdog.challegram.util.Unlockable;
import org.thunderdog.challegram.widget.ToastView;

import java.io.File;

import me.vkryl.android.util.InvalidateDelegate;
import me.vkryl.android.util.LayoutDelegate;
import me.vkryl.core.StringUtils;

public class UIHandler extends Handler {
  private static final int SHOW_TOAST = 1;
  private static final int NAVIGATE = 2;
  private static final int SHOW_KEYBOARD = 5;
  private static final int UNLOCK = 6;
  private static final int OPEN_FILE = 7;
  private static final int SET_CONTROLLER = 8;
  private static final int OPEN_LINK = 13;
  private static final int OPEN_GALLERY = 14;
  private static final int OPEN_CAMERA = 15;
  private static final int SHOW_PROGRESS = 19;
  private static final int HIDE_PROGRESS = 20;
  private static final int OPEN_NUMBER = 21;
  // private static final int OPEN_MAP = 22;
  private static final int INVALIDATE_VIEW = 23;
  /*private static final int INIT_CHAT = 24;
  private static final int INIT_DEFAULT = 25;*/
  private static final int INVALIDATE = 27;
  private static final int COPY_TEXT = 28;
  private static final int LAYOUT_VIEW = 29;
  private static final int LAYOUT = 30;
  // private static final int CREATE_CHAT = 31;
  private static final int SET_PLAY_PROGRESS = 32;
  private static final int SET_PLAY_CHANGED = 33;
  private static final int NAVIGATE_BACK = 34;
  // private static final int OPEN_CHANNEL = 35;
  private static final int OPEN_AUDIO = 36;
  // private static final int LOCALE_CHANGED = 37;
  private static final int OPEN_CUSTOM_TOAST = 38;
  // private static final int REGISTER_PUSH_TOKEN = 40;
  private static final int CHECK_DISALLOW_SCREENSHOTS = 41;
  private static final int RETHROW = 42;

  public UIHandler (Context context) {
    super(context.getMainLooper());
  }

  public void showToast (@StringRes int stringRes, int duration) {
    if (stringRes == 0)
      throw new IllegalArgumentException();
    String text = Lang.getString(stringRes);
    if (!StringUtils.isEmpty(text)) {
      sendMessage(Message.obtain(this, SHOW_TOAST, duration, 0, text));
    }
  }

  public void showToast (@NonNull CharSequence message, int duration) {
    if (message == null)
      throw new IllegalArgumentException();
    if (!StringUtils.isEmpty(message)) {
      sendMessage(Message.obtain(this, SHOW_TOAST, duration, 0, message));
    }
  }

  public void showCustomToast (int resource, int duration, int positionY) {
    sendMessage(Message.obtain(this, OPEN_CUSTOM_TOAST, duration, positionY, Lang.getString(resource)));
  }

  public void showCustomToast (CharSequence message, int duration, int positionY) {
    sendMessage(Message.obtain(this, OPEN_CUSTOM_TOAST, duration, positionY, message));
  }

  public void showProgress (String string, BaseActivity.ProgressPopupListener listener) {
    sendMessage(Message.obtain(this, SHOW_PROGRESS, new Object[] {string, listener}));
  }

  public void hideProgress () {
    sendMessage(Message.obtain(this, HIDE_PROGRESS));
  }

  public void setPlayProgress (TGAudio audio, float progress) {
    sendMessage(Message.obtain(this, SET_PLAY_PROGRESS, Float.floatToIntBits(progress), 0, audio));
  }

  public void setPlayChanged (TGAudio audio, boolean playing) {
    sendMessage(Message.obtain(this, SET_PLAY_CHANGED, playing ? 1 : 0, 0, audio));
  }

  public void setController (ViewController<?> controller) {
    sendMessage(Message.obtain(this, SET_CONTROLLER, 0, 0, controller));
  }

  public void checkDisallowScreenshots () {
    sendMessage(Message.obtain(this, CHECK_DISALLOW_SCREENSHOTS, 0, 0));
  }

  public void navigateBack () {
    sendMessage(Message.obtain(this, NAVIGATE_BACK));
  }

  public void navigateTo (ViewController<?> controller) {
    sendMessage(Message.obtain(this, NAVIGATE, 0, 0, controller));
  }

  public void navigateDelayed (ViewController<?> controller, long delay) {
    sendMessageDelayed(Message.obtain(this, NAVIGATE, 0, 0, controller), delay);
  }

  public void setControllerDelayed (ViewController<?> controller, boolean asForward, boolean saveFirst, long delay) {
    int arg = 0;
    if (asForward) arg += 1;
    if (saveFirst) arg += 2;
    sendMessageDelayed(Message.obtain(this, SET_CONTROLLER, 0, arg, controller), delay);
  }

  public void showKeyboardDelayed (View view, boolean show) {
    sendMessageDelayed(Message.obtain(this, SHOW_KEYBOARD, show ? 1 : 0, 0, view), 120l);
  }

  public void invalidate (View view) {
    sendMessage(Message.obtain(this, INVALIDATE_VIEW, view));
  }

  public void invalidate (InvalidateDelegate view, long delay) {
    if (delay <= 0l) {
      sendMessage(Message.obtain(this, INVALIDATE, view));
    } else {
      sendMessageDelayed(Message.obtain(this, INVALIDATE, view), delay);
    }
  }

  public void requestLayout (View view) {
    sendMessage(Message.obtain(this, LAYOUT_VIEW, view));
  }

  public void requestLayout (LayoutDelegate view) {
    sendMessage(Message.obtain(this, LAYOUT, view));
  }

  public void unlock (Unlockable unlockable) {
    sendMessage(Message.obtain(this, UNLOCK, unlockable));
  }

  public void openFile (TdlibDelegate delegate, String displayName, File file, String mimeType) {
    sendMessage(Message.obtain(this, OPEN_FILE, 0, 0, new Object[] {delegate, displayName, file, mimeType}));
  }

  public void openFile (TdlibDelegate delegate, String displayName, File file, String mimeType, int views) {
    sendMessage(Message.obtain(this, OPEN_FILE, views, 0, new Object[] {delegate, displayName, file, mimeType}));
  }

  public void openLink (String url) {
    sendMessage(Message.obtain(this, OPEN_LINK, url));
  }

  public void unlock (Unlockable unlockable, long delay) {
    if (delay <= 0) {
      unlock(unlockable);
    } else {
      sendMessageDelayed(Message.obtain(this, UNLOCK, unlockable), delay);
    }
  }

  /*public void openUser (int userId) {
    sendMessage(Message.obtain(this, OPEN_USER, userId, 0));
  }

  public void openChannel (int channelId) {
    sendMessage(Message.obtain(this, OPEN_CHANNEL, channelId, 0));
  }*/

  /*public void createChat (TdApi.User user, Object shareItem) {
    if (shareItem == null) {
      sendMessage(Message.obtain(this, OPEN_CHAT, 1, 0, user));
    } else {
      sendMessage(Message.obtain(this, OPEN_CHAT, 1, 1, new Object[] {user, shareItem}));
    }
  }*/

  /*public void createChat (int userId, Object data, boolean isSecret) {
    sendMessage(Message.obtain(this, CREATE_CHAT, userId, isSecret ? 1 : 0, data));
  }*/

  @Deprecated
  public void openCamera (BaseActivity context, long delay, boolean isPrivate, boolean isVideo) {
    if (delay <= 0l) {
      sendMessage(Message.obtain(this, OPEN_CAMERA, isPrivate ? 1 : 0, isVideo ? 1 : 0, context));
    } else {
      sendMessageDelayed(Message.obtain(this, OPEN_CAMERA, isPrivate ? 1 : 0, isVideo ? 1 : 0, context), delay);
    }
  }

  public void openGallery (BaseActivity context, long delay, boolean sendAsFile) {
    if (delay <= 0l) {
      sendMessage(Message.obtain(this, OPEN_GALLERY, sendAsFile ? 1 : 0, 0, context));
    } else {
      sendMessageDelayed(Message.obtain(this, OPEN_GALLERY, sendAsFile ? 1 : 0, 0, context), delay);
    }
  }

  public void openAudio (long delay) {
    if (delay <= 0l) {
      sendMessage(Message.obtain(this, OPEN_AUDIO));
    } else {
      sendMessageDelayed(Message.obtain(this, OPEN_AUDIO), delay);
    }
  }

  /*public void initChat (MainActivity activity, TdApi.Chat chat) {
    sendMessage(Message.obtain(this, INIT_CHAT, new Object[] {activity, chat}));
  }

  public void initDefault (MainActivity activity) {
    sendMessage(Message.obtain(this, INIT_DEFAULT, activity));
  }

  public void initDefault (MainActivity activity, long delay) {
    sendMessageDelayed(Message.obtain(this, INIT_DEFAULT, activity), delay);
  }*/

  public void copyText (CharSequence text, int toast) {
    sendMessage(Message.obtain(this, COPY_TEXT, toast, 0, text));
  }

  public void openNumber (String number) {
    sendMessage(Message.obtain(this, OPEN_NUMBER, number));
  }

  @SuppressWarnings ("unchecked")
  @Override
  public void handleMessage (Message msg) {
    switch (msg.what) {
      case SHOW_TOAST: {
        Toast.makeText(UI.getContext(), (CharSequence) msg.obj, msg.arg1).show();
        break;
      }
      case OPEN_CUSTOM_TOAST: {
        Context context = UI.getContext();
        if (context != null) {
          Toast toast = new Toast(context);
          ToastView toastView = new ToastView(context);
          toastView.setText((CharSequence) msg.obj);
          toast.setGravity(Gravity.CENTER, 0, msg.arg2);
          toast.setView(toastView);
          toast.show();
        }
        break;
      }
      case NAVIGATE_BACK: {
        final NavigationController navigation = UI.getNavigation();
        if (navigation != null) {
          navigation.navigateBack();
        }
        break;
      }
      case NAVIGATE: {
        ViewController<?> c = (ViewController<?>) msg.obj;
        ((MainActivity) c.context()).navigateToSafely(c);
        break;
      }
      case SET_PLAY_PROGRESS: {
        TGAudio audio = (TGAudio) msg.obj;
        float progress = Float.intBitsToFloat(msg.arg1);
        audio.getListener().onPlayProgress(audio, audio.getId(), progress);
        break;
      }
      case SET_PLAY_CHANGED: {
        TGAudio audio = (TGAudio) msg.obj;
        boolean isPlaying = msg.arg1 == 1;
        audio.getListener().onPlayChanged(audio, audio.getId(), isPlaying);
        break;
      }
      case SHOW_KEYBOARD: {
        Keyboard.show((View) msg.obj);
        break;
      }
      case UNLOCK: {
        ((Unlockable) msg.obj).unlock();
        break;
      }
      case OPEN_FILE: {
        Object[] data = (Object[]) msg.obj;

        TdlibDelegate delegate = (TdlibDelegate) data[0];
        String displayName = (String) data[1];
        File file = (File) data[2];
        String mimeType = (String) data[3];

        U.openFile(delegate, displayName, file, mimeType, msg.arg1);

        data[0] = null;
        data[1] = null;
        data[2] = null;
        data[3] = null;

        break;
      }
      case OPEN_LINK: {
        Intents.openLink((String) msg.obj);
        break;
      }
      case CHECK_DISALLOW_SCREENSHOTS: {
        final BaseActivity context = UI.getUiContext();
        if (context != null) {
          context.checkDisallowScreenshots();
        }
        break;
      }
      case SET_CONTROLLER: {
        try {
          final  ViewController<?> controller = (ViewController<?>) msg.obj;
          final NavigationController navigation = UI.getNavigation();
          if (navigation != null) {
            navigation.setControllerAnimated(controller, (msg.arg2 & 1) == 1, (msg.arg2 & 2) == 2);
          }
        } catch (Throwable t) {
          Log.w("Cannot set controller", t);
        }

        break;
      }
      case COPY_TEXT: {
        try {
          U.copyText((CharSequence) msg.obj);
          if (!Device.HAS_BUILTIN_CLIPBOARD_TOASTS && msg.arg1 != 0) {
            showCustomToast(msg.arg1, Toast.LENGTH_SHORT, 0);
          }
        } catch (Throwable t) {
          Log.w("Failed to copy text", t);
          showToast(R.string.CopyTextFailed, Toast.LENGTH_SHORT);
        }
        break;
      }
      case OPEN_GALLERY: {
        Intents.openGallery((BaseActivity) msg.obj, msg.arg1 == 1);
        break;
      }
      case OPEN_AUDIO: {
        Intents.openAudio((BaseActivity) msg.obj);
        break;
      }
      case OPEN_CAMERA: {
        Intents.openCamera((BaseActivity) msg.obj, msg.arg1 == 1, msg.arg2 == 1);
        break;
      }
      case OPEN_NUMBER: {
        Intents.openNumber((String) msg.obj);
        break;
      }
      case SHOW_PROGRESS: {
        Object[] data = (Object[]) msg.obj;
        final BaseActivity context = UI.getUiContext();
        if (context != null) {
          context.showProgress((String) data[0], (BaseActivity.ProgressPopupListener) data[1]);
        }
        data[0] = null;
        data[1] = null;
        break;
      }
      case HIDE_PROGRESS: {
        final BaseActivity context = UI.getUiContext();
        if (context != null) {
          context.hideProgress(false);
        }
        break;
      }
      case INVALIDATE_VIEW: {
        ((View) msg.obj).invalidate();
        break;
      }
      case INVALIDATE: {
        ((InvalidateDelegate) msg.obj).invalidate();
        break;
      }
      case LAYOUT_VIEW: {
        ((View) msg.obj).requestLayout();
        ((View) msg.obj).invalidate();
        break;
      }
      case LAYOUT: {
        ((LayoutDelegate) msg.obj).requestLayout();
        break;
      }
    }
  }
}
