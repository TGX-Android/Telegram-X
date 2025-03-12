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
 * File created on 09/02/2024 at 18:34
 */
package org.thunderdog.challegram.component.attach;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MediaPreview;
import org.thunderdog.challegram.component.chat.MediaPreviewSimple;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.ContentPreview;
import org.thunderdog.challegram.data.InlineResult;
import org.thunderdog.challegram.data.InlineResultCommon;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.mediaview.MediaViewController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.RightId;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.widget.PopupLayout;

import java.io.File;
import java.util.ArrayList;

import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.RunnableData;

public class MediaToReplacePickerManager extends MediaLayoutManager {
  public MediaToReplacePickerManager (ViewController<?> context) {
    super(context);
  }

  public void openMediaView (RunnableData<LocalPickedFile> callback, long chatId, @Nullable Runnable onDismissPrepare, boolean overlayStatusBar, @Nullable MessagesController messagesController, @NonNull TdApi.Message messageToReplace) {
    final int rightsFlags;

    final int constructor = messageToReplace.content.getConstructor();
    final boolean filesAsDefault = constructor == TdApi.MessageAudio.CONSTRUCTOR
      || constructor == TdApi.MessageDocument.CONSTRUCTOR
      || constructor == TdApi.MessageAnimation.CONSTRUCTOR;

    if (messageToReplace.mediaAlbumId != 0) {
      switch (messageToReplace.content.getConstructor()) {
        case TdApi.MessagePhoto.CONSTRUCTOR:
        case TdApi.MessageVideo.CONSTRUCTOR:
          rightsFlags = (1 << RightId.SEND_PHOTOS) | (1 << RightId.SEND_VIDEOS);
          break;
        case TdApi.MessageAudio.CONSTRUCTOR:
          rightsFlags = (1 << RightId.SEND_AUDIO);
          break;
        case TdApi.MessageDocument.CONSTRUCTOR:
          rightsFlags = (1 << RightId.SEND_DOCS);
          break;
        case TdApi.MessageAnimation.CONSTRUCTOR:
          rightsFlags = (1 << RightId.SEND_OTHER_MESSAGES);
          break;
        default:
          rightsFlags = 0;
          break;
      }
    } else {
      rightsFlags = (1 << RightId.SEND_PHOTOS) | (1 << RightId.SEND_VIDEOS) | (1 << RightId.SEND_DOCS) | (1 << RightId.SEND_AUDIO) | (1 << RightId.SEND_OTHER_MESSAGES);
    }

    openMediaView(callback, chatId, onDismissPrepare, overlayStatusBar, messagesController, rightsFlags, filesAsDefault);
  }

  public void openMediaView (RunnableData<LocalPickedFile> callback, long chatId, @Nullable Runnable onDismissPrepare, boolean overlayStatusBar, @Nullable MessagesController messagesController, int rightsFlags, boolean filesAsDefault) {
    waitPermissionsForOpen(hasMedia -> {
      final MediaLayout mediaLayout = new MediaLayout(context) {
        @Override
        public int getCameraButtonOffset () {
          return 0;
        }

        @Override
        public void onPopupDismissPrepare (PopupLayout popup) {
          if (onDismissPrepare != null) {
            onDismissPrepare.run();
          }
        }
      };
      mediaLayout.setSingleMediaMode(true);
      mediaLayout.setDisallowGallerySystemPicker(true);
      mediaLayout.setFilesControllerDelegate(new MediaBottomFilesController.Delegate() {
        @Override
        public boolean showRestriction (View view, int rightId) {
          if (!BitwiseUtils.hasFlag(rightsFlags, (1 << rightId))) {
            if (messagesController != null) {
              messagesController.showRestriction(view, Lang.getString(R.string.EditMediaRestricted));
            }
            return true;
          }

          return messagesController != null && messagesController.showRestriction(view, rightId);
        }

        @Override
        public void onFilesSelected (ArrayList<InlineResult<?>> results, boolean needShowKeyboard) {
          if (callback != null) {
            callback.runWithData(new LocalPickedFile(results.get(0)));
            mediaLayout.hide(false);
          }
        }
      });
      mediaLayout.setMediaViewControllerArgumentsEditor(args -> args
        .setSendButtonIcon(R.drawable.baseline_check_circle_24)
        .setFlag(MediaViewController.Args.FLAG_DISALLOW_MULTI_SELECTION_MEDIA)
        .setFlag(MediaViewController.Args.FLAG_DISALLOW_SET_DESTRUCTION_TIMER)
        .setFlag(MediaViewController.Args.FLAG_DISALLOW_SEND_BUTTON_HAPTIC_MENU)
        .setReceiverChatId(chatId)
      );

      final boolean allowGallery = BitwiseUtils.hasFlag(rightsFlags, (1 << RightId.SEND_PHOTOS) | (1 << RightId.SEND_VIDEOS));
      final boolean allowFiles = BitwiseUtils.hasFlag(rightsFlags, (1 << RightId.SEND_OTHER_MESSAGES) | (1 << RightId.SEND_DOCS) | (1 << RightId.SEND_AUDIO));

      mediaLayout.setItemsAdapter(new MediaLayout.ItemsAdapter() {
        @Override
        public MediaBottomBar.BarItem[] getBottomBarItems () {
          if (allowGallery && allowFiles) {
            return new MediaBottomBar.BarItem[] {
              new MediaBottomBar.BarItem(R.drawable.baseline_insert_drive_file_24, R.string.File, ColorId.attachFile),
              new MediaBottomBar.BarItem(R.drawable.baseline_image_24, R.string.Gallery, ColorId.attachPhoto)
            };
          } else if (allowFiles) {
            return new MediaBottomBar.BarItem[] {
              new MediaBottomBar.BarItem(R.drawable.baseline_insert_drive_file_24, R.string.File, ColorId.attachFile)
            };
          } else if (allowGallery) {
            return new MediaBottomBar.BarItem[] {
              new MediaBottomBar.BarItem(R.drawable.baseline_image_24, R.string.Gallery, ColorId.attachPhoto)
            };
          }

          throw new IllegalArgumentException("No send rights");
        }

        @Override
        public int getDefaultItemIndex () {
          return allowGallery && allowFiles && !filesAsDefault ? 1 : 0;
        }

        @Override
        public MediaBottomBaseController<?> createControllerForIndex (int index) {
          if (allowGallery && allowFiles) {
            switch (index) {
              case 0: return new MediaBottomFilesController(mediaLayout);
              case 1: return new MediaBottomGalleryController(mediaLayout);
              default: throw new IllegalArgumentException("Unknown index passed: " + index);
            }
          } else if (allowFiles) {
            return new MediaBottomFilesController(mediaLayout);
          } else if (allowGallery) {
            return new MediaBottomGalleryController(mediaLayout);
          }
          throw new IllegalArgumentException("Unknown index passed: " + index);
        }

        @Override
        public boolean needBottomBar () {
          return allowGallery && allowFiles;
        }
      });
      mediaLayout.init(MediaLayout.MODE_CUSTOM_ADAPTER, null);
      mediaLayout.setCallback(singleMediaCallback(image -> callback.runWithData(new LocalPickedFile(image))));
      if (!hasMedia) {
        mediaLayout.setNoMediaAccess();
      }

      openingMediaLayout = true;
      mediaLayout.preload(() -> {
        if (!context.isDestroyed()) {
          mediaLayout.show(overlayStatusBar);
        }
        openingMediaLayout = false;
      }, 300L);

      currentMediaLayout = mediaLayout;
    });
  }

