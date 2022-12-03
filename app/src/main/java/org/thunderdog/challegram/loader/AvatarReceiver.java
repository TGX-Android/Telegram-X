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
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.FutureBool;
import me.vkryl.td.ChatId;
import me.vkryl.td.Td;

public class AvatarReceiver implements Receiver, ChatListener, TdlibCache.UserDataChangeListener, TdlibCache.SupergroupDataChangeListener, TdlibCache.BasicGroupDataChangeListener {
  private final ComplexReceiver complexReceiver;
  private final BoolAnimator isFullScreen;
  private final BoolAnimator isForum;
  private boolean displayFullSizeOnlyInFullScreen;
  private boolean isDetached;

  public AvatarReceiver (@Nullable View view) {
    this.complexReceiver = new ComplexReceiver(view);
    this.isFullScreen = new BoolAnimator(0,
      (id, factor, fraction, callee) -> invalidate(),
      AnimatorUtils.DECELERATE_INTERPOLATOR,
      180l
    );
    this.isForum = new BoolAnimator(0,
      (id, factor, fraction, callee) -> invalidate(),
      AnimatorUtils.DECELERATE_INTERPOLATOR,
      180l
    );
  }

  public void setDisplayFullSizeOnlyInFullScreen (boolean displayFullSizeOnlyInFullScreen) {
    if (this.displayFullSizeOnlyInFullScreen != displayFullSizeOnlyInFullScreen) {
      this.displayFullSizeOnlyInFullScreen = displayFullSizeOnlyInFullScreen;
      invalidate();
    }
  }

  public void setFullScreen (boolean isFullScreen, boolean animated) {
    this.isFullScreen.setValue(isFullScreen, animated);
  }

  public void forceFullScreen (boolean isFullScreen, float floatValue) {
    this.isFullScreen.forceValue(isFullScreen, floatValue);
  }

  // Public API

  public boolean requestPlaceholder (Tdlib tdlib, AvatarPlaceholder.Metadata specificPlaceholder, boolean allowAnimation, boolean fullSize) {
    return requestData(tdlib, DataType.PLACEHOLDER, specificPlaceholder != null ? specificPlaceholder.colorId : 0, specificPlaceholder, null, null, allowAnimation, fullSize);
  }

  public boolean isDisplayingPlaceholder (AvatarPlaceholder.Metadata specificPlaceholder) {
    return specificPlaceholder != null &&
      dataType == DataType.PLACEHOLDER &&
      this.specificPlaceholder == specificPlaceholder;
  }

  public boolean requestSpecific (Tdlib tdlib, TdApi.ChatPhoto specificPhoto, boolean allowAnimation, boolean fullSize) {
    return requestData(tdlib, DataType.SPECIFIC_PHOTO, specificPhoto != null ? specificPhoto.id : 0, null, specificPhoto, null, allowAnimation, fullSize);
  }

  public boolean isDisplayingSpecificPhoto (TdApi.ChatPhoto specificPhoto) {
    return specificPhoto != null &&
      dataType == DataType.SPECIFIC_PHOTO &&
      dataId == specificPhoto.id;
  }

  public boolean requestSpecific (Tdlib tdlib, ImageFile specificFile) {
    return requestData(tdlib, DataType.SPECIFIC_FILE, specificFile != null ? specificFile.getId() : 0, null, null, specificFile, false, false);
  }

