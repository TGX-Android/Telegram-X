package org.thunderdog.challegram.tool;

import android.animation.ValueAnimator;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;

import me.vkryl.core.reference.ReferenceList;

/**
 * Date: 3/10/18
 * Author: default
 */

public class Invalidator implements Handler.Callback, ReferenceList.FullnessListener, ViewController.AttachListener, BaseActivity.ActivityListener {
  public interface Target {
    ViewController<?> getTargetParent (Invalidator context);
    long onInvalidateTarget (Invalidator context);
    // void onTargetAttached (Invalidator context, boolean isAttached);
  }

  private final BaseActivity context;
  private final ReferenceList<Target> targets = new ReferenceList<>(false, true, this);
  private final ReferenceList<Target> pendingTargets = new ReferenceList<>();
  private final Handler handler = new Handler(Looper.getMainLooper(), this);
  private boolean isLooping;

  public Invalidator (BaseActivity context) {
    this.context = context;
    context.addActivityListener(this);
  }

  public BaseActivity context () {
    return context;
  }

  public void addTarget (@NonNull Target target) {
    ViewController<?> controller = target.getTargetParent(this);
    if (controller != null) {
      boolean added;
      if (controller.getAttachState()) {
        added = targets.add(target);
        if (added) {
          // target.onTargetAttached(this, true);
        }
      } else {
        added = pendingTargets.add(target);
      }
      if (added) {
        controller.addAttachStateListener(this);
      }
    } else {
      if (targets.add(target)) {
        // target.onTargetAttached(this, true);
      }
    }
  }

  public void removeTarget (@NonNull Target target) {
    ViewController<?> controller = target.getTargetParent(this);
    if (controller != null && !controller.getAttachState()) {
      pendingTargets.remove(target);
    } else {
      if (targets.remove(target)) {
        // target.onTargetAttached(this, false);
      }
    }
  }

  @Override
  public void onAttachStateChanged (ViewController<?> context, NavigationController navigation, boolean isAttached) {
    ReferenceList<Target> fromList, toList;
    if (isAttached) {
      fromList = pendingTargets;
      toList = targets;
    } else {
      fromList = targets;
      toList = pendingTargets;
    }
    for (Target target : fromList) {
      if (target.getTargetParent(this) == context) {
        fromList.remove(target);
        toList.add(target);
        // target.onTargetAttached(this, isAttached);
      }
    }
  }

  @Override
  public boolean handleMessage (Message msg) {
    long minFrameDelay = -1;
    for (Target target : targets) {
      long frameDelay = target.onInvalidateTarget(this);
      if (frameDelay != -1) {
        if (minFrameDelay == -1 || frameDelay < minFrameDelay) {
          minFrameDelay = frameDelay;
        }
      }
    }
    if (isLooping) {
      handler.sendMessageDelayed(Message.obtain(handler, 0), Math.max(getFrameDelay(), minFrameDelay));
    }
    return true;
  }

  private boolean isFull;

  @Override
  public void onFullnessStateChanged (ReferenceList<?> list, boolean isFull) {
    if (this.isFull != isFull) {
      this.isFull = isFull;
      checkLooping();
    }
  }

  private void checkLooping () {
    boolean isLooping = this.isFull && context.getActivityState() == UI.STATE_RESUMED;
    if (this.isLooping != isLooping) {
      this.isLooping = isLooping;
      if (isLooping) {
        handler.sendMessageDelayed(Message.obtain(handler, 0), getFrameDelay());
      } else {
        handler.removeMessages(0);
      }
    }
  }

  private static long getFrameDelay () {
    return Math.max(ValueAnimator.getFrameDelay(), 15l);
  }

  @Override
  public void onActivityPause () {
    checkLooping();
  }

  @Override
  public void onActivityResume () {
    checkLooping();
  }

  @Override
  public void onActivityDestroy () {
    checkLooping();
  }

  @Override
  public void onActivityPermissionResult (int code, boolean granted) { }
}
