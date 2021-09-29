/**
 * File created on 03/05/15 at 11:21
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.text.TextUtils;
import android.view.MotionEvent;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.text.Letters;
import org.thunderdog.challegram.util.text.Text;

public class TGMessageContact extends TGMessage implements TdlibCache.UserDataChangeListener {
  private String name;
  private boolean nameFake;
  private String phone;

  private long userId;
  private TdApi.User user;

  private ImageFile avatar;
  private @ThemeColorId
  int avatarColorId;
  private Letters letters;

  private String tName;
  private String tPhone;

  private int nameWidth, phoneWidth;
  private int lettersWidth; // int pContactRight = maxTextWidth + getContentX() + Screen.dp(10f) + avatarSize + Screen.dp(6f);

  public TGMessageContact (MessagesManager context, TdApi.Message msg, TdApi.MessageContact rawContact) {
    super(context, msg);

    if (contactHeight == 0) {
      initSizes();
    }

    TdApi.Contact contact = rawContact.contact;
    this.name = TD.getUserName(contact.firstName, contact.lastName);
    this.nameFake = Text.needFakeBold(name);
    this.letters = TD.getLetters(contact.firstName, contact.lastName);
    this.phone = Strings.formatPhone(contact.phoneNumber, contact.userId != 0, true);
    this.userId = contact.userId;

    if (contact.userId != 0) {
      this.user = tdlib.cache().user(contact.userId);
      tdlib.cache().addUserDataListener(contact.userId, this);
    }
  }

  @Override
  protected void onMessageContainerDestroyed () {
    if (userId != 0) {
      tdlib.cache().removeUserDataListener(userId, this);
    }
  }

  @Override
  public void onUserUpdated (final TdApi.User user) {
    tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        TGMessageContact.this.user = user;
        buildAvatar();
        // buildName();
        invalidateContent();
        invalidate();
      }
    });
  }

  private int lastMaxWidth;

  @Override
  protected void buildContent (int maxWidth) {
    buildAvatar();
    lettersWidth = Paints.measureLetters(letters, LETTERS_SIZE);
    lastMaxWidth = maxWidth - textLeft;
    buildName();
  }

  private void buildName () {
    if (lastMaxWidth > 0) {
      tName = TextUtils.ellipsize(name, Paints.getBoldPaint15(nameFake), lastMaxWidth, TextUtils.TruncateAt.END).toString();
      nameWidth = (int) U.measureText(tName, Paints.getBoldPaint15(nameFake));

      tPhone = TextUtils.ellipsize(phone, Paints.getTextPaint15(), lastMaxWidth, TextUtils.TruncateAt.END).toString();
      phoneWidth = (int) U.measureText(tPhone, Paints.getTextPaint15());
    }
  }

  private void buildAvatar () {
    if (user == null || TD.isPhotoEmpty(user.profilePhoto)) {
      avatar = null;
      avatarColorId = TD.getAvatarColorId(TD.isUserDeleted(user) ? -1 : userId, tdlib.myUserId());
    } else {
      avatar = new ImageFile(tdlib, user.profilePhoto.small);
      avatar.setSize(avatarSize);
    }
  }

  @Override
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth, Receiver preview, Receiver receiver) {
    if (!useBubbles()) {
      c.drawRect(startX, startY, startX + Screen.dp(3f), startY + contactHeight, Paints.fillingPaint(Theme.chatVerticalLineColor()));
      startX += Screen.dp(10f);
    }
    startY += Screen.dp(1f);
    if (avatar == null) {
      c.drawCircle(startX + avatarRadius, startY + avatarRadius, avatarRadius, Paints.fillingPaint(Theme.getColor(avatarColorId)));
      Paints.drawLetters(c, letters, startX + avatarRadius - (int) (lettersWidth / 2f), startY + lettersTop, LETTERS_SIZE);
    } else {
      receiver.setBounds(startX, startY, startX + avatarSize, startY + avatarSize);
      if (receiver.needPlaceholder()) {
        c.drawCircle(startX + avatarRadius, startY + avatarRadius, avatarRadius, Paints.getPlaceholderPaint());
      }
      receiver.draw(c);
    }

    startX += avatarSize + Screen.dp(6f);

    c.drawText(tName, startX, startY + nameTop, Paints.getBoldPaint15(nameFake, getChatAuthorColor())); // Paints.getAuthorPaint15(TGDataCache.instance().getMyUserId() == userId, userId)
    c.drawText(tPhone, startX, startY + phoneTop, Paints.getRegularTextPaint(15f, getDecentColor()));
  }

  @Override
  protected int getContentWidth () {
    return (useBubbles() ? 0 : Screen.dp(10f)) + avatarSize + Screen.dp(12f) + Math.max(nameWidth, phoneWidth);
  }

  @Override
  protected int getBottomLineContentWidth () {
    return Screen.dp(1f) + avatarSize + Screen.dp(6f) + phoneWidth;
  }

  @Override
  protected int getContentHeight () {
    return contactHeight;
  }

  @Override
  public int getImageContentRadius (boolean isPreview) {
    return avatarRadius;
  }

  @Override
  public void requestImage (ImageReceiver receiver) {
    receiver.requestFile(avatar);
  }

  @Override
  public boolean needImageReceiver () {
    return true;
  }

  private float currentTouchX, currentTouchY;

  @Override
  public boolean onTouchEvent (MessageView view, MotionEvent e) {
    if (super.onTouchEvent(view, e)) return true;

    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        float x = e.getX();
        float y = e.getY();

        int startX = getContentX() + Screen.dp(10f);
        if (x >= startX && x <= startX + avatarSize + Screen.dp(6f) + Math.max(nameWidth, phoneWidth) && y >= getContentY() + Screen.dp(1f) && y <= getContentY() + Screen.dp(1f) + avatarSize) {
          currentTouchX = x;
          currentTouchY = y;
          return true;
        }

        currentTouchX = 0f;
        currentTouchY = 0f;

        break;
      }
      case MotionEvent.ACTION_CANCEL: {
        currentTouchX = 0f;
        currentTouchY = 0f;

        break;
      }
      case MotionEvent.ACTION_UP: {
        if (currentTouchX != 0f && currentTouchY != 0f) {
          if (Math.abs(e.getX() - currentTouchX) < Screen.getTouchSlop() && Math.abs(e.getY()  - currentTouchY) < Screen.getTouchSlop()) {
            if (userId != 0) {
              tdlib.ui().openPrivateProfile(controller(), userId, openParameters());
            } else {
              UI.openNumber(phone);
            }
            return true;
          }
          currentTouchX = 0f;
          currentTouchY = 0f;
        }
        break;
      }
    }
    return false;
  }

  // Sizes

  private static int contactHeight, avatarRadius, avatarSize, textLeft, nameTop, phoneTop, lettersTop;

  private static void initSizes () {
    textLeft = Screen.dp(57f);
    contactHeight = Screen.dp(43f);
    avatarRadius = Screen.dp(20.5f);
    avatarSize = avatarRadius * 2;
    nameTop = Screen.dp(16f);
    phoneTop = Screen.dp(36f);
    lettersTop = Screen.dp(26f);
  }
}
