/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data.article;

import android.content.Context;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.filegen.PhotoGenerationInfo;
import org.thunderdog.challegram.loader.ImageReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/** Imports a picker URI into durable, account-scoped storage before adding it to a draft. */
public final class ArticleMediaFiles {
  private ArticleMediaFiles () { }
  public enum Kind { PHOTO, VIDEO, ANIMATION, AUDIO, DOCUMENT, VOICE }

  public static TdApi.InputPageBlock importFile (Context context, int accountId, long userId, Uri uri, Kind kind, long maxBytes) throws IOException {
    String name = "attachment";
    try (Cursor cursor = context.getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
      if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) name = cursor.getString(0);
    } catch (RuntimeException ignored) { }
    name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
    if (name.isEmpty() || name.equals(".") || name.equals("..")) name = "attachment";
    if (name.length() > 160) name = name.substring(name.length() - 160);
    File directory = new File(context.getFilesDir(), "article-media/" + accountId + "_" + userId + "/" + UUID.randomUUID());
    if (!directory.mkdirs()) throw new IOException("Cannot create article attachment directory");
    File file = new File(directory, name);
    boolean complete = false;
    try {
      try (InputStream in = context.getContentResolver().openInputStream(uri); FileOutputStream out = new FileOutputStream(file)) {
        if (in == null) throw new IOException("Attachment is unavailable");
        byte[] buffer = new byte[64 * 1024]; long size = 0; int read;
        while ((read = in.read(buffer)) != -1) {
          size += read;
          if (size > maxBytes || Thread.currentThread().isInterrupted()) throw new IOException("Attachment is too large or import was cancelled");
          out.write(buffer, 0, read);
        }
        if (size == 0) throw new IOException("Empty attachment");
        out.getFD().sync();
      }
      TdApi.InputPageBlock block = block(file, kind);
      complete = true;
      return block;
    } finally {
      if (!complete) { file.delete(); directory.delete(); }
    }
  }

  private static TdApi.InputPageBlock block (File file, Kind kind) throws IOException {
    TdApi.InputFile input = new TdApi.InputFileLocal(file.getAbsolutePath());
    TdApi.PageBlockCaption caption = new TdApi.PageBlockCaption(new TdApi.RichTextPlain(""), new TdApi.RichTextPlain(""));
    if (kind == Kind.DOCUMENT) return new TdApi.InputPageBlockDocument(new TdApi.InputDocument(input, null, true), caption);
    int width = 0, height = 0, duration = 0;
    String title = "", performer = "";
    BitmapFactory.Options image = new BitmapFactory.Options(); image.inJustDecodeBounds = true;
    if (kind == Kind.PHOTO || kind == Kind.ANIMATION) {
      BitmapFactory.decodeFile(file.getAbsolutePath(), image);
      width = image.outWidth; height = image.outHeight;
    }
    if (kind != Kind.PHOTO && (kind != Kind.ANIMATION || width <= 0)) {
      MediaMetadataRetriever metadata = new MediaMetadataRetriever();
      try {
        metadata.setDataSource(file.getAbsolutePath());
        duration = (int) ((number(metadata, MediaMetadataRetriever.METADATA_KEY_DURATION) + 999) / 1000);
        width = (int) number(metadata, MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        height = (int) number(metadata, MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        if (android.os.Build.VERSION.SDK_INT >= 17) {
          long rotation = number(metadata, MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
          if (rotation == 90 || rotation == 270) { int old = width; width = height; height = old; }
        }
        title = string(metadata, MediaMetadataRetriever.METADATA_KEY_TITLE);
        performer = string(metadata, MediaMetadataRetriever.METADATA_KEY_ARTIST);
      } catch (RuntimeException e) { throw new IOException("Unsupported media", e); }
      finally { metadata.release(); }
    }
    switch (kind) {
      case PHOTO:
        if (width <= 0 || height <= 0) throw new IOException("Unsupported photo");
        int orientation = U.getExifOrientation(file.getAbsolutePath());
        int sample = ImageReader.calculateInSampleSize(image, 1280, 1280);
        width /= sample; height /= sample;
        if (U.isExifRotated(orientation)) { int old = width; width = height; height = old; }
        input = PhotoGenerationInfo.newFile(file.getAbsolutePath(), U.getRotationForExifOrientation(orientation));
        return new TdApi.InputPageBlockPhoto(new TdApi.InputPhoto(input, null, null, new int[0], width, height), caption, false);
      case VIDEO:
        if (width <= 0 || height <= 0) throw new IOException("Unsupported video");
        return new TdApi.InputPageBlockVideo(new TdApi.InputVideo(input, null, null, 0, new int[0], duration, width, height, true), caption, false);
      case ANIMATION:
        if (width <= 0 || height <= 0) throw new IOException("Unsupported animation");
        return new TdApi.InputPageBlockAnimation(new TdApi.InputAnimation(input, null, new int[0], duration, width, height), caption, false);
      case AUDIO: return new TdApi.InputPageBlockAudio(new TdApi.InputAudio(input, null, duration, title, performer), caption);
      case VOICE: return new TdApi.InputPageBlockVoiceNote(new TdApi.InputVoiceNote(input, duration, new byte[0]), caption);
      default: throw new IOException("Unknown media type");
    }
  }
  private static String string (MediaMetadataRetriever metadata, int key) { String value = metadata.extractMetadata(key); return value == null ? "" : value; }
  private static long number (MediaMetadataRetriever metadata, int key) { try { return Long.parseLong(string(metadata, key)); } catch (NumberFormatException e) { return 0; } }
}