  public static class LocalPickedFile {
    public final ImageGalleryFile imageGalleryFile;
    public final InlineResult<?> inlineResult;

    public LocalPickedFile (ImageGalleryFile imageGalleryFile) {
      this(imageGalleryFile, null);
    }

    public LocalPickedFile (InlineResult<?> inlineResult) {
      this(null, inlineResult);
    }

    public LocalPickedFile (ImageGalleryFile imageGalleryFile, InlineResult<?> inlineResult) {
      this.imageGalleryFile = imageGalleryFile;
      this.inlineResult = inlineResult;
    }

    public String getFileName (String defaultName) {
      if (inlineResult instanceof InlineResultCommon) {
        return ((InlineResultCommon) inlineResult).getTrackTitle();
      }
      return defaultName;
    }

    public String getMimeType (String defaultMimeType) {
      String mimeType = null;

      if (inlineResult instanceof InlineResultCommon) {
        mimeType = ((InlineResultCommon) inlineResult).getMimeType();
      } else if (imageGalleryFile != null && imageGalleryFile.isVideo()) {
        mimeType = imageGalleryFile.getVideoMimeType();
      }

      return !StringUtils.isEmpty(mimeType) ? mimeType : defaultMimeType;
    }

    public boolean isMusic () {
      return inlineResult != null && inlineResult.getType() == InlineResult.TYPE_AUDIO;
    }

    @Nullable
    public MediaPreview buildMediaPreview (Tdlib tdlib, int size, int cornerRadius) {
      if (imageGalleryFile != null) {
        return new MediaPreviewSimple(tdlib, size, 0, imageGalleryFile);
      } else if (inlineResult instanceof InlineResultCommon) {
        final TdApi.File file = ((InlineResultCommon) inlineResult).getTrackFile();
        final String path = TD.getFilePath(file);
        final String mimeType = getMimeType(null);

        if (!StringUtils.isEmpty(path) && !StringUtils.isEmpty(mimeType)) {
          return MediaPreview.valueOf(tdlib, new File(path), mimeType, size, cornerRadius);
        }
      }

      return null;
    }

    @Nullable
    public ContentPreview buildContentPreview () {
      if (imageGalleryFile != null) {
        if (imageGalleryFile.isVideo()) {
          return new ContentPreview(ContentPreview.EMOJI_VIDEO, R.string.ChatContentVideo);
        } else {
          return new ContentPreview(ContentPreview.EMOJI_PHOTO, R.string.ChatContentPhoto);
        }
      } else if (inlineResult != null) {
        final String fileName = getFileName(null);
        final boolean isMusic = isMusic();
        if (StringUtils.isEmpty(fileName)) {
          return new ContentPreview(isMusic ? ContentPreview.EMOJI_AUDIO : ContentPreview.EMOJI_FILE, R.string.ChatContentFile);
        } else {
          return new ContentPreview(isMusic ? ContentPreview.EMOJI_AUDIO : ContentPreview.EMOJI_FILE, 0, fileName, false);
        }
      }
      return null;
    }
  }
}
