/**
 * File created on 27/02/16 at 21:16
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.component.emoji;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.data.TGGif;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.EmojiLayout;

import java.util.ArrayList;

import me.vkryl.android.util.ClickHelper;

public class MediaGifsAdapter extends RecyclerView.Adapter<MediaGifsAdapter.GifHolder> implements View.OnClickListener, View.OnLongClickListener {
  private Context context;
  private ArrayList<TGGif> gifs;
  private Callback callback;
  private @Nullable ClickHelper.Delegate clickHelperDelegate;

  public MediaGifsAdapter (Context context, @Nullable ClickHelper.Delegate delegate) {
    this.context = context;
    this.clickHelperDelegate = delegate;
  }

  public interface Callback {
    void onGifPressed (View view, TdApi.Animation animation);
    void onGifLongPressed (View view, TdApi.Animation animation);
  }

  private boolean needHeader;

  public void setNeedHeader () {
    this.needHeader = true;
  }

  public void setCallback (Callback callback) {
    this.callback = callback;
  }

  public void setGIFs (ArrayList<TGGif> gifs) {
    int oldGifsCount = this.gifs != null ? this.gifs.size() : 0;
    int newGifsCount = gifs != null ? gifs.size() : 0;

    if (oldGifsCount == newGifsCount) {
      if (newGifsCount == 0) {
        return;
      }
      int index = 0;
      boolean hasDiff = false;
      for (TGGif gif : gifs) {
        if (gif.getId() != this.gifs.get(index).getId()) {
          hasDiff = true;
          break;
        }
        index++;
      }
      if (!hasDiff) {
        return;
      }
    }

    if (this.gifs != null && this.gifs.size() > 0) {
      int prevCount = this.gifs.size();
      this.gifs = null;
      // notifyItemRangeRemoved(needHeader ? 1 : 0, prevCount);
    }
    this.gifs = gifs;
    int count = gifs != null && !gifs.isEmpty() ? gifs.size() : 0;
    if (count > 0) {
      // notifyItemRangeInserted(needHeader ? 1 : 0, count);
    }
    notifyDataSetChanged();// FIXME
  }

  public void removeSavedGif (int gifId) {
    if (this.gifs == null || this.gifs.isEmpty()) {
      return;
    }
    if (this.gifs.size() == 1) {
      if (this.gifs.get(0).getId() == gifId) {
        clear();
      }
      return;
    }
    int i = 0;
    for (TGGif gif : gifs) {
      if (gif.getId() == gifId) {
        gifs.remove(i);
        // notifyItemRemoved(needHeader ? i + 1 : i);
        notifyDataSetChanged(); // FIXME
        break;
      }
      i++;
    }
  }

  public void clear () {
    setGIFs(null);
  }

  public TGGif getGif (int i) {
    return gifs.get(needHeader ? i - 1 : i);
  }

  @Override
  public void onClick (View v) {
    if (callback != null) {
      callback.onGifPressed(v, ((GifView) v).getGif().getAnimation());
    }
  }

  @Override
  public boolean onLongClick (View v) {
    if (callback != null) {
      callback.onGifLongPressed(v, ((GifView) v).getGif().getAnimation());
      return true;
    }
    return false;
  }

  @Override
  public GifHolder onCreateViewHolder (ViewGroup parent, int viewType) {
    return GifHolder.create(context, viewType, this, this, clickHelperDelegate);
  }

  @Override
  public void onBindViewHolder (GifHolder holder, int position) {
    ((GifView) holder.itemView).setGif(gifs.get(needHeader ? position - 1 : position));
  }

  @Override
  public int getItemViewType (int position) {
    return position == 0 && needHeader ? 1 : 0;
  }

  @Override
  public void onViewAttachedToWindow (GifHolder holder) {
    ((GifView) holder.itemView).attach();
  }

  @Override
  public void onViewDetachedFromWindow (GifHolder holder) {
    ((GifView) holder.itemView).detach();
  }

  @Override
  public int getItemCount () {
    return gifs == null ? 0 : gifs.size();
  }

  public static class GifHolder extends RecyclerView.ViewHolder {
    public GifHolder (View itemView) {
      super(itemView);
    }

    public static GifHolder create (Context context, int viewType, View.OnClickListener onClickListener, View.OnLongClickListener onLongClickListener, ClickHelper.Delegate delegate) {
      switch (viewType) {
        case 0: {
          GifView view = new GifView(context);
          if (delegate != null) {
            view.initWithDelegate(delegate);
          } else {
            view.setOnClickListener(onClickListener);
            view.setOnLongClickListener(onLongClickListener);
          }
          Views.setClickable(view);
          view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
          return new GifHolder(view);
        }
        case 1: {
          View view = new View(context);
          view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, EmojiLayout.getHeaderSize()));
          return new GifHolder(view);
        }
      }
      throw new RuntimeException();
    }
  }
}
