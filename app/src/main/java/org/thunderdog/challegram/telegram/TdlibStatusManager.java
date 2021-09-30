package org.thunderdog.challegram.telegram;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Invalidator;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.util.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.StringUtils;
import me.vkryl.core.reference.ReferenceMap;
import me.vkryl.td.ChatId;
import me.vkryl.td.Td;

/**
 * Date: 05/11/2016
 * Author: default
 */

public class TdlibStatusManager implements CleanupStartupDelegate {
  public static final int CHANGE_FLAG_TEXT = 1; // update text
  public static final int CHANGE_FLAG_ICON = 1 << 1; // update animation on the left
  public static final int CHANGE_FLAG_POSITION = 1 << 2; // update position of main content & activity

  public interface ChatStateListener {
    void onChatActionsChanged (long chatId, long messageThreadId, @NonNull ChatState chatState, int changeFlags);
    default boolean canAnimateAction (long chatId, long messageThreadId, @NonNull ChatState chatState) { return false; }
  }

  public static class UserAction {
    public final long userId;
    public TdApi.ChatAction action;
    public UserAction (long userId, TdApi.ChatAction action) {
      this.userId = userId;
      this.action = action;
    }
  }

  public static class ChatState implements FactorAnimator.Target {
    private final ArrayList<UserAction> actions = new ArrayList<>();
    private final TdlibStatusManager context;
    private final Tdlib tdlib;

    private final long chatId, messageThreadId;
    private final String key;
    private final boolean isUser;

    public ChatState (TdlibStatusManager context, Tdlib tdlib, long chatId, long messageThreadId) {
      this.context = context;
      this.tdlib = tdlib;
      this.chatId = chatId;
      this.messageThreadId = messageThreadId;
      this.key = makeKey(chatId, messageThreadId);
      this.isUser = ChatId.isUserChat(chatId);
    }

    public boolean isEmpty () {
      return actions.isEmpty() && visibilityFactor == 0f;
    }

    private boolean isVisible;
    private BoolAnimator visibleAnimator;

    private static final long ANIMATION_DURATION = 180l;

    private void setVisibilityFactor (float factor) {
      if (this.visibilityFactor != factor) {
        this.visibilityFactor = factor;
        notifyChatActionsChanged(chatId, messageThreadId, this, CHANGE_FLAG_POSITION, context.listeners.iterator(key));
      }
    }

    public boolean needsLooping () {
      return !actions.isEmpty() && visibilityFactor == 1f && effectiveAction != null && DrawAlgorithms.supportsStatus(effectiveAction);
    }

    private void setIsVisible (boolean isVisible, boolean animated) {
      if (this.isVisible != isVisible || !animated) {
        this.isVisible = isVisible;
        if (animated) {
          if (visibleAnimator == null) {
            visibleAnimator = new BoolAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, ANIMATION_DURATION, !isVisible);
          }
          visibleAnimator.setValue(isVisible, true);
        } else {
          if (visibleAnimator != null) {
            visibleAnimator.setValue(isVisible, false);
          }
          setVisibilityFactor(isVisible ? 1f : 0f);
        }
      }
    }

    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      setVisibilityFactor(factor);
    }

    @Override
    public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

    private String effectiveText;
    private float visibilityFactor;
    private TdApi.ChatAction effectiveAction;
    private int effectiveProgress;

    public String text () {
      return effectiveText;
    }
    public float visibility () {
      return visibilityFactor;
    }
    public @Nullable TdApi.ChatAction action () {
      return effectiveAction;
    }
    public int progress () {
      return effectiveProgress;
    }

    private void updateProgress () {
      TdApi.ChatAction effectiveAction = null;
      int effectiveProgress = 0;

      if (isUser) {
        effectiveAction = actions.get(0).action;
        effectiveProgress = TD.getProgress(effectiveAction);
      } else {
        for (TdlibStatusManager.UserAction action : actions) {
          int progress = TD.getProgress(action.action);
          if (progress != -1) {
            effectiveProgress += progress;
          }
          if (effectiveAction == null) {
            effectiveAction = action.action;
          } else if (effectiveAction.getConstructor() != action.action.getConstructor()) {
            effectiveAction = null;
            effectiveProgress = 0;
            break;
          }
        }
      }

      this.effectiveAction = effectiveAction;
      this.effectiveProgress = effectiveProgress;
    }

    private void updateText () {
      String prefix;
      int userCount = 0;
      TdApi.ChatAction effectiveAction = null;
      int effectiveProgress = 0;
      boolean isActionUnknown = false;

      if (isUser) {
        effectiveAction = actions.get(0).action;
        effectiveProgress = TD.getProgress(effectiveAction);
        userCount = 1;
        prefix = null;
      } else {
        StringBuilder b = new StringBuilder();
        if (Fonts.isLtrCharSupported()) {
          b.append(Strings.LTR_CHAR);
        }
        List<String> names = new ArrayList<>();
        int othersCount = 0;
        for (TdlibStatusManager.UserAction action : actions) {
          if (!isActionUnknown) {
            int progress = TD.getProgress(action.action);
            if (progress != -1) {
              effectiveProgress += progress;
            }
            if (effectiveAction == null) {
              effectiveAction = action.action;
            } else if (effectiveAction.getConstructor() != action.action.getConstructor()) {
              effectiveAction = null;
              isActionUnknown = true;
              effectiveProgress = 0;
            }
          }
          if (++userCount <= 2) { // Allow only 2 names
            names.add(tdlib.cache().userName(action.userId));
          } else {
            othersCount++;
          }
        }
        b.append(Lang.pluralPeopleNames(names, othersCount));
        if (Fonts.isScopeEndSupported()) {
          b.append(Strings.SCOPE_END);
        }
        prefix = b.toString();
      }

      this.effectiveAction = effectiveAction;
      this.effectiveProgress = effectiveProgress;

      if (effectiveAction == null) {
        this.effectiveText = prefix + '…';
        return;
      }
      int res;
      switch (effectiveAction.getConstructor()) {
        case TdApi.ChatActionTyping.CONSTRUCTOR:
          res = isUser ? R.string.Typing : userCount == 1 ? R.string.IsTypingGroup : R.string.AreTypingGroup;
          break;
        case TdApi.ChatActionRecordingVideo.CONSTRUCTOR:
          res = isUser ? R.string.RecordingVideo : userCount == 1 ? R.string.IsRecordingVideo : R.string.AreRecordingVideo;
          break;
        case TdApi.ChatActionRecordingVoiceNote.CONSTRUCTOR:
          res = isUser ? R.string.RecordingAudio : userCount == 1 ? R.string.IsRecordingAudio : R.string.AreRecordingAudio;
          break;
        case TdApi.ChatActionChoosingLocation.CONSTRUCTOR:
          res = isUser ? R.string.SendingLocation : userCount == 1 ? R.string.IsSendingLocation : R.string.AreSendingLocation;
          break;
        case TdApi.ChatActionChoosingContact.CONSTRUCTOR:
          res = isUser ? R.string.SendingContact : userCount == 1 ? R.string.IsSendingContact : R.string.AreSendingContact;
          break;
        case TdApi.ChatActionStartPlayingGame.CONSTRUCTOR:
          res = isUser ? R.string.SendingGame : userCount == 1 ? R.string.IsSendingGame : R.string.AreSendingGame;
          break;
        case TdApi.ChatActionChoosingSticker.CONSTRUCTOR:
          res = isUser ? R.string.ChoosingSticker : userCount == 1 ? R.string.IsChoosingSticker : R.string.AreChoosingSticker;
          break;
        case TdApi.ChatActionRecordingVideoNote.CONSTRUCTOR:
          res = isUser ? R.string.RecordingRound : userCount == 1 ? R.string.IsRecordingRound : R.string.AreRecordingRound;
          break;
        case TdApi.ChatActionUploadingVideo.CONSTRUCTOR:
          res = isUser ? R.string.SendingVideo : userCount == 1 ? R.string.IsSendingVideo : R.string.AreSendingVideos;
          break;
        case TdApi.ChatActionUploadingVideoNote.CONSTRUCTOR:
          res = isUser ? R.string.SendingRound : userCount == 1 ? R.string.IsSendingRound : R.string.AreSendingRound;
          break;
        case TdApi.ChatActionUploadingVoiceNote.CONSTRUCTOR:
          res = isUser ? R.string.SendingVoice : userCount == 1 ? R.string.IsSendingVoice : R.string.AreSendingVoice;
          break;
        case TdApi.ChatActionUploadingPhoto.CONSTRUCTOR:
          res = isUser ? R.string.SendingPhoto : userCount == 1 ? R.string.IsSendingPhoto : R.string.AreSendingPhoto;
          break;
        case TdApi.ChatActionUploadingDocument.CONSTRUCTOR:
          res = isUser ? R.string.SendingFile : userCount == 1 ? R.string.IsSendingFile : R.string.AreSendingFile;
          break;
        default:
          throw new IllegalArgumentException(Integer.toString(effectiveAction.getConstructor()));
      }

      this.effectiveText = isUser ? Lang.getString(res) : Lang.getString(res, prefix);
    }

    public void setAll (TdApi.ChatAction action) {
      int size = actions.size();
      int changeFlags = 0;
      for (int i = size - 1; i >= 0; i--) {
        changeFlags |= setActionAt(i, actions.get(i).userId, action);
      }
      applyChanges(changeFlags);
    }

    public void setAction (long userId, TdApi.ChatAction action) {
      int foundIndex = -1;
      int i = 0;
      for (UserAction userAction : actions) {
        if (userAction.userId == userId) {
          if (Td.equalsTo(userAction.action, action)) {
            return;
          }
          foundIndex = i;
          break;
        }
        i++;
      }
      int changeFlags = setActionAt(foundIndex, userId, action);
      applyChanges(changeFlags);
    }

    private int setActionAt (int index, long userId, TdApi.ChatAction action) {
      boolean isCancel = action.getConstructor() == TdApi.ChatActionCancel.CONSTRUCTOR;
      int changeFlags = 0;
      if (isCancel) {
        if (index != -1) {
          actions.remove(index);
          changeFlags |= CHANGE_FLAG_TEXT;
        }
      } else if (index != -1) {
        UserAction userAction = actions.get(index);
        TdApi.ChatAction oldAction = userAction.action;
        userAction.action = action;
        if (oldAction.getConstructor() == action.getConstructor()) {
          changeFlags |= CHANGE_FLAG_ICON;
        } else {
          changeFlags = CHANGE_FLAG_TEXT | CHANGE_FLAG_ICON;
        }
      } else {
        actions.add(0, new UserAction(userId, action));
        changeFlags |= CHANGE_FLAG_TEXT;
      }
      return changeFlags;
    }

    private void applyChanges (int changeFlags) {
      if (changeFlags == 0)
        return;
      boolean isVisible = !actions.isEmpty();
      if (isVisible) {
        if ((changeFlags & CHANGE_FLAG_TEXT) != 0) {
          updateText();
        } else if ((changeFlags & CHANGE_FLAG_ICON) != 0) {
          updateProgress();
        }
      }
      if (this.isVisible != isVisible) {
        setIsVisible(isVisible, context.canAnimate(chatId, messageThreadId, key, this));
      }
      notifyChatActionsChanged(chatId, messageThreadId, this, changeFlags, context.listeners.iterator(key));
    }
  }

  public interface HelperTarget {
    void layoutChatAction ();
    void invalidateTypingPart (boolean onlyIcon);
    boolean canLoop ();
    boolean canAnimate ();
  }

  public static class Helper implements Invalidator.Target, TdlibStatusManager.ChatStateListener {
    private final Tdlib tdlib;
    private final BaseActivity context;
    private final HelperTarget target;
    private final ViewController<?> parent;

    public Helper (@NonNull BaseActivity context, @NonNull Tdlib tdlib, HelperTarget target, @Nullable ViewController<?> parent) {
      if (tdlib == null)
        throw new IllegalArgumentException();
      if (context == null)
        throw new IllegalArgumentException();
      this.tdlib = tdlib;
      this.context = context;
      this.target = target;
      this.parent = parent;
    }

    private @Nullable TdlibStatusManager.ChatState chatState;
    private String chatActionText;
    private int chatActionIconWidth;
    private Text trimmedChatAction;

    private boolean isLooping;

    private void setChatActionText (String text, TdApi.ChatAction action) {
      this.chatActionIconWidth = DrawAlgorithms.getStatusWidth(action);
      if (!StringUtils.equalsOrBothEmpty(chatActionText, text)) {
        this.chatActionText = text;
        if (StringUtils.isEmpty(text)) {
          setDrawingText(null);
        } else {
          target.layoutChatAction();
        }
      }
    }

    private long chatId, messageThreadId;

    public void attachToChat (long chatId, long messageThreadId) {
      if (this.chatId != chatId || this.messageThreadId != messageThreadId) {
        if (this.chatId != 0) {
          tdlib.status().removeListener(this.chatId, this.messageThreadId, this);
        }
        this.chatId = chatId;
        this.messageThreadId = messageThreadId;
        if (chatId != 0) {
          tdlib.status().addListener(chatId, messageThreadId, this);
        }
        setState(tdlib.status().state(chatId, messageThreadId), -1);
      }
    }

    public void detachFromAnyChat () {
      if (this.chatId != 0) {
        tdlib.status().removeListener(this.chatId, this.messageThreadId, this);
        this.chatId = 0;
        this.messageThreadId = 0;
        setState(null, -1);
      }
    }

    public String fullText () {
      return chatActionText;
    }

    public int actionIconWidth () {
      return chatActionIconWidth;
    }

    public void setDrawingText (Text text) {
      this.trimmedChatAction = text;
    }

    public @Nullable TdlibStatusManager.ChatState drawingState () {
      return chatState != null && !chatState.isEmpty() ? chatState : null;
    }

    public @Nullable Text drawingText () {
      return trimmedChatAction;
    }

    public void setState (TdlibStatusManager.ChatState chatState, int changeFlags) {
      boolean changedText = this.chatState == null || (changeFlags & TdlibStatusManager.CHANGE_FLAG_TEXT) != 0;
      this.chatState = chatState;
      if (changedText && chatState != null) {
        setChatActionText(chatState.text(), chatState.action());
      }
      setLooping(chatState != null && chatState.needsLooping() && target.canLoop());
      changeFlags &= ~TdlibStatusManager.CHANGE_FLAG_ICON;
      target.invalidateTypingPart(!changedText && changeFlags == 0);
    }

    private void setLooping (boolean isLooping) {
      if (this.isLooping != isLooping) {
        this.isLooping = isLooping;
        if (isLooping) {
          context.invalidator().addTarget(this);
        } else {
          context.invalidator().removeTarget(this);
        }
      }
    }

    @Override
    public ViewController<?> getTargetParent (Invalidator context) {
      return this.parent;
    }

    @Override
    public long onInvalidateTarget (Invalidator context) {
      target.invalidateTypingPart(true);
      return -1; // TODO calculate exact amount of time till next frame
    }

    @Override
    public void onChatActionsChanged (long chatId, long messageThreadId, @NonNull TdlibStatusManager.ChatState chatState, int changeFlags) {
      setState(chatState, changeFlags);
    }

    @Override
    public boolean canAnimateAction (long chatId, long messageThreadId, @NonNull ChatState chatState) {
      return target.canAnimate();
    }
  }

  private final Tdlib tdlib;
  private final HashMap<String, ChatState> chatStates = new HashMap<>();
  private final ReferenceMap<String, ChatStateListener> listeners = new ReferenceMap<>();

  TdlibStatusManager (Tdlib tdlib) {
    this.tdlib = tdlib;
    tdlib.listeners().addCleanupListener(this);
  }

  @Override
  public void onPerformStartup (boolean isAfterRestart) {

  }

  @Override
  public void onPerformUserCleanup () {
    chatStates.clear();
    listeners.clear();
  }

  @Override
  public void onPerformRestart () {
    tdlib.ui().post(() -> {
      if (!chatStates.isEmpty()) {
        TdApi.ChatAction action = new TdApi.ChatActionCancel();
        for (Map.Entry<String, ChatState> entry : chatStates.entrySet()) {
          entry.getValue().setAll(action);
        }
      }
    });
  }

  // Status

  private void addListener (long chatId, long messageThreadId, ChatStateListener listener) {
    listeners.add(makeKey(chatId, messageThreadId), listener);
  }

  private void removeListener (long chatId, long messageThreadId, ChatStateListener listener) {
    listeners.remove(makeKey(chatId, messageThreadId), listener);
  }

  public @Nullable ChatState state (long chatId, long messageThreadId) {
    return chatStates.get(makeKey(chatId, messageThreadId));
  }

  public boolean hasStatus (long chatId, long messageThreadId) {
    ChatState state = chatStates.get(makeKey(chatId, messageThreadId));
    return state != null && !state.actions.isEmpty();
  }

  private boolean canAnimate (long chatId, long messageThreadId, String key, ChatState state) {
    boolean animated = false;
    Iterator<ChatStateListener> itr = listeners.iterator(key);
    if (itr != null) {
      while (itr.hasNext()) {
        if (itr.next().canAnimateAction(chatId, messageThreadId, state)) {
          animated = true;
        }
      }
    }
    return animated;
  }

  private static String makeKey (long chatId, long messageThreadId) {
    return messageThreadId != 0 ? chatId + "_" + messageThreadId : Long.toString(chatId);
  }

  @UiThread
  void onUpdateChatUserAction (TdApi.UpdateUserChatAction update) {
    if (update.action.getConstructor() == TdApi.ChatActionWatchingAnimations.CONSTRUCTOR) {
      // TODO?
      return;
    }
    String key = makeKey(update.chatId, update.messageThreadId);
    ChatState state = chatStates.get(key);
    if (state == null) {
      if (update.action.getConstructor() == TdApi.ChatActionCancel.CONSTRUCTOR) {
        return;
      }
      state = new ChatState(this, tdlib, update.chatId, update.messageThreadId);
      chatStates.put(key, state);
    }
    state.setAction(update.userId, update.action);
  }

  private static void notifyChatActionsChanged (long chatId, long messageThreadId, ChatState chatState, int changeFlags, @Nullable Iterator<ChatStateListener> list) {
    if (list != null) {
      while (list.hasNext()) {
        list.next().onChatActionsChanged(chatId, messageThreadId, chatState, changeFlags);
      }
    }
  }

  // Utils

  public CharSequence chatStatus (long chatId) {
    if (chatId == 0) {
      return "chat unavailable";
    }
    switch (ChatId.getType(chatId)) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR: {
        return getPrivateChatSubtitle(ChatId.toUserId(chatId));
      }
      case TdApi.ChatTypeSecret.CONSTRUCTOR: {
        TdApi.SecretChat secretChat = tdlib.chatToSecretChat(chatId);
        return secretChat != null ? getPrivateChatSubtitle(secretChat.userId) : "unknown secret chat";
      }
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR: {
        return getBasicGroupChatSubtitle(ChatId.toBasicGroupId(chatId));
      }
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
        return getSupergroupChatSubtitle(ChatId.toSupergroupId(chatId));
      }
    }
    throw new IllegalArgumentException(Long.toString(chatId));
  }

  public CharSequence chatStatusExpanded (TdApi.Chat chat) {
    if (chat == null) {
      return null;
    }
    switch (chat.type.getConstructor()) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR:
      case TdApi.ChatTypeSecret.CONSTRUCTOR:
        return getPrivateChatSubtitle(TD.getUserId(chat), tdlib.chatUser(chat), true, false);
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR:
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR:
        // TODO X members, Y online, Z bots
        break;
    }
    return null;
  }

  public CharSequence chatStatus (TdApi.Chat chat) {
    if (chat == null) {
      return "chat unavailable";
    }
    switch (chat.type.getConstructor()) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR: {
        return getPrivateChatSubtitle(((TdApi.ChatTypePrivate) chat.type).userId);
      }
      case TdApi.ChatTypeSecret.CONSTRUCTOR: {
        return getPrivateChatSubtitle(((TdApi.ChatTypeSecret) chat.type).userId);
      }
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR: {
        return getBasicGroupChatSubtitle(((TdApi.ChatTypeBasicGroup) chat.type).basicGroupId);
      }
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
        return getSupergroupChatSubtitle(((TdApi.ChatTypeSupergroup) chat.type).supergroupId);
      }
    }
    throw new IllegalArgumentException(chat.type.toString());
  }

  public String getPrivateChatSubtitle (long userId) {
    return getPrivateChatSubtitle(userId, tdlib.cache().user(userId), true, true);
  }

  public String getPrivateChatSubtitle (long userId, @Nullable TdApi.User user, boolean allowMyself) {
    return getPrivateChatSubtitle(userId, user, allowMyself, true);
  }

  public boolean isOnline (long userId) {
    if (tdlib.isSelfUserId(userId) || tdlib.isServiceNotificationsChat(ChatId.fromUserId(userId)))
      return false;
    TdApi.User user = tdlib.cache().user(userId);
    return user != null && user.type.getConstructor() == TdApi.UserTypeRegular.CONSTRUCTOR && user.status.getConstructor() == TdApi.UserStatusOnline.CONSTRUCTOR;
  }

  public String getPrivateChatSubtitle (long userId, @Nullable TdApi.User user, boolean allowMyself, boolean allowDuration) {
    if (allowMyself && tdlib.isSelfUserId(userId)) {
      return Lang.lowercase(Lang.getString(R.string.ChatWithYourself));
    }
    final long chatId = ChatId.fromUserId(userId);
    if (tdlib.isServiceNotificationsChat(chatId)) {
      return Lang.getString(R.string.ServiceNotifications);
    }
    if (tdlib.isRepliesChat(chatId)) {
      return Lang.getString(R.string.ReplyNotifications);
    }
    if (user == null) {
      return Lang.getString(R.string.UserUnavailable);
    }
    if (user.isSupport) {
      return Lang.getString(user.status instanceof TdApi.UserStatusOnline ? R.string.SupportOnline : R.string.Support);
    }
    switch (user.type.getConstructor()) {
      case TdApi.UserTypeBot.CONSTRUCTOR: {
        return Lang.getString(R.string.Bot);
      }
      case TdApi.UserTypeDeleted.CONSTRUCTOR: {
        return Lang.getString(R.string.deletedUser);
      }
      case TdApi.UserTypeUnknown.CONSTRUCTOR: {
        return Lang.getString(R.string.unknownUser);
      }
    }
    return Lang.getUserStatus(tdlib, user.status, allowDuration);
  }

  private CharSequence getBasicGroupChatSubtitle (long basicGroupId) {
    TdApi.BasicGroup group = tdlib.cache().basicGroup(basicGroupId);
    if (group == null || !group.isActive) {
      return Lang.getString(R.string.inactiveGroup);
    } else if (TD.isLeft(group.status)) {
      return Lang.getString(R.string.notInChat);
    } else if (TD.isKicked(group.status)) {
      return Lang.getString(R.string.YouWereKicked);
    } else  {
      return Lang.pluralMembers(group.memberCount, tdlib.chatOnlineMemberCount(ChatId.fromBasicGroupId(basicGroupId)), false);
    }
  }

  private CharSequence getSupergroupChatSubtitle (long supergroupId) {
    int memberCount = 0;
    TdApi.SupergroupFullInfo supergroupFull = tdlib.cache().supergroupFull(supergroupId, true);
    if (supergroupFull != null) {
      memberCount = supergroupFull.memberCount;
    }

    TdApi.Supergroup supergroup = tdlib.cache().supergroup(supergroupId);
    if (memberCount == 0) {
      memberCount = supergroup != null ? supergroup.memberCount : 0;
    }

    if (memberCount > 0) {
      return Lang.pluralMembers(memberCount, tdlib.chatOnlineMemberCount(ChatId.fromSupergroupId(supergroupId)), supergroup != null && supergroup.isChannel);
    }
    if (supergroup == null) {
      return "channel unavailable";
    }
    int resource = supergroup.isChannel ? (StringUtils.isEmpty(supergroup.username) ? R.string.ChannelPrivate : R.string.Channel) : (!StringUtils.isEmpty(supergroup.username) ? R.string.PublicGroup : R.string.Group);
    return Lang.lowercase(Lang.getString(resource));
  }
}
