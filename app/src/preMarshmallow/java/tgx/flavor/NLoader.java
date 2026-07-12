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
 * File created on 07/02/2017
 */
package tgx.flavor;

import android.os.SystemClock;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.getkeepsafe.relinker.ReLinker;
import com.getkeepsafe.relinker.ReLinkerInstance;

import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.N;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.List;

public class NLoader {

  public static boolean loadLibraries () {
    return instance().loadLibrary();
  }

  public static String collectLog () {
    String log = instance().collectLogImpl();
    return log != null ? log : "";
  }

  private static NLoader instance;

  private static synchronized NLoader instance() {
    if (instance == null) {
      instance = new NLoader();
    }
    return instance;
  }

  private boolean loaded;
  private List<String> messages;
  private final ReLinker.Logger logger;

  private NLoader () {
    logger = new ReLinker.Logger() {
      @Override
      public void log (String message) {
        synchronized (this) {
          if (messages == null) {
            messages = new ArrayList<>();
          }
          messages.add(message);
        }
      }
    };
  }

  private void loadLibraryImpl (ReLinkerInstance reLinker, String library, @Nullable String version) {
    long ms = SystemClock.uptimeMillis();
    reLinker.loadLibrary(UI.getAppContext(), library, version);
    android.util.Log.v("tgx", "Loaded " + library + " in " + (SystemClock.uptimeMillis() - ms) + "ms");
  }

  private synchronized boolean loadLibrary () {
    if (loaded) {
      return true;
    }
    try {
      ReLinkerInstance reLinker = ReLinker.recursively().log(logger);
      if (BuildConfig.SHARED_STL) {
        loadLibraryImpl(reLinker, "c++_shared", BuildConfig.NDK_VERSION);
      }
      loadLibraryImpl(reLinker, "cryptox", BuildConfig.OPENSSL_VERSION_FULL);
      loadLibraryImpl(reLinker, "sslx", BuildConfig.OPENSSL_VERSION_FULL);
      loadLibraryImpl(reLinker, "tdjni", BuildConfig.TDLIB_VERSION);
      loadLibraryImpl(reLinker, "leveldbjni", BuildConfig.LEVELDB_VERSION);
      loadLibraryImpl(reLinker, "tgcallsjni", BuildConfig.JNI_VERSION /*TODO: separate variable?*/);
      loadLibraryImpl(reLinker, "tgxjni", BuildConfig.JNI_VERSION);
      N.setupLibraries();
    } catch (Throwable t) {
      RuntimeException e = new IllegalStateException(collectLogImpl() + "\n" + t.getMessage(), t);
      e.setStackTrace(t.getStackTrace());
      throw e;
    }
    loaded = true;
    return true;
  }

  @Nullable
  private String collectLogImpl () {
    String log = null;
    synchronized (this) {
      if (messages != null && !messages.isEmpty()) {
        log = "==== ReLinker ====\n" + TextUtils.join("\n", messages) + "\n==== ReLinker END ====\n";
      }
      messages = null;
    }
    return log;
  }
}
