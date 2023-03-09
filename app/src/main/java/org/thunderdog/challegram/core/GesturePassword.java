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
 * File created on 29/08/2015 at 03:47
 */
package org.thunderdog.challegram.core;

import android.gesture.Gesture;
import android.gesture.GestureStore;
import android.gesture.Prediction;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.tool.UI;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import me.vkryl.core.StringUtils;

public class GesturePassword {
  private static final double PREDICTION_MINIMUM = 2.35;

  private final GestureStore store;
  private final @Nullable String suffix;
  private boolean isLoaded;

  public GesturePassword (@Nullable String suffix) {
    this.suffix = suffix;
    store = new GestureStore();
    store.setOrientationStyle(GestureStore.ORIENTATION_SENSITIVE);
    store.setSequenceType(GestureStore.SEQUENCE_SENSITIVE);
  }

  private File getFile () {
    String path = UI.getAppContext().getFilesDir().getPath();
    if (path.charAt(path.length() - 1) == '/') {
      path = path + "int/temp.g";
    } else {
      path = path + "/int/temp.g";
    }
    if (!StringUtils.isEmpty(suffix)) {
      path = path + "." + suffix;
    }
    File file = new File(path);
    File parent = file.getParentFile();
    if (!parent.exists() && !parent.mkdirs()) {
      return null;
    }
    return file;
  }

  private void loadGesture () {
    if (!isLoaded) {
      File file = getFile();

      if (file != null && file.exists()) {
        try {
          store.load(new FileInputStream(file), true);
          isLoaded = true;
        } catch (Throwable t) {
          Log.e("Cannot load gesture", t);
        }
      }
    }
  }

  private GestureStore tempStore;
  private Gesture tempGesture;

  public void saveTemp (Gesture gesture) {
    if (tempStore == null) {
      tempStore = new GestureStore();
      tempStore.setOrientationStyle(GestureStore.ORIENTATION_SENSITIVE);
      tempStore.setSequenceType(GestureStore.SEQUENCE_SENSITIVE);
    } else {
      tempStore.removeEntry("main");
    }
    tempStore.addGesture("main", gesture);
    tempGesture = gesture;
  }

  public boolean save (Gesture gesture) {
    File file = getFile();

    try {
      if (file != null) {
        if (file.exists()) {
          //noinspection ResultOfMethodCallIgnored
          file.delete();
        }
        //noinspection ResultOfMethodCallIgnored
        file.createNewFile();
        store.removeEntry("main");
        store.addGesture("main", gesture);
        if (tempGesture != null) {
          store.addGesture("main", tempGesture);
        }
        store.save(new FileOutputStream(file), true);
        return true;
      }
    } catch (FileNotFoundException t) {
      Log.w("File not found", t);
    } catch (IOException io) {
      Log.w("IO Exception", io);
    }

    return false;
  }

  public boolean compare (Gesture gesture, boolean temp, Callback callback) {
    if (temp) {
      return tempStore != null && predict(tempStore, gesture);
    }
    loadGesture();
    if (!isLoaded) {
      if (callback != null) {
        callback.onGestureLoadError();
      }
      return false;
    }
    return predict(store, gesture);
  }

  private static boolean predict (GestureStore store, Gesture gesture) {
    ArrayList<Prediction> predictions = store.recognize(gesture);

    if (predictions.size() == 0) {
      return false;
    }

    /*double total = 0.0;

    for (Prediction prediction : predictions) {
      total += prediction.score;
    }

    total /= predictions.size();*/

    double minimum = 100;

    for (Prediction prediction : predictions) {
      minimum = Math.min(prediction.score, minimum);
    }

    return minimum >= PREDICTION_MINIMUM;
  }

  public interface Callback {
    void onGestureLoadError ();
  }
}
