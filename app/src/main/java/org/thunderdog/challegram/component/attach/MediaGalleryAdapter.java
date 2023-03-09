/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 21/10/2016
 */
package org.thunderdog.challegram.component.attach;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.tool.Screen;

import java.util.ArrayList;

public class MediaGalleryAdapter extends RecyclerView.Adapter<MediaGalleryAdapter.MediaHolder> implements View.OnClickListener, View.OnLongClickListener, MeasuredAdapterDelegate, MediaGalleryImageView.ClickListener {
  public interface Callback {
    void onCameraRequested ();
    void onPhotoOrVideoPicked (ImageFile image);
    void onPhotoOrVideoSelected (int selectedCount, ImageFile image, int selectionIndex);
    boolean onPhotoOrVideoOpenRequested (ImageFile fromFile);
  }

  public static final int OPTION_SELECTABLE = 0x01;
  public static final int OPTION_ALWAYS_SELECTABLE = 0x02;
  public static final int OPTION_NEED_COUNTER = 0x04;
  public static final int OPTION_CAMERA_AVAILABLE = 0x08;
  public static final int OPTION_NEED_CAMERA = 0x10;

  private final Context context;
  private final RecyclerView parent;
  private final GridLayoutManager manager;
  private final Callback callback;
  private final boolean isSelectable;
  private final boolean isAlwaysSelectable; // when we click on a first photo, it will be selected
  private final boolean needCounter;
  private final boolean cameraAvailable;
  private boolean animationsEnabled;
  private boolean showCamera;

  public MediaGalleryAdapter (Context context, RecyclerView parent, GridLayoutManager manager, Callback callback, int options) {
    this.context = context;
    this.parent = parent;
    this.manager = manager;
    this.callback = callback;
    this.isSelectable = (options & OPTION_SELECTABLE) != 0;
    this.isAlwaysSelectable = isSelectable && (options & OPTION_ALWAYS_SELECTABLE) != 0;
    this.needCounter = (options & OPTION_NEED_COUNTER) != 0;
    this.cameraAvailable = false; // (options & OPTION_CAMERA_AVAILABLE) != 0 && (!Config.CUSTOM_CAMERA_ENABLED || (options & OPTION_NEED_CAMERA) != 0);
    this.showCamera = cameraAvailable && (options & OPTION_NEED_CAMERA) != 0;
    this.selected = new ArrayList<>();
  }

  public void setAnimationsEnabled (boolean isEnabled, LinearLayoutManager manager) {
    if (this.animationsEnabled != isEnabled) {
      this.animationsEnabled = true;

      int first = manager.findFirstVisibleItemPosition();
      int last = manager.findLastVisibleItemPosition();

      for (int i = first; i <= last; i++) {
        View view = manager.findViewByPosition(i);
        if (view != null && view instanceof MediaGalleryImageView) {
          ((MediaGalleryImageView) view).setAnimationsDisabled(!isEnabled);
        }
      }

      int rangeStart = showCamera ? 1 : 0;
      if (first > rangeStart) {
        notifyItemRangeChanged(rangeStart, first);
      }

      int rangeEnd = getItemCount();
      if (last < rangeEnd - 1) {
        notifyItemRangeChanged(last + 1, rangeEnd - 1 - last);
      }
    }
  }

  // Data

  private ArrayList<ImageFile> images;
  private final ArrayList<ImageFile> selected;

  public void setImages (ArrayList<ImageFile> images, boolean showCamera) {
    int oldItemCount = getItemCount();
    this.images = images;
    boolean oldShowCamera = this.showCamera;
    this.showCamera = cameraAvailable && showCamera;
    U.notifyItemsReplaced(this, oldItemCount, this.showCamera && oldShowCamera ? 1 : 0);
  }

  public int getSelectionIndex (ImageFile file) {
    if (isSelectable && selected.size() > 0) {
      return selected.indexOf(file);
    } else {
      return -1;
    }
  }

  public void clearSelectedImages (GridLayoutManager manager) {
    if (images == null || images.isEmpty()) {
      return;
    }
    selected.clear();
    int first = manager.findFirstVisibleItemPosition();
    int last = manager.findLastVisibleItemPosition();
    for (int i = first; i <= last; i++) {
      View v = manager.findViewByPosition(i);
      if (v != null && v instanceof MediaGalleryImageView) {
        ((MediaGalleryImageView) v).setChecked(-1, true);
      }
    }
    if (first > 0) {
      notifyItemRangeChanged(0, first);
    }
    int lastRange = (showCamera ? 1 : 0) + images.size();
    if (last < lastRange) {
      notifyItemRangeChanged(last, lastRange - last);
    }
  }

  // Adapter

  @Override
  public MediaHolder onCreateViewHolder (ViewGroup parent, int viewType) {
    return MediaHolder.create(context, viewType, this, this, isSelectable ? this : null);
  }

  @Override
  public void onViewAttachedToWindow (MediaHolder holder) {
    holder.attach();
  }

  @Override
  public void onViewDetachedFromWindow (MediaHolder holder) {
    holder.detach();
  }

  /*@Override
  public void onViewRecycled (MediaHolder holder) {
    switch (holder.getItemViewType()) {
      case MediaHolder.VIEW_TYPE_CAMERA: {
        ((MediaGalleryCameraView) holder.itemView).onDataDestroy();
        break;
      }
      case MediaHolder.VIEW_TYPE_MEDIA: {
        ((MediaGalleryImageView) holder.itemView).onDataDestroy();
        break;
      }
    }
  }*/

  private int indexOfImage (ImageFile image) {
    if (images != null) {
      int i = 0;
      for (ImageFile imageFile : images) {
        if (imageFile == image) {
          return i;
        }
        i++;
      }
    }
    return -1;
  }

  public View findViewForImage (ImageFile imageFile, LinearLayoutManager manager) {
    int i = indexOfImage(imageFile);
    if (i != -1) {
      return manager.findViewByPosition(i + (showCamera ? 1 : 0));
    }
    return null;
  }

  private ArrayList<ImageFile> invisbileFiles;

  private boolean isVisible (ImageFile imageFile) {
    if (invisbileFiles != null && !invisbileFiles.isEmpty()) {
      for (ImageFile image : invisbileFiles) {
        if (image == imageFile) {
          return false;
        }
      }
    }
    return true;
  }

  public void setImageVisible (ImageFile imageFile, boolean isVisible, RecyclerView.LayoutManager manager) {
    boolean nowIsVisible = isVisible(imageFile);

    if (isVisible != nowIsVisible) {
      if (isVisible) {
        final int size = invisbileFiles.size();
        for (int i = 0; i < size; i++) {
          if (invisbileFiles.get(i) == imageFile) {
            invisbileFiles.remove(i);
            break;
          }
        }
      } else {
        if (invisbileFiles == null) {
          invisbileFiles = new ArrayList<>();
        }
        invisbileFiles.add(imageFile);
      }

      int i = indexOfImage(imageFile);
      if (i != -1) {
        int adapterPosition = i + (showCamera ? 1 : 0);
        View view = manager.findViewByPosition(adapterPosition);
        if (view != null) {
          ((MediaGalleryImageView) view).setInvisible(!isVisible, true);
        } else {
          notifyItemChanged(adapterPosition);
        }
      }
    }
  }

  @Override
  public void onBindViewHolder (MediaHolder holder, int position) {
    switch (holder.getItemViewType()) {
      case MediaHolder.VIEW_TYPE_CAMERA: {
        // TODO
        break;
      }
      case MediaHolder.VIEW_TYPE_MEDIA: {
        ImageFile imageFile = images.get(showCamera ? position - 1 : position);
        holder.setImage(imageFile, getSelectionIndex(imageFile), isSelectable, isVisible(imageFile));
        holder.setAnimationsDisabled(!animationsEnabled);
        break;
      }
      case MediaHolder.VIEW_TYPE_COUNTER: {
        holder.setCounter(images.size());
        break;
      }
    }
  }

  @Override
  public int getItemCount () {
    return (images != null && !images.isEmpty() ? images.size() + (needCounter ? 1 : 0) : 0) + (showCamera ? 1 : 0);
  }

  @Override
  public int getItemViewType (int position) {
    return showCamera && position-- == 0 ? MediaHolder.VIEW_TYPE_CAMERA : position == images.size() ? MediaHolder.VIEW_TYPE_COUNTER : MediaHolder.VIEW_TYPE_MEDIA;
  }

  // Measure

  @Override
  public int measureHeight (int maxHeight) {
    int decorationWidth = Screen.dp(4f);
    int decorationTotalWidth = decorationWidth * (manager.getSpanCount() + 1);
    int height = ((parent.getMeasuredWidth() - decorationTotalWidth) / manager.getSpanCount() + decorationWidth) * (int) Math.ceil((float) getItemCount() / (float) manager.getSpanCount()) + decorationWidth;
    return maxHeight < 0 ? height : Math.min(maxHeight, height);
  }

  @Override
  public int measureScrollTop (int position) {
    int decorationWidth = Screen.dp(4f);
    int decorationTotalWidth = decorationWidth * (manager.getSpanCount() + 1);

    int spanCount = manager.getSpanCount();
    int itemSize = (parent.getMeasuredWidth() - decorationTotalWidth) / spanCount;

    View view = manager.findViewByPosition(manager.findFirstVisibleItemPosition());
    int actualSize = view != null ? view.getMeasuredWidth() : itemSize;

    return (actualSize + decorationWidth) * (int) Math.floor((float) position / (float) spanCount) + decorationWidth;
  }

  // Callbacks

  @Override
  public void onClick (View view, boolean isSelect) {
    ImageFile image = ((MediaGalleryImageView) view).getImage();

    if (!isSelect && callback.onPhotoOrVideoOpenRequested(image)) {
      return;
    }

    boolean inSelectMode = isAlwaysSelectable || selected.size() > 0;
    int selectionIndex = getSelectionIndex(image);
    int shiftIndex = -1;

    if (selectionIndex >= 0) {
      shiftIndex = selectionIndex;
      selected.remove(selectionIndex);
      selectionIndex = -1;
    } else if (inSelectMode) {
      selectionIndex = selected.size();
      selected.add(image);
    }
    if (inSelectMode) {
      ((MediaGalleryImageView) view).setChecked(selectionIndex, true);
      if (callback != null) {
        callback.onPhotoOrVideoSelected(selected.size(), image, selectionIndex);
      }
    } else {
      if (callback != null) {
        callback.onPhotoOrVideoPicked(image);
      }
    }

    shiftCounters(shiftIndex);
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.btn_camera) {
      if (callback != null) {
        callback.onCameraRequested();
      }
      return;
    }
  }

  public int getSelectedCount () {
    return selected.size();
  }

  public ArrayList<ImageFile> getSelectedPhotosAndVideosAsList (boolean copy) {
    if (selected.size() == 0) {
      return null;
    }
    if (copy) {
      ArrayList<ImageFile> imageFiles = new ArrayList<>(selected.size());
      imageFiles.addAll(selected);
      return imageFiles;
    }
    return selected;
  }

  public void setSelected (ImageFile imageFile, boolean isSelected) {
    int selectionIndex;
    int shiftIndex = -1;
    if (isSelected) {
      selectionIndex = selected.size();
      selected.add(imageFile);
    } else {
      selectionIndex = -1;
      shiftIndex = selected.indexOf(imageFile);
      if (shiftIndex != -1) {
        selected.remove(shiftIndex);
      }
    }

    if (callback != null) {
      callback.onPhotoOrVideoSelected(selected.size(), imageFile, selectionIndex);
    }

    int i = indexOfImage(imageFile);
    if (i != -1) {
      int adapterPosition = i + (showCamera ? 1 : 0);
      View view = manager.findViewByPosition(adapterPosition);
      if (view != null) {
        ((MediaGalleryImageView) view).setChecked(selectionIndex, false);
      } else {
        notifyItemChanged(adapterPosition);
      }
    }

    shiftCounters(shiftIndex);
  }

  private void shiftCounters (int startIndex) {
    final int count = selected.size();

    if (startIndex < 0 || startIndex >= count) {
      return;
    }

    int firstVisibleItemPosition = manager.findFirstVisibleItemPosition();
    int lastVisibleItemPosition = manager.findLastVisibleItemPosition();

    for (int i = startIndex; i < count; i++) {
      int index = indexOfImage(selected.get(i));
      if (index != -1) {
        if (showCamera) {
          index++;
        }
        if (firstVisibleItemPosition != RecyclerView.NO_POSITION && lastVisibleItemPosition != RecyclerView.NO_POSITION &&
          index >= firstVisibleItemPosition && index <= lastVisibleItemPosition) {
          View view = manager.findViewByPosition(index);
          if (view != null) {
            ((MediaGalleryImageView) view).setSelectionIndex(i);
            continue;
          }
        }
        notifyItemChanged(index);
      }
    }
  }

  @Override
  public boolean onLongClick (View v) {
    if (v.getId() == R.id.btn_camera) {
      return false;
    }

    ImageFile image = ((MediaGalleryImageView) v).getImage();
    int selectionIndex = getSelectionIndex(image);
    int shiftIndex = -1;

    if (selectionIndex >= 0) {
      shiftIndex = selectionIndex;
      selected.remove(selectionIndex);
      selectionIndex = -1;
    } else {
      selectionIndex = selected.size();
      selected.add(image);
    }

    ((MediaGalleryImageView) v).setChecked(selectionIndex, true);
    if (callback != null) {
      callback.onPhotoOrVideoSelected(selected.size(), image, selectionIndex);
    }

    shiftCounters(shiftIndex);

    return true;
  }


  // ViewHolder

  public static class MediaHolder extends RecyclerView.ViewHolder {
    public static final int VIEW_TYPE_MEDIA = 0x01;
    public static final int VIEW_TYPE_CAMERA = 0x02;
    public static final int VIEW_TYPE_COUNTER = 0x04;

    public MediaHolder (View itemView) {
      super(itemView);
    }

    public final void attach () {
      switch (getItemViewType()) {
        case VIEW_TYPE_CAMERA: {
          ((MediaGalleryCameraView) itemView).attach();
          break;
        }
        case VIEW_TYPE_MEDIA: {
          ((MediaGalleryImageView) itemView).attach();
          break;
        }
      }
    }

    public final void detach () {
      switch (getItemViewType()) {
        case VIEW_TYPE_CAMERA: {
          ((MediaGalleryCameraView) itemView).detach();
          break;
        }
        case VIEW_TYPE_MEDIA: {
          ((MediaGalleryImageView) itemView).detach();
          break;
        }
      }
    }

    public void setImage (ImageFile image, int selectionIndex, boolean isCheckable, boolean isVisible) {
      ((MediaGalleryImageView) itemView).setImage(image, selectionIndex, isCheckable);
      ((MediaGalleryImageView) itemView).setInvisible(!isVisible, false);
    }

    public void setAnimationsDisabled (boolean disabled) {
      ((MediaGalleryImageView) itemView).setAnimationsDisabled(disabled);
    }

    public void setCounter (int counter) {
      // TODO
    }

    public static MediaHolder create (Context context, int viewType, MediaGalleryImageView.ClickListener listener, View.OnClickListener onClickListener, View.OnLongClickListener onLongClickListener) {
      switch (viewType) {
        case VIEW_TYPE_MEDIA: {
          MediaGalleryImageView view = new MediaGalleryImageView(context);
          view.setClickListener(listener);
          view.setOnLongClickListener(onLongClickListener);
          return new MediaHolder(view);
        }
        case VIEW_TYPE_CAMERA: {
          MediaGalleryCameraView view = new MediaGalleryCameraView(context);
          view.setOnClickListener(onClickListener);
          view.setOnLongClickListener(onLongClickListener);
          MediaHolder holder = new MediaHolder(view);
          holder.setIsRecyclable(false);
          return holder;
        }
        case VIEW_TYPE_COUNTER: {
          break;
        }
      }
      throw new IllegalArgumentException("viewType == " + viewType);
    }
  }
}
