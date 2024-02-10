package org.thunderdog.challegram.component.attach;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.mediaview.MediaViewController;
import org.thunderdog.challegram.navigation.ViewController;

import me.vkryl.core.lambda.RunnableData;

public class SingleMediaPickerManager extends MediaLayoutManager {
  public SingleMediaPickerManager (ViewController<?> context) {
    super(context);
  }

  public void openMediaView (RunnableData<ImageGalleryFile> callback, long chatId) {
    waitPermissionsForOpen(hasMedia -> {
      final MediaLayout mediaLayout = new MediaLayout(context) {
        @Override
        public int getCameraButtonOffset () {
          return 0;
        }
      };
      mediaLayout.setSingleMediaMode(true);
      mediaLayout.setDisallowGallerySystemPicker(true);
      mediaLayout.init(MediaLayout.MODE_AVATAR_PICKER, null);
      mediaLayout.setMediaViewControllerArgumentsEditor(args -> args
        .setSendButtonIcon(R.drawable.baseline_check_circle_24)
        .setFlag(MediaViewController.Args.FLAG_DISALLOW_MULTI_SELECTION_MEDIA)
        .setFlag(MediaViewController.Args.FLAG_DISALLOW_SET_DESTRUCTION_TIMER)
        .setFlag(MediaViewController.Args.FLAG_DISALLOW_SEND_BUTTON_HAPTIC_MENU)
        .setReceiverChatId(chatId)
      );
      mediaLayout.setCallback(singleMediaCallback(callback));
      if (!hasMedia) {
        mediaLayout.setNoMediaAccess();
      }

      openingMediaLayout = true;
      mediaLayout.preload(() -> {
        if (!context.isDestroyed()) {
          mediaLayout.show(true);
        }
        openingMediaLayout = false;
      }, 300L);

      currentMediaLayout = mediaLayout;
    });
  }
}
