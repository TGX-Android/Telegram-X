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
 * File created on 06/05/2015 at 14:31
 */
package org.thunderdog.challegram.loader;

import android.graphics.Bitmap;
import android.os.CancellationSignal;

import androidx.palette.graphics.Palette;

import com.google.android.exoplayer2.metadata.id3.ApicFrame;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.player.AudioController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibManager;

import me.vkryl.td.Td;

public class ImageActor implements ImageReader.Listener, AudioController.ApicListener {
  private ImageFile file;

  private volatile boolean isCancelled;

  public ImageActor (ImageFile file) {
    this.file = file;
  }

  public void cancel () {
    this.isCancelled = true;
    if (cancellationSignal != null)
      cancellationSignal.cancel();
    if (file instanceof ImageApicFile) {
      ImageApicFile apicFile = (ImageApicFile) file;
      TdlibManager.instance().audio().cancelApic(apicFile.tdlib(), apicFile.getMessage(), this);
    }
  }

  public boolean isCancelled () {
    return isCancelled;
  }

  private CancellationSignal cancellationSignal;

  public CancellationSignal getCancellationSignal () {
    if (cancellationSignal == null)
      cancellationSignal = new CancellationSignal();
    if (isCancelled)
      cancellationSignal.cancel();
    return cancellationSignal;
  }

  public void watcherJoined (WatcherReference reference) {
    reference.imageProgress(file, file.getProgressFactor());
  }

  private static boolean isCustomFile (ImageFile file) {
    return
      file instanceof ImageGalleryFile ||
      file instanceof ImageFileLocal ||
      file instanceof ImageMp3File ||
      file instanceof ImageVideoThumbFile ||
      file instanceof ImageFilteredFile ||
      file instanceof ImageApicFile;
  }

  public boolean act () {
    if (Config.DEBUG_DISABLE_IMAGES) {
      return false;
    }

    if (isCancelled) {
      Log.i(Log.TAG_IMAGE_LOADER, "#%s: tried to start working, but actor has been cancelled", file.toString());
      return false;
    }

    final TdApi.File rawFile = file.getFile();

    if (isCustomFile(file) || ImageLoader.isFileLoaded(file.tdlib(), rawFile)) {
      act(file.getFilePath());
      return false;
    }

    return true;
  }

  private void act (String path) {
    if (isCancelled) return;

    if (Log.isEnabled(Log.TAG_IMAGE_LOADER)) {
      Log.v(Log.TAG_IMAGE_LOADER, "#%s: loading from local storage: %s", file.toString(), path);
    }

    if (file instanceof ImageApicFile) {
      requestApic((ImageApicFile) file);
      return;
    }

    ImageReader.instance().readImage(this, file, path, this);
  }

  // Local image loader

  @Override
  public void onImageLoaded (boolean success, Bitmap result) {
    if (success) {
      if (file.needPalette()) {
        try {
          file.setPalette(Palette.from((Bitmap) result).generate().getDarkVibrantSwatch());
        } catch (Throwable t) {
          Log.e("Failed to generate palette", t);
        }
      }
      if (file.shouldBeCached()) {
        ImageCache.instance().putBitmap(file, (Bitmap) result);
      } else if (isCancelled) {
        Log.i(Log.TAG_IMAGE_LOADER, "#%s: recycling bitmap because associated actor is canceled and image should not be cached", file.toString());
        ((Bitmap) result).recycle();
        return;
      }
      /*if (file.needOverlayCalcs()) {
        file.makeOverlayCalcs((Bitmap) result);
      }*/
    }

    ImageLoader.instance().onResult(file, success, result);
  }


  // TDLib image loader

  public void onProgress (TdApi.File file) {
    this.file.updateFile(file);
  }

  public void onLoad (TdApi.File downloadedFile) {
    if (!isCancelled) {
      Td.copyTo(downloadedFile, this.file.getFile());
      act(downloadedFile.local.path);
    }
  }

  // Apic loader

  private void requestApic (ImageApicFile file) {
    ApicFrame apicFrame = TdlibManager.instance().audio().requestApic(file.tdlib(), file.getMessage(), this);
    if (apicFrame != null) {
      onApicLoaded(file.tdlib(), file.getMessage(), apicFrame);
    }
  }

  @Override
  public void onApicLoaded (Tdlib tdlib, TdApi.Message message, ApicFrame apicFrame) {
    if (!isCancelled) {
      ((ImageApicFile) file).setApicFrame(apicFrame);
      ImageReader.instance().readImage(this, file, null, this);
    }
  }
}
