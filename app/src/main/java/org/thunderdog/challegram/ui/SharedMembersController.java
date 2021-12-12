package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.DoubleTextWrapper;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.v.MediaRecyclerView;
import org.thunderdog.challegram.widget.CheckBox;
import org.thunderdog.challegram.widget.EmptySmartView;
import org.thunderdog.challegram.widget.SmallChatView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.td.ChatId;
import me.vkryl.td.Td;

/**
 * Date: 8/17/17
 * Author: default
 */

public class SharedMembersController extends SharedBaseController<DoubleTextWrapper> implements
  TdlibCache.BasicGroupDataChangeListener,
  TdlibCache.UserDataChangeListener,
  TdlibCache.ChatMemberStatusChangeListener,
  TdlibCache.UserStatusChangeListener {
  public SharedMembersController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  /*@Override
  public int getIcon () {
    return R.drawable.baseline_group_20;
  }*/

  @Override
  public CharSequence getName () {
    if (specificFilter != null) {
      switch (specificFilter.getConstructor()) {
        case TdApi.SupergroupMembersFilterAdministrators.CONSTRUCTOR:
          return Lang.getString(R.string.TabAdmins);
        case TdApi.SupergroupMembersFilterBanned.CONSTRUCTOR:
          return Lang.getString(R.string.TabBlacklist);
        case TdApi.SupergroupMembersFilterRestricted.CONSTRUCTOR:
          return Lang.getString(R.string.TabRestricted);
      }
    }
    return Lang.getString(R.string.TabMembers);
  }

  @Override
  protected CharSequence buildTotalCount (ArrayList<DoubleTextWrapper> data) {
    int res = R.string.xMembers;
    if (specificFilter != null) {
      switch (specificFilter.getConstructor()) {
        case TdApi.SupergroupMembersFilterAdministrators.CONSTRUCTOR:
          res = R.string.xAdmins;
          break;
        case TdApi.SupergroupMembersFilterBanned.CONSTRUCTOR:
          res = R.string.xBanned;
          break;
        case TdApi.SupergroupMembersFilterRestricted.CONSTRUCTOR:
          res = R.string.xUsers;
          break;
      }
    }
    return Lang.pluralBold(res, data.size());
  }

  private TdApi.SupergroupMembersFilter specificFilter;

  public SharedMembersController setSpecificFilter (TdApi.SupergroupMembersFilter filter) {
    this.specificFilter = filter;
    return this;
  }

  public TdApi.SupergroupMembersFilter getSpecificFilter () {
    return specificFilter;
  }

  @Override
  protected boolean canSearch () {
    return true;
  }

  @Override
  protected boolean supportsMessageContent () {
    return false;
  }

  @Override
  protected TdApi.Function buildRequest (long chatId, long messageThreadId, String query, long offset, String secretOffset, int limit) {
    limit = offset == 0 ? 50 : 100;
    long supergroupId = ChatId.toSupergroupId(chatId);
    if (!StringUtils.isEmpty(query)) {
      TdApi.ChatMembersFilter chatMembersFilter = null;
      if (specificFilter != null) {
        switch (specificFilter.getConstructor()) {
          case TdApi.SupergroupMembersFilterRestricted.CONSTRUCTOR:
            chatMembersFilter = new TdApi.ChatMembersFilterRestricted();
            break;
          case TdApi.SupergroupMembersFilterBanned.CONSTRUCTOR:
            chatMembersFilter = new TdApi.ChatMembersFilterBanned();
            break;
          case TdApi.SupergroupMembersFilterBots.CONSTRUCTOR:
            chatMembersFilter = new TdApi.ChatMembersFilterBots();
            break;
          case TdApi.SupergroupMembersFilterAdministrators.CONSTRUCTOR:
            chatMembersFilter = new TdApi.ChatMembersFilterAdministrators();
            break;
        }
      }
      return new TdApi.SearchChatMembers(chatId, query, limit, chatMembersFilter);
    }
    if (supergroupId != 0) {
      return new TdApi.GetSupergroupMembers(supergroupId, specificFilter != null ? specificFilter : new TdApi.SupergroupMembersFilterRecent(), (int) offset, limit);
    }
    long basicGroupId = ChatId.toBasicGroupId(chatId);
    if (basicGroupId != 0) {
      processGroupFull(tdlib.cache().basicGroupFull(basicGroupId));
    }
    return null;
  }

  private long groupId;

  @Override
  protected void onCreateView (Context context, MediaRecyclerView recyclerView, SettingsAdapter adapter) {
    super.onCreateView(context, recyclerView, adapter);
    groupId = ChatId.toBasicGroupId(chatId);
    if (groupId != 0) {
      tdlib.cache().subscribeToGroupUpdates(groupId, this);
    }
    tdlib.cache().addGlobalUsersListener(this);
    tdlib.cache().addGlobalChatMemberStatusListener(this);
  }

  @Override
  public void destroy () {
    super.destroy();
    if (groupId != 0) {
      tdlib.cache().unsubscribeFromGroupUpdates(groupId, this);
    }
    tdlib.cache().removeGlobalUsersListener(this);
    tdlib.cache().removeGlobalChatMemberStatusListener(this);
  }

  @Override
  protected DoubleTextWrapper parseObject (TdApi.Object object) {
    switch (object.getConstructor()) {
      case TdApi.ChatMember.CONSTRUCTOR: {
        if (groupId != 0 && specificFilter != null && !TD.matchesFilter(specificFilter, ((TdApi.ChatMember) object).status))
          return null;
        return DoubleTextWrapper.valueOf(tdlib, (TdApi.ChatMember) object, needFullMemberDescription(), needAdminSign());
      }
      case TdApi.User.CONSTRUCTOR: {
        return new DoubleTextWrapper(tdlib, ((TdApi.User) object).id, true);
      }
    }
    return null;
  }

  private boolean needAdminSign () {
    return specificFilter == null || specificFilter.getConstructor() != TdApi.SupergroupMembersFilterAdministrators.CONSTRUCTOR;
  }

  private boolean needFullMemberDescription () {
    return specificFilter != null && specificFilter.getConstructor() != TdApi.SupergroupMembersFilterRecent.CONSTRUCTOR;
  }

  @Override
  protected int provideViewType () {
    return ListItem.TYPE_CHAT_SMALL;
  }

  @Override
  protected void modifyChatViewIfNeeded (ListItem item, SmallChatView chatView, @Nullable CheckBox checkBox, boolean isUpdate) {
    DoubleTextWrapper user = (DoubleTextWrapper) item.getData();
    if (user == null || user.getMember() == null) {
      return;
    }
    TdApi.ChatMember member = user.getMember();
    boolean isCreator = TD.isCreator(member.status);

    if (specificFilter == null || parent == null) {
      /*if (item.getViewType() == SettingItem.TYPE_CHAT_SMALL) {
        chatView.setEnabled(tdlib.myUserId() != user.getUserId());
      }*/
      return;
    }

    switch (item.getViewType()) {
      case ListItem.TYPE_CHAT_SMALL_SELECTABLE: {
        ((View) chatView.getParent()).setEnabled(!isCreator/* && !parent.group.everyoneIsAdministrator*/);
        if (checkBox != null) {
          checkBox.setDisabled(isCreator);
          // checkBox.setHidden(parent.group.everyoneIsAdministrator, isUpdate);
          checkBox.setChecked(isCreator || member.status.getConstructor() == TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR, isUpdate);
        }
        break;
      }
      case ListItem.TYPE_CHAT_SMALL: {
        // chatView.setEnabled(tdlib.myUserId() != user.getUserId() || !isCreator /*&& (!isCreator || specificFilter.getConstructor() == TdApi.ChannelMembersFilterRecent.CONSTRUCTOR)*/);
        chatView.clearPreviewChat();
        break;
      }
    }
  }

  @Override
  protected boolean needsCustomLongClickListener () {
    return true;
  }

  @Override
  protected boolean onLongClick (View v, ListItem item) {
    final DoubleTextWrapper content = (DoubleTextWrapper) item.getData();
    TdApi.ChatMember member = content.getMember();

    if (parent == null || member == null) {
      return false;
    }

    IntList ids = new IntList(3);
    IntList colors = new IntList(3);
    IntList icons = new IntList(3);
    StringList strings = new StringList(3);

    TdApi.ChatMemberStatus myStatus = groupId != 0 ? parent.group.status : parent.supergroup.status;

    if (TD.isCreator(member.status)) {
      if (TD.isCreator(myStatus)) {
        ids.append(R.id.btn_editRights);
        colors.append(OPTION_COLOR_NORMAL);
        icons.append(R.drawable.baseline_edit_24);
        strings.append(R.string.EditAdminTitle);

        boolean isAnonymous = ((TdApi.ChatMemberStatusCreator) member.status).isAnonymous;
        if (!isChannel() || isAnonymous) {
          ids.append(isAnonymous ? R.id.btn_makePublic : R.id.btn_makePrivate);
          colors.append(OPTION_COLOR_NORMAL);
          icons.append(isAnonymous ? R.drawable.nilsfast_baseline_incognito_off_24 : R.drawable.infanf_baseline_incognito_24);
          strings.append(isAnonymous ? R.string.EditOwnerPublic : R.string.EditOwnerAnonymous);
        }
      }
    } else {
      int promoteMode = TD.canPromoteAdmin(myStatus, member.status);
      if (promoteMode != TD.PROMOTE_MODE_NONE) {
        ids.append(R.id.btn_editRights);
        colors.append(OPTION_COLOR_NORMAL);
        icons.append(R.drawable.baseline_stars_24);
        switch (promoteMode) {
          case TD.PROMOTE_MODE_EDIT:
            strings.append(R.string.EditAdminRights);
            break;
          case TD.PROMOTE_MODE_NEW:
            strings.append(R.string.SetAsAdmin);
            break;
          case TD.PROMOTE_MODE_VIEW:
            strings.append(R.string.ViewAdminRights);
            break;
          default:
            throw new IllegalStateException();
        }
        if (member.status.getConstructor() == TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR && (!isChannel() || ((TdApi.ChatMemberStatusAdministrator) member.status).isAnonymous) && (
          TD.isCreator(myStatus) ||
          (myStatus.getConstructor() == TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR &&
           ((TdApi.ChatMemberStatusAdministrator) myStatus).isAnonymous &&
           promoteMode != TD.PROMOTE_MODE_VIEW
          )
        )) {
          boolean isAnonymous = ((TdApi.ChatMemberStatusAdministrator) member.status).isAnonymous;
          ids.append(isAnonymous ? R.id.btn_makePublic : R.id.btn_makePrivate);
          colors.append(OPTION_COLOR_NORMAL);
          icons.append(isAnonymous ? R.drawable.nilsfast_baseline_incognito_off_24 : R.drawable.infanf_baseline_incognito_24);
          strings.append(isAnonymous ? R.string.EditAdminPublic : R.string.EditAdminAnonymous);
        }
      }

      int restrictMode = TD.canRestrictMember(myStatus, member.status);
      if (restrictMode != TD.RESTRICT_MODE_NONE) {
        if (!isChannel() && !(restrictMode == TD.RESTRICT_MODE_EDIT && member.memberId.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR)) {
          ids.append(R.id.btn_restrictMember);
          colors.append(OPTION_COLOR_NORMAL);
          icons.append(R.drawable.baseline_block_24);
          switch (restrictMode) {
            case TD.RESTRICT_MODE_EDIT:
              strings.append(member.memberId.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR ? tdlib.isChannel(((TdApi.MessageSenderChat) member.memberId).chatId) ? R.string.EditChannelRestrictions : R.string.EditGroupRestrictions : R.string.EditUserRestrictions);
              break;
            case TD.RESTRICT_MODE_NEW:
              strings.append(member.memberId.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR ? tdlib.isChannel(((TdApi.MessageSenderChat) member.memberId).chatId) ? R.string.BanChannel : R.string.BanChat : R.string.RestrictUser);
              break;
            case TD.RESTRICT_MODE_VIEW:
              strings.append(R.string.ViewRestrictions);
              break;
            default:
              throw new IllegalStateException();
          }
        }

        if (restrictMode != TD.RESTRICT_MODE_VIEW) {
          if (TD.isMember(member.status)) {
            ids.append(R.id.btn_blockUser);
            colors.append(OPTION_COLOR_NORMAL);
            icons.append(R.drawable.baseline_remove_circle_24);
            strings.append(isChannel() ? R.string.ChannelRemoveUser : R.string.RemoveFromGroup);
          } else {
            boolean canUnblock = false;
            switch (member.status.getConstructor()) {
              case TdApi.ChatMemberStatusBanned.CONSTRUCTOR:
              case TdApi.ChatMemberStatusRestricted.CONSTRUCTOR: {
                canUnblock = true;
                break;
              }
            }
            if (canUnblock) {
              strings.append(
                member.status.getConstructor() == TdApi.ChatMemberStatusRestricted.CONSTRUCTOR ? R.string.RemoveRestrictions :
                tdlib.cache().senderBot(member.memberId) ? R.string.UnbanMemberBot :
                  member.memberId.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR ? (tdlib.isChannel(Td.getSenderId(member.memberId)) ? R.string.UnbanMemberChannel : R.string.UnbanMemberGroup) :
                  R.string.UnbanMember
              );
              ids.append(R.id.btn_unblockUser);
              colors.append(OPTION_COLOR_NORMAL);
              icons.append(R.drawable.baseline_remove_circle_24);
            }
          }
        }
      }
    }

    if (!isChannel() || TD.isAdmin(member.status)) {
      ids.append(R.id.btn_messageViewList);
      if (tdlib.isSelfUserId(content.getUserId())) {
        strings.append(R.string.ViewMessagesFromYou);
      } else {
        strings.append(Lang.getString(content.getSender().getConstructor() == TdApi.MessageSenderUser.CONSTRUCTOR ? R.string.ViewMessagesFromUser : R.string.ViewMessagesFromChat, tdlib.senderName(content.getSender(), true)));
      }
      icons.append(R.drawable.baseline_person_24);
      colors.append(OPTION_COLOR_NORMAL);
    }

    if (!ids.isEmpty()) {
      String name = tdlib.senderName(content.getSender());
      CharSequence info = TD.getMemberDescription(this, member, false);
      CharSequence date = TD.getMemberJoinDate(member);
      CharSequence result;

      Lang.SpanCreator firstBoldCreator = (target, argStart, argEnd, spanIndex, needFakeBold) -> spanIndex == 0 ? Lang.newBoldSpan(needFakeBold) : null;

      if (date != null && info != null) {
        result = Lang.getString(R.string.format_nameStatusDate, firstBoldCreator, name, info, date);
      } else if (date != null) {
        result = Lang.getString(R.string.format_nameAndDate, firstBoldCreator, name, date);
      } else if (info != null) {
        result = Lang.getString(R.string.format_nameAndStatus, firstBoldCreator, name, info);
      } else {
        result = Lang.boldify(name);
      }
      showOptions(result, ids.get(), strings.get(), colors.get(), icons.get(), (itemView, id) -> {
        switch (id) {
          case R.id.btn_messageViewList:
            HashtagChatController c = new HashtagChatController(context, tdlib);
            c.setArguments(new HashtagChatController.Arguments(null, chatId, null, new TdApi.MessageSenderUser(content.getUserId()), false));
            if (parent != null) {
              parent.navigateTo(c);
            } else {
              getParentOrSelf().navigateTo(c);
            }
            break;
          case R.id.btn_makePrivate:
          case R.id.btn_makePublic: {
            Runnable act = () -> {
              TdApi.ChatMemberStatus newStatus = Td.copyOf(content.getMember().status);

              switch (newStatus.getConstructor()) {
                case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
                  ((TdApi.ChatMemberStatusCreator) newStatus).isAnonymous = id == R.id.btn_makePrivate;
                  break;
                case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
                  ((TdApi.ChatMemberStatusAdministrator) newStatus).isAnonymous = id == R.id.btn_makePrivate;
                  break;
                default:
                  return;
              }

              tdlib.setChatMemberStatus(chatId, content.getSender(), newStatus, content.getMember().status, null);
            };

            if (ChatId.isBasicGroup(chatId)) {
              showConfirm(Lang.getMarkdownString(this, R.string.UpgradeChatPrompt), Lang.getString(R.string.Proceed), act);
            } else {
              act.run();
            }

            break;
          }
          case R.id.btn_editRights:
            editMember(content, false);
            break;
          case R.id.btn_restrictMember:
            editMember(content, true);
            break;
          case R.id.btn_blockUser:
            tdlib.ui().kickMember(getParentOrSelf(), chatId, content.getSender(), content.getMember().status);
            break;
          case R.id.btn_unblockUser:
            tdlib.ui().unblockMember(getParentOrSelf(), chatId, content.getSender(), content.getMember().status);
            break;
        }
        return true;
      });
      return true;
    }

    return false;
  }

  private boolean isChannel () {
    return parent != null && parent.isChannel();
  }

  private void editMember (DoubleTextWrapper content, boolean restrict) {
    if (parent == null) {
      return;
    }

    TdApi.ChatMemberStatus myStatus = parent.supergroup != null ? parent.supergroup.status : parent.group.status;

    TdApi.ChatMember member = content.getMember();
    if (restrict) {
      int mode = TD.canRestrictMember(myStatus, member.status);
      if (mode == TD.RESTRICT_MODE_NEW) {
        member = null;
      }
    } else {
      int mode = TD.canPromoteAdmin(myStatus, member.status);
      if (mode == TD.RESTRICT_MODE_NEW) {
        member = null;
      }
    }

    EditRightsController c = new EditRightsController(context, tdlib);
    c.setArguments(new EditRightsController.Args(chatId, content.getSender(), restrict, myStatus, member));
    parent.navigateTo(c);
  }

  @Override
  public void onClick (View view) {
    ListItem item = (ListItem) view.getTag();
    if (item == null || !(item.getData() instanceof DoubleTextWrapper)) {
      return;
    }

    DoubleTextWrapper content = (DoubleTextWrapper) item.getData();

    if (parent == null) {
      return;
    }

    if (specificFilter != null && specificFilter.getConstructor() != TdApi.SupergroupMembersFilterRecent.CONSTRUCTOR) {
      switch (specificFilter.getConstructor()) {
        case TdApi.SupergroupMembersFilterRestricted.CONSTRUCTOR:
        case TdApi.SupergroupMembersFilterBanned.CONSTRUCTOR: {
          editMember(content, true);
          break;
        }
        case TdApi.SupergroupMembersFilterAdministrators.CONSTRUCTOR: {
          editMember(content, false);
          break;
        }
      }
      return;
    }

    tdlib.ui().openPrivateProfile(this, content.getUserId(), new TdlibUi.UrlOpenParameters().tooltip(context().tooltipManager().builder(view)));
  }

  @Override
  protected int getEmptySmartMode () {
    if (!isSearching() && specificFilter != null) {
      switch (specificFilter.getConstructor()) {
        case TdApi.SupergroupMembersFilterBanned.CONSTRUCTOR:
          return EmptySmartView.MODE_EMPTY_MEMBERS_BANNED;
        case TdApi.SupergroupMembersFilterRestricted.CONSTRUCTOR:
          return EmptySmartView.MODE_EMPTY_MEMBERS_RESTRICTED;
      }
    }
    return EmptySmartView.MODE_EMPTY_MEMBERS;
  }

  @Override
  protected void modifyResultIfNeeded (ArrayList<DoubleTextWrapper> data, boolean preparation) {
    Comparator<DoubleTextWrapper> comparator;
    ArrayList<DoubleTextWrapper> target = isSearching() ? this.searchData : this.data;
    if (preparation) {
      comparator = getUserComparator();
      if (comparator != null)
        Collections.sort(data, comparator);
    } else if (target != null && !target.isEmpty()) {
      comparator = provideItemComparator();
      if (comparator == null)
        return;
      // Removing members that are already present in the list
      final int size = data.size();
      for (int i = size - 1; i >= 0; i--) {
        DoubleTextWrapper wrapper = data.get(i);
        int currentIndex = find(target, wrapper, comparator);
        if (currentIndex >= 0) {
          data.remove(i);
        }
      }
    }
  }

  private int find (List<DoubleTextWrapper> list, DoubleTextWrapper wrapper, Comparator<DoubleTextWrapper> comparator) {
    return comparator == this.comparator ? Collections.binarySearch(list, wrapper, comparator) : list.indexOf(wrapper);
    /*if (comparator == this.comparator) {
      // return ;
    } else {
      list.indexOf()
    }*/
  }

  // Group

  private TdApi.BasicGroupFullInfo groupFull;

  private void processGroupFull (TdApi.BasicGroupFullInfo groupFull) {
    if (groupFull == null) {
      return;
    }

    if (this.groupFull != null && !isSearching()) {
      this.groupFull = groupFull;

      final int size = data.size(); // First, remove users
      for (int i = size - 1; i >= 0; i--) {
        DoubleTextWrapper member = data.get(i);
        if (indexOfMember(groupFull.members, member.getSender()) == -1 || (specificFilter != null && !TD.matchesFilter(specificFilter, member.getMember().status))) {
          removeMember(member.getSender());
        }
      }

      // Then, add users
      for (TdApi.ChatMember chatMember : groupFull.members) {
        if (specificFilter == null || TD.matchesFilter(specificFilter, chatMember.status)) {
          addOrUpdateMember(chatMember);
        }
      }

      return;
    }

    this.groupFull = groupFull;
    processData("", 0, groupFull, 0);
  }

  @Override
  protected boolean needDateSectionSplitting () {
    return false;
  }

  @Override
  protected String getExplainedTitle () {
    if (specificFilter != null) {
      switch (specificFilter.getConstructor()) {
        case TdApi.SupergroupMembersFilterRestricted.CONSTRUCTOR:
          return Lang.getString(R.string.MembersDetailRestricted);
        case TdApi.SupergroupMembersFilterAdministrators.CONSTRUCTOR:
          return Lang.getString(R.string.MembersDetailAdmins);
        case TdApi.SupergroupMembersFilterBanned.CONSTRUCTOR:
          return Lang.getString(isChannel() ? R.string.MembersDetailBannedChannel : R.string.MembersDetailBannedGroup);
      }
    }
    return Lang.getString(R.string.Recent);
  }

  @Override
  protected boolean supportsLoadingMore (boolean isSearch) {
    return !isSearch && ChatId.toSupergroupId(chatId) != 0;
  }

  @Override
  protected long getCurrentOffset (ArrayList<DoubleTextWrapper> data, long emptyValue) {
    return data == null || data.isEmpty() ? emptyValue : data.size();
  }

  @Override
  public void onBasicGroupUpdated (TdApi.BasicGroup basicGroup, boolean migratedToSupergroup) {
    if (migratedToSupergroup) {
      tdlib.ui().post(() -> {
        if (!isDestroyed()) {
          tdlib.cache().unsubscribeFromGroupUpdates(groupId, this);
          groupId = 0;
          setArguments(new Args(ChatId.fromSupergroupId(basicGroup.upgradedToSupergroupId), messageThreadId));
        }
      });
    }
  }

  @Override
  public void onBasicGroupFullUpdated (long basicGroupId, final TdApi.BasicGroupFullInfo basicGroupFull) {
    tdlib.ui().post(() -> {
      if (!isDestroyed() && groupId == basicGroupId) {
        processGroupFull(basicGroupFull);
      }
    });
  }

  @Override
  public void addMessage (TdApi.Message message) {
    if (!ProfileController.filterMediaMessage(message) || message.chatId != chatId || tdlib.isSelfSender(message) || !tdlib.isSupergroup(message.chatId))
      return;
    tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        bringMemberToTop(message.senderId);
      }
    });
  }

  @Override
  public void onUserUpdated (TdApi.User user) {
    // TODO?
  }

  private static int indexOfMember (TdApi.ChatMember[] members, TdApi.MessageSender memberId) {
    if (members == null || members.length == 0) {
      return -1;
    }
    int i = 0;
    for (TdApi.ChatMember member : members) {
      if (Td.equalsTo(member.memberId, memberId)) {
        return i;
      }
      i++;
    }
    return -1;
  }

  private static int indexOfMember (ArrayList<DoubleTextWrapper> array, TdApi.MessageSender memberId) {
    if (array == null || array.isEmpty()) {
      return -1;
    }
    int i = 0;
    for (DoubleTextWrapper wrapper : array) {
      if (Td.equalsTo(wrapper.getSender(), memberId)) {
        return i;
      }
      i++;
    }
    return -1;
  }

  private void updateUserStatus (ArrayList<DoubleTextWrapper> array, long userId, boolean reorder, boolean notify) {
    final int fromIndex = indexOfMember(array, new TdApi.MessageSenderUser(userId));
    if (fromIndex == -1)
      return;

    DoubleTextWrapper item = array.get(fromIndex);
    item.updateSubtitle();
    Comparator<DoubleTextWrapper> comparator = provideItemComparator();
    if (reorder && comparator != null) {
      array.remove(fromIndex);
      int newIndex = find(array, item, comparator);
      if (newIndex >= 0) {
        array.add(fromIndex, item);
        return;
      }
      newIndex = newIndex * -1 - 1;
      array.add(newIndex, item);

      final int headerItemCount = getHeaderItemCount();
      if (notify && newIndex != fromIndex) {
        ListItem contentItem, separatorItem;
        if (fromIndex == 0) {
          contentItem = adapter.getItems().remove(headerItemCount);
          separatorItem = adapter.getItems().remove(headerItemCount);
          adapter.notifyItemRangeRemoved(headerItemCount, 2);
        } else {
          int startIndex = headerItemCount + fromIndex * 2;
          contentItem = adapter.getItems().remove(startIndex);
          separatorItem = adapter.getItems().remove(startIndex - 1);
          adapter.notifyItemRangeRemoved(startIndex - 1, 2);
        }

        if (newIndex == 0) {
          adapter.getItems().add(headerItemCount, separatorItem);
          adapter.getItems().add(headerItemCount, contentItem);
          adapter.notifyItemRangeInserted(headerItemCount, 2);
        } else {
          int startIndex = headerItemCount + newIndex * 2 - 1;
          adapter.getItems().add(startIndex, contentItem);
          adapter.getItems().add(startIndex, separatorItem);
          adapter.notifyItemRangeInserted(startIndex, 2);
        }
      }
    }
  }

  private void removeMember (TdApi.MessageSender memberId) {
    if (isSearching()) {
      removeMemberImpl(searchData, memberId, true);
      removeMemberImpl(data, memberId, false);
    } else {
      removeMemberImpl(data, memberId, true);
    }
  }

  private void updateCounter () {
    adapter.updateValuedSettingByPosition(adapter.getItems().size() - 1);
  }

  private void removeMemberImpl (ArrayList<DoubleTextWrapper> array, TdApi.MessageSender memberId, boolean notify) {
    int i = indexOfMember(array, memberId);
    if (i != -1) {
      array.remove(i);
      if (notify) {
        if (array.isEmpty()) {
          buildCells();
          return;
        }
        final int headerItemCount = getHeaderItemCount();
        if (i == 0) {
          adapter.removeRange(headerItemCount, 2);
        } else {
          int startIndex = headerItemCount + i * 2;
          adapter.removeRange(startIndex - 1, 2);
        }
        updateCounter();
        onItemsHeightProbablyChanged();
      }
    }
  }

  private void addOrUpdateMember (TdApi.ChatMember member) {
    if (isSearching()) {
      addOrUpdateMemberImpl(searchData, member, false, true);
      addOrUpdateMemberImpl(data, member, true, false);
    } else {
      addOrUpdateMemberImpl(data, member, true, true);
    }
  }

  private void addOrUpdateMemberImpl (ArrayList<DoubleTextWrapper> array, TdApi.ChatMember member, boolean addIfNotPresent, boolean notify) {
    if (array == null) {
      return;
    }
    int i = indexOfMember(array, member.memberId);
    if (i != -1) {
      DoubleTextWrapper wrapper = array.get(i);
      wrapper.setMember(member, needFullMemberDescription(), needAdminSign());
      return;
    }
    if (addIfNotPresent) {
      DoubleTextWrapper newItem = parseObject(member);
      if (newItem != null) {
        int newIndex = find(array, newItem, getUserComparator());
        if (newIndex < 0) {
          newIndex = newIndex * -1 - 1;
          array.add(newIndex, newItem);
          if (notify) {
            if (array.size() == 1) {
              buildCells();
              return;
            }
            final int headerItemCount = getHeaderItemCount();
            ListItem separatorItem = new ListItem(ListItem.TYPE_SEPARATOR);
            ListItem contentItem = new ListItem(provideViewType()).setData(newItem);
            if (newIndex == 0) {
              adapter.getItems().add(headerItemCount, separatorItem);
              adapter.getItems().add(headerItemCount, contentItem);
              adapter.notifyItemRangeInserted(headerItemCount, 2);
            } else {
              int startIndex = headerItemCount + newIndex * 2 - 1;
              adapter.getItems().add(startIndex, contentItem);
              adapter.getItems().add(startIndex, separatorItem);
              adapter.notifyItemRangeInserted(startIndex, 2);
            }
            updateCounter();
            onItemsHeightProbablyChanged();
          }
        }
      }
    }
  }

  private void bringMemberToTop (TdApi.MessageSender memberId) {
    if (memberId.getConstructor() != TdApi.MessageSenderUser.CONSTRUCTOR)
      return;
    if (isSearching()) {
      bringMemberToTop(searchData, memberId, false, true);
      bringMemberToTop(data, memberId, true, false);
    } else {
      bringMemberToTop(data, memberId, true, true);
    }
  }

  private void bringMemberToTop (ArrayList<DoubleTextWrapper> array, TdApi.MessageSender memberId, boolean addIfNotPresent, boolean notify) {
    if (array == null)
      return;
    int fromIndex = indexOfMember(array, memberId);
    int newIndex = 0;
    if (!array.isEmpty() && tdlib.isSelfUserId(array.get(0).getUserId())) {
      newIndex++;
    }
    if (fromIndex != -1) {
      if (fromIndex > newIndex) {
        DoubleTextWrapper wrapper = array.remove(fromIndex);
        array.add(newIndex, wrapper);
        if (notify) {
          final int headerItemCount = getHeaderItemCount();
          ListItem contentItem, separatorItem;
          if (fromIndex == 0) {
            contentItem = adapter.getItems().remove(headerItemCount);
            separatorItem = adapter.getItems().remove(headerItemCount);
            adapter.notifyItemRangeRemoved(headerItemCount, 2);
          } else {
            int startIndex = headerItemCount + fromIndex * 2;
            contentItem = adapter.getItems().remove(startIndex);
            separatorItem = adapter.getItems().remove(startIndex - 1);
            adapter.notifyItemRangeRemoved(startIndex - 1, 2);
          }

          if (newIndex == 0) {
            adapter.getItems().add(headerItemCount, separatorItem);
            adapter.getItems().add(headerItemCount, contentItem);
            adapter.notifyItemRangeInserted(headerItemCount, 2);
          } else {
            int startIndex = headerItemCount + newIndex * 2 - 1;
            adapter.getItems().add(startIndex, contentItem);
            adapter.getItems().add(startIndex, separatorItem);
            adapter.notifyItemRangeInserted(startIndex, 2);
          }
        }
      }
    } else if (addIfNotPresent) {
      tdlib.client().send(new TdApi.GetChatMember(chatId, memberId), result -> {
        if (result.getConstructor() == TdApi.ChatMember.CONSTRUCTOR) {
          TdApi.ChatMember member = (TdApi.ChatMember) result;
          if (TD.isMember(member.status)) {
            tdlib.ui().post(() -> {
              if (!isDestroyed()) {
                addOrUpdateMember(member);
              }
            });
          }
        }
      });
    }
  }

  private final Comparator<DoubleTextWrapper> comparator = (o1, o2) -> tdlib.userProviderComparator().compare(o1, o2);

  private Comparator<DoubleTextWrapper> getUserComparator () {
    if (specificFilter != null && groupId == 0) {
      return null;
    } else if (ChatId.isSupergroup(chatId)) {
      return (o1, o2) -> {
        boolean m1 = tdlib.isSelfUserId(o1.getUserId());
        boolean m2 = tdlib.isSelfUserId(o2.getUserId());
        return m1 != m2 ? (m1 ? -1 : 1) : 0;
      };
    } else {
      return comparator;
    }
  }

  @Override
  protected Comparator<DoubleTextWrapper> provideItemComparator () {
    return (specificFilter != null && groupId == 0) || ChatId.isSupergroup(chatId) ? null : comparator;
  }

  @Override
  public boolean needUserStatusUiUpdates () {
    return true;
  }

  @Override
  @UiThread
  public void onUserStatusChanged (long userId, TdApi.UserStatus status, boolean uiOnly) {
    final boolean isSearching = isSearching();
    if (this.data != null && !this.data.isEmpty()) {
      updateUserStatus(this.data, userId, !uiOnly, !isSearching);
    }
    if (this.searchData != null && !this.searchData.isEmpty()) {
      updateUserStatus(this.searchData, userId, !uiOnly, isSearching);
    }
  }

  @Override
  public void onChatMemberStatusChange (final long memberChatId, final TdApi.ChatMember member) {
    tdlib.ui().post(() -> {
      if (!isDestroyed() && chatId == memberChatId) {
        final boolean needAdd = TD.matchesFilter(specificFilter != null ? specificFilter : new TdApi.SupergroupMembersFilterRecent(), member.status);
        if (needAdd) {
          addOrUpdateMember(member);
        } else {
          removeMember(member.memberId);
        }
      }
    });
  }
}
