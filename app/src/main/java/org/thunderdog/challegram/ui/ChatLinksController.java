/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.SpannableStringBuilder;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.component.user.RemoveHelper;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.DoubleTextWrapper;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibContext;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.CheckBoxView;
import org.thunderdog.challegram.widget.EmbeddableStickerView;
import org.thunderdog.challegram.widget.ForceTouchView;
import org.thunderdog.challegram.widget.ListInfoView;
import org.thunderdog.challegram.widget.SmallChatView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ChatLinksController extends RecyclerViewController<ChatLinksController.Args> implements View.OnClickListener, TGLegacyManager.EmojiLoadListener {
  private SettingsAdapter adapter;
  private static final String UTYAN_EMOJI = "\uD83E\uDD73";

  private boolean[] isLoadingContent = {false, false};
  private boolean isChannel;
  private boolean isOwner;
  private long chatId;
  private long adminUserId;
  @Nullable
  private InviteLinkController.Callback callback;

  private TdApi.ChatInviteLink currentInviteLink;

  private List<TdApi.ChatInviteLink> inviteLinks;
  private List<TdApi.ChatInviteLink> inviteLinksRevoked;
  private TdApi.ChatInviteLinkCount[] inviteLinkCounts;

  private int totalLinkCount;
  private int totalRevokedLinkCount;

  private List<TdApi.ChatInviteLink> pendingRefreshLinks = new ArrayList<>();
  private Handler cellUpdateHandler = new Handler(Looper.getMainLooper()) {
    @Override
    public void handleMessage (@NonNull Message msg) {
      requestUpdateLinkCell((TdApi.ChatInviteLink) msg.obj, false);
    }
  };

  private void requestUpdateLinkCell (TdApi.ChatInviteLink linkObj, boolean ignoreUpdate) {
    if (!ignoreUpdate) adapter.updateValuedSettingByData(linkObj);

    if (linkObj.isRevoked || TimeUnit.SECONDS.toMillis(linkObj.expirationDate) < tdlib.currentTimeMillis()) {
      cellUpdateHandler.removeMessages(0, linkObj);
      pendingRefreshLinks.remove(linkObj);
      return;
    }

    long relativeUpd = Lang.getNextReverseRelativeDateUpdateMs(linkObj.expirationDate, TimeUnit.SECONDS, tdlib.currentTimeMillis(), TimeUnit.MILLISECONDS, true, 0);
    if (relativeUpd != -1) {
      pendingRefreshLinks.add(linkObj);
      cellUpdateHandler.sendMessageDelayed(Message.obtain(cellUpdateHandler, 0, linkObj), relativeUpd);
    }
  }

  @Override
  public void onActivityResume () {
    super.onActivityResume();
    for (TdApi.ChatInviteLink linkToUpdate : new ArrayList<>(pendingRefreshLinks)) {
      requestUpdateLinkCell(linkToUpdate, false);
    }
  }

  @Override
  public void onActivityPause () {
    super.onActivityPause();
    cellUpdateHandler.removeMessages(0);
  }

  @Override
  public void destroy () {
    super.destroy();
    cellUpdateHandler.removeMessages(0);
    TGLegacyManager.instance().removeEmojiListener(this);
  }

  public void onLinkCreated (TdApi.ChatInviteLink newLink, @Nullable TdApi.ChatInviteLink existingLink) {
    if (existingLink != null) {
      int wasIndex = inviteLinks.indexOf(existingLink);
      inviteLinks.remove(existingLink);
      inviteLinks.add(wasIndex, newLink);
    } else {
      inviteLinks.add(1, newLink);
    }

    runOnUiThread(() -> {
      if (existingLink != null) {
        smOnLinkEdited(existingLink, newLink);
      } else {
        smOnLinkCreated(newLink);
        notifyParentIfPossible();
      }
    }, 250L);
  }

  @Override
  public void onEmojiUpdated (boolean isPackSwitch) {
    adapter.updateValuedSettingByLongId(chatId);
  }

  public static class Args {
    private final long chatId;
    private final long adminUserId;
    private final boolean isOwner;
    private final @Nullable InviteLinkController.Callback callback;
    private final @Nullable ViewController<?> parent;

    public Args (long chatId, long adminUserId, @Nullable InviteLinkController.Callback callback, @Nullable ViewController<?> parent, boolean isOwner) {
      this.chatId = chatId;
      this.adminUserId = adminUserId;
      this.callback = callback;
      this.parent = parent;
      this.isOwner = isOwner;
    }
  }

  public ChatLinksController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    chatId = args.chatId;
    adminUserId = args.adminUserId;
    callback = args.callback;
    isChannel = tdlib.isChannel(args.chatId);
    isOwner = args.isOwner;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    this.adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getId() == R.id.btn_deleteAllRevokedLinks) {
          view.setIconColorId(R.id.theme_color_textNegative);
        } else if (item.getId() == R.id.btn_inviteLink) {
          TdApi.ChatInviteLink link = (TdApi.ChatInviteLink) item.getData();
          view.setData(generateLinkSubtitle(link));
          view.setTag(link);
          view.setIconColorId(R.id.theme_color_icon);
        } else {
          if (item.getId() == R.id.btn_showAdvanced) {
            view.setTag(item.getIntValue() == 1);
          }

          view.setIconColorId(R.id.theme_color_icon);
        }
      }

      @Override
      protected void setEmbedSticker (ListItem item, int position, EmbeddableStickerView userView, boolean isUpdate) {
        TdApi.Sticker sticker = (TdApi.Sticker) item.getData();
        userView.setSticker(new TGStickerObj(tdlib, sticker, UTYAN_EMOJI, sticker.fullType));
        userView.setCaptionText(Lang.getString(isChannel ? R.string.ChannelLinkInfo : R.string.LinkInfo));
      }

      @Override
      protected void setInfo (ListItem item, int position, ListInfoView infoView) {
        if (item.getLongValue() > 0) {
          infoView.showInfo(Lang.getString(R.string.format_activeAndRevokedLinks, Lang.pluralBold(R.string.xActiveLinks, item.getIntValue()), Lang.pluralBold(R.string.xRevokedLinks, item.getLongValue())));
        } else {
          infoView.showInfo(Lang.pluralBold(R.string.xActiveLinks, item.getIntValue()));
        }
      }

      @Override
      protected void modifyDescription (ListItem item, TextView textView) {
        textView.setText(Emoji.instance().replaceEmoji(item.getString()));
      }

      @Override
      protected void modifyChatView (ListItem item, SmallChatView chatView, @Nullable CheckBoxView checkBox, boolean isUpdate) {
        DoubleTextWrapper wrapper = new DoubleTextWrapper(tdlib, item.getLongId(), true);
        wrapper.setSubtitle(Lang.pluralBold(R.string.xLinks, item.getIntValue()));
        wrapper.setIgnoreOnline(true);
        chatView.setChat(wrapper);
        chatView.setTag(item.getLongId());

        if (item.getId() == R.id.btn_openChat) {
          chatView.setPreviewChatId(new TdApi.ChatListMain(), adminUserId, null);
          chatView.setOnLongClickListener(v -> {
            showOptions(tdlib.cache().userName(adminUserId), new int[] { R.id.btn_openChat, R.id.btn_editRights }, new String[] { Lang.getString(R.string.OpenChat), Lang.getString(R.string.EditAdminRights) }, null, new int[] { R.drawable.baseline_forum_24, R.drawable.baseline_stars_24 }, (view, id) -> {
              if (id == R.id.btn_openChat) {
                tdlib.ui().openChat(ChatLinksController.this, adminUserId, new TdlibUi.ChatOpenParameters().keepStack());
              } else if (id == R.id.btn_editRights) {
                openRightsScreen();
              }
              
              return true;
            });

            return true;
          });
          chatView.setPreviewActionListProvider((v, forceTouchContext, ids, icons, strings, target) -> {
            ids.append(R.id.btn_openChat);
            icons.append(R.drawable.baseline_forum_24);
            strings.append(R.string.OpenChat);

            ids.append(R.id.btn_editRights);
            icons.append(R.drawable.baseline_stars_24);
            strings.append(R.string.EditAdminRights);

            forceTouchContext.setExcludeHeader(true);

            return new ForceTouchView.ActionListener() {
              @Override
              public void onForceTouchAction (ForceTouchView.ForceTouchContext context, int actionId, Object arg) {

              }

              @Override
              public void onAfterForceTouchAction (ForceTouchView.ForceTouchContext context, int actionId, Object arg) {
                switch (actionId) {
                  case R.id.btn_openChat:
                    tdlib.ui().openChat(ChatLinksController.this, adminUserId, new TdlibUi.ChatOpenParameters().keepStack());
                    break;
                  case R.id.btn_editRights:
                    openRightsScreen();
                    break;
                }
              }
            };
          });
        } else {
          chatView.clearPreviewChat();
          chatView.setOnLongClickListener(null);
          chatView.setPreviewActionListProvider(null);
        }
      }
    };

    requestLinkRebind();
    recyclerView.setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? View.OVER_SCROLL_IF_CONTENT_SCROLLS : View.OVER_SCROLL_NEVER);
    recyclerView.setAdapter(this.adapter);

    TGLegacyManager.instance().addEmojiListener(this);

    RemoveHelper.attach(recyclerView, new RemoveHelper.Callback() {
      @Override
      public boolean canRemove (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, int position) {
        if (adapter.getItems().isEmpty()) return false;
        ListItem item = (ListItem) adapter.getItems().get(position);
        return item != null && item.getId() == R.id.btn_inviteLink;
      }

      @Override
      public void onRemove (RecyclerView.ViewHolder viewHolder) {
        ListItem item = (ListItem) adapter.getItems().get(viewHolder.getBindingAdapterPosition());
        TdApi.ChatInviteLink link = (TdApi.ChatInviteLink) item.getData();

        if (link.isRevoked) {
          showOptions(Lang.getString(R.string.AreYouSureDeleteInviteLink), new int[]{R.id.btn_deleteLink, R.id.btn_cancel}, new String[]{Lang.getString(R.string.InviteLinkDelete), Lang.getString(R.string.Cancel)}, new int[]{ViewController.OPTION_COLOR_RED, ViewController.OPTION_COLOR_NORMAL}, new int[]{R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView2, id2) -> {
            if (id2 == R.id.btn_deleteLink) {
              inviteLinksRevoked.remove(link);
              smOnRevokedLinkDeleted(link);
              notifyParentIfPossible();
              tdlib.client().send(new TdApi.DeleteRevokedChatInviteLink(chatId, link.inviteLink), null);
            }

            return true;
          });
        } else {
          showOptions(Lang.getString(tdlib.isChannel(chatId) ? R.string.AreYouSureRevokeInviteLinkChannel : R.string.AreYouSureRevokeInviteLinkGroup), new int[]{R.id.btn_revokeLink, R.id.btn_cancel}, new String[]{Lang.getString(R.string.RevokeLink), Lang.getString(R.string.Cancel)}, new int[]{ViewController.OPTION_COLOR_RED, ViewController.OPTION_COLOR_NORMAL}, new int[]{R.drawable.baseline_link_off_24, R.drawable.baseline_cancel_24}, (itemView2, id2) -> {
            if (id2 == R.id.btn_revokeLink) {
              tdlib.client().send(new TdApi.RevokeChatInviteLink(chatId, link.inviteLink), result -> {
                if (result.getConstructor() == TdApi.ChatInviteLinks.CONSTRUCTOR) {
                  runOnUiThreadOptional(() -> onLinkRevoked(link, (TdApi.ChatInviteLinks) result));
                }
              });
            }

            return true;
          });
        }
      }
    });
  }

  private void openRightsScreen () {
    tdlib.client().send(new TdApi.GetChatMember(chatId, new TdApi.MessageSenderUser(adminUserId)), result -> {
      if (result.getConstructor() != TdApi.ChatMember.CONSTRUCTOR) return;
      runOnUiThreadOptional(() -> {
        TdApi.ChatMember member = (TdApi.ChatMember) result;
        EditRightsController c = new EditRightsController(context, tdlib);
        c.setArguments(new EditRightsController.Args(chatId, new TdApi.MessageSenderUser(adminUserId), false, tdlib.chatStatus(chatId), member).noFocusLock());
        navigateTo(c);
      });
    });
  }

  @Override
  public void onClick (View v) {
    switch (v.getId()) {
      case R.id.btn_openChat: {
        openRightsScreen();
        break;
      }
      case R.id.btn_openAdminInviteLinks:
        ChatLinksController cc = new ChatLinksController(context, tdlib);
        cc.setArguments(new ChatLinksController.Args(chatId, (Long) v.getTag(), null, this, false));
        navigateTo(cc);
        break;
      case R.id.btn_inviteLink:
        TdApi.ChatInviteLink link = (TdApi.ChatInviteLink) v.getTag();

        tdlib.ui().showInviteLinkOptions(this, link, chatId, false, false, () -> {
          inviteLinksRevoked.remove(link);
          smOnRevokedLinkDeleted(link);
          notifyParentIfPossible();
        }, (links) -> onLinkRevoked(link, links));

        break;
      case R.id.btn_deleteAllRevokedLinks:
        showOptions(Lang.getString(R.string.AreYouSureDeleteAllInviteLinks), new int[]{R.id.btn_deleteAllRevokedLinks, R.id.btn_cancel}, new String[]{Lang.getString(R.string.DeleteAllRevokedLinks), Lang.getString(R.string.Cancel)}, new int[]{OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[]{R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
          if (id == R.id.btn_deleteAllRevokedLinks) {
            TdApi.ChatInviteLink firstLink = inviteLinksRevoked.get(0);
            TdApi.ChatInviteLink lastLink = inviteLinksRevoked.get(inviteLinksRevoked.size() - 1);
            inviteLinksRevoked.clear();
            smOnRevokedLinksCleared(firstLink, lastLink);
            notifyParentIfPossible();
            tdlib.client().send(new TdApi.DeleteAllRevokedChatInviteLinks(chatId, adminUserId), null);
          }

          return true;
        });

        break;
      case R.id.btn_createInviteLink:
        EditChatLinkController c = new EditChatLinkController(context, tdlib);
        c.setArguments(new EditChatLinkController.Args(null, chatId, this));
        navigateTo(c);
        break;
      case R.id.btn_showAdvanced:
        loadMoreLinks((Boolean) v.getTag());
        break;
    }
  }

  private void notifyParentIfPossible () {
    smOnUserLinkCountChanged(adminUserId, inviteLinks.size());
    if (getArgumentsStrict().parent != null && getArgumentsStrict().parent instanceof ChatLinksController) {
      ((ChatLinksController) getArgumentsStrict().parent).smOnUserLinkCountChanged(adminUserId, inviteLinks.size());
    }
  }

  private void onLinkRevoked (TdApi.ChatInviteLink link, final TdApi.ChatInviteLinks newInviteLink) {
    if (newInviteLink.inviteLinks.length > 0) {
      inviteLinks.remove(link);
      inviteLinksRevoked.add(0, newInviteLink.inviteLinks[0]);

      if (link.isPrimary && newInviteLink.inviteLinks.length > 1) {
        currentInviteLink = newInviteLink.inviteLinks[1];
        inviteLinks.add(0, currentInviteLink);

        smOnLinkEdited(link, currentInviteLink);
        smOnLinkRevoked(null, inviteLinksRevoked.get(0));
        notifyParentIfPossible();

        if (callback != null && isOwner && adminUserId == tdlib().myUserId()) {
          callback.onInviteLinkChanged(currentInviteLink);
        }
      } else {
        smOnLinkRevoked(link, inviteLinksRevoked.get(0));
      }
    }
  }

  private CharSequence generateLinkSubtitle (TdApi.ChatInviteLink inviteLink) {
    SpannableStringBuilder subtitle = new SpannableStringBuilder();

    long nowMs = tdlib.currentTimeMillis();
    long expiresInMs = TimeUnit.SECONDS.toMillis(inviteLink.expirationDate) - nowMs;

    if (inviteLink.memberCount > 0) {
      subtitle.append(Lang.pluralBold(R.string.InviteLinkJoins, inviteLink.memberCount));
    } else if (inviteLink.isPrimary || inviteLink.memberLimit == 0 || (inviteLink.memberCount == 0 && inviteLink.isRevoked)) {
      subtitle.append(Lang.getString(R.string.InviteLinkNoJoins));
    }

    if (inviteLink.isPrimary) {
      return subtitle.toString(); // primary links should only display join count
    } else {
      subtitle.append(" • ");
    }

    if (inviteLink.createsJoinRequest && inviteLink.pendingJoinRequestCount > 0) {
      subtitle.append(Lang.pluralBold(R.string.xRequests, inviteLink.pendingJoinRequestCount)).append(" • ");
    }

    if (!inviteLink.isRevoked && inviteLink.memberLimit > 0) {
      if (inviteLink.memberCount == inviteLink.memberLimit) {
        subtitle.append(Lang.getString(R.string.InviteLinkMemberLimitReached));
      } else {
        subtitle.append(Lang.pluralBold(R.string.InviteLinkRemains, inviteLink.memberLimit - inviteLink.memberCount));
      }

      subtitle.append(inviteLink.expirationDate != 0 ? " • " : "");
    }

    if (inviteLink.isRevoked || inviteLink.expirationDate == 0) {
      // add nothing (no expire date or the link is revoked)
    } else if (expiresInMs > 0) {
      subtitle.append(Lang.getReverseRelativeDateBold(
        inviteLink.expirationDate, TimeUnit.SECONDS,
        nowMs, TimeUnit.MILLISECONDS,
        true, 0, R.string.InviteLinkExpires, false
      ));
    } else {
      subtitle.append(Lang.getStringBold(R.string.InviteLinkExpiredAt, Lang.getTimestamp(inviteLink.expirationDate, TimeUnit.SECONDS)));
    }

    if (subtitle.charAt(subtitle.length() - 2) == '•') {
      subtitle.delete(subtitle.length() - 3, subtitle.length() - 1);
    }

    if (subtitle.charAt(1) == '•') {
      subtitle.delete(0, 3);
    }

    return subtitle;
  }

  private void loadMoreLinks (boolean revoked) {
    if (isLoadingContent[revoked ? 1 : 0]) {
      return;
    } else {
      isLoadingContent[revoked ? 1 : 0] = true;
    }

    List<TdApi.ChatInviteLink> inviteList = revoked ? inviteLinksRevoked : inviteLinks;

    requestLinks(revoked, true, inviteList.get(inviteList.size() - 1).inviteLink, links -> {
      final int index = adapter.indexOfViewByIdAndValue(R.id.btn_showAdvanced, revoked ? 1 : 0);
      if (index == -1) return;

      runOnUiThreadOptional(() -> {
        inviteList.addAll(Arrays.asList(links.inviteLinks));
        List<ListItem> newItems = new ArrayList<>();
        for (int i = 0; i < links.inviteLinks.length; i++) {
          TdApi.ChatInviteLink inviteLink = links.inviteLinks[i];
          newItems.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_inviteLink, 0, simplifyInviteLink(inviteLink), false).setData(inviteLink));
          if (i != links.inviteLinks.length - 1) {
            newItems.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          }
        }

        if (inviteList.size() < (revoked ? totalRevokedLinkCount : totalLinkCount)) {
          newItems.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          newItems.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_showAdvanced, R.drawable.baseline_direction_arrow_down_24, Lang.plural(R.string.StatsXShowMore, Math.min(100, (revoked ? totalRevokedLinkCount : totalLinkCount) - inviteList.size())), false).setIntValue(revoked ? 1 : 0));
        }

        adapter.removeItem(index);
        adapter.addItems(index, newItems.toArray(new ListItem[0]));
        isLoadingContent[revoked ? 1 : 0] = false;
      });
    });
  }

  private void requestLinkRebind () {
    requestLinks(false, false, "", activeLinks -> {
      this.totalLinkCount = activeLinks.totalCount;
      this.inviteLinks = new ArrayList<>(Arrays.asList(activeLinks.inviteLinks));
      requestLinks(true, false, "", revokedLinks -> {
        this.totalRevokedLinkCount = revokedLinks.totalCount;
        this.inviteLinksRevoked = new ArrayList<>(Arrays.asList(revokedLinks.inviteLinks));

        if (isOwner) {
          requestAdminsWithLinks(admins -> {
            this.inviteLinkCounts = admins.inviteLinkCounts;
            runOnUiThreadOptional(this::onTdlibRequestsDone);
          });
        } else {
          runOnUiThreadOptional(this::onTdlibRequestsDone);
        }
      });
    });
  }

  private void onTdlibRequestsDone () {
    buildCells();
    executeScheduledAnimation();
  }

  private void requestLinks (boolean revoked, boolean maxedRequest, String offsetInviteLink, Consumer<TdApi.ChatInviteLinks> linksConsumer) {
    tdlib.client().send(new TdApi.GetChatInviteLinks(chatId, adminUserId, revoked, 0, offsetInviteLink, maxedRequest ? 100 : 20), object -> {
      if (object.getConstructor() == TdApi.ChatInviteLinks.CONSTRUCTOR) {
        linksConsumer.accept(((TdApi.ChatInviteLinks) object));
      } else if (object.getConstructor() == TdApi.Error.CONSTRUCTOR) {
        UI.showError(object);
      }
    });
  }

  private void requestAdminsWithLinks (Consumer<TdApi.ChatInviteLinkCounts> linksConsumer) {
    tdlib.client().send(new TdApi.GetChatInviteLinkCounts(chatId), object -> {
      if (object.getConstructor() == TdApi.ChatInviteLinkCounts.CONSTRUCTOR) {
        linksConsumer.accept(((TdApi.ChatInviteLinkCounts) object));
      } else if (object.getConstructor() == TdApi.Error.CONSTRUCTOR) {
        UI.showError(object);
      }
    });
  }

  // Helpers for Smart Animation
  // This is called after the actual list is changed

  // Remove everything from "Revoked Links", including the header
  public void smOnRevokedLinksCleared (TdApi.ChatInviteLink firstRevoked, TdApi.ChatInviteLink lastRevoked) {
    int firstLink = adapter.indexOfViewByData(firstRevoked);
    int lastLink = adapter.indexOfViewByData(lastRevoked);

    int startPos = firstLink - 4; // also remove header
    int itemCount = (lastLink - firstLink) + 6; // include the bottom shadow

    adapter.removeRange(startPos, itemCount);
  }

  // Remove from "Additional Links" and add to "Revoked Links". If there was no revoked links, add header.
  public void smOnLinkRevoked (TdApi.ChatInviteLink wasActiveInviteLink, TdApi.ChatInviteLink revokedInviteLink) {
    boolean shouldAddHeader = inviteLinksRevoked.size() == 1;

    ListItem linkItem = new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_inviteLink, 0, simplifyInviteLink(revokedInviteLink), false).setData(revokedInviteLink);
    ListItem linkItemSeparator = new ListItem(ListItem.TYPE_SEPARATOR_FULL);

    if (wasActiveInviteLink != null)
      adapter.removeRange(adapter.indexOfViewByData(wasActiveInviteLink) - 1, 2);

    if (shouldAddHeader) {
      int lastActiveLinkIdx;

      if (inviteLinkCounts != null && inviteLinkCounts.length > 1) {
        lastActiveLinkIdx = adapter.indexOfViewByData(inviteLinkCounts[inviteLinkCounts.length - 1]) + 1;
      } else if (inviteLinks.size() > 1) {
        lastActiveLinkIdx = adapter.indexOfViewByData(inviteLinks.get(inviteLinks.size() - 1)) + 2;
      } else {
        // find the "Create Link" instead
        if (adapter.indexOfViewById(R.id.btn_createInviteLink) == -1) {
          // Viewing other admin, so "Create button" is not available - using a hint as index then
          lastActiveLinkIdx = adapter.indexOfViewByType(ListItem.TYPE_DESCRIPTION) + 3;
        } else {
          lastActiveLinkIdx = adapter.indexOfViewById(R.id.btn_createInviteLink) + 2;
        }
      }

      lastActiveLinkIdx += 1; // include the shadow

      ListItem[] arr = new ListItem[]{
        new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.RevokedInviteLinks),
        new ListItem(ListItem.TYPE_SHADOW_TOP),
        new ListItem(ListItem.TYPE_SETTING, R.id.btn_deleteAllRevokedLinks, R.drawable.baseline_delete_24, R.string.DeleteAllRevokedLinks).setTextColorId(R.id.theme_color_textNegative),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        linkItem,
        new ListItem(ListItem.TYPE_SHADOW_BOTTOM)
      };

      adapter.addItems(Math.min(lastActiveLinkIdx, adapter.getItemCount()), arr);
    } else {
      int firstRevokedLinkIdx = adapter.indexOfViewByData(inviteLinksRevoked.get(1)) - 1; // 0 is our new link
      adapter.addItem(firstRevokedLinkIdx, linkItem);
      adapter.addItem(firstRevokedLinkIdx, linkItemSeparator);
    }

    if (adminUserId != tdlib.myUserId() && inviteLinks.size() == 1) {
      if (revokedInviteLink.isPrimary) return;
      // No additional links left, we can also remove header
      adapter.removeRange(adapter.indexOfViewById(R.id.btn_inviteLink) + 3, 3);
    }

    if (revokedInviteLink.isPrimary) {
      totalRevokedLinkCount++;
    } else {
      totalRevokedLinkCount++;
      totalLinkCount--;
    }

    updateTotalCount();
  }

  // Remove from "Revoked Links". If there is no revoked links left, remove header.
  public void smOnRevokedLinkDeleted (TdApi.ChatInviteLink revokedLink) {
    int revokedLinkIdx = adapter.indexOfViewByData(revokedLink);
    boolean shouldRemoveHeader = inviteLinksRevoked.isEmpty();

    if (shouldRemoveHeader) {
      adapter.removeRange(revokedLinkIdx - 4, 6);
    } else {
      adapter.removeRange(revokedLinkIdx - 1, 2);
    }

    totalRevokedLinkCount--;
    updateTotalCount();
  }

  // Makes pendingJoinRequestCount - 1. For seamless UI updates.
  public void onChatLinkPendingDecisionMade (String linkUrl) {
    TdApi.ChatInviteLink link = null;

    for (TdApi.ChatInviteLink compare : inviteLinks) {
      if (compare.inviteLink.equals(linkUrl)) {
        link = compare;
        break;
      }
    }

    if (link == null) {
      return;
    }

    link.pendingJoinRequestCount -= 1;
    onLinkCreated(link, link);
  }

  // Rebind link cell
  public void smOnLinkEdited (TdApi.ChatInviteLink oldLink, TdApi.ChatInviteLink newLink) {
    int oldLinkIndex = adapter.indexOfViewByData(oldLink);
    ListItem oldLinkItem = adapter.getItem(oldLinkIndex);

    if (oldLinkItem != null) {
      oldLinkItem.setString(simplifyInviteLink(newLink));
      oldLinkItem.setData(newLink);
      adapter.notifyItemChanged(oldLinkIndex);
    }
  }

  public void smOnLinkCreated (TdApi.ChatInviteLink newLink) {
    int newIndex = adapter.indexOfViewById(R.id.btn_createInviteLink) + 1;
    adapter.addItem(newIndex, new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_inviteLink, 0, simplifyInviteLink(newLink), false).setData(newLink));
    adapter.addItem(newIndex, new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    requestUpdateLinkCell(newLink, true);
    totalLinkCount++;
    updateTotalCount();
  }

  public void smOnUserLinkCountChanged (long userId, int newCount) {
    int oldLinkIndex = adapter.indexOfViewByLongId(userId);
    ListItem oldLinkItem = adapter.getItem(oldLinkIndex);

    if (oldLinkItem != null) {
      oldLinkItem.setIntValue(newCount);
      adapter.notifyItemChanged(oldLinkIndex);
    }

    updateTotalCount();
  }

  private void updateTotalCount() {
    int infoIndex = adapter.indexOfViewByType(ListItem.TYPE_LIST_INFO_VIEW);
    ListItem infoItem = adapter.getItem(infoIndex);
    if (infoItem != null) {
      int totalCount = 0;
      int totalCountRevoked = 0;

      for (ListItem item : adapter.getItems()) {
        if (item.getId() == R.id.btn_openAdminInviteLinks) {
          totalCount += item.getIntValue();
          totalCountRevoked += item.getLongValue();
        }
      }

      totalCount += totalLinkCount;
      totalCountRevoked += totalRevokedLinkCount;

      infoItem.setIntValue(totalCount).setLongValue(totalCountRevoked);
      adapter.updateValuedSettingByPosition(infoIndex);

      if (getArgumentsStrict().parent != null && getArgumentsStrict().parent instanceof ProfileController) {
        ((ProfileController) getArgumentsStrict().parent).onInviteLinkCountChanged(totalCount, totalCountRevoked);
      }
    }
  }

  private String simplifyInviteLink (TdApi.ChatInviteLink link) {
    if (link.name != null && !link.name.isEmpty()) {
      return link.name;
    }

    String[] linkSegments = link.inviteLink.split("/");
    return linkSegments[linkSegments.length - 1];
  }

  //

  private void buildCells () {
    ArrayList<ListItem> items = new ArrayList<>();

    int lastIvIndex = inviteLinks.size() - 1;
    int lastRvIndex = inviteLinksRevoked.size() - 1;
    boolean viewingOtherAdmin = adminUserId != tdlib.myUserId();
    boolean showAdditionalLinks = true;
    boolean primaryHeaderCreated = false;

    if (viewingOtherAdmin) {
      items.add(new ListItem(ListItem.TYPE_CHAT_SMALL, R.id.btn_openChat).setLongId(adminUserId).setIntValue(inviteLinks.size()));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    } else {
      items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
      items.add(new ListItem(ListItem.TYPE_EMBED_STICKER).setData(tdlib.findTgxEmoji(UTYAN_EMOJI)));
    }

    for (TdApi.ChatInviteLink inviteLink : inviteLinks) {
      if (inviteLink.isPrimary && !primaryHeaderCreated) {
        currentInviteLink = inviteLink;
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.PrimaryInviteLink));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_inviteLink, 0, simplifyInviteLink(inviteLink), false).setData(inviteLink));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

        if (viewingOtherAdmin) {
          TdApi.User adminUser = tdlib.cache().user(adminUserId);
          CharSequence hintText = Lang.getMarkdownString(new TdlibContext(context, tdlib), R.string.InviteLinkOtherAdminHint, TD.getUserName(adminUser), tdlib.chatTitle(chatId));
          items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, hintText, false).setLongId(chatId));
          showAdditionalLinks = inviteLinks.size() > 1;
        }

        if (showAdditionalLinks) {
          items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.AdditionalInviteLinks));
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
          if (!viewingOtherAdmin) items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_createInviteLink, R.drawable.baseline_add_link_24, R.string.CreateLink));
          if (inviteLinks.size() > 1) items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        }

        primaryHeaderCreated = true;
        continue;
      }

      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_inviteLink, 0, simplifyInviteLink(inviteLink), false).setData(inviteLink));
      requestUpdateLinkCell(inviteLink, true);
      if (inviteLinks.indexOf(inviteLink) != lastIvIndex)
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    }

    if (inviteLinks.size() < totalLinkCount) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_showAdvanced, R.drawable.baseline_direction_arrow_down_24, Lang.plural(R.string.StatsXShowMore, Math.min(100, totalLinkCount - inviteLinks.size())), false).setIntValue(0));
    }

    if (showAdditionalLinks) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      if (!viewingOtherAdmin) items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.AdditionalInviteLinksHint));
    }

    if (inviteLinkCounts != null && inviteLinkCounts.length > 1) {
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.OtherAdminsInviteLinks));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));

      for (int i = 0; i < inviteLinkCounts.length; i++) {
        TdApi.ChatInviteLinkCount linkCount = inviteLinkCounts[i];
        if (linkCount.userId == tdlib.myUserId()) continue;
        items.add(new ListItem(ListItem.TYPE_CHAT_SMALL, R.id.btn_openAdminInviteLinks).setLongId(linkCount.userId).setIntValue(linkCount.inviteLinkCount).setLongValue(linkCount.revokedInviteLinkCount).setData(linkCount));
        if (i != inviteLinkCounts.length - 1) items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      }

      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    if (!inviteLinksRevoked.isEmpty()) {
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.RevokedInviteLinks));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_deleteAllRevokedLinks, R.drawable.baseline_delete_24, R.string.DeleteAllRevokedLinks).setTextColorId(R.id.theme_color_textNegative));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));

      for (TdApi.ChatInviteLink inviteLink : inviteLinksRevoked) {
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_inviteLink, 0, simplifyInviteLink(inviteLink), false).setData(inviteLink));
        if (inviteLinksRevoked.indexOf(inviteLink) != lastRvIndex)
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      }

      if (inviteLinksRevoked.size() < totalRevokedLinkCount) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_showAdvanced, R.drawable.baseline_direction_arrow_down_24, Lang.plural(R.string.StatsXShowMore, Math.min(100, totalRevokedLinkCount - inviteLinksRevoked.size())), false).setIntValue(1));
      }

      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    items.add(new ListItem(ListItem.TYPE_LIST_INFO_VIEW));
    adapter.setItems(items, false);
    updateTotalCount();
  }

  @Override
  public int getId () {
    return R.id.controller_chatLinks;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.InviteLinks);
  }

  @Override
  public boolean needAsynchronousAnimation () {
    return inviteLinks == null;
  }
}
