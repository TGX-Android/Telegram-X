package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGReaction;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.v.CustomRecyclerView;

import me.vkryl.android.widget.FrameLayoutFix;

public class ReactionsSelectorRecyclerView extends CustomRecyclerView {
  public ReactionsSelectorRecyclerView (@NonNull Context context, Tdlib tdlib, TdApi.Chat chat, String chosen, boolean rightPadding) {
    super(context);

    setHasFixedSize(true);
    setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? OVER_SCROLL_IF_CONTENT_SCROLLS : OVER_SCROLL_NEVER);
    setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, Lang.rtl()));
    setAdapter(adapter = new ReactionsAdapter(context, tdlib, chat.availableReactions, chosen));
    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(56), Gravity.CENTER_VERTICAL));
    setPadding(Screen.dp(8), 0, Screen.dp(rightPadding ? 8 : 0), 0);
    setClipToPadding(false);
  }

  ReactionsAdapter adapter;
  public void setDelegate (ReactionSelectDelegate delegate) {
    adapter.setDelegate(delegate);
  }

  private static class ReactionView extends FrameLayoutFix {
    public StickerSmallView stickerView;
    private boolean chosen;

    public ReactionView (Context context) {
      super(context);
      stickerView = new StickerSmallView(context, 0) {
        @Override
        public boolean dispatchTouchEvent (MotionEvent event) {
          return false;
        }
      };
      stickerView.setLayoutParams(newParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));
      addView(stickerView);
    }

    public void setReaction (TGReaction reaction, boolean isChosen) {
      this.chosen = isChosen;
      TGStickerObj centerAnimationSicker = reaction.newCenterAnimationSicker();
      if (centerAnimationSicker.getPreviewAnimation() != null) {
        centerAnimationSicker.getPreviewAnimation().setPlayOnce(true);
        stickerView.setPadding(0);
      } else {
        stickerView.setPadding(Screen.dp(4));
      }
      stickerView.setSticker(centerAnimationSicker);
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      super.onMeasure(MeasureSpec.makeMeasureSpec(Screen.dp(42f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Screen.dp(56f), MeasureSpec.EXACTLY));
    }

    @Override
    protected void dispatchDraw (Canvas c) {
      if (chosen) {
        c.drawCircle(getMeasuredWidth() / 2f, getMeasuredHeight() / 2f, Screen.dp(19.5f), Paints.fillingPaint(Theme.getColor(R.id.theme_color_badge)));
      }
      super.dispatchDraw(c);
    }
  }

  private static class ReactionHolder extends RecyclerView.ViewHolder {
    public ReactionHolder (@NonNull View itemView) {
      super(itemView);
    }

    public static ReactionHolder create (Context context, Tdlib tdlib) {
      ReactionView reactionView = new ReactionView(context);
      reactionView.stickerView.init(tdlib);
      reactionView.setId(R.id.btn_reactionSelector);
      reactionView.setLayoutParams(new RecyclerView.LayoutParams(Screen.dp(42), ViewGroup.LayoutParams.WRAP_CONTENT));
      return new ReactionHolder(reactionView);
    }
  }

  private static class ReactionsAdapter extends RecyclerView.Adapter<ReactionHolder> {
    private final Tdlib tdlib;
    private final Context context;
    private final String[] reactions;
    private final String chosen;
    private ReactionSelectDelegate delegate;

    ReactionsAdapter (Context context, Tdlib tdlib, String[] reactions, String chosen) {
      this.context = context;
      this.tdlib = tdlib;
      this.reactions = reactions;
      this.chosen = chosen;
    }

    public void setDelegate (ReactionSelectDelegate delegate) {
      this.delegate = delegate;
    }

    @NonNull
    @Override
    public ReactionHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      return ReactionHolder.create(context, tdlib);
    }

    @Override
    public void onBindViewHolder (@NonNull ReactionHolder holder, int position) {
      TGReaction reaction = tdlib.getReaction(reactions[position]);
      if (reaction == null) return;
      ((ReactionView) holder.itemView).setReaction(reaction, reaction.getReaction().reaction.equals(chosen));
      ((ReactionView) holder.itemView).setOnClickListener((v) -> {
        if (delegate != null) {
          delegate.onClick(v, reaction);
        }
      });
      ((ReactionView) holder.itemView).setOnLongClickListener((v) -> {
        if (delegate != null) {
          delegate.onLongClick(v, reaction);
        }
        return true;
      });
    }

    @Override
    public int getItemCount () {
      return reactions != null ? reactions.length : 0;
    }

    @Override
    public void onViewAttachedToWindow (ReactionHolder holder) {
      ((ReactionView) holder.itemView).stickerView.attach();
    }

    @Override
    public void onViewDetachedFromWindow (ReactionHolder holder) {
      ((ReactionView) holder.itemView).stickerView.detach();
    }

    @Override
    public void onViewRecycled (ReactionHolder holder) {
      ((ReactionView) holder.itemView).stickerView.performDestroy();
    }
  }

  public interface ReactionSelectDelegate {
    void onClick (View v, TGReaction reaction);

    void onLongClick (View v, TGReaction reaction);
  }
}
