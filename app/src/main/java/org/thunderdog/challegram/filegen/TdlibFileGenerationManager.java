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
 * File created on 08/12/2016
 */
package org.thunderdog.challegram.filegen;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Message;
import android.util.Xml;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Device;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.BaseThread;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageCache;
import org.thunderdog.challegram.loader.ImageFilteredFile;
import org.thunderdog.challegram.loader.ImageReader;
import org.thunderdog.challegram.mediaview.paint.PaintState;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColors;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.theme.ThemeProperties;
import org.thunderdog.challegram.theme.ThemeProperty;
import org.thunderdog.challegram.theme.ThemeSet;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.unit.ByteUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import okio.Sink;
import okio.Source;

public final class TdlibFileGenerationManager {
  private OkHttpClient getClient () {
    if (_client == null) {
      synchronized (this) {
        if (_client == null) {
          _client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .build();
        }
      }
    }
    return _client;
  }

  ThreadPoolExecutor getContentExecutor () {
    if (_contentExecutor == null) {
      synchronized (this) {
        if (_contentExecutor == null) {
          _contentExecutor = new ThreadPoolExecutor(1, 4, 1, TimeUnit.MINUTES, new LinkedBlockingQueue<>());
        }
      }
    }
    return _contentExecutor;
  }

  private static class FileGenQueue extends BaseThread {
    private final TdlibFileGenerationManager context;

    public FileGenQueue (TdlibFileGenerationManager context) {
      super("FileGenQueue");
      this.context = context;
    }

    @Override
    protected void process (Message msg) {
      context.process(msg);
    }
  }

  private static final int TASK_GENERATE_PHOTO = 0;
  private static final int TASK_GENERATE_THUMB = 1;
  private static final int TASK_GENERATE_AVATAR = 3;
  private static final int TASK_DOWNLOAD_HTTP = 5;
  private static final int TASK_COPY_CONTENT_STREAM = 6;
  private static final int TASK_COPY_FILE = 7;
  private static final int TASK_EXPORT_LANGUAGE = 8;
  private static final int TASK_EXPORT_THEME = 9;
  private static final int TASK_GENERATE_ANIMATED_STICKER_PREVIEW = 10;

  private final Tdlib tdlib;

  private BaseThread _queue, _thumbQueue;
  private VideoGen _videoGen;
  private final HashMap<Long, GenerationInfo> pendingTasks = new HashMap<>();

  private OkHttpClient _client;

  public TdlibFileGenerationManager (final Tdlib tdlib) {
    this.tdlib = tdlib;
  }

  private BaseThread queue () {
    if (_queue == null) {
      synchronized (this) {
        if (_queue == null) {
          _queue = new FileGenQueue(this);
        }
      }
    }
    return _queue;
  }

  private BaseThread thumbQueue () {
    if (_thumbQueue == null) {
      synchronized (this) {
        if (_thumbQueue == null) {
          _thumbQueue = new FileGenQueue(this);
        }
      }
    }
    return _thumbQueue;
  }

  private VideoGen videoGen () {
    if (_videoGen == null) {
      synchronized (this) {
        if (_videoGen == null) {
          _videoGen = new VideoGen(tdlib);
        }
      }
    }
    return _videoGen;
  }

  private void process (Message msg) {
    switch (msg.what) {
      case TASK_GENERATE_PHOTO: {
        PhotoGenerationInfo info = (PhotoGenerationInfo) msg.obj;
        try {
          generatePhoto(info);
        } catch (Throwable t) {
          Log.e("Cannot generate photo", t);
          failGeneration(info, ERROR_UNKNOWN, "Unknown error, see logs for details");
        }
        break;
      }
      case TASK_GENERATE_AVATAR: {
        SimpleGenerationInfo info = (SimpleGenerationInfo) msg.obj;
        try {
          generateAvatar(info);
        } catch (Throwable t) {
          Log.e("Cannot generate avatar", t);
          failGeneration(info, ERROR_UNKNOWN, "Unknown error, see logs for details");
        }
        break;
      }
      case TASK_GENERATE_THUMB: {
        Object[] obj = (Object[]) msg.obj;
        generateThumb((ThumbGenerationInfo) obj[0], (String) obj[1], msg.arg1);
        obj[0] = null;
        obj[1] = null;
        break;
      }
      case TASK_DOWNLOAD_HTTP: {
        Object[] obj = (Object[]) msg.obj;
        downloadHttpFile(BitwiseUtils.mergeLong(msg.arg1, msg.arg2), (String) obj[0], (String) obj[1]);
        obj[0] = null;
        obj[1] = null;
        break;
      }
      case TASK_COPY_CONTENT_STREAM: {
        Object[] obj = (Object[]) msg.obj;
        copyContentStream((String) obj[0], (String) obj[1], BitwiseUtils.mergeLong(msg.arg1, msg.arg2), (String) obj[2]);
        obj[0] = null;
        obj[1] = null;
        break;
      }
      case TASK_COPY_FILE: {
        Object[] obj = (Object[]) msg.obj;
        copyFile((String) obj[0], (String) obj[1], BitwiseUtils.mergeLong(msg.arg1, msg.arg2), (String) obj[2]);
        obj[0] = null;
        obj[1] = null;
        break;
      }
      case TASK_EXPORT_LANGUAGE: {
        Object[] obj = (Object[]) msg.obj;
        exportLanguage(BitwiseUtils.mergeLong(msg.arg1, msg.arg2), (String) obj[0], (TdApi.LanguagePackString[]) obj[1], (String) obj[2]);
        obj[0] = null;
        obj[1] = null;
        obj[2] = null;
        break;
      }
      case TASK_EXPORT_THEME: {
        Object[] obj = (Object[]) msg.obj;
        exportTheme(BitwiseUtils.mergeLong(msg.arg1, msg.arg2), (Integer) obj[0], (Integer) obj[1], (String) obj[2], (String) obj[3]);
        obj[0] = null;
        obj[1] = null;
        obj[2] = null;
        break;
      }
      case TASK_GENERATE_ANIMATED_STICKER_PREVIEW: {
        Object[] obj = (Object[]) msg.obj;
        generateAnimatedStickerThumb((String) obj[0], (String) obj[1], BitwiseUtils.mergeLong(msg.arg1, msg.arg2), (String) obj[2]);
        obj[0] = null;
        obj[1] = null;
        break;
      }
    }
  }

  public VideoGen.Entry getVideoProgress (String path) {
    return videoGen().getProgressEntry(path);
  }

  // Tasks from user

  public void saveFilteredBitmap (final ImageFilteredFile file, final Bitmap filteredBitmap) {
    if (file == null) {
      return;
    }
    ImageCache.instance().addReference(file, filteredBitmap);
    ImageReader.instance().post(() -> {
      final String path = file.getFilePath();
      final File targetFile = new File(path);

      boolean success = false;

      if (targetFile.exists() && !targetFile.delete()) {
        Log.e("Cannot delete target file, bad things will happen");
      } else {
        try (FileOutputStream stream = new FileOutputStream(targetFile)) {
          success = filteredBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        } catch (Throwable t) {
          Log.e("Cannot open filtered target file stream");
        }

        if (!success && targetFile.exists() && !targetFile.delete()) {
          Log.e("Cannot delete failed target file, bad things may happen");
        }
      }

      if (!success) {
        Log.w("Couldn't compress image for any reasons");
      }

      ImageCache.instance().removeReference(file, filteredBitmap);
    });
  }

  public void removeFilteredBitmap (final String path) {
    if (path != null) {
      ImageReader.instance().post(() -> {
        final File targetFile = new File(path);

        if (targetFile.exists() && !targetFile.delete()) {
          Log.e("Cannot delete target file, but we don't need it now. Bad things may happen in future");
        }
      });
    }
  }

  // Updates from TDLib

  public void updateFileGenerationStart (TdApi.UpdateFileGenerationStart update) {
    synchronized (this) {
      startGeneration(update.generationId, update.originalPath, update.destinationPath, update.conversion);
    }
  }

