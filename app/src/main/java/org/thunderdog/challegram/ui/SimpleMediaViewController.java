/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 10/03/2018
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.mediaview.MediaCellView;
import org.thunderdog.challegram.mediaview.data.MediaItem;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.widget.ForceTouchView;

public class SimpleMediaViewController extends ViewController<SimpleMediaViewController.Args> implements ForceTouchView.PreviewDelegate {
  private static final int MODE_PHOTO = 0;
  private static final int MODE_GIF = 1;
  private static final int MODE_PROFILE_PHOTO = 2;
  private static final int MODE_CHAT_PHOTO = 3;
  private static final int MODE_CHAT_PHOTO_FULL = 4;

  public static class Args {
    protected final int mode;

    public TdApi.Photo photo;
    public TdApi.Animation animation;
    public ImageFile previewFile;
    public ImageFile targetFile;

    public TdApi.ProfilePhoto profilePhoto;
    public TdApi.ChatPhotoInfo chatPhotoInfo;
    public TdApi.ChatPhoto chatPhoto;
    public long dataId;

    public Args (TdApi.Photo photo, ImageFile previewFile, ImageFile targetFile) {
      this.mode = MODE_PHOTO;
      this.photo = photo;
      this.previewFile = previewFile;
      this.targetFile = targetFile;
    }

    public Args (TdApi.Animation animation, ImageFile previewFile) {
      this.mode = MODE_GIF;
      this.animation = animation;
      this.previewFile = previewFile;
    }

    public Args (TdApi.ProfilePhoto profilePhoto, long userId) {
      this.mode = MODE_PROFILE_PHOTO;
      this.profilePhoto = profilePhoto;
      this.dataId = userId;
    }

    public Args (TdApi.ChatPhotoInfo chatPhotoInfo, long chatId) {
      this.mode = MODE_CHAT_PHOTO;
      this.chatPhotoInfo = chatPhotoInfo;
      this.dataId = chatId;
    }

    public Args (TdApi.ChatPhoto chatPhoto) {
      this.mode = MODE_CHAT_PHOTO_FULL;
      this.chatPhoto = chatPhoto;
    }
  }

  public SimpleMediaViewController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_media_simple;
  }

  private MediaCellView cellView;

  @Override
  protected View onCreateView (Context context) {
    cellView = new MediaCellView(context());
    cellView.setBoundForceTouchContext(null);

    MediaItem mediaItem = null;

    Args args = getArgumentsStrict();
    switch (args.mode) {
      case MODE_PHOTO: {
        mediaItem = MediaItem.valueOf(context(), tdlib, args.photo, null);
        if (mediaItem.isLoaded()) {
          mediaItem.setPreviewImageFile(args.targetFile);
        } else {
          mediaItem.setPreviewImageFile(args.previewFile);
        }
        break;
      }
      case MODE_GIF: {
        if (TD.isFileLoaded(args.animation.animation)) {
          cellView.setEnableEarlyLoad();
        }
        mediaItem = MediaItem.valueOf(context(), tdlib, args.animation, null);
        break;
      }
      case MODE_PROFILE_PHOTO: {
        mediaItem = new MediaItem(context(), tdlib, args.dataId, args.profilePhoto);
        break;
      }
      case MODE_CHAT_PHOTO: {
        mediaItem = new MediaItem(context(), tdlib, args.dataId, args.chatPhotoInfo);
        break;
      }
      case MODE_CHAT_PHOTO_FULL: {
        mediaItem = new MediaItem(context(), tdlib, args.dataId, 0, args.chatPhoto);
        break;
      }
    }
    if (mediaItem == null) {
      throw new IllegalArgumentException();
    }
    mediaItem.download(true);
    cellView.setMedia(mediaItem);
    executeScheduledAnimation();
    return cellView;
  }

  @Override
  public void onPrepareForceTouchContext (ForceTouchView.ForceTouchContext context) {
    cellView.setBoundForceTouchContext(context);
    context.setAllowFullscreen(true);
    context.setStateListener(cellView);
    context.setBackgroundColor(0x70000000);
  }

  @Override
  public boolean wouldHideKeyboardInForceTouchMode () {
    return false;
  }

  @Override
  public void destroy () {
    super.destroy();
    cellView.destroy();
  }
}
