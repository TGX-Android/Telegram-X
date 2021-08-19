package org.thunderdog.challegram.ui;

import android.content.Context;
import android.text.InputType;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.widget.FrameLayoutFix;

public class EditChatLinkController extends EditBaseController<EditChatLinkController.Args> implements View.OnClickListener {
    private static final int[] PRESETS = new int[]{0, 3600, 3600 * 24, 3600 * 24 * 7};

    private SettingsAdapter adapter;
    private int expireDate;
    private int memberLimit;
    private boolean isCreation;

    public EditChatLinkController(Context context, Tdlib tdlib) {
        super(context, tdlib);
    }

    @Override
    public void setArguments(Args args) {
        super.setArguments(args);
        isCreation = args.existingInviteLink == null;
        if (args.existingInviteLink != null) {
            expireDate = args.existingInviteLink.expireDate;
            memberLimit = args.existingInviteLink.memberLimit;
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btn_inviteLinkDateLimit) {
            int[] ids = new int[PRESETS.length];
            String[] strings = new String[PRESETS.length];

            for (int i = 0; i < PRESETS.length; i++) {
                ids[i] = i;
                strings[i] = i == 0 ? Lang.getString(R.string.InviteLinkNoLimitSet) : Lang.getDuration(PRESETS[i]);
            }

            showOptions(null, ids, strings, null, null, (itemView, id) -> {
                expireDate = id == 0 ? 0 : (int) ((System.currentTimeMillis() / 1000L) + PRESETS[id]);
                adapter.updateValuedSettingById(R.id.btn_inviteLinkDateLimit);
                return true;
            });
        } else if (v.getId() == R.id.btn_inviteLinkUserLimit) {
            openInputAlert(Lang.getString(R.string.InviteLinkLimitedByUsersItem), Lang.getString(R.string.InviteLinkLimitedByUsersAlertHint), R.string.Done, R.string.Cancel, String.valueOf(memberLimit), (inputView, result) -> {
                memberLimit = Math.min(Math.max(0, Integer.parseInt(result)), 99999);
                adapter.updateValuedSettingById(R.id.btn_inviteLinkUserLimit);
                return true;
            }, true).getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
        }
    }

    @Override
    public CharSequence getName() {
        return Lang.getString(isCreation ? R.string.CreateLink : R.string.InviteLinkEdit);
    }

    @Override
    protected boolean onDoneClick() {
        setDoneInProgress(true);
        tdlib.client().send(
                isCreation ? new TdApi.CreateChatInviteLink(getArgumentsStrict().chatId, expireDate, memberLimit) : new TdApi.EditChatInviteLink(getArgumentsStrict().chatId, getArgumentsStrict().existingInviteLink.inviteLink, expireDate, memberLimit)
                , result -> {
                    switch (result.getConstructor()) {
                        case TdApi.ChatInviteLink.CONSTRUCTOR: {
                            runOnUiThreadOptional(() -> {
                                getArgumentsStrict().controller.onLinkCreated((TdApi.ChatInviteLink) result, getArgumentsStrict().existingInviteLink);
                                navigateBack();
                            });

                            break;
                        }
                        case TdApi.Error.CONSTRUCTOR: {
                            runOnUiThreadOptional(() -> {
                                UI.showError(result);
                                setDoneInProgress(false);
                            });
                            break;
                        }
                        default: {
                            runOnUiThreadOptional(() -> {
                                Log.unexpectedTdlibResponse(result, isCreation ? TdApi.CreateChatInviteLink.class : TdApi.EditChatInviteLink.class, TdApi.ChatInviteLink.class);
                                setDoneInProgress(false);
                            });
                            break;
                        }
                    }
                });

        return true;
    }

    @Override
    public int getId() {
        return R.id.controller_editChatLink;
    }

    @Override
    protected void onCreateView(Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {
        setDoneVisible(true);
        setDoneIcon(R.drawable.baseline_check_24);

        adapter = new SettingsAdapter(this) {
            @Override
            protected void setValuedSetting(ListItem item, SettingView view, boolean isUpdate) {
                if (item.getId() == R.id.btn_inviteLinkDateLimit) {
                    view.setData(expireDate > 0 ? Lang.getUntilDate(expireDate, TimeUnit.SECONDS) : Lang.getString(R.string.InviteLinkNoLimitSet));
                } else if (item.getId() == R.id.btn_inviteLinkUserLimit) {
                    view.setData(memberLimit > 0 ? Lang.plural(R.string.xUsers, memberLimit) : Lang.getString(R.string.InviteLinkNoLimitSet));
                }
            }
        };

        List<ListItem> items = new ArrayList<>();

        items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));

        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.InviteLinkLimitedByPeriod));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_inviteLinkDateLimit, 0, R.string.InviteLinkLimitedByPeriodItem));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.InviteLinkLimitedByPeriodHint));

        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.InviteLinkLimitedByUsers));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_inviteLinkUserLimit, 0, R.string.InviteLinkLimitedByUsersItem));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.InviteLinkLimitedByUsersHint));

        adapter.setItems(items, false);
        recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        recyclerView.setAdapter(adapter);
    }

    public static class Args {
        @Nullable
        public final TdApi.ChatInviteLink existingInviteLink;
        public final long chatId;
        public final ChatLinksController controller;

        public Args(@Nullable TdApi.ChatInviteLink existingInviteLink, long chatId, ChatLinksController controller) {
            this.existingInviteLink = existingInviteLink;
            this.chatId = chatId;
            this.controller = controller;
        }
    }

    @Override
    protected int getRecyclerBackgroundColorId() {
        return R.id.theme_color_background;
    }
}