  public void updateFileGenerationStop (TdApi.UpdateFileGenerationStop update) {
    synchronized (this) {
      cancelGeneration(update.generationId);
    }
  }

  // Entry points

  private final HashMap<Long, Call> downloadingHttpFiles = new HashMap<>();

  private void cancelGeneration (long generationId) {
    Call call = downloadingHttpFiles.remove(generationId);
    if (call != null) {
      call.cancel();
    }
    GenerationInfo info = pendingTasks.get(generationId);
    if (info != null) {
      pendingTasks.remove(generationId);
      info.cancel();
    }
  }

  private void startGeneration (long generationId, String originalPath, String destinationPath, String conversion) {
    if (conversion.equals("#url#")) {
      downloadHttpFile(generationId, originalPath, destinationPath);
      return;
    }
    if (conversion.startsWith("language_export_")) {
      exportLanguage(generationId, conversion.substring("language_export_".length()), destinationPath);
      return;
    }
    if (conversion.startsWith("round")) {
      BaseActivity context = UI.getUiContext();
      if (context != null) {
        context.getRecordAudioVideoController().setRoundGeneration(conversion, generationId, destinationPath);
      } else {
        failGeneration(originalPath, conversion, generationId, ERROR_BAD_INPUT, "Video is no longer recording");
      }
      return;
    }
    if (conversion.startsWith(GenerationInfo.TYPE_STICKER_PREVIEW)) {
      generateAnimatedStickerThumb(originalPath, conversion, generationId, destinationPath);
      return;
    }
    if (conversion.startsWith("copy")) {
      copyFile(originalPath, conversion, generationId, destinationPath);
      return;
    }
    if (conversion.startsWith("theme_export_")) {
      String arg = conversion.substring(conversion.indexOf('_', "theme_export_".length()) + 1);
      String[] args = arg.split(",");
      final int themeId = StringUtils.parseInt(args[0]);
      final int flags = args.length > 1 ? StringUtils.parseInt(args[1]) : 0;
      if (ThemeManager.isCustomTheme(themeId) || BitwiseUtils.getFlag(flags, Theme.EXPORT_FLAG_INCLUDE_DEFAULT_VALUES) || ThemeSet.getProperty(themeId, ThemeProperty.PARENT_THEME) != 0) {
        String author = args.length > 2 ? args[2] : null;
        exportTheme(generationId, themeId, flags, author, destinationPath);
      } else {
        failGeneration(originalPath, conversion, generationId, ERROR_BAD_INPUT, "Invalid theme, id: " + themeId + ", flags: " + flags);
      }
      return;
    }
    if (conversion.startsWith("content://")) {
      copyContentStream(originalPath, conversion, generationId, destinationPath);
      return;
    }
    String thumbPrefix =
      conversion.startsWith(GenerationInfo.TYPE_PHOTO_THUMB) ? GenerationInfo.TYPE_PHOTO_THUMB :
      conversion.startsWith(GenerationInfo.TYPE_VIDEO_THUMB) ? GenerationInfo.TYPE_VIDEO_THUMB :
      conversion.startsWith(GenerationInfo.TYPE_MUSIC_THUMB) ? GenerationInfo.TYPE_MUSIC_THUMB :
      null;
    if (thumbPrefix != null) {
      String dataConversion = conversion.substring(thumbPrefix.length());
      int numericCount = 0;
      for (int i = 0; i < dataConversion.length(); i++) {
        if (Character.isDigit(dataConversion.charAt(i))) {
          numericCount++;
        } else {
          break;
        }
      }
      int resolution = SMALL_THUMB_RESOLUTION;
      if (numericCount > 0) {
        resolution = StringUtils.parseInt(dataConversion.substring(0, numericCount));
        dataConversion = dataConversion.substring(numericCount);
      }
      int type =
        thumbPrefix.equals(GenerationInfo.TYPE_PHOTO_THUMB) ? ThumbGenerationInfo.TYPE_PHOTO :
        thumbPrefix.equals(GenerationInfo.TYPE_VIDEO_THUMB) ? ThumbGenerationInfo.TYPE_VIDEO :
        /*thumbPrefix.equals(GenerationInfo.TYPE_MUSIC_THUMB) ?*/ ThumbGenerationInfo.TYPE_MUSIC;
      ThumbGenerationInfo generationInfo = new ThumbGenerationInfo(generationId, originalPath, destinationPath, type, dataConversion);
      if (!dataConversion.isEmpty() && type == ThumbGenerationInfo.TYPE_VIDEO) {
        VideoGenerationInfo.parseConversion(generationInfo, dataConversion);
      }
      generateThumb(generationInfo, originalPath, resolution);
      return;
    }

    GenerationInfo task;
    BaseThread targetQueue;
    int targetMessage;

    if (conversion.startsWith(GenerationInfo.TYPE_AVATAR)) {
      task = new SimpleGenerationInfo(generationId, originalPath, destinationPath, conversion);
      targetQueue = queue();
      targetMessage = TASK_GENERATE_AVATAR;
    } else if (conversion.startsWith(GenerationInfo.TYPE_PHOTO)) {
      try {
        task = new PhotoGenerationInfo(generationId, originalPath, destinationPath, conversion);
      } catch (Throwable t) {
        Log.e("Unable to process input conversion: %d %s", t, StringUtils.isEmpty(conversion) ? 0 : conversion.length(), conversion);
        failGeneration(originalPath, conversion, generationId, ERROR_APP_FAILURE, "Incorrect conversion input: " + conversion);
        return;
      }
      int resolutionLimit = ((PhotoGenerationInfo) task).getResolutionLimit();
      targetQueue = resolutionLimit > 0 && resolutionLimit <= BIG_THUMB_RESOLUTION ? thumbQueue() : queue();
      targetMessage = TASK_GENERATE_PHOTO;
    } else if (conversion.startsWith(GenerationInfo.TYPE_VIDEO)) {
      boolean isKnownConversion = tdlib.settings().isKnownConversion(originalPath, conversion);
      if (isKnownConversion) {
        failGeneration(originalPath, conversion, generationId, ERROR_APP_FAILURE, "Failing video generation because previous attempt did not finish.");
        return;
      } else {
        tdlib.settings().rememberConversion(originalPath, conversion);
      }
      task = new VideoGenerationInfo(generationId, originalPath, destinationPath, conversion);
      targetQueue = videoGen().getQueue();
      targetMessage = VideoGen.MESSAGE_START_CONVERSION;
    } else {
      Log.w("Unknown conversion task: %s", conversion);
      failGeneration(originalPath, conversion, generationId, ERROR_APP_FAILURE, "Unknown conversion: " + conversion);
      return;
    }

    pendingTasks.put(generationId, task);
    targetQueue.sendMessage(Message.obtain(targetQueue.getHandler(), targetMessage, task), 0);
  }

  private static final int ERROR_UNKNOWN = -1;
  private static final int ERROR_APP_FAILURE = -2;
  private static final int ERROR_BAD_INPUT = -3;

  private void finishGenerationImpl (String originalPath, String conversion, long generationId, @Nullable TdApi.Error error) {
    synchronized (this) {
      pendingTasks.remove(generationId);
      tdlib.client().send(new TdApi.FinishFileGeneration(generationId, error), tdlib.silentHandler());
      tdlib.settings().forgetConversion(originalPath, conversion);
    }
  }

  @AnyThread
  public void failGeneration (GenerationInfo info, int errorCode, String message) {
    finishGenerationImpl(info.getOriginalPath(), info.conversion, info.generationId, new TdApi.Error(errorCode, message));
  }

  @AnyThread
  public void failGeneration (String originalPath, String conversion, long generationId, int errorCode, String message) {
    finishGenerationImpl(originalPath, conversion, generationId, new TdApi.Error(errorCode, message));
  }

  @AnyThread
  public void finishGeneration (GenerationInfo info) {
    finishGenerationImpl(info.getOriginalPath(), info.conversion, info.getGenerationId(), null);
  }

