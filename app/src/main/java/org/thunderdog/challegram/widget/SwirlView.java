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
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.RequiresApi;

import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.core.lambda.CancellableRunnable;
import tgx.flavor.Flavor;

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
public final class SwirlView extends ImageView {
  // Keep in sync with attrs.
  public enum State {
    OFF,
    ON,
    ERROR,
  }

  private State state = State.OFF;
  private CancellableRunnable disabler;

  public SwirlView(Context context) {
    super(context);
  }

  public State getState () {
    return state;
  }

  public void showDelayed (int delay) {
    if (state == State.OFF) {
      if (delay > 0) {
        UI.post(() -> {
          if (state == State.OFF) {
            setState(State.ON);
          }
        }, delay);
      } else {
        setState(State.ON);
      }
    }
  }

  public void showError (boolean isFatal) {
    if (state == State.ERROR) {
      /*if (disabler != null) {
        UI.removePendingRunnable(disabler);
        postDelayed(disabler, 1000);
      }*/
      return;
    }
    if (disabler != null) {
      disabler.cancel();
      disabler = null;
    }
    State savedState = state;
    setState(State.ERROR);
    if (!isFatal) {
      disabler = new CancellableRunnable() {
        @Override
        public void act () {
          if (disabler == this) {
            setState(savedState);
          }
        }
      };
      disabler.removeOnCancel(UI.getAppHandler());
      UI.post(disabler, 1000);
    }
  }

  public void setState(State state) {
    setState(state, true);
  }

  public void setState(State state, boolean animate) {
    if (state == this.state) return;

    if (disabler != null) {
      removeCallbacks(disabler);
    }

    @DrawableRes int resId = Flavor.getSwirlDrawable(this.state, state, animate);
    if (resId == 0) {
      setImageDrawable(null);
    } else {
      Drawable icon = Drawables.get(getResources(), resId);
      setImageDrawable(icon);
      if (icon instanceof Animatable) {
        ((Animatable) icon).start();
      }
    }

    this.state = state;
  }
}
