package org.thunderdog.challegram.component.reactions;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.ReactionsListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.WeakHashMap;

import androidx.annotation.Nullable;

public final class ReactionsManager implements ChatListener, ReactionsListener {

    private static ReactionsManager sInstance;

    private final Tdlib tdlib;
    private long lastSubscribedChatId = 0;
    private String[] chatAvailableReactions;
    private Map<String, TdApi.Reaction> supportedReactions;
    private final Map<AvailableReactionsListener, Boolean> listeners = new WeakHashMap<>();
    private final Map<QuickReactionListener, Boolean> quickReactionListeners = new WeakHashMap<>();
    private final Map<ChatReactionsListener, Boolean> chatReactionListeners = new WeakHashMap<>();

    public static ReactionsManager instance(Tdlib tdlib) {
        if (sInstance == null) {
            sInstance = new ReactionsManager(tdlib);
        }
        return sInstance;
    }

    private ReactionsManager(Tdlib tdlib) {
        this.tdlib = tdlib;
        this.supportedReactions = createDictionary(tdlib.getSupportedReactions());
    }

    public @Nullable TdApi.Reaction getReaction(@Nullable String reaction) {
        if (reaction == null) return null;
        fillDictionaryIfEmpty();
        return supportedReactions.get(reaction);
    }

    public List<TdApi.Reaction> getChatAvailableReactions(long chatId) {
        if (chatId == lastSubscribedChatId && chatAvailableReactions != null && chatAvailableReactions.length > 0) {
            List<TdApi.Reaction> reactions = new ArrayList<>();
            for(String stringReaction : chatAvailableReactions) {
                reactions.add(getReaction(stringReaction));
            }
            return reactions;
        }
        return getSupportedReactions();
    }

    public TdApi.Reaction getQuickReaction() {
        String r = getQuickReactionString();
        if (r != null) {
            return getReaction(r);
        }
        return getDefaultReaction();
    }

    public String getQuickReactionString() {
        if (!isQuickReactionsEnabled()) return null;
        return Settings.instance().getQuickReaction();
    }

    public TdApi.Reaction getDefaultReaction() {
        List<TdApi.Reaction> reactions = this.getSupportedReactions();
        return !reactions.isEmpty() && isQuickReactionsEnabled() ? reactions.get(0) : null;
    }

    public void setQuickReaction(String reaction) {
        Settings.instance().setQuickReaction(reaction);
        Settings.instance().setQuickReactionEnabled(true);
        for (QuickReactionListener listener : quickReactionListeners.keySet()) {
            listener.onQuickReactionStateUpdated();
        }
    }

    public List<TdApi.Reaction> getSupportedReactions() {
        fillDictionaryIfEmpty();
        return new ArrayList<>(this.supportedReactions.values());
    }

    public boolean isQuickReactionsEnabled() {
        return Settings.instance().isQuickReactionEnabled();
    }

    public boolean isReactionSupported(String reaction) {
        if (supportedReactions == null) return false;
        TdApi.Reaction supportedReaction = this.supportedReactions.get(reaction);
        return supportedReaction != null && supportedReaction.isActive;
    }

    public void setQuickReactionEnabled(boolean isEnabled) {
        Settings.instance().setQuickReactionEnabled(isEnabled);
        for (QuickReactionListener listener : quickReactionListeners.keySet()) {
            listener.onQuickReactionStateUpdated();
        }
    }

    public void updateAvailableReactions(String[] availableReactions) {
        this.chatAvailableReactions = availableReactions;
        for (ChatReactionsListener listener : chatReactionListeners.keySet()) {
            listener.onChatReactionStateUpdated();
        }
    }

    public void setChatReactionsEnabled(long chatId, boolean isEnabled) {
        if (chatId == lastSubscribedChatId) {
            if (isEnabled) {
                this.chatAvailableReactions = supportedReactions.keySet().toArray(new String[0]);
            } else {
                this.chatAvailableReactions = new String[0];
            }
            for (ChatReactionsListener listener : chatReactionListeners.keySet()) {
                listener.onChatReactionStateUpdated();
            }
        }
    }

    public void commitAvailableReactions(long chatId) {
        if (chatId == lastSubscribedChatId) {
            tdlib.client().send(
                    new TdApi.SetChatAvailableReactions(lastSubscribedChatId, chatAvailableReactions),
                    object -> {}
            );
        }
    }