  @AnyThread
  public void finishGeneration (String originalPath, String conversion, long generationId) {
    finishGenerationImpl(originalPath, conversion, generationId, null);
  }

  // HTTP

  @AnyThread
  private void finishHttpGeneration (long generationId, TdApi.Error error) {
    synchronized (this) {
      downloadingHttpFiles.remove(generationId);
      tdlib.client().send(new TdApi.FinishFileGeneration(generationId, error), tdlib.silentHandler());
    }
  }

  private static @Nullable String getRetryUrl (final String url) {
    try {
      Uri uri = Uri.parse(url);
      if ("maps.googleapis.com".equals(uri.getHost()) && StringUtils.isEmpty(uri.getQueryParameter("key"))) {
        return url; //  + "&key=" + Config.GOOGLE_API_KEY;
      }
    } catch (Throwable ignored) { }
    return null;
  }

  private boolean retryHttpFile (final long conversionId, final String url, final String destinationPath, final boolean isPreprocessing) {
    final String newUrl = getRetryUrl(url);
    return redirectHttpFile(conversionId, url, newUrl, destinationPath, isPreprocessing);
  }

  private boolean redirectHttpFile (final long conversionId, final String oldUrl, final String newUrl, final String destinationPath, final boolean isPreprocessing) {
    if (StringUtils.isEmpty(newUrl)) {
      return false;
    }
    Request request = new Request.Builder().url(newUrl).build();
    Call newCall = getClient().newCall(request);
    synchronized (this) {
      Call existingCall = downloadingHttpFiles.remove(conversionId);
      if (existingCall != null) {
        downloadingHttpFiles.put(conversionId, newCall);
        Log.i(Log.TAG_IMAGE_LOADER, "Redirecting HTTP request...\nurl:%s\nnewUrl:%s", oldUrl, newUrl);
        newCall.enqueue(newCallback(conversionId, newUrl, destinationPath, isPreprocessing));
        return true;
      }
    }
    return false;
  }

  private void downloadHttpFile (final long conversionId, final String url, final String destinationPath) {
    Request request = new Request.Builder()
      .url(url)
      .build();

    Call call = getClient().newCall(request);
    synchronized (this) {
      downloadingHttpFiles.put(conversionId, call);
    }
    call.enqueue(newCallback(conversionId, url, destinationPath, false));
  }

  private Callback newCallback (final long conversionId, final String url, final String destinationPath,final boolean isPreprocessing) {
    return new Callback() {
      @Override
      public void onFailure (@NonNull Call call, @NonNull IOException e) {
        if (call.isCanceled()) {
          return;
        }
        if (retryHttpFile(conversionId, url, destinationPath, isPreprocessing)) {
          return;
        }
        Log.i(Log.TAG_IMAGE_LOADER, "Failed to load http file: %s", e, url);
        finishHttpGeneration(conversionId, new TdApi.Error(-1, "HTTP Error: " + Log.toString(e)));
      }

      @Override
      public void onResponse (@NonNull Call call, @NonNull Response response) throws IOException {
        if (!response.isSuccessful()) {
          if (!retryHttpFile(conversionId, url, destinationPath, isPreprocessing)) {
            finishHttpGeneration(conversionId, new TdApi.Error(-2, "HTTP status code: " + response.code()));
          }
          return;
        }

        ResponseBody responseBody = response.body();
        if (responseBody == null) {
          if (!retryHttpFile(conversionId, url, destinationPath, isPreprocessing)) {
            finishHttpGeneration(conversionId, new TdApi.Error(-2, "HTTP responseBody is null"));
          }
          return;
        }

        if (isPreprocessing) {
          boolean redirected = false;
          /*try {
            JSONObject object = new JSONObject(responseBody.string());
            JSONObject venue = object.getJSONObject("response").getJSONObject("venue");
            String iconUrl = MediaLocationData.findIconUrl(venue);
            redirected = redirectHttpFile(conversionId, url, iconUrl, destinationPath, false);
          } catch (JSONException e) {
            Log.e(e);
          }*/
          if (!redirected) {
            finishHttpGeneration(conversionId, new TdApi.Error(-3, "Preprocessing failed"));
          }
          return;
        }

        File file = new File(destinationPath);

        long totalDone = 0;
        boolean error = false;

        try (BufferedSource in = responseBody.source();
             Sink sink = Okio.sink(file);
             BufferedSink out = Okio.buffer(sink);) {
          long contentLength = responseBody.contentLength();

          long done;
          while ((done = in.read(out.getBuffer(), 2048)) != -1) {
            totalDone += done;
            tdlib.client().send(new TdApi.SetFileGenerationProgress(conversionId, (int) contentLength, (int) totalDone), tdlib.silentHandler());
          }
        } catch (IOException e) {
          error = true;
          Log.e(Log.TAG_IMAGE_LOADER, "Couldn't load HTTP file, url:%s", e, url);
        }

        if (error && retryHttpFile(conversionId, url, destinationPath, isPreprocessing)) {
          return;
        }

        finishHttpGeneration(conversionId, error ? new TdApi.Error() : null);
      }
    };
  }

  // Content input

  private ThreadPoolExecutor _contentExecutor;

  boolean copy (final long conversionId, final String sourcePath, final Source in, final String destinationPath, final int expectedSize, @Nullable AtomicBoolean isCancelled) {
    boolean ok = true;
    long totalDone = 0;
    long lastDoneNotify = 0;
    boolean canceled = false;

    File file = new File(destinationPath);

    try (Sink sink = Okio.sink(file)) {
      try (BufferedSink out = Okio.buffer(sink)) {
        long done;
        while ((done = in.read(out.getBuffer(), 20480)) != -1) {
          if (canceled = (isCancelled != null && isCancelled.get())) {
            break;
          }
          totalDone += done;
          if ((done == 0 && totalDone > 0) || totalDone - lastDoneNotify >= ByteUnit.KIB.toBytes(5)) {
            out.flush();
            lastDoneNotify = totalDone;
            tdlib.client().send(new TdApi.SetFileGenerationProgress(conversionId, expectedSize, (int) totalDone), tdlib.silentHandler());
          }
        }
        out.flush();
      }
    } catch (IOException e) {
      ok = false;
      Log.e("Couldn't copy file for upload:%s", e, sourcePath);
    }

    return ok && !canceled;
  }

  private void generateAnimatedStickerThumb (final String fromPath, final String conversion, final long generationId, final String destinationPath) {
    if (Thread.currentThread() != queue()) {
      queue().sendMessage(Message.obtain(queue().getHandler(), TASK_GENERATE_ANIMATED_STICKER_PREVIEW, BitwiseUtils.splitLongToFirstInt(generationId), BitwiseUtils.splitLongToSecondInt(generationId), new Object[] {fromPath, conversion, destinationPath}), 0);
      return;
    }

    getContentExecutor().execute(() -> {
      boolean success = false;
      try {
        Bitmap result = ImageReader.decodeLottieFrame(fromPath, 512);
        if (result != null) {
          try (FileOutputStream out = new FileOutputStream(destinationPath)) {
            success = result.compress(U.compressFormat(true), COMPRESSION_LEVEL, out);
          } catch (Throwable t) {
            Log.e("Can't compress image", t);
          }
          result.recycle();
        }
      } catch (Throwable t) {
        Log.e("Cannot copy file, fromPath: %s", t, fromPath);
      }
      if (success) {
        finishGeneration(fromPath, conversion, generationId);
      } else {
        failGeneration(fromPath, conversion, generationId, -1, "Unable to copy file contents");
      }
    });
  }

  private void copyFile (final String fromPath, final String conversion, final long generationId, final String destinationPath) {
    if (Thread.currentThread() != queue()) {
      queue().sendMessage(Message.obtain(queue().getHandler(), TASK_COPY_FILE, BitwiseUtils.splitLongToFirstInt(generationId), BitwiseUtils.splitLongToSecondInt(generationId), new Object[] {fromPath, conversion, destinationPath}), 0);
      return;
    }

    getContentExecutor().execute(() -> {
      boolean success = false;
      try {
        File file = new File(fromPath);
        try (Source in = Okio.source(file)) {
          success = copy(generationId, fromPath, in, destinationPath, (int) file.length(), null);
        }
      } catch (Throwable t) {
        Log.e("Cannot copy file, fromPath: %s", t, fromPath);
      }
      if (success) {
        finishGeneration(fromPath, conversion, generationId);
      } else {
        failGeneration(fromPath, conversion, generationId, -1, "Unable to copy file contents");
      }
    });
  }

