/**
 * File created on 26/03/16 at 03:18
 * Copyright Vyacheslav Krylov, 2014
 */
package me.vkryl.android.animator;

import android.view.View;

import me.vkryl.android.ViewUtils;

public interface Animated {
  default void runOnceViewBecomesReady (View view, Runnable action) {
    ViewUtils.runJustBeforeBeingDrawn(view, action);
  }
}
