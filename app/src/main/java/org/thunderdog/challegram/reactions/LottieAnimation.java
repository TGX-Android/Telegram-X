package org.thunderdog.challegram.reactions;

import android.graphics.Bitmap;
import android.text.TextUtils;
import android.util.Log;

import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.N;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.tool.UI;

import java.io.File;
import java.io.IOException;

public class LottieAnimation {
  private long nativeHandle;
  private static final String TAG = "LottieAnimation";
  private long frameCount;
  private double duration, frameRate;

  public LottieAnimation (String filePath, int width, int height) throws IOException {
    long t = System.currentTimeMillis();
    String json = U.gzipFileToString(filePath);
    if (TextUtils.isEmpty(json))
      throw new IOException("Failed to preload lottie animation from " + filePath);
    double[] meta = new double[3];
    nativeHandle = N.createLottieDecoder(filePath, json, meta, 0);
    frameCount = (long) meta[0];
    frameRate = meta[1];
    duration = meta[2];

    File cacheDir = new File(UI.getContext().getCacheDir(), "reactionAnims");
    if (!cacheDir.exists())
      cacheDir.mkdirs();
    File cacheFile = new File(cacheDir, new File(filePath).getName() + "_" + width + "_" + height + ".cache");
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    N.createLottieCache(nativeHandle, cacheFile.getAbsolutePath(), null, bitmap, false, false);

    if (BuildConfig.DEBUG)
      Log.d(TAG, "PreloadedLottieAnimation: loaded " + filePath + ", " + frameCount + " frames, " + frameRate + " fps, " + duration + " seconds in " + (System.currentTimeMillis() - t));
  }

  public void release () {
    N.destroyLottieDecoder(nativeHandle);
    nativeHandle = 0;
  }

  public long getFrameCount () {
    return frameCount;
  }

  public double getFrameRate () {
    return frameRate;
  }

  public void getFrame (Bitmap bmp, long frame) {
    if (nativeHandle == 0) {
      Log.w(TAG, "getFrame: called with released decoder");
      return;
    }
    N.getLottieFrame(nativeHandle, bmp, frame);
  }

  @Override
  protected void finalize () throws Throwable {
    super.finalize();
    if (nativeHandle != 0) {
      release();
    }
  }
}
