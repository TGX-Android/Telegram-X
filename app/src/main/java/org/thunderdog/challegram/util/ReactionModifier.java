package org.thunderdog.challegram.util;

import android.graphics.Canvas;
import android.view.View;

import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.user.UserView;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.td.Td;

public class ReactionModifier implements DrawModifier {
  @Dimension(unit = Dimension.DP)
  private static final float MARGIN_RIGHT = 18f;
  @Dimension(unit = Dimension.DP)
  private static final float REACTION_SIZE = 24f;

  @Nullable
  private final ImageFile imageFile;

  public ReactionModifier (@NonNull Tdlib tdlib, @Nullable String reaction) {
    this(tdlib, tdlib.getReaction(reaction));
  }

  public ReactionModifier (@NonNull Tdlib tdlib, @Nullable TdApi.Reaction reaction) {
    TdApi.Sticker staticIcon = reaction != null ? reaction.staticIcon : null;
    if (staticIcon != null && !Td.isAnimated(staticIcon.type)) {
      imageFile = new ImageFile(tdlib, reaction.staticIcon.sticker);
      imageFile.setScaleType(ImageFile.FIT_CENTER);
      imageFile.setNoBlur();
      imageFile.setWebp();
    } else {
      imageFile = null;
    }
  }

  @Override
  public void afterDraw (@NonNull View view, @NonNull Canvas canvas) {
    ImageReceiver receiver;
    if (view instanceof SettingView) {
      SettingView settingView = (SettingView) view;
      receiver = settingView.getReceiver();
    } else if (view instanceof UserView) {
      UserView userView = (UserView) view;
      receiver = userView.getDrawModifierImagerReceiver();
    } else {
      return;
    }
    receiver.requestFile(imageFile);
    if (!receiver.isEmpty()) {
      int size = Screen.dp(REACTION_SIZE);
      int top = (view.getHeight() - size) / 2;
      int right = view.getWidth() - Screen.dp(MARGIN_RIGHT);
      receiver.setBounds(right - size, top, right, top + size);
      if (receiver.needPlaceholder()) {
        receiver.drawPlaceholderRounded(canvas, size / 2);
      } else {
        receiver.draw(canvas);
      }
    }
  }
}