  private void exportLanguage (final long conversionId, final String conversion, final String destinationPath) {
    final String languageCode = conversion.substring(conversion.indexOf('_') + 1);
    tdlib.client().send(new TdApi.GetLanguagePackStrings(languageCode, null), result -> {
      switch (result.getConstructor()) {
        case TdApi.LanguagePackStrings.CONSTRUCTOR:
          exportLanguage(conversionId, languageCode, ((TdApi.LanguagePackStrings) result).strings, destinationPath);
          break;
        case TdApi.Error.CONSTRUCTOR: {
          tdlib.client().send(new TdApi.FinishFileGeneration(conversionId, (TdApi.Error) result), tdlib.silentHandler());
          break;
        }
      }
    });
  }

  private void exportLanguage (final long conversionId, final String languageCode, final TdApi.LanguagePackString[] strings, final String destinationPath) {
    if (Thread.currentThread() != queue()) {
      queue().sendMessage(Message.obtain(queue().getHandler(), TASK_EXPORT_LANGUAGE, BitwiseUtils.splitLongToFirstInt(conversionId), BitwiseUtils.splitLongToSecondInt(conversionId), new Object[] {languageCode, strings, destinationPath}), 0);
      return;
    }

    getContentExecutor().execute(() -> {
      Arrays.sort(strings, (a, b) -> {
        int s1 = Lang.Pack.getStringSection(a);
        int s2 = Lang.Pack.getStringSection(b);
        if (s1 != s2)
          return Integer.compare(s1, s2);
        return a.key.compareTo(b.key);
      });
      boolean success = false;
      try (FileOutputStream os = new FileOutputStream(new File(destinationPath))) {
        XmlSerializer serializer = Xml.newSerializer();
        serializer.setOutput(os, "UTF-8");
        serializer.startDocument("utf-8", true);
        serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
        serializer.startTag(null, "resources");

        Lang.PluralizationRules rules = Lang.getPluralizationRules(Lang.makeLanguageCode(languageCode));
        for (TdApi.LanguagePackString string : strings) {
          switch (string.value.getConstructor()) {
            case TdApi.LanguagePackStringValueOrdinary.CONSTRUCTOR: {
              TdApi.LanguagePackStringValueOrdinary value = (TdApi.LanguagePackStringValueOrdinary) string.value;
              serializer.startTag(null, "string");
              serializer.attribute(null, "name", string.key);
              serializer.text(Strings.wrap(value.value, false));
              serializer.endTag(null, "string");
              break;
            }
            case TdApi.LanguagePackStringValuePluralized.CONSTRUCTOR: {
              TdApi.LanguagePackStringValuePluralized pluralized = (TdApi.LanguagePackStringValuePluralized) string.value;
              for (Lang.PluralizationForm form : rules.forms) {
                String suffix, value;
                switch (form.form) {
                  case Lang.PluralForm.ZERO:
                    suffix = "_zero";
                    value = pluralized.zeroValue;
                    break;
                  case Lang.PluralForm.ONE:
                    suffix = "_one";
                    value = pluralized.oneValue;
                    break;
                  case Lang.PluralForm.TWO:
                    suffix = "_two";
                    value = pluralized.twoValue;
                    break;
                  case Lang.PluralForm.FEW:
                    suffix = "_few";
                    value = pluralized.fewValue;
                    break;
                  case Lang.PluralForm.MANY:
                    suffix = "_many";
                    value = pluralized.manyValue;
                    break;
                  case Lang.PluralForm.OTHER:
                    suffix = "_other";
                    value = pluralized.otherValue;
                    break;
                  default:
                    throw new IllegalArgumentException(pluralized.toString());
                }
                serializer.startTag(null, "string");
                serializer.attribute(null, "name", string.key + suffix);
                serializer.text(Strings.wrap(value, false));
                serializer.endTag(null, "string");
              }
              break;
            }
            case TdApi.LanguagePackStringValueDeleted.CONSTRUCTOR:
              break;
          }
        }

        serializer.endTag(null, "resources");
        serializer.endDocument();
        serializer.flush();

        success = true;
      } catch (Throwable t) {
        Log.e("Cannot export language, code: %s", t, languageCode);
      }
      tdlib.client().send(new TdApi.FinishFileGeneration(conversionId, success ? null : new TdApi.Error()), tdlib.silentHandler());
    });
  }

  private static void appendMap (StringBuilder out, Map<?, List<String>> map) {
    List<Map.Entry<?, List<String>>> list = new ArrayList<>(map.entrySet());
    for (Map.Entry<?, List<String>> entry : list) {
      Collections.sort(entry.getValue(), String::compareToIgnoreCase);
    }
    Collections.sort(list, (a, b) -> a.getValue().get(0).compareToIgnoreCase(b.getValue().get(0)));

    for (Map.Entry<?, List<String>> entry : list) {
      boolean first = true;
      List<String> keys = entry.getValue();
      for (String key : keys) {
        if (first) {
          first = false;
        } else {
          out.append(", ");
        }
        out.append(key);
      }
      out.append(": ");
      Object entryKey = entry.getKey();
      out.append(entryKey instanceof Integer ? Strings.getHexColor((Integer) entryKey, true) : entryKey instanceof Float ? U.formatFloat((float) entryKey, true) : (String) entryKey);
      out.append("\n");
    }
  }

