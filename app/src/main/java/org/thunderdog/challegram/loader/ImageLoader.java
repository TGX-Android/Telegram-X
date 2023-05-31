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
 * File created on 06/05/2015 at 12:40
 */
package org.thunderdog.challegram.loader;

import android.graphics.Bitmap;

import androidx.annotation.Keep;
import androidx.collection.ArraySet;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class ImageLoader {
  private static ImageLoader instance;

  public static ImageLoader instance () {
    if (instance == null) {
      instance = new ImageLoader();
    }
    return instance;
  }

  private final ImageThread thread = new ImageThread();

  private final HashMap<String, ImageWatchers> watchers = new HashMap<>();
  private final HashMap<String, ArrayList<String>> workers = new HashMap<>();

  private ImageLoader () {
    ImageCache.instance();
  }

  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  @Keep
  private final Set<Watcher> tempWatchers = new ArraySet<>();

  public void loadFile (final ImageFile file, ImageReader.Listener listener) {
    AtomicReference<WatcherReference> reference = new AtomicReference<>();
    Watcher watcher = new Watcher() {
      @Override
      public void imageLoaded (ImageFile file, boolean successful, Bitmap bitmap) {
        tempWatchers.remove(this);
        listener.onImageLoaded(successful, bitmap);
        removeWatcher(reference.get());
      }
    };
    tempWatchers.add(watcher);
    reference.set(new WatcherReference(watcher));
    requestFile(file, reference.get());
  }

  public void requestFile (final ImageFile file, WatcherReference reference) {
    if (Thread.currentThread() != thread) {
      thread.request(file, reference);
      return;
    }

    if (Log.isEnabled(Log.TAG_IMAGE_LOADER)) {
      Log.d(Log.TAG_IMAGE_LOADER, "#%s: requestFile called: type: %s, %s", file.toString(), file.getClass().getSimpleName(), file.getFilePath());
    }

    ImageWatchers record;

    synchronized (watchers) {
      record = watchers.get(file.toString());
    }

    if (record == null) {
      ImageActor actor = new ImageActor(file);
      record = new ImageWatchers(file, actor, reference);
      synchronized (watchers) {
        watchers.put(file.toString(), record);
      }
      if (actor.act()) {
        synchronized (watchers) {
          final int fileId = file.getId();
          final Tdlib tdlib = file.tdlib();
          final boolean isPersistent = file instanceof ImageFileRemote;
          final String cacheKey = file.getFileLoadKey();

          ArrayList<String> actors = workers.get(cacheKey);

          if (actors == null) {
            actors = new ArrayList<>();
            actors.add(file.toString());

            if (Log.isEnabled(Log.TAG_IMAGE_LOADER)) {
              Log.v(Log.TAG_IMAGE_LOADER, "#%s: loading from remote", file.toString());
            }

            workers.put(cacheKey, actors);

            if (isPersistent) {
              ((ImageFileRemote) file).extractFile(object -> {
                switch (object.getConstructor()) {
                  case TdApi.File.CONSTRUCTOR: {
                    downloadFilePersistent((ImageFileRemote) file, (TdApi.File) object);
                    break;
                  }
                  case TdApi.Error.CONSTRUCTOR: {
                    UI.showError(object);
                    break;
                  }
                }
              });
            } else {
              if (!Config.DEBUG_DISABLE_DOWNLOAD) {
                tdlib.client().send(new TdApi.DownloadFile(fileId, 32, 0, 0, false), tdlib.imageLoadHandler());
              }
            }
          } else {
            if (!actors.contains(file.toString())) {
              if (Log.isEnabled(Log.TAG_IMAGE_LOADER)) {
                Log.v(Log.TAG_IMAGE_LOADER, "#%s: another actor added to the loading list, total: %d", file.toString(), actors.size() + 1);
              }
              actors.add(file.toString());
            }
          }
        }
      }
    } else {
      synchronized (watchers) {
        if (Log.isEnabled(Log.TAG_IMAGE_LOADER)) {
          Log.v(Log.TAG_IMAGE_LOADER, "#%s: another watcher joined same actor, total: %d", file.toString(), watchers.size() + 1);
        }
        record.addWatcher(reference);
      }
    }
  }

  void downloadFilePersistent (final ImageFileRemote persistentFile, final TdApi.File file) {
    if (Thread.currentThread() != thread) {
      thread.downloadFilePersistent(persistentFile, file);
      return;
    }

    Tdlib tdlib = persistentFile.tdlib();

    synchronized (watchers) {
      ArrayList<String> actors = workers.get(persistentFile.getFileLoadKey());
      if (actors != null && !actors.isEmpty()) {
        workers.put(ImageFile.getFileLoadKey(tdlib, file.id), actors);
      }
    }

    persistentFile.updateRemoteFile(file);

    if (TD.isFileLoadedAndExists(file)) {
      onLoad(tdlib, file);
    } else {
      if (!Config.DEBUG_DISABLE_DOWNLOAD) {
        tdlib.client().send(new TdApi.DownloadFile(file.id, 1, 0, 0, false), tdlib.imageLoadHandler());
      }
    }
  }

  public void removeWatcher (WatcherReference reference) {
    if (Thread.currentThread() != thread) {
      thread.removeWatcher(reference);
      return;
    }

    synchronized (watchers) {
      ArrayList<String> itemsToRemove = null;
      Set<Map.Entry<String, ImageWatchers>> entrySet = watchers.entrySet();

      for (Map.Entry<String, ImageWatchers> entry : entrySet) {
        ImageWatchers record = entry.getValue();

        if (record.removeWatcher(reference) && !record.hasWatchers()) {
          if (itemsToRemove == null) {
            itemsToRemove = new ArrayList<>();
          }

          itemsToRemove.add(entry.getKey());
        }
      }

      if (itemsToRemove != null) {
        for (String item : itemsToRemove) {
          ImageWatchers record = watchers.remove(item);

          if (record != null) {
            record.getActor().cancel();

            final ImageFile file = record.getFile();
            final Tdlib tdlib = file.tdlib();
            if (tdlib == null) {
              continue;
            }
            final boolean isPersistent = file instanceof ImageFileRemote;
            final String cacheKey = file.getFileLoadKey();
            final ArrayList<String> actors = workers.get(cacheKey);
            if (actors != null && actors.remove(item) && actors.isEmpty()) {
              workers.remove(cacheKey);
              if (isPersistent && ((ImageFileRemote) file).isRemoteFileReady()) {
                workers.remove(ImageFile.getFileLoadKey(tdlib, file.getId()));
              }
              if (!isPersistent && file.needCancellation()) {
                tdlib.client().send(new TdApi.CancelDownloadFile(file.getId(), file.isCancellationOnlyPending()), tdlib.okHandler());
              }
            }
          }
        }
      }
    }
  }

  public boolean onProgress (Tdlib tdlib, TdApi.File file) {
    boolean found = false;

    synchronized (watchers) {
      String cacheKey = ImageFile.getFileLoadKey(tdlib, file.id);
      ArrayList<String> actors = workers.get(cacheKey); // remoteFiles.get(file.id) != null ? loadingRemoteFiles.get(file.remote.id) : loadingFiles.get(file.id);
      if (actors == null) {
        cacheKey = ImageFile.getFileLoadKey(tdlib, file.remote.id);
        actors = workers.get(cacheKey);
      }

      if (actors == null) {
        return false;
      }

      float progress = TD.getFileProgress(file);

      for (String actor : actors) {
        ImageWatchers record = watchers.get(actor);
        if (record != null) {
          record.getFile().updateFile(file);
          record.getActor().onProgress(file);
          for (WatcherReference reference : record.getWatchers()) {
            reference.imageProgress(record.getFile(), progress);
          }
          found = true;
        }
      }
    }

    if (Log.isEnabled(Log.TAG_IMAGE_LOADER)) {
      if (found) {
        Log.v(Log.TAG_IMAGE_LOADER, "#%d (raw): successfully dispatched progress event", file.id);
      } else {
        Log.v(Log.TAG_IMAGE_LOADER, "#%d (raw): failed to dispatch progress event (no listeners)", file.id);
      }
    }

    return found;
  }

  public boolean onLoad (Tdlib tdlib, TdApi.File file) {
    boolean found = false;

    synchronized (watchers) {
      ArrayList<String> actors = workers.get(ImageFile.getFileLoadKey(tdlib, file.id));
      if (actors == null) {
        actors = workers.get(ImageFile.getFileLoadKey(tdlib, file.remote.id));
      }

      if (actors != null) {
        for (String actor : actors) {
          ImageWatchers record = watchers.get(actor);
          if (record != null) {
            thread.onLoad(record.getActor(), file);
            found = true;
          }
        }
      }
    }

    if (Log.isEnabled(Log.TAG_IMAGE_LOADER)) {
      if (found) {
        Log.v(Log.TAG_IMAGE_LOADER, "#%d (raw): successfully dispatched load event", file.id);
      } else {
        Log.v(Log.TAG_IMAGE_LOADER, "#%d (raw): failed to dispatch load event (no listeners)", file.id);
      }
    }

    return found;
  }

  public void onResult (ImageFile file, boolean success, Bitmap bitmap) {
    if (Thread.currentThread() != thread) {
      thread.onResult(file, success, bitmap);
      return;
    }

    synchronized (watchers) {
      ImageWatchers record = watchers.get(file.toString());

      if (Log.isEnabled(Log.TAG_IMAGE_LOADER)) {
        if (success) {
          Log.d(Log.TAG_IMAGE_LOADER, "#%s: completed, watches: %d", file.toString(), record == null ? -1 : record.getWatchers().size());
        } else {
          Log.w(Log.TAG_IMAGE_LOADER, "#%s: failed, watches: %d", file.toString(), record == null ? -1 : record.getWatchers().size());
        }
      }

      if (record != null) {
        watchers.remove(file.toString());

        for (WatcherReference reference : record.getWatchers()) {
          reference.imageLoaded(file, success, bitmap);
        }
      } else {
        if (Log.isEnabled(Log.TAG_IMAGE_LOADER)) {
          Log.d(Log.TAG_IMAGE_LOADER, "#%s: wanted to dispatch result, but there're no listeners anymore", file.toString());
        }
        if (success && !file.shouldBeCached()) {
          if (Log.isEnabled(Log.TAG_IMAGE_LOADER)) {
            Log.d(Log.TAG_IMAGE_LOADER, "#%s: recycling, because there will be no references", file.toString());
          }
          ((Bitmap) bitmap).recycle();
        }
      }

      final boolean isRemote = file instanceof ImageFileRemote;
      final Tdlib tdlib = file.tdlib();
      final String cacheKey = file.getFileLoadKey();
      ArrayList<String> actors = workers.get(cacheKey);

      if (actors != null) {
        if (actors.remove(file.toString()) && actors.isEmpty()) {
          workers.remove(cacheKey);
          if (isRemote && ((ImageFileRemote) file).isRemoteFileReady()) {
            workers.remove(ImageFile.getFileLoadKey(tdlib, file.getId()));
          }
        }
      }
    }
  }

  public void clear (int accountId, boolean withMemcache) {
    if (Thread.currentThread() != thread) {
      thread.clear(accountId, withMemcache);
      return;
    }

    synchronized (watchers) {
      if (accountId == TdlibAccount.NO_ID) {
        workers.clear();
      } else {
        Set<Map.Entry<String, ArrayList<String>>> entrySet = workers.entrySet();
        ArrayList<String> keysToRemove = null;
        String prefix = accountId + "_";
        // String remotePrefix = accountId + "_remote_";
        for (Map.Entry<String, ArrayList<String>> entry : entrySet) {
          String key = entry.getKey();
          if (key.startsWith(prefix)) {
            if (keysToRemove == null) {
              keysToRemove = new ArrayList<>();
            }
            keysToRemove.add(key);
          }
        }
        if (keysToRemove != null) {
          for (String key : keysToRemove) {
            ArrayList<String> actors = workers.remove(key);
            if (actors != null && !actors.isEmpty()) {
              // TODO ?
            }
          }
        }
      }

      if (!watchers.isEmpty()) {
        Set<Map.Entry<String, ImageWatchers>> entrySet = watchers.entrySet();

        for (Map.Entry<String, ImageWatchers> entry : entrySet) {
          ImageWatchers record = entry.getValue();

          ImageFile file = record.getFile();
          if (accountId != TdlibAccount.NO_ID && file.accountId() != accountId) {
            continue;
          }

          record.getActor().cancel();
          for (WatcherReference reference : record.getWatchers()) {
            reference.imageLoaded(record.getFile(), false, null);
          }
        }

        watchers.clear();
      }
    }

    if (accountId != TdlibAccount.NO_ID) {
      ImageCache.instance().clearForAccount(accountId);
    } else {
      ImageCache.instance().clear(withMemcache);
    }
  }
}
