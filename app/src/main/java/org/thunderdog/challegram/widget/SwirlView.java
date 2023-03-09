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

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.core.lambda.CancellableRunnable;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
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

    @DrawableRes int resId = getDrawable(this.state, state, animate);
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

  @DrawableRes private static int getDrawable(State currentState, State newState, boolean animate) {
    switch (newState) {
      case OFF:
        if (animate) {
          if (currentState == State.ON) {
            return R.drawable.swirl_draw_off_animation;
          } else if (currentState == State.ERROR) {
            return R.drawable.swirl_error_off_animation;
          }
        }

        return 0;
      case ON:
        if (animate) {
          if (currentState == State.OFF) {
            return R.drawable.swirl_draw_on_animation;
          } else if (currentState == State.ERROR) {
            return R.drawable.swirl_error_state_to_fp_animation;
          }
        }

        return R.drawable.swirl_fingerprint;
      case ERROR:
        if (animate) {
          if (currentState == State.ON) {
            return R.drawable.swirl_fp_to_error_state_animation;
          } else if (currentState == State.OFF) {
            return R.drawable.swirl_error_on_animation;
          }
        }

        return R.drawable.swirl_error;
      default:
        throw new IllegalArgumentException("Unknown state: " + newState);
    }
  }
}