  private void exportTheme (final long conversionId, final int themeId, final int flags, final String author, final String destinationPath) {
    if (Thread.currentThread() != queue()) {
      queue().sendMessage(Message.obtain(queue().getHandler(), TASK_EXPORT_THEME, BitwiseUtils.splitLongToFirstInt(conversionId), BitwiseUtils.splitLongToSecondInt(conversionId), new Object[] {themeId, flags, author, destinationPath}), 0);
      return;
    }
    getContentExecutor().execute(() -> {
      boolean success = false;
      try (FileWriter os = new FileWriter(new File(destinationPath))) {

        Settings.ThemeExportInfo info = Settings.instance().exportTheme(themeId, (flags & Theme.EXPORT_FLAG_INCLUDE_DEFAULT_VALUES) != 0);
        StringBuilder b = new StringBuilder();
        if ((flags & Theme.EXPORT_FLAG_JAVA) != 0) {
          final Map<String, Integer> colors = ThemeColors.getMap();
          final Map<String, Integer> properties = ThemeProperties.getMap();
          b.append("package org.thunderdog.challegram.theme.builtin;\n" +
            "\n" +
            "import org.thunderdog.challegram.R;\n" +
            "import org.thunderdog.challegram.theme.ThemeColorIdTinted;\n" +
            "import org.thunderdog.challegram.theme.ThemeId;\n" +
            "\n" +
            "import androidx.annotation.ColorInt;\n\n");
          b.append("public final class Theme").append(info.name).append(" extends ThemeBase {\n");
          b.append("  public Theme").append(info.name).append(" () {\n");
          b.append("    super(ThemeId.").append(info.name.toUpperCase()).append(");\n");
          b.append("  }\n");
          ThemeDelegate base = ThemeSet.getBuiltinTheme(ThemeId.BLUE);
          if (!info.properties.isEmpty()) {
            b.append("\n  @Override\n");
            b.append("  public float getProperty (@ThemeProperty int propertyId) {\n");
            b.append("    switch (propertyId) {\n");
            for (Map.Entry<Float, List<String>> entry : info.properties.entrySet()) {
              float value = entry.getKey();
              boolean found = false;
              for (String name : entry.getValue()) {
                int propertyId = properties.get(name);
                if (base.getProperty(propertyId) != value) {
                  b.append("      case R.id.theme_property_").append(name).append(":\n");
                  found = true;
                }
              }
              if (found) {
                b.append("        return ").append(U.formatFloat(value, false)).append("f;\n");
              }
            }
            b.append("    }\n");
            b.append("    return super.getProperty(propertyId);\n");
            b.append("  }\n");
          }
          if (!info.colors.isEmpty()) {
            b.append("\n  @ColorInt\n");
            b.append("  @Override\n");
            b.append("  public int getColor (@ThemeColorIdTinted int targetColorId) {\n");
            b.append("    switch (targetColorId) {\n");
            for (Map.Entry<Integer, List<String>> entry : info.colors.entrySet()) {
              boolean found = false;
              int value = entry.getKey();
              for (String name : entry.getValue()) {
                int colorId = colors.get(name);
                if (base.getColor(colorId) != value) {
                  b.append("      case R.id.theme_color_").append(name).append(":\n");
                  found = true;
                }
              }
              if (found) {
                b.append("        return 0x").append(Integer.toHexString(entry.getKey())).append(";\n");
              }
            }
            b.append("    }\n");
            b.append("    return super.getColor(targetColorId);\n");
            b.append("  }\n");
          }
          b.append("}");
        } else {
          b.append("!\n");
          if (!ThemeManager.isCustomTheme(themeId)) {
            b.append("id: ").append(themeId).append("\n");
          }
          b.append("name: ").append(Strings.wrap(info.name, true)).append("\n");
          b.append("time: ").append(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())).append("\n");
          if (!StringUtils.isEmpty(author)) {
            b.append("author: ").append(Strings.wrap(author, true)).append("\n");
          }
          if (!StringUtils.isEmpty(info.wallpaper)) {
            b.append("wallpaper: ").append(Strings.wrap(info.wallpaper, true)).append("\n");
          }
          if (!info.properties.isEmpty()) {
            b.append("@\n");
            appendMap(b, info.properties);
          }
          if (!info.colors.isEmpty()) {
            b.append("#\n");
            appendMap(b, info.colors);
          }
        }
        final String code = b.toString();

        os.append(code);
        os.flush();
        success = true;
      } catch (Throwable t) {
        Log.e("Cannot export theme, themeId: %d", t, themeId);
      }
      tdlib.client().send(new TdApi.FinishFileGeneration(conversionId, success ? null : new TdApi.Error()), tdlib.silentHandler());
    });
  }

  private void copyContentStream (final String originalPath, final String conversion, final long generationId, final String destinationPath) {
    if (Thread.currentThread() != queue()) {
      queue().sendMessage(Message.obtain(queue().getHandler(), TASK_COPY_CONTENT_STREAM, BitwiseUtils.splitLongToFirstInt(generationId), BitwiseUtils.splitLongToSecondInt(generationId), new Object[] {originalPath, conversion, destinationPath}), 0);
      return;
    }

    int i = conversion.lastIndexOf(',');
    final String sourceUri = conversion.substring(0, i);
    String arg = conversion.substring(i + 1);
    int j = arg.indexOf('_');
    final int expectedSize = StringUtils.parseInt(j != -1 ? arg.substring(0, j) : arg);

    getContentExecutor().execute(() -> {
      boolean success = false;
      try (InputStream stream = U.openInputStream(sourceUri)) {
        try (Source in = Okio.source(stream)) {
          success = copy(generationId, sourceUri, in, destinationPath, expectedSize, null);
        }
      } catch (Throwable t) {
        Log.e("Cannot copy content, sourceUri: %s", t, sourceUri);
      }
      if (success) {
        finishGeneration(originalPath, conversion, generationId);
      } else {
        failGeneration(originalPath, conversion, generationId, -1, "Unable to copy content stream");
      }
    });
  }

  // Compress bitmap

  private void compress (GenerationInfo info, Bitmap bitmap, int quality, boolean transparent) {
    boolean failed = true;
    try (FileOutputStream out = new FileOutputStream(info.getDestinationPath())) {
      failed = !bitmap.compress(U.compressFormat(transparent), quality, out);
    } catch (Throwable t) {
      Log.e("Cannot compress image", t);
    }
    if (failed) {
      failGeneration(info, ERROR_APP_FAILURE, "Image has failed to compress");
    } else {
      finishGeneration(info);
    }
  }

  // Photo

  private void generatePhoto (PhotoGenerationInfo info) throws Throwable {
    final String originalPath = info.getOriginalPath();
    Uri uri = originalPath.startsWith("content://") ? Uri.parse(originalPath) : null;
    final boolean applyLessCompression = U.isScreenshotFolder(originalPath);
    boolean isTransparent = info.getAllowTransparency() || (!applyLessCompression && isTransparent(originalPath, uri));

    final int maxSize = info.getResolutionLimit() != 0 ? info.getResolutionLimit() : PhotoGenerationInfo.SIZE_LIMIT;
    final boolean saveToGallery = Settings.instance().needSaveEditedMediaToGallery() && info.isEdited();

    Bitmap bitmap = null;
    boolean needRotate = false;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && maxSize <= BIG_THUMB_RESOLUTION && info.getCropState() == null && info.getPaintState() == null) {
      android.graphics.ImageDecoder.Source source;
      if (uri != null) {
        source = android.graphics.ImageDecoder.createSource(UI.getAppContext().getContentResolver(), uri);
      } else {
        source = android.graphics.ImageDecoder.createSource(new File(originalPath));
      }
      bitmap = android.graphics.ImageDecoder.decodeBitmap(source, new android.graphics.ImageDecoder.OnHeaderDecodedListener() {
        @Override
        public void onHeaderDecoded (@NonNull android.graphics.ImageDecoder decoder, @NonNull android.graphics.ImageDecoder.ImageInfo imageInfo, @NonNull android.graphics.ImageDecoder.Source source) {
          int width = imageInfo.getSize().getWidth();
          int height = imageInfo.getSize().getHeight();
          decoder.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE);
          if (!info.isFiltered()) {
            decoder.setTargetSampleSize(ImageReader.calculateInSampleSize(width, height, maxSize, maxSize));
            decoder.setUnpremultipliedRequired(true);
          }
          PaintState paintState = info.getPaintState();
          boolean removeTransparency = isTransparent && !info.getAllowTransparency();
          if (paintState != null || removeTransparency) {
            decoder.setPostProcessor(new android.graphics.PostProcessor() {
              @Override
              public int onPostProcess (@NonNull Canvas c) {
                if (removeTransparency) {
                  c.drawColor(0xffffffff, PorterDuff.Mode.DST_OVER);
                }
                if (paintState != null) {
                  paintState.draw(c, 0, 0, width, height);
                }
                return PixelFormat.UNKNOWN;
              }
            });
          }
        }
      });
    }

    if (bitmap == null) {
      needRotate = true;
      BitmapFactory.Options opts = new BitmapFactory.Options();
      opts.inSampleSize = 1;

      if (!info.isFiltered()) {
        try (InputStream is = U.openInputStream(originalPath)) {
          opts.inJustDecodeBounds = true;
          BitmapFactory.decodeStream(is, null, opts);
          opts.inSampleSize = ImageReader.ceilInSampleSize(opts.outWidth, opts.outHeight, maxSize, maxSize);
        }
      }

      opts.inJustDecodeBounds = false;
      opts.inPreferredConfig = Bitmap.Config.ARGB_8888;

      try (InputStream is = U.openInputStream(originalPath)) {
        bitmap = info.readImage(is, opts, originalPath);
      }
    }

    if (bitmap == null) {
      failGeneration(info, ERROR_APP_FAILURE, "Original image has failed to read");
      return;
    }

    if (info.needSpecialProcessing(needRotate)) {
      Bitmap processedBitmap = info.process(bitmap, needRotate);

      if (processedBitmap == null) {
        bitmap.recycle();
        failGeneration(info, ERROR_APP_FAILURE, "processedBitmap == null");
        return;
      }

      if (processedBitmap != bitmap) {
        bitmap.recycle();
        bitmap = processedBitmap;
      }
    }

    Bitmap originalBitmap = bitmap;

    if (Math.max(bitmap.getWidth(), bitmap.getHeight()) > maxSize) {
      bitmap = ImageReader.resizeBitmap(bitmap, maxSize, maxSize, false, true, !saveToGallery);
    }

    if (isTransparent && !info.getAllowTransparency()) {
      try {
        if (bitmap.isMutable()) {
          Canvas c = new Canvas(bitmap);
          c.drawColor(0xffffffff, PorterDuff.Mode.DST_OVER);
          U.recycle(c);
        } else {
          Bitmap nonTransparent = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());
          Canvas c = new Canvas(nonTransparent);
          c.drawBitmap(bitmap, 0, 0, null);
          c.drawColor(0xffffffff, PorterDuff.Mode.DST_OVER);
          bitmap = nonTransparent;
          U.recycle(c);
          if (!saveToGallery) {
            U.recycle(originalBitmap);
          }
        }
      } catch (Throwable ignored) { }
    }

    compress(info, bitmap, info.isFiltered() ? 100 : applyLessCompression ? COMPRESSION_LEVEL_LESS : COMPRESSION_LEVEL, isTransparent && info.getAllowTransparency());

    if (saveToGallery) {
      Background.instance().post(() -> U.savePhotoToGallery(originalBitmap, isTransparent), 750);
    }
  }

  private static final int COMPRESSION_LEVEL_LESS = 92;
  private static final int COMPRESSION_LEVEL = Device.IS_SAMSUNG ? 92 : 89;

  private void generateAvatar (SimpleGenerationInfo info) throws Throwable {
    final String originalPath = info.getOriginalPath();
    final int maxSize = 640;

    BitmapFactory.Options opts = new BitmapFactory.Options();
    opts.inSampleSize = 1;

    opts.inJustDecodeBounds = true;
    ImageReader.decodeFile(originalPath, opts);
    opts.inSampleSize = ImageReader.calculateInSampleSize(opts, maxSize, maxSize);

    opts.inJustDecodeBounds = false;
    opts.inPreferredConfig = Bitmap.Config.ARGB_8888;

    Bitmap bitmap = null;
    try (InputStream is = U.openInputStream(originalPath)) {
      bitmap = BitmapFactory.decodeStream(is, null, opts);
    } catch (Throwable t) {
      Log.e("Cannot compress photo", t);
    }

    if (bitmap == null) {
      failGeneration(info, ERROR_APP_FAILURE, "bitmap == null");
      return;
    }

    int outputOrientation = U.getExifOrientation(originalPath);
    bitmap = orientBitmap(bitmap, outputOrientation);

    if (bitmap.getWidth() != bitmap.getHeight()) {
      bitmap = cropSquare(bitmap);
    }

    if (Math.max(bitmap.getWidth(), bitmap.getHeight()) > maxSize) {
      bitmap = ImageReader.resizeBitmap(bitmap, maxSize, maxSize, false, true, true);
    }

    compress(info, bitmap, 89, false);
  }

  public static Bitmap cropSquare (Bitmap bitmap) {
    final int width = bitmap.getWidth();
    final int height = bitmap.getHeight();
    if (width == height)
      return bitmap;
    Bitmap cropped;
    if (width >= height) {
      cropped = Bitmap.createBitmap(bitmap, width / 2 - height / 2, 0, height, height);
    } else {
      cropped = Bitmap.createBitmap(bitmap, 0, height / 2 - width / 2, width, width);
    }
    bitmap.recycle();
    return cropped;
  }

  // Common thumb logic

  private void generateThumb (ThumbGenerationInfo info, String originalPath, int resolution) {
    if (Thread.currentThread() != queue()) {
      queue().sendMessage(Message.obtain(queue().getHandler(), TASK_GENERATE_THUMB, resolution, 0, new Object[] {info, originalPath}), 0);
      return;
    }
    try {
      switch (info.getType()) {
        case ThumbGenerationInfo.TYPE_PHOTO:
          generatePhotoThumb(info, originalPath, resolution);
          break;
        case ThumbGenerationInfo.TYPE_VIDEO:
          generateVideoThumb(info, originalPath, resolution);
          break;
        case ThumbGenerationInfo.TYPE_MUSIC:
          generateAudioThumb(info, originalPath, resolution);
          break;
        default:
          throw new IllegalArgumentException("type == " + info.getType());
      }
    } catch (Throwable t) {
      Log.e("Cannot generate thumb type:%d", t, info.getType());
      failGeneration(info, ERROR_APP_FAILURE, "Failed to generate thumb, see logs for details");
    }
  }

  // Video thumb

  private void generateVideoThumb (ThumbGenerationInfo info, String originalPath, int resolution) throws Throwable {
    MediaMetadataRetriever retriever = null;
    Bitmap bitmap = null;
    try {
      retriever = U.openRetriever(originalPath);
      bitmap = retriever.getFrameAtTime(Math.max(0, info.getStartTime()), MediaMetadataRetriever.OPTION_CLOSEST);
      if (bitmap == null && info.getStartTime() <= 0) {
        bitmap = retriever.getFrameAtTime(-1);
      }
    } catch (RuntimeException ex) {
      // Assume this is a corrupt video file
    }
    U.closeRetriever(retriever);

    if (bitmap == null) {
      failGeneration(info, ERROR_APP_FAILURE, "Could not get frame");
      return;
    }

    if (Math.max(bitmap.getWidth(), bitmap.getHeight()) > resolution) {
      bitmap = ImageReader.resizeBitmap(bitmap, resolution, resolution, false, true, true);
    }

    int rotate = info.getRotate();
    if (rotate != 0) {
      bitmap = rotateBitmap(bitmap, rotate);
    }

    // bitmap = rotateBitmap(bitmap, outputRotation);

    compress(info, bitmap, 89, false);
  }

  private void generateAudioThumb (ThumbGenerationInfo info, String originalPath, int resolution) {
    MediaMetadataRetriever retriever = null;
    Bitmap bitmap = null;
    try {
      retriever = U.openRetriever(originalPath);
      byte[] bytes = retriever.getEmbeddedPicture();
      if (bytes != null && bytes.length > 0) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
        opts.inJustDecodeBounds = false;
        opts.inSampleSize = ImageReader.calculateInSampleSize(opts, resolution, resolution);
        bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
      }
    } catch (RuntimeException ex) {
      // Assume this is a corrupt video file
    }
    U.closeRetriever(retriever);

    if (bitmap == null) {
      failGeneration(info, ERROR_APP_FAILURE, "Could not get frame");
      return;
    }

    if (Math.max(bitmap.getWidth(), bitmap.getHeight()) > resolution) {
      bitmap = ImageReader.resizeBitmap(bitmap, resolution, resolution, false, true, true);
    }

    int rotate = info.getRotate();
    if (rotate != 0) {
      bitmap = rotateBitmap(bitmap, rotate);
    }

    // bitmap = rotateBitmap(bitmap, outputRotation);

    compress(info, bitmap, 89, false);
  }

  public static Bitmap rotateBitmap (Bitmap bitmap, int outputRotation) {
    if (MathUtils.modulo(outputRotation, 360) != 0 && U.isValidBitmap(bitmap)) {
      try {
        Matrix matrix = new Matrix();
        matrix.postRotate(outputRotation);
        Bitmap newBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        bitmap.recycle();
        bitmap = newBitmap;
      } catch (Throwable ignored) { }
    }
    return bitmap;
  }

  public static Bitmap orientBitmap (final Bitmap bitmap, final int outputOrientation) {
    final Matrix matrix = U.exifMatrix(bitmap.getWidth(), bitmap.getHeight(), outputOrientation);
    if (matrix == null)
      return bitmap;
    try {
      Bitmap newBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
      bitmap.recycle();
      return newBitmap;
    } catch (Throwable t) {
      Log.e(Log.TAG_IMAGE_LOADER, "Cannot orient bitmap: %d, size: %dx%d", outputOrientation, bitmap.getWidth(), bitmap.getHeight());
    }
    return bitmap;
  }

  private boolean isTransparent (String path, @Nullable Uri uri) {
    try {
      String ext = U.getExtension(path);
      if ("png".equals(ext) || "webp".equals(ext)) {
        return true;
      }
      if (uri != null) {
        String param;
        param = uri.getQueryParameter("mimeType");
        if (param != null && (param.equals("image/webp") || param.equals("image/png")))
          return true;
        param = uri.getQueryParameter("fileName");
        if (param != null)
          param = U.getExtension(param);
        if (param != null && (param.equals("webp") || param.equals("png")))
          return true;
      }
    } catch (Throwable t) {
      Log.e(t);
    }
    return false;
  }

  private void generatePhotoThumb (ThumbGenerationInfo info, String originalPath, int resolution) throws Throwable {
    Uri uri = originalPath.startsWith("content://") ? Uri.parse(originalPath) : null;
    boolean transparent = isTransparent(originalPath, uri);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P /*&& Config.MODERN_IMAGE_DECODER_ENABLED*/) {
      try {
        // int rotation = ImageReader.getRotation(originalPath);
        android.graphics.ImageDecoder.Source source;
        if (uri != null) {
          source = android.graphics.ImageDecoder.createSource(UI.getAppContext().getContentResolver(), uri);
        } else {
          source = android.graphics.ImageDecoder.createSource(new File(originalPath));
        }
        Bitmap bitmap = android.graphics.ImageDecoder.decodeBitmap(source, new android.graphics.ImageDecoder.OnHeaderDecodedListener() {
          @Override
          public void onHeaderDecoded (@NonNull android.graphics.ImageDecoder decoder, @NonNull android.graphics.ImageDecoder.ImageInfo imageInfo, @NonNull android.graphics.ImageDecoder.Source source) {
            int width = imageInfo.getSize().getWidth();
            int height = imageInfo.getSize().getHeight();
            float ratio = Math.min((float) SMALL_THUMB_RESOLUTION / width, (float) SMALL_THUMB_RESOLUTION / height);
            if (ratio < 1f) {
              width *= ratio;
              height *= ratio;
              decoder.setTargetSize(width, height);
            }
          }
        });
        /*if (rotation != 0) {
          bitmap = rotateBitmap(bitmap, rotation);
        }*/
        compress(info, bitmap, 89, transparent);
      } catch (Throwable t) {
        Log.e("Cannot compress photo", t);
        failGeneration(info, ERROR_APP_FAILURE, "Cannot compress photo, see logs for details");
      }
      return;
    }
    BitmapFactory.Options opts = new BitmapFactory.Options();
    opts.inSampleSize = 1;
    opts.inJustDecodeBounds = true;
    if (uri != null) {
      try (InputStream is = UI.getContext().getContentResolver().openInputStream(uri)) {
        BitmapFactory.decodeStream(is, null, opts);
      }
    } else {
      ImageReader.decodeFile(originalPath, opts);
    }
    opts.inSampleSize = ImageReader.calculateInSampleSize(opts, SMALL_THUMB_RESOLUTION, SMALL_THUMB_RESOLUTION);
    opts.inJustDecodeBounds = false;

    opts.inPreferredConfig = Bitmap.Config.ARGB_8888;

    Bitmap bitmap = null;

    try (InputStream is = U.openInputStream(originalPath)) {
      bitmap = BitmapFactory.decodeStream(is, null, opts);
    } catch (Throwable t) {
      Log.e("Cannot compress photo", t);
    }

    if (bitmap == null) {
      failGeneration(info, ERROR_APP_FAILURE, "bitmap == null");
      return;
    }
    if (Math.max(bitmap.getWidth(), bitmap.getHeight()) > SMALL_THUMB_RESOLUTION) {
      bitmap = ImageReader.resizeBitmap(bitmap, SMALL_THUMB_RESOLUTION, SMALL_THUMB_RESOLUTION, false, true, true);
    }

    int outputOrientation = U.getExifOrientation(originalPath);

    bitmap = orientBitmap(bitmap, outputOrientation);

    compress(info, bitmap, 89, transparent);
  }

  // Thumbnail 2.0

  public static final int SMALL_THUMB_RESOLUTION = 90;
  public static final int BIG_THUMB_RESOLUTION = 320;

  private static TdApi.InputThumbnail newThumbnail (TdApi.InputFile inputFile, int maxResolution, int width, int height) {
    if (inputFile == null)
      return null;
    if (Math.max(width, height) > maxResolution) {
      if (width == 0) {
        height = maxResolution;
      } else if (height == 0) {
        width = maxResolution;
      } else {
        float factor = Math.min((float) maxResolution / (float) width, (float) maxResolution / (float) height);
        width *= factor;
        height *= factor;
      }
    }
    return new TdApi.InputThumbnail(inputFile, width, height);
  }

  public <T extends TdApi.InputMessageContent> T createThumbnail (@NonNull T content, boolean isSecret) {
    return createThumbnail(content, isSecret, null);
  }

  private interface ConversionConverter {
    String onConvert (String originalPath, String originalConversion);
  }

  private static TdApi.InputFileGenerated newThumbnailFile (TdApi.InputFile inputFile, @Nullable TdApi.File helperFile, ConversionConverter converter) {
    String newConversion = null;
    String originalPath = null;

    try {
      switch (inputFile.getConstructor()) {
        case TdApi.InputFileGenerated.CONSTRUCTOR: {
          TdApi.InputFileGenerated generated = (TdApi.InputFileGenerated) inputFile;
          originalPath = generated.originalPath;
          if (!StringUtils.isEmpty(generated.conversion)) {
            newConversion = converter.onConvert(originalPath, generated.conversion);
          }
          break;
        }
        case TdApi.InputFileLocal.CONSTRUCTOR: {
          originalPath = ((TdApi.InputFileLocal) inputFile).path;
          newConversion = converter.onConvert(originalPath, null);
          break;
        }
        case TdApi.InputFileId.CONSTRUCTOR: {
          if (helperFile != null && ((TdApi.InputFileId) inputFile).id == helperFile.id && TD.isFileLoaded(helperFile)) {
            originalPath = helperFile.local.path;
            newConversion = converter.onConvert(originalPath, null);
          }
          break;
        }
        case TdApi.InputFileRemote.CONSTRUCTOR: {
          if (helperFile != null && helperFile.remote != null && StringUtils.equalsOrBothEmpty(((TdApi.InputFileRemote) inputFile).id, helperFile.remote.id) && TD.isFileLoaded(helperFile)) {
            originalPath = helperFile.local.path;
            newConversion = converter.onConvert(originalPath, null);
          }
          break;
        }
      }
    } catch (Throwable t) {
      Log.w("unable to create thumbnail conversion: %s", inputFile);
    }
    if (!StringUtils.isEmpty(originalPath) && !StringUtils.isEmpty(newConversion)) {
      return new TdApi.InputFileGenerated(originalPath, newConversion, 0);
    }
    return null;
  }

  public <T extends TdApi.InputMessageContent> T createThumbnail (@NonNull final T content, final boolean isSecretChat, @Nullable final TdApi.File file) {
    final boolean isSecret = isSecretChat || TD.isSecret(content);
    final int resolution = content.getConstructor() == TdApi.InputMessageSticker.CONSTRUCTOR || isSecret ? SMALL_THUMB_RESOLUTION : BIG_THUMB_RESOLUTION;

    switch (content.getConstructor()) {
      case TdApi.InputMessagePhoto.CONSTRUCTOR: {
        TdApi.InputMessagePhoto photo = (TdApi.InputMessagePhoto) content;
        if (photo.thumbnail == null && isSecret) {
          TdApi.InputFile thumbnail;
          if (Math.max(photo.width, photo.height) <= resolution) {
            thumbnail = photo.photo;
          } else {
            thumbnail = newThumbnailFile(photo.photo, file, (originalPath, originalConversion) -> {
              if (originalConversion == null)
                return ThumbGenerationInfo.makeConversion(ThumbGenerationInfo.TYPE_PHOTO, null, resolution);
              if (originalConversion.startsWith(GenerationInfo.TYPE_PHOTO))
                return PhotoGenerationInfo.editResolutionLimit(originalConversion, resolution);
              return null;
            });
          }
          if (thumbnail != null) {
            photo.thumbnail = newThumbnail(thumbnail, resolution, photo.width, photo.height);
          }
        }
        break;
      }
      case TdApi.InputMessageVideo.CONSTRUCTOR:
      case TdApi.InputMessageAnimation.CONSTRUCTOR: {
        TdApi.InputFile sourceFile;
        TdApi.InputThumbnail currentThumbnail;
        int width, height;
        switch (content.getConstructor()) {
          case TdApi.InputMessageAnimation.CONSTRUCTOR: {
            TdApi.InputMessageAnimation animation = (TdApi.InputMessageAnimation) content;
            sourceFile = animation.animation;
            currentThumbnail = animation.thumbnail;
            width = animation.width;
            height = animation.height;
            break;
          }
          case TdApi.InputMessageVideo.CONSTRUCTOR: {
            TdApi.InputMessageVideo video = (TdApi.InputMessageVideo) content;
            sourceFile = video.video;
            currentThumbnail = video.thumbnail;
            width = video.width;
            height = video.height;
            break;
          }
          default:
            throw new UnsupportedOperationException();
        }
        if (currentThumbnail == null) {
          TdApi.InputFile thumbnail = newThumbnailFile(sourceFile, file, (originalPath, originalConversion) -> {
            if (originalConversion == null || originalConversion.startsWith(GenerationInfo.TYPE_VIDEO)) {
              return ThumbGenerationInfo.makeConversion(ThumbGenerationInfo.TYPE_VIDEO, originalConversion, resolution);
            }
            return null;
          });
          if (thumbnail != null) {
            TdApi.InputThumbnail newThumbnail = newThumbnail(thumbnail, resolution, width, height);
            switch (content.getConstructor()) {
              case TdApi.InputMessageAnimation.CONSTRUCTOR: {
                ((TdApi.InputMessageAnimation) content).thumbnail = newThumbnail;
                break;
              }
              case TdApi.InputMessageVideo.CONSTRUCTOR: {
                ((TdApi.InputMessageVideo) content).thumbnail = newThumbnail;
                break;
              }
            }
          }
        }
        break;
      }
      case TdApi.InputMessageDocument.CONSTRUCTOR: {
        TdApi.InputMessageDocument document = (TdApi.InputMessageDocument) content;
        if (document.thumbnail == null) {
          TdApi.InputFileGenerated thumbnail = newThumbnailFile(document.document, file, (originalPath, originalConversion) -> {
            String mimeType = U.resolveMimeType(originalPath);
            if (!StringUtils.isEmpty(mimeType)) {
              if (mimeType.startsWith("video/") && (originalConversion == null || originalConversion.startsWith(GenerationInfo.TYPE_VIDEO))) {
                return ThumbGenerationInfo.makeConversion(ThumbGenerationInfo.TYPE_VIDEO, originalConversion, resolution);
              }
              if (mimeType.startsWith("image/")) {
                if (originalConversion == null)
                  return PhotoGenerationInfo.makeConversion(0, 0, true, resolution);
                else if (originalConversion.startsWith(GenerationInfo.TYPE_PHOTO))
                  return PhotoGenerationInfo.editResolutionLimit(originalConversion, resolution);
              }
              if (mimeType.startsWith("audio/") && originalConversion == null) {
                return ThumbGenerationInfo.makeConversion(ThumbGenerationInfo.TYPE_MUSIC, null, resolution);
              }
              if (mimeType.startsWith("application/pdf")) {
                // TODO PDF
              }
            }
            return null;
          });
          if (thumbnail != null) {
            int width = resolution, height = resolution;
            String mimeType = U.resolveMimeType(thumbnail.originalPath);
            if (!StringUtils.isEmpty(mimeType)) {
              if (mimeType.startsWith("video/")) {
                try {
                  U.MediaMetadata metadata = U.getMediaMetadata(thumbnail.originalPath);
                  if (metadata != null && metadata.width > 0 && metadata.height > 0) {
                    width = metadata.width;
                    height = metadata.height;
                  }
                } catch (Throwable t) {
                  Log.i("Couldn't read video metadata", t);
                }
              } else if (mimeType.startsWith("image/")) {
                BitmapFactory.Options opts = ImageReader.getImageSize(thumbnail.originalPath);
                if (opts.outWidth > 0 && opts.outHeight > 0) {
                  width = opts.outWidth;
                  height = opts.outHeight;
                }
              } else if (mimeType.startsWith("audio/")) {
                MediaMetadataRetriever retriever = null;
                try {
                  retriever = U.openRetriever(thumbnail.originalPath);
                  byte[] picture = retriever.getEmbeddedPicture();
                  if (picture != null && picture.length > 0) {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(picture, 0, picture.length, opts);
                    if (opts.outWidth > 0 && opts.outHeight > 0) {
                      width = opts.outWidth;
                      height = opts.outHeight;
                    }
                  }
                } catch (Throwable t) {
                  Log.w("Unable to extract thumbnail size", t);
                } finally {
                  U.closeRetriever(retriever);
                }
              } else if (mimeType.equals("application/pdf")) {
                // TODO pdf
              }
            }
            document.thumbnail = newThumbnail(thumbnail, resolution, width, height);
          }
        }
        break;
      }
      case TdApi.InputMessageAudio.CONSTRUCTOR: {
        TdApi.InputMessageAudio audio = (TdApi.InputMessageAudio) content;
        if (audio.albumCoverThumbnail == null) {
          TdApi.InputFileGenerated thumbnail = newThumbnailFile(audio.audio, file, (originalPath, originalConversion) -> ThumbGenerationInfo.makeConversion(ThumbGenerationInfo.TYPE_MUSIC, null, resolution));
          if (thumbnail != null) {
            int width = resolution, height = resolution;
            MediaMetadataRetriever retriever = null;
            try {
              retriever = U.openRetriever(thumbnail.originalPath);
              byte[] picture = retriever.getEmbeddedPicture();
              if (picture != null && picture.length > 0) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(picture, 0, picture.length, opts);
                if (opts.outWidth > 0 && opts.outHeight > 0) {
                  width = opts.outWidth;
                  height = opts.outHeight;
                }
              }
            } catch (Throwable t) {
              Log.w("Unable to extract thumbnail size", t);
            } finally {
              U.closeRetriever(retriever);
            }
            audio.albumCoverThumbnail = newThumbnail(thumbnail, resolution, width, height);
          }
        }
        break;
      }
      case TdApi.InputMessageVideoNote.CONSTRUCTOR: {
        TdApi.InputMessageVideoNote videoNote = (TdApi.InputMessageVideoNote) content;
        if (videoNote.thumbnail == null) {
          TdApi.InputFile thumbnail = newThumbnailFile(videoNote.videoNote, file, (originalPath, originalConversion) -> ThumbGenerationInfo.makeConversion(ThumbGenerationInfo.TYPE_VIDEO, originalConversion, resolution));
          if (thumbnail != null) {
            videoNote.thumbnail = newThumbnail(thumbnail, resolution, videoNote.length, videoNote.length);
          }
        }
        break;
      }
      case TdApi.InputMessageSticker.CONSTRUCTOR: {
        TdApi.InputMessageSticker sticker = (TdApi.InputMessageSticker) content;
        if (sticker.thumbnail == null) {
          TdApi.InputFile thumbnail = newThumbnailFile(sticker.sticker, file, (originalPath, originalConversion) -> originalConversion != null ? PhotoGenerationInfo.editResolutionLimit(originalConversion, resolution) : PhotoGenerationInfo.makeConversion(0, 0, true, resolution));
          if (thumbnail != null) {
            sticker.thumbnail = newThumbnail(thumbnail, resolution, sticker.width, sticker.height);
          }
        }
        break;
      }
    }
    return content;
  }
}
