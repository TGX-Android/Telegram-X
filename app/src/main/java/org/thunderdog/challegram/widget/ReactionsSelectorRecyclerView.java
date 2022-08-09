package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
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
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGReaction;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.Arrays;

import me.vkryl.android.widget.FrameLayoutFix;

public class ReactionsSelectorRecyclerView extends CustomRecyclerView {
  public ReactionsSelectorRecyclerView (@NonNull Context context, TGMessage message) {
    super(context);

    String chosen = message.getMessageReactions().getChosen();
    TdApi.AvailableReaction[] reactions = message.getMessageAvailableReactions();

    LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, Lang.rtl());
    setHasFixedSize(true);
    setOverScrollMode(Config.HAS_NICE_OVER_SCROLL_EFFECT ? OVER_SCROLL_IF_CONTENT_SCROLLS : OVER_SCROLL_NEVER);
    setLayoutManager(linearLayoutManager);
    setAdapter(adapter = new ReactionsAdapter(context, message));
    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_VERTICAL));
    setPadding(Screen.dp(9), Screen.dp(8), Screen.dp(9), Screen.dp(8));
    setClipToPadding(false);

    int index = -1;
    for (int a = 0; a < reactions.length; a++) {
      if (reactions[a].reaction.equals(chosen)) {
        index = a;
      }
    }

    if (index != -1) {
      linearLayoutManager.scrollToPositionWithOffset(index, Screen.currentWidth() / 2 - Screen.dp(38) / 2);
    }
  }

  ReactionsAdapter adapter;
  public void setDelegate (ReactionSelectDelegate delegate) {
    adapter.setDelegate(delegate);
  }

  private static class ReactionView extends FrameLayoutFix {
    public StickerSmallView stickerView;
    private Counter counter;
    private boolean chosen;
    private boolean useCounter;
    private RectF rectF;

    public ReactionView (Context context) {
      super(context);
      stickerView = new StickerSmallView(context, Screen.dp(-1)) {
        @Override
        public boolean dispatchTouchEvent (MotionEvent event) {
          return false;
        }
      };
      stickerView.setLayoutParams(newParams(Screen.dp(38), Screen.dp(38), Gravity.LEFT | Gravity.CENTER_VERTICAL));
      addView(stickerView);

      rectF = new RectF();
      counter = new Counter.Builder()
        .noBackground()
        .textColor(R.id.theme_color_fillingPositiveContent, R.id.theme_color_text, R.id.theme_color_text)
        .textSize(13f)
        .allBold(false)
        .callback(this)
        .build();
    }

    public void setReaction (TGReaction reaction, TdApi.MessageReaction messageReaction, boolean useCounter) {
      this.useCounter = useCounter;
      this.chosen = messageReaction.isChosen;
      TGStickerObj centerAnimationSicker = reaction.newCenterAnimationSicker();
      if (centerAnimationSicker.getPreviewAnimation() != null) {
        centerAnimationSicker.getPreviewAnimation().setPlayOnce(true);
        centerAnimationSicker.getPreviewAnimation().setLooped(false);
      }
      stickerView.setSticker(centerAnimationSicker);
      if (useCounter) {
        counter.setCount(messageReaction.totalCount, !messageReaction.isChosen, false);
      }
      requestLayout();
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      int width = Screen.dp(38f);
      int padding = Screen.dp(1);
      if (useCounter) {
        width += counter.getScaledWidth(Screen.dp(6));
      }

      rectF.set(padding, padding, width - padding, Screen.dp(37));

      super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec);
    }

    @Override
    protected void dispatchDraw (Canvas c) {
      if (chosen) {
        c.drawRoundRect(rectF, Screen.dp(18), Screen.dp(18), Paints.fillingPaint(Theme.getColor(R.id.theme_color_fillingPositive)));
      }
      if (useCounter) {
        counter.draw(c, Screen.dp(35), getMeasuredHeight() / 2f, Gravity.LEFT, 1f);
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
      //reactionView.setLayoutParams(new RecyclerView.LayoutParams(Screen.dp(42), ViewGroup.LayoutParams.WRAP_CONTENT));
      return new ReactionHolder(reactionView);
    }
  }

  private static class ReactionsAdapter extends RecyclerView.Adapter<ReactionHolder> {
    private final Tdlib tdlib;
    private final Context context;
    private final TGMessage message;
    private final TdApi.AvailableReaction[] reactions;
    private final String chosen;
    private ReactionSelectDelegate delegate;

    ReactionsAdapter (Context context, TGMessage message) {
      this.context = context;
      this.tdlib = message.tdlib();
      this.reactions = message.getMessageAvailableReactions();
      this.message = message;
      this.chosen = message.getMessageReactions().getChosen();
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
      String emoji = reactions[position].reaction;
      TGReaction reaction = tdlib.getReaction(emoji);
      TdApi.MessageReaction tdReaction = message.getMessageReactions().getTdMessageReaction(emoji);
      ReactionView view = (ReactionView) holder.itemView;
      if (reaction == null) return;

      final boolean needUseCounter = (message.isChannel() || !message.canGetAddedReactions()) && !message.useReactionBubbles();
      view.setReaction(reaction, tdReaction, needUseCounter);
      view.setOnClickListener((v) -> {
        if (delegate != null) {
          delegate.onClick(v, reaction);
        }
      });
      view.setOnLongClickListener((v) -> {
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
