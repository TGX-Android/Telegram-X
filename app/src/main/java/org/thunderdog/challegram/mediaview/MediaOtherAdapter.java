/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 10/12/2016
 */
package org.thunderdog.challegram.mediaview;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.U;
import org.thunderdog.challegram.loader.ImageFile;

import java.util.ArrayList;

public class MediaOtherAdapter extends RecyclerView.Adapter<MediaOtherAdapter.Holder> {
  private final Context context;
  private final View.OnClickListener onClickListener;
  private ArrayList<ImageFile> images;

  public MediaOtherAdapter (Context context, View.OnClickListener onClickListener) {
    this.context = context;
    this.onClickListener = onClickListener;
  }

  public void setImages (ArrayList<ImageFile> images) {
    int oldItemCount = getItemCount();
    this.images = images;
    U.notifyItemsReplaced(this, oldItemCount);
  }

  @Override
  public Holder onCreateViewHolder (ViewGroup parent, int viewType) {
    MediaOtherView view = new MediaOtherView(context);
    view.setOnDeleteClick(onClickListener);
    return new Holder(view);
  }

  @Override
  public void onBindViewHolder (Holder holder, int position) {
    ((MediaOtherView) holder.itemView).setImage(images.get(position));
  }

  @Override
  public void onViewAttachedToWindow (Holder holder) {
    ((MediaOtherView) holder.itemView).attach();
  }

  @Override
  public void onViewDetachedFromWindow (Holder holder) {
    ((MediaOtherView) holder.itemView).detach();
  }

  @Override
  public void onViewRecycled (Holder holder) {
    ((MediaOtherView) holder.itemView).performDestroy();
  }

  public void removeImage (ImageFile imageFile) {
    int i = 0;
    for (ImageFile file : images) {
      if (file == imageFile) {
        images.remove(i);
        notifyItemRemoved(i);
        break;
      }
      i++;
    }
  }

  @Override
  public int getItemCount () {
    return images != null ? images.size() : 0;
  }

  public static class Holder extends RecyclerView.ViewHolder {
    public Holder (View itemView) {
      super(itemView);
    }
  }

}
