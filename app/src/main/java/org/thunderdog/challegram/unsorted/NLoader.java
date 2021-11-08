package org.thunderdog.challegram.unsorted;

import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.getkeepsafe.relinker.ReLinker;
import com.getkeepsafe.relinker.ReLinkerInstance;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ext.ffmpeg.FfmpegLibrary;
import com.google.android.exoplayer2.ext.flac.FlacLibrary;
import com.google.android.exoplayer2.ext.opus.OpusLibrary;
import com.google.android.exoplayer2.ext.vp9.VpxLibrary;

import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.voip.VoIPController;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import me.vkryl.leveldb.LevelDB;

/**
 * Date: 7/2/17
 * Author: default
 */

public class NLoader implements ReLinker.Logger {

  private static volatile boolean loaded;

  private static volatile NLoader instance;

  public static NLoader instance () {
    if (instance == null) {
      synchronized (NLoader.class) {
        if (instance == null) {
          instance = new NLoader();
        }
      }
    }
    return instance;
  }

  public static boolean ensureLibraryLoaded () {
    return loaded || loadLibrary();
  }

  public static synchronized boolean loadLibrary () {
    if (!loaded) {
      try {
        long ms;
        ReLinkerInstance reLinker = ReLinker.recursively().log(NLoader.instance());
        int libCount = 2;
        if (Config.SO_SHARED) {
          libCount++;
        }
        List<String> libraries = new ArrayList<>(libCount);
        if (Config.SO_SHARED) {
          libraries.add("c++_shared");
        }
        libraries.add("tdjni");
        libraries.add("leveldbjni");
        libraries.add("challegram.23");
        for (String library : libraries) {
          ms = SystemClock.uptimeMillis();
          reLinker.loadLibrary(UI.getAppContext(), library, "1." + BuildConfig.SO_VERSION);
          android.util.Log.v("tgx", "Loaded " + library + " in " + (SystemClock.uptimeMillis() - ms) + "ms");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
          OpusLibrary.setLibraries(C.CRYPTO_TYPE_UNSUPPORTED);
          VpxLibrary.setLibraries(C.CRYPTO_TYPE_UNSUPPORTED);
          FlacLibrary.setLibraries();
          FfmpegLibrary.setLibraries();
          if (BuildConfig.DEBUG) {
            android.util.Log.v("tgx", String.format(Locale.US,
              "leveldb %s, libopus %s, libvpx %s, ffmpeg %s, tgvoip %s",
              LevelDB.getVersion(),
              OpusLibrary.getVersion(),
              VpxLibrary.getVersion(),
              FfmpegLibrary.getVersion(),
              VoIPController.getVersion()
            ));
          }
        }
      } catch (Throwable t) {
        RuntimeException e = new IllegalStateException(instance().collectLog() + "\n" + t.getMessage(), t);
        e.setStackTrace(t.getStackTrace());
        throw e;
      }
      loaded = true;
    }
    return loaded;
  }

  private ArrayList<String> messages;

  @Override
  public void log (String message) {
    synchronized (this) {
      if (messages == null) {
        messages = new ArrayList<>();
      }
      messages.add(message);
    }
  }

  public @Nullable String collectLog () {
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
