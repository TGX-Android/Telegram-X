package org.thunderdog.challegram.reactions;

import android.graphics.Bitmap;
import android.text.TextUtils;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.N;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class LottieAnimationThreadPool {
  private static final ThreadPoolExecutor threadPool = new ThreadPoolExecutor(1, Math.max(2, Runtime.getRuntime().availableProcessors()), 30, TimeUnit.SECONDS, new LinkedBlockingDeque<>());

  public static void submit (Runnable r) {
    threadPool.submit(r);
  }

  public static CancellableRunnable loadOneAnimation (Tdlib tdlib, TdApi.Sticker animation, Consumer<LottieAnimation> callback, int width, int height) {
    final String[] filePath = {null};
    CancellableRunnable runnable = new CancellableRunnable() {
      @Override
      public void run () {
        if (cancelled)
          return;
        if (filePath[0] == null) {
          UI.post(() -> callback.accept(null));
          return;
        }
        try {
          LottieAnimation anim = new LottieAnimation(filePath[0], width, height);
          if (cancelled) {
            anim.release();
            return;
          }
          UI.post(() -> {
            if (cancelled) {
              anim.release();
              return;
            }
            callback.accept(anim);
          });
        } catch (IOException x) {
          Log.e(Log.TAG_IMAGE_LOADER, x);
        }
      }
    };

    if (animation == null) {
      submit(runnable);
      return runnable;
    }

    if (animation.sticker.local.isDownloadingCompleted) {
      filePath[0] = animation.sticker.local.path;
      submit(runnable);
    } else {
      tdlib.send(new TdApi.DownloadFile(animation.sticker.id, 32, 0, 0, true), res -> {
        if (res instanceof TdApi.File) {
          if (runnable.isCancelled())
            return;
          TdApi.File file = (TdApi.File) res;
          if (file.local.isDownloadingCompleted) {
            filePath[0] = file.local.path;
            submit(runnable);
          }
        }
      });
    }

    return runnable;
  }

  public static CancellableRunnable loadMultipleAnimations (Tdlib tdlib, Consumer<LottieAnimation[]> callback, long timeout, TdApi.Sticker... animations) {
    final LottieAnimation[] loadedAnims = new LottieAnimation[animations.length];
    final ArrayList<CancellableRunnable> loadingAnims = new ArrayList<>(animations.length);
    Runnable timeoutHandler = () -> {
      for (CancellableRunnable cr : loadingAnims) {
        cr.cancel();
      }
      callback.accept(loadedAnims);
    };
    CancellableRunnable res = new CancellableRunnable() {
      @Override
      public void cancel () {
        super.cancel();
        for (CancellableRunnable cr : loadingAnims) {
          cr.cancel();
        }
        for (LottieAnimation anim : loadedAnims) {
          anim.release();
        }
      }

      @Override
      public void run () {

      }
    };

    UI.post(timeoutHandler, timeout);

    for (int i = 0; i < animations.length; i++) {
      final int _i = i;
      CancellableRunnable cr = loadOneAnimation(tdlib, animations[i], anim -> {
        loadedAnims[_i] = anim;
        for (LottieAnimation a : loadedAnims) {
          if (a == null)
            return;
        }
        UI.removePendingRunnable(timeoutHandler);
        UI.post(() -> {
          if (res.isCancelled()) // Already released in cancel()
            return;
          callback.accept(loadedAnims);
        });
      }, 500, 500);
      loadingAnims.add(cr);
    }

    return res;
  }

  public static void createCacheForPreloadedAnimation (File file, int width, int height) {
    threadPool.submit(() -> {
      String json = U.gzipFileToString(file.getAbsolutePath());
      if (TextUtils.isEmpty(json)) {
        android.util.Log.e("tdlib", "Failed to read " + file.getAbsolutePath() + " for cache");
        return;
      }
      long ptr = N.createLottieDecoder(file.getAbsolutePath(), json, null, 0);
      if (ptr == 0) {
        android.util.Log.e("tdlib", "Failed to create lottie decoder for " + file.getAbsolutePath());
        return;
      }

      File cacheDir = new File(UI.getContext().getCacheDir(), "reactionAnims");
      if (!cacheDir.exists())
        cacheDir.mkdirs();
      File cacheFile = new File(cacheDir, file.getName() + "_" + width + "_" + height + ".cache");
      Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
      N.createLottieCache(ptr, cacheFile.getAbsolutePath(), null, bitmap, true, false);
      N.destroyLottieDecoder(ptr);
      if (BuildConfig.DEBUG) {
        android.util.Log.i("tdlib", "Created cache for lottie animation " + file.getAbsolutePath());
      }
    });
  }
}
