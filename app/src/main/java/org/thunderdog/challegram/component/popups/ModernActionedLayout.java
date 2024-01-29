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
 */
package org.thunderdog.challegram.component.popups;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.component.attach.MediaBottomBaseController;
import org.thunderdog.challegram.component.attach.MediaLayout;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.UI;

public class ModernActionedLayout extends MediaLayout {
  private MediaBottomBaseController<?> curController;

  public static void showGiftCode (ViewController<?> context, String code, @Nullable TdApi.MessagePremiumGiftCode giftCodeContent, @NonNull TdApi.PremiumGiftCodeInfo giftCodeInfo) {
    if (context.getKeyboardState()) {
      context.hideSoftwareKeyboard();
      UI.post(() -> showGiftCode(context, code, giftCodeContent, giftCodeInfo), 100);
      return;
    }
    showMal(context, (mal) -> new GiftCodeController(mal, code, giftCodeContent, giftCodeInfo));
  }

  public static void showMessageSeen (ViewController<?> context, TGMessage msg, TdApi.MessageViewers viewers) {
    showMal(context, (mal) -> new MessageSeenController(mal, msg, viewers));
  }

  public static void showJoinRequests (ViewController<?> context, long chatId, TdApi.ChatJoinRequestsInfo requestsInfo) {
    showDeferredMal(context, (mal) -> new JoinRequestsController(mal, chatId, requestsInfo));
  }

  public static void showJoinDialog (ViewController<?> context, TdApi.ChatInviteLinkInfo inviteLinkInfo, Runnable onJoinClicked) {
    showMal(context, (mal) -> new JoinDialogController(mal, inviteLinkInfo, onJoinClicked));
  }

  public ModernActionedLayout (ViewController<?> context) {
    super(context);
  }

  public void setController (MediaBottomBaseController<?> controller) {
    controller.getValue();
    curController = controller;
  }

  @Override
  public MediaBottomBaseController<?> createControllerForIndex (int index) {
    return curController;
  }

  // Helpers

  interface MalDataProvider <VC extends MediaBottomBaseController<?>> {
    VC provide(ModernActionedLayout layout);
  }

  private static void showMal (ViewController<?> context, MalDataProvider<?> provider) {
    ModernActionedLayout mal = new ModernActionedLayout(context);
    mal.setController(provider.provide(mal));
    mal.initCustom();
    mal.show();
  }

  private static void showDeferredMal (ViewController<?> context, MalDataProvider<?> provider) {
    ModernActionedLayout mal = new ModernActionedLayout(context);
    MediaBottomBaseController<?> controller = provider.provide(mal);
    controller.postOnAnimationExecute(mal::show);
    mal.setController(controller);
    mal.initCustom();
  }
}