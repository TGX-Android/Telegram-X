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
 *
 * File created on 10/12/2016
 */
package org.thunderdog.challegram.mediaview;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.widget.FrameLayoutFix;

public class BlurButtonsWrap extends FrameLayoutFix implements View.OnClickListener {
  private MediaFilterNameView nameView;

  private LinearLayout buttonsWrap;
  private int activeIndex = -1;

  public BlurButtonsWrap (Context context) {
    super(context);

    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(Screen.dp(86f), ViewGroup.LayoutParams.MATCH_PARENT);
    params.bottomMargin = Screen.dp(2.5f);

    this.nameView = new  MediaFilterNameView(context);
    this.nameView.setLayoutParams(params);
    this.nameView.setName(Lang.getString(R.string.Blur));
    addView(nameView);

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    params.leftMargin = Screen.dp(64f) + Screen.dp(22f) + Screen.dp(18f) - Screen.dp(12f);
    params.rightMargin = Screen.dp(22f) - Screen.dp(12f);

    buttonsWrap = new LinearLayout(context);
    buttonsWrap.setOrientation(LinearLayout.HORIZONTAL);

    for (int i = 0; i < 3; i++) {
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
      BlurButton button = new BlurButton(context);
      button.setText(Lang.getString(i == 0 ? R.string.Off : i == 1 ? R.string.BlurRadial : R.string.BlurLinear).toUpperCase());
      button.setOnClickListener(this);
      if (i == 0) {
        button.setPadding(Screen.dp(20f), 0, 0, 0);
      } else if (i == 2) {
        button.setPadding(0, 0, Screen.dp(20f), 0);
      }
      button.setLayoutParams(lp);
      buttonsWrap.addView(button);
    }

    buttonsWrap.setLayoutParams(params);
    addView(buttonsWrap);

    setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(42f)));
  }

  public void setData (int selected) {
    if (this.activeIndex != selected) {
      if (this.activeIndex != -1) {
        BlurButton button = (BlurButton) buttonsWrap.getChildAt(activeIndex);
        button.setIsChecked(false, false);
      }
      this.activeIndex = selected;
      if (selected != -1) {
        BlurButton button = (BlurButton) buttonsWrap.getChildAt(selected);
        button.setIsChecked(true, false);
      }
    }
  }

  private Listener listener;

  public void setListener (Listener listener) {
    this.listener = listener;
  }

  public interface Listener {
    void onBlurModeChanged (BlurButtonsWrap wrap, int newMode);
    boolean allowBlurChanges ();
  }

  @Override
  public void onClick (View v) {
    if (listener != null && !listener.allowBlurChanges()) {
      return;
    }
    int i = buttonsWrap.indexOfChild(v);
    if (i != -1 && activeIndex != i) {
      if (this.activeIndex != -1) {
        BlurButton button = (BlurButton) buttonsWrap.getChildAt(activeIndex);
        button.setIsChecked(false, true);
      }
      this.activeIndex = i;
      BlurButton button = (BlurButton) buttonsWrap.getChildAt(i);
      button.setIsChecked(true, true);

      if (listener != null) {
        listener.onBlurModeChanged(this, i);
      }
    }
  }
}
