package org.thunderdog.challegram.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.Tdlib;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Container for managing all Saved Messages tags.
 * Handles caching and lookup by reaction key.
 */
public class TGSavedMessagesTags {
  private final Tdlib tdlib;
  private final long savedMessagesTopicId;

  private final List<TGSavedMessagesTag> tagsList;
  private final Map<String, TGSavedMessagesTag> tagsMap;

  public TGSavedMessagesTags (@NonNull Tdlib tdlib, long savedMessagesTopicId, @Nullable TdApi.SavedMessagesTags tags) {
    this.tdlib = tdlib;
    this.savedMessagesTopicId = savedMessagesTopicId;
    this.tagsList = new ArrayList<>();
    this.tagsMap = new HashMap<>();

    if (tags != null && tags.tags != null) {
      setTags(tags);
    }
  }

  /**
   * @return The topic ID this tags container is for (0 for global)
   */
  public long getSavedMessagesTopicId () {
    return savedMessagesTopicId;
  }

  /**
   * Update tags from TDLib response
   */
  public void setTags (@NonNull TdApi.SavedMessagesTags tags) {
    tagsList.clear();
    tagsMap.clear();

    if (tags.tags != null) {
      for (TdApi.SavedMessagesTag tag : tags.tags) {
        TGSavedMessagesTag tgTag = new TGSavedMessagesTag(tdlib, tag);
        tagsList.add(tgTag);
        tagsMap.put(tgTag.getKey(), tgTag);
      }
    }
  }

  /**
   * @return Immutable list of all tags
   */
  public List<TGSavedMessagesTag> getTags () {
    return Collections.unmodifiableList(tagsList);
  }

  /**
   * @return Number of tags
   */
  public int getCount () {
    return tagsList.size();
  }

  /**
   * @return Whether there are any tags
   */
  public boolean isEmpty () {
    return tagsList.isEmpty();
  }

  /**
   * Get tag by its reaction key
   */
  @Nullable
  public TGSavedMessagesTag getTagByKey (String key) {
    return tagsMap.get(key);
  }

  /**
   * Get tag by ReactionType
   */
  @Nullable
  public TGSavedMessagesTag getTag (TdApi.ReactionType reactionType) {
    String key = TD.makeReactionKey(reactionType);
    return tagsMap.get(key);
  }

  /**
   * Get tag at index
   */
  @Nullable
  public TGSavedMessagesTag getTagAt (int index) {
    if (index >= 0 && index < tagsList.size()) {
      return tagsList.get(index);
    }
    return null;
  }

  /**
   * Find the label for a reaction type (if it's a tag with label)
   */
  @Nullable
  public String getLabelForReaction (TdApi.ReactionType reactionType) {
    TGSavedMessagesTag tag = getTag(reactionType);
    return tag != null ? tag.getLabel() : null;
  }

  /**
   * Check if a reaction type is used as a tag
   */
  public boolean containsTag (TdApi.ReactionType reactionType) {
    String key = TD.makeReactionKey(reactionType);
    return tagsMap.containsKey(key);
  }

  /**
   * Get total count of all tagged messages
   */
  public int getTotalTaggedCount () {
    int total = 0;
    for (TGSavedMessagesTag tag : tagsList) {
      total += tag.getCount();
    }
    return total;
  }

  /**
   * Create a copy with updated tags
   */
  public TGSavedMessagesTags withUpdatedTags (TdApi.SavedMessagesTags newTags) {
    TGSavedMessagesTags copy = new TGSavedMessagesTags(tdlib, savedMessagesTopicId, null);
    copy.setTags(newTags);
    return copy;
  }

  /**
   * Update a single tag's data (e.g., count changed)
   */
  public void updateTag (TdApi.SavedMessagesTag updatedTag) {
    String key = TD.makeReactionKey(updatedTag.tag);
    TGSavedMessagesTag existing = tagsMap.get(key);
    if (existing != null) {
      int index = tagsList.indexOf(existing);
      TGSavedMessagesTag newTag = new TGSavedMessagesTag(tdlib, updatedTag);
      if (index >= 0) {
        tagsList.set(index, newTag);
      }
      tagsMap.put(key, newTag);
    } else {
      // New tag
      TGSavedMessagesTag newTag = new TGSavedMessagesTag(tdlib, updatedTag);
      tagsList.add(newTag);
      tagsMap.put(key, newTag);
    }
  }

  /**
   * Remove a tag
   */
  public void removeTag (TdApi.ReactionType reactionType) {
    String key = TD.makeReactionKey(reactionType);
    TGSavedMessagesTag removed = tagsMap.remove(key);
    if (removed != null) {
      tagsList.remove(removed);
    }
  }
}
