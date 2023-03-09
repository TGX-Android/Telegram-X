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
 * File created on 03/12/2016
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.text.TextPaint;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Letters;

import me.vkryl.td.Td;

public class UserContext {
  private final Tdlib tdlib;
  private final long userId;
  private @Nullable TdApi.User user;

  private String fullName;

  private @Nullable ImageFile imageFile;

  private @ThemeColorId
  int avatarColorId;
  private @Nullable
  Letters letters;

  private int lettersWidth;
  private int nameWidth;

  public UserContext (Tdlib tdlib, long userId) {
    this.tdlib = tdlib;
    this.userId = userId;
    TdApi.User user = tdlib.cache().user(userId);
    if (user != null) {
      set(user);
    } else {
      this.avatarColorId = TD.getAvatarColorId(-1, 0);
      this.letters = TD.getLetters();
      this.fullName = "User#" + userId;
    }
  }

  public UserContext (Tdlib tdlib, @NonNull TdApi.User user) {
    this.tdlib = tdlib;
    this.userId = user.id;
    set(user);
  }

  public void set (TdApi.User user) {
    this.user = user;
    this.fullName = TD.getUserName(user.firstName, user.lastName);
    if (user.profilePhoto != null) {
      if (imageFile == null || imageFile.getId() != user.profilePhoto.small.id) {
        this.imageFile = new ImageFile(tdlib, user.profilePhoto.small);
        this.imageFile.setSize(ChatView.getDefaultAvatarCacheSize());
      } else {
        this.imageFile.getFile().local.path = user.profilePhoto.small.local.path;
      }
    } else {
      this.avatarColorId = TD.getAvatarColorId(user.id, tdlib.myUserId());
      this.letters = TD.getLetters(user);
    }
  }

  public final Tdlib tdlib () {
    return tdlib;
  }

  public boolean setStatus (TdApi.UserStatus status) {
    if (user != null && status != null) {
      user.status = status;
      return true;
    }
    return false;
  }

  public boolean hasPhoto () {
    return imageFile != null;
  }

  public long getId () {
    return userId;
  }

  @Nullable
  public TdApi.User getUser () {
    return user;
  }

  public String getUsername () {
    return Td.primaryUsername(user);
  }

  @Nullable
  public ImageFile getImageFile () {
    return imageFile;
  }

  /*public int getAvatarColor () {
    return avatarColor;
  }*/

  @Nullable
  public Letters getLetters () {
    return letters;
  }

  public int getLettersWidth () {
    return lettersWidth;
  }

  // Drawing-related shit

  public void measureTexts (float lettersSizeDp, @Nullable TextPaint namePaint) {
    if (lettersWidth == 0) {
      this.lettersWidth = Paints.measureLetters(letters, lettersSizeDp);
    }
    if (namePaint != null && nameWidth == 0) {
      this.nameWidth = fullName != null ? (int) U.measureText(fullName, namePaint) : 0;
    }
  }

  private String trimmedName;
  private int trimmedNameWidth;

  public void trimName (TextPaint paint, int maxWidth) {
    if (nameWidth > maxWidth) {
      trimmedName = fullName != null ? TextUtils.ellipsize(fullName, paint, maxWidth, TextUtils.TruncateAt.END).toString() : null;
      trimmedNameWidth = (int) U.measureText(trimmedName, paint);
    } else {
      trimmedName = fullName;
      trimmedNameWidth = nameWidth;
    }
  }

  public int getTrimmedNameWidth () {
    return trimmedNameWidth;
  }

  public String getTrimmedName () {
    return trimmedName;
  }

  public String getFirstName () {
    if (user != null) {
      String firstName = user.firstName.trim();
      if (firstName.isEmpty()) {
        return user.lastName.trim();
      } else {
        return firstName;
      }
    }
    return "User#" + userId;
  }

  public void drawPlaceholder (Canvas c, int radius, int left, int top, float lettersSize) {
    c.drawCircle(left + radius, top + radius, radius, Paints.fillingPaint(Theme.getColor(avatarColorId)));
    if (letters != null) {
      Paints.drawLetters(c, letters, left + radius - lettersWidth / 2, top + radius + Screen.dp(5f), lettersSize);
    }
  }
}
