package org.thunderdog.challegram.ui;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class SingleViewAdapter<V extends View> extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

  private final V singleView;

  public V getSingleView () {
    return singleView;
  }

  public SingleViewAdapter (V singleView) {
    this.singleView = singleView;
  }

  public V getView () {
    return singleView;
  }

  @NonNull
  @Override
  public RecyclerView.ViewHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
    return new RecyclerView.ViewHolder(singleView) {};
  }

  @Override
  public void onBindViewHolder (@NonNull RecyclerView.ViewHolder holder, int position) {
  }

  @Override
  public int getItemCount () {
    return 1;
  }
}
