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

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.data.InlineResult;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.mediaview.MediaViewController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.widget.PopupLayout;

import java.util.ArrayList;

import me.vkryl.core.lambda.RunnableData;

public class SingleMediaPickerManager extends MediaLayoutManager {
  public SingleMediaPickerManager (ViewController<?> context) {
    super(context);
  }

  public void openMediaView (RunnableData<ImageGalleryFile> callback, RunnableData<InlineResult<?>> fileCallback, long chatId, @Nullable Runnable onDismissPrepare, boolean overlayStatusBar, @Nullable MessagesController messagesController, @Nullable TdApi.Message messageToMediaReplace, boolean galleryOnly) {
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
          if (messageToMediaReplace != null) {
            boolean isInAlbum = messageToMediaReplace.mediaAlbumId != 0;
          }
          return messagesController != null && messagesController.showRestriction(view, rightId);
        }

        @Override
        public void onFilesSelected (ArrayList<InlineResult<?>> results, boolean needShowKeyboard) {
          if (fileCallback != null) {
            fileCallback.runWithData(results.get(0));
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
      mediaLayout.init(galleryOnly ? MediaLayout.MODE_AVATAR_PICKER : MediaLayout.MODE_GALLERY_AND_FILES, null);
      mediaLayout.setCallback(singleMediaCallback(callback));
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
}
