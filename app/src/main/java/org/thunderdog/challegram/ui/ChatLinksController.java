package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ChatLinksController extends RecyclerViewController<ChatLinksController.Args> implements View.OnClickListener {
    private static final ChatLinkComparator linkComparator = new ChatLinkComparator();

    private SettingsAdapter adapter;
    private boolean isChannel;
    private long chatId;
    private TdApi.ChatInviteLink currentInviteLink;
    private List<TdApi.ChatInviteLink> inviteLinks;
    private List<TdApi.ChatInviteLink> inviteLinksRevoked;
    private InviteLinkController.Callback callback;

    public void onLinkCreated(TdApi.ChatInviteLink newLink, @Nullable TdApi.ChatInviteLink existingLink) {
        if (existingLink != null) {
            int wasIndex = inviteLinks.indexOf(existingLink);
            inviteLinks.remove(existingLink);
            inviteLinks.add(wasIndex, newLink);
        } else {
            inviteLinks.add(1, newLink);
        }

        runOnUiThread(this::buildCells, 250L);
    }

    public static class Args {
        private final long chatId;
        private final InviteLinkController.Callback callback;

        public Args(long chatId, InviteLinkController.Callback callback) {
            this.chatId = chatId;
            this.callback = callback;
        }
    }

    public ChatLinksController(Context context, Tdlib tdlib) {
        super(context, tdlib);
    }

    @Override
    public void setArguments(Args args) {
        super.setArguments(args);
        chatId = args.chatId;
        callback = args.callback;
        isChannel = tdlib.isChannel(args.chatId);
    }

    @Override
    protected void onCreateView(Context context, CustomRecyclerView recyclerView) {
        this.adapter = new SettingsAdapter(this) {
            @Override
            protected void setValuedSetting(ListItem item, SettingView view, boolean isUpdate) {
                if (item.getId() == R.id.btn_inviteLink) {
                    TdApi.ChatInviteLink link = (TdApi.ChatInviteLink) item.getData();
                    view.setData(generateLinkSubtitle(link));
                    view.setTag(link);
                }
            }
        };

        requestLinkRebind();
        recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        recyclerView.setAdapter(this.adapter);
        UI.post(this::updateLinksExpiration, 1000L);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_inviteLink:
                TdApi.ChatInviteLink link = (TdApi.ChatInviteLink) v.getTag();
                if (link.isPrimary) return;

                String info = null;
                String[] strings;
                int[] icons;
                int[] ids;
                int[] colors;

                if (link.isRevoked) {
                    info = Lang.getString(R.string.AreYouSureDeleteInviteLink);
                    ids = new int[] {R.id.btn_deleteLink, R.id.btn_cancel};
                    strings = new String[] {Lang.getString(R.string.Delete), Lang.getString(R.string.Cancel)};
                    colors = new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL};
                    icons = new int[] {R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24};
                } else {
                    ids = new int[] {R.id.btn_edit, R.id.btn_copyLink, R.id.btn_shareLink, R.id.btn_revokeLink};
                    strings = new String[] {Lang.getString(R.string.InviteLinkEdit), Lang.getString(R.string.CopyLink), Lang.getString(R.string.ShareLink), Lang.getString(R.string.RevokeLink)};
                    colors = new int[] {OPTION_COLOR_NORMAL, OPTION_COLOR_NORMAL, OPTION_COLOR_NORMAL, OPTION_COLOR_RED};
                    icons = new int[] {R.drawable.baseline_edit_24, R.drawable.baseline_content_copy_24, R.drawable.baseline_share_arrow_24, R.drawable.baseline_delete_forever_24};
                }

                showOptions(
                        info,
                        ids,
                        strings,
                        colors,
                        icons,
                        (itemView, id) -> {
                            switch (id) {
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
                                    tdlib.client().send(new TdApi.DeleteRevokedChatInviteLink(chatId, link.inviteLink), result -> {
                                        switch (result.getConstructor()) {
                                            case TdApi.Ok.CONSTRUCTOR: {
                                                runOnUiThreadOptional(() -> {
                                                    inviteLinksRevoked.remove(link);
                                                    buildCells();
                                                });

                                                break;
                                            }
                                            case TdApi.Error.CONSTRUCTOR: {
                                                UI.showError(result);
                                                break;
                                            }
                                            default: {
                                                Log.unexpectedTdlibResponse(result, TdApi.ReplacePrimaryChatInviteLink.class, TdApi.ChatInviteLink.class);
                                                break;
                                            }
                                        }
                                    });
                                    break;
                                case R.id.btn_revokeLink:
                                    showOptions(Lang.getString(isChannel ? R.string.AreYouSureRevokeInviteLinkChannel : R.string.AreYouSureRevokeInviteLinkGroup), new int[] {R.id.btn_revokeLink, R.id.btn_cancel}, new String[] {Lang.getString(R.string.RevokeLink), Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24}, (itemView2, id2) -> {
                                        if (id2 == R.id.btn_revokeLink) {
                                            tdlib.client().send(new TdApi.RevokeChatInviteLink(chatId, link.inviteLink), result -> {
                                                switch (result.getConstructor()) {
                                                    case TdApi.ChatInviteLinks.CONSTRUCTOR: {
                                                        runOnUiThreadOptional(() -> {
                                                            final TdApi.ChatInviteLinks newInviteLink = (TdApi.ChatInviteLinks) result;
                                                            inviteLinks.remove(link);
                                                            if (newInviteLink.inviteLinks.length > 0) inviteLinksRevoked.add(newInviteLink.inviteLinks[0]);
                                                            sortLinks();
                                                            buildCells();
                                                        });

                                                        break;
                                                    }
                                                    case TdApi.Error.CONSTRUCTOR: {
                                                        UI.showError(result);
                                                        break;
                                                    }
                                                    default: {
                                                        Log.unexpectedTdlibResponse(result, TdApi.RevokeChatInviteLink.class, TdApi.ChatInviteLinks.class);
                                                        break;
                                                    }
                                                }
                                            });
                                        }

                                        return true;
                                    });
                                    break;
                            }

                            return true;
                        }
                );
                break;
            case R.id.btn_copyLink:
                UI.copyText(currentInviteLink.inviteLink, R.string.CopiedLink);
                break;
            case R.id.btn_shareLink:
                shareLink(currentInviteLink);
                break;
            case R.id.btn_revokeLink:
                showOptions(Lang.getString(isChannel ? R.string.AreYouSureRevokeInviteLinkChannel : R.string.AreYouSureRevokeInviteLinkGroup), new int[] {R.id.btn_revokeLink, R.id.btn_cancel}, new String[] {Lang.getString(R.string.RevokeLink), Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
                    if (id == R.id.btn_revokeLink) {
                        tdlib.client().send(new TdApi.ReplacePrimaryChatInviteLink(chatId), result -> {
                            switch (result.getConstructor()) {
                                case TdApi.ChatInviteLink.CONSTRUCTOR: {
                                    runOnUiThreadOptional(() -> {
                                        final TdApi.ChatInviteLink newInviteLink = (TdApi.ChatInviteLink) result;
                                        if (callback != null) callback.onInviteLinkChanged(newInviteLink);
                                        currentInviteLink = newInviteLink;
                                        inviteLinks.remove(0);
                                        inviteLinks.add(0, newInviteLink);
                                        buildCells();
                                    });

                                    break;
                                }
                                case TdApi.Error.CONSTRUCTOR: {
                                    UI.showError(result);
                                    break;
                                }
                                default: {
                                    Log.unexpectedTdlibResponse(result, TdApi.ReplacePrimaryChatInviteLink.class, TdApi.ChatInviteLink.class);
                                    break;
                                }
                            }
                        });
                    }

                    return true;
                });

                break;
            case R.id.btn_deleteAllRevokedLinks:
                showOptions(Lang.getString(R.string.AreYouSureDeleteAllInviteLinks), new int[] {R.id.btn_deleteAllRevokedLinks, R.id.btn_cancel}, new String[] {Lang.getString(R.string.DeleteAllRevokedLinks), Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
                    if (id == R.id.btn_deleteAllRevokedLinks) {
                        tdlib.client().send(new TdApi.DeleteAllRevokedChatInviteLinks(chatId, tdlib.myUserId()), result -> {
                            switch (result.getConstructor()) {
                                case TdApi.Ok.CONSTRUCTOR: {
                                    runOnUiThreadOptional(() -> {
                                        inviteLinksRevoked.clear();
                                        buildCells();
                                    });

                                    break;
                                }
                                case TdApi.Error.CONSTRUCTOR: {
                                    UI.showError(result);
                                    break;
                                }
                                default: {
                                    Log.unexpectedTdlibResponse(result, TdApi.ReplacePrimaryChatInviteLink.class, TdApi.ChatInviteLink.class);
                                    break;
                                }
                            }
                        });
                        buildCells();
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

    private void shareLink(TdApi.ChatInviteLink link) {
        String chatName = tdlib.chatTitle(chatId);
        String exportText = Lang.getString(tdlib.isChannel(chatId) ? R.string.ShareTextChannelLink : R.string.ShareTextChatLink, chatName, link.inviteLink);
        String text = Lang.getString(R.string.ShareTextLink, chatName, link.inviteLink);
        ShareController c = new ShareController(context, tdlib);
        c.setArguments(new ShareController.Args(text).setShare(exportText, null));
        c.show();
    }

    private void updateLinksExpiration() {
        adapter.updateAllValuedSettingsById(R.id.btn_inviteLink);
        if (!isDestroyed()) UI.post(this::updateLinksExpiration, 1000L);
    }

    private String generateLinkSubtitle(TdApi.ChatInviteLink inviteLink) {
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
        } else if (exIn < 0) {
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

    private void requestLinkRebind() {
        requestLinks(false, activeLinks -> {
            this.inviteLinks = new ArrayList<>(Arrays.asList(activeLinks.inviteLinks));
            requestLinks(true, revokedLinks -> {
                this.inviteLinksRevoked = new ArrayList<>(Arrays.asList(revokedLinks.inviteLinks));
                runOnUiThreadOptional(() -> {
                    buildCells();
                    executeScheduledAnimation();
                });
            });
        });
    }

    private void requestLinks(boolean revoked, Consumer<TdApi.ChatInviteLinks> linksConsumer) {
        tdlib.client().send(new TdApi.GetChatInviteLinks(chatId, tdlib.myUserId(), revoked, 0, "", 20), object -> {
            switch (object.getConstructor()) {
                case TdApi.ChatInviteLinks.CONSTRUCTOR: {
                    linksConsumer.accept(((TdApi.ChatInviteLinks) object));
                    break;
                }
                case TdApi.Error.CONSTRUCTOR: {
                    UI.showError(object);
                    break;
                }
                default: {
                    Log.unexpectedTdlibResponse(object, TdApi.GetChatInviteLinks.class, TdApi.ChatInviteLinks.class);
                    break;
                }
            }
        });
    }

    private void sortLinks() {
        Collections.sort(inviteLinks, linkComparator);
        Collections.sort(inviteLinksRevoked, linkComparator);
    }

    private void buildCells() {
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
                items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
                items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_copyLink, 0, R.string.CopyLink));
                items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
                items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_shareLink, 0, R.string.ShareLink));
                items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
                items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_revokeLink, 0, R.string.RevokeLink).setTextColorId(R.id.theme_color_textNegative));
                items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
                items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, isChannel ? R.string.ChannelLinkInfo : R.string.LinkInfo));

                items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.AdditionalInviteLinks));
                items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
                items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_createInviteLink, 0, R.string.CreateLink));
                if (lastIvIndex != 0) items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));

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

        adapter.setItems(items, false);
    }

    @Override
    public int getId() {
        return R.id.controller_chatLinks;
    }

    @Override
    public CharSequence getName() {
        return Lang.getString(R.string.InviteLinks);
    }

    @Override
    public boolean needAsynchronousAnimation () {
        return inviteLinks == null;
    }

    private static class ChatLinkComparator implements Comparator<TdApi.ChatInviteLink> {
        @Override
        public int compare(TdApi.ChatInviteLink x, TdApi.ChatInviteLink y) {
            return Integer.compare(
                    x.editDate != 0 ? x.editDate : x.date,
                    y.editDate != 0 ? y.editDate : y.date
            );
        }
    }
}