  public void requestMessageSender (@Nullable Tdlib tdlib, @Nullable TdApi.MessageSender sender, boolean allowAnimation, boolean fullSize) {
    if (sender != null) {
      switch (sender.getConstructor()) {
        case TdApi.MessageSenderUser.CONSTRUCTOR: {
          long userId = ((TdApi.MessageSenderUser) sender).userId;
          requestUser(tdlib, userId, allowAnimation, fullSize);
          break;
        }
        case TdApi.MessageSenderChat.CONSTRUCTOR: {
          long chatId = ((TdApi.MessageSenderChat) sender).chatId;
          if (tdlib != null && tdlib.isSelfChat(chatId)) {
            requestUser(tdlib, tdlib.chatUserId(chatId), allowAnimation, fullSize);
          } else {
            requestChat(tdlib, chatId, allowAnimation, fullSize);
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

  public boolean requestUser (Tdlib tdlib, long userId, boolean allowAnimation, boolean fullSize) {
    return requestData(tdlib, DataType.USER, userId, null, null, null, allowAnimation, fullSize);
  }

  public boolean isDisplayingUser (long userId) {
    return dataType == DataType.USER && dataId == userId;
  }

  public boolean requestChat (Tdlib tdlib, long chatId, boolean allowAnimation, boolean fullSize) {
    return requestData(tdlib, DataType.CHAT, chatId, null, null, null, allowAnimation, fullSize);
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
  private TdApi.ChatPhoto specificPhoto;
  private ImageFile specificFile;
  private boolean allowAnimation, fullSize;

  private void subscribeToUpdates () {
    switch (dataType) {
      case DataType.NONE:
      case DataType.PLACEHOLDER:
      case DataType.SPECIFIC_PHOTO:
      case DataType.SPECIFIC_FILE:
        break;
      case DataType.USER:
        this.tdlib.cache().addUserDataListener(this.dataId, this);
        break;
      case DataType.CHAT: {
        this.tdlib.listeners().subscribeToChatUpdates(this.dataId, this);
        switch (ChatId.getType(this.dataId)) {
          case TdApi.ChatTypePrivate.CONSTRUCTOR:
          case TdApi.ChatTypeSecret.CONSTRUCTOR: {
            if (this.additionalDataId != 0) {
              tdlib.cache().addUserDataListener(this.additionalDataId, this);
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
      case DataType.SPECIFIC_PHOTO:
      case DataType.SPECIFIC_FILE:
        break;
      case DataType.USER: {
        this.tdlib.cache().removeUserDataListener(this.dataId, this);
        break;
      }
      case DataType.CHAT:
        this.tdlib.listeners().unsubscribeFromChatUpdates(this.dataId, this);
        if (this.additionalDataId != 0) {
          switch (ChatId.getType(this.dataId)) {
            case TdApi.ChatTypePrivate.CONSTRUCTOR:
            case TdApi.ChatTypeSecret.CONSTRUCTOR:
              this.tdlib.cache().removeUserDataListener(this.additionalDataId, this);
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
  private boolean requestData (@Nullable Tdlib tdlib, @DataType int dataType, long dataId, @Nullable AvatarPlaceholder.Metadata specificPlaceholder, @Nullable TdApi.ChatPhoto specificPhoto, ImageFile specificFile, boolean allowAnimation, boolean fullSize) {
    if (!UI.inUiThread())
      throw new IllegalStateException();
    if (dataType == DataType.NONE || dataId == 0 || tdlib == null) {
      dataType = DataType.NONE;
      dataId = 0;
      tdlib = null;
      specificPhoto = null;
      specificPlaceholder = null;
      specificFile = null;
      allowAnimation = false;
    }
    if (this.tdlib != tdlib || this.dataType != dataType || this.dataId != dataId) {
      unsubscribeFromUpdates();

      this.dataType = dataType;
      this.dataId = dataId;
      this.tdlib = tdlib;
      this.specificPlaceholder = specificPlaceholder;
      this.specificPhoto = specificPhoto;
      this.specificFile = specificFile;
      this.allowAnimation = allowAnimation;
      this.fullSize = fullSize;
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
      } else {
        this.additionalDataId = 0;
      }

      if (!isDetached) {
        subscribeToUpdates();
      }

      requestResources(false);
      return true;
    } else if (dataType != DataType.NONE && (this.allowAnimation != allowAnimation || this.fullSize != fullSize)) {
      this.allowAnimation = allowAnimation;
      this.fullSize = fullSize;
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
    updateResources(() -> isDisplayingUser(user.id));
  }

  @Override
  public void onUserFullUpdated (long userId, TdApi.UserFullInfo userFull) {
    updateResources(() -> isDisplayingUser(userId) || isDisplayingUserChat(userId));
  }

  @Override
  public void onSupergroupUpdated (TdApi.Supergroup supergroup) {
    updateResources(() -> isDisplayingSupergroupChat(supergroup.id));
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
  private void updateResources (FutureBool act) {
    UI.post(() -> {
      if (act.get()) {
        requestResources(true);
      }
    });
  }

  private void setIsForum (boolean isForum, boolean animated) {
    this.isForum.setValue(isForum, animated);
  }

  @UiThread
  private void requestResources (boolean isUpdate) {
    switch (dataType) {
      case DataType.NONE: {
        setIsForum(false, isUpdate);
        requestEmpty();
        break;
      }
      case DataType.SPECIFIC_PHOTO: {
        setIsForum(false, isUpdate);
        if (specificPhoto != null) {
          requestPhoto(specificPhoto, allowAnimation, fullSize);
        } else {
          requestEmpty();
        }
        break;
      }
      case DataType.SPECIFIC_FILE: {
        setIsForum(false, isUpdate);
        if (specificFile != null) {
          requestPhoto(specificFile);
        } else {
          requestEmpty();
        }
        break;
      }
      case DataType.PLACEHOLDER: {
        setIsForum(false, isUpdate);
        requestPlaceholder(specificPlaceholder);
        break;
      }
      case DataType.USER: {
        setIsForum(false, isUpdate);
        TdApi.User user = tdlib.cache().user(dataId);
        TdApi.ProfilePhoto profilePhoto = user != null ? user.profilePhoto : null;
        if (profilePhoto == null) {
          AvatarPlaceholder.Metadata metadata = tdlib.cache().userPlaceholderMetadata(dataId, user, true);
          requestPlaceholder(metadata);
        } else {
          TdApi.UserFullInfo userFullInfo = profilePhoto.hasAnimation && allowAnimation ? tdlib.cache().userFull(dataId) : null;
          requestPhoto(profilePhoto, userFullInfo != null ? userFullInfo.photo : null, allowAnimation, fullSize);
        }
        break;
      }
      case DataType.CHAT: {
        TdApi.Chat chat = tdlib.chat(dataId);
        TdApi.Supergroup supergroup = tdlib.chatToSupergroup(dataId);
        setIsForum(supergroup != null && supergroup.isForum, isUpdate);
        TdApi.ChatPhotoInfo chatPhotoInfo = chat != null && !tdlib.isSelfChat(dataId) ? chat.photo : null;
        if (chatPhotoInfo == null) {
          AvatarPlaceholder.Metadata metadata = tdlib.chatPlaceholderMetadata(dataId, chat, true);
          requestPlaceholder(metadata);
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
          requestPhoto(chatPhotoInfo, photoFull, allowAnimation, fullSize);
        }
        break;
      }
    }
  }

  // Implementation

  private void updateState (AvatarPlaceholder.Metadata placeholder, TdApi.ProfilePhoto profilePhoto, TdApi.ChatPhotoInfo chatPhotoInfo) {
    this.requestedPlaceholder = placeholder;
    this.requestedProfilePhoto = profilePhoto;
    this.requestedChatPhotoInfo = chatPhotoInfo;
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

  private void requestEmpty () {
    // Just clear everything, display nothing
    enabledReceivers = 0;
    complexReceiver.clear();
    updateState(null, null, null);
  }

  private void requestPlaceholder (@NonNull AvatarPlaceholder.Metadata placeholder) {
    // No remote resource, only generated placeholder
    enabledReceivers = 0;
    complexReceiver.clear();
    updateState(placeholder, null, null);
  }

  private void requestPhoto (@NonNull TdApi.ProfilePhoto profilePhoto, @Nullable TdApi.ChatPhoto photoFull, boolean allowAnimation, boolean fullSize) {
    updateState(null, profilePhoto, null);
    if (fullSize) {
      // profilePhoto.minithumbnail, profilePhoto.small, profilePhoto.big, photoFull?.animation
    } else {
      // profilePhoto.minithumbnail, profilePhoto.small, photoFull?.smallAnimation
    }
    loadMinithumbnail(profilePhoto.minithumbnail);
    loadPreviewPhoto(profilePhoto.small);
    loadFullPhoto(fullSize ? profilePhoto.big : null);
    loadPreviewAnimation(allowAnimation && photoFull != null && !fullSize ? (photoFull.smallAnimation == null ? photoFull.animation : photoFull.smallAnimation) : null);
    loadFullAnimation(allowAnimation && photoFull != null && fullSize ? photoFull.animation : null);
  }

  private void requestPhoto (@NonNull TdApi.ChatPhotoInfo chatPhotoInfo, @Nullable TdApi.ChatPhoto photoFull, boolean allowAnimation, boolean fullSize) {
    updateState(null, null, chatPhotoInfo);
    if (fullSize) {
      // chatPhotoInfo.minithumbnail, chatPhotoInfo.small, chatPhotoInfo.big, photoFull?.animation
    } else {
      // chatPhotoInfo.minithumbnail, chatPhotoInfo.small, photoFull?.smallAnimation
    }
    loadMinithumbnail(chatPhotoInfo.minithumbnail);
    loadPreviewPhoto(chatPhotoInfo.small);
    loadFullPhoto(fullSize ? chatPhotoInfo.big : null);
    loadPreviewAnimation(allowAnimation && photoFull != null && !fullSize ? (photoFull.smallAnimation == null ? photoFull.animation : photoFull.smallAnimation) : null);
    loadFullAnimation(allowAnimation && photoFull != null && fullSize ? photoFull.animation : null);
  }

  private void requestPhoto (ImageFile specificFile) {
    updateState(null, null, null);
    loadPreviewPhoto(specificFile);
  }

  private void requestPhoto (@NonNull TdApi.ChatPhoto chatPhoto, boolean allowAnimation, boolean fullSize) {
    updateState(null, null, new TdApi.ChatPhotoInfo(
      chatPhoto.sizes.length > 0 ? Td.findSmallest(chatPhoto.sizes).photo : null,
      chatPhoto.sizes.length > 0 ? Td.findBiggest(chatPhoto.sizes).photo : null,
      chatPhoto.minithumbnail,
      chatPhoto.animation != null || chatPhoto.smallAnimation != null
    ));
    if (fullSize) {
      // chatPhoto.minithumbnail, Td.findSmallest(chatPhoto.sizes), Td.findBiggest(chatPhoto.sizes), allowAnimation ? chatPhoto.animation : null
    } else {
      // chatPhoto.minithumbnail, Td.findSmallest(chatPhoto.sizes), allowAnimation ? chatPhoto.smallAnimation : null
    }
    loadMinithumbnail(chatPhoto.minithumbnail);
    TdApi.PhotoSize smallestSize = Td.findSmallest(chatPhoto.sizes);
    loadPreviewPhoto(smallestSize != null ? smallestSize.photo : null);
    TdApi.PhotoSize biggestSize = fullSize && chatPhoto.sizes.length > 1 ? Td.findBiggest(chatPhoto.sizes) : null;
    loadFullPhoto(fullSize && biggestSize != null ? biggestSize.photo : null);
    loadPreviewAnimation(allowAnimation && !fullSize ? (chatPhoto.smallAnimation == null ? chatPhoto.animation : chatPhoto.smallAnimation) : null);
    loadFullAnimation(allowAnimation && fullSize ? chatPhoto.animation : null);
  }

  // Low-level requests

  private int enabledReceivers;
  private AvatarPlaceholder.Metadata requestedPlaceholder;
  private TdApi.ProfilePhoto requestedProfilePhoto;
  private TdApi.ChatPhotoInfo requestedChatPhotoInfo;

  private void loadMinithumbnail (@Nullable TdApi.Minithumbnail minithumbnail) {
    ImageFile file = minithumbnail != null ? new ImageFileLocal(minithumbnail) : null;
    minithumbnailReceiver().requestFile(file);
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
    loadPreviewPhoto(file);
  }

  private void loadPreviewPhoto (ImageFile imageFile) {
    previewPhotoReceiver().requestFile(imageFile);
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
    fullPhotoReceiver().requestFile(file);
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
    previewAnimationReceiver().requestFile(file);
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
    fullAnimationReceiver().requestFile(file);
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

  private float primaryPlaceholderRadius;

  public void setPrimaryPlaceholderRadius (float radius) {
    this.primaryPlaceholderRadius = radius;
    if (requestedPlaceholder != null) {
      invalidate();
    }
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
    requestData(null, DataType.NONE, 0, null, null, null, false, false);
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
      float radiusFactor = MathUtils.clamp(
        MathUtils.fromTo(
          Theme.avatarRadiusDefault(),
          Theme.avatarRadiusForum(),
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
        drawPlaceholderDrawable(c, requestedPlaceholder.extraDrawableRes, avatarContentColorId, alpha * (1f - primaryContentAlpha));
      }
    }
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
      Drawables.get(colorId);
    float scale = radiusPx < currentRadiusPx ? radiusPx / (float) currentRadiusPx : 1f;
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
