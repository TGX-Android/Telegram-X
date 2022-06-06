/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 02/02/2016 at 23:55
 */
package org.thunderdog.challegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.filegen.SimpleGenerationInfo;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.navigation.ActivityResultHandler;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.EditHeaderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.util.OptionDelegate;
import org.thunderdog.challegram.widget.EditText;
import org.thunderdog.challegram.widget.NoScrollTextView;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;
import me.vkryl.td.TdConstants;

public class CreateChannelController extends ViewController<String[]> implements EditHeaderView.ReadyCallback, OptionDelegate, ActivityResultHandler, Client.ResultHandler, TextView.OnEditorActionListener {
  public CreateChannelController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private EditText descView;
  private ImageView iconView;

  // Header view

  private EditHeaderView headerCell;
  private TextView hintView;

  @Override
  protected View onCreateView (Context context) {
    LinearLayout contentView;

    contentView = new LinearLayout(context);
    contentView.setOrientation(LinearLayout.VERTICAL);
    ViewSupport.setThemedBackground(contentView, R.id.theme_color_filling, this);
    contentView.setPadding(0, Size.getHeaderSizeDifference(false), 0, 0);

    FrameLayoutFix frameLayout = new FrameLayoutFix(context);
    frameLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    frameLayout.setPadding(Screen.dp(16f), Screen.dp(32f), Screen.dp(16f), 0);

    iconView = new ImageView(context);
    iconView.setScaleType(ImageView.ScaleType.CENTER);
    iconView.setImageResource(R.drawable.baseline_info_24);
    iconView.setColorFilter(Theme.iconColor());
    addThemeFilterListener(iconView, R.id.theme_color_icon);
    iconView.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(24f), Screen.dp(46f), Lang.gravity(), Lang.rtl() ? 0 : Screen.dp(6f), 0, Lang.rtl() ? Screen.dp(6f) : 0, 0));
    frameLayout.addView(iconView);

    String[] args = getArguments();

    int marginRight = 0; // Screen.dp(32f);
    int marginLeft = Screen.dp(24f) + Screen.dp(16f) * 2;

    int padding = Screen.dp(9f);

    descView = new EditText(context);
    descView.setId(R.id.edit_description);
    descView.setOnFocusChangeListener((v, hasFocus) -> {
      removeThemeListenerByTarget(iconView);
      iconView.setColorFilter(hasFocus ? Theme.togglerActiveColor() : Theme.iconColor());
      int id = hasFocus ? R.id.theme_color_togglerActive : R.id.theme_color_icon;
      addThemeFilterListener(iconView, id);
    });
    descView.setPadding(0, padding, 0, padding);
    descView.setSingleLine(false);
    descView.setMaxLines(4);
    descView.setHint(Lang.getString(R.string.Description));
    descView.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
    descView.setGravity(Lang.gravity());
    descView.setFilters(new InputFilter[]{new InputFilter.LengthFilter(TdConstants.MAX_CHANNEL_DESCRIPTION_LENGTH)});
    descView.setInputType(descView.getInputType() | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    descView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.NO_GRAVITY, Lang.rtl() ? marginRight : marginLeft, 0, Lang.rtl() ? marginLeft : marginRight, 0));
    frameLayout.addView(descView);

    if (args != null) {
      Views.setText(descView, args[1]);
    }
    contentView.addView(frameLayout);

    hintView = new NoScrollTextView(context);
    hintView.setTextColor(Theme.textDecent2Color());
    hintView.setTypeface(Fonts.getRobotoRegular());
    hintView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
    hintView.setPadding(Screen.dp(Lang.rtl() ? 22f : 72f), Screen.dp(5f), Screen.dp(Lang.rtl() ? 72f : 22f), Screen.dp(16f));
    hintView.setGravity(Lang.gravity());
    hintView.setText(Lang.getString(R.string.DescriptionInfo));
    contentView.addView(hintView);

    // Header view

    headerCell = new EditHeaderView(context, this);
    headerCell.setInputOptions(R.string.ChannelName, InputType.TYPE_TEXT_FLAG_CAP_WORDS);
    headerCell.setNextField(R.id.edit_description);
    headerCell.setReadyCallback(this);
    setLockFocusView(headerCell.getInputView());

    if (args != null) {
      Views.setText(headerCell.getInputView(), args[0]);
    }

    return contentView;
  }

  @Override
  public boolean saveInstanceState (Bundle outState, String keyPrefix) {
    super.saveInstanceState(outState, keyPrefix);
    outState.putString(keyPrefix + "title", getTitle());
    outState.putString(keyPrefix + "description", getDescription());
    return true;
  }

  @Override
  public boolean restoreInstanceState (Bundle in, String keyPrefix) {
    super.restoreInstanceState(in, keyPrefix);
    String title = in.getString(keyPrefix + "title", "");
    String description = in.getString(keyPrefix + "description", "");
    setArguments(new String[] {title, description});
    return true;
  }

  @Override
  public boolean onEditorAction (TextView v, int actionId, KeyEvent event) {
    if (actionId == EditorInfo.IME_ACTION_DONE && getTitle().length() > 0) {
      createChannel();
      return true;
    }
    return false;
  }

  @Override
  public int getId () {
    return R.id.controller_newChannel;
  }

  @Override
  public void hideSoftwareKeyboard () {
    super.hideSoftwareKeyboard();
    Keyboard.hideList(headerCell == null ? null : headerCell.getInputView(), descView);
  }

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override
  protected int getHeaderHeight () {
    return Size.getHeaderBigPortraitSize(false);
  }

  @Override
  protected int getFloatingButtonId () {
    return headerCell.isInputEmpty() ? 0 : R.drawable.baseline_arrow_forward_24;
  }

  @Override
  protected void onFloatingButtonPressed () {
    createChannel();
  }

  @Override
  public boolean onOptionItemPressed (View optionItemView, int id) {
    tdlib.ui().handlePhotoOption(context, id, null, headerCell);
    return true;
  }

  @Override
  public void onActivityResult (int requestCode, int resultCode, Intent data) {
    if (resultCode == Activity.RESULT_OK) {
      tdlib.ui().handlePhotoChange(requestCode, data, headerCell);
    }
  }

  @Override
  public void onReadyStateChanged (boolean ready) {
    if (floatingButton != null) {
      if (ready) {
        floatingButton.show(this);
        setLockFocusView(descView);
      } else {
        floatingButton.hide();
        setLockFocusView(headerCell.getInputView());
      }
    }
  }

  public String getTitle () {
    return headerCell.getInput().trim();
  }

  public String getDescription () {
    return descView.getText().toString();
  }

  public String getPhoto () {
    return headerCell.getPhoto();
  }

  public ImageFile getImageFile () {
    return headerCell.getImageFile();
  }

  public void setDescription (String description) {
    if (description != null) {
      descView.setText(description);
      Views.setSelection(descView, description.length());
    }
  }

  private boolean isCreating;
  private String currentPhoto;
  private ImageFile currentImageFile;

  private void toggleCreating () {
    isCreating = !isCreating;
    headerCell.setInputEnabled(!isCreating);
    descView.setEnabled(!isCreating);
  }

  public void createChannel () {
    if (isCreating) {
      return;
    }

    String title = getTitle();
    if (StringUtils.isEmptyOrBlank(title))
      return;
    String desc = getDescription();

    toggleCreating();

    currentPhoto = getPhoto();
    currentImageFile = getImageFile();

    UI.showProgress(Lang.getString(R.string.ProgressCreateChannel), null, 300l);

    tdlib.client().send(new TdApi.CreateNewSupergroupChat(title, true, desc, null, false), this);
  }

  public void channelCreated (TdApi.Chat chat) {
    if (!isCreating) {
      return;
    }

    toggleCreating();

    if (chat != null) {
      CreateChannelLinkController c = new CreateChannelLinkController(context, tdlib);
      c.setArguments(new CreateChannelLinkController.Args(chat, currentImageFile));
      navigateTo(c);
    }
  }

  private TdApi.Chat chat;

  @Override
  public void onResult (TdApi.Object object) {
    UI.hideProgress();
    switch (object.getConstructor()) {
      case TdApi.Ok.CONSTRUCTOR: {
        // Do nothing. Photo's been set
        return;
      }
      case TdApi.Chat.CONSTRUCTOR: {
        long chatId = TD.getChatId(object);
        chat = tdlib.chatStrict(chatId);
        if (currentPhoto != null) {
          tdlib.client().send(new TdApi.SetChatPhoto(chat.id, new TdApi.InputChatPhotoStatic(new TdApi.InputFileGenerated(currentPhoto, SimpleGenerationInfo.makeConversion(currentPhoto), 0))), this);
        }
        break;
      }
      case TdApi.Error.CONSTRUCTOR: {
        UI.showError(object);
        chat = null;
        break;
      }
      default: {
        Log.unexpectedTdlibResponse(object, TdApi.CreateNewSupergroupChat.class, TdApi.Ok.class, TdApi.Chat.class, TdApi.Error.class);
        return;
      }
    }
    UI.post(() -> channelCreated(chat));
  }

  @Override
  public void destroy () {
    super.destroy();
    if (headerCell != null) {
      headerCell.performDestroy();
    }
  }

  // Updates

  /*@Override
  public void updateChatTitle (long chatId, String title) {
    if (chat != null && chat.id == chatId) {
      chat.title = title;
    }
  }

  @Override
  public void updateChatPhoto (long chatId, TdApi.ChatPhoto photo) {
    if (chat != null && chat.id == chatId) {
      chat.photo = photo;
    }
  }

  @Override
  public void updateChannel (TdApi.Channel channel) {
    if (chat != null && chat.type.getConstructor() == TdApi.ChannelChatInfo.CONSTRUCTOR && ((TdApi.ChannelChatInfo) chat.type).channel.id == channel.id) {
      ((TdApi.ChannelChatInfo) chat.type).channel = channel;
    }
  }*/
}