    public void sendMessageReaction(long chatId, long messageId, String reaction) {
        tdlib.client().send(new TdApi.SetMessageReaction(chatId, messageId, reaction, false), object -> {});
    }

    public void sendMessageReaction(long messageId, String reaction) {
        if (lastSubscribedChatId != -1) {
            tdlib.client().send(new TdApi.SetMessageReaction(lastSubscribedChatId, messageId, reaction, false), object -> {});
        }
    }

    public String[] getChatAvailableReactions() {
        return chatAvailableReactions;
    }

    public int getChatAvailableReactionsCount() {
        return chatAvailableReactions != null ? chatAvailableReactions.length : 0;
    }

    public int getSupportedReactionsCount() {
        fillDictionaryIfEmpty();
        return supportedReactions != null ? supportedReactions.size() : 0;
    }

    public void addAvailableReactionsListener(AvailableReactionsListener listener) {
        listeners.put(listener, true);
    }

    public void removeAvailableReactionsListener(AvailableReactionsListener listener) {
        listeners.remove(listener);
    }

    public void addQuickReactionStateListener(QuickReactionListener listener) {
        quickReactionListeners.put(listener, true);
    }

    public void removeQuickReactionStateListener(QuickReactionListener listener) {
        quickReactionListeners.remove(listener);
    }

    public void addChatReactionsListener(ChatReactionsListener listener) {
        chatReactionListeners.put(listener, true);
    }

    public void removeChatReactionsListener(ChatReactionsListener listener) {
        chatReactionListeners.remove(listener);
    }

    public void loadMessageAvailableReaction(long messageId) {
        this.tdlib.client().send(new TdApi.GetMessageAvailableReactions(lastSubscribedChatId, messageId), object -> {
            if (object.getConstructor() == TdApi.AvailableReactions.CONSTRUCTOR) {
                TdApi.AvailableReactions availableReactions = (TdApi.AvailableReactions) object;
                for (AvailableReactionsListener listener : listeners.keySet()) {
                    listener.onMessageAvailableReactions(lastSubscribedChatId, messageId, availableReactions.reactions);
                }
            }
        });
    }

    public void subscribeForUpdates(long chatId) {
        lastSubscribedChatId = chatId;
        this.tdlib.listeners().subscribeToChatUpdates(chatId, this);
        this.tdlib.listeners().subscribeToReactionUpdates(this);
        TdApi.Chat chat = tdlib.chat(lastSubscribedChatId);
        if (chat != null) updateAvailableReactions(chat.availableReactions);
    }

    public void unsubscribeFromUpdates() {
        this.tdlib.listeners().unsubscribeFromChatUpdates(lastSubscribedChatId, this);
        this.tdlib.listeners().unsubscribeFromReactionUpdates(this);
        lastSubscribedChatId = -1;
        chatAvailableReactions = null;
    }

    public void destroy() {
        listeners.clear();
        supportedReactions.clear();
        chatAvailableReactions = null;
    }

    private void fillDictionaryIfEmpty() {
        if (supportedReactions == null || supportedReactions.isEmpty()) {
            supportedReactions = createDictionary(tdlib.getSupportedReactions());
        }
    }

    @Override
    public void onChatAvailableReactionsUpdated(long chatId, String[] availableReactions) {
        if (chatId == lastSubscribedChatId && availableReactions != null && availableReactions.length > 0) {
            chatAvailableReactions = Arrays.copyOf(availableReactions, availableReactions.length);
        }
    }

    @Override
    public void onSupportedReactionsUpdated(TdApi.Reaction[] supportedReactions) {
        this.supportedReactions = createDictionary(supportedReactions);
    }

    public interface AvailableReactionsListener {
        default void onMessageAvailableReactions(long chatId, long messageId, String[] reactions) {}
        default void onChatAvailableReactionsUpdated(long chatId, String[] reactions) {}
    }

    public interface QuickReactionListener {
        void onQuickReactionStateUpdated();
    }

    public interface ChatReactionsListener {
        void onChatReactionStateUpdated();
    }

    private Map<String, TdApi.Reaction> createDictionary(TdApi.Reaction[] reactions) {
        final Map<String, TdApi.Reaction> reactionMap = new TreeMap<>();
        if (reactions == null || reactions.length == 0) return reactionMap;
        for (TdApi.Reaction reaction : reactions) {
            if (reaction.isActive) reactionMap.put(reaction.reaction, reaction);
        }
        return reactionMap;
    }
}
