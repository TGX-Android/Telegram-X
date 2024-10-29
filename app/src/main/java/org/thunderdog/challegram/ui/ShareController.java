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
 * File created on 20/07/2017
 */
package org.thunderdog.challegram.ui;

import android.animation.Animator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Interpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.collection.LongSparseArray;
import androidx.collection.SparseArrayCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.attach.GridSpacingItemDecoration;
import org.thunderdog.challegram.component.chat.EmojiToneHelper;
import org.thunderdog.challegram.component.chat.InputView;
import org.thunderdog.challegram.component.dialogs.SearchManager;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.InlineResult;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGFoundChat;
import org.thunderdog.challegram.mediaview.data.MediaItem;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.navigation.HeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.MenuMoreWrap;
import org.thunderdog.challegram.navigation.TelegramViewController;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.ChatListListener;
import org.thunderdog.challegram.telegram.RightId;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibChatList;
import org.thunderdog.challegram.telegram.TdlibChatListSlice;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.TGMimeType;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.HapticMenuHelper;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.v.RtlGridLayoutManager;
import org.thunderdog.challegram.widget.BaseView;
import org.thunderdog.challegram.widget.CustomImageView;
import org.thunderdog.challegram.widget.EmojiLayout;
import org.thunderdog.challegram.widget.ForceTouchView;
import org.thunderdog.challegram.widget.KeyboardFrameLayout;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.SeparatorView;
import org.thunderdog.challegram.widget.TextFormattingLayout;
import org.thunderdog.challegram.widget.VerticalChatView;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.SingleViewProvider;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.FileUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.collection.LongList;
import me.vkryl.core.collection.LongSet;
import me.vkryl.core.lambda.Filter;
import me.vkryl.core.lambda.RunnableBool;
import tgx.td.ChatId;
import tgx.td.ChatPosition;
import tgx.td.Td;

