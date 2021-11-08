package org.thunderdog.challegram.component.popups;

import org.thunderdog.challegram.component.attach.MediaBottomBaseController;
import org.thunderdog.challegram.component.attach.MediaLayout;
import org.thunderdog.challegram.navigation.ViewController;

public class ModernActionedLayout extends MediaLayout {
  private MediaBottomBaseController<?> curController;

  public ModernActionedLayout (ViewController<?> context) {
    super(context);

  }

  public void setController (MediaBottomBaseController<?> controller) {
    curController = controller;
  }

  @Override
  public MediaBottomBaseController<?> createControllerForIndex (int index) {
    return curController;
  }
}
