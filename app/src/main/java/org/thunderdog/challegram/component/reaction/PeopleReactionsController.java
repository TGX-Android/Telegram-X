package org.thunderdog.challegram.component.reaction;

import android.content.Context;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.user.UserView;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.RecyclerViewController;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;

import me.vkryl.android.AnimatorUtils;

public class PeopleReactionsController extends RecyclerViewController<Void> implements Client.ResultHandler  {
    interface Callback {
        public void close();
    }
    public PeopleReactionsController (Context context, Tdlib tdlib, long chatId, long messageId, String reaction, Callback callback) {
        super(context, tdlib);
        this.chatId = chatId;
        this.messageId = messageId;
        this.reaction = reaction;
        users = new ArrayList<>();
        this.callback = callback;
    }

    @Override
    public int getId() {
        return R.id.controller_peopleReactions;
    }

    private final long chatId;
    private final long messageId;
    private final String reaction;
    private boolean isLoadingMore;
    private Callback callback;

    private int totalCount;
    private SettingsAdapter adapter;
    private String offset = null;
    private ArrayList<TGUser> users;

    @Override
    protected int getRecyclerBackground() {
        // TODO find better background shadow color
        return R.id.theme_color_messageSelection;
    }

    @Override
    protected void onCreateView(Context context, CustomRecyclerView recyclerView) {
        recyclerView.setAdapter(adapter = new SettingsAdapter(this) {
            @Override
            protected void setUser (ListItem item, int position, UserView userView, boolean isUpdate) {
                userView.setUser(new TGUser(tdlib, tdlib.chatUser(item.getLongId())));
            }
        });
        recyclerView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180l));
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled (RecyclerView recyclerView, int dx, int dy) {
                if (isFocused() && canLoadMore() && !isLoadingMore && offset != null) {
                    int lastVisiblePosition = ((LinearLayoutManager) recyclerView.getLayoutManager()).findLastVisibleItemPosition();
                    if (lastVisiblePosition + 10 >= users.size()) {
                        requestUsers();
                    }
                }
            }
        });
        requestUsers();
    }

    @Override
    public void onResult (TdApi.Object object) {
        switch (object.getConstructor()) {
            case TdApi.AddedReactions.CONSTRUCTOR: {
                isLoadingMore = false;
                TdApi.AddedReactions addedReactions = ((TdApi.AddedReactions) object);
                offset = addedReactions.nextOffset;
                totalCount = addedReactions.totalCount;
                TdApi.AddedReaction[] reactions = addedReactions.reactions;
                for (TdApi.AddedReaction reaction: reactions) {
                    TdApi.MessageSender messageSender = reaction.senderId;
                    switch (messageSender.getConstructor()) {
                        case TdApi.MessageSenderUser.CONSTRUCTOR: {
                            runOnUiThread(() -> adapter.addItem(-1, new ListItem(ListItem.TYPE_USER, R.id.user).setLongId(((TdApi.MessageSenderUser) messageSender).userId)));
                            break;
                        }
                        case TdApi.MessageSenderChat.CONSTRUCTOR: {
                            runOnUiThread(() -> adapter.addItem(-1, new ListItem(ListItem.TYPE_USER, R.id.user).setLongId(((TdApi.MessageSenderChat) messageSender).chatId)));
                            break;
                        }
                    }
                }
                break;
            }
            case TdApi.Error.CONSTRUCTOR: {
                callback.close();
                break;
            }
        }
    }

    private void requestUsers() {
        isLoadingMore = true;
        tdlib.send(new TdApi.GetMessageAddedReactions(chatId, messageId, reaction, offset, 100), this);
    }

    private boolean canLoadMore() {
        return users.size() < totalCount;
    }
}
