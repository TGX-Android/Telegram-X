package org.thunderdog.challegram.ui;

import android.content.Context;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.List;

import me.vkryl.core.lambda.RunnableData;

/**
 * Date: 2019-10-20
 * Author: default
 */
public class SettingsCloudEmojiController extends SettingsCloudController<Settings.EmojiPack> {
  public SettingsCloudEmojiController (Context context, Tdlib tdlib) {
    super(context, tdlib, Settings.TUTORIAL_EMOJI_PACKS, R.string.EmojiInfo, R.string.EmojiCurrent, R.string.EmojiBuiltIn, R.string.EmojiLoaded, R.string.EmojiUpdate, R.string.EmojiInstalling);
  }

  @Override
  public int getId () {
    return R.id.controller_emojiSets;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.EmojiSets);
  }

  @Override
  protected Settings.EmojiPack getCurrentSetting () {
    return Settings.instance().getEmojiPack();
  }

  @Override
  protected void getSettings (RunnableData<List<Settings.EmojiPack>> callback) {
    tdlib.getEmojiPacks(callback);
  }

  @Override
  protected void applySetting (Settings.EmojiPack setting) {
    Emoji.instance().changeEmojiPack(setting);
    if (getThemeController() != null) {
      getThemeController().updateSelectedEmoji();
    }
  }
}
