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
 * File created on 23/04/2015 at 21:56
 */
package org.thunderdog.challegram.navigation;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;

public class NavigationProcessor extends Handler {
  private static final int CLEAR_ANIMATION = 0;
  private static final int SET_CONTROLLER = 1;
  private static final int SET_CONTROLLER_ANIMATED = 2;
  private static final int REBASE_STACK = 3;
  private static final int NAVIGATE = 4;
  // private static final int DESTROY_LAST = 6;
  private static final int REMOVE_PREVIOUS = 7;
  private static final int REPLACE_CURRENT_NEXT = 8;
  private static final int REPLACE_CURRENT_PREV = 9;
  private static final int NAVIGATE_BACK = 10;
  private static final int INSERT_CONTROLLER = 11;
  private static final int CALL_FOCUS = 12;

  private static final long INTERRUPTION_DELAY = 12l;

  private NavigationController navigation;
  private NavigationStack stack;

  public NavigationProcessor (NavigationController navigation) {
    this.navigation = navigation;
    this.stack = new NavigationStack();
  }

  public NavigationStack getStack () {
    return stack;
  }

  public int getStackSize () {
    return stack.size();
  }

  public void checkRebaseMessage () {
    if (rebaseOnAnimationEnd) {
      sendMessage(Message.obtain(this, rebaseMessage, 0, rebaseArg2, rebaseObject));

      rebaseOnAnimationEnd = false;
      rebaseArg2 = 0;
      rebaseMessage = 0;
      rebaseObject = null;
    }
  }

  public void clearAnimationDelayed () {
    clearAnimation();
  }

  private static boolean checkUiThread () {
    return Looper.myLooper() == Looper.getMainLooper();
  }

  public void clearAnimation () {
    if (checkUiThread()) {
      navigation.setIsAnimating(false);
    } else {
      sendMessage(Message.obtain(this, CLEAR_ANIMATION));
    }
  }

  public void setController (ViewController<?> controller) {
    if (checkUiThread()) {
      controller.get();

      stack.clear(navigation);
      stack.push(controller, true);

      navigation.removeChildren();
      navigation.addChildWrapper(controller);
      callFocusDelayed(controller);
      navigation.getHeaderView().setTitle(controller);
      navigation.setIsAnimating(false);
    } else {
      sendMessage(Message.obtain(this, SET_CONTROLLER, 0, 0, controller));
    }
  }

  public void insertController (ViewController<?> controller, int index) {
    if (checkUiThread()) {
      stack.insert(controller, index);
    } else {
      sendMessage(Message.obtain(this, INSERT_CONTROLLER, 0, index, controller));
    }
  }

  public ViewController<?> getFutureRebaseController () {
    return rebaseOnAnimationEnd ? (ViewController<?>) rebaseObject : null;
  }

  public void setControllerAnimated (ViewController<?> controller, boolean asForward, boolean saveFirst) {
    int arg = 0;
    if (asForward) arg += 1;
    if (saveFirst) arg += 2;

    if (checkUiThread()) {
      if (navigation.isAnimating()) {
        rebaseOnAnimationEnd = true;
        rebaseObject = controller;
        rebaseMessage = SET_CONTROLLER_ANIMATED;
        rebaseArg2 = arg;
        return;
      }

      controller.get();

      navigation.setIsAnimating(true);
      // stack.getCurrent().onBlur();
      stack.push(controller, true);
      navigation.rebaseStack(controller, asForward, saveFirst);
    } else {
      sendMessage(Message.obtain(this, SET_CONTROLLER_ANIMATED, 0, arg, controller));
    }
  }

  public void rebaseStack (ViewController<?> controller, boolean saveFirst) {
    if (checkUiThread()) {
      if (navigation.isAnimating()) {
        navigation.removeChildWrapper(stack.getPrevious());
        stack.reset(navigation, saveFirst);
        clearAnimationDelayed();
        controller.onFocus();
      }
    } else {
      sendMessage(Message.obtain(this, REBASE_STACK, saveFirst ? 1 : 0, 0, controller));
    }
  }

  public void navigateTo (ViewController<?> controller) {
    if (checkUiThread()) {
      if (!navigation.isAnimating()){
        return;
      }

      if (navigation.isEmpty()) {
        throw new IllegalStateException();
      }

      navigation.hideContextualPopups();

      controller.get();

      // stack.getCurrent().onBlur();
      stack.push(controller, true);
      navigation.navigate(controller, NavigationController.MODE_FORWARD);
    } else {
      sendMessage(Message.obtain(this, NAVIGATE, 0, 0, controller));
    }
  }

