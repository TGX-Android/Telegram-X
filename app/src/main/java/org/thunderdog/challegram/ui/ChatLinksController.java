package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.user.UserView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibContext;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.vkryl.core.collection.IntList;

public class ChatLinksController extends RecyclerViewController<ChatLinksController.Args> implements View.OnClickListener {
    private SettingsAdapter adapter;

    private boolean isChannel;
    private boolean isOwner;
    private long chatId;
    private int adminId;
    @Nullable private InviteLinkController.Callback callback;

    private TdApi.ChatInviteLink currentInviteLink;

    private List<TdApi.ChatInviteLink> inviteLinks;
    private List<TdApi.ChatInviteLink> inviteLinksRevoked;
    private TdApi.ChatInviteLinkCount[] inviteLinkCounts;

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

    public static class Args {
        private final long chatId;
        private final int adminId;
        private final boolean isOwner;
        @Nullable private final InviteLinkController.Callback callback;
        @Nullable private final ChatLinksController parent;

        public Args (long chatId, int adminId, @Nullable InviteLinkController.Callback callback, @Nullable ChatLinksController parent, boolean isOwner) {
            this.chatId = chatId;
            this.adminId = adminId;
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
        adminId = args.adminId;
        callback = args.callback;
        isChannel = tdlib.isChannel(args.chatId);
        isOwner = args.isOwner;
    }

    @Override
    protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
        this.adapter = new SettingsAdapter(this) {
            @Override
            protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
                if (item.getId() == R.id.btn_inviteLink) {
                    TdApi.ChatInviteLink link = (TdApi.ChatInviteLink) item.getData();
                    view.setData(generateLinkSubtitle(link));
                    view.setTag(link);
                }
            }

            @Override
            protected void setUser (ListItem item, int position, UserView userView, boolean isUpdate) {
                if (isUpdate) {
                    userView.updateSubtext();
                } else {
                    TGUser tgUser = new TGUser(tdlib, tdlib.cache().user((int) item.getLongId()));
                    tgUser.setCustomStatus(Lang.plural(R.string.xLinks, item.getIntValue()));
                    userView.setUser(tgUser);
                    userView.setTag((int) item.getLongId());
                }
            }
        };

