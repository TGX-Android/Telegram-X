package org.thunderdog.challegram.loader;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.AnyThread;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.theme.ThemeProperty;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.BounceAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.FutureBool;
import me.vkryl.td.ChatId;
import me.vkryl.td.Td;

public class AvatarReceiver implements Receiver, ChatListener, TdlibCache.UserDataChangeListener, TdlibCache.UserStatusChangeListener, TdlibCache.SupergroupDataChangeListener, TdlibCache.BasicGroupDataChangeListener {
  public static class FullChatPhoto {
    public final @NonNull TdApi.ChatPhoto chatPhoto;
    public final long chatId;

    public FullChatPhoto (@NonNull TdApi.ChatPhoto chatPhoto, long chatId) {
      this.chatPhoto = chatPhoto;
      this.chatId = chatId;
    }
  }

  private final ComplexReceiver complexReceiver;
  private final BoolAnimator isFullScreen;
  private final BoolAnimator isForum;
  private final BounceAnimator isOnline;
  private final BoolAnimator allowOnline;
  private boolean isDetached;

  private boolean displayFullSizeOnlyInFullScreen;
  private @ThemeProperty int defaultAvatarRadiusPropertyId, forumAvatarRadiusPropertyId;
  private @ThemeColorId int contentCutOutColorId;
  private @ScaleMode int scaleMode;
  private float primaryPlaceholderRadius;

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    ScaleMode.NONE,
    ScaleMode.FIT_CENTER,
    ScaleMode.CENTER_CROP
  })
  public @interface ScaleMode {
    int
      NONE = 0,
      FIT_CENTER = 1,
      CENTER_CROP = 2;
  }

  @Retention(RetentionPolicy.SOURCE)
  @IntDef(value = {
    Options.NONE,
    Options.FULL_SIZE,
    Options.FORCE_ANIMATION,
    Options.FORCE_FORUM,
    Options.NO_UPDATES,
    Options.SHOW_ONLINE
  }, flag = true)
  public @interface Options {
    int
      NONE = 0,
      FULL_SIZE = 1,
      FORCE_ANIMATION = 1 << 1,
      FORCE_FORUM = 1 << 2,
      NO_UPDATES = 1 << 3,
      SHOW_ONLINE = 1 << 4
    ;
  }

  public AvatarReceiver (@Nullable View view) {
    this.complexReceiver = new ComplexReceiver(view);
    FactorAnimator.Target target = (id, factor, fraction, callee) -> invalidate();
    this.isFullScreen = new BoolAnimator(0, target, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
    this.isForum = new BoolAnimator(0, target, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
    this.isOnline = new BounceAnimator(target);
    this.allowOnline = new BoolAnimator(0, target, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, true);
  }

  public void setDisplayFullSizeOnlyInFullScreen (boolean displayFullSizeOnlyInFullScreen) {
    if (this.displayFullSizeOnlyInFullScreen != displayFullSizeOnlyInFullScreen) {
      this.displayFullSizeOnlyInFullScreen = displayFullSizeOnlyInFullScreen;
      invalidate();
    }
  }

  public void setScaleMode (@ScaleMode int scaleMode) {
    if (this.scaleMode != scaleMode) {
      this.scaleMode = scaleMode;
      requestResources(true);
    }
  }

  public void setAvatarRadiusPropertyIds (@ThemeProperty int defaultAvatarRadiusPropertyId, @ThemeProperty int forumAvatarRadiusPropertyId) {
    if (this.defaultAvatarRadiusPropertyId != defaultAvatarRadiusPropertyId || this.forumAvatarRadiusPropertyId != forumAvatarRadiusPropertyId) {
      this.defaultAvatarRadiusPropertyId = defaultAvatarRadiusPropertyId;
      this.forumAvatarRadiusPropertyId = forumAvatarRadiusPropertyId;
      invalidate();
    }
  }

  public void setContentCutOutColorId (@ThemeColorId int colorId) {
    if (this.contentCutOutColorId != colorId) {
      this.contentCutOutColorId = colorId;
      invalidate();
    }
  }

  public void clearAvatarRadiusPropertyIds () {
    //noinspection WrongConstant
    setAvatarRadiusPropertyIds(0, 0);
  }

  public void setFullScreen (boolean isFullScreen, boolean animated) {
    this.isFullScreen.setValue(isFullScreen, animated);
  }

  public void forceFullScreen (boolean isFullScreen, float floatValue) {
    this.isFullScreen.forceValue(isFullScreen, floatValue);
  }

  public void setAllowOnline (boolean allowOnline, boolean animated) {
    this.allowOnline.setValue(allowOnline, animated);
  }

  public void forceAllowOnline (boolean allowOnline, float floatValue) {
    this.allowOnline.forceValue(allowOnline, floatValue);
  }

  public void setPrimaryPlaceholderRadius (float radius) {
    this.primaryPlaceholderRadius = radius;
    if (requestedPlaceholder != null) {
      invalidate();
    }
  }

  // Public API

  public boolean requestPlaceholder (Tdlib tdlib, AvatarPlaceholder.Metadata specificPlaceholder, @Options int options) {
    return requestData(tdlib, DataType.PLACEHOLDER, specificPlaceholder != null ? specificPlaceholder.colorId : 0, specificPlaceholder, null, null, options | Options.NO_UPDATES);
  }

  public boolean isDisplayingPlaceholder (AvatarPlaceholder.Metadata specificPlaceholder) {
    return specificPlaceholder != null &&
      dataType == DataType.PLACEHOLDER &&
      this.specificPlaceholder == specificPlaceholder;
  }

  public boolean requestSpecific (Tdlib tdlib, FullChatPhoto specificPhoto, @Options int options) {
    return requestData(tdlib, DataType.SPECIFIC_PHOTO, specificPhoto != null ? specificPhoto.chatPhoto.id : 0, null, specificPhoto, null, options | Options.NO_UPDATES);
  }

  public boolean isDisplayingSpecificPhoto (TdApi.ChatPhoto specificPhoto) {
    return specificPhoto != null &&
      dataType == DataType.SPECIFIC_PHOTO &&
      dataId == specificPhoto.id;
  }

  public boolean requestSpecific (Tdlib tdlib, ImageFile specificFile, @Options int options) {
    return requestData(tdlib, DataType.SPECIFIC_FILE, specificFile != null ? specificFile.getId() : 0, null, null, specificFile, options);
  }

  public void requestMessageSender (@Nullable Tdlib tdlib, @Nullable TdApi.MessageSender sender, @Options int options) {
    if (sender != null) {
      switch (sender.getConstructor()) {
        case TdApi.MessageSenderUser.CONSTRUCTOR: {
          long userId = ((TdApi.MessageSenderUser) sender).userId;
          requestUser(tdlib, userId, options);
          break;
        }
        case TdApi.MessageSenderChat.CONSTRUCTOR: {
          long chatId = ((TdApi.MessageSenderChat) sender).chatId;
          if (tdlib != null && tdlib.isSelfChat(chatId)) {
            requestUser(tdlib, tdlib.chatUserId(chatId), options);
          } else {
            requestChat(tdlib, chatId, options);
          }
          break;
        }
        default:
          throw new UnsupportedOperationException(sender.toString());
      }
    } else {
      clear();
    }
  }

  public boolean requestUser (Tdlib tdlib, long userId, @Options int options) {
    return requestData(tdlib, DataType.USER, userId, null, null, null, options);
  }

  public boolean isDisplayingUser (long userId) {
    return dataType == DataType.USER && dataId == userId;
  }

  public boolean requestChat (Tdlib tdlib, long chatId, @Options int options) {
    return requestData(tdlib, DataType.CHAT, chatId, null, null, null, options);
  }

  public boolean isDisplayingChat (long chatId) {
    return dataType == DataType.CHAT && dataId == chatId;
  }

  public boolean isDisplayingUserChat (long userId) {
    return userId != 0 &&
      dataType == DataType.CHAT && ChatId.isUserChat(dataId) &&
      additionalDataId == userId;
  }

  public boolean isDisplayingSupergroupChat (long supergroupId) {
    return supergroupId != 0 &&
      dataType == DataType.CHAT && ChatId.isSupergroup(dataId) &&
      additionalDataId == supergroupId;
  }

  public boolean isDisplayingBasicGroupChat (long basicGroupId) {
    return basicGroupId != 0 &&
      dataType == DataType.CHAT && ChatId.isBasicGroup(dataId) &&
      additionalDataId == basicGroupId;
  }

  // Auto-update event listeners

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    DataType.NONE,
    DataType.PLACEHOLDER,
    DataType.SPECIFIC_PHOTO,
    DataType.SPECIFIC_FILE,
    DataType.USER,
    DataType.CHAT
  })
  private @interface DataType {
    int
      NONE = 0,
      PLACEHOLDER = 1,
      SPECIFIC_PHOTO = 2,
      SPECIFIC_FILE = 3,
      USER = 4,
      CHAT = 5;
  }

  private Tdlib tdlib;
  private @DataType int dataType = DataType.NONE;
  private long dataId, additionalDataId;
  private AvatarPlaceholder.Metadata specificPlaceholder;
  private FullChatPhoto specificPhoto;
  private ImageFile specificFile;
  private @Options int options;

  private void subscribeToUpdates () {
    switch (dataType) {
      case DataType.NONE:
      case DataType.PLACEHOLDER:
      case DataType.SPECIFIC_FILE:
        break;
      case DataType.SPECIFIC_PHOTO:
        if (this.additionalDataId != 0) {
          tdlib.cache().subscribeToSupergroupUpdates(this.additionalDataId, this);
        }
        break;
      case DataType.USER:
        this.tdlib.cache().subscribeToUserUpdates(this.dataId, this);
        break;
      case DataType.CHAT: {
        this.tdlib.listeners().subscribeToChatUpdates(this.dataId, this);
        switch (ChatId.getType(this.dataId)) {
          case TdApi.ChatTypePrivate.CONSTRUCTOR:
          case TdApi.ChatTypeSecret.CONSTRUCTOR: {
            if (this.additionalDataId != 0) {
              tdlib.cache().subscribeToUserUpdates(this.additionalDataId, this);
            }
            break;
          }
          case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
            if (this.additionalDataId != 0) {
              tdlib.cache().subscribeToSupergroupUpdates(this.additionalDataId, this);
            }
            break;
          }
          case TdApi.ChatTypeBasicGroup.CONSTRUCTOR: {
            if (this.additionalDataId != 0) {
              tdlib.cache().subscribeToGroupUpdates(this.additionalDataId, this);
            }
            break;
          }
        }
        break;
      }
    }
  }

  private void unsubscribeFromUpdates () {
    switch (this.dataType) {
      case DataType.NONE:
      case DataType.PLACEHOLDER:
      case DataType.SPECIFIC_FILE:
        break;
      case DataType.SPECIFIC_PHOTO:
        if (this.additionalDataId != 0) {
          this.tdlib.cache().unsubscribeFromSupergroupUpdates(this.additionalDataId, this);
        }
        break;
      case DataType.USER: {
        this.tdlib.cache().unsubscribeFromUserUpdates(this.dataId, this);
        break;
      }
      case DataType.CHAT:
        this.tdlib.listeners().unsubscribeFromChatUpdates(this.dataId, this);
        if (this.additionalDataId != 0) {
          switch (ChatId.getType(this.dataId)) {
            case TdApi.ChatTypePrivate.CONSTRUCTOR:
            case TdApi.ChatTypeSecret.CONSTRUCTOR:
              this.tdlib.cache().unsubscribeFromUserUpdates(this.additionalDataId, this);
              break;
            case TdApi.ChatTypeSupergroup.CONSTRUCTOR:
              this.tdlib.cache().unsubscribeFromSupergroupUpdates(this.additionalDataId, this);
              break;
            case TdApi.ChatTypeBasicGroup.CONSTRUCTOR:
              this.tdlib.cache().unsubscribeFromGroupUpdates(this.additionalDataId, this);
              break;
          }
        }
        break;
    }
  }

  @UiThread
  private boolean requestData (@Nullable Tdlib tdlib, @DataType int dataType, long dataId, @Nullable AvatarPlaceholder.Metadata specificPlaceholder, FullChatPhoto specificPhoto, ImageFile specificFile, @Options int options) {
    if (!UI.inUiThread())
      throw new IllegalStateException();
    if (dataType == DataType.NONE || dataId == 0 || (tdlib == null && !(dataType == DataType.SPECIFIC_FILE || dataType == DataType.PLACEHOLDER))) {
      dataType = DataType.NONE;
      dataId = 0;
      tdlib = null;
      specificPhoto = null;
      specificPlaceholder = null;
      specificFile = null;
      options = Options.NONE;
    }
    if (this.tdlib != tdlib || this.dataType != dataType || this.dataId != dataId) {
      unsubscribeFromUpdates();

      this.dataType = dataType;
      this.dataId = dataId;
      this.tdlib = tdlib;
      this.specificPlaceholder = specificPlaceholder;
      this.specificPhoto = specificPhoto;
      this.specificFile = specificFile;
      this.options = options;
      if (dataType == DataType.CHAT) {
        switch (ChatId.getType(dataId)) {
          case TdApi.ChatTypePrivate.CONSTRUCTOR:
          case TdApi.ChatTypeSecret.CONSTRUCTOR:
            this.additionalDataId = tdlib.chatUserId(this.dataId);
            break;
          case TdApi.ChatTypeBasicGroup.CONSTRUCTOR:
            this.additionalDataId = ChatId.toBasicGroupId(this.dataId);
            break;
          case TdApi.ChatTypeSupergroup.CONSTRUCTOR:
            this.additionalDataId = ChatId.toSupergroupId(this.dataId);
            break;
          default:
            throw new UnsupportedOperationException(Long.toString(this.dataId));
        }
      } else if (dataType == DataType.SPECIFIC_PHOTO) {
        this.additionalDataId = specificPhoto != null ? ChatId.toSupergroupId(specificPhoto.chatId) : 0;
      } else {
        this.additionalDataId = 0;
      }

      if (!isDetached) {
        subscribeToUpdates();
      }

      requestResources(false);
      return true;
    } else if (dataType != DataType.NONE && this.options != options) {
      this.options = options;
      requestResources(false);
      return true;
    }
    return false;
  }

  @Override
  public void onChatPhotoChanged (long chatId, @Nullable TdApi.ChatPhotoInfo photo) {
    updateResources(() -> isDisplayingChat(chatId));
  }

  @Override
  public void onUserUpdated (TdApi.User user) {
    updateResources(() -> isDisplayingUser(user.id) || /*isPremium might have changed*/ isDisplayingUserChat(user.id));
  }

  @Override
  public void onUserFullUpdated (long userId, TdApi.UserFullInfo userFull) {
    updateResources(() -> isDisplayingUser(userId) || /*userFull.photo might have changed*/ isDisplayingUserChat(userId));
  }

  @Override
  public void onUserStatusChanged (long userId, TdApi.UserStatus status, boolean uiOnly) {
    if (!uiOnly) {
      runOnUiThread(() -> isDisplayingUser(userId) || isDisplayingUserChat(userId), () ->
        updateOnlineState(true)
      );
    }
  }

  @Override
  public void onSupergroupUpdated (TdApi.Supergroup supergroup) {
    updateResources(() -> isDisplayingSupergroupChat(supergroup.id) || (this.dataType == DataType.SPECIFIC_PHOTO && this.additionalDataId == supergroup.id));
  }

  @Override
  public void onSupergroupFullUpdated (long supergroupId, TdApi.SupergroupFullInfo newSupergroupFull) {
    updateResources(() -> isDisplayingSupergroupChat(supergroupId));
  }

  @Override
  public void onBasicGroupFullUpdated (long basicGroupId, TdApi.BasicGroupFullInfo basicGroupFull) {
    updateResources(() -> isDisplayingBasicGroupChat(basicGroupId));
  }

  @AnyThread
  private void runOnUiThread (FutureBool condition, Runnable act) {
    UI.post(() -> {
      if (condition.get()) {
        act.run();
      }
    });
  }

  @AnyThread
  private void updateResources (FutureBool act) {
    runOnUiThread(act, () ->
      requestResources(true)
    );
  }

  private void setIsForum (boolean isForum, boolean animated) {
    this.isForum.setValue(isForum, animated);
  }

  private void updateForumState (boolean isUpdate) {
    switch (dataType) {
      case DataType.NONE: {
        setIsForum(false, isUpdate);
        break;
      }
      case DataType.SPECIFIC_PHOTO: {
        setIsForum(BitwiseUtils.getFlag(options, Options.FORCE_FORUM) || (specificPhoto != null && tdlib.chatForum(specificPhoto.chatId)), isUpdate);
        break;
      }
      case DataType.SPECIFIC_FILE:
      case DataType.PLACEHOLDER:
      case DataType.USER: {
        setIsForum(BitwiseUtils.getFlag(options, Options.FORCE_FORUM), isUpdate);
        break;
      }
      case DataType.CHAT: {
        setIsForum(BitwiseUtils.getFlag(options, Options.FORCE_FORUM) || tdlib.chatForum(dataId), isUpdate);
        break;
      }
    }
  }

  private void setIsOnline (boolean isOnline, boolean animated) {
    if (animated && isFullScreen.getFloatValue() == 1f) {
      animated = false;
    }
    this.isOnline.setValue(isOnline, animated);
  }

  private void updateOnlineState (boolean isUpdate) {
    boolean allowOnline = BitwiseUtils.getFlag(options, Options.SHOW_ONLINE);
    switch (dataType) {
      case DataType.NONE:
      case DataType.SPECIFIC_FILE:
      case DataType.PLACEHOLDER: {
        setIsOnline(false, isUpdate);
        break;
      }
      case DataType.SPECIFIC_PHOTO: {
        setIsOnline(allowOnline && specificPhoto != null && tdlib.chatOnline(specificPhoto.chatId), isUpdate);
        break;
      }
      case DataType.USER: {
        setIsOnline(allowOnline && tdlib.cache().isOnline(dataId), isUpdate);
        break;
      }
      case DataType.CHAT: {
        setIsOnline(allowOnline && tdlib.chatOnline(dataId), isUpdate);
        break;
      }
    }
  }

  @UiThread
  private void requestResources (boolean isUpdate) {
    updateForumState(isUpdate);
    updateOnlineState(isUpdate);
    if (isUpdate && BitwiseUtils.getFlag(options, Options.NO_UPDATES)) {
      return;
    }
    switch (dataType) {
      case DataType.NONE: {
        requestEmpty();
        break;
      }
      case DataType.SPECIFIC_PHOTO: {
        if (specificPhoto != null) {
          requestPhoto(specificPhoto.chatPhoto, BitwiseUtils.getFlag(options, Options.FORCE_ANIMATION) || tdlib.needAvatarPreviewAnimation(specificPhoto.chatId), options);
        } else {
          requestEmpty();
        }
        break;
      }
      case DataType.SPECIFIC_FILE: {
        if (specificFile != null) {
          requestPhoto(specificFile, options);
        } else {
          requestEmpty();
        }
        break;
      }
      case DataType.PLACEHOLDER: {
        requestPlaceholder(specificPlaceholder, options);
        break;
      }
      case DataType.USER: {
        TdApi.User user = tdlib.cache().user(dataId);
        TdApi.ProfilePhoto profilePhoto = user != null ? user.profilePhoto : null;
        if (profilePhoto == null) {
          AvatarPlaceholder.Metadata metadata = tdlib.cache().userPlaceholderMetadata(dataId, user, false);
          requestPlaceholder(metadata, options);
        } else {
          boolean allowAnimation = BitwiseUtils.getFlag(options, Options.FORCE_ANIMATION) || tdlib.needUserAvatarPreviewAnimation(dataId);
          TdApi.UserFullInfo userFullInfo = profilePhoto.hasAnimation && allowAnimation ? tdlib.cache().userFull(dataId) : null;
          TdApi.ChatPhoto photoFull = userFullInfo != null ? userFullInfo.photo : null;
          if (photoFull != null && photoFull.id != profilePhoto.id) {
            // Information in UserFullInfo is most likely outdated.
            // Not displaying it until we receive it from TDLib.
            photoFull = null;
          }
          requestPhoto(profilePhoto, photoFull, allowAnimation, options);
        }
        break;
      }
      case DataType.CHAT: {
        TdApi.Chat chat = tdlib.chat(dataId);
        setIsForum(tdlib.chatForum(dataId), isUpdate);
        boolean allowAnimation = BitwiseUtils.getFlag(options, Options.FORCE_ANIMATION) || tdlib.needAvatarPreviewAnimation(dataId);
        TdApi.ChatPhotoInfo chatPhotoInfo = chat != null && !tdlib.isSelfChat(dataId) ? chat.photo : null;
        if (chatPhotoInfo == null) {
          AvatarPlaceholder.Metadata metadata = tdlib.chatPlaceholderMetadata(dataId, chat, true);
          requestPlaceholder(metadata, options);
        } else {
          TdApi.ChatPhoto photoFull;
          if (chatPhotoInfo.hasAnimation && allowAnimation) {
            switch (ChatId.getType(dataId)) {
              case TdApi.ChatTypePrivate.CONSTRUCTOR:
              case TdApi.ChatTypeSecret.CONSTRUCTOR: {
                TdApi.UserFullInfo fullInfo = tdlib.cache().userFull(additionalDataId);
                photoFull = fullInfo != null ? fullInfo.photo : null;
                break;
              }
              case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
                TdApi.SupergroupFullInfo fullInfo = tdlib.cache().supergroupFull(additionalDataId);
                photoFull = fullInfo != null ? fullInfo.photo : null;
                break;
              }
              case TdApi.ChatTypeBasicGroup.CONSTRUCTOR: {
                TdApi.BasicGroupFullInfo fullInfo = tdlib.cache().basicGroupFull(additionalDataId);
                photoFull = fullInfo != null ? fullInfo.photo : null;
                break;
              }
              default:
                throw new UnsupportedOperationException(chat.type.toString());
            }
          } else {
            photoFull = null;
          }
          requestPhoto(chatPhotoInfo, photoFull, allowAnimation, options);
        }
        break;
      }
    }
  }

  // Implementation

  private void updateState (AvatarPlaceholder.Metadata placeholder, TdApi.ProfilePhoto profilePhoto, TdApi.ChatPhotoInfo chatPhotoInfo, TdApi.ChatPhoto chatPhoto, @Options int options) {
    this.requestedPlaceholder = placeholder;
    this.requestedProfilePhoto = profilePhoto;
    this.requestedChatPhotoInfo = chatPhotoInfo;
    this.requestedChatPhoto = chatPhoto;
  }

  @Nullable
  public AvatarPlaceholder.Metadata getRequestedPlaceholder () {
    return requestedPlaceholder;
  }

  @Nullable
  public TdApi.ProfilePhoto getRequestedProfilePhoto () {
    return requestedProfilePhoto;
  }

  @Nullable
  public TdApi.ChatPhotoInfo getRequestedChatPhotoInfo () {
    return requestedChatPhotoInfo;
  }

  @Nullable
  public TdApi.ChatPhoto getRequestedChatPhoto () {
    return requestedChatPhoto;
  }

  public long getRequestedChatId () {
    return dataType == DataType.CHAT ? dataId : 0;
  }

  public long getRequestedUserId () {
    return dataType == DataType.USER ? dataId : 0;
  }

  private void requestEmpty () {
    // Just clear everything, display nothing
    enabledReceivers = 0;
    complexReceiver.clear();
    updateState(null, null, null, null, Options.NONE);
  }

  private void requestPlaceholder (@NonNull AvatarPlaceholder.Metadata placeholder, int options) {
    // No remote resource, only generated placeholder
    enabledReceivers = 0;
    complexReceiver.clear();
    updateState(placeholder, null, null, null, options);
  }

  private void requestPhoto (@NonNull TdApi.ProfilePhoto profilePhoto, @Nullable TdApi.ChatPhoto photoFull, boolean allowAnimation, @Options int options) {
    updateState(null, profilePhoto, null, null, options);
    // full: profilePhoto.minithumbnail, profilePhoto.small, profilePhoto.big, photoFull?.animation
    // preview: profilePhoto.minithumbnail, profilePhoto.small, photoFull?.smallAnimation ?: photoFull.animation
    loadMinithumbnail(profilePhoto.minithumbnail);
    loadPreviewPhoto(profilePhoto.small);
    loadFullPhoto(BitwiseUtils.getFlag(options, Options.FULL_SIZE) ? profilePhoto.big : null);
    loadAnimation(photoFull, allowAnimation, options);
  }

  private void requestPhoto (@NonNull TdApi.ChatPhotoInfo chatPhotoInfo, @Nullable TdApi.ChatPhoto photoFull, boolean allowAnimation, @Options int options) {
    updateState(null, null, chatPhotoInfo, null, options);
    // full:    chatPhotoInfo.minithumbnail, chatPhotoInfo.small, chatPhotoInfo.big, photoFull?.animation
    // preview: chatPhotoInfo.minithumbnail, chatPhotoInfo.small, photoFull?.smallAnimation ?: photoFull?.animation
    loadMinithumbnail(chatPhotoInfo.minithumbnail);
    loadPreviewPhoto(chatPhotoInfo.small);
    loadFullPhoto(BitwiseUtils.getFlag(options, Options.FULL_SIZE) ? chatPhotoInfo.big : null);
    loadAnimation(photoFull, allowAnimation, options);
  }

  private void requestPhoto (ImageFile specificFile, @Options int options) {
    updateState(null, null, null, null, options);
    loadPreviewPhoto(specificFile);
  }

  private void requestPhoto (@NonNull TdApi.ChatPhoto chatPhoto, boolean allowAnimation, @Options int options) {
    updateState(null, null, new TdApi.ChatPhotoInfo(
      chatPhoto.sizes.length > 0 ? Td.findSmallest(chatPhoto.sizes).photo : null,
      chatPhoto.sizes.length > 0 ? Td.findBiggest(chatPhoto.sizes).photo : null,
      chatPhoto.minithumbnail,
      chatPhoto.animation != null || chatPhoto.smallAnimation != null,
      false
    ), chatPhoto, options);
    // full: chatPhoto.minithumbnail, Td.findSmallest(chatPhoto.sizes), Td.findBiggest(chatPhoto.sizes), allowAnimation ? chatPhoto.animation : null
    // preview: chatPhoto.minithumbnail, Td.findSmallest(chatPhoto.sizes), allowAnimation ? chatPhoto?.smallAnimation ?: chatPhoto?.animation : null
    loadMinithumbnail(chatPhoto.minithumbnail);
    TdApi.PhotoSize smallestSize = Td.findSmallest(chatPhoto.sizes);
    loadPreviewPhoto(smallestSize != null ? smallestSize.photo : null);
    TdApi.PhotoSize biggestSize = BitwiseUtils.getFlag(options, Options.FULL_SIZE) && chatPhoto.sizes.length > 1 ? Td.findBiggest(chatPhoto.sizes) : null;
    loadFullPhoto(BitwiseUtils.getFlag(options, Options.FULL_SIZE) && biggestSize != null ? biggestSize.photo : null);
    loadAnimation(chatPhoto, allowAnimation, options);
  }

  private void loadAnimation (@Nullable TdApi.ChatPhoto photo, boolean allowAnimation, @Options int options) {
    if (photo != null && allowAnimation) {
      boolean fullSize = BitwiseUtils.getFlag(options, Options.FULL_SIZE);
      TdApi.AnimatedChatPhoto smallAnimation = photo.smallAnimation == null ? photo.animation : photo.smallAnimation;
      TdApi.AnimatedChatPhoto fullAnimation = photo.smallAnimation != null ? photo.animation : null;
      loadPreviewAnimation(!fullSize || fullAnimation == null ? smallAnimation : null);
      loadFullAnimation(fullSize ? fullAnimation : null);
    } else {
      loadPreviewAnimation(null);
      loadFullAnimation(null);
    }
  }

  // Low-level requests

  private int enabledReceivers;
  private AvatarPlaceholder.Metadata requestedPlaceholder;
  private TdApi.ProfilePhoto requestedProfilePhoto;
  private TdApi.ChatPhotoInfo requestedChatPhotoInfo;
  private TdApi.ChatPhoto requestedChatPhoto;
  private @Options int requestedOptions;

  private GifFile applyScale (GifFile file) {
    if (file != null) {
      switch (this.scaleMode) {
        case ScaleMode.NONE:
          break;
        case ScaleMode.FIT_CENTER:
          file.setScaleType(GifFile.FIT_CENTER);
          break;
        case ScaleMode.CENTER_CROP:
          file.setScaleType(GifFile.CENTER_CROP);
          break;
      }
    }
    return file;
  }

  private ImageFile applyScale (ImageFile file) {
    if (file != null) {
      switch (this.scaleMode) {
        case ScaleMode.NONE:
          break;
        case ScaleMode.FIT_CENTER:
          file.setScaleType(ImageFile.FIT_CENTER);
          break;
        case ScaleMode.CENTER_CROP:
          file.setScaleType(ImageFile.CENTER_CROP);
          break;
      }
    }
    return file;
  }

  private void loadMinithumbnail (@Nullable TdApi.Minithumbnail minithumbnail) {
    ImageFile file = minithumbnail != null ? new ImageFileLocal(minithumbnail) : null;
    minithumbnailReceiver().requestFile(applyScale(file));
    enabledReceivers = BitwiseUtils.setFlag(enabledReceivers, ReceiverType.MINITHUMBNAIL, file != null);
  }

  private void loadPreviewPhoto (TdApi.File photoFile) {
    ImageFile file;
    if (photoFile != null) {
      file = new ImageFile(tdlib, photoFile);
      file.setSize(ChatView.getDefaultAvatarCacheSize());
    } else {
      file = null;
    }
    loadPreviewPhoto(applyScale(file));
  }

  private void loadPreviewPhoto (ImageFile imageFile) {
    previewPhotoReceiver().requestFile(applyScale(imageFile));
    enabledReceivers = BitwiseUtils.setFlag(enabledReceivers, ReceiverType.PREVIEW_PHOTO, imageFile != null);
  }

  private void loadFullPhoto (TdApi.File photoFile) {
    ImageFile file;
    if (photoFile != null) {
      file = new ImageFile(tdlib, photoFile);
      // TODO setSize?
    } else {
      file = null;
    }
    fullPhotoReceiver().requestFile(applyScale(file));
    enabledReceivers = BitwiseUtils.setFlag(enabledReceivers, ReceiverType.FULL_PHOTO, file != null);
  }

  private void loadPreviewAnimation (TdApi.AnimatedChatPhoto animatedChatPhoto) {
    GifFile file;
    if (animatedChatPhoto != null) {
      file = new GifFile(tdlib, animatedChatPhoto);
      // TODO setRequestedSize?
    } else {
      file = null;
    }
    previewAnimationReceiver().requestFile(applyScale(file));
    enabledReceivers = BitwiseUtils.setFlag(enabledReceivers, ReceiverType.PREVIEW_ANIMATION, file != null);
  }

  private void loadFullAnimation (TdApi.AnimatedChatPhoto animatedChatPhoto) {
    GifFile file;
    if (animatedChatPhoto != null) {
      file = new GifFile(tdlib, animatedChatPhoto);
      // TODO setRequestedSize?
    } else {
      file = null;
    }
    fullAnimationReceiver().requestFile(applyScale(file));
    enabledReceivers = BitwiseUtils.setFlag(enabledReceivers, ReceiverType.FULL_ANIMATION, file != null);
  }

  // Internal receivers

  private static final int RECEIVER_TYPE_COUNT = 5;
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(value = {
    ReceiverType.MINITHUMBNAIL,
    ReceiverType.PREVIEW_PHOTO,
    ReceiverType.FULL_PHOTO,
    ReceiverType.PREVIEW_ANIMATION,
    ReceiverType.FULL_ANIMATION
  })
  private @interface ReceiverType {
    int
      MINITHUMBNAIL = 1,
      PREVIEW_PHOTO = 1 << 1,
      FULL_PHOTO = 1 << 2,
      PREVIEW_ANIMATION = 1 << 3,
      FULL_ANIMATION = 1 << 4;
  }

  private Receiver primaryReceiver () {
    return minithumbnailReceiver();
  }

  private Receiver findReceiver (@ReceiverType int receiverType) {
    switch (receiverType) {
      case ReceiverType.PREVIEW_ANIMATION:
      case ReceiverType.FULL_ANIMATION:
        return complexReceiver.getGifReceiver(receiverType);
      case ReceiverType.FULL_PHOTO:
      case ReceiverType.MINITHUMBNAIL:
      case ReceiverType.PREVIEW_PHOTO:
        return complexReceiver.getImageReceiver(receiverType);
    }
    throw new IllegalArgumentException(Integer.toString(receiverType));
  }

  private ImageReceiver minithumbnailReceiver () {
    return complexReceiver.getImageReceiver(ReceiverType.MINITHUMBNAIL);
  }

  private ImageReceiver previewPhotoReceiver () {
    return complexReceiver.getImageReceiver(ReceiverType.PREVIEW_PHOTO);
  }

  private ImageReceiver fullPhotoReceiver () {
    return complexReceiver.getImageReceiver(ReceiverType.FULL_PHOTO);
  }

  private GifReceiver previewAnimationReceiver () {
    return complexReceiver.getGifReceiver(ReceiverType.PREVIEW_ANIMATION);
  }

  private GifReceiver fullAnimationReceiver () {
    return complexReceiver.getGifReceiver(ReceiverType.FULL_ANIMATION);
  }

  // Receiver implementation

  @Override
  public View getTargetView () {
    return primaryReceiver().getTargetView();
  }

  @Override
  public int getTargetWidth () {
    return primaryReceiver().getTargetWidth();
  }

  @Override
  public int getTargetHeight () {
    return primaryReceiver().getTargetHeight();
  }

  @Override
  public void setUpdateListener (ReceiverUpdateListener listener) {
    if (listener != null) {
      complexReceiver.setUpdateListener(
        (receiver, key) ->
          listener.onRequestInvalidate(receiver)
      );
    } else {
      complexReceiver.setUpdateListener(null);
    }
  }

  @Override
  public int getLeft () {
    return primaryReceiver().getLeft();
  }

  @Override
  public int getTop () {
    return primaryReceiver().getTop();
  }

  @Override
  public int getRight () {
    return primaryReceiver().getRight();
  }

  @Override
  public int getBottom () {
    return primaryReceiver().getBottom();
  }

  @Override
  public void setRadius (float radius) {
    // setRadius is intentionally disabled for AvatarReceiver, as it manages corner radius by itself
    throw new UnsupportedOperationException();
  }

  @Override
  public void setTag (Object tag) {
    primaryReceiver().setTag(tag);
  }

  @Override
  public Object getTag () {
    return primaryReceiver().getTag();
  }

  @Override
  public void attach () {
    if (isDetached) {
      this.isDetached = false;
      requestResources(false);
      complexReceiver.attach();
      subscribeToUpdates();
    }
  }

  @Override
  public void detach () {
    if (!isDetached) {
      this.isDetached = true;
      complexReceiver.detach();
      unsubscribeFromUpdates();
    }
  }

  @Override
  public void clear () {
    destroy();
  }

  @Override
  public void destroy () {
    requestData(null, DataType.NONE, 0, null, null, null, Options.NONE);
  }

  @Override
  public boolean isEmpty () {
    return dataType == DataType.NONE;
  }

  @Override
  public boolean needPlaceholder () {
    if (enabledReceivers != 0) {
      for (int i = 0; i < RECEIVER_TYPE_COUNT; i++) {
        @ReceiverType int receiverType = 1 << i;
        if (BitwiseUtils.getFlag(enabledReceivers, receiverType) && !findReceiver(receiverType).needPlaceholder()) {
          return false;
        }
      }
    }
    return requestedPlaceholder == null;
  }

  @Override
  public void setAlpha (float alpha) {
    for (int i = 0; i < RECEIVER_TYPE_COUNT; i++) {
      @ReceiverType int receiverType = 1 << i;
      findReceiver(receiverType).setAlpha(alpha);
    }
  }

  @Override
  public float getAlpha () {
    return primaryReceiver().getAlpha();
  }

  @Override
  public void setAnimationDisabled (boolean disabled) {
    complexReceiver.setAnimationDisabled(disabled);
  }

  @Override
  public boolean setBounds (int left, int top, int right, int bottom) {
    boolean changed = false;
    for (int i = 0; i < RECEIVER_TYPE_COUNT; i++) {
      @ReceiverType int receiverType = 1 << i;
      if (findReceiver(receiverType).setBounds(left, top, right, bottom)) {
        changed = true;
      }
    }
    return changed;
  }

  @Override
  public void forceBoundsLayout () {
    for (int i = 0; i < RECEIVER_TYPE_COUNT; i++) {
      @ReceiverType int receiverType = 1 << i;
      findReceiver(receiverType).forceBoundsLayout();
    }
  }

  @Override
  public boolean isInsideContent (float x, float y, int emptyWidth, int emptyHeight) {
    return primaryReceiver().isInsideContent(x, y, emptyWidth, emptyHeight);
  }

  @Override
  public void invalidate () {
    primaryReceiver().invalidate();
  }

  @Override
  public float getPaintAlpha () {
    return primaryReceiver().getPaintAlpha();
  }

  @Override
  public void setPaintAlpha (float alpha) {
    for (int i = 0; i < RECEIVER_TYPE_COUNT; i++) {
      @ReceiverType int receiverType = 1 << i;
      findReceiver(receiverType).setPaintAlpha(alpha);
    }
  }

  @Override
  public void restorePaintAlpha () {
    for (int i = 0; i < RECEIVER_TYPE_COUNT; i++) {
      @ReceiverType int receiverType = 1 << i;
      findReceiver(receiverType).restorePaintAlpha();
    }
  }

  @Override
  public void drawPlaceholder (Canvas c) {
    drawPlaceholderRounded(c, getDisplayRadius());
  }

  public final float getDisplayRadius () {
    float fullScreen = this.isFullScreen.getFloatValue();
    if (fullScreen != 1f) {
      float maxRadius = Math.min(getWidth(), getHeight()) / 2f;
      float defaultAvatarRadius = defaultAvatarRadiusPropertyId != 0 ? Theme.getProperty(defaultAvatarRadiusPropertyId) : -1.0f;
      if (defaultAvatarRadius == -1.0f) {
        defaultAvatarRadius = Theme.getProperty(ThemeProperty.AVATAR_RADIUS);
      }
      float forumAvatarRadius = forumAvatarRadiusPropertyId != 0 ? Theme.getProperty(forumAvatarRadiusPropertyId) : -1.0f;
      if (forumAvatarRadius == -1.0f) {
        forumAvatarRadius = Theme.getProperty(ThemeProperty.AVATAR_RADIUS_FORUM);
      }
      float radiusFactor = MathUtils.clamp(
        MathUtils.fromTo(
          defaultAvatarRadius,
          forumAvatarRadius,
          isForum.getFloatValue()
        )
      );
      return maxRadius * radiusFactor * (1f - fullScreen);
    }
    return 0f;
  }

  @Override
  public void draw (Canvas c) {
    float displayRadius = getDisplayRadius();
    float alpha = primaryReceiver().getPaintAlpha();
    if (enabledReceivers != 0) {
      int startReceiverTypeIndex = 0;
      for (int i = RECEIVER_TYPE_COUNT - 1; i >= 0; i--) {
        //noinspection WrongConstant
        @ReceiverType int receiverType = 1 << i;

        Receiver receiver = BitwiseUtils.getFlag(enabledReceivers, receiverType) ? findReceiver(receiverType) : null;
        if (receiver != null && !receiver.needPlaceholder() && !(displayFullSizeOnlyInFullScreen && (receiverType == ReceiverType.FULL_ANIMATION || receiverType == ReceiverType.FULL_PHOTO) && isFullScreen.getFloatValue() != 1f)) {
          startReceiverTypeIndex = i;
          break;
        }
      }
      for (int i = startReceiverTypeIndex; i < RECEIVER_TYPE_COUNT; i++) {
        //noinspection WrongConstant
        @ReceiverType int receiverType = 1 << i;
        Receiver receiver = BitwiseUtils.getFlag(enabledReceivers, receiverType) ? findReceiver(receiverType) : null;
        if (receiver != null) {
          receiver.setRadius(displayRadius);
          receiver.setBounds(getLeft(), getTop(), getRight(), getBottom());
          boolean applyAlpha = displayFullSizeOnlyInFullScreen && (receiverType == ReceiverType.FULL_ANIMATION || receiverType == ReceiverType.FULL_PHOTO) && isFullScreen.getFloatValue() != 1f;
          if (applyAlpha) {
            receiver.setPaintAlpha(isFullScreen.getFloatValue());
          }
          receiver.draw(c);
          if (applyAlpha) {
            receiver.restorePaintAlpha();
          }
        }
      }
    } else if (requestedPlaceholder != null) {
      int toColorId = Theme.avatarSmallToBig(requestedPlaceholder.colorId);
      int placeholderColor = toColorId != 0 ? ColorUtils.fromToArgb(
        Theme.getColor(requestedPlaceholder.colorId),
        Theme.getColor(toColorId),
        isFullScreen.getFloatValue()
      ) : Theme.getColor(requestedPlaceholder.colorId);
      drawPlaceholderRounded(c, displayRadius, ColorUtils.alphaColor(alpha, placeholderColor));
      int avatarContentColorId = R.id.theme_color_avatar_content;
      float primaryContentAlpha = requestedPlaceholder.extraDrawableRes != 0 ? 1f - isFullScreen.getFloatValue() : 1f;
      if (primaryContentAlpha > 0f) {
        if (requestedPlaceholder.drawableRes != 0) {
          drawPlaceholderDrawable(c, requestedPlaceholder.drawableRes, avatarContentColorId, alpha * primaryContentAlpha);
        } else {
          drawPlaceholderLetters(c, requestedPlaceholder.letters, alpha * primaryContentAlpha);
        }
      }
      if (primaryContentAlpha < 1f) {
        drawPlaceholderDrawable(c, requestedPlaceholder.extraDrawableRes, avatarContentColorId, alpha * (1f - primaryContentAlpha) * .75f);
      }
    }

    int contentCutOutColor = ColorUtils.alphaColor(alpha,
      Theme.getColor(contentCutOutColorId != 0 ? contentCutOutColorId : R.id.theme_color_filling)
    );
    int onlineColor = ColorUtils.alphaColor(alpha, Theme.getColor(R.id.theme_color_online));
    DrawAlgorithms.drawOnline(c,
      this,
      allowOnline.getFloatValue() * isOnline.getFloatValue(),
      contentCutOutColor,
      onlineColor
    );
  }

  private Text displayingLetters;
  private float displayingLettersTextSize;

  private void drawPlaceholderLetters (Canvas c, String letters, float alpha) {
    if (StringUtils.isEmpty(letters)) {
      return;
    }

    float currentRadiusPx = getWidth() / 2f;

    float textSizeDp = (int) ((primaryPlaceholderRadius != 0 ? primaryPlaceholderRadius : Screen.px(currentRadiusPx)) * .75f);

    if (displayingLetters == null || !displayingLetters.getText().equals(letters) || displayingLettersTextSize != textSizeDp) {
      displayingLetters = new Text.Builder(
        letters, (int) (currentRadiusPx * 3), Paints.robotoStyleProvider(textSizeDp), TextColorSets.Regular.AVATAR_CONTENT)
        .allBold()
        .singleLine()
        .build();
      displayingLettersTextSize = textSizeDp;
    }

    float radiusPx = primaryPlaceholderRadius != 0f ? Screen.dp(primaryPlaceholderRadius) : currentRadiusPx;
    float scale = radiusPx < currentRadiusPx ? radiusPx / (float) currentRadiusPx : 1f;
    scale *= Math.min(1f, (radiusPx * 2f) / (float) (Math.max(displayingLetters.getWidth(), displayingLetters.getHeight())));

    float centerX = centerX();
    float centerY = centerY();

    final boolean needRestore = scale != 1f;
    final int saveCount;
    if (needRestore) {
      saveCount = Views.save(c);
      c.scale(scale, scale, centerX, centerY);
    } else {
      saveCount = -1;
    }
    displayingLetters.draw(c, (int) (centerX - displayingLetters.getWidth() / 2),  (int) (centerY - displayingLetters.getHeight() / 2), null, alpha);
    if (needRestore) {
      Views.restore(c, saveCount);
    }
  }

  private void drawPlaceholderDrawable (Canvas c, int resId, int colorId, float alpha) {
    float currentRadiusPx = getWidth() / 2f;
    float radiusPx = primaryPlaceholderRadius != 0f ? Screen.dp(primaryPlaceholderRadius) : currentRadiusPx;
    View view = getTargetView();
    Drawable drawable = view instanceof DrawableProvider ?
      ((DrawableProvider) view).getSparseDrawable(resId, colorId) :
      Drawables.get(resId);
    float scale = radiusPx < currentRadiusPx ? radiusPx / currentRadiusPx : 1f;
    scale *= Math.min(1f, (radiusPx * 2f) / (float) Math.max(drawable.getMinimumWidth(), drawable.getMinimumHeight()));
    float centerX = centerX();
    float centerY = centerY();
    final boolean needRestore = scale != 1f;
    final int saveCount;
    if (needRestore) {
      saveCount = Views.save(c);
      c.scale(scale, scale, centerX, centerY);
    } else {
      saveCount = -1;
    }
    Drawables.drawCentered(c, drawable, centerX, centerY, PorterDuffPaint.get(colorId, alpha));
    if (needRestore) {
      Views.restore(c, saveCount);
    }
  }
}
