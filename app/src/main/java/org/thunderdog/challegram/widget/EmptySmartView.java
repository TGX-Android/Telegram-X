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
 * File created on 28/01/2017
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.view.View;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Letters;

import me.vkryl.core.StringUtils;

public class EmptySmartView extends View {
  private static final float TITLE_TEXT_SIZE_DP = 16f;

  public static final int MODE_EMPTY_MEDIA = 1;
  public static final int MODE_EMPTY_FILES = 2;
  public static final int MODE_EMPTY_MUSIC = 3;
  public static final int MODE_EMPTY_LINKS = 4;
  public static final int MODE_EMPTY_MEMBERS = 5;
  public static final int MODE_EMPTY_GROUPS = 6;
  public static final int MODE_EMPTY_MEMBERS_RESTRICTED = 7;
  public static final int MODE_EMPTY_MEMBERS_BANNED = 8;
  public static final int MODE_EMPTY_GIFS = 9;
  public static final int MODE_EMPTY_VOICE = 10;
  public static final int MODE_EMPTY_VIDEO_MESSAGES = 11;
  public static final int MODE_EMPTY_PHOTO = 12;
  public static final int MODE_EMPTY_VIDEO = 13;
  public static final int MODE_EMPTY_RESTRICTED = 14;
  public static final int MODE_EMPTY_RESULTS = 15;

  private Letters title, description;
  private Drawable icon;
  private int mode;

  public EmptySmartView (Context context) {
    super(context);
  }

  public void setMode (int mode, boolean isChannel, String arg) {
    if (this.mode != mode) {
      int iconRes;
      int titleRes, descRes;

      switch (mode) {
        case MODE_EMPTY_MEDIA: {
          titleRes = R.string.NoMediaToShow;
          descRes = isChannel ? R.string.NoMediaToShowInChannel : R.string.NoMediaToShowInChat;
          iconRes = R.drawable.baseline_image_96;
          break;
        }
        case MODE_EMPTY_PHOTO: {
          titleRes = R.string.NoPhotosToShow;
          descRes = isChannel ? R.string.NoPhotosToShowInChannel : R.string.NoPhotosToShowInChat;
          iconRes = R.drawable.baseline_camera_alt_96;
          break;
        }
        case MODE_EMPTY_VIDEO: {
          titleRes = R.string.NoVideosToShow;
          descRes = isChannel ? R.string.NoVideosToShowInChannel : R.string.NoVideosToShowInChat;
          iconRes = R.drawable.baseline_video_library_96;
          break;
        }
        case MODE_EMPTY_FILES: {
          titleRes = R.string.NoDocumentsToShow;
          descRes = isChannel ? R.string.NoDocumentsToShowInChannel : R.string.NoDocumentsToShowInChat;
          iconRes = R.drawable.baseline_insert_drive_file_96;
          break;
        }
        case MODE_EMPTY_MUSIC: {
          titleRes = R.string.NoMusicToShow;
          descRes = isChannel ? R.string.NoMusicToShowInChannel : R.string.NoMusicToShowInChat;
          iconRes = R.drawable.baseline_music_note_96;
          break;
        }
        case MODE_EMPTY_LINKS: {
          titleRes = R.string.NoLinksToShow;
          descRes = isChannel ? R.string.NoLinksToShowInChannel : R.string.NoLinksToShowInChat;
          iconRes = R.drawable.baseline_language_96;
          break;
        }
        case MODE_EMPTY_VOICE: {
          titleRes = R.string.NoVoiceToShow;
          descRes = isChannel ? R.string.NoVoiceToShowInChannel : R.string.NoVoiceToShowInChat;
          iconRes = R.drawable.baseline_mic_96;
          break;
        }
        case MODE_EMPTY_VIDEO_MESSAGES: {
          titleRes = R.string.NoVideoToShow;
          descRes = isChannel ? R.string.NoVideoToShowInChannel : R.string.NoVideoToShowInChat;
          iconRes = R.drawable.deproko_baseline_msg_video_96;
          break;
        }
        case MODE_EMPTY_GIFS: {
          titleRes = R.string.NoGifsToShow;
          descRes = isChannel ? R.string.NoGifsToShowInChannel : R.string.NoGifsToShowInChat;
          iconRes = R.drawable.baseline_gif_96;
          break;
        }
        case MODE_EMPTY_MEMBERS: {
          titleRes = R.string.NoMembersToShow;
          descRes = R.string.NoMembersToShowDesc;
          iconRes = R.drawable.baseline_search_96;
          break;
        }
        case MODE_EMPTY_RESULTS: {
          titleRes = R.string.NoResultsToShow;
          descRes = R.string.NoResultsToShowDesc;
          iconRes = R.drawable.baseline_search_96;
          break;
        }
        case MODE_EMPTY_GROUPS: {
          titleRes = R.string.NoGroupsToShow;
          descRes = R.string.GroupsWillBeShownHere;
          iconRes = R.drawable.baseline_group_96;
          break;
        }
        case MODE_EMPTY_MEMBERS_RESTRICTED:
        case MODE_EMPTY_MEMBERS_BANNED: {
          titleRes = R.string.NoMembersToShow;
          descRes = mode == MODE_EMPTY_MEMBERS_BANNED ? R.string.BannedWillBeShownHere : R.string.RestrictedWillBeShownHere;
          iconRes = R.drawable.baseline_group_96;
          break;
        }
        case MODE_EMPTY_RESTRICTED: {
          titleRes = R.string.MediaRestricted;
          descRes = 0;
          if (!StringUtils.isEmpty(arg))
            description = new Letters(arg);
          else
            description = null;
          iconRes = R.drawable.baseline_block_96;
          break;
        }
        default: {
          return;
        }
      }

      this.mode = mode;
      if (titleRes != 0) {
        this.title = new Letters(Lang.getString(titleRes));
      }
      if (descRes != 0) {
        this.description = new Letters(Lang.getString(descRes));
      }
      this.icon = Drawables.get(iconRes);

      invalidate();
    }
  }

