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
 * File created on 25/02/2016 at 22:11
 */
package org.thunderdog.challegram.component.preview;

import androidx.annotation.Nullable;

import com.google.android.youtube.player.YouTubePlayer;
import com.google.android.youtube.player.YouTubePlayerFragment;

import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;

public class YouTubeFragment extends YouTubePlayerFragment {

  private @Nullable YouTubeFragmentHelper helper;

  public static YouTubeFragment newInstance (YouTubeFragmentHelper helper) {
    YouTubeFragment fragment;
    
    fragment = new YouTubeFragment();
    fragment.helper = helper;
    fragment.initialize(BuildConfig.YOUTUBE_API_KEY, fragment.helper);

    return fragment;
  }

  @Override
  public void onResume () {
    super.onResume();
    if (Log.isEnabled(Log.TAG_YOUTUBE)) {
      Log.i("YouTubeFragment.onResume");
    }
    if (helper != null) {
      helper.onResume();
    }
  }

  public @Nullable YouTubePlayer getPlayer () {
    return helper != null ? helper.getPlayer() : null;
  }

  public void destroy () {
    if (helper != null) {
      helper.onDestroy();
    }
    try {
      onDestroy();
    } catch (Throwable ignored) { }
    try {
      getActivity().getFragmentManager().beginTransaction().remove(this).commit();
    } catch (Throwable ignored) { }
  }

  public boolean isStopped () {
    return helper != null && helper.isStopped();
  }
}
