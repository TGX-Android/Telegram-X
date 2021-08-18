package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.dialogs.SearchManager;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.TelegramViewController;
import org.thunderdog.challegram.telegram.Tdlib;

import me.vkryl.android.widget.FrameLayoutFix;

/**
 * Date: 7/27/17
 * Author: default
 */

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
