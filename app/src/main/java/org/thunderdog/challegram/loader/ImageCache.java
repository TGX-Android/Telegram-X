/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 06/05/2015 at 13:32
 */
package org.thunderdog.challegram.loader;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.tool.UI;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import me.vkryl.core.unit.ByteUnit;

public class ImageCache {
  private static ImageCache instance;

  public static ImageCache instance () {
    if (instance == null) {
      instance = new ImageCache();
    }
    return instance;
  }

  public static HashMap<String, AtomicInteger> getReferenceCounters () {
    return instance().getCounters();
  }

  private final HashMap<String, AtomicInteger> counters;
  private final HashMap<String, Integer> rotations;
  private final HashMap<String, WeakReference<Bitmap>> references;
  private class BitmapLruCache extends LruCache<String, Bitmap> {
    public BitmapLruCache (int maxSize) {
      super(maxSize);
    }

    @Override
    protected int sizeOf (String key, Bitmap value) {
      if (value == null || value.isRecycled())
        return 1;
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB_MR1) {
        return value.getRowBytes() * value.getHeight();
      } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
        return value.getByteCount();
      } else {
        return value.getAllocationByteCount();
      }
    }

    @Override
    protected void entryRemoved (boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {
      if (!counters.containsKey(key)) {
        if (Log.isEnabled(Log.TAG_IMAGE_LOADER)) {
          Log.v(Log.TAG_IMAGE_LOADER, "#%s: recycling bitmap in entryRemoved", key);
        }
        U.recycle(oldValue);
      }
    }
  }

  private final BitmapLruCache memcache;

  private static final boolean ALLOW_REFERENCES = true;

  private ImageCache () {
    counters = new HashMap<>();
    rotations = new HashMap<>();
    memcache = new BitmapLruCache(getMemcacheSize());
    references = new HashMap<>();
  }

  private boolean hasInMemcache (String key) {
    return memcache.get(key) != null;
  }

  private int getMemcacheSize () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
      int mib = Math.min(15, ((ActivityManager) UI.getAppContext().getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass() / 7);
      return (int) ByteUnit.MIB.toBytes(mib);
    } else {
      return (int) ByteUnit.MIB.toBytes(3);
    }
  }

  public HashMap<String, AtomicInteger> getCounters () {
    return counters;
  }

  public void addReference (ImageFile file, Bitmap bitmap) {
    if (file != null && bitmap != null) {
      synchronized (counters) {
        final String key = file.toString();
        AtomicInteger count = counters.get(key);
        if (count != null) {
          count.incrementAndGet();
        } else {
          counters.put(key, count = new AtomicInteger(1));
        }
        if (Log.isEnabled(Log.TAG_IMAGE_LOADER)) {
          Log.v(Log.TAG_IMAGE_LOADER, "#%s: reference++: %d", key, count.get());
        }
      }
    } else {
      if (Log.isEnabled(Log.TAG_IMAGE_LOADER)) {
        Log.w(Log.TAG_IMAGE_LOADER, "#%s: addReference failed bitmap: %s", file != null ? file.toString() : "null", getBitmapInfo(bitmap));
      }
    }
  }

  public void removeReference (ImageFile file, Bitmap bitmap) {
    if (file != null && bitmap != null) {
      synchronized (counters) {
        String key = file.toString();

        AtomicInteger count = counters.get(key);
        if (count != null) {
          int result = count.decrementAndGet();
          if (result < 0)
            throw new IllegalStateException("key:" + key);
          if (Log.isEnabled(Log.TAG_IMAGE_LOADER)) {
            Log.v(Log.TAG_IMAGE_LOADER, "#%s: reference--: %d", key, result);
          }
          if (result == 0) {
            counters.remove(key);
            if (!hasInMemcache(key)) {
              if (Log.isEnabled(Log.TAG_IMAGE_LOADER)) {
                Log.v(Log.TAG_IMAGE_LOADER, "#%s: recycling bitmap in removeReference", key);
              }
              U.recycle(bitmap);
            }
            if (ALLOW_REFERENCES) {
              references.remove(key);
            }
          }
        }
      }
    } else if (file == null) {
      if (Log.isEnabled(Log.TAG_IMAGE_LOADER)) {
        Log.w(Log.TAG_IMAGE_LOADER, "#null: removeReference failed, bitmap: %s", getBitmapInfo(bitmap));
      }
    }
  }

  private static String getBitmapInfo (@Nullable Bitmap bitmap) {
    return bitmap != null ? (bitmap.isRecycled() ? "recycled" : bitmap.getWidth() + "x" + bitmap.getHeight()) : "null";
  }

  public void putBitmap (ImageFile file, Bitmap bitmap) {
    final String key = file.toString();
    memcache.put(key, bitmap);
    if (file.getRotation() != 0) {
      rotations.put(key, file.getRotation());
    }
    if (ALLOW_REFERENCES) {
      synchronized (counters) {
        references.put(key, new WeakReference<>(bitmap));
      }
    }
  }

  public Bitmap getBitmap (ImageFile file) {
    final String key = file.toString();
    final Bitmap cached = memcache.get(key);
    if (cached != null) {
      Integer rotation = rotations.get(key);
      if (rotation != null) {
        file.setRotation(rotation);
      }
    }
    if (ALLOW_REFERENCES && cached == null) {
      synchronized (counters) {
        final WeakReference<Bitmap> reference = references.get(key);
        final Bitmap referenced = reference != null ? reference.get() : null;
        if (referenced == null || referenced.isRecycled()) {
          references.remove(key);
        } else {
          return referenced;
        }
      }
    }
    return cached;
  }

  @Override
  @NonNull
  public String toString () {
    return "ImageCache { counters = " + counters.size() + ", memcache = " + memcache.size() + " }";
  }

  public void clear (boolean withMemcache) {
    synchronized (counters) {
      if (withMemcache) {
        counters.clear();
      }
    }
    if (withMemcache) {
      memcache.evictAll();
    }
  }

  public void clearForAccount (int accountId) {
    synchronized (counters) {
      final String prefix = "account" + accountId + "_";
      /*U.removeByPrefix(counters, prefix);
      U.removeByPrefix(rotations, prefix);
      if (ALLOW_REFERENCES) {
        U.removeByPrefix(references, prefix);
      }*/
      Set<String> snapshot = memcache.snapshot().keySet();
      for (String key : snapshot) {
        if (key.startsWith(prefix)) {
          memcache.remove(key);
        }
      }
    }
  }
}
