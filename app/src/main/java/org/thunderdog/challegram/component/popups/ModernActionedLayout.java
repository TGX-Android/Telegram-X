/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
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

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.component.attach.MediaBottomBaseController;
import org.thunderdog.challegram.component.attach.MediaLayout;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.navigation.ViewController;

public class ModernActionedLayout extends MediaLayout {
  private MediaBottomBaseController<?> curController;

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
    controller.get();
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