package org.thunderdog.challegram.mediaview.data;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.mediaview.MediaStackCallback;
import org.thunderdog.challegram.telegram.Tdlib;

import java.util.ArrayList;

/**
 * Date: 09/12/2016
 * Author: default
 */

public class MediaStack {
  private final BaseActivity context;
  private final Tdlib tdlib;
  private int currentIndex;
  private ArrayList<MediaItem> items;
  private int estimatedBefore, estimatedAfter;

  private @Nullable
  MediaStackCallback callback;

  public MediaStack (BaseActivity context, Tdlib tdlib) {
    this.context = context;
    this.tdlib = tdlib;
    this.currentIndex = -1;
  }

  public void set (MediaItem item) {
    this.currentIndex = 0;
    this.items = new ArrayList<>();
    this.items.add(item);
  }

  public void set (ImageFile imageFile, ArrayList<ImageFile> imageFiles) {
    this.items = new ArrayList<>(imageFiles.size());

    int foundIndex = -1;
    int i = 0;
    for (ImageFile file : imageFiles) {
      if (file == imageFile) {
        foundIndex = i;
      }

      MediaItem item;
      if (file instanceof ImageGalleryFile) {
        item = new MediaItem(context, tdlib, (ImageGalleryFile) file);
      } else {
        continue;
      }
      items.add(item);

      i++;
    }

    if (foundIndex == -1) {
      throw new IllegalArgumentException("not found target image in the correspoding list");
    }
    currentIndex = foundIndex;
  }

  public void set (int currentIndex, ArrayList<MediaItem> items) {
    this.currentIndex = currentIndex;
    this.items = items;
  }

  public void forceIndex (int index) {
    if (this.currentIndex != index) {
      this.currentIndex = index;
      notifyMediaChanged(false);
    }
  }

  public void setCallback (@Nullable MediaStackCallback callback) {
    this.callback = callback;
  }

  public void setEstimatedSize (int estimatedBefore, int estimatedAfter) {
    if (this.estimatedBefore != estimatedBefore || this.estimatedAfter != estimatedAfter) {
      this.estimatedBefore = estimatedBefore;
      this.estimatedAfter = estimatedAfter;
      // notifyMediaChanged(false);
    }
  }

  public void insertItems (ArrayList<MediaItem> items, boolean onTop) {
    if (onTop) {
      this.items.addAll(0, items);
      currentIndex += items.size();
      estimatedBefore -= items.size();
      if (estimatedBefore < 0) {
        estimatedBefore = 0;
      }
    } else {
      this.items.addAll(items);
      estimatedAfter -= items.size();
      if (estimatedAfter < 0) {
        estimatedAfter = 0;
      }
    }
    notifyMediaChanged(true);
  }

  public int indexOfImageFile (ImageFile imageFile) {
    int i = 0;
    for (MediaItem item : items) {
      if (item.getSourceGalleryFile() == imageFile) {
        return i;
      }
      i++;
    }
    return -1;
  }

  public MediaItem get (int index) {
    return index >= 0 && index < items.size() ? items.get(index) : null;
  }

  // getters

  public int getEstimatedSize () {
    return getCurrentSize() + estimatedBefore + estimatedAfter;
  }

  public int getEstimatedIndex () {
    return currentIndex + estimatedBefore;
  }

  public int getCurrentSize () {
    return items != null ? items.size() : 0;
  }

  public ArrayList<MediaItem> getAll () {
    return items;
  }

  public boolean hasNext () {
    return currentIndex < getCurrentSize() - 1;
  }

  public boolean hasPrevious () {
    return currentIndex > 0;
  }

  public MediaItem getCurrent () {
    return items != null && currentIndex != -1 ? items.get(currentIndex) : null;
  }

  public MediaItem firstAvailable () {
    return items != null ? items.get(0) : null;
  }

  public MediaItem lastAvalable () {
    return items != null ? items.get(items.size() - 1) : null;
  }

  public int getCurrentIndex () {
    return currentIndex;
  }

  public MediaItem getNext () {
    return hasNext() ? items.get(currentIndex + 1) : null;
  }

  public MediaItem getPrevious () {
    return hasPrevious() ? items.get(currentIndex - 1) : null;
  }

  // appliers

  private void notifyMediaChanged (boolean itemsAdded) {
    if (callback != null) {
      callback.onMediaChanged(estimatedBefore + currentIndex, getEstimatedSize(), items.get(currentIndex), itemsAdded);
    }
  }

  public void applyNext () {
    currentIndex++;
    notifyMediaChanged(false);
  }

  public void applyPrevious () {
    currentIndex--;
    notifyMediaChanged(false);
  }
}