  public void removePrevious (ViewController<?> controller) {
    if (checkUiThread()) {
      if (navigation.isAnimating()) {
        navigation.removeChildWrapper(controller);
        clearAnimationDelayed();
      }
    } else {
      sendMessage(Message.obtain(this, REMOVE_PREVIOUS, controller));
    }
  }

  public void setNextAsCurrent () {
    if (checkUiThread()) {
      if (navigation.isAnimating()) {
        ViewController<?> previous = stack.getPrevious();
        if (previous != null) {
          navigation.removeChildWrapper(previous);
          previous.get().setAlpha(1f);
        }

        if (NavigationController.DROP_SHADOW_ENABLED) {
          navigation.setShadowsVisibility(View.GONE);
        }

        clearAnimationDelayed();

        ViewController<?> current = stack.getCurrent();
        if (current != null) {
          current.onFocus();
        }
      }
    } else {
      sendMessage(Message.obtain(this, REPLACE_CURRENT_NEXT));
    }
  }

  public void navigateBack () {
    if (checkUiThread()) {
      if (navigation.isAnimating()) {
        return;
      }
      navigation.hideContextualPopups();
      navigation.setIsAnimating(true);
      ViewController<?> previous = stack.getPrevious();
      if (previous != null) {
        previous.get();
        navigation.navigate(previous, NavigationController.MODE_BACKWARD);
      }
    } else {
      sendMessage(Message.obtain(this, NAVIGATE_BACK));
    }
  }

  public void setPrevAsCurrent () {
    if (checkUiThread()) {
      if (navigation.isAnimating()) {
        ViewController<?> next = stack.removeLast();
        if (next != null) {
          navigation.removeChildWrapper(next);
        }

        if (NavigationController.DROP_SHADOW_ENABLED) {
          navigation.setShadowsVisibility(View.GONE);
        }

        if (next != null) {
          next.attachNavigationController(navigation);
          next.destroy();
          next.detachNavigationController();
        }

        clearAnimationDelayed();

        ViewController<?> current = stack.getCurrent();
        if (current != null) {
          current.onFocus();
        }
      }
    } else {
      sendMessage(Message.obtain(this, REPLACE_CURRENT_PREV));
    }
  }

  public void callFocusDelayed (ViewController<?> controller) {
    sendMessageDelayed(Message.obtain(this, CALL_FOCUS, controller), 18l);
  }

  @SuppressWarnings ("unchecked")
  @Override
  public void handleMessage (Message msg) {
    switch (msg.what) {
      case CLEAR_ANIMATION: {
        clearAnimation();
        break;
      }
      case SET_CONTROLLER: {
        setController((ViewController<?>) msg.obj);
        break;
      }
      case SET_CONTROLLER_ANIMATED: {
        setControllerAnimated((ViewController<?>) msg.obj, (msg.arg2 & 1) != 0, (msg.arg2 & 2) != 0);
        break;
      }
      case REMOVE_PREVIOUS: {
        removePrevious((ViewController<?>) msg.obj);
        break;
      }
      case REBASE_STACK: {
        rebaseStack((ViewController<?>) msg.obj, msg.arg1 == 1);
        break;
      }
      case NAVIGATE: {
        navigateTo((ViewController<?>) msg.obj);
        break;
      }
      /*case DESTROY_LAST: {
        ViewController controller = stack.removeLast();
        controller.attachNavigationController(navigation);
        controller.destroy();
        controller.detachNavigationController();
        clearAnimationDelayed();

        break;
      }*/
      case REPLACE_CURRENT_NEXT: {
        setNextAsCurrent();
        break;
      }
      case REPLACE_CURRENT_PREV: {
        setPrevAsCurrent();
        break;
      }
      case NAVIGATE_BACK: {
        navigateBack();
        break;
      }
      case INSERT_CONTROLLER: {
        insertController((ViewController<?>) msg.obj, msg.arg2);
        break;
      }
      case CALL_FOCUS: {
        if (!((ViewController<?>) msg.obj).isDestroyed()) {
          ((ViewController<?>) msg.obj).onFocus();
        }
        break;
      }
    }
  }

  private boolean rebaseOnAnimationEnd;
  private int rebaseMessage;
  private int rebaseArg2;
  private Object rebaseObject;

}
