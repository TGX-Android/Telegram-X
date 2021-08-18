package org.thunderdog.challegram.data;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.util.text.Letters;

/**
 * Date: 12/14/17
 * Author: default
 */
public class AvatarInfo {
  public final Tdlib tdlib;
  public final int userId;
  public ImageFile imageFile;

  public int avatarColorId;
  public Letters letters;

  public float lettersWidth15dp;

  public AvatarInfo (Tdlib tdlib, int userId) {
    this.tdlib = tdlib;
    this.userId = userId;
    updateUser();
  }

  public void updateUser () {
    TdApi.User user = tdlib.cache().user(userId);
    letters = TD.getLetters(user);
    avatarColorId = TD.getAvatarColorId(user, tdlib.myUserId());
    imageFile = TD.getAvatar(tdlib, user);
    lettersWidth15dp = Paints.measureLetters(letters, 15f);
  }


}