  private int lastWidth;

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    final int width = getMeasuredWidth();
    if (lastWidth != width) {
      lastWidth = width;
      layoutTexts(width);
    }
  }

  private int titleWidth;
  private StaticLayout staticLayout;

  private void layoutTexts (int width) {
    titleWidth = title != null ? (int) U.measureText(title.text, Paints.getMediumTextPaint(TITLE_TEXT_SIZE_DP, title.needFakeBold)) : 0;
    staticLayout = description != null ? new StaticLayout(description.text, Paints.getTextPaint16(), mode == MODE_EMPTY_RESTRICTED ? width - Screen.dp(8f) * 2 : width, Layout.Alignment.ALIGN_CENTER, 1f, Screen.dp(2.5f), false) : null;
  }

  @Override
  protected void onDraw (Canvas c) {
    if (title == null || description == null || icon == null) {
      return;
    }

    final int viewWidth = getMeasuredWidth();
    final int viewHeight = getMeasuredHeight() - getPaddingTop();
    final int totalHeight = icon.getMinimumHeight() + Screen.dp(38f) + Screen.dp(18f) + staticLayout.getHeight() + Screen.dp(24f);
    final int visibleHeight = Math.max(totalHeight, viewHeight - getTop());


    c.save();
    c.translate(0, getPaddingTop() + visibleHeight / 2 - totalHeight / 2);

    // c.drawRect(0, 0, getMeasuredWidth(), totalHeight, Paints.fillingPaint(0x4cff0000));

    Drawables.draw(c, icon, viewWidth / 2 - icon.getMinimumWidth() / 2, Screen.dp(12f), Paints.getBackgroundIconPorterDuffPaint());
    if (title != null) {
      c.drawText(title.text, viewWidth / 2 - titleWidth / 2, icon.getMinimumHeight() + Screen.dp(32f) + Screen.dp(12f), Paints.getMediumTextPaint(TITLE_TEXT_SIZE_DP, Theme.textDecent2Color(), title.needFakeBold));
    }

    if (staticLayout != null) {
      Paints.getTextPaint16(Theme.textDecent2Color()); // Forcing color of the paint
      c.translate(viewWidth / 2 - staticLayout.getWidth() / 2, totalHeight - staticLayout.getHeight() - Screen.dp(12f));
      staticLayout.draw(c);
    }
    c.restore();
  }
}
