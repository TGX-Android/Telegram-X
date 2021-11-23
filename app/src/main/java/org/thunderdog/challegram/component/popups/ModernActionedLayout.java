package org.thunderdog.challegram.component.popups;

import org.thunderdog.challegram.component.attach.MediaBottomBaseController;
import org.thunderdog.challegram.component.attach.MediaLayout;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.navigation.ViewController;

public class ModernActionedLayout extends MediaLayout {
  private MediaBottomBaseController<?> curController;

  public static void showMessageSeen (ViewController<?> context, TGMessage msg, long[] userIds) {
    ModernActionedLayout mal = new ModernActionedLayout(context);
    mal.setController(new MessageSeenController(mal, msg, userIds));
    mal.initCustom();
    mal.show();
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
}