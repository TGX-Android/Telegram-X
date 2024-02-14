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
 * File created on 19/10/2016
 */
package org.thunderdog.challegram.component.attach;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Looper;
import android.os.StatFs;
import android.provider.MediaStore;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.core.Media;
import org.thunderdog.challegram.data.InlineResult;
import org.thunderdog.challegram.data.InlineResultCommon;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.telegram.RightId;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.util.Permissions;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.StorageUtils;
import me.vkryl.core.DateUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.RunnableData;

public class MediaBottomFilesController extends MediaBottomBaseController<Void> implements View.OnClickListener, Menu, View.OnLongClickListener, Comparator<File>, TGPlayerController.PlayListBuilder {
  public MediaBottomFilesController (MediaLayout context) {
    super(context, R.string.File);
  }

  @Override
  public int getId () {
    return R.id.controller_media_files;
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_more;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_more) {
      header.addMoreButton(menu, this);
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_more) {
      showSystemPicker(false);
    }
  }

  @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
  private void showSystemPicker (boolean forceDownloads) {
    RunnableData<Set<Uri>> callback = uris -> {
      if (uris != null && !uris.isEmpty()) {
        List<String> files = new ArrayList<>(uris.size());
        for (Uri uri : uris) {
          String filePath = U.tryResolveFilePath(uri);
          if (!StringUtils.isEmpty(filePath) && U.canReadFile(filePath)) {
            files.add(filePath);
          } else {
            files.add(uri.toString());
          }
        }
        mediaLayout.pickDateOrProceed((sendOptions, disableMarkdown) ->
          mediaLayout.sendFilesMixed(mediaLayout.getTarget() != null ? mediaLayout.getTarget().getAttachButton() : null, files, null, sendOptions, false)
        );
      }
    };
    final Intent intent = new Intent(Intent.ACTION_GET_CONTENT)
      .addCategory(Intent.CATEGORY_OPENABLE)
      .setType("*/*")
      .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
      .putExtra("android.content.extra.SHOW_ADVANCED", true);
    context.putActivityResultHandler(Intents.ACTIVITY_RESULT_FILES, (requestCode, resultCode, data) -> {
      if (resultCode == Activity.RESULT_OK) {
        // Use a LinkedHashSet to maintain any ordering that may be
        // present in the ClipData
        LinkedHashSet<Uri> uris = new LinkedHashSet<>();
        Uri dataUri = data.getData();
        if (dataUri != null) {
          uris.add(dataUri);
        }
        ClipData clipData = data.getClipData();
        if (clipData != null) {
          for (int i = 0; i < clipData.getItemCount(); i++) {
            Uri uri = clipData.getItemAt(i).getUri();
            if (uri != null) {
              uris.add(uri);
            }
          }
        }
        if (!uris.isEmpty()) {
          callback.runWithData(uris);
        }
      }
    });
    try {
      context.startActivityForResult(intent, Intents.ACTIVITY_RESULT_FILES);
    } catch (ActivityNotFoundException e) {
      UI.showToast(R.string.NoFilePicker, Toast.LENGTH_SHORT);
      Log.i(e);
    }
  }

  private SettingsAdapter adapter;

  private static final String KEY_GALLERY = "gallery";
  private static final String KEY_BUCKET = "bucket";
  private static final String KEY_MUSIC = "music";
  private static final String KEY_DOWNLOADS = "downloads";
  private static final String KEY_FOLDER = "dir://";
  private static final String KEY_FILE = "file://";
  private static final String KEY_UPPER = "..";

  private int initialItemsCount;

  private void buildCells () {
    navigateToPath(null, null, null, false, null, null, null);
  }

  private void navigateToPath (final View view, final String currentPath, final String parentPath, boolean isUpper, final InlineResultCommon data, Runnable onDone, Runnable onError) {
    cancelCurrentLoadOperation();

    ArrayList<ListItem> items = new ArrayList<>();

    if (currentPath != null && !currentPath.isEmpty()) {
      if (!isUpper && mediaLayout.getTarget() != null) {
        boolean isMusic = KEY_MUSIC.equals(currentPath);
        boolean res = mediaLayout.getTarget().showRestriction(view, isMusic ? RightId.SEND_AUDIO : RightId.SEND_DOCS);
        if (res) {
          return;
        }
      }
      LoadOperation operation;
      RunnableData<Runnable> before = null;
      if (KEY_GALLERY.equals(currentPath)) {
        operation = buildGallery();
      } else if (KEY_MUSIC.equals(currentPath)) {
        operation = buildMusic();
      } else if (KEY_DOWNLOADS.equals(currentPath)) {
        operation = buildDownloads();
      } else if (KEY_BUCKET.equals(currentPath)) {
        operation = buildBucket(data);
      } else if (currentPath.startsWith(KEY_FOLDER)) {
        String path = currentPath.substring(KEY_FOLDER.length());
        operation = buildFolder(path, parentPath);
        String internalPath = UI.getAppContext().getFilesDir().getPath();
        File external = UI.getAppContext().getExternalFilesDir(null);
        String externalPath = external != null ? external.getPath() : null;
        if (!isUpper && (path.equals(internalPath) || path.equals(externalPath))) {
          before = after -> showOptions(Lang.getMarkdownString(this, R.string.ApplicationFolderWarning), new int[] {R.id.btn_done, R.id.btn_cancel}, new String[] {Lang.getString(R.string.ApplicationFolderWarningConfirm), Lang.getString(R.string.Cancel)}, new int[] {OptionColor.RED, OptionColor.NORMAL}, new int[] {R.drawable.baseline_warning_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
            if (id == R.id.btn_done) {
              after.run();
            }
            return true;
          });
        }
      } else {
        operation = null;
      }
      if (operation != null) {
        operation.setCallbacks(onDone, onError);
        this.currentLoadOperation = operation;
        if (before != null) {
          before.runWithData(() -> Background.instance().post(operation));
        } else {
          Background.instance().post(operation);
        }
      }
      return;
    }

    try {
      final File baseExternalDir = Environment.getExternalStorageDirectory();
      if (baseExternalDir != null) {
        final String environmentPath = baseExternalDir.getPath();
        final boolean isRemovable = Environment.isExternalStorageRemovable();
        StatFs fs = new StatFs(environmentPath);
        String text = Lang.getString(R.string.FreeXofY, Strings.buildSize(StorageUtils.freeMemorySize(fs)), Strings.buildSize(StorageUtils.totalMemorySize(fs)));
        InlineResultCommon internalStorage = new InlineResultCommon(context, tdlib, KEY_FOLDER + environmentPath, ColorId.fileAttach, isRemovable ? R.drawable.baseline_sd_storage_24 : R.drawable.baseline_storage_24, Lang.getString(isRemovable ? R.string.SdCard : R.string.InternalStorage), text);
        items.add(createItem(internalStorage, R.id.btn_internalStorage));
      }

      final ArrayList<String> externalStorageFiles = U.getExternalStorageDirectories(baseExternalDir != null ? baseExternalDir.getPath() : null, false);
      if (externalStorageFiles != null) {
        for (String dir : externalStorageFiles) {
          InlineResultCommon internalStorage = new InlineResultCommon(context, tdlib, KEY_FOLDER + dir, ColorId.fileAttach, R.drawable.baseline_storage_24, Lang.getString(R.string.Storage), dir);
          items.add(createItem(internalStorage, R.id.btn_internalStorage));
        }
      }

    } catch (Throwable t) {
      Log.e("Cannot add storage directory", t);
    }

    InlineResultCommon galleryItem = createItem(context, tdlib, KEY_GALLERY, R.drawable.baseline_image_24, Lang.getString(R.string.Gallery), Lang.getString(R.string.SendMediaHint));
    items.add(createItem(galleryItem, R.id.btn_galleryFiles));

    InlineResultCommon musicItem = createItem(context, tdlib, KEY_MUSIC, R.drawable.baseline_music_note_24, Lang.getString(R.string.Music), Lang.getString(R.string.SendMusicHint));
    items.add(createItem(musicItem, R.id.btn_musicFiles));

    boolean addedDownloads = false;
    boolean downloadsEmpty = false;
    if (context.permissions().canManageStorage()) {
      try {
        File file = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (file.exists() && file.isDirectory()) {
          File[] files = file.listFiles();
          if (files != null && !(downloadsEmpty = files.length == 0)) {
            InlineResultCommon common = createItem(context, tdlib, KEY_FOLDER + file.getPath(), R.drawable.baseline_file_download_24, Lang.getString(R.string.Downloads), Lang.plural(R.string.xFiles, files.length));
            items.add(createItem(common, file.isDirectory() ? R.id.btn_folder : R.id.btn_file));
            addedDownloads = true;
          }
        }
      } catch (Throwable t) {
        Log.e("Cannot add Downloads directory", t);
      }
    }
    if (!addedDownloads && !downloadsEmpty && context().permissions().canRequestDownloadsAccess()) {
      InlineResultCommon downloadsItem = createItem(context, tdlib, KEY_DOWNLOADS, R.drawable.baseline_file_download_24, Lang.getString(R.string.Downloads), Lang.getString(R.string.Files));
      items.add(createItem(downloadsItem, R.id.btn_downloads));
    }

    boolean hasRoot = false;
    try {
      final File rootDir = new File("/");
      File[] files = rootDir.listFiles();
      if (files != null && files.length > 0) {
        int filesCount = 0;
        int foldersCount = 0;
        for (File file : files) {
          if (file.isDirectory())
            foldersCount++;
          else
            filesCount++;
        }
        String text;
        if (filesCount != 0 && foldersCount != 0)
          text = Lang.getString(R.string.format_filesAndFolders, Lang.plural(R.string.xFolders, foldersCount), Lang.plural(R.string.xFiles, filesCount));
        else if (foldersCount != 0)
          text = Lang.plural(R.string.xFolders, foldersCount);
        else
          text = Lang.plural(R.string.xFiles, filesCount);
        InlineResultCommon rootDirectory = new InlineResultCommon(context, tdlib, KEY_FOLDER + rootDir.getPath(), ColorId.fileAttach, R.drawable.baseline_folder_24, Lang.getString(R.string.RootDirectory), text);
        items.add(createItem(rootDirectory, R.id.btn_folder));
        hasRoot = true;
      }
    } catch (Throwable t) {
      Log.i("Cannot add root directory", t);
    }

    initialItemsCount = items.size();

    if (!hasRoot) {
      addApplicationFolders(items);
    }

    /*if (Settings.instance().inDeveloperMode()) {
      String internalPath = TD.getTGDir(false);
      String externalPath = TD.getTGDir(true);

      InlineResultCommon internal = createItem(context, tdlib, KEY_FOLDER + internalPath, R.drawable.baseline_folder_24, "[TDLib] Internal", internalPath);
      items.add(createItem(internal, R.id.btn_folder));

      if (!internalPath.equals(externalPath)) {
        InlineResultCommon external = createItem(context, tdlib, KEY_FOLDER + externalPath, R.drawable.baseline_folder_24, "[TDLib] External", externalPath);
        items.add(createItem(external, R.id.btn_folder));
      }

      File externalFile = UI.getContext().getExternalFilesDir(null);
      if (externalFile != null && !Strings.compare(externalPath, externalFile.getPath())) {
        externalPath = externalFile.getPath();
        InlineResultCommon external = createItem(context, tdlib, KEY_FOLDER + externalPath, R.drawable.baseline_folder_24, "[Challegram] External", externalPath);
        items.add(createItem(external, R.id.btn_folder));
      }

      File internalFile = UI.getContext().getFilesDir();
      if (internalFile != null) {
        internalPath = internalFile.getPath();
        if (!externalPath.equals(internalPath)) {
          internal = createItem(context, tdlib, KEY_FOLDER + internalPath, R.drawable.baseline_folder_24, "[Challegram] Internal", internalPath);
          items.add(createItem(internal, R.id.btn_folder));
        }
      }
    }*/

    setFilesItems(null, items, false);
  }

  @Override
  public boolean supportsMediaGrouping () {
    return true;
  }

  private void setFilesItems (final LoadOperation context, final ArrayList<ListItem> items, final boolean extend) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      UI.post(() -> setFilesItems(context, items, extend));
      return;
    }

    if (context != null && (this.currentLoadOperation != context || context.isCancelled() || isDestroyed())) {
      return;
    }

    adapter.setItems(items, false);

    if (extend && !isExpanded()) {
      expandFully();
    }
  }

  public static class FileEntry {
    private final Uri uri;
    private final long _id;
    private final String displayName;
    private final long size;
    private final String data;
    private final String mimeType;
    private final long dateAdded, dateModified;

    public FileEntry (Uri uri, long _id, String displayName, long size, String data, String mimeType, long dateAdded, long dateModified) {
      this.uri = uri;
      this._id = _id;
      this.displayName = displayName;
      this.size = size;
      this.data = data;
      this.mimeType = mimeType;
      this.dateAdded = dateAdded;
      this.dateModified = dateModified;
    }

    public Uri getUri () {
      return uri;
    }

    public long getId () {
      return _id;
    }

    public String getDisplayName () {
      return displayName;
    }

    public long getSize () {
      return size;
    }

    public String getData () {
      return data;
    }

    public String getMimeType () {
      return mimeType;
    }

    public long getDateAdded () {
      return dateAdded;
    }

    public long getDateModified () {
      return dateModified;
    }
  }

  public static class MusicEntry {
    private final long _id;
    private final String artist, title, path;
    private final long duration;
    private final String mime;
    private final long albumId;

    public MusicEntry (long _id, String artist, String title, String path, long duration, String mime, long albumId) {
      this._id = _id;
      this.artist = artist;
      this.title = title;
      this.path = path;
      this.duration = duration;
      this.mime = mime;
      this.albumId = albumId;
    }

    public String getMimeType () {
      return mime;
    }

    public long getId () {
      return _id;
    }

    public String getArtist () {
      return artist;
    }

    public String getTitle () {
      return title;
    }

    public String getPath () {
      return path;
    }

    public long getDuration () {
      return duration;
    }

    public long getAlbumId () {
      return albumId;
    }

    public boolean probablyHasArtwork () {
      return getAlbumId() != 0;
    }

    public Uri getArtwork () {
      return ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), getAlbumId());
    }
  }

  private static abstract class LoadOperation implements Runnable {
    private final MediaBottomFilesController context;

    public static class Result {
      ArrayList<ListItem> items;
      boolean needExpand;

      public Result (ArrayList<ListItem> items, boolean needExpand) {
        this.items = items;
        this.needExpand = needExpand;
      }

      public boolean isEmpty () {
        return items == null || items.isEmpty();
      }
    }

    public LoadOperation (MediaBottomFilesController context) {
      this.context = context;
    }

    private volatile boolean isCancelled;

    abstract Result act ();

    public void cancel () {
      synchronized (this) {
        isCancelled = true;
      }
    }

    public boolean isCancelled () {
      synchronized (this) {
        return isCancelled;
      }
    }

    private Runnable onDone, onError;

    public void setCallbacks (Runnable onDone, Runnable onError) {
      this.onDone = onDone;
      this.onError = onError;
    }

    @Override
    public void run () {
      synchronized (this) {
        if (!isCancelled) {
          final Result result = act();
          final Runnable callback = result != null && !result.isEmpty() ? onDone : onError;
          if (callback != null) {
            UI.post(() -> {
              if (!context.isDestroyed() && context.currentLoadOperation == LoadOperation.this && !isCancelled()) {
                if (result != null && !result.isEmpty()) {
                  context.setFilesItems(LoadOperation.this, result.items, result.needExpand);
                }
                callback.run();
              }
            });
          }
        }
      }
    }
  }

  private LoadOperation buildGallery () {
    return new LoadOperation(this) {
      @Override
      public Result act () {
        Media.Gallery gallery = Media.instance().getGallery();

        if (gallery == null) {
          openAlert(this, R.string.AppName, R.string.AccessError);
          return null;
        }

        if (gallery.isEmpty()) {
          openAlert(this, R.string.AppName, R.string.NothingFound);
          return null;
        }

        ArrayList<ListItem> items = new ArrayList<>(gallery.getBucketCount() - 1 + gallery.getAllMediaBucket().size());
        InlineResult<?> home = createItem(context, tdlib, KEY_UPPER, R.drawable.baseline_image_24, "..", Lang.getString(R.string.AttachFolderHome));
        items.add(createItem(home, R.id.btn_folder_upper));

        ArrayList<Media.GalleryBucket> buckets = gallery.getBuckets();
        Media.GalleryBucket allBucket = gallery.getAllMediaBucket();

        boolean hasRecentImages = false;
        if (allBucket != null && allBucket.size() > 0) {
          boolean first = true;
          int recentCount = 0;
          for (ImageFile file : allBucket.getMedia()) {
            if (file instanceof ImageGalleryFile) {
              ImageGalleryFile galleryFile = (ImageGalleryFile) file;
              if (!DateUtils.isToday(galleryFile.getDateTaken() / 1000l, TimeUnit.SECONDS) && !DateUtils.isYesterday(galleryFile.getDateTaken() / 1000l, TimeUnit.SECONDS)) {
                break;
              }
              if (first) {
                items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Recent));
                first = false;
              }
              InlineResult<?> result = createItem(context, tdlib, (ImageGalleryFile) file);
              ListItem item = new ListItem(ListItem.TYPE_CUSTOM_INLINE, R.id.btn_file).setLongId(((ImageGalleryFile) file).getGalleryId()).setData(result);
              items.add(item);
              hasRecentImages = true;
              recentCount++;
              if (recentCount == 10) {
                break;
              }
            }
          }
        }

        if (hasRecentImages) {
          items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Folders));
        }

        for (Media.GalleryBucket bucket : buckets) {
          if (bucket != allBucket) {
            InlineResult<?> result = createItem(context, tdlib, bucket);
            ListItem item = new ListItem(ListItem.TYPE_CUSTOM_INLINE, R.id.btn_bucket).setLongId(bucket.getId()).setData(result);
            items.add(item);
          }
        }

        if (!items.isEmpty()) {
          return new Result(items, true);
        }

        return null;
      }
    };
  }

  private LoadOperation buildBucket (final InlineResultCommon data) {
    return new LoadOperation(this) {
      @Override
      public Result act () {
        Media.GalleryBucket bucket = (Media.GalleryBucket) data.getTag();
        if (bucket == null || bucket.size() == 0) {
          return null;
        }

        int size = bucket.size();
        ArrayList<ListItem> items = new ArrayList<>( size + 1);
        InlineResult<?> home = createItem(context, tdlib, KEY_UPPER, R.drawable.baseline_image_24, "..", Lang.getString(R.string.Gallery));
        items.add(createItem(home, R.id.btn_folder_upper));

        for (ImageFile file : bucket.getMedia()) {
          if (file instanceof ImageGalleryFile) {
            InlineResult<?> result = createItem(context, tdlib, (ImageGalleryFile) file);
            ListItem item = new ListItem(ListItem.TYPE_CUSTOM_INLINE, R.id.btn_file).setLongId(((ImageGalleryFile) file).getGalleryId()).setData(result);
            items.add(item);
          }
        }

        return new Result(items, true);
      }
    };
  }

  private LoadOperation buildDownloads () {
    return new LoadOperation(this) {
      @Override
      public Result act () {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
          return null;
        }
        final String[] projection = {
          MediaStore.Downloads._ID,
          MediaStore.Downloads.DISPLAY_NAME,
          MediaStore.Downloads.SIZE,
          MediaStore.Downloads.DATA,
          MediaStore.Downloads.RELATIVE_PATH,
          MediaStore.Downloads.MIME_TYPE,
          MediaStore.Downloads.DATE_ADDED,
          MediaStore.Downloads.DATE_MODIFIED,
          MediaStore.Downloads.IS_PENDING
        };
        try {
          try (Cursor c = UI.getAppContext().getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, MediaStore.Downloads.IS_PENDING + " != 1", null, MediaStore.Downloads.DATE_MODIFIED + " desc, " + MediaStore.Downloads.DATE_ADDED + " desc")) {
            if (c == null) {
              openAlert(this, R.string.AppName, R.string.AccessError);
              return null;
            }

            int count = c.getCount();
            ArrayList<FileEntry> entries = new ArrayList<>(count);
            while (c.moveToNext()) {
              long id = c.getLong(0);
              String displayName = c.getString(1);
              long size = c.getLong(2);
              String data = c.getString(3);
              String relativePath = c.getString(4);
              String mimeType = c.getString(5);
              long dateAdded = c.getLong(6);
              long dateModified = c.getLong(7);
              if (!StringUtils.isEmpty(data)) {
                entries.add(new FileEntry(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL, id), id, displayName, size, data, mimeType, dateAdded, dateModified));
              }
            }

            if (entries.isEmpty()) {
              openAlert(this, R.string.AppName, R.string.NoDownloadFilesFound);
              return null;
            }

            ArrayList<ListItem> items = new ArrayList<>(entries.size() + 1);
            InlineResult<?> result = createItem(context, tdlib, KEY_UPPER, R.drawable.baseline_folder_24, "..", Lang.getString(R.string.AttachFolderHome));
            items.add(createItem(result, R.id.btn_folder_upper));

            for (FileEntry entry : entries) {
              final String subtitle = Lang.getFileTimestamp(Math.max(entry.getDateModified(), entry.getDateAdded()), TimeUnit.SECONDS, entry.getSize());
              InlineResultCommon fileItem = new InlineResultCommon(context, tdlib, new File(entry.getData()), entry.getDisplayName(), subtitle, entry, false);
              items.add(createItem(fileItem, R.id.btn_file));
            }

            return new Result(items, true);

          }
        } catch (Throwable t) {
          Log.e("Cannot build downloads", t);
          openAlert(this, R.string.AppName, R.string.AccessError);
        }
        return null;
      }
    };
  }

  private LoadOperation buildMusic () {
    return new LoadOperation(this) {
      @Override
      public Result act () {
        final String[] projection = {
          MediaStore.Audio.Media._ID,
          MediaStore.Audio.Media.ARTIST,
          MediaStore.Audio.Media.TITLE,
          MediaStore.Audio.Media.DATA,
          MediaStore.Audio.Media.DURATION,
          MediaStore.Audio.Media.DATE_ADDED,
          MediaStore.Audio.Media.MIME_TYPE,
          MediaStore.Audio.Media.ALBUM_ID,
        };
        try {
          Cursor c = UI.getAppContext().getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, MediaStore.Audio.Media.IS_MUSIC + " != 0", null, MediaStore.Audio.Media.DATE_ADDED + " desc");
          if (c == null) {
            openAlert(this, R.string.AppName, R.string.AccessError);
            return null;
          }

          int count = c.getCount();

          ArrayList<MusicEntry> entries = new ArrayList<>(count);
          while (c.moveToNext()) {
            long id = c.getLong(0);
            String artist = c.getString(1);
            String title = c.getString(2);
            String data = c.getString(3);
            long duration = c.getInt(4);
            String mime = c.getString(6);
            long albumId = c.getLong(7);
            if (!StringUtils.isEmpty(data)) {
              entries.add(new MusicEntry(id, artist != null ? artist : "", title != null ? title : "", data, duration, mime, albumId));
            }
          }
          U.closeCursor(c);

          if (entries.isEmpty()) {
            openAlert(this, R.string.AppName, R.string.NoMusicFilesFound);
            return null;
          }

          Collections.sort(entries, (o1, o2) -> {
            int cmp = o1.artist.compareToIgnoreCase(o2.artist);
            return cmp != 0 ? cmp : o1.title.compareTo(o2.title);
          });

          ArrayList<ListItem> items = new ArrayList<>(entries.size() + 1);
          InlineResult<?> result = createItem(context, tdlib, KEY_UPPER, R.drawable.baseline_folder_24, "..", Lang.getString(R.string.AttachFolderHome));
          items.add(createItem(result, R.id.btn_folder_upper));

          for (MusicEntry entry : entries) {
            items.add(new ListItem(ListItem.TYPE_CUSTOM_INLINE, R.id.btn_file).setData(new InlineResultCommon(context, tdlib, entry, MediaBottomFilesController.this)));
          }

          return new Result(items, true);
        } catch (Throwable t) {
          Log.e("Cannot build music", t);
          openAlert(this, R.string.AppName, R.string.AccessError);
        }
        return null;
      }
    };
  }

  @Nullable
  @Override
  public TGPlayerController.PlayList buildPlayList (TdApi.Message fromMessage) {
    List<ListItem> items = adapter.getItems();
    ArrayList<TdApi.Message> out = null;
    int foundIndex = -1;
    for (ListItem item : items) {
      if (item.getId() != R.id.btn_file || item.getViewType() != ListItem.TYPE_CUSTOM_INLINE || !(item.getData() instanceof InlineResultCommon)) {
        continue;
      }
      InlineResultCommon inlineResult = (InlineResultCommon) item.getData();
      if (inlineResult.getType() == InlineResult.TYPE_AUDIO) {
        TdApi.Message fakeMessage = inlineResult.getPlayPauseMessage();
        if (fakeMessage == null) {
          continue;
        }
        if (foundIndex == -1 && TGPlayerController.compareTracks(fakeMessage, fromMessage)) {
          foundIndex = out != null ? out.size() : 0;
        }
        if (out == null) {
          out = new ArrayList<>();
        }
        out.add(fakeMessage);
      }
    }
    if (out == null || foundIndex == -1) {
      return null;
    }
    return new TGPlayerController.PlayList(out, foundIndex).setReachedEnds(true, true);
  }

  @Override
  public boolean wouldReusePlayList (TdApi.Message fromMessage, boolean isReverse, boolean hasAltered, List<TdApi.Message> trackList, long playListChatId) {
    return false;
  }

  private LoadOperation currentLoadOperation;

  private void cancelCurrentLoadOperation () {
    if (currentLoadOperation != null) {
      currentLoadOperation.cancel();
      currentLoadOperation = null;
    }
  }

  private void openAlert (final LoadOperation context, final int title, final int content) {
    UI.post(() -> {
      if (!isDestroyed() && currentLoadOperation == context && !context.isCancelled()) {
        openAlert(title, content);
      }
    });
  }

  private LoadOperation buildFolder (final String path, final String parent) {
    return new LoadOperation(this) {
      @Override
      public Result act () {
        try {
          final File dir = new File(path);
          if (!dir.exists() || !dir.isDirectory()) {
            openAlert(this, R.string.AppName, R.string.FolderDoesNotExist);
            return null;
          }

          if (!dir.canRead()) {
            openAlert(this, R.string.AppName, R.string.AccessError);
            return null;
          }

          File[] files = dir.listFiles();

          if (files == null || files.length == 0) {
            openAlert(this, R.string.AppName, R.string.FolderEmpty);
            return null;
          }

          ArrayList<File> filesList = new ArrayList<>(files.length);
          Collections.addAll(filesList, files);
          Collections.sort(filesList, MediaBottomFilesController.this);

          ArrayList<ListItem> items = new ArrayList<>(filesList.size() + 1);
          items.add(createItem(createItem(context, tdlib, KEY_UPPER, R.drawable.baseline_folder_24, "..", StringUtils.isEmpty(parent) ? Lang.getString(R.string.AttachFolderHome) : parent), R.id.btn_folder_upper));

          if ("/".equals(path)) {
            addApplicationFolders(items);
          }

          for (File file : filesList) {
            items.add(createItem(createItem(context, tdlib, file, null), file.isDirectory() ? R.id.btn_folder : R.id.btn_file));
          }

          return new Result(items, true);
        } catch (Throwable t) {
          Log.e("Cannot build folder", t);
          openAlert(this, R.string.AppName, R.string.AccessError);
        }
        return null;
      }
    };
  }

  private void addApplicationFolders (List<ListItem> items) {
    try {
      String internalPath = UI.getContext().getFilesDir().getPath();
      File externalFile = UI.getContext().getExternalFilesDir(null);
      String externalPath = externalFile != null ? externalFile.getPath() : null;

      if (externalFile != null && !StringUtils.equalsOrBothEmpty(externalPath, internalPath)) {
        InlineResultCommon external = createItem(context, tdlib, KEY_FOLDER + externalPath, R.drawable.baseline_settings_24, Lang.getString(R.string.ApplicationFolderExternal), externalPath);
        items.add(createItem(external, R.id.btn_folder));
      }
      items.add(createItem(createItem(context, tdlib, KEY_FOLDER + internalPath, R.drawable.baseline_settings_24, Lang.getString(R.string.ApplicationFolder), internalPath), R.id.btn_folder));
    } catch (Throwable t) {
      Log.e(t);
    }
  }

  private static String normalizePath (String path) {
    return path.startsWith(KEY_FOLDER) ? path.substring(KEY_FOLDER.length()) : path.startsWith(KEY_FILE) ? path.substring(KEY_FILE.length()) : path;
  }

  @Override
  public int compare (File o1, File o2) {
    final boolean d1 = o1.isDirectory();
    final boolean d2 = o2.isDirectory();

    if (d1 != d2) {
      return d1 ? -1 : 1;
    }

    if (d1) {
      return o1.compareTo(o2);
    }

    final long t1 = o1.lastModified();
    final long t2 = o2.lastModified();
    if (t1 > 0 && t2 > 0 && t1 != t2) {
      return Long.compare(t2, t1);
    }

    final String n1 = o1.getName();
    final String n2 = o2.getName();

    String e1 = U.getExtension(n1);
    String e2 = U.getExtension(n2);

    if (e1 == null && e2 == null) {
      return n1.compareTo(n2);
    }
    if (e1 == null) {
      return -1; // files without extension are higher
    }
    if (e2 == null) {
      return 1;
    }

    e1 = e1.toLowerCase();
    e2 = e2.toLowerCase();

    return e1.equals(e2) ? n1.compareTo(n2) : e1.compareTo(e2);
  }

  private void init () {
    if (adapter == null) {
      adapter = new SettingsAdapter(this);
      adapter.setOnLongClickListener(this);
      buildCells();
    }
  }

  @Override
  protected View onCreateView (Context context) {
    buildContentView(false);

    init();

    setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
    setAdapter(adapter);

    return contentView;
  }

  public static InlineResultCommon createItem (BaseActivity context, Tdlib tdlib, File file, @Nullable Object tag) {
    return createItem(context, tdlib, file, tag, null, file.lastModified(), null, false);
  }

  public static InlineResultCommon createItem (BaseActivity context, Tdlib tdlib, File file, @Nullable Object tag, String title, long lastModifiedTime, String subtitle, boolean isFolder) {
    if (file.isDirectory()) {
      return new InlineResultCommon(context, tdlib, KEY_FOLDER + file.getPath(), ColorId.fileAttach, R.drawable.baseline_folder_24, file.getName(), Lang.getString(R.string.Folder));
    } else {
      if (subtitle == null) {
        subtitle = Lang.getFileTimestamp(lastModifiedTime, TimeUnit.MILLISECONDS, file.length());
      }
      return new InlineResultCommon(context, tdlib, file, title != null ? title : file.getName(), subtitle, tag, isFolder);
    }
  }

  public static InlineResultCommon createItem (BaseActivity context, Tdlib tdlib, ImageGalleryFile file) {
    return createItem(context, tdlib, new File(file.getFilePath()), file, null, file.getDateTaken(), null, false);
  }

  public static InlineResultCommon createItem (BaseActivity context, Tdlib tdlib, String path, int iconRes, String title, String subtitle) {
    return new InlineResultCommon(context, tdlib, path, ColorId.fileAttach, iconRes, title, subtitle);
  }

  public static ListItem createItem (InlineResult<?> result, int id) {
    return new ListItem(ListItem.TYPE_CUSTOM_INLINE, id).setData(result);
  }

  public static InlineResultCommon createItem (BaseActivity context, Tdlib tdlib, Media.GalleryBucket bucket) {
    File file = new File(bucket.getPreviewImage().getFilePath());
    String bucketContent = Lang.pluralPhotosAndVideos(bucket.getPhotosCount(), bucket.getVideosCount()).toString();
    String str;
    if (DateUtils.isToday(bucket.getModifyTime(), TimeUnit.MILLISECONDS)) {
      String date = Lang.getModifiedTimestamp(bucket.getModifyTime(), TimeUnit.MILLISECONDS);
      str = Lang.getString(R.string.format_contentAndModifyDate2, bucketContent, date);
    } else {
      str = bucketContent;
    }
    return createItem(context, tdlib, file, bucket, bucket.getName(), 0, str, true);
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    if (super.onBackPressed(fromTop)) {
      return true;
    }
    if (inFileSelectMode) {
      mediaLayout.cancelMultiSelection();
      return true;
    }
    if (!stack.isEmpty()) {
      navigateUpper();
      return true;
    }
    return false;
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.btn_folder_upper) {
      navigateUpper();
      return;
    }
    Object tag = v.getTag();
    if (tag == null || !(tag instanceof ListItem)) {
      return;
    }

    ListItem item = (ListItem) tag;
    if (item.getViewType() == ListItem.TYPE_CUSTOM_INLINE) {
      InlineResultCommon result = (InlineResultCommon) item.getData();
      final int itemId = item.getId();
      if (itemId == R.id.btn_file || itemId == R.id.btn_music) {
        if (inFileSelectMode) {
          selectItem(item, result);
        } else {
          switch (result.getType()) {
            case InlineResult.TYPE_AUDIO: {
              mediaLayout.sendMusic(v, (MusicEntry) result.getTag());
              break;
            }
            case InlineResult.TYPE_DOCUMENT: {
              mediaLayout.sendFile(v, result.getId());
              break;
            }
          }
        }
      } else if (itemId == R.id.btn_bucket) {
        navigateInside(v, KEY_BUCKET, result);
      } else {
        String path = result.getId();
        boolean isMusic = KEY_MUSIC.equals(path);
        if (mediaLayout.getTarget() != null) {
          boolean res = mediaLayout.getTarget().showRestriction(v, isMusic ? RightId.SEND_AUDIO : RightId.SEND_DOCS);
          if (res) {
            return;
          }
        }
        boolean isDownloads = KEY_DOWNLOADS.equals(path);
        if (v.getId() == R.id.btn_internalStorage || isDownloads) {
          if (!context.permissions().canManageStorage()) {
            showSystemPicker(isDownloads);
            return;
          }
          if (context.permissions().requestReadExternalStorage(Permissions.ReadType.ALL, grantType -> {
            if (grantType != Permissions.GrantResult.ALL || !context.permissions().canManageStorage()) {
              showSystemPicker(isDownloads);
            } else {
              navigateTo(v, result);
            }
          })) {
            return;
          }
        }

        if (path != null) {
          switch (path) {
            case KEY_GALLERY: {
              if (context.permissions().requestReadExternalStorage(Permissions.ReadType.IMAGES_AND_VIDEOS, grantType -> {
                if (grantType == Permissions.GrantResult.ALL) {
                  navigateTo(v, result);
                } else {
                  // TODO 1-tap access to privacy settings?
                  context.tooltipManager().builder(v).icon(R.drawable.baseline_warning_24).show(tdlib, R.string.MissingGalleryPermission).hideDelayed();
                }
              })) {
                return;
              }
              break;
            }
            case KEY_MUSIC: {
              if (context.permissions().requestReadExternalStorage(Permissions.ReadType.AUDIO, grantType -> {
                if (grantType == Permissions.GrantResult.ALL) {
                  navigateTo(v, result);
                } else {
                  // TODO 1-tap access to privacy settings?
                  context.tooltipManager().builder(v).icon(R.drawable.baseline_warning_24).show(tdlib, R.string.MissingAudioPermission).hideDelayed();
                }
              })) {
                return;
              }
              break;
            }
          }
        }
        navigateTo(v, result);
      }
    }
  }

  private void navigateTo (View view, InlineResultCommon result) {
    String path = result.getId();
    if (path != null) {
      if (KEY_GALLERY.equals(path) || KEY_MUSIC.equals(path) || KEY_DOWNLOADS.equals(path) || KEY_BUCKET.equals(path) || path.startsWith(KEY_FOLDER)) {
        navigateInside(view, path, result);
      } else if (KEY_UPPER.equals(path)) {
        navigateUpper();
      }
    }
  }

  @Override
  public boolean canMoveRecycler () {
    return super.canMoveRecycler() && stack.isEmpty();
  }

  @Override
  public boolean onLongClick (View v) {
    Object tag = v.getTag();
    if (tag != null && tag instanceof ListItem) {
      ListItem item = (ListItem) tag;
      if (item.getViewType() == ListItem.TYPE_CUSTOM_INLINE && (item.getId() == R.id.btn_file || item.getId() == R.id.btn_music)) {
        InlineResult<?> result = (InlineResult<?>) item.getData();
        if (result != null) {
          selectItem(item, result);
          return true;
        }
      }
    }
    return false;
  }

  private ArrayList<InlineResult<?>> selectedItems;

  private boolean inFileSelectMode;

  private void setInFileSelectMode (boolean inFileSelectMode) {
    if (this.inFileSelectMode != inFileSelectMode) {
      this.inFileSelectMode = inFileSelectMode;
      adapter.setInSelectMode(inFileSelectMode, false, null);
      if (!inFileSelectMode && !selectedItems.isEmpty()) {
        selectedItems.clear();
        adapter.clearSelectedItems();
      }
    }
  }

  private void selectItem (ListItem item, InlineResult<?> inlineResult) {
    int existingIndex;
    if (selectedItems == null) {
      existingIndex = -1;
      selectedItems = new ArrayList<>();
    } else {
      existingIndex = selectedItems.indexOf(inlineResult);
    }

    int selectionIndex;
    boolean isSelected;
    if (existingIndex != -1) {
      isSelected = false;
      selectionIndex = existingIndex;

      selectedItems.remove(existingIndex);
      item.setSelected(false, existingIndex);

      int remainingCount = selectedItems.size();
      for (int i = existingIndex; i < remainingCount; i++) {
        InlineResult<?> nextData = selectedItems.get(i);
        int foundIndex = adapter.indexOfViewByData(nextData);
        if (foundIndex == -1) {
          throw new IllegalStateException();
        }
        ListItem nextItem = adapter.getItems().get(foundIndex);
        int newIndex = nextItem.decrementSelectionIndex();
        adapter.setIsSelected(foundIndex, true, newIndex);
      }
    } else {
      isSelected = true;
      selectionIndex = selectedItems.size();

      selectedItems.add(inlineResult);
      item.setSelected(true, selectionIndex);
    }

    int i = 0;
    for (ListItem listItem : adapter.getItems()) {
      if (listItem.getId() == item.getId() && listItem.getData() == item.getData()) {
        adapter.setIsSelected(i, isSelected, selectionIndex);
        break;
      }
      i++;
    }
    setInFileSelectMode(!selectedItems.isEmpty());
    mediaLayout.setCounter(selectedItems.size());
  }

  @Override
  protected void onMultiSendPress (View view, @NonNull TdApi.MessageSendOptions options, boolean disableMarkdown) {
    if (selectedItems == null || selectedItems.isEmpty()) {
      return;
    }

    ArrayList<MusicEntry> musicEntries = new ArrayList<>();
    ArrayList<String> files = new ArrayList<>();

    for (InlineResult<?> result : selectedItems) {
      switch (result.getType()) {
        case InlineResult.TYPE_AUDIO: {
          musicEntries.add((MusicEntry) ((InlineResultCommon) result).getTag());
          break;
        }
        case InlineResult.TYPE_DOCUMENT: {
          files.add(result.getId());
          break;
        }
      }
    }

    if (musicEntries.isEmpty() && files.isEmpty()) {
      return;
    }

    mediaLayout.sendFilesMixed(view, files, musicEntries, options, true);
  }

  @Override
  protected void onCancelMultiSelection () {
    if (selectedItems != null) {
      setInFileSelectMode(false);
    }
  }

  private ArrayList<StackItem> stack = new ArrayList<>();

  private static class StackItem {
    private final String path;
    private final int position, positionOffset;

    public StackItem (String path, int position, int positionOffset) {
      this.path = path;
      this.position = position;
      this.positionOffset = positionOffset;
    }
  }

  private void navigateUpper () {
    if (!stack.isEmpty()) {
      if (inFileSelectMode) {
        mediaLayout.cancelMultiSelection();
      }
      final StackItem removedItem = stack.remove(stack.size() - 1);
      if (stack.isEmpty()) {
        buildCells();
        collapseToStart();
      } else {
        StackItem item = stack.get(stack.size() - 1);
        navigateToPath(null, item.path, getLastPath(2), true, null, () -> {
          LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
          manager.scrollToPositionWithOffset(removedItem.position, removedItem.positionOffset);
        }, this::navigateUpper);
      }
    }
  }

  private String getLastPath (int skipCount) {
    return stack.size() < skipCount ? null : normalizePath(stack.get(stack.size() - skipCount).path);
  }

  private void navigateInside (final View view, final String path, final InlineResultCommon data) {
    if (inFileSelectMode) {
      mediaLayout.cancelMultiSelection();
    }

    final LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
    final int firstPosition = manager.findFirstVisibleItemPosition();
    final View firstView = firstPosition != RecyclerView.NO_POSITION ? manager.findViewByPosition(firstPosition) : null;
    final int firstPositionOffset = firstView != null ? firstView.getTop() : 0;

    navigateToPath(view, path, getLastPath(1), false, data, () -> {
      stack.add(new StackItem(path, firstPosition != RecyclerView.NO_POSITION ? firstPosition : 0, firstPositionOffset));
      manager.scrollToPositionWithOffset(0, 0);
    }, null);
  }

  @Override
  protected int getInitialContentHeight () {
    init();
    return Screen.dp(72f) * (initialItemsCount != 0 ? initialItemsCount : U.isExternalMemoryAvailable() && !Environment.isExternalStorageEmulated() ? 5 : 4);
  }

  @Override
  protected int getRecyclerHeaderOffset () {
    return Screen.dp(101f);
  }
}

