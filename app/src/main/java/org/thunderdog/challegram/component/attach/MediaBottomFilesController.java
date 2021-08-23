package org.thunderdog.challegram.component.attach;

import android.content.Context;
import android.database.Cursor;
import android.os.Environment;
import android.os.Looper;
import android.os.StatFs;
import android.provider.MediaStore;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
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
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.SettingsAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.DateUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.RunnableData;

/**
 * Date: 19/10/2016
 * Author: default
 */

public class MediaBottomFilesController extends MediaBottomBaseController<Void> implements View.OnClickListener, View.OnLongClickListener, Comparator<File>, TGPlayerController.PlayListBuilder {
  public MediaBottomFilesController (MediaLayout context) {
    super(context, R.string.File);
  }

  @Override
  public int getId () {
    return R.id.controller_media_files;
  }

  private SettingsAdapter adapter;

  private static final String KEY_GALLERY = "gallery";
  private static final String KEY_BUCKET = "bucket";
  private static final String KEY_MUSIC = "music";
  private static final String KEY_FOLDER = "dir://";
  private static final String KEY_FILE = "file://";
  private static final String KEY_UPPER = "..";

  private int initialItemsCount;

  private void buildCells () {
    navigateToPath(null, null, false, null, null, null);
  }

  private void navigateToPath (final String currentPath, final String parentPath, boolean isUpper, final InlineResultCommon data, Runnable onDone, Runnable onError) {
    cancelCurrentLoadOperation();

    ArrayList<ListItem> items = new ArrayList<>();

    if (currentPath != null && !currentPath.isEmpty()) {
      LoadOperation operation;
      RunnableData<Runnable> before = null;
      if (KEY_GALLERY.equals(currentPath)) {
        operation = buildGallery();
      } else if (KEY_MUSIC.equals(currentPath)) {
        operation = buildMusic();
      } else if (KEY_BUCKET.equals(currentPath)) {
        operation = buildBucket(data);
      } else if (currentPath.startsWith(KEY_FOLDER)) {
        String path = currentPath.substring(KEY_FOLDER.length());
        operation = buildFolder(path, parentPath);
        String internalPath = UI.getAppContext().getFilesDir().getPath();
        File external = UI.getAppContext().getExternalFilesDir(null);
        String externalPath = external != null ? external.getPath() : null;
        if (!isUpper && (path.equals(internalPath) || path.equals(externalPath))) {
          before = after -> showOptions(Lang.getMarkdownString(this, R.string.ApplicationFolderWarning), new int[] {R.id.btn_done, R.id.btn_cancel}, new String[] {Lang.getString(R.string.ApplicationFolderWarningConfirm), Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_warning_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
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
        String text = Lang.getString(R.string.FreeXofY, Strings.buildSize(U.getFreeMemorySize(fs)), Strings.buildSize(U.getTotalMemorySize(fs)));
        InlineResultCommon internalStorage = new InlineResultCommon(context, tdlib, KEY_FOLDER + environmentPath, R.id.theme_color_fileAttach, isRemovable ? R.drawable.baseline_sd_storage_24 : R.drawable.baseline_storage_24, Lang.getString(isRemovable ? R.string.SdCard : R.string.InternalStorage), text);
        items.add(createItem(internalStorage, R.id.btn_internalStorage));
      }

      final ArrayList<String> externalStorageFiles = U.getExternalStorageDirectories(baseExternalDir != null ? baseExternalDir.getPath() : null, false);
      if (externalStorageFiles != null) {
        for (String dir : externalStorageFiles) {
          InlineResultCommon internalStorage = new InlineResultCommon(context, tdlib, KEY_FOLDER + dir, R.id.theme_color_fileAttach, R.drawable.baseline_storage_24, Lang.getString(R.string.Storage), dir);
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

    try {
      File file = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
      if (file.exists() && file.isDirectory()) {
        File[] files = file.listFiles();
        if (files != null && files.length > 0) {
          InlineResultCommon common = createItem(context, tdlib, KEY_FOLDER + file.getPath(), R.drawable.baseline_file_download_24, Lang.getString(R.string.Downloads), Lang.plural(R.string.xFiles, files.length));
          items.add(createItem(common, file.isDirectory() ? R.id.btn_folder : R.id.btn_file));
        }
      }
    } catch (Throwable t) {
      Log.e("Cannot add Downloads directory", t);
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
        InlineResultCommon rootDirectory = new InlineResultCommon(context, tdlib, KEY_FOLDER + rootDir.getPath(), R.id.theme_color_fileAttach, R.drawable.baseline_folder_24, Lang.getString(R.string.RootDirectory), text);
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

  private LoadOperation buildMusic () {
    return new LoadOperation(this) {
      @Override
      public Result act () {
        try {
          String[] projection = {
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.MIME_TYPE
          };

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
            String mime = c.getString(5);
            if (!StringUtils.isEmpty(data)) {
              entries.add(new MusicEntry(id, artist != null ? artist : "", title != null ? title : "", data, duration, mime, 0));
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

      items.add(createItem(createItem(context, tdlib, KEY_FOLDER + internalPath, R.drawable.baseline_settings_24, Lang.getString(R.string.ApplicationFolder), internalPath), R.id.btn_folder));
      if (externalFile != null && !StringUtils.equalsOrBothEmpty(externalPath, internalPath)) {
        InlineResultCommon external = createItem(context, tdlib, KEY_FOLDER + externalPath, R.drawable.baseline_settings_24, Lang.getString(R.string.ApplicationFolderExternal), externalPath);
        items.add(createItem(external, R.id.btn_folder));
      }
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
      return new InlineResultCommon(context, tdlib, KEY_FOLDER + file.getPath(), R.id.theme_color_fileAttach, R.drawable.baseline_folder_24, file.getName(), Lang.getString(R.string.Folder));
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
    return new InlineResultCommon(context, tdlib, path, R.id.theme_color_fileAttach, iconRes, title, subtitle);
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
      switch (item.getId()) {
        case R.id.btn_file:
        case R.id.btn_music: {
          if (inFileSelectMode) {
            selectItem(item, result);
          } else {
            switch (result.getType()) {
              case InlineResult.TYPE_AUDIO: {
                mediaLayout.sendMusic((MusicEntry) result.getTag());
                break;
              }
              case InlineResult.TYPE_DOCUMENT: {
                mediaLayout.sendFile(result.getId());
                break;
              }
            }
          }
          break;
        }
        case R.id.btn_bucket: {
          navigateInside(KEY_BUCKET, result);
          break;
        }
        default: {
          String path = result.getId();
          if (path != null) {
            if (KEY_GALLERY.equals(path) || KEY_MUSIC.equals(path) || KEY_BUCKET.equals(path) || path.startsWith(KEY_FOLDER)) {
              navigateInside(path, result);
            } else if (KEY_UPPER.equals(path)) {
              navigateUpper();
            }
          }
          break;
        }
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
  protected void onMultiSendPress (@NonNull TdApi.MessageSendOptions options, boolean disableMarkdown) {
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

    mediaLayout.sendFilesMixed(files, musicEntries, options);
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
        navigateToPath(item.path, getLastPath(2), true, null, () -> {
          LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
          manager.scrollToPositionWithOffset(removedItem.position, removedItem.positionOffset);
        }, this::navigateUpper);
      }
    }
  }

  private String getLastPath (int skipCount) {
    return stack.size() < skipCount ? null : normalizePath(stack.get(stack.size() - skipCount).path);
  }

  private void navigateInside (final String path, final InlineResultCommon data) {
    if (inFileSelectMode) {
      mediaLayout.cancelMultiSelection();
    }

    final LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
    final int firstPosition = manager.findFirstVisibleItemPosition();
    final View view = firstPosition != RecyclerView.NO_POSITION ? manager.findViewByPosition(firstPosition) : null;
    final int firstPositionOffset = view != null ? view.getTop() : 0;

    navigateToPath(path, getLastPath(1), false, data, () -> {
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

