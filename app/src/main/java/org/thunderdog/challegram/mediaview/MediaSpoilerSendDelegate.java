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
 * File created on 08/03/2023
 */
package org.thunderdog.challegram.mediaview;

import androidx.annotation.CallSuper;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class MediaSpoilerSendDelegate implements MediaSendDelegate {
  private final AtomicBoolean showCaptionAboveMedia = new AtomicBoolean();
  private final AtomicBoolean hideMedia = new AtomicBoolean();

  @Override
  public final boolean allowHideMedia () {
    return true;
  }

  @Override
  public final boolean isHideMediaEnabled () {
    return hideMedia.get();
  }

  @Override
  @CallSuper
  public void onHideMediaStateChanged (boolean hideMedia) {
    this.hideMedia.set(hideMedia);
  }

  @Override
  public boolean allowShowCaptionAboveMedia () {
    return true;
  }

  @Override
  public boolean showCaptionAboveMedia () {
    return showCaptionAboveMedia.get();
  }

  @Override
  public void onShowCaptionAboveMediaStateChanged (boolean showCaptionAboveMedia) {
    this.showCaptionAboveMedia.set(showCaptionAboveMedia);
  }
}
