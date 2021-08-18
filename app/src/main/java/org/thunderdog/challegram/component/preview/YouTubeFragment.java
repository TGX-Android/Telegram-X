/**
 * File created on 25/02/16 at 22:11
 * Copyright Vyacheslav Krylov, 2014
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