        requestLinkRebind();
        recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        recyclerView.setAdapter(this.adapter);
        UI.post(this::updateLinksExpiration, 1000L);
    }

    @Override
    public void onClick (View v) {
        switch (v.getId()) {
            case R.id.btn_openAdminInviteLinks:
                ChatLinksController cc = new ChatLinksController(context, tdlib);
                cc.setArguments(new ChatLinksController.Args(chatId, (Integer) v.getTag(), null, this, false));
                navigateTo(cc);
                break;
            case R.id.btn_inviteLink:
                TdApi.ChatInviteLink link = (TdApi.ChatInviteLink) v.getTag();

                if (link.isRevoked && link.memberCount == 0) {
                    deleteLink(link);
                    return;
                }

                StringList strings = new StringList(5);
                IntList icons = new IntList(5);
                IntList ids = new IntList(5);
                IntList colors = new IntList(5);

                if (!link.isRevoked) {
                    if (!link.isPrimary) {
                        ids.append(R.id.btn_edit);
                        strings.append(R.string.InviteLinkEdit);
                        icons.append(R.drawable.baseline_edit_24);
                        colors.append(OPTION_COLOR_NORMAL);
                    }

                    ids.append(R.id.btn_copyLink);
                    strings.append(R.string.InviteLinkCopy);
                    icons.append(R.drawable.baseline_content_copy_24);
                    colors.append(OPTION_COLOR_NORMAL);

                    ids.append(R.id.btn_shareLink);
                    strings.append(R.string.ShareLink);
                    icons.append(R.drawable.baseline_share_arrow_24);
                    colors.append(OPTION_COLOR_NORMAL);
                }

                if (link.memberCount > 0) {
                    ids.append(R.id.btn_viewInviteLinkMembers);
                    strings.append(R.string.InviteLinkViewMembers);
                    icons.append(R.drawable.baseline_visibility_24);
                    colors.append(OPTION_COLOR_NORMAL);
                }

                if (link.isRevoked) {
                    ids.append(R.id.btn_deleteLink);
                    strings.append(R.string.InviteLinkDelete);
                } else {
                    ids.append(R.id.btn_revokeLink);
                    strings.append(R.string.RevokeLink);
                }

                icons.append(R.drawable.baseline_delete_forever_24);
                colors.append(OPTION_COLOR_RED);

                showOptions(null, ids.get(), strings.get(), colors.get(), icons.get(), (itemView, id) -> {
                            switch (id) {
                                case R.id.btn_viewInviteLinkMembers:
                                    ChatLinkMembersController c2 = new ChatLinkMembersController(context, tdlib);
                                    c2.setArguments(new ChatLinkMembersController.Args(chatId, link.inviteLink));
                                    navigateTo(c2);
                                    break;
                                case R.id.btn_edit:
                                    EditChatLinkController c = new EditChatLinkController(context, tdlib);
                                    c.setArguments(new EditChatLinkController.Args(link, chatId, this));
                                    navigateTo(c);
                                    break;
                                case R.id.btn_copyLink:
                                    UI.copyText(link.inviteLink, R.string.CopiedLink);
                                    break;
                                case R.id.btn_shareLink:
                                    shareLink(link);
                                    break;
                                case R.id.btn_deleteLink:
                                    deleteLink(link);
                                    break;
                                case R.id.btn_revokeLink:
                                    showOptions(Lang.getString(isChannel ? R.string.AreYouSureRevokeInviteLinkChannel : R.string.AreYouSureRevokeInviteLinkGroup), new int[]{R.id.btn_revokeLink, R.id.btn_cancel}, new String[]{Lang.getString(R.string.RevokeLink), Lang.getString(R.string.Cancel)}, new int[]{OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[]{R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24}, (itemView2, id2) -> {
                                        if (id2 == R.id.btn_revokeLink) {
                                            revokeLink(link);
                                        }

                                        return true;
                                    });
                                    break;
                            }

                            return true;
                        }
                );
                break;
            case R.id.btn_deleteAllRevokedLinks:
                showOptions(Lang.getString(R.string.AreYouSureDeleteAllInviteLinks), new int[] {R.id.btn_deleteAllRevokedLinks, R.id.btn_cancel}, new String[] {Lang.getString(R.string.DeleteAllRevokedLinks), Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
                    if (id == R.id.btn_deleteAllRevokedLinks) {
                        tdlib.client().send(new TdApi.DeleteAllRevokedChatInviteLinks(chatId, adminId), result -> {
                            if (result.getConstructor() != TdApi.Ok.CONSTRUCTOR) return;
                            runOnUiThreadOptional(() -> {
                                TdApi.ChatInviteLink firstLink = inviteLinksRevoked.get(0);
                                TdApi.ChatInviteLink lastLink = inviteLinksRevoked.get(inviteLinksRevoked.size() - 1);
                                inviteLinksRevoked.clear();
                                smOnRevokedLinksCleared(firstLink, lastLink);
                                notifyParentIfPossible();
                            });
                        });
                    }

                    return true;
                });

                break;
            case R.id.btn_createInviteLink:
                EditChatLinkController c = new EditChatLinkController(context, tdlib);
                c.setArguments(new EditChatLinkController.Args(null, chatId, this));
                navigateTo(c);
                break;
        }
    }

    private void deleteLink (TdApi.ChatInviteLink link) {
        showOptions(Lang.getString(R.string.AreYouSureDeleteInviteLink), new int[]{R.id.btn_deleteLink, R.id.btn_cancel}, new String[]{Lang.getString(R.string.InviteLinkDelete), Lang.getString(R.string.Cancel)}, new int[]{OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[]{R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24}, (itemView2, id2) -> {
            if (id2 == R.id.btn_deleteLink) {
                tdlib.client().send(new TdApi.DeleteRevokedChatInviteLink(chatId, link.inviteLink), result -> {
                    if (result.getConstructor() != TdApi.Ok.CONSTRUCTOR) return;
                    runOnUiThreadOptional(() -> {
                        inviteLinksRevoked.remove(link);
                        smOnRevokedLinkDeleted(link);
                        notifyParentIfPossible();
                    });
                });
            }

            return true;
        });
    }

    private void notifyParentIfPossible () {
        if (getArgumentsStrict().parent == null) return;
        getArgumentsStrict().parent.smOnUserLinkCountChanged(adminId, inviteLinks.size() + inviteLinksRevoked.size());
    }

    private void revokeLink (TdApi.ChatInviteLink link) {
        tdlib.client().send(new TdApi.RevokeChatInviteLink(chatId, link.inviteLink), result -> {
            if (result.getConstructor() == TdApi.ChatInviteLinks.CONSTRUCTOR) {
                runOnUiThreadOptional(() -> {
                    final TdApi.ChatInviteLinks newInviteLink = (TdApi.ChatInviteLinks) result;

                    if (newInviteLink.inviteLinks.length > 0) {
                        inviteLinks.remove(link);
                        inviteLinksRevoked.add(0, newInviteLink.inviteLinks[0]);

                        if (link.isPrimary && newInviteLink.inviteLinks.length > 1) {
                            currentInviteLink = newInviteLink.inviteLinks[1];
                            inviteLinks.add(0, currentInviteLink);

                            smOnLinkEdited(link, currentInviteLink);
                            smOnLinkRevoked(null, inviteLinksRevoked.get(0));

                            if (callback != null && isOwner && adminId == tdlib().myUserId()) {
                                callback.onInviteLinkChanged(currentInviteLink);
                            }
                        } else {
                            smOnLinkRevoked(link, inviteLinksRevoked.get(0));
                        }
                    }
                });
            } else if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
                UI.showError(result);
            }
        });
    }

    private void shareLink (TdApi.ChatInviteLink link) {
        String chatName = tdlib.chatTitle(chatId);
        String exportText = Lang.getString(tdlib.isChannel(chatId) ? R.string.ShareTextChannelLink : R.string.ShareTextChatLink, chatName, link.inviteLink);
        String text = Lang.getString(R.string.ShareTextLink, chatName, link.inviteLink);
        ShareController c = new ShareController(context, tdlib);
        c.setArguments(new ShareController.Args(text).setShare(exportText, null));
        c.show();
    }

    private void updateLinksExpiration () {
        adapter.updateAllValuedSettingsById(R.id.btn_inviteLink);
        if (!isDestroyed()) UI.post(this::updateLinksExpiration, 1000L);
    }

    private String generateLinkSubtitle (TdApi.ChatInviteLink inviteLink) {
        StringBuilder subtitle = new StringBuilder();

        int exIn = (int) (inviteLink.expireDate - (System.currentTimeMillis() / 1000));

        if (inviteLink.memberCount > 0) {
            subtitle.append(Lang.getString(R.string.InviteLinkJoins, inviteLink.memberCount));
        } else if (inviteLink.isPrimary || inviteLink.memberLimit == 0 || (inviteLink.memberCount == 0 && exIn < 0)) {
            subtitle.append(Lang.getString(R.string.InviteLinkNoJoins));
        }

        if (inviteLink.isPrimary) {
            return subtitle.toString(); // primary links should only display join count
        } else {
            subtitle.append(" • ");
        }

        if (inviteLink.expireDate == 0 && inviteLink.memberCount == 0 && inviteLink.memberLimit > 0) {
            subtitle.append(Lang.getString(R.string.InviteLinkCanJoin, inviteLink.memberLimit)).append(" • ");
        } else if (inviteLink.memberLimit > inviteLink.memberCount && (exIn > 0 || inviteLink.expireDate == 0)) {
            subtitle.append(Lang.getString(R.string.InviteLinkRemains, inviteLink.memberLimit - inviteLink.memberCount)).append(inviteLink.expireDate != 0 ? " • " : "");
        }

        if (inviteLink.isRevoked) {
            subtitle.append(Lang.getString(R.string.InviteLinkRevoked));
        } else if (inviteLink.expireDate == 0) {
            // add nothing
        } else if (exIn > 0) {
            subtitle.append(Lang.getString(R.string.InviteLinkExpires, Lang.getDuration(exIn)));
        } else {
            subtitle.append(Lang.getString(R.string.InviteLinkExpired));
        }

        if (subtitle.charAt(subtitle.length() - 2) == '•') {
            subtitle.delete(subtitle.length() - 3, subtitle.length() - 1);
        }

        if (subtitle.charAt(1) == '•') {
            subtitle.delete(0, 3);
        }

        return subtitle.toString();
    }

    private void requestLinkRebind () {
        requestLinks(false, activeLinks -> {
            this.inviteLinks = new ArrayList<>(Arrays.asList(activeLinks.inviteLinks));
            requestLinks(true, revokedLinks -> {
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

    private void onTdlibRequestsDone() {
        buildCells();
        executeScheduledAnimation();
    }

    private void requestLinks (boolean revoked, Consumer<TdApi.ChatInviteLinks> linksConsumer) {
        tdlib.client().send(new TdApi.GetChatInviteLinks(chatId, adminId, revoked, 0, "", 20), object -> {
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

        ListItem linkItem = new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_inviteLink, 0, revokedInviteLink.inviteLink, false).setData(revokedInviteLink);
        ListItem linkItemSeparator = new ListItem(ListItem.TYPE_SEPARATOR_FULL);

        if (wasActiveInviteLink != null) adapter.removeRange(adapter.indexOfViewByData(wasActiveInviteLink) - 1, 2);

        if (shouldAddHeader) {
            int lastActiveLinkIdx;

            if (inviteLinks.size() > 1) {
                lastActiveLinkIdx = adapter.indexOfViewByData(inviteLinks.get(inviteLinks.size() - 1)) + 1;
            } else {
                // find the "Create Link" instead
                lastActiveLinkIdx = adapter.indexOfViewById(R.id.btn_createInviteLink) + 1;
            }

            lastActiveLinkIdx += 1; // include the shadow

            ListItem[] arr = new ListItem[] {
                    new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.RevokedInviteLinks),
                    new ListItem(ListItem.TYPE_SHADOW_TOP),
                    new ListItem(ListItem.TYPE_SETTING, R.id.btn_deleteAllRevokedLinks, 0, R.string.DeleteAllRevokedLinks).setTextColorId(R.id.theme_color_textNegative),
                    new ListItem(ListItem.TYPE_SEPARATOR_FULL),
                    linkItem,
                    new ListItem(ListItem.TYPE_SHADOW_BOTTOM)
            };

            adapter.addItems(lastActiveLinkIdx, arr);
        } else {
            int firstRevokedLinkIdx = adapter.indexOfViewByData(inviteLinksRevoked.get(1)) - 1; // 0 is our new link
            adapter.addItem(firstRevokedLinkIdx, linkItem);
            adapter.addItem(firstRevokedLinkIdx, linkItemSeparator);
        }
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
    }

    // Rebind link cell
    public void smOnLinkEdited (TdApi.ChatInviteLink oldLink, TdApi.ChatInviteLink newLink) {
        int oldLinkIndex = adapter.indexOfViewByData(oldLink);
        ListItem oldLinkItem = adapter.getItem(oldLinkIndex);

        if (oldLinkItem != null) {
            oldLinkItem.setString(newLink.inviteLink);
            oldLinkItem.setData(newLink);
            adapter.notifyItemChanged(oldLinkIndex);
        }
    }

    public void smOnLinkCreated (TdApi.ChatInviteLink newLink) {
        int newIndex = adapter.indexOfViewById(R.id.btn_createInviteLink) + 1;
        adapter.addItem(newIndex, new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_inviteLink, 0, newLink.inviteLink, false).setData(newLink));
        adapter.addItem(newIndex, new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    }

    public void smOnUserLinkCountChanged (int userId, int newCount) {
        int oldLinkIndex = adapter.indexOfViewByLongId(userId);
        ListItem oldLinkItem = adapter.getItem(oldLinkIndex);

        if (oldLinkItem != null) {
            oldLinkItem.setIntValue(newCount);
            adapter.notifyItemChanged(oldLinkIndex);
        }
    }

    //

    private void buildCells () {
        ArrayList<ListItem> items = new ArrayList<>();
        items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));

        int lastIvIndex = inviteLinks.size() - 1;
        int lastRvIndex = inviteLinksRevoked.size() - 1;

        for (TdApi.ChatInviteLink inviteLink : inviteLinks) {
            if (inviteLink.isPrimary) {
                currentInviteLink = inviteLink;
                items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.PrimaryInviteLink));
                items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
                items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_inviteLink, 0, inviteLink.inviteLink, false).setData(inviteLink));
                items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

                CharSequence hintText;

                if (adminId != tdlib.myUserId()) {
                    TdApi.User adminUser = tdlib.cache().user(adminId);
                    hintText = Lang.getMarkdownString(new TdlibContext(context, tdlib), R.string.InviteLinkOtherAdminHint, TD.getUserName(adminUser), tdlib.chatTitle(chatId));
                } else if (isChannel) {
                    hintText = Lang.getString(R.string.ChannelLinkInfo);
                } else {
                    hintText = Lang.getString(R.string.LinkInfo);
                }

                items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, hintText, false));

                items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.AdditionalInviteLinks));
                items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
                items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_createInviteLink, 0, R.string.CreateLink));
                if (inviteLinks.size() > 1) items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));

                continue;
            }

            items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_inviteLink, 0, inviteLink.inviteLink, false).setData(inviteLink));
            if (inviteLinks.indexOf(inviteLink) != lastIvIndex) items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        }

        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

        if (!inviteLinksRevoked.isEmpty()) {
            items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.RevokedInviteLinks));
            items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
            items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_deleteAllRevokedLinks, 0, R.string.DeleteAllRevokedLinks).setTextColorId(R.id.theme_color_textNegative));
            items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));

            for (TdApi.ChatInviteLink inviteLink : inviteLinksRevoked) {
                items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_inviteLink, 0, inviteLink.inviteLink, false).setData(inviteLink));
                if (inviteLinksRevoked.indexOf(inviteLink) != lastRvIndex) items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
            }

            items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        }

        if (inviteLinkCounts != null && inviteLinkCounts.length > 1) {
            items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.OtherAdminsInviteLinks));
            items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));

            for (int i = 0; i < inviteLinkCounts.length; i++) {
                TdApi.ChatInviteLinkCount linkCount = inviteLinkCounts[i];
                if (linkCount.userId == tdlib.myUserId()) continue;
                items.add(new ListItem(ListItem.TYPE_USER, R.id.btn_openAdminInviteLinks).setLongId(linkCount.userId).setIntValue(linkCount.inviteLinkCount + linkCount.revokedInviteLinkCount));
                if (i != inviteLinkCounts.length - 1) items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
            }

            items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        }

        adapter.setItems(items, false);
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
