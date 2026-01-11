package org.thunderdog.challegram.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.telegram.Tdlib;

/**
 * Wrapper for TdApi.SavedMessagesTag with UI-related functionality.
 * Tags are reactions with optional custom labels (Premium feature).
 */
public class TGSavedMessagesTag {
  private final Tdlib tdlib;
  private final TdApi.SavedMessagesTag tag;
  private final String key;

  @Nullable
  private TGReaction reaction;

  public TGSavedMessagesTag (@NonNull Tdlib tdlib, @NonNull TdApi.SavedMessagesTag tag) {
    this.tdlib = tdlib;
    this.tag = tag;
    this.key = TD.makeReactionKey(tag.tag);
    this.reaction = tdlib.getReaction(tag.tag);
  }

  /**
   * @return The reaction key for lookup
   */
  public String getKey () {
    return key;
  }

  /**
   * @return The underlying TdApi.SavedMessagesTag
   */
  public TdApi.SavedMessagesTag getTag () {
    return tag;
  }

  /**
   * @return The ReactionType used as the tag
   */
  public TdApi.ReactionType getReactionType () {
    return tag.tag;
  }

  /**
   * @return Custom label if set (0-12 chars), or null
   */
  @Nullable
  public String getLabel () {
    return tag.label != null && !tag.label.isEmpty() ? tag.label : null;
  }

  /**
   * @return Whether this tag has a custom label
   */
  public boolean hasLabel () {
    return tag.label != null && !tag.label.isEmpty();
  }

  /**
   * @return The number of messages with this tag
   */
  public int getCount () {
    return tag.count;
  }

  /**
   * @return Display text - label if set, otherwise emoji
   */
  public String getDisplayText () {
    if (hasLabel()) {
      return tag.label;
    }
    // Fallback to emoji representation
    if (tag.tag.getConstructor() == TdApi.ReactionTypeEmoji.CONSTRUCTOR) {
      return ((TdApi.ReactionTypeEmoji) tag.tag).emoji;
    }
    return "";
  }

  /**
   * @return The TGReaction for this tag (for sticker/emoji loading)
   */
  @Nullable
  public TGReaction getReaction () {
    if (reaction == null) {
      reaction = tdlib.getReaction(tag.tag);
    }
    return reaction;
  }

  /**
   * @return Whether this is a custom emoji tag (vs standard emoji)
   */
  public boolean isCustomEmoji () {
    return tag.tag.getConstructor() == TdApi.ReactionTypeCustomEmoji.CONSTRUCTOR;
  }

  /**
   * @return The custom emoji ID if this is a custom emoji tag, 0 otherwise
   */
  public long getCustomEmojiId () {
    if (tag.tag.getConstructor() == TdApi.ReactionTypeCustomEmoji.CONSTRUCTOR) {
      return ((TdApi.ReactionTypeCustomEmoji) tag.tag).customEmojiId;
    }
    return 0;
  }

  /**
   * @return The emoji string if this is an emoji tag, null otherwise
   */
  @Nullable
  public String getEmoji () {
    if (tag.tag.getConstructor() == TdApi.ReactionTypeEmoji.CONSTRUCTOR) {
      return ((TdApi.ReactionTypeEmoji) tag.tag).emoji;
    }
    return null;
  }

  /**
   * @return Static icon sticker for display
   */
  @Nullable
  public TGStickerObj getStaticIcon () {
    TGReaction r = getReaction();
    return r != null ? r.staticIconSicker() : null;
  }

  /**
   * Request sticker files for display
   */
  public void requestFiles (ComplexReceiver receiver, int key) {
    TGReaction r = getReaction();
    if (r != null) {
      TGStickerObj sticker = r.staticCenterAnimationSicker();
      if (sticker != null && sticker.getImage() != null) {
        receiver.getImageReceiver(key).requestFile(sticker.getImage());
      }
    }
  }

  /**
   * Updates the tag data (e.g., when count changes)
   */
  public TGSavedMessagesTag withUpdatedTag (TdApi.SavedMessagesTag newTag) {
    return new TGSavedMessagesTag(tdlib, newTag);
  }

  @Override
  public boolean equals (Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TGSavedMessagesTag that = (TGSavedMessagesTag) o;
    return key.equals(that.key);
  }

  @Override
  public int hashCode () {
    return key.hashCode();
  }
}
