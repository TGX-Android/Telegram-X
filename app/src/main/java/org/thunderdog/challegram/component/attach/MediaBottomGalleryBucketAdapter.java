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
 * File created on 24/10/2016
 */
package org.thunderdog.challegram.component.attach;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Media;
import org.thunderdog.challegram.tool.Screen;

public class MediaBottomGalleryBucketAdapter extends RecyclerView.Adapter<MediaBottomGalleryBucketAdapter.BucketViewHolder> implements View.OnClickListener, MeasuredAdapterDelegate {
  public interface Callback {
    void onBucketSelected (Media.GalleryBucket bucket);
  }

  private final Context context;
  private final Callback callback;
  private final Media.Gallery gallery;

  public MediaBottomGalleryBucketAdapter (Context context, Callback callback, Media.Gallery gallery) {
    this.context = context;
    this.callback = callback;
    this.gallery = gallery;
  }

  @Override
  public BucketViewHolder onCreateViewHolder (ViewGroup parent, int viewType) {
    return BucketViewHolder.create(context, this);
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.bucket) {
      Media.GalleryBucket bucket = ((MediaBottomGalleryBucketView) v).getBucket();
      if (callback != null) {
        callback.onBucketSelected(bucket);
      }
    }
  }

  @Override
  public void onBindViewHolder (BucketViewHolder holder, int position) {
    holder.setBucket(gallery.getBucketForIndex(position));
  }

  @Override
  public int measureHeight (int maxHeight) {
    int height = getItemCount() * Screen.dp(48f);
    return maxHeight == -1 || maxHeight >= height ? height : maxHeight;
  }

  @Override
  public void onViewRecycled (BucketViewHolder holder) {
    holder.detach();
  }

  @Override
  public int measureScrollTop (int position) {
    return 0;
  }

  @Override
  public void onViewAttachedToWindow (BucketViewHolder holder) {
    holder.attach();
  }

  @Override
  public void onViewDetachedFromWindow (BucketViewHolder holder) {
    holder.detach();
  }

  @Override
  public int getItemCount () {
    return gallery.getBucketCount();
  }

  public static class BucketViewHolder extends RecyclerView.ViewHolder {
    public BucketViewHolder (View itemView) {
      super(itemView);
    }

    public void attach () {
      ((MediaBottomGalleryBucketView) itemView).attach();
    }

    public void detach () {
      ((MediaBottomGalleryBucketView) itemView).detach();
    }

    public void setBucket (Media.GalleryBucket bucket) {
      ((MediaBottomGalleryBucketView) itemView).setBucket(bucket);
    }

    public static BucketViewHolder create (Context context, View.OnClickListener onClickListener) {
      MediaBottomGalleryBucketView view = new MediaBottomGalleryBucketView(context);
      view.setId(R.id.bucket);
      view.setOnClickListener(onClickListener);
      return new BucketViewHolder(view);
    }
  }
}