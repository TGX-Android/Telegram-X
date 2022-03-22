package org.thunderdog.challegram.component.popups;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.component.attach.MediaBottomBaseController;
import org.thunderdog.challegram.component.attach.MediaLayout;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.navigation.ViewController;

public class ModernActionedLayout extends MediaLayout {
  private MediaBottomBaseController<?> curController;

  public static void showMessageSeen (ViewController<?> context, TGMessage msg, long[] userIds) {
    showMal(context, (mal) -> new MessageSeenController(mal, msg, userIds));
  }

  public static void showMessageReactors (ViewController<?> context, int reactionCount, long chatId, long msgId, TdApi.MessageReaction[] reactions) {
    // TODO: Show subcontroller only if reactions shouldn't be separated
    showMal(context, (mal) -> new MessageReactorsController(mal, chatId, msgId, reactionCount, reactions));
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