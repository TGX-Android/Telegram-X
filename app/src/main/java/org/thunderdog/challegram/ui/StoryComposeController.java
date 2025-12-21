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
 * File created on 21/12/2024
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.MediaLayout;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.mediaview.MediaSelectDelegate;
import org.thunderdog.challegram.mediaview.MediaSendDelegate;
import org.thunderdog.challegram.mediaview.MediaViewController;
import org.thunderdog.challegram.mediaview.MediaViewDelegate;
import org.thunderdog.challegram.mediaview.MediaViewThumbLocation;
import org.thunderdog.challegram.mediaview.data.MediaItem;
import org.thunderdog.challegram.mediaview.data.MediaStack;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.camera.CameraController;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.widget.PopupLayout;

import java.util.ArrayList;

import me.vkryl.android.widget.FrameLayoutFix;

/**
 * Story compose controller - entry point for creating new stories.
 * Provides options to capture via camera or pick from gallery.
 */
public class StoryComposeController extends ViewController<StoryComposeController.Args> implements
    PopupLayout.AnimatedPopupProvider,
    MediaViewDelegate,
    MediaSelectDelegate,
    MediaSendDelegate {

  public static class Args {
    public final long chatId; // Chat to post story to (usually user's own chat)

    public Args (long chatId) {
      this.chatId = chatId;
    }
  }

  private PopupLayout popupLayout;
  private FrameLayout contentView;

  public StoryComposeController (@NonNull Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_storyCompose;
  }

  @Override
  protected View onCreateView (Context context) {
    contentView = new FrameLayout(context);
    contentView.setLayoutParams(new ViewGroup.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    ));
    contentView.setBackgroundColor(Theme.fillingColor());

    // Create options layout
    LinearLayout optionsLayout = new LinearLayout(context);
    optionsLayout.setOrientation(LinearLayout.VERTICAL);
    optionsLayout.setGravity(Gravity.CENTER);

    FrameLayout.LayoutParams optionsParams = new FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.WRAP_CONTENT
    );
    optionsParams.gravity = Gravity.CENTER;
    optionsLayout.setLayoutParams(optionsParams);

    // Title
    TextView titleView = new TextView(context);
    titleView.setText(R.string.CreateStory);
    titleView.setTextSize(20);
    titleView.setTextColor(Theme.textAccentColor());
    titleView.setTypeface(Fonts.getRobotoMedium());
    titleView.setGravity(Gravity.CENTER);
    titleView.setPadding(0, 0, 0, Screen.dp(24));
    optionsLayout.addView(titleView);

    // Camera button
    View cameraButton = createOptionButton(context, R.drawable.baseline_camera_alt_24, R.string.Camera, v -> {
      openCamera();
    });
    optionsLayout.addView(cameraButton);

    // Gallery button
    View galleryButton = createOptionButton(context, R.drawable.baseline_image_24, R.string.Gallery, v -> {
      openGallery();
    });
    optionsLayout.addView(galleryButton);

    // Close button
    ImageView closeButton = new ImageView(context);
    closeButton.setImageResource(R.drawable.baseline_close_24);
    closeButton.setColorFilter(Theme.iconColor());
    closeButton.setPadding(Screen.dp(16), Screen.dp(16), Screen.dp(16), Screen.dp(16));
    closeButton.setOnClickListener(v -> {
      if (popupLayout != null) {
        popupLayout.hideWindow(true);
      }
    });
    FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.WRAP_CONTENT,
      ViewGroup.LayoutParams.WRAP_CONTENT
    );
    closeParams.gravity = Gravity.TOP | Gravity.END;
    closeButton.setLayoutParams(closeParams);

    contentView.addView(optionsLayout);
    contentView.addView(closeButton);

    return contentView;
  }

  private View createOptionButton (Context context, int iconRes, int textRes, View.OnClickListener listener) {
    LinearLayout button = new LinearLayout(context);
    button.setOrientation(LinearLayout.HORIZONTAL);
    button.setGravity(Gravity.CENTER_VERTICAL);
    button.setPadding(Screen.dp(24), Screen.dp(16), Screen.dp(24), Screen.dp(16));
    button.setBackgroundResource(R.drawable.bg_btn_header);
    button.setOnClickListener(listener);

    LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.WRAP_CONTENT
    );
    buttonParams.setMargins(Screen.dp(32), Screen.dp(8), Screen.dp(32), Screen.dp(8));
    button.setLayoutParams(buttonParams);

    ImageView icon = new ImageView(context);
    icon.setImageResource(iconRes);
    icon.setColorFilter(Theme.iconColor());
    LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(Screen.dp(24), Screen.dp(24));
    iconParams.setMarginEnd(Screen.dp(16));
    icon.setLayoutParams(iconParams);
    button.addView(icon);

    TextView text = new TextView(context);
    text.setText(textRes);
    text.setTextSize(16);
    text.setTextColor(Theme.textAccentColor());
    text.setTypeface(Fonts.getRobotoRegular());
    button.addView(text);

    return button;
  }

  public void show () {
    popupLayout = new PopupLayout(context());
    popupLayout.setNeedRootInsets();
    popupLayout.init(true);
    popupLayout.setShowListener(popup -> {});
    popupLayout.setDismissListener(popup -> {
      destroy();
    });
    popupLayout.showAnimatedPopupView(getValue(), this);
  }

  private void openCamera () {
    if (popupLayout != null) {
      popupLayout.hideWindow(false);
    }

    CameraController cameraController = new CameraController(context(), tdlib());
    cameraController.setMediaEditorDelegates(this, this, this);
    navigateTo(cameraController);
  }

  private void openGallery () {
    if (popupLayout != null) {
      popupLayout.hideWindow(false);
    }

    // Open media layout for gallery selection
    MediaLayout mediaLayout = new MediaLayout(this);
    mediaLayout.setDelegate(this, this);
    mediaLayout.show();
  }

  // MediaViewDelegate implementation
  @Override
  public void onMediaViewEnter (MediaViewController mediaView, MediaItem mediaItem) { }

  @Override
  public MediaViewThumbLocation getTargetLocation (int indexInStack, MediaItem item) {
    return null;
  }

  @Override
  public void setMediaItemVisible (int index, MediaItem item, boolean isVisible) { }

  // MediaSelectDelegate implementation
  @Override
  public boolean onSendMedia (MediaStack stack, boolean forceNoSpoiler) {
    // User selected media from gallery, now post as story
    if (stack != null && stack.getCurrentItem() != null) {
      MediaItem item = stack.getCurrentItem();
      postStory(item);
    }
    return true;
  }

  // MediaSendDelegate implementation
  @Override
  public long provideEstimatedChatId () {
    Args args = getArguments();
    return args != null ? args.chatId : tdlib().selfChatId();
  }

  @Override
  public boolean canSendMedia () {
    return true;
  }

  private void postStory (MediaItem mediaItem) {
    if (mediaItem == null) return;

    long chatId = provideEstimatedChatId();

    // Create story content based on media type
    TdApi.InputStoryContent content;
    if (mediaItem.isVideo()) {
      String path = mediaItem.getSourcePath();
      if (path == null && mediaItem.getSourceGalleryFile() != null) {
        path = mediaItem.getSourceGalleryFile().getFilePath();
      }
      if (path == null) {
        UI.showToast(R.string.Error, Toast.LENGTH_SHORT);
        return;
      }
      content = new TdApi.InputStoryContentVideo(
        new TdApi.InputFileLocal(path),
        new int[0], // addedStickerFileIds
        mediaItem.getDuration() / 1000.0, // duration in seconds
        0.0, // coverFrameTimestamp
        false // isAnimation
      );
    } else {
      String path = mediaItem.getSourcePath();
      if (path == null && mediaItem.getSourceGalleryFile() != null) {
        path = mediaItem.getSourceGalleryFile().getFilePath();
      }
      if (path == null) {
        UI.showToast(R.string.Error, Toast.LENGTH_SHORT);
        return;
      }
      content = new TdApi.InputStoryContentPhoto(
        new TdApi.InputFileLocal(path),
        new int[0] // addedStickerFileIds
      );
    }

    // Default privacy: everyone
    TdApi.StoryPrivacySettings privacySettings = new TdApi.StoryPrivacySettingsEveryone(false);

    // Post the story
    tdlib().client().send(new TdApi.PostStory(
      chatId,
      content,
      null, // areas
      null, // caption
      privacySettings,
      new int[0], // albumIds
      86400, // activePeriod (24 hours)
      null, // fromStoryFullId
      false, // isPostedToChatPage
      false // protectContent
    ), result -> {
      UI.post(() -> {
        if (result.getConstructor() == TdApi.Story.CONSTRUCTOR) {
          UI.showToast(R.string.StoryPosted, Toast.LENGTH_SHORT);
        } else if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          TdApi.Error error = (TdApi.Error) result;
          UI.showToast(error.message, Toast.LENGTH_LONG);
        }
      });
    });
  }

  // PopupLayout.AnimatedPopupProvider implementation
  @Override
  public boolean shouldHideKeyboardOnShow () {
    return true;
  }

  @Override
  public int provideAnimationType () {
    return PopupLayout.ANIMATION_TYPE_BOTTOM_TO_TOP;
  }

  @Override
  public void onPrepareToShow (PopupLayout popup) { }

  @Override
  public void onShow (PopupLayout popup) { }

  /**
   * Opens the story compose flow.
   * Checks if user can post stories first.
   */
  public static void open (ViewController<?> from) {
    if (Settings.instance().hideStories()) {
      return;
    }

    Tdlib tdlib = from.tdlib();
    long chatId = tdlib.selfChatId();

    // Check if we can post story
    tdlib.client().send(new TdApi.CanPostStory(chatId), result -> {
      UI.post(() -> {
        if (result.getConstructor() == TdApi.CanPostStoryResult.CONSTRUCTOR) {
          TdApi.CanPostStoryResult canPost = (TdApi.CanPostStoryResult) result;
          // Check the specific result type
          if (canPost.getConstructor() == TdApi.CanPostStoryResultOk.CONSTRUCTOR) {
            StoryComposeController controller = new StoryComposeController(from.context(), tdlib);
            controller.setArguments(new Args(chatId));
            controller.show();
          } else {
            UI.showToast(R.string.ErrorCannotPostStory, Toast.LENGTH_LONG);
          }
        } else if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
          TdApi.Error error = (TdApi.Error) result;
          UI.showToast(error.message, Toast.LENGTH_LONG);
        }
      });
    });
  }
}