public class ShareController extends TelegramViewController<ShareController.Args> implements
  FactorAnimator.Target, Runnable, PopupLayout.PopupHeightProvider,
  View.OnClickListener, Menu, PopupLayout.TouchSectionProvider,
  EmojiLayout.Listener, BaseView.ActionListProvider, EmojiToneHelper.Delegate,
  ChatListListener, Filter<TdApi.Chat>, InputView.SelectionChangeListener {
  private static final int MODE_MESSAGES = 0;
  private static final int MODE_TEXT = 1;
  private static final int MODE_GAME = 2;
  private static final int MODE_FILES = 3;
  private static final int MODE_CONTACT = 4;
  private static final int MODE_STICKER = 5;
  private static final int MODE_CUSTOM = 6;
  private static final int MODE_CUSTOM_CONTENT = 7;
  private static final int MODE_TELEGRAM_FILES = 8;

  public interface ShareProviderDelegate {
    void generateFunctionsForChat (long chatId, TdApi.Chat chat, TdApi.MessageSendOptions sendOptions, ArrayList<TdApi.Function<?>> functions);
    CharSequence generateErrorMessageForChat (long chatId);
  }

  public static class FileInfo {
    public final String path, mimeType;

    public FileInfo (String path, String mimeType) {
      this.path = path;
      this.mimeType = mimeType;
    }
  }

  public static class Args {
    private final int mode;

    private TdApi.ChatList chatList;
    private TdApi.Message[] messages;
    private long messageThreadId;

    private TdApi.FormattedText text;

    private String shareText, shareButtonText;
    private String exportText;

    private TdApi.Game game;
    private long botUserId;
    private TdApi.Message botMessage;
    private boolean withUserScore;

    private TdApi.InputMessageContent customContent;

    private FileInfo[] files;

    private TdApi.User contactUser;

    private TdApi.Sticker sticker;

    private boolean needOpenChat;

    private boolean allowCopyLink;

    private boolean disallowReply;

    private ShareProviderDelegate customDelegate;

    private Runnable after;

    private TdApi.MessageSendOptions defaultSendOptions;

    public Args (TdApi.Message message) {
      this(new TdApi.Message[]{message});
    }

    public Args (TdApi.Message[] messages) {
      this.mode = MODE_MESSAGES;
      this.messages = messages;
    }

    public Args (TdApi.InputMessageContent content) {
      this.mode = MODE_CUSTOM_CONTENT;
      this.customContent = content;
    }

    public Args (String text) {
      this(new TdApi.FormattedText(text, new TdApi.TextEntity[0]));
    }

    public Args (TdApi.FormattedText text) {
      this.mode = MODE_TEXT;
      this.text = text;
    }

    public Args (TdApi.Game game, long botUserId, TdApi.Message message, boolean withUserScore) {
      this.mode = MODE_GAME;
      this.game = game;
      this.botUserId = botUserId;
      this.botMessage = message;
      this.withUserScore = withUserScore;
    }

    public Args (File file, String mimeType) {
      this(file.getPath(), mimeType);
    }

    public Args (String filePath, String mimeType) {
      this(new FileInfo[] {new FileInfo(filePath, mimeType)});
    }

    public Args (FileInfo[] files) {
      this.mode = MODE_FILES;
      this.files = files;
    }

    public Args (TdApi.User contactUser) {
      this.mode = MODE_CONTACT;
      this.contactUser = contactUser;
    }

    public Args (TdApi.Sticker sticker) {
      if (Td.customEmojiId(sticker) != 0) {
        this.mode = MODE_TEXT;
        this.text = TD.toSingleEmojiText(sticker);
      } else {
        this.mode = MODE_STICKER;
        this.sticker = sticker;
      }
    }

    public Args (ShareProviderDelegate delegate) {
      this.mode = MODE_CUSTOM;
      this.customDelegate = delegate;
    }

    private MediaItem[] telegramFiles;
    private CharSequence telegramCaption, telegramExportCaption;

    public Args (MediaItem file, CharSequence caption, CharSequence exportCaption) {
      this(new MediaItem[] {file}, caption, exportCaption);
    }

    public Args (MediaItem[] files, CharSequence caption, CharSequence exportCaption) {
      this.mode = MODE_TELEGRAM_FILES;
      this.telegramFiles = files;
      this.telegramCaption = caption;
      this.telegramExportCaption = exportCaption;
    }

    public Args setExport (String exportText) {
      this.exportText = exportText;
      return this;
    }

    public Args setDisallowReply (boolean disallowReply) {
      this.disallowReply = disallowReply;
      return this;
    }

    public Args setShare (@NonNull String shareText, @Nullable String shareButtonText) {
      this.shareText = shareText;
      this.shareButtonText = shareButtonText;
      return this;
    }

    public Args setDefaultSendOptions (@Nullable TdApi.MessageSendOptions defaultSendOptions) {
      this.defaultSendOptions = defaultSendOptions;
      return this;
    }

    public Args setAllowCopyLink (boolean allowCopyLink) {
      this.allowCopyLink = allowCopyLink;
      return this;
    }

    private @StringRes int copyLinkActionNameRes;
    private Runnable copyLinkAction;

    public Args setCustomCopyLinkAction (@StringRes int copyLinkActionName, Runnable runnable) {
      this.copyLinkActionNameRes = copyLinkActionName;
      this.copyLinkAction = runnable;
      return this;
    }

    public Args setAfter (Runnable after) {
      this.after = after;
      return this;
    }

    public Args setNeedOpenChat (boolean needOpenChat) {
      this.needOpenChat = needOpenChat;
      return this;
    }

    public Args setMessageThreadId (long messageThreadId) {
      this.messageThreadId = messageThreadId;
      return this;
    }
  }

  public ShareController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private int mode;
  private TdApi.ChatList displayingChatList;

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.SendTo);
  }

  @Override
  protected int getHeaderTextColorId () {
    return ColorId.text;
  }

  @Override
  protected int getHeaderColorId () {
    return ColorId.filling;
  }

  @Override
  protected int getHeaderIconColorId () {
    return ColorId.icon;
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_CLOSE;
  }

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    this.mode = args.mode;
    this.displayingChatList = args.chatList != null ? args.chatList : ChatPosition.CHAT_LIST_MAIN;
  }

  @Override
  public int getId () {
    return R.id.controller_share;
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_search;
  }

  private int getMenuItemCount () {
    int count = 1;
    if (canExportContent()) {
      count++;
    }
    return count;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_search) {
      if (canCopyLink()) {
        header.addButton(menu, R.id.menu_btn_copy, getHeaderIconColorId(), this, R.drawable.baseline_link_24, Screen.dp(49f), R.drawable.bg_btn_header);
      }
      int exportState = getExportContentState();
      if (exportState != EXPORT_NONE) {
        header.addButton(menu, R.id.menu_btn_forward, getHeaderIconColorId(), this, R.drawable.baseline_share_24, Screen.dp(49f), R.drawable.bg_btn_header);
      }
      header.addSearchButton(menu, this, getHeaderIconColorId()).setTouchDownListener((v, e) -> {
        resetTopEnsuredState();
        hideSoftwareKeyboard();
      });
    } else if (id == R.id.menu_clear) {
      header.addClearButton(menu, this);
    }
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    if (inSearchMode()) {
      closeSearchMode(null);
      return true;
    }
    if (emojiShown) {
      forceCloseEmojiKeyboard();
      return true;
    }
    return false;
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_search) {
      if (getDisplayingChats() != null) {
        openSearchMode();
      }
    } else if (id == R.id.menu_btn_copy) {
      copyLink();
    } else if (id == R.id.menu_btn_forward) {
      exportContent();
    } else if (id == R.id.btn_menu_customize) {
      showShareSettings();
    } else if (id == R.id.menu_btn_clear) {
      clearSearchInput();
    }
  }

  private void revoke (List<Uri> uris) {
    for (Uri uri : uris) {
      context.revokeUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }
  }

  private void exportFiles (TdApi.File[] files, String type, @Nullable String title, @Nullable CharSequence caption) {
    Intent shareIntent;
    if (files.length == 1) {
      Uri uri = U.contentUriFromFile(new File(files[0].local.path));
      if (uri == null)
        return;
      shareIntent = new Intent();
      shareIntent.setAction(Intent.ACTION_SEND);
      shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
    } else {
      ArrayList<Uri> uris = new ArrayList<>(files.length);
      for (TdApi.File file : files) {
        Uri uri = U.contentUriFromFile(new File(file.local.path));
        if (uri == null) {
          revoke(uris);
          return;
        }
        uris.add(uri);
      }
      shareIntent = new Intent();
      shareIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
      shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
    }
    shareIntent.setType(type);
    if (!StringUtils.isEmpty(caption)) {
      shareIntent.putExtra(Intent.EXTRA_TEXT, caption);
    }
    if (StringUtils.isEmpty(title)) {
      int title1, title2;
      if (type.equals("image/gif")) {
        title1 = R.string.ShareTitleGif;
        title2 = R.string.ShareTitleGifX;
      } else if (type.startsWith("image/")) {
        title1 = R.string.ShareTitleImage;
        title2 = R.string.ShareTitleImageX;
      } else if (type.startsWith("video/")) {
        title1 = R.string.ShareTitleVideo;
        title2 = R.string.ShareTitleVideoX;
      } else if (type.startsWith("audio/")) {
        title1 = R.string.ShareTitleAudio;
        title2 = R.string.ShareTitleAudioX;
      } else {
        title1 = R.string.ShareTitleFile;
        title2 = R.string.ShareTitleFileX;
      }
      title = files.length > 1 ? Lang.plural(title2, files.length) : Lang.getString(title1);
    }
    context.startActivity(Intent.createChooser(shareIntent, title));
  }

  private List<TdApi.File> awaitingFiles;
  private static class FileEntry {
    public final TdApi.File file;
    public Runnable onTimeout;

    public FileEntry (TdApi.File file) {
      this.file = Td.copyOf(file);
    }

    public long size () {
      return (file.local != null && file.local.isDownloadingActive) ? file.expectedSize : file.size;
    }
  }
  private SparseArrayCompat<FileEntry> downloadingFiles;
  private int downloadedFileCount = 0;
  private float lastDispatchedProgress;

  private float calculateFileProgress () {
    if (downloadingFiles != null) {
      double totalSize = 0;
      for (int i = 0; i < downloadingFiles.size(); i++) {
        totalSize += downloadingFiles.valueAt(i).size();
      }
      float progress = 0f;
      for (int i = 0; i < downloadingFiles.size(); i++) {
        FileEntry file = downloadingFiles.valueAt(i);
        progress += TD.getFileProgress(file.file) * (float) ((double) file.size() / totalSize);
      }
      return progress;
    }
    return 0f;
  }

  private void exportDelayed () {
    tdlib.ui().postDelayed(() -> {
      if (isDestroyed() || popupLayout == null || popupLayout.isWindowHidden() || isSent)
        return;
      int state = getExportContentState();
      if (state == EXPORT_AVAILABLE) {
        exportContent();
      } else {
        Log.w("cant export content #2: %d", state);
      }
    }, 100);

  }

  private void onFileLoaded (FileEntry file) {
    if (downloadingFiles == null || isDestroyed() || popupLayout == null || popupLayout.isWindowHidden() || isSent)
      return;
    dispatchDownloadProgress();
    file.onTimeout = null;
    if (++downloadedFileCount == downloadingFiles.size()) {
      if (headerView != null) {
        headerView.updateCustomButton(getMenuId(), R.id.menu_btn_forward, view -> {
          if (view instanceof HeaderButton) {
            ((HeaderButton) view).setShowProgress(false, 0f);
          }
        });
      }
      exportDelayed();
    }
  }

  private void dispatchDownloadProgress () {
    if (downloadingFiles != null) {
      float newProgress = calculateFileProgress();
      if (lastDispatchedProgress != newProgress && headerView != null) {
        headerView.updateCustomButton(getMenuId(), R.id.menu_btn_forward, view -> {
          if (view instanceof HeaderButton) {
            ((HeaderButton) view).setCurrentProgress(lastDispatchedProgress = calculateFileProgress());
          }
        });
      }
    }
  }

  private void cancelDownloadingFiles () {
    if (downloadingFiles != null) {
      for (int i = 0; i < downloadingFiles.size(); i++) {
        FileEntry file = downloadingFiles.valueAt(i);
        if (file.onTimeout != null) {
          file.onTimeout.run();
          file.onTimeout = null;
        }
      }
      downloadingFiles = null;
      downloadedFileCount = 0;
    }
    if (!isDestroyed() && headerView != null) {
      headerView.updateCustomButton(getMenuId(), R.id.menu_btn_forward, view -> {
        if (view instanceof HeaderButton) {
          ((HeaderButton) view).setShowProgress(false, 0f);
        }
      });
    }
  }

  private TdApi.File getFile (TdApi.Message message) {
    TdApi.File file = TD.getFile(message);
    if (file != null && downloadingFiles != null) {
      FileEntry downloadedFile = downloadingFiles.get(file.id);
      if (downloadedFile != null) {
        file = downloadedFile.file;
      }
    }
    return file;
  }

  private void downloadAwaitingFilesAndExport () {
    if (this.downloadingFiles != null || this.awaitingFiles == null)
      return;
    this.downloadingFiles = new SparseArrayCompat<>(awaitingFiles.size());
    for (TdApi.File file : awaitingFiles) {
      downloadingFiles.put(file.id, new FileEntry(file));
    }

    if (headerView != null) {
      headerView.updateCustomButton(getMenuId(), R.id.menu_btn_forward, view -> {
        if (view instanceof HeaderButton) {
          ((HeaderButton) view).setShowProgress(true, lastDispatchedProgress = calculateFileProgress());
        }
      });
    }
    for (int i = 0; i < downloadingFiles.size(); i++) {
      FileEntry file = downloadingFiles.valueAt(i);
      file.onTimeout = tdlib.files().downloadFileSync(file.file, -1, downloadedFile -> tdlib.ui().post(() -> onFileLoaded(file)), updatedFile -> tdlib.ui().post(this::dispatchDownloadProgress), null);
    }
  }

  private void exportContact (String phoneNumber, String firstName, String lastName, @Nullable TdApi.Usernames usernames, @Nullable String customVcard) {
    StringBuilder b = new StringBuilder()
      .append("BEGIN:VCARD\r\n")
      .append("VERSION:3.0\r\n")
      .append("N:").append(Strings.vcardEscape(lastName)).append(";").append(Strings.vcardEscape(firstName)).append("\r\n")
      .append("FN:").append(Strings.vcardEscape(TD.getUserName(firstName, lastName))).append("\r\n")
      .append("TEL;TYPE=cell:").append(Strings.vcardEscape(Strings.formatPhone(phoneNumber, true, true))).append("\r\n");
    if (Td.hasUsername(usernames)) {
      b.append("URL:").append(Strings.vcardEscape(tdlib.tMeUrl(usernames))).append("\r\n");
    }
    b.append("END:VCARD\r\n");
    String vcard = b.toString();

    Background.instance().post(() -> {
      try {
        File vcfCacheDirectory = new File(UI.getAppContext().getFilesDir(), "vcf");
        if (!FileUtils.createDirectory(vcfCacheDirectory))
          return;
        File file = new File(vcfCacheDirectory, "temp.vcf");
        if (!file.exists() && !file.createNewFile())
          return;
        try (FileWriter fw = new FileWriter(file)) {
          fw.append(vcard);
          fw.flush();
        }
        tdlib.ui().post(() -> {
          if (!isDestroyed()) {
            exportSingleFile(file, "text/x-vcard", Lang.getString(R.string.ShareTitleContact));
          }
        });
      } catch (IOException t) {
        Log.e("Cannot create VCF file", t);
      }
    });
  }

  private void exportFiles (FileInfo[] files, String title) {
    if (files.length == 1) {
      FileInfo file = files[0];
      exportSingleFile(new File(file.path), file.mimeType, title);
      return;
    }
    ArrayList<Uri> uris = new ArrayList<>();
    Intent intent = new Intent();
    intent.setAction(Intent.ACTION_SEND_MULTIPLE);
    Set<String> fileTypes = new LinkedHashSet<>();
    for (FileInfo info : files) {
      fileTypes.add(info.mimeType);
    }
    if (fileTypes.size() == 1) {
      intent.setType(fileTypes.iterator().next());
    } else {
      intent.setType("*/*");
    }
    for (FileInfo info : files) {
      File file = new File(info.path);
      uris.add(U.contentUriFromFile(file));
    }
    intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
  }

  private void exportSingleFile (File file, String mimeType, String title) {
    Uri uri = U.contentUriFromFile(file);
    if (uri == null)
      return;
    Intent shareIntent = new Intent();
    shareIntent.setAction(Intent.ACTION_SEND);
    shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
    shareIntent.setType(mimeType);
    context.startActivity(Intent.createChooser(shareIntent, title));
  }

  @SuppressWarnings("WrongConstant")
  private void exportContent () {
    if (isSent)
      return;

    int exportState = getExportContentState();
    if (exportState == EXPORT_DISABLED) {
      if (downloadingFiles != null) {
        cancelDownloadingFiles();
      } else {
        downloadAwaitingFilesAndExport();
      }
    }
    if (exportState != EXPORT_AVAILABLE) {
      Log.i("cant export content #1: %d", exportState);
      return;
    }

    Args args = getArgumentsStrict();

    if (!StringUtils.isEmpty(args.exportText)) {
      Intents.shareText(args.exportText);
    } else {
      switch (mode) {
        case MODE_MESSAGES: {
          if (args.messages.length == 1 && canExportStaticContent(args.messages[0])) {
            exportStaticContent(args.messages[0]);
            break;
          }
          String mimeType = getExportMimeType(args.messages[0]);
          int count = args.messages.length;

          @TdApi.MessageContent.Constructors int type = args.messages[0].content.getConstructor();
          for (int i = 1; i < count; i++) {
            @TdApi.MessageContent.Constructors int contentType = args.messages[i].content.getConstructor();
            if (contentType != type) {
              type = 0;
              break;
            }
          }

          long authorId = Td.getMessageAuthorId(args.messages[0]);
          for (int i = 1; i < count; i++) {
            long contentAuthorId = Td.getMessageAuthorId(args.messages[i]);
            if (authorId != contentAuthorId) {
              authorId = 0;
              break;
            }
          }

          int title1Res, title2Res, textRes;
          switch (type) {
            case TdApi.MessagePhoto.CONSTRUCTOR:
            case TdApi.MessageChatChangePhoto.CONSTRUCTOR:
              title1Res = R.string.ShareTitleImage;
              title2Res = R.string.ShareTitleImageX;
              textRes = R.string.ShareTextPhoto;
              break;
            case TdApi.MessageVideo.CONSTRUCTOR:
              title1Res = R.string.ShareTitleVideo;
              title2Res = R.string.ShareTitleVideoX;
              textRes = R.string.ShareTextVideo;
              break;
            case TdApi.MessageAnimation.CONSTRUCTOR:
              title1Res = R.string.ShareTitleGif;
              title2Res = R.string.ShareTitleGifX;
              textRes = R.string.ShareTextGif;
              break;
            case TdApi.MessageAudio.CONSTRUCTOR:
              title1Res = R.string.ShareTitleAudio;
              title2Res = R.string.ShareTitleAudioX;
              textRes = R.string.ShareTextMusic;
              break;
            case TdApi.MessageDocument.CONSTRUCTOR:
              title1Res = R.string.ShareTitleFile;
              title2Res = R.string.ShareTitleFileX;
              textRes = R.string.ShareTextFile;
              break;
            case TdApi.MessageText.CONSTRUCTOR:
              title1Res = R.string.ShareTitleText;
              title2Res = R.string.ShareTitleMediaX;
              textRes = R.string.ShareTextPlain;
              break;
            default:
              Td.assertMessageContent_91c1e338();
              title1Res = R.string.ShareTitleMedia;
              title2Res = R.string.ShareTitleMediaX;
              textRes = R.string.ShareTextMedia;
              break;
          }

          String title = count > 1 ? Lang.plural(title2Res, count) : Lang.getString(title1Res);

          TdApi.File[] files = new TdApi.File[args.messages.length];
          int i = 0;
          for (TdApi.Message message : args.messages) {
            files[i] = getFile(message);
            i++;
          }

          TdApi.FormattedText messageCaption = null;
          if (authorId != 0) {
            for (TdApi.Message message : args.messages) {
              TdApi.FormattedText formattedCaption = Td.textOrCaption(message.content);
              //noinspection UnsafeOptInUsageError
              if (Td.isEmpty(messageCaption)) {
                messageCaption = formattedCaption;
              } else if (!Td.isEmpty(formattedCaption) && !Td.equalsTo(messageCaption, formattedCaption)) {
                messageCaption = null;
                break;
              }
            }
          }

          boolean isOutgoing = authorId != 0 && args.messages[0].isOutgoing && !tdlib.isChannel(args.messages[0].chatId);
          boolean isPrivateMedia = authorId == 0 && ChatId.isUserChat(args.messages[0].chatId);

          final String author, username, authorSignature;
          if (authorId == 0) {
            author = tdlib.chatTitle(args.messages[0].chatId);
            username = tdlib.chatUsername(args.messages[0].chatId);
            isOutgoing = tdlib.isSelfChat(args.messages[0].chatId);
            isPrivateMedia = isPrivateMedia && !isOutgoing;
          } else {
            author = tdlib.messageAuthor(args.messages[0], false, false);
            username = tdlib.messageAuthorUsername(args.messages[0]);
          }
          authorSignature = !StringUtils.isEmpty(username) ? Lang.getString(R.string.format_ShareAuthorLink, author, tdlib.tMeUrl(username)) : author;

          //noinspection UnsafeOptInUsageError
          if (!Td.isEmpty(messageCaption)) {
            CharSequence caption = TD.toCharSequence(messageCaption);
            if (isOutgoing || isPrivateMedia) {
              exportFiles(files, mimeType, title, caption);
            } else if (canShareLink()) {
              tdlib.getMessageLink(args.messages[0], args.messages.length > 1, args.messageThreadId != 0, link ->
                exportFiles(files, mimeType, title, Lang.getString(R.string.format_ShareTextSignature, caption, link.url))
              );
            } else {
              exportFiles(files, mimeType, title, Lang.getString(R.string.format_ShareTextSignature, caption, Lang.getString(textRes, authorSignature)));
            }
          } else if (isOutgoing) {
            exportFiles(files, mimeType, title, null);
          } else if (isPrivateMedia) {
            exportFiles(files, mimeType, title, Lang.getString(R.string.ShareTextSharedMedia, authorSignature));
          } else if (canShareLink()) {
            tdlib.getMessageLink(args.messages[0], args.messages.length > 1, args.messageThreadId != 0, link -> {
                String signature = Lang.getString(R.string.format_ShareAuthorMessage, author, link.url);
                exportFiles(files, mimeType, title, Lang.getString(textRes, signature));
              }
            );
          } else {
            exportFiles(files, mimeType, title, Lang.getString(textRes, authorSignature));
          }
          break;
        }
        case MODE_TELEGRAM_FILES: {
          String mimeType = args.telegramFiles[0].getShareMimeType();
          TdApi.File[] files = new TdApi.File[args.telegramFiles.length];
          int i = 0;
          for (MediaItem item : args.telegramFiles) {
            files[i] = item.getShareFile();
            i++;
          }
          exportFiles(files, mimeType, null, args.telegramExportCaption);
          break;
        }
        case MODE_FILES: {
          exportFiles(args.files, args.files.length > 1 ? Lang.plural(R.string.ShareTitleFiles, args.files.length) : Lang.getString(R.string.ShareTitleFile));
          break;
        }
        case MODE_CONTACT: {
          exportContact(args.contactUser.phoneNumber, args.contactUser.firstName, args.contactUser.lastName, args.contactUser.usernames, null);
          break;
        }
      }
    }

    onSent();
    if (popupLayout != null)
      popupLayout.hideWindow(true);
  }

  @Override
  protected boolean useGraySearchHeader () {
    return true;
  }

  @Override
  protected int getSearchMenuId () {
    return R.id.menu_clear;
  }

  private RelativeLayout contentView;
  private CustomRecyclerView chatSearchView;
  private CustomRecyclerView recyclerView;
  private SettingsAdapter adapter;
  private LickView lickView;
  private InputView inputView;
  private View fixView;

  private static class LickView extends View {
    public LickView (Context context) {
      super(context);
    }

    private float factor;

    public void setFactor (float factor) {
      if (this.factor != factor) {
        this.factor = factor;
        invalidate();
      }
    }

    @Override
    protected void onDraw (Canvas c) {
      if (factor > 0f) {
        int bottom = getMeasuredHeight();
        int top = bottom - (int) ((float) bottom * factor);
        c.drawRect(0, top, getMeasuredWidth(), bottom, Paints.fillingPaint(Theme.fillingColor()));
      }
    }
  }

  private DoubleHeaderView headerCell;

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }

  private GridSpacingItemDecoration decoration;

  @Override
  protected int getChatSearchFlags () {
    return SearchManager.FLAG_NEED_TOP_CHATS | SearchManager.FLAG_ONLY_WRITABLE | SearchManager.FLAG_NEED_GLOBAL_SEARCH;
  }

  @Override
  protected View getSearchAntagonistView () {
    return contentView;
  }

  private SendButton sendButton;
  private SeparatorView bottomShadow;
  private boolean canShareLink;

  private HapticMenuHelper sendMenu;

  private LinearLayout bottomWrap;
  private View stubInputView;

  private ImageView emojiButton;
  private ImageView okButton;

  private FrameLayoutFix wrapView;

  @Override
  public boolean needsTempUpdates () {
    return true;
  }

  @Override
  public void onThemeColorsChanged (boolean areTemp, ColorState state) {
    super.onThemeColorsChanged(areTemp, state);
    if (headerView != null) {
      headerView.resetColors(this, null);
    }
  }

  @Override
  public boolean accept (TdApi.Chat chat) {
    if (tdlib.chatAvailable(chat)) {
      Tdlib.RestrictionStatus restrictionStatus = tdlib.getRestrictionStatus(chat, RightId.SEND_BASIC_MESSAGES);
      return restrictionStatus == null || !restrictionStatus.isGlobal() || tdlib.canSendSendSomeMedia(chat, true);
    }
    return false;
  }

  @Override
  protected View onCreateView (Context context) {
    canShareLink = canShareLink();

    headerCell = new DoubleHeaderView(context);
    headerCell.setTitle(R.string.SendTo);
    headerCell.initWithMargin(Screen.dp(56f) * getMenuItemCount(), false);
    headerCell.setThemedTextColor(ColorId.text, ColorId.textLight, this);
    updateHeader();
    if (Settings.instance().chatFoldersEnabled() && TD.isChatListMain(displayingChatList) && tdlib.chatFolderCount() > 0) {
      headerCell.setTitle(R.string.CategoryMain);
      headerCell.setTitleIcon(R.drawable.baseline_keyboard_arrow_down_20);
      headerCell.setOnClickListener(v -> {
        if (!inSearchMode()) {
          showFolderSelector();
        }
      });
      Views.setClickable(headerCell);
    }

    contentView = new RelativeLayout(context);
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    // rootLayout.addView(contentView);

    RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    params.topMargin = HeaderView.getSize(true);
    if (canShareLink) {
      params.addRule(RelativeLayout.ABOVE, R.id.btn_send);
    }
    recyclerView = new CustomRecyclerView(context) {
      @Override
      public boolean onTouchEvent (MotionEvent e) {
        return !(e.getAction() == MotionEvent.ACTION_DOWN && headerView != null && e.getY() < headerView.getTranslationY() - HeaderView.getSize(true)) && super.onTouchEvent(e);
      }

      @Override
      protected void onLayout (boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        post(ShareController.this);
        if (getAdapter() != null) {
          launchOpenAnimation();
        }
        checkHeaderPosition();
        if (awaitLayout) {
          awaitLayout = false;
          autoScroll(awaitScrollBy);
        }
        launchExpansionAnimation();
      }

      @Override
      public void draw (Canvas c) {
        int top = detectTopRecyclerEdge();
        if (top == 0) {
          c.drawColor(Theme.fillingColor());
        } else {
          c.drawRect(0, top, getMeasuredWidth(), getMeasuredHeight(), Paints.fillingPaint(Theme.fillingColor()));
        }
        super.draw(c);
      }

      @Override
      protected void onMeasure (int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        if (getAdapter() != null) {
          launchOpenAnimation();
        }
        checkHeaderPosition();
      }
    };
    addThemeInvalidateListener(recyclerView);
    recyclerView.setItemAnimator(null);
    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
          setAutoScrollFinished(true);
        } else if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
          resetTopEnsuredState();
          hideSoftwareKeyboard();
        }
      }

      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        checkHeaderPosition();
        TdlibChatListSlice displayingList = getDisplayingList();
        if (displayingList != null && displayingList.canLoad() && !inSearchMode()) {
          GridLayoutManager gridManager = (GridLayoutManager) recyclerView.getLayoutManager();
          int i = gridManager.findLastVisibleItemPosition();
          if (i >= adapter.getItemCount() - gridManager.getSpanCount()) {
            displayingList.loadMore(30, null);
          }
        }
      }
    });
    recyclerView.setHasFixedSize(true);
    recyclerView.setLayoutManager(new RtlGridLayoutManager(context, 4));
    recyclerView.setLayoutParams(params);

    final GridLayoutManager.SpanSizeLookup lookup = new GridLayoutManager.SpanSizeLookup() {
      @Override
      public int getSpanSize (int position) {
        return adapter.getItems().get(position).getViewType() == ListItem.TYPE_CHAT_VERTICAL_FULLWIDTH ? 1 : ((GridLayoutManager) recyclerView.getLayoutManager()).getSpanCount();
      }
    };
    decoration = new GridSpacingItemDecoration(calculateSpanCount(), Screen.dp(HORIZONTAL_PADDING_SIZE), true, false, false);
    /*decoration.setNeedDraw(true, SettingItem.TYPE_CHAT_VERTICAL_FULLWIDTH);
    decoration.setDrawColorId(ColorId.filling);*/
    decoration.setSpanSizeLookup(lookup);
    recyclerView.addItemDecoration(decoration);
    recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
      @Override
      public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        outRect.top = 0;
        outRect.bottom = 0;
        int i = parent.getChildAdapterPosition(view);
        if (i != RecyclerView.NO_POSITION) {
          int spanCount = ((GridLayoutManager) parent.getLayoutManager()).getSpanCount();
          if (i < spanCount) {
            int totalSpanCount = 0;
            for (int pos = 0; pos < i && totalSpanCount <= spanCount; pos++) {
              totalSpanCount += lookup.getSpanSize(pos);
            }
            if (i <= spanCount) {
              outRect.top = getContentOffset();
            }
          }
          final int itemCount = adapter.getItemCount();
          int addSize = itemCount % spanCount;
          if (addSize == 0)
            addSize = spanCount;
          if (i >= itemCount - addSize) {
            int rowCount = (int) Math.ceil((float) itemCount / (float) spanCount);
            int itemsHeight = rowCount * Screen.dp(86f) + Screen.dp(HORIZONTAL_PADDING_SIZE) + Screen.dp(VERTICAL_PADDING_SIZE);
            outRect.bottom = Math.max(0, getValue().getMeasuredHeight() == 0 ? Screen.currentHeight() : getValue().getMeasuredHeight() - HeaderView.getSize(true) - itemsHeight - (canShareLink ? Screen.dp(56f) : 0));
          }
        }
      }
    });

    ((GridLayoutManager) recyclerView.getLayoutManager()).setSpanSizeLookup(lookup);

    contentView.addView(recyclerView);

    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(56f));
    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
    sendButton = new SendButton(context);
    sendButton.setLayoutParams(params);
    sendButton.setId(R.id.btn_send);
    if (canShareLink) {
      sendButton.setShareText(getShareButtonText());
    }
    sendButton.getChildAt(0).setId(R.id.btn_send);
    sendButton.getChildAt(0).setOnClickListener(this);
    sendButton.getChildAt(0).setOnLongClickListener(v -> {
      if (selectedChats.size() > 0) {
        sendMessages(true, false, getArgumentsStrict().defaultSendOptions);
        return true;
      }
      return false;
    });
    switch (mode) {
      case MODE_MESSAGES: {
        // sendButton.getChildAt(0).setOnLongClickListener(this);
        break;
      }
    }
    if (!canShareLink) {
      sendButton.setTranslationY(Screen.dp(56f));
      sendButton.setIsReady(true, false);
    }
    addThemeInvalidateListener(sendButton);

    sendMenu = new HapticMenuHelper(view -> {
      if (selectedChats.size() == 0)
        return null;
      List<HapticMenuHelper.MenuItem> items = null;
      if (selectedChats.size() == 1) {
        items = tdlib.ui().fillDefaultHapticMenu(selectedChats.valueAt(0).getChatId(), false, false, !forceSendWithoutSound, true);
        if (items == null)
          items = new ArrayList<>(1);
        items.add(new HapticMenuHelper.MenuItem(R.id.btn_sendAndOpen, Lang.getString(R.string.SendAndOpen), R.drawable.baseline_forward_24));
      } else {
        boolean hasSecretChats = false;
        for (int i = 0; i < selectedChats.size(); i++) {
          TGFoundChat chat = selectedChats.valueAt(i);
          if (chat.isSecret()) {
            hasSecretChats = true;
            break;
          }
        }
        if (!hasSecretChats) {
          items = new ArrayList<>(2);
          items.add(new HapticMenuHelper.MenuItem(R.id.btn_sendScheduled, Lang.getString(R.string.SendSchedule), R.drawable.baseline_date_range_24));
          if (!forceSendWithoutSound) {
            items.add(new HapticMenuHelper.MenuItem(R.id.btn_sendNoSound, Lang.getString(R.string.SendNoSound), R.drawable.baseline_notifications_off_24));
          }
        }
      }
      if (needShareSettings()) {
        if (items == null)
          items = new ArrayList<>();
        items.add(new HapticMenuHelper.MenuItem(R.id.btn_settings, Lang.getString(R.string.MoreForwardOptions), R.drawable.baseline_more_horiz_24).bindTutorialFlag(Settings.TUTORIAL_FORWARD_COPY));
      }
      return items;
    }, (view, parentView, item) -> {
      if (selectedChats == null || selectedChats.size() == 0)
        return false;
      TdApi.MessageSendOptions defaultSendOptions = getArgumentsStrict().defaultSendOptions;
      boolean needHideKeyboard = parentView.getId() == R.id.btn_done;
      final int viewId = view.getId();
      if (viewId == R.id.btn_settings) {
        showShareSettings();
      } else if (viewId == R.id.btn_sendScheduled) {
        tdlib.ui().showScheduleOptions(this, selectedChats.size() == 1 ? selectedChats.valueAt(0).getChatId() : 0, false, (sendOptions, disableMarkdown) -> performSend(needHideKeyboard, sendOptions, false), defaultSendOptions, null);
      } else if (viewId == R.id.btn_sendOnceOnline) {
        performSend(needHideKeyboard, Td.newSendOptions(defaultSendOptions, new TdApi.MessageSchedulingStateSendWhenOnline()), false);
      } else if (viewId == R.id.btn_sendNoSound) {
        performSend(needHideKeyboard, Td.newSendOptions(defaultSendOptions, true), false);
      } else if (viewId == R.id.btn_sendAndOpen) {
        performSend(needHideKeyboard, Td.newSendOptions(defaultSendOptions), true);
      }
      return true;
    }, getThemeListeners(), null).attachToView(sendButton.getChildAt(0));

    // Bottom wrap
    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.addRule(RelativeLayout.ABOVE, R.id.btn_send);
    bottomWrap = new LinearLayout(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        checkCommentPosition();
      }

      @Override
      protected void onLayout (boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        checkCommentPosition();
      }
    };
    bottomWrap.setLayoutParams(params);
    bottomWrap.setId(R.id.share_bottom);
    bottomWrap.setOrientation(LinearLayout.VERTICAL);
    contentView.addView(bottomWrap);

    inputView = new InputView(context, tdlib, this) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        checkButtonsPosition();
      }

      @Override
      protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        checkButtonsPosition();
      }

      @Override
      public boolean onTouchEvent (MotionEvent event) {
        boolean r = super.onTouchEvent(event);
        if (textFormattingLayout != null) {
          textFormattingLayout.onInputViewTouchEvent(event);
        }
        return r;
      }
    };
    inputView.setInputListener(new InputView.InputListener() {
      @Override
      public void onInputChanged (InputView v, String input) {
        if (emojiLayout != null) {
          emojiLayout.onTextChanged(input);
        }
      }

      @Override
      public boolean canSearchInline (InputView v) {
        return false;
      }

      @Override
      public TdApi.Chat provideInlineSearchChat (InputView v) {
        return null;
      }

      @Override
      public long provideInlineSearchChatId (InputView v) {
        return 0;
      }

      @Override
      public long provideInlineSearchChatUserId (InputView v) {
        return 0;
      }

      @Override
      public void showInlineResults (InputView v, ArrayList<InlineResult<?>> items, boolean isContent) {

      }

      @Override
      public void addInlineResults (InputView v, ArrayList<InlineResult<?>> items) {

      }
    });
    inputView.setEnabled(false);
    inputView.setGravity(Gravity.LEFT | Gravity.BOTTOM);
    inputView.setTypeface(Fonts.getRobotoRegular());
    inputView.setTextColor(Theme.textAccentColor());
    addThemeTextAccentColorListener(inputView);
    inputView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f);
    inputView.setPadding(Screen.dp(60f), Screen.dp(12f), Screen.dp(55f), Screen.dp(12f));
    inputView.setHintTextColor(Theme.textPlaceholderColor());
    addThemeHintTextColorListener(inputView, ColorId.textPlaceholder);
    ViewSupport.setThemedBackground(inputView, ColorId.filling, this);
    inputView.setHighlightColor(Theme.fillingTextSelectionColor());
    addThemeHighlightColorListener(inputView, ColorId.textSelectionHighlight);
    inputView.setMinimumHeight(Screen.dp(48f));
    inputView.setHint(Lang.getString(R.string.AddComment));
    inputView.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
    inputView.setInputType(inputView.getInputType() | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);
    inputView.setSingleLine(false);
    inputView.setMaxLines(4);
    inputView.setSelectionChangeListener(this);
    inputView.setSpanChangeListener(this::onInputSpansChanged);
    bottomWrap.addView(inputView);

    if (OPEN_KEYBOARD_WITH_AUTOSCROLL) {
      params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(48f));
      params.leftMargin = inputView.getPaddingLeft();
      params.addRule(RelativeLayout.ABOVE, R.id.btn_send);
      stubInputView = new View(context);
      stubInputView.setId(R.id.share_comment_stub);
      stubInputView.setOnClickListener(this);
      stubInputView.setLayoutParams(params);
      contentView.addView(stubInputView);
    }

    // Buttons

    params = new RelativeLayout.LayoutParams(Screen.dp(55f), Screen.dp(48f));
    params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
    params.addRule(RelativeLayout.ALIGN_TOP, R.id.share_bottom);

    okButton = new CustomImageView(context);
    okButton.setId(R.id.btn_done);
    okButton.setScaleType(ImageView.ScaleType.CENTER);
    okButton.setImageResource(R.drawable.deproko_baseline_send_24);
    okButton.setColorFilter(Theme.chatSendButtonColor());
    addThemeFilterListener(okButton, ColorId.chatSendButton);
    okButton.setVisibility(View.INVISIBLE);
    okButton.setOnClickListener(this);
    okButton.setLayoutParams(params);
    contentView.addView(okButton);
    sendMenu.attachToView(okButton);

    params = new RelativeLayout.LayoutParams(Screen.dp(60f), Screen.dp(48f));
    params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
    params.addRule(RelativeLayout.ALIGN_TOP, R.id.share_bottom);

    emojiButton = new CustomImageView(context);
    emojiButton.setScaleType(ImageView.ScaleType.CENTER);
    emojiButton.setId(R.id.btn_emoji);
    emojiButton.setOnClickListener(this);
    emojiButton.setImageResource(R.drawable.deproko_baseline_insert_emoticon_26);
    emojiButton.setColorFilter(Theme.iconColor());
    addThemeFilterListener(emojiButton, ColorId.icon);
    emojiButton.setLayoutParams(params);
    contentView.addView(emojiButton);

    // Send

    contentView.addView(sendButton);

    // Shadow

    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.addRule(RelativeLayout.ABOVE, R.id.share_bottom);
    bottomShadow = SeparatorView.simpleSeparator(context, params, false);
    bottomShadow.setColorId(ColorId.shareSeparator);
    bottomShadow.setAlignBottom();
    if (canShareLink) {
      bottomShadow.setTranslationY(Screen.dp(48f));
    } else {
      bottomShadow.setAlpha(0f);
    }
    bottomShadow.setLayoutParams(params);
    contentView.addView(bottomShadow);

    adapter = new SettingsAdapter(this) {
      @Override
      protected void setChatData (ListItem item, VerticalChatView chatView) {
        TGFoundChat chat = (TGFoundChat) item.getData();
        chatView.setPreviewActionListProvider(ShareController.this);
        chatView.setChat(chat);
        chatView.setIsChecked(isChecked(chat.getAnyId()), false);
      }
    };
    adapter.setNoEmptyProgress();
    TGLegacyManager.instance().addEmojiListener(adapter);

    headerView = new HeaderView(context);
    headerView.initWithSingleController(this, false);
    headerView.getFilling().setShadowAlpha(0f);
    headerView.getBackButton().setIsReverse(true);
    getSearchHeaderView(headerView);

    // Fix blinking black line between header & content
    FrameLayout.LayoutParams fp = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(6f));
    fp.topMargin = HeaderView.getSize(false) - Screen.dp(3f);
    fixView = new View(context);
    ViewSupport.setThemedBackground(fixView, ColorId.filling, this);
    fixView.setLayoutParams(fp);

    wrapView = new FrameLayoutFix(context);
    wrapView.addView(fixView);
    wrapView.addView(contentView);
    chatSearchView = generateChatSearchView(wrapView);
    ((ViewGroup.MarginLayoutParams) chatSearchView.getLayoutParams()).topMargin = HeaderView.getSize(true);
    wrapView.addView(headerView);
    if (HeaderView.getTopOffset() > 0) {
      lickView = new LickView(context);
      addThemeInvalidateListener(lickView);
      lickView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, HeaderView.getTopOffset()));
      wrapView.addView(lickView);
    }

    checkCommentPosition();

    // Load chats

    initializeChatList(displayingChatList);

    return wrapView;
  }

  private void initializeChatList (TdApi.ChatList chatList) {
    TdlibChatListSlice list = new TdlibChatListSlice(tdlib, chatList, this, true) {
      @Override
      protected boolean modifySlice (List<Entry> slice, int currentSize) {
        int index = 0;
        for (Entry entry : slice) {
          if (tdlib.isSelfChat(entry.chat)) {
            if (currentSize > 0) {
              slice.remove(index);
              return true;
            } else if (index == 0 || ChatPosition.isPinned(entry.chat, chatList())) {
              return false;
            } else {
              slice.remove(index);
              entry.bringToTop();
              slice.add(0, entry);
              return true;
            }
          }
          index++;
        }
        if (currentSize == 0 && TD.isChatListMain(chatList())) {
          TdApi.Chat selfChat = tdlib.selfChat();
          if (selfChat != null && !ChatPosition.isPinned(selfChat, chatList())) {
            Entry entry = new Entry(selfChat, chatList(), ChatPosition.findPosition(selfChat, chatList()), true);
            entry.bringToTop();
            slice.add(0, entry);
            return true;
          }
          bringToTop(tdlib.selfChatId(), () -> new TdApi.CreatePrivateChat(tdlib.myUserId(), false), null);
        }
        return false;
      }
    };
    // FIXME replace Math.max with proper fix.
    int startLoadCount = Math.max(20, Screen.calculateLoadingItems(Screen.dp(95f), 1) * calculateSpanCount());
    list.initializeList(this, entries -> processChats(list.chatList(), entries), startLoadCount, this::executeScheduledAnimation);
    bindListToChatList(chatList, list);
  }

  private boolean isDisplayingChatList (@Nullable TdApi.ChatList chatList) {
    return Td.equalsTo(chatList, displayingChatList);
  }

  @Override
  public void onChatAdded (TdlibChatList chatList, TdApi.Chat chat, int atIndex, Tdlib.ChatChange changeInfo) {
    runOnUiThreadOptional(() -> {
      List<TGFoundChat> displayingChats = getChatsByChatList(chatList.chatList());
      if (displayingChats != null) {
        TGFoundChat parsedChat = newChat(chatList.chatList(), chat);
        displayingChats.add(atIndex, parsedChat);
        if (isDisplayingChatList(chatList.chatList())) {
          if (isEmptyItem(adapter.getItem(0))) {
            adapter.removeItem(0);
          }
          adapter.addItem(atIndex, valueOfChat(parsedChat));
          recyclerView.invalidateItemDecorations();
        }
      }
    });
  }

  @Override
  public void onChatRemoved (TdlibChatList chatList, TdApi.Chat chat, int fromIndex, Tdlib.ChatChange changeInfo) {
    runOnUiThreadOptional(() -> {
      List<TGFoundChat> displayingChats = getChatsByChatList(chatList.chatList());
      if (displayingChats != null) {
        displayingChats.remove(fromIndex);
        if (isDisplayingChatList(chatList.chatList())) {
          adapter.removeItem(fromIndex);
          recyclerView.invalidateItemDecorations();
        }
      }
    });
  }

  @Override
  public void onChatMoved (TdlibChatList chatList, TdApi.Chat chat, int fromIndex, int toIndex, Tdlib.ChatChange changeInfo) {
    runOnUiThreadOptional(() -> {
      List<TGFoundChat> displayingChats = getChatsByChatList(chatList.chatList());
      if (displayingChats != null) {
        TGFoundChat entry = displayingChats.remove(fromIndex);
        displayingChats.add(toIndex, entry);
        if (isDisplayingChatList(chatList.chatList())) {
          LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
          int savedPosition, savedOffset;
          if (manager != null) {
            savedPosition = manager.findFirstVisibleItemPosition();
            View view = manager.findViewByPosition(savedPosition);
            savedOffset = view != null ? manager.getDecoratedTop(view) : 0;
          } else {
            savedPosition = RecyclerView.NO_POSITION;
            savedOffset = 0;
          }

          adapter.moveItem(fromIndex, toIndex);
          recyclerView.invalidateItemDecorations(); // TODO detect only first-non-first row changes
          if (savedPosition != RecyclerView.NO_POSITION) {
            manager.scrollToPositionWithOffset(savedPosition, savedOffset);
          }
        }
      }
    });
  }

  private TGFoundChat newChat (TdApi.ChatList chatList, TdApi.Chat rawChat) {
    TGFoundChat chat = new TGFoundChat(tdlib, chatList, rawChat, false, null);
    chat.setNoUnread();
    chat.setNoSubscription();
    return chat;
  }

  private void processChats (TdApi.ChatList chatList, List<TdlibChatListSlice.Entry> entries) {
    final List<TGFoundChat> result = new ArrayList<>(entries.size());
    for (TdlibChatList.Entry entry : entries) {
      result.add(newChat(chatList, entry.chat));
    }
    runOnUiThreadOptional(() ->
      displayChats(chatList, result)
    );
  }

  @Override
  public ForceTouchView.ActionListener onCreateActions (final View v, ForceTouchView.ForceTouchContext context, IntList ids, IntList icons, StringList strings, ViewController<?> target) {
    return null;  // Deprecated
  }

  @Override
  public ForceTouchView.ActionListener onCreateActions (View v, ForceTouchView.ForceTouchContext context, ArrayList<BaseView.ActionItem> items, ViewController<?> target) {
    final ListItem item = (ListItem) v.getTag();
    final TGFoundChat chat = (TGFoundChat) item.getData();
    final TdApi.Chat tdChat = chat.getChat();

    context.setExcludeHeader(true);
    context.setShrunkenFooter(true);

    if (tdChat != null && tdChat.messageSenderId != null) {
      if (tdlib.isSelfSender(tdChat.messageSenderId)) {
        items.add(new BaseView.ActionItem(R.id.btn_openSendersMenu, R.drawable.dot_baseline_acc_personal_24, Lang.getString(R.string.SendAsOption)));
      } else if (!tdlib.isChannel(tdChat.messageSenderId)) {
        items.add(new BaseView.ActionItem(R.id.btn_openSendersMenu, R.drawable.dot_baseline_acc_anon_24, Lang.getString(R.string.SendAsOption)));
      } else {
        items.add(new BaseView.ActionItem(R.id.btn_openSendersMenu, Lang.getString(R.string.SendAsOption), tdChat.messageSenderId));
      }
    }

    items.add(new BaseView.ActionItem(R.id.btn_selectChat, R.drawable.baseline_playlist_add_check_24, Lang.getString(isChecked(chat.getAnyId()) ? R.string.Unselect : R.string.Select)));

    return new ForceTouchView.ActionListener() {
      @Override
      public void onForceTouchAction (ForceTouchView.ForceTouchContext context, int actionId, Object arg) {
        if (actionId == R.id.btn_openSendersMenu) {
          MessagesController.getChatAvailableMessagesSenders(tdlib, tdChat.id, result -> {
            final SetSenderController c = new SetSenderController(ShareController.this.context, tdlib);
            c.setArguments(new SetSenderController.Args(tdChat, result.senders, tdChat.messageSenderId));
            c.setDelegate(newSender -> MessagesController.setNewMessageSender(tdlib, tdChat.id, newSender, () -> {
              chat.updateChat();
              ((VerticalChatView) v).onChatDefaultMessageSenderIdChanged(chat.getChatId(), newSender.sender);
            }));
            c.show();
          });
          return;
        }

        toggleChecked(v, chat, isChecked ->
          ((VerticalChatView) v).setIsChecked(isChecked, true)
        );
      }

      @Override
      public void onAfterForceTouchAction (ForceTouchView.ForceTouchContext context, int actionId, Object arg) {

      }
    };
  }

  @Override
  protected boolean canSelectFoundChat (TGFoundChat chat) {
    return true;
  }

  private int detectTopRecyclerEdge () {
    GridLayoutManager manager = (GridLayoutManager) recyclerView.getLayoutManager();
    int first = manager.findFirstVisibleItemPosition();
    int spanCount = manager.getSpanCount();
    int top = 0;
    if (first != -1 && first < spanCount) {
      View topView = manager.findViewByPosition(first);
      if (topView != null) {
        final int topViewPos = topView.getTop() - Screen.dp(VERTICAL_PADDING_SIZE);
        if (topViewPos > 0) {
          int totalSpanCount = 0;
          for (int pos = 0; pos < first && totalSpanCount <= spanCount; pos++) {
            totalSpanCount += manager.getSpanSizeLookup().getSpanSize(pos);
          }
          if (totalSpanCount <= spanCount) {
            top = topViewPos;
          }
        }
      }
    }
    return top;
  }

  // Emoji tones

  @Override
  public int[] displayBaseViewWithAnchor (EmojiToneHelper context, View anchorView, View viewToDisplay, int viewWidth, int viewHeight, int horizontalMargin, int horizontalOffset, int verticalOffset) {
    return EmojiToneHelper.defaultDisplay(context, anchorView, viewToDisplay, viewWidth, viewHeight, horizontalMargin, horizontalOffset, verticalOffset, contentView, bottomWrap, keyboardFrameLayout);
  }

  @Override
  public void removeView (EmojiToneHelper context, View displayedView) {
    contentView.removeView(displayedView);
  }

  // Other

  @Override
  public void hideSoftwareKeyboard () {
    super.hideSoftwareKeyboard();
    Keyboard.hide(inputView);
    forceCloseEmojiKeyboard();
  }

  private boolean needPostponeAutoScroll = false;

  @Override
  protected boolean onFoundChatClick (View view, TGFoundChat chat) {
    if (chat.getId() == 0) {
      chat = new TGFoundChat(tdlib, chat.getList(), chat.getAnyId(), false);
    }
    final long chatId = chat.getAnyId();

    final TGFoundChat finalChat = chat;
    final RunnableBool after = (b) -> {
      if (b) {
        onFoundChatClickAfter(finalChat);
      }
    };

    if (!isChecked(chatId)) {
      if (processSingleTap(chat))
        return true;
      if (!toggleChecked(view, chat, after))
        return true;
    } else {
      onFoundChatClickAfter(chat);
    }
    return true;
  }

  private void onFoundChatClickAfter (TGFoundChat chat) {
    int i = adapter.indexOfViewByLongId(chat.getAnyId());
    if (i != -1) {
      View itemView = recyclerView.getLayoutManager().findViewByPosition(i);
      if (itemView != null) {
        ((VerticalChatView) itemView).setIsChecked(true, false);
      } else {
        adapter.notifyItemChanged(i);
      }
    }
    TdApi.Chat selectedChat = chat.getChat();
    if (selectedChat != null) {
      Runnable after = () -> runOnUiThreadOptional(() -> {
        needPostponeAutoScroll = true;
        closeSearchMode(null);
        needPostponeAutoScroll = false;
      });
      for (TdlibChatListSlice list : listByChatList.values()) {
        TdApi.ChatPosition position = ChatPosition.findPosition(selectedChat, list.chatList());
        if (position != null && position.order != 0) {
          Runnable doAfter;
          if (isDisplayingChatList(list.chatList())) {
            doAfter = after; after = null;
          } else {
            doAfter = null;
          }
          list.bringToTop(chat.getAnyId(), null, doAfter);
        }
      }
      if (after != null) {
        after.run();
      }
    }
  }

  @Override
  protected boolean canInteractWithFoundChat (TGFoundChat chat) {
    return false;
  }

  private static final float HORIZONTAL_PADDING_SIZE = 8f;
  private static final float VERTICAL_PADDING_SIZE = 4f;
  private boolean autoScrollFinished = true, openKeyboardUponFinishingAutoScroll;

  private View lastFirstView;

  private void checkHeaderPosition () {
    View view = lastFirstView;
    if (view == null || recyclerView.getChildAdapterPosition(view) != 0) {
      view = lastFirstView = recyclerView.getLayoutManager().findViewByPosition(0);
    }
    int top = HeaderView.getTopOffset();
    if (view != null) {
      top = Math.max(view.getTop() - HeaderView.getSize(false) + recyclerView.getTop() - Screen.dp(VERTICAL_PADDING_SIZE), HeaderView.getTopOffset());
    }
    if (!appliedRecyclerMargin && expandFactor != 0f) {
      top = Math.max(HeaderView.getTopOffset(), (int) (top + calculateRecyclerOffset()));
    }
    if (headerView != null) {
      headerView.setTranslationY(top);
      fixView.setTranslationY(top);
      final int topOffset = HeaderView.getTopOffset();
      top -= topOffset;
      checkInputEnabled(top);
      chatSearchView.setTranslationY(top);
      if (lickView != null) {
        lickView.setTranslationY(top);
        float factor = top > topOffset ? 0f : 1f - ((float) top / (float) topOffset);
        lickView.setFactor(factor);
        headerView.getFilling().setShadowAlpha(factor);
      }
      if (top == 0 && needUpdateSearchMode) {
        super.updateSearchMode(true);
        needUpdateSearchMode = false;
      } else if (autoScrollFinished && top > 0 && inSearchMode() && getSearchTransformFactor() == 1f) {
        recyclerView.scrollBy(0, top);
      }
      if (folderSelectorLayout != null) {
        View boundView = folderSelectorLayout.getBoundView();
        if (boundView != null && boundView.getTranslationY() != headerView.getTranslationY()) {
          boundView.setTranslationY(headerView.getTranslationY());
          boundView.requestLayout();
        }
      }
    }
  }

  private boolean preventAutoScroll;

  private final LongSparseArray<TGFoundChat> selectedChats = new LongSparseArray<>();
  private final LongList selectedChatIds = new LongList(10);

  private boolean isChecked (long chatId) {
    return selectedChats.get(chatId) != null;
  }

  private boolean hasSelectedAnything;

  private boolean processSingleTap (TGFoundChat chat) {
    if (!hasSelectedAnything && chat.isSelfChat() && selectedChats.size() == 0) {
      selectedChats.put(chat.getAnyId(), chat);
      selectedChatIds.append(chat.getAnyId());
      sendMessages(false, true, null);
      return true;
    }
    return false;
  }

  private boolean hasVoiceOrVideoMessageContent () {
    Args args = getArgumentsStrict();
    switch (mode) {
      case MODE_TELEGRAM_FILES: {
        for (MediaItem item : args.telegramFiles) {
          TdApi.InputMessageContent content = item.createShareContent();
          if (content.getConstructor() == TdApi.InputMessageVoiceNote.CONSTRUCTOR ||
            content.getConstructor() == TdApi.InputMessageVideoNote.CONSTRUCTOR) {
            return true;
          }
        }
        break;
      }
      case MODE_MESSAGES: {
        for (TdApi.Message message : args.messages) {
          if (Td.isVoiceNote(message.content) || Td.isVideoNote(message.content)) {
            return true;
          }
        }
        break;
      }
      case MODE_CUSTOM_CONTENT: {
        return
          Td.isVoiceNote(args.customContent) || Td.isVideoNote(args.customContent);
      }
    }
    return false;
  }

  private CharSequence getErrorMessage (long chatId) {
    Args args = getArgumentsStrict();
    TdApi.Chat chat = tdlib.chatStrict(chatId);

    CharSequence slowModeRestrictionText = tdlib().getSlowModeRestrictionText(chatId);
    if (slowModeRestrictionText != null) {
      return slowModeRestrictionText;
    }

    switch (mode) {
      case MODE_TEXT: {
        return tdlib.getBasicMessageRestrictionText(chat);
      }
      case MODE_FILES: {
        return tdlib.getDefaultRestrictionText(chat, RightId.SEND_DOCS);
      }
      case MODE_STICKER: {
        return tdlib.getStickerRestrictionText(chat);
      }
      case MODE_CUSTOM: {
        return args.customDelegate.generateErrorMessageForChat(chatId);
      }
      case MODE_CUSTOM_CONTENT: {
        return tdlib.getRestrictionText(chat, args.customContent);
      }
      case MODE_TELEGRAM_FILES: {
        for (MediaItem item : args.telegramFiles) {
          CharSequence restrictionText = tdlib.getRestrictionText(chat, item.createShareContent());
          if (restrictionText != null)
            return restrictionText;
        }
        break;
      }
      case MODE_MESSAGES: {
        for (TdApi.Message message : args.messages) {
          if (ChatId.isSecret(chatId)) {
            if (!TD.canSendToSecretChat(message.content))
              return Lang.getString(R.string.SecretChatForwardError);
            TdApi.ForwardMessages function = new TdApi.ForwardMessages(chatId, 0, message.chatId, new long[] {message.id}, new TdApi.MessageSendOptions(false, false, false, false, null, 0, 0, true), needHideAuthor, needRemoveCaptions);
            TdApi.Object check = tdlib.clientExecute(function, 1000L);
            if (check instanceof TdApi.Error) {
              return TD.toErrorString(check);
            }
          }

          if (message.content.getConstructor() == TdApi.MessagePoll.CONSTRUCTOR && !((TdApi.MessagePoll) message.content).poll.isAnonymous && tdlib.isChannel(chatId))
            return Lang.getString(R.string.PollPublicForwardHint);

          if (Td.isVoiceNote(message.content) || Td.isVideoNote(message.content)) {
            CharSequence restrictionText = tdlib.getVoiceVideoRestrictionText(chat, Td.isVideoNote(message.content));
            if (restrictionText != null)
              return restrictionText;
          }

          CharSequence restrictionText = tdlib.getRestrictionText(chat, message);
          if (restrictionText != null)
            return restrictionText;
        }
        break;
      }
    }
    return null;
  }

  @Nullable
  private View findVisibleChatView (long chatId) {
    int i = adapter.indexOfViewByLongId(chatId);
    if (i != -1) {
      int firstVisiblePosition = ((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
      int lastVisiblePosition = ((LinearLayoutManager) recyclerView.getLayoutManager()).findLastVisibleItemPosition();
      if (i >= firstVisiblePosition && i <= lastVisiblePosition) {
        return recyclerView.getLayoutManager().findViewByPosition(i);
      }
    }
    return null;
  }

  private boolean showErrorMessage (View anchorView, long chatId, boolean includeTitle) {
    CharSequence errorMessage = getErrorMessage(chatId);
    if (errorMessage != null) {
      if (anchorView == null) {
        anchorView = findVisibleChatView(chatId);
        includeTitle = includeTitle && anchorView == null;
      }
      if (anchorView == null && isExpanded) {
        anchorView = isSendHidden ? okButton : sendButton;
      }
      if (includeTitle) {
        errorMessage = Lang.getString(R.string.format_chatAndError, (target, argStart, argEnd, argIndex, needFakeBold) -> argIndex == 0 ? Lang.boldCreator().onCreateSpan(target, argStart, argEnd, argIndex, needFakeBold) : null, tdlib.chatTitle(chatId), errorMessage);
      }
      if (anchorView != null) {
        context().tooltipManager().builder(anchorView).icon(R.drawable.baseline_warning_24).show(tdlib, errorMessage).hideDelayed();
      } else {
        UI.showToast(errorMessage, Toast.LENGTH_SHORT);
      }
      return true;
    }
    return false;
  }

  private boolean toggleChecked (View view, TGFoundChat chat, RunnableBool after) {
    return toggleCheckedImpl(view, chat, after, true);
  }

  private final LongSet lockedChatIds = new LongSet();

  private boolean toggleCheckedImpl (View view, TGFoundChat chat, @Nullable RunnableBool after, boolean performAsyncChecks) {
    long chatId = chat.getAnyId();
    if (lockedChatIds.has(chatId)) {
      return false;
    }

    boolean result = !isChecked(chatId);

    if (result) {
      if (performAsyncChecks) {
        // FIXME: view recycling safety
        // By the time `after` is called, initial view could have been already recycled.
        // Current implementation relies on the quick response from GetUserFull,
        // however, there's a chance `view` could have been already taken by some other view.
        // Should be fixed inside `after` contents.
        if (ChatId.isUserChat(chatId) && hasVoiceOrVideoMessageContent()) {
          lockedChatIds.add(chatId);
          tdlib.cache().userFull(tdlib.chatUserId(chatId), userFullInfo -> {
            lockedChatIds.remove(chatId);
            toggleCheckedImpl(view, chat, after, false);
          });
          return false;
        } else if (ChatId.isSupergroup(chatId)) {
          lockedChatIds.add(chatId);
          tdlib.cache().supergroupFull(ChatId.toSupergroupId(chatId), supergroupFullInfo -> {
            lockedChatIds.remove(chatId);
            toggleCheckedImpl(view, chat, after, false);
          });
          return false;
        }
      }
      if (showErrorMessage(view, chatId, false)) {
        result = false;
      }
    }

    if (result) {
      selectedChats.put(chatId, chat);
      selectedChatIds.append(chatId);
      hasSelectedAnything = true;
    } else {
      selectedChats.remove(chatId);
      selectedChatIds.remove(chatId);
    }
    checkAbilityToSend();
    updateHeader();
    if (after != null) {
      after.runWithBool(result);
    }
    return result;
  }

  private void updateHeader () {
    if (selectedChats.size() == 0) {
      headerCell.setSubtitle(Lang.lowercase(Lang.getString(R.string.SelectChats)));
    } else if (selectedChats.size() == 1) {
      headerCell.setSubtitle(selectedChats.valueAt(0).getFullTitle());
    } else {
      final int size = selectedChatIds.size();
      int count = 0;
      final int limit = getMenuItemCount() > 1 ? 2 : 3;
      List<String> names = new ArrayList<>();
      int others = 0;
      for (int i = 0; i < size; i++) {
        long chatId = selectedChatIds.get(i);
        TGFoundChat chat = selectedChats.get(chatId);
        if (count == limit - 1 && size > limit) {
          others = size - count;
          break;
        }
        if (chat.isSelfChat()) {
          names.add(Lang.getString(R.string.SavedMessages));
        } else {
          names.add(chat.getSingleLineTitle().toString());
        }
        count++;
      }
      headerCell.setSubtitle(Lang.pluralChatTitles(names, others));
    }
  }

  private static final boolean OPEN_KEYBOARD_WITH_AUTOSCROLL = false;
  private boolean sendOnKeyboardClose;
  private TdApi.MessageSendOptions sendOnKeyboardCloseSendOptions;
  private boolean sendOnKeyboardCloseGoToChat;

  private void performSend (boolean needHideKeyboard, TdApi.MessageSendOptions finalSendOptions, boolean forceGoToChat) {
    if (needHideKeyboard) {
      if (!sendOnKeyboardClose) {
        sendOnKeyboardClose = true;
        sendOnKeyboardCloseSendOptions = finalSendOptions;
        sendOnKeyboardCloseGoToChat = forceGoToChat;
        hideSoftwareKeyboard();
      }
    } else {
      sendMessages(forceGoToChat, false, finalSendOptions);
    }
  }

  private boolean selectedSelfChatOnly () {
    return selectedChats.size() == 1 && selectedChats.valueAt(0).isSelfChat();
  }

  @Override
  public void onClick (View v) {
    final int viewId = v.getId();
    if (viewId == R.id.btn_send) {
      if (selectedChats.size() == 0) {
        if (canShareLink) {
          shareLink();
          // copyLink();
        }
      } else if (inputView != null && !selectedSelfChatOnly() && !tdlib.hasPremium() && inputView.hasOnlyPremiumFeatures()) {
        context().tooltipManager().builder(sendButton)
          .show(tdlib, Strings.buildMarkdown(this, Lang.getString(R.string.MessageContainsPremiumFeatures), null))
          .hideDelayed();
      } else {
        performSend(false, Td.newSendOptions(), false);
      }
    } else if (viewId == R.id.btn_done) {
      if (inputView != null && !selectedSelfChatOnly() && !tdlib.hasPremium() && inputView.hasOnlyPremiumFeatures()) {
        context().tooltipManager().builder(okButton)
          .show(tdlib, Strings.buildMarkdown(this, Lang.getString(R.string.MessageContainsPremiumFeatures), null))
          .hideDelayed();
      } else if (selectedChats.size() == 0) {
        hideSoftwareKeyboard();
      } else {
        performSend(true, Td.newSendOptions(), false);
      }
    } else if (viewId == R.id.share_comment_stub) {
      if (OPEN_KEYBOARD_WITH_AUTOSCROLL) {
        final int top = getTopEdge();
        openKeyboardUponFinishingAutoScroll = true;
        smoothScrollBy(top);
      }
    } else if (viewId == R.id.btn_emoji) {
      processEmojiClick();
    } else {
      ListItem item = (ListItem) v.getTag();
      TGFoundChat chat = (TGFoundChat) item.getData();
      final int itemId = item.getId();
      if (itemId == R.id.chat) {
        if (autoScrollFinished) {
          if (!processSingleTap(chat)) {
            toggleChecked(v, chat, isChecked ->
              ((VerticalChatView) v).setIsChecked(isChecked, true)
            );
          }
        }
      } else if (itemId == R.id.search_chat) {
        if (!processSingleTap(chat)) {
          toggleChecked(v, chat, isChecked ->
            ((VerticalChatView) v).setIsChecked(isChecked, true)
          );
          closeSearchMode(null);
        }
      }
    }
  }

  private void setSmoothScrollFactor (float factor) {
    if (!autoScrollFinished && currentScrollBy != 0) {
      int scrollBy = (int) ((float) currentScrollBy * factor);
      int by = scrollBy - lastScrollBy;
      recyclerView.scrollBy(0, by);
      lastScrollBy = scrollBy;
    }
  }

  private int currentScrollBy, lastScrollBy;

  private int totalScrollingBy;
  private boolean autoScrolling;

  private static final boolean PREVENT_HEADER_ANIMATOR = false; // TODO

  @Override
  protected boolean launchCustomHeaderTransformAnimator (boolean open, int transformMode, Animator.AnimatorListener listener) {
    return PREVENT_HEADER_ANIMATOR && open && getTopEdge() > 0;
  }

  private void smoothScrollBy (int y) {
    if (y == 0) {
      recyclerView.stopScroll();
    }
    setAutoScrollFinished(false);
    /*FIXME replace with
    currentScrollBy = y;
    lastScrollBy = 0;*/
    if (PREVENT_HEADER_ANIMATOR) {
      totalScrollingBy = y;
      autoScrolling = true;
    }
    recyclerView.smoothScrollBy(0, y);
  }

  @Override
  protected Interpolator getSearchTransformInterpolator () {
    return AnimatorUtils.DECELERATE_INTERPOLATOR;
  }

  @Override
  protected long getSearchTransformDuration () {
    return 220l;
  }

  private int calculateSpanCount () {
    if (UI.isLandscape()) {
      int itemWidth = Screen.smallestSide() / 4;
      return Screen.currentWidth() / itemWidth;
    } else {
      return 4;
    }
  }

  @Override
  public void run () {
    int spanCount = calculateSpanCount();
    GridLayoutManager manager = (GridLayoutManager) recyclerView.getLayoutManager();
    if (manager.getSpanCount() != spanCount) {
      manager.setSpanCount(spanCount);
      decoration.setSpanCount(spanCount);
      recyclerView.invalidateItemDecorations();
    }
  }

  private int getTopEdge () {
    return Math.max(0, (int) (headerView.getTranslationY() - HeaderView.getTopOffset()));
  }

  private int awaitingChatSearchOpen;

  @Override
  protected void onChatSearchOpenStarted () {
    if (awaitingChatSearchOpen > 0) {
      final int scrollBy = awaitingChatSearchOpen;
      awaitingChatSearchOpen = 0;
      runOnUiThread(() -> smoothScrollBy(scrollBy), 50);
      awaitingChatSearchOpen = 0;
    }
  }

  @Override
  protected void onEnterSearchMode () {
    super.onEnterSearchMode();
    final int top = getTopEdge();
    if (top > 0) {
      awaitingChatSearchOpen = top;
    } else {
      setAutoScrollFinished(true);
    }
    hideFolderSelector();
  }

  private void setAutoScrollFinished (boolean isFinished) {
    if (this.autoScrollFinished != isFinished) {
      this.autoScrollFinished = isFinished;
      recyclerView.setScrollDisabled(!isFinished);
      if (isFinished) {
        if (scheduledScrollLock) {
          processScrollLock();
        }
        if (OPEN_KEYBOARD_WITH_AUTOSCROLL) {
          if (openKeyboardUponFinishingAutoScroll) {
            Keyboard.show(inputView);
            openKeyboardUponFinishingAutoScroll = false;
          }
        }
      }
    }
  }

  private boolean awaitLayout;
  private int awaitScrollBy;

  @Override
  protected void onLeaveSearchMode () {
    super.onLeaveSearchMode();
    if (preventAutoScroll) {
      preventAutoScroll = false;
      setAutoScrollFinished(true);
      return;
    }
    final int top = getTopEdge();
    final int contentOffset = getContentOffset() - Screen.dp(VERTICAL_PADDING_SIZE);
    if (top == 0) {
      GridLayoutManager manager = (GridLayoutManager) recyclerView.getLayoutManager();
      int i = manager.findFirstVisibleItemPosition();
      View view = i == 0 ? manager.findViewByPosition(0) : null;
      if (i != 0 || needPostponeAutoScroll) {
        awaitLayout = true;
        awaitScrollBy = -contentOffset;
        manager.scrollToPositionWithOffset(0, Screen.dp(VERTICAL_PADDING_SIZE));
        return;
      } else if (view != null && view.getTop() < Screen.dp(VERTICAL_PADDING_SIZE)) {
        int viewTop = view.getTop();
        recyclerView.scrollBy(0, viewTop - Screen.dp(VERTICAL_PADDING_SIZE));
      }
    }
    autoScroll(top - contentOffset);
  }

  private void autoScroll (int by) {
    if (awaitLayout) {
      awaitScrollBy = by;
    } else {
      smoothScrollBy(by);
    }
  }

  private boolean needUpdateSearchMode;

  @Override
  protected void updateSearchMode (boolean inSearch) {
    if (!inSearch || getTopEdge() == 0) {
      super.updateSearchMode(inSearch);
      needUpdateSearchMode = false;
    } else {
      needUpdateSearchMode = true;
    }
  }

  @Override
  protected void applySearchTransformFactor (float factor, boolean isOpening) {
    super.applySearchTransformFactor(factor, isOpening);
    setSmoothScrollFactor(isOpening ? factor : 1f - factor);
    setScrollLocked(factor == 1f);
    popupLayout.setIgnoreBottom(factor != 0f);
  }

  private boolean isScrollLocked;

  private boolean scheduledScrollLock;
  private void processScrollLock () {
    scheduledScrollLock = false;
    recyclerView.stopScroll();
    if (getTopEdge() == 0) {
      GridLayoutManager manager = (GridLayoutManager) recyclerView.getLayoutManager();
      int i = manager.findFirstVisibleItemPosition();
      View view = i == 0 ? manager.findViewByPosition(0) : null;
      preventAutoScroll = i > 0 || (view != null && view.getTop() < Screen.dp(VERTICAL_PADDING_SIZE));
    } else {
      preventAutoScroll = false;
    }
  }

  private void setScrollLocked (boolean isLocked) {
    if (this.isScrollLocked != isLocked) {
      this.isScrollLocked = isLocked;
      if (isScrollLocked) {
        if (autoScrollFinished) {
          processScrollLock();
        } else {
          scheduledScrollLock = true;
        }
        forceCloseEmojiKeyboard();
        checkKeyboardVisible();
      } else {
        scheduledScrollLock = false;
      }
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_EXPAND: {
        setExpandFactor(factor);
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_EXPAND: {
        onExpansionFinished(finalFactor);
        break;
      }
    }
  }

  // PopupLayout


  @Override
  public boolean shouldTouchOutside (float x, float y) {
    return headerView != null && y < headerView.getTranslationY() - HeaderView.getSize(true);
  }

  private PopupLayout popupLayout;

  public void show () {
    show(false);
  }

  public void show (boolean overlayStatusBar) {
    if (tdlib == null) {
      if (getExportContentState() == EXPORT_AVAILABLE) {
        exportContent();
        destroy();
      }
      return;
    }
    popupLayout = new PopupLayout(context()) {
      @Override
      public void onCustomShowComplete () {
        super.onCustomShowComplete();
        if (!isDestroyed()) {
          recyclerView.invalidateItemDecorations();
        }
      }
    };
    popupLayout.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    // popupLayout.set®(View.LAYER_TYPE_HARDWARE, Views.LAYER_PAINT);
    popupLayout.setBoundController(this);
    popupLayout.setPopupHeightProvider(this);
    popupLayout.setOverlayStatusBar(overlayStatusBar);
    popupLayout.init(false);
    popupLayout.setHideKeyboard();
    popupLayout.setNeedRootInsets();
    popupLayout.setTouchProvider(this);
    popupLayout.setIgnoreHorizontal();
    getValue();
    context().addFullScreenView(this, false);
  }

  @Override
  protected boolean useDropShadow () {
    return false;
  }

  private boolean openLaunched;

  private void launchOpenAnimation () {
    if (!openLaunched) {
      openLaunched = true;
      popupLayout.showSimplePopupView(getValue(), calculateTotalHeight());
    }
  }

  private int getTargetHeight () {
    return Screen.currentHeight(); // UI.getContext(getContext()).getNavigation().getWrap().getMeasuredHeight();
  }

  private int getContentOffset () {
    return getTargetHeight() / 2 - HeaderView.getSize(true) - (isExpanded ? calculateMovementDistance() : 0) + (canShareLink ? 0 : Screen.dp(56f) / 2);
  }

  private int calculateTotalHeight () {
    return getTargetHeight() - (getContentOffset() + HeaderView.getTopOffset());
  }

  @Override
  public int getCurrentPopupHeight () {
    return (getTargetHeight() - detectTopRecyclerEdge() - (int) ((float) HeaderView.getTopOffset() * (1f - (lickView != null ? lickView.factor : 0f))));
  }

  // Data loading

  private final Map<String, TdlibChatListSlice> listByChatList = new HashMap<>();
  private final Map<String, List<TGFoundChat>> chatsByChatList = new HashMap<>();

  private @Nullable TdlibChatListSlice getDisplayingList () {
    return getListByChatList(displayingChatList);
  }

  private @Nullable List<TGFoundChat> getDisplayingChats () {
    return getChatsByChatList(displayingChatList);
  }

  private @Nullable TdlibChatListSlice getListByChatList (TdApi.ChatList chatList) {
    return listByChatList.get(TD.makeChatListKey(chatList));
  }

  private void bindListToChatList (TdApi.ChatList chatList, TdlibChatListSlice list) {
    listByChatList.put(TD.makeChatListKey(chatList), list);
  }

  private @Nullable List<TGFoundChat> getChatsByChatList (TdApi.ChatList chatList) {
    return chatsByChatList.get(TD.makeChatListKey(chatList));
  }

  private void bindChatsToChatList (TdApi.ChatList chatList, List<TGFoundChat> chats) {
    chatsByChatList.put(TD.makeChatListKey(chatList), chats);
  }

  private void displayChatList (String title, TdApi.ChatList chatList) {
    if (isDisplayingChatList(chatList))
      return;
    headerCell.setTitle(StringUtils.isEmptyOrBlank(title) ? getName() : title);
    displayingChatList = chatList;
    if (getListByChatList(chatList) != null) {
      List<TGFoundChat> chats = getChatsByChatList(chatList);
      if (chats != null && !chats.isEmpty()) {
        List<ListItem> items = new ArrayList<>(chats.size());
        addCells(chats, items);
        adapter.setItems(items, false);
      } else {
        adapter.setItems(Collections.singletonList(emptyItem()), false);
      }
    } else {
      adapter.setItems(Collections.singletonList(emptyItem()), false);
      initializeChatList(chatList);
    }
    recyclerView.invalidateItemDecorations();
  }

  private void displayChats (TdApi.ChatList chatList, List<TGFoundChat> chats) {
    List<TGFoundChat> displayingChats = getChatsByChatList(chatList);
    if (displayingChats == null) {
      bindChatsToChatList(chatList, chats);
    } else {
      ArrayUtils.ensureCapacity(displayingChats, displayingChats.size() + chats.size());
      displayingChats.addAll(chats);
    }

    if (isDisplayingChatList(chatList)) {
      if (isEmptyItem(adapter.getItem(0))) {
        adapter.getItems().remove(0);
        adapter.notifyItemRemoved(0);
      }
      final int startIndex = adapter.getItems().size();
      addCells(chats, adapter.getItems());
      if (recyclerView.getAdapter() == null) {
        recyclerView.setAdapter(adapter);
        launchOpenAnimation();
      } else {
        int newItemCount = adapter.getItems().size() - startIndex;
        if (newItemCount > 0) {
          adapter.notifyItemRangeInserted(startIndex, newItemCount);
          recyclerView.invalidateItemDecorations();
        }
      }
    }
  }

  private static void addCells (List<TGFoundChat> entries, List<ListItem> out) {
    if (entries.isEmpty()) {
      return;
    }
    ArrayUtils.ensureCapacity(out, out.size() + entries.size());
    for (TGFoundChat entry : entries) {
      out.add(valueOfChat(entry));
    }
  }

  private static ListItem valueOfChat (TGFoundChat chat) {
    return new ListItem(ListItem.TYPE_CHAT_VERTICAL_FULLWIDTH, R.id.chat).setData(chat).setLongId(chat.getAnyId());
  }

  private static ListItem emptyItem () {
    return new ListItem(ListItem.TYPE_PADDING, android.R.id.empty).setHeight(Screen.dp(86f) + Screen.dp(VERTICAL_PADDING_SIZE));
  }

  private static boolean isEmptyItem (@Nullable ListItem item) {
    return item != null && item.getId() == android.R.id.empty;
  }

  // Button

  private static class SendButton extends FrameLayoutFix implements FactorAnimator.Target {
    public SendButton (Context context) {
      super(context);
      ViewUtils.setBackground(this, new Drawable() {
        @Override
        public void draw (@NonNull Canvas c) {
          doDraw(c);
        }

        @Override
        public void setAlpha (@IntRange(from = 0, to = 255) int alpha) {

        }

        @Override
        public void setColorFilter (@Nullable ColorFilter colorFilter) {

        }

        @Override
        public int getOpacity () {
          return PixelFormat.UNKNOWN;
        }
      });
      View view = new View(context);
      RippleSupport.setTransparentSelector(view);
      Views.setClickable(view);
      view.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      addView(view);
    }

    private boolean isReady;
    private FactorAnimator animator;

    public void setIsReady (boolean isReady, boolean animated) {
      if (this.isReady != isReady) {
        this.isReady = isReady;
        final float toFactor = isReady ? 1f : 0f;
        if (animated) {
          if (animator == null) {
            animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.factor);
          }
          animator.animateTo(toFactor);
        } else {
          if (animator != null) {
            animator.forceFactor(toFactor);
          }
          setFactor(toFactor);
        }
      }
    }

    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      setFactor(factor);
    }

    @Override
    public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {

    }

    private float factor; // 0f -> copy link, 1f -> send

    private void setFactor (float factor) {
      if (this.factor != factor) {
        this.factor = factor;
        invalidate();
      }
    }

    private String sendText;
    private boolean sendTextFake;
    private float sendWidth;

    private String copyText;
    private boolean copyTextFake;
    private float copyWidth;

    public void setShareText (@NonNull String text) {
      this.copyText = text.toUpperCase();
      this.copyTextFake = Text.needFakeBold(copyText);
      this.copyWidth = U.measureText(copyText, Paints.getTitleBigPaint(copyTextFake));
    }

    public void setSendText (@NonNull String text) {
      sendText = text.toUpperCase();
      sendTextFake = Text.needFakeBold(sendText);
      sendWidth = U.measureText(sendText, Paints.getTitleBigPaint(sendTextFake));
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
      if (sendText == null) {
        setSendText(Lang.getString(R.string.Send));
      }
      if (copyText == null) {
        setShareText(Lang.getString(R.string.ShareBtnLink));
      }
    }

    private void doDraw (Canvas c) {
      final int width = getMeasuredWidth();
      final int height = getMeasuredHeight();
      final int color = factor == 0f ? Theme.fillingColor() : factor == 1f ? Theme.getColor(ColorId.fillingPositive) : ColorUtils.fromToArgb(Theme.fillingColor(), Theme.getColor(ColorId.fillingPositive), factor);
      c.drawColor(color);
      if (factor != 0f && factor != 1f) {
        float radius = (float) Math.sqrt(width * width + height * height) * .5f;
        c.drawCircle(width / 2, height / 2, radius * factor, Paints.fillingPaint(ColorUtils.alphaColor(factor, Theme.getColor(ColorId.fillingPositive))));
      }
      final int textColor = factor == 0f ? Theme.getColor(ColorId.textNeutral) : factor == 1f ? Theme.getColor(ColorId.fillingPositiveContent) : ColorUtils.fromToArgb(Theme.getColor(ColorId.textNeutral), Theme.getColor(ColorId.fillingPositiveContent), factor);
      if (factor <= .5f) {
        TextPaint paint = Paints.getTitleBigPaint(copyTextFake);
        final int sourceTextColor = paint.getColor();
        paint.setColor(textColor);
        c.drawText(copyText, width / 2 - copyWidth / 2, height / 2 + Screen.dp(7f), paint);
        paint.setColor(sourceTextColor);
      }
      if (factor >= .5f) {
        TextPaint paint = Paints.getTitleBigPaint(sendTextFake);
        final int sourceTextColor = paint.getColor();
        paint.setColor(textColor);
        c.drawText(sendText, width / 2 - sendWidth / 2, height / 2 + Screen.dp(7f), paint);
        paint.setColor(sourceTextColor);
      }
    }
  }

  private boolean isExpanded;
  private FactorAnimator expandAnimator;
  private float expandFactor;
  private static final int ANIMATOR_EXPAND = 1;

  private void setExpandFactor (float factor) {
    if (this.expandFactor != factor) {
      this.expandFactor = factor;
      float y = calculateRecyclerOffset();
      recyclerView.setTranslationY(y);
      checkCommentPosition();
      checkHeaderPosition();
      if (!canShareLink) {
        bottomShadow.setAlpha(expandFactor >= .2f ? 1f : expandFactor / .2f);
      }
      if (sendHint != null) {
        sendHint.reposition();
      }
    }
  }

  private void checkCommentPosition () {
    float y = (float) calculateMovementDistance() * (1f - expandFactor) - getKeyboardOffset();
    bottomWrap.setTranslationY(y);
    if (OPEN_KEYBOARD_WITH_AUTOSCROLL) {
      stubInputView.setTranslationY(y);
    }
    bottomShadow.setTranslationY(y);
    if (!canShareLink) {
      sendButton.setTranslationY(y);
    }
    checkButtonsPosition();
  }

  private void checkButtonsPosition () {
    int add = Math.max(0, inputView.getMeasuredHeight() - Screen.dp(48f));
    float y = bottomWrap.getTranslationY() + add;
    okButton.setTranslationY(y);
    emojiButton.setTranslationY(y);
  }

  private int calculateMovementDistance () {
    if (canShareLink) {
      return Math.max(bottomWrap.getMeasuredHeight(), Screen.dp(48f));
    } else {
      return Math.max(bottomWrap.getMeasuredHeight(), Screen.dp(48f)) + Screen.dp(56f);
    }
  }

  private boolean appliedRecyclerMargin;

  private void setAppliedRecyclerMargin (boolean isApplied) {
    if (this.appliedRecyclerMargin != isApplied) {
      this.appliedRecyclerMargin = isApplied;
      recyclerView.setTranslationY(calculateRecyclerOffset());
      ((RelativeLayout.LayoutParams) recyclerView.getLayoutParams()).addRule(RelativeLayout.ABOVE, isApplied ? R.id.share_bottom : canShareLink ? R.id.btn_send : 0);
      recyclerView.setLayoutParams(recyclerView.getLayoutParams());
      recyclerView.invalidateItemDecorations();
      if (ignoreRecyclerMovement && savedPosition == 0) {
        if (isApplied) {
          recyclerView.scrollBy(0, calculateMovementDistance() * -1);
        } else {
          ((LinearLayoutManager) recyclerView.getLayoutManager()).scrollToPositionWithOffset(savedPosition, savedOffset - getContentOffset());
        }
      }
      inputView.setEnabled(isApplied);
    }
  }

  private void checkInputEnabled (int top) {
    final boolean isEnabled = appliedRecyclerMargin /*&& top == 0*/;
    if (inputView.isEnabled() != isEnabled) {
      inputView.setEnabled(isEnabled);
    }
    if (OPEN_KEYBOARD_WITH_AUTOSCROLL) {
      stubInputView.setEnabled(!isEnabled);
    }
    if (OPEN_KEYBOARD_WITH_AUTOSCROLL) {
      if (isEnabled && openKeyboardUponFinishingAutoScroll) {
        openKeyboardUponFinishingAutoScroll = false;
        Keyboard.show(inputView);
      }
    }
  }

  private boolean topEnsured;
  private int topEnsuredBy;

  private void resetTopEnsuredState () {
    topEnsured = false;
  }

  private void ensureTopPosition () {
    int top = getTopEdge();
    if (top > 0) {
      topEnsured = true;
      topEnsuredBy = top;
      recyclerView.scrollBy(0, top);
    } else {
      topEnsured = false;
    }
  }

  private void restoreEnsuredTopPosition () {
    if (topEnsured) {
      recyclerView.scrollBy(0, -topEnsuredBy);
      topEnsured = false;
    }
  }

  private boolean isSendHidden;

  private void setSendButtonHidden (boolean isHidden) {
    if (this.isSendHidden != isHidden) {
      this.isSendHidden = isHidden;
      sendButton.setVisibility(isHidden ? View.GONE : View.VISIBLE);
      RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) bottomWrap.getLayoutParams();
      if (isHidden) {
        params.addRule(RelativeLayout.ABOVE, 0);
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
      } else {
        params.addRule(RelativeLayout.ABOVE, R.id.btn_send);
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
      }
      okButton.setVisibility(isHidden ? View.VISIBLE : View.INVISIBLE);
    }
  }

  @Override
  public boolean onKeyboardStateChanged (boolean visible) {
    if (inSearchMode() || isSent) {
      return super.onKeyboardStateChanged(visible);
    }
    if (visible && !getKeyboardState()) {
      closeEmojiKeyboard(true);
    }
    boolean result = super.onKeyboardStateChanged(visible);
    if (keyboardFrameLayout != null) {
      keyboardFrameLayout.onKeyboardStateChanged(visible);
    }
    checkKeyboardVisible();
    return result;
  }

  private float calculateRecyclerOffset () {
    return appliedRecyclerMargin || ignoreRecyclerMovement ? 0f : (float) calculateMovementDistance() * expandFactor * -1f;
  }

  private int savedPosition, savedOffset;

  // Returns whether animation should start after onLayout
  private boolean onPrepareToExpand (final float toFactor) {
    ignoreRecyclerMovement = getTopEdge() <= HeaderView.getTopOffset() * 2;
    if (ignoreRecyclerMovement) {
      LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
      savedPosition = manager.findFirstVisibleItemPosition();
      /*if (savedPosition == 0) {
        ignoreRecyclerMovement = false;
      }*/
      View view = manager.findViewByPosition(savedPosition);
      savedOffset = view != null ? view.getTop() : 0;
    }
    if (appliedRecyclerMargin && toFactor < 1f) {
      setAppliedRecyclerMargin(false);
      return true;
    }
    return false;
  }

  private void onExpansionFinished (float finalFactor) {
    if (finalFactor == 1f) {
      setAppliedRecyclerMargin(true);
      setLockFocusView(inputView, false);
    } else {
      setLockFocusView(null);
    }
  }

  private boolean expansionAnimationScheduled;
  private float scheduledExpansionFactor;

  private void launchExpansionAnimation () {
    if (expansionAnimationScheduled) {
      expansionAnimationScheduled = false;
      expandAnimator.animateTo(scheduledExpansionFactor);
    }
  }

  private boolean ignoreRecyclerMovement;
  private TooltipOverlayView.TooltipInfo sendHint;

  private void setIsExpanded (boolean isExpanded) {
    if (this.isExpanded != isExpanded) {
      this.isExpanded = isExpanded;

      if (isExpanded) {
        CharSequence hint;
        long tutorialFlag = Settings.TUTORIAL_FORWARD_SCHEDULE;
        long singleChatId = selectedChatIds.size() == 1 ? selectedChatIds.get(0) : 0;
        if (ChatId.isSecret(singleChatId)) {
          hint = null;
        } else if (tdlib.isSelfChat(singleChatId)) {
          hint = Lang.getString(R.string.HoldToRemind);
        } else if (ChatId.isPrivate(singleChatId) && tdlib.cache().userLastSeenAvailable(ChatId.toUserId(singleChatId))) {
          hint = Lang.getStringBold(R.string.HoldToSchedule2, tdlib.cache().userFirstName(ChatId.toUserId(singleChatId)));
        } else if (tdlib.isChannel(singleChatId)) {
          hint = Lang.getString(R.string.HoldToSilentBroadcast);
        } else {
          hint = Lang.getString(R.string.HoldToSchedule);
        }
        if (mode == MODE_MESSAGES && Settings.instance().needTutorial(Settings.TUTORIAL_FORWARD_COPY)) {
          hint = Lang.getString(R.string.HoldToSendAsCopy);
          tutorialFlag = Settings.TUTORIAL_FORWARD_COPY;
        }
        if (hint != null && Settings.instance().needTutorial(tutorialFlag)) {
          Settings.instance().markTutorialAsShown(tutorialFlag);
          if (tutorialFlag != Settings.TUTORIAL_FORWARD_SCHEDULE) {
            Settings.instance().markTutorialAsShown(Settings.TUTORIAL_FORWARD_SCHEDULE);
          }
          sendHint = context().tooltipManager().builder(new SingleViewProvider(null) {
            @Nullable
            @Override
            public View findAnyTarget () {
              return isSendHidden ? okButton : sendButton;
            }
          }).show(tdlib, hint).hideDelayed();
        }
      } else {
        if (sendHint != null) {
          sendHint.hideNow();
        }
        resetTopEnsuredState();
        hideSoftwareKeyboard();
      }

      final boolean animated = getSearchTransformFactor() == 0f;
      final float toFactor = isExpanded ? 1f : 0f;
      if (animated) {
        if (expandAnimator == null) {
          expandAnimator = new FactorAnimator(ANIMATOR_EXPAND, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.expandFactor);
        }
        if (onPrepareToExpand(toFactor)) {
          expansionAnimationScheduled = true;
          scheduledExpansionFactor = toFactor;
        } else {
          expandAnimator.animateTo(toFactor);
        }
      } else {
        if (expandAnimator != null) {
          expandAnimator.forceFactor(toFactor);
        }
        expansionAnimationScheduled = false;
        setExpandFactor(toFactor);
        onExpansionFinished(toFactor);
      }
    }
  }

  private void checkAbilityToSend () {
    final int selectedCount = selectedChats.size();
    final boolean isReady = selectedCount > 0;

    if (canShareLink) {
      sendButton.setIsReady(isReady, true);
    }
    setIsExpanded(isReady);
  }

  private float getKeyboardOffset () {
    return keyboardFrameLayout != null ? keyboardFrameLayout.getLayoutTranslationOffset() : 0f;
  }

  private void onKeyboardLayoutTranslation (float translationY) {
    checkCommentPosition();
  }

  // Emoji keyboard

  private boolean isKeyboardReallyVisible;

  private void setKeyboardVisible (boolean isVisible) {
    if (this.isKeyboardReallyVisible != isVisible) {
      this.isKeyboardReallyVisible = isVisible;
      if (isVisible) {
        ensureTopPosition();
      } else {
        restoreEnsuredTopPosition();
      }
      setSendButtonHidden(isVisible);
      if (!isVisible && sendOnKeyboardClose) {
        sendOnKeyboardClose = false;
        sendMessages(sendOnKeyboardCloseGoToChat, false, sendOnKeyboardCloseSendOptions);
      }
    }
  }

  private KeyboardFrameLayout keyboardFrameLayout;
  private EmojiLayout emojiLayout;
  private TextFormattingLayout textFormattingLayout;

  private boolean emojiShown, emojiState;

  private void processEmojiClick () {
    if (emojiShown) {
      closeEmojiKeyboard();
    } else {
      openEmojiKeyboard();
    }
  }

  private void checkKeyboardVisible () {
    setKeyboardVisible(!inSearchMode() && getLockFocusView() != null && (getKeyboardState() || emojiShown));
  }

  private void openEmojiKeyboard () {
    if (!emojiShown) {
      if (keyboardFrameLayout == null) {
        keyboardFrameLayout = new KeyboardFrameLayout(context());
        keyboardFrameLayout.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        keyboardFrameLayout.setParentView(bottomWrap, contentView, wrapView);
        keyboardFrameLayout.setUpdateTranslationListener(this::onKeyboardLayoutTranslation);

        textFormattingLayout = keyboardFrameLayout.contentView.textFormattingLayout;
        textFormattingLayout.init(this, inputView, new TextFormattingLayout.Delegate() {
          @Override
          public void onWantsCloseTextFormattingKeyboard () {
            closeTextFormattingKeyboard();
          }

          @Override
          public void onWantsOpenTextFormattingKeyboard () {
            openEmojiKeyboard();
          }
        });

        emojiLayout = keyboardFrameLayout.contentView.emojiLayout;
        emojiLayout.initWithMediasEnabled(this, false, this, this, false);
        emojiLayout.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        emojiLayout.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        bottomWrap.addView(keyboardFrameLayout);
      }
      keyboardFrameLayout.setVisible(true);
      emojiState = getKeyboardState();

      setEmojiShown(true);
      if (emojiState) {
        emojiButton.setImageResource(R.drawable.baseline_keyboard_24);
        keyboardFrameLayout.hideKeyboard(inputView);
      } else {
        emojiButton.setImageResource(R.drawable.baseline_direction_arrow_down_24);
      }

      checkKeyboardVisible();
    }
  }

  private void forceCloseEmojiKeyboard () {
    if (emojiShown) {
      if (keyboardFrameLayout != null) {
        keyboardFrameLayout.setVisible(false);
      }
      setEmojiShown(false);
      emojiButton.setImageResource(getTargetIcon());

      checkKeyboardVisible();
    }
  }

  private void closeEmojiKeyboard () {
    closeEmojiKeyboard(false);
  }

  private void closeEmojiKeyboard (boolean byKeyboardOpen) {
    if (emojiShown) {
      if (keyboardFrameLayout != null) {
        keyboardFrameLayout.setVisible(false);
        if (emojiState && !byKeyboardOpen) {
          keyboardFrameLayout.showKeyboard(inputView);
        }
      }
      setEmojiShown(false);
      emojiButton.setImageResource(getTargetIcon());
      checkKeyboardVisible();
    }
  }

  @Override
  public void onEnterEmoji (String emoji) {
    inputView.onEmojiSelected(emoji);
  }

  @Override
  public void onEnterCustomEmoji (TGStickerObj sticker) {
    inputView.onCustomEmojiSelected(sticker);
  }

  @Override
  public void onDeleteEmoji () {
    if (inputView.length() > 0) {
      inputView.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
    }
  }

  @Override
  public boolean isEmojiInputEmpty () {
    return inputView.getText().length() == 0;
  }

  @Override
  public void onSearchRequested (EmojiLayout layout, boolean areStickers) {

  }

  // Settings

  private String getExportMimeType (TdApi.Message message) {
    if (message == null || message.sendingState != null) {
      return null;
    }
    TdApi.File file = getFile(message);
    if (file == null)
      return null;
    //noinspection SwitchIntDef
    switch (message.content.getConstructor()) {
      case TdApi.MessageText.CONSTRUCTOR: {
        return TD.getMimeType(((TdApi.MessageText) message.content).linkPreview);
      }
      case TdApi.MessagePhoto.CONSTRUCTOR: {
        TdApi.MessagePhoto media = (TdApi.MessagePhoto) message.content;
        if (media.isSecret)
          return null;
        return "image/jpeg";
      }
      case TdApi.MessageChatChangePhoto.CONSTRUCTOR:
        return "image/jpeg";
      case TdApi.MessageVideo.CONSTRUCTOR: {
        TdApi.MessageVideo media = (TdApi.MessageVideo) message.content;
        if (media.isSecret)
          return null;
        String mimeType = media.video.mimeType;
        if (StringUtils.isEmpty(mimeType) || !mimeType.startsWith("video/"))
          mimeType = "video/*";
        return mimeType;
      }
      case TdApi.MessageAudio.CONSTRUCTOR: {
        TdApi.MessageAudio media = (TdApi.MessageAudio) message.content;
        String mimeType = media.audio.mimeType;
        if (StringUtils.isEmpty(mimeType) || !mimeType.startsWith("audio/"))
          mimeType = "audio/*";
        return mimeType;
      }
      case TdApi.MessageAnimation.CONSTRUCTOR: {
        TdApi.MessageAnimation media = (TdApi.MessageAnimation) message.content;
        if (media.isSecret)
          return null;
        String mimeType = media.animation.mimeType;
        if (StringUtils.isEmpty(mimeType) || !(mimeType.startsWith("video/") || mimeType.equals("image/gif")))
          mimeType = "video/*";
        return mimeType;
      }
      case TdApi.MessageDocument.CONSTRUCTOR: {
        TdApi.MessageDocument media = (TdApi.MessageDocument) message.content;
        String mimeType = media.document.mimeType;
        if (StringUtils.isEmpty(mimeType)) {
          String extension = U.getExtension(media.document.fileName);
          mimeType = TGMimeType.mimeTypeForExtension(extension);
        }
        if (StringUtils.isEmpty(mimeType))
          return "application/octet-stream";
        return mimeType;
      }
    }
    return null;
  }

  private static final int EXPORT_NONE = 0;
  private static final int EXPORT_AVAILABLE = 1;
  private static final int EXPORT_DISABLED = 2;

  private static final boolean ALLOW_SHARED_PRIVATE = true;

  private boolean canExportContent () {
    return getExportContentState() != EXPORT_NONE;
  }

  private static boolean canExportStaticContent (TdApi.Message msg) {
    //noinspection SwitchIntDef
    switch (msg.content.getConstructor()) {
      case TdApi.MessageContact.CONSTRUCTOR:
        return true;
    }
    return false;
  }

  private void exportStaticContent (TdApi.Message msg) {
    //noinspection SwitchIntDef
    switch (msg.content.getConstructor()) {
      case TdApi.MessageContact.CONSTRUCTOR: {
        TdApi.Contact contact = ((TdApi.MessageContact) msg.content).contact;
        exportContact(contact.phoneNumber, contact.firstName, contact.lastName, contact.userId != 0 ? tdlib.cache().userUsernames(contact.userId) : null, contact.vcard);
        break;
      }
    }
  }

  private int getExportContentState () {
    Args args = getArgumentsStrict();
    if (!StringUtils.isEmpty(args.exportText))
      return EXPORT_AVAILABLE;
    switch (mode) {
      case MODE_MESSAGES: {
        if (args.messages.length == 0)
          return EXPORT_NONE;
        if (args.messages.length == 1 && canExportStaticContent(args.messages[0]))
          return EXPORT_AVAILABLE;
        String lastMimeType = null;
        long lastAuthorId = 0;
        List<TdApi.File> awaitingFiles = null;
        for (TdApi.Message message : args.messages) {
          String mimeType = getExportMimeType(message);
          if (StringUtils.isEmpty(mimeType))
            return EXPORT_NONE;
          if (lastMimeType == null)
            lastMimeType = mimeType;
          else if (!lastMimeType.equals(mimeType))
            return EXPORT_NONE;
          long authorId = Td.getMessageAuthorId(message);
          if (authorId == 0)
            return EXPORT_NONE;
          if (lastAuthorId == 0)
            lastAuthorId = authorId;
          else if (lastAuthorId != authorId && (!ALLOW_SHARED_PRIVATE && ChatId.isUserChat(message.chatId) && !tdlib.isSelfChat(message.chatId)))
            return EXPORT_NONE;
          TdApi.File file = getFile(message);
          if (file == null)
            return EXPORT_NONE;
          if (!TD.isFileLoadedAndExists(file)) {
            if (awaitingFiles == null)
              awaitingFiles = new ArrayList<>();
            awaitingFiles.add(file);
          }
        }
        this.awaitingFiles = awaitingFiles;
        if (awaitingFiles != null) {
          return EXPORT_DISABLED;
        }
        return EXPORT_AVAILABLE;
      }
      case MODE_TELEGRAM_FILES: {
        if (args.telegramFiles.length == 0)
          return EXPORT_NONE;
        String lastMimeType = null;
        for (MediaItem item : args.telegramFiles) {
          String mimeType = item.getShareMimeType();
          if (StringUtils.isEmpty(mimeType))
            return EXPORT_NONE;
          if (lastMimeType == null)
            lastMimeType = mimeType;
          else if (!lastMimeType.equals(mimeType))
            return EXPORT_NONE;
        }
        return EXPORT_AVAILABLE;
      }
      case MODE_FILES:
      case MODE_CONTACT: {
        return EXPORT_AVAILABLE;
      }
    }
    return EXPORT_NONE;
  }

  private boolean needShareSettings () {
    if (mode != MODE_MESSAGES) {
      for (int i = 0; i < selectedChatIds.size(); i++) {
        long chatId = selectedChatIds.get(i);
        if (!tdlib.isSelfChat(chatId) && !ChatId.isSecret(chatId)) {
          return true;
        }
      }
      return false;
    }
    return true;
  }

  private void showShareSettings () {
    boolean canRemoveCaptions = false;
    int canSendWithoutSound = 0;
    boolean hasSecretChats = false;
    List<ListItem> items = new ArrayList<>();

    if (mode == MODE_MESSAGES) {
      for (TdApi.Message message : getArgumentsStrict().messages) {
        if (!Td.isText(message.content) && TD.canCopyText(message)) {
          canRemoveCaptions = true;
          break;
        }
      }
      items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_hideAuthor, 0, R.string.SendAsCopy, R.id.btn_hideAuthor, needHideAuthor));
      if (canRemoveCaptions) {
        items.add(new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_removeCaptions, 0, R.string.RemoveCaptions, R.id.btn_removeCaptions, needRemoveCaptions));
      }
    }

    for (int i = 0; i < selectedChatIds.size(); i++) {
      long chatId = selectedChatIds.get(i);
      if (ChatId.isSecret(chatId)) {
        hasSecretChats = true;
      }
      if (!tdlib.isSelfChat(chatId) && !ChatId.isSecret(chatId)) {
        canSendWithoutSound++;
      }
    }

    if (canSendWithoutSound > 0) {
      ListItem item = new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_sendNoSound, 0, R.string.SendNoSound, R.id.btn_sendNoSound, forceSendWithoutSound);
      if (hasSecretChats) {
        item.setString(Lang.pluralBold(R.string.SendNoSoundX, canSendWithoutSound));
      }
      items.add(item);
    }
    if (items.isEmpty())
      return;

    ListItem[] itemsArray = items.toArray(new ListItem[0]);
    showSettings(R.id.btn_menu_customize, itemsArray, (id, result) -> {
      needHideAuthor = result.get(R.id.btn_hideAuthor) == R.id.btn_hideAuthor;
      needRemoveCaptions = result.get(R.id.btn_removeCaptions) == R.id.btn_removeCaptions;
      forceSendWithoutSound = result.get(R.id.btn_sendNoSound) == R.id.btn_sendNoSound;
    });
  }

  // Send messages

  private boolean isSent;
  private boolean needHideAuthor, needRemoveCaptions, forceSendWithoutSound;

  private void sendMessages (boolean forceGoToChat, boolean isSingleTap, @Nullable TdApi.MessageSendOptions finalSendOptions) {
    if (selectedChats.size() == 0 || isSent) {
      return;
    }

    // Double check if there are any new restrictions
    for (int i = 0; i < selectedChats.size(); i++) {
      long chatId = selectedChats.valueAt(i).getChatId();
      if (showErrorMessage(null, chatId, true)) {
        return;
      }
    }

    final ArrayList<TdApi.Function<?>> functions = new ArrayList<>();
    final TdApi.FormattedText comment = inputView.getOutputText(true);

    //noinspection UnsafeOptInUsageError
    final boolean hasComment = !Td.isEmpty(comment);

    final Args args = getArgumentsStrict();

    TdApi.MessageSendOptions cloudSendOptions = Td.newSendOptions(finalSendOptions, forceSendWithoutSound);
    // FIXME: it seems separate sendOptions for secret chats no longer required or something is broken?
    TdApi.MessageSendOptions secretSendOptions = Td.newSendOptions(cloudSendOptions);

    for (int i = 0; i < selectedChatIds.size(); i++) {
      final long chatId = selectedChatIds.get(i);
      if (showErrorMessage(null, chatId, true))
        return;

      final TdApi.Chat chat = tdlib.chat(selectedChatIds.get(i));
      if (chat == null) {
        long myUserId = tdlib.myUserId();
        if (chatId != myUserId) {
          throw new RuntimeException("Unknown chatId:" + chatId);
        }
        tdlib.client().send(new TdApi.CreatePrivateChat(myUserId, true), tdlib.silentHandler());
      }
      TdApi.MessageSendOptions sendOptions = ChatId.isSecret(chatId) ? secretSendOptions : cloudSendOptions;
      boolean messageReplyIncluded = false;
      if (hasComment) {
        TdApi.InputMessageReplyToMessage replyTo = null;
        if (Config.FORCE_REPLY_WHEN_FORWARDING_WITH_COMMENT && !args.disallowReply && !ChatId.isSecret(chatId) && mode == MODE_MESSAGES && !(needHideAuthor || needRemoveCaptions) && args.messages[0].chatId != chatId) {
          messageReplyIncluded = true;

          long singleSourceChatId = 0, singleSourceMediaGroupId = 0, contentfulMediaMessageId = 0;
          for (int index = 0; index < args.messages.length; index++) {
            TdApi.Message message = args.messages[index];
            TdApi.MessageProperties properties = tdlib.getMessagePropertiesSync(message);
            if (!properties.canBeRepliedInAnotherChat) {
              messageReplyIncluded = false;
              break;
            }
            if (index == 0) {
              singleSourceChatId = message.chatId;
              singleSourceMediaGroupId = message.mediaAlbumId;
            } else if (singleSourceChatId != message.chatId || singleSourceMediaGroupId != message.mediaAlbumId || message.mediaAlbumId == 0) {
              messageReplyIncluded = false;
              break;
            }
            if (!Td.isEmpty(Td.textOrCaption(message.content))) {
              if (contentfulMediaMessageId != 0) {
                messageReplyIncluded = false;
                break;
              }
              contentfulMediaMessageId = message.id;
            }
          }

          if (messageReplyIncluded) {
            replyTo = new TdApi.InputMessageReplyToMessage(contentfulMediaMessageId != 0 ? contentfulMediaMessageId : args.messages[0].id, null);
          }
        }
        functions.addAll(TD.sendMessageText(chatId, 0, replyTo, sendOptions, new TdApi.InputMessageText(comment, null, false), tdlib.maxMessageTextLength()));
      }
      switch (mode) {
        case MODE_TEXT: {
          functions.addAll(TD.sendMessageText(chatId, 0, null, sendOptions, new TdApi.InputMessageText(args.text, null, false), tdlib.maxMessageTextLength()));
          break;
        }
        case MODE_MESSAGES: {
          if (!messageReplyIncluded && !TD.forwardMessages(chatId, 0, args.messages, needHideAuthor, needRemoveCaptions, sendOptions, functions))
            return;
          break;
        }
        case MODE_GAME: {
          functions.add(new TdApi.SendMessage(chatId, 0, null, sendOptions, null, new TdApi.InputMessageForwarded(args.botMessage.chatId, args.botMessage.id, args.withUserScore, null)));
          break;
        }
        case MODE_FILES  : {
          List<TdApi.InputMessageContent> contents = new ArrayList<>(args.files.length);
          for (FileInfo fileInfo : args.files) {
            TdApi.InputMessageDocument document = new TdApi.InputMessageDocument(TD.createInputFile(fileInfo.path), null, false, null);
            contents.add(document);
          }
          TdApi.Function<?> function;
          if (contents.size() == 1) {
            function = new TdApi.SendMessage(chatId, 0, null, sendOptions, null, contents.get(0));
          } else {
            function = new TdApi.SendMessageAlbum(chatId, 0, null, sendOptions, contents.toArray(new TdApi.InputMessageContent[0]));
          }
          functions.add(function);
          break;
        }
        case MODE_CONTACT: {
          functions.add(new TdApi.SendMessage(chatId, 0, null, sendOptions, null, new TdApi.InputMessageContact(new TdApi.Contact(args.contactUser.phoneNumber, args.contactUser.firstName, args.contactUser.lastName, null, args.botUserId))));
          break;
        }
        case MODE_STICKER: {
          functions.add(new TdApi.SendMessage(chatId, 0, null, sendOptions, null, new TdApi.InputMessageSticker(new TdApi.InputFileId(args.sticker.sticker.id), null, 0, 0, null)));
          break;
        }
        case MODE_CUSTOM: {
          args.customDelegate.generateFunctionsForChat(chatId, chat, sendOptions, functions);
          break;
        }
        case MODE_CUSTOM_CONTENT: {
          functions.addAll(TD.sendMessageText(chatId, 0, null, sendOptions, args.customContent, tdlib.maxMessageTextLength()));
          break;
        }
        case MODE_TELEGRAM_FILES: {
          TdApi.FormattedText formattedCaption = StringUtils.isEmpty(args.telegramCaption) ? null : TD.newText(args.telegramCaption);
          TdApi.FormattedText messageCaption = formattedCaption != null && formattedCaption.text.codePointCount(0, formattedCaption.text.length()) <= tdlib.maxCaptionLength() ? formattedCaption : null;
          boolean showCaptionAboveMedia = false; // TODO?
          if (formattedCaption != null && messageCaption == null) {
            functions.addAll(TD.sendMessageText(chatId, 0, null, sendOptions, new TdApi.InputMessageText(formattedCaption, null, false), tdlib.maxMessageTextLength()));
          }
          for (MediaItem item : args.telegramFiles) {
            boolean last = item == args.telegramFiles[args.telegramFiles.length - 1];
            TdApi.InputMessageContent content = item.createShareContent(last ? messageCaption : null, last && showCaptionAboveMedia);
            if (content == null)
              return;
            functions.add(new TdApi.SendMessage(chatId, 0, null, sendOptions, null, content));
          }
          break;
        }
        default:
          return;
      }
    }

    if (functions.size() == 1) {
      tdlib.client().send(functions.get(0), tdlib.messageHandler());
    } else {
      tdlib.runOnTdlibThread(() -> {
        for (TdApi.Function<?> function : functions) {
          tdlib.client().send(function, tdlib.messageHandler());
        }
      });
    }

    onSent();

    if (isSingleTap) {
      UI.showToast(R.string.DoneSave, Toast.LENGTH_SHORT);
    } else if ((args.needOpenChat || forceGoToChat) && selectedChats.size() == 1) {
      ViewController<?> c = context().navigation().getCurrentStackItem();
      if (!(c instanceof MessagesController && ((MessagesController) c).compareChat(selectedChats.valueAt(0).getAnyId(), 0))) {
        UI.post(() -> tdlib.ui().openChat(ShareController.this, selectedChats.valueAt(0).getAnyId(), null), 250);
      }
    } else {
      UI.showToast(R.string.Done, Toast.LENGTH_SHORT);
    }

    popupLayout.hideWindow(true);
  }

  private void onSent () {
    if (!isSent) {
      isSent = true;
      Args args = getArgumentsStrict();
      if (args.after != null) {
        args.after.run();
      }
    }
  }

  private String getShareButtonText () {
    Args args = getArgumentsStrict();
    if (args.copyLinkAction != null) {
      return Lang.getString(args.copyLinkActionNameRes);
    }
    if (!StringUtils.isEmpty(args.shareText) && !StringUtils.isEmpty(args.shareButtonText))
      return args.shareButtonText;
    switch (mode) {
      case MODE_MESSAGES: {
        return Lang.getString(tdlib.isChannel(args.messages[0].chatId) ? R.string.ShareBtnPost : R.string.ShareBtnMessage);
      }
    }
    return Lang.getString(R.string.ShareBtnLink);
  }

  private boolean canShareLink () {
    Args args = getArgumentsStrict();
    if (!StringUtils.isEmpty(args.shareText) || args.copyLinkAction != null)
      return true;
    switch (mode) {
      case MODE_MESSAGES: {
        return canCopyMessageLink();
      }
    }
    return false;
  }

  private void shareLink () {
    if (isSent)
      return;
    Args args = getArgumentsStrict();
    if (args.copyLinkAction != null) {
      args.copyLinkAction.run();
    } else if (!StringUtils.isEmpty(args.shareText)) {
      Intents.shareText(args.shareText);
    } else {
      switch (mode) {
        case MODE_MESSAGES: {
          final String name;
          boolean isUser;
          switch (args.messages[0].senderId.getConstructor()) {
            case TdApi.MessageSenderUser.CONSTRUCTOR:
              name = tdlib.cache().userName(((TdApi.MessageSenderUser) args.messages[0].senderId).userId);
              isUser = true;
              break;
            case TdApi.MessageSenderChat.CONSTRUCTOR:
              name = tdlib.chatTitle(((TdApi.MessageSenderChat) args.messages[0].senderId).chatId);
              isUser = false;
              break;
            default:
              Td.assertMessageSender_439d4c9c();
              throw Td.unsupported(args.messages[0].senderId);
          }
          tdlib.getMessageLink(args.messages[0], args.messages.length > 1, args.messageThreadId != 0, link ->
            Intents.shareText(Lang.getString(args.messageThreadId != 0 && isUser ? R.string.ShareTextComment : isUser || !tdlib.isChannel(args.messages[0].chatId) ? R.string.ShareTextMessage : R.string.ShareTextPost, link.url, name))
          );
          break;
        }
      }
    }

    onSent();
    if (popupLayout != null) {
      popupLayout.hideWindow(true);
    }
  }

  private boolean canCopyMessageLink () {
    Args args = getArgumentsStrict();
    if (args.messages.length == 0)
      return false;
    if (tdlib.canCopyPublicMessageLinks(args.messages[0].chatId)) {
      if (args.messages.length == 1) {
        return true;
      }
      long mediaGroupId = args.messages[0].mediaAlbumId;
      if (mediaGroupId != 0) {
        for (int i = 1; i < args.messages.length; i++) {
          if (mediaGroupId != args.messages[i].mediaAlbumId) {
            return false;
          }
        }
        return true;
      }
    }
    return false;
  }

  private boolean canCopyLink () {
    if (!getArgumentsStrict().allowCopyLink) {
      return false;
    }
    switch (mode) {
      case MODE_MESSAGES: {
        return canCopyMessageLink();
      }
    }
    return false;
  }

  private void copyLink () {
    if (isSent) {
      return;
    }

    Args args = getArgumentsStrict();
    switch (mode) {
      case MODE_MESSAGES:
        tdlib.getMessageLink(args.messages[0], args.messages.length > 1, args.messageThreadId != 0, link -> UI.copyText(link.url, link.isPublic ? R.string.CopiedLink : R.string.CopiedLinkPrivate));
        break;
    }

    onSent();
    if (popupLayout != null) {
      popupLayout.hideWindow(true);
    }
  }

  private @Nullable PopupLayout folderSelectorLayout;

  private void showFolderSelector () {
    if (headerView == null)
      return;
    MenuMoreWrap menu = new MenuMoreWrap(context, /* scrollable */ true) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        int overrideHeightMeasureSpec;
        int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
        if (heightSpecMode == MeasureSpec.UNSPECIFIED) {
          overrideHeightMeasureSpec = heightMeasureSpec;
        } else {
          int heightSpecSize = Math.max(MeasureSpec.getSize(heightMeasureSpec) - Math.round(getTranslationY()), 0);
          overrideHeightMeasureSpec = MeasureSpec.makeMeasureSpec(heightSpecSize, heightSpecMode);
        }
        super.onMeasure(widthMeasureSpec, overrideHeightMeasureSpec);
      }
    };
    menu.init(getThemeListeners(), null);
    menu.addItem(0, Lang.getString(R.string.CategoryMain), R.drawable.baseline_forum_24, null, v -> {
      PopupLayout popupLayout = PopupLayout.parentOf(v);
      popupLayout.hideWindow(true);
      displayChatList(Lang.getString(R.string.CategoryMain), ChatPosition.CHAT_LIST_MAIN);
    });
    View.OnClickListener onItemClickListener = v -> {
      PopupLayout popupLayout = PopupLayout.parentOf(v);
      popupLayout.hideWindow(true);
      TdApi.ChatFolderInfo chatFolderInfo = (TdApi.ChatFolderInfo) v.getTag();
      displayChatList(chatFolderInfo.title, new TdApi.ChatListFolder(chatFolderInfo.id));
    };
    for (TdApi.ChatFolderInfo chatFolderInfo : tdlib.chatFolders()) {
      View itemView = menu.addItem(View.NO_ID, chatFolderInfo.title, TD.findFolderIcon(chatFolderInfo.icon, R.drawable.baseline_folder_24), /* icon */ null, onItemClickListener);
      itemView.setTag(chatFolderInfo);
    }
    menu.setAnchorMode(MenuMoreWrap.ANCHOR_MODE_HEADER);
    menu.setTranslationY(headerView.getTranslationY());
    folderSelectorLayout = new PopupLayout(context);
    folderSelectorLayout.init(true);
    folderSelectorLayout.setNeedRootInsets();
    folderSelectorLayout.setOverlayStatusBar(true);
    folderSelectorLayout.setDismissListener((popup) -> folderSelectorLayout = null);
    folderSelectorLayout.showMoreView(menu);
  }

  private void hideFolderSelector () {
    if (folderSelectorLayout != null) {
      folderSelectorLayout.hideWindow(isFocused());
      folderSelectorLayout = null;
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    for (TdlibChatListSlice list : listByChatList.values()) {
      list.unsubscribeFromUpdates(this);
    }
    Views.destroyRecyclerView(recyclerView);
    TGLegacyManager.instance().removeEmojiListener(adapter);
    cancelDownloadingFiles();
    context().removeFullScreenView(this, false);
  }

  private void setEmojiShown (boolean emojiShown) {
    if (this.emojiShown != emojiShown) {
      this.emojiShown = emojiShown;
      setTextFormattingLayoutVisible(textInputHasSelection);
      if (inputView != null) {
        inputView.setActionModeVisibility(!textInputHasSelection || !emojiShown);
      }
    }
  }

  /**/

  private boolean textInputHasSelection;
  private boolean textFormattingVisible;

  @Override
  public void onInputSelectionChanged (InputView v, int start, int end) {
    if (textFormattingLayout != null) {
      textFormattingLayout.onInputViewSelectionChanged(start, end);
    }
  }

  @Override
  public void onInputSelectionExistChanged (InputView v, boolean hasSelection) {
    textInputHasSelection = hasSelection;
    if (!emojiShown) {
     emojiButton.setImageResource(getTargetIcon());
    }
  }

  public void onInputSpansChanged (InputView view) {
    if (textFormattingLayout != null) {
      textFormattingLayout.onInputViewSpansChanged();
    }
  }

  private void setTextFormattingLayoutVisible (boolean visible) {
    textFormattingVisible = keyboardFrameLayout != null && keyboardFrameLayout.contentView.setTextFormattingLayoutVisible(visible);
  }

  private void closeTextFormattingKeyboard () {
    if (textFormattingVisible && emojiShown) {
      closeEmojiKeyboard();
    }
  }

  public @DrawableRes int getTargetIcon () {
    return (textInputHasSelection || (textFormattingVisible && emojiShown)) ? R.drawable.baseline_format_text_24 : R.drawable.deproko_baseline_insert_emoticon_26;
  }
}
