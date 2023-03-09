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
 * File created on 27/07/2017
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.dialogs.SearchManager;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.TelegramViewController;
import org.thunderdog.challegram.telegram.Tdlib;

import me.vkryl.android.widget.FrameLayoutFix;

public class HashtagController extends TelegramViewController<String> {
  public HashtagController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_hashtag;
  }

  @Override
  public CharSequence getName () {
    return getArgumentsStrict();
  }

  @Override
  protected int getHeaderColorId () {
    return R.id.theme_color_filling;
  }

  @Override
  protected int getHeaderIconColorId () {
    return R.id.theme_color_headerLightIcon;
  }

  @Override
  protected int getHeaderTextColorId () {
    return R.id.theme_color_text;
  }

  @Override
  protected String getChatSearchInitialQuery () {
    return getArgumentsStrict();
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override
  protected int getChatSearchFlags () {
    return SearchManager.FLAG_NEED_MESSAGES | SearchManager.FLAG_NO_CHATS;
  }

  @Override
  protected View onCreateView (Context context) {
    FrameLayoutFix wrapView = new FrameLayoutFix(context);
    wrapView.addView(generateChatSearchView(null));
    return wrapView;
  }

  @Override
  public View getViewForApplyingOffsets () {
    return getChatSearchView();
  }
}
