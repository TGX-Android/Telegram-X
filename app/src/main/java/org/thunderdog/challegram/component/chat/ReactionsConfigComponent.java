package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.gcomb.ReactionReceiver;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.widget.ImageReceiverView;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.SimplestCheckBox;

import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;

public class ReactionsConfigComponent extends RecyclerView.Adapter<ReactionsConfigComponent.RccViewHolder> {
  public static final int HEIGHT = Screen.dp(120f);

  private final Delegate delegate;

  public ReactionsConfigComponent (Delegate delegate) {
    this.delegate = delegate;
  }

  @NonNull
  @Override
  public RccViewHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
    return new RccViewHolder(RccViewHolder.createItemView(parent));
  }

  @Override
  public void onBindViewHolder (@NonNull RccViewHolder holder, int position, @NonNull List<Object> payloads) {
    holder.bind(delegate.getAvailableReactions()[position], delegate, payloads.size() == 1 && payloads.get(0) instanceof Boolean);
  }

  @Override
  public void onBindViewHolder (@NonNull RccViewHolder holder, int position) {
    holder.bind(delegate.getAvailableReactions()[position], delegate, false);
  }

  @Override
  public void onViewAttachedToWindow (@NonNull RccViewHolder holder) {
    holder.onAttach();
  }

  @Override
  public void onViewDetachedFromWindow (@NonNull RccViewHolder holder) {
    holder.onDetached();
  }

  @Override
  public void onViewRecycled (@NonNull RccViewHolder holder) {
    holder.onRecycle();
  }

  @Override
  public int getItemCount () {
    return delegate.getAvailableReactions().length;
  }

  public static class RccViewHolder extends RecyclerView.ViewHolder {
    public RccViewHolder (@NonNull View itemView) {
      super(itemView);
    }

    public void bind (String emoji, Delegate delegate, boolean animated) {
      getView().setReaction(delegate.getReaction(emoji), delegate, animated);
      getView().setOnClickListener((v) -> getView().onClick(emoji, delegate));
    }

    public void onAttach () {
      getView().attach();
    }

    public void onDetached () {
      getView().detach();
    }

    public void onRecycle () {
      getView().destroy();
    }

    private RccView getView () {
      return (RccView) itemView;
    }

    private static View createItemView (ViewGroup parent) {
      RccView rv = new RccView(parent.getContext());
      RippleSupport.setTransparentSelector(rv);
      Views.setClickable(rv);
      rv.setLayoutParams(new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ReactionsConfigComponent.HEIGHT));
      return rv;
    }
  }

  public interface Delegate {
    Tdlib provideTdlib ();
    String[] getAvailableReactions ();

    boolean isReactionEnabled (String emoji);
    void toggleReaction (String emoji, int[] coords, Runnable onFirstFrameEnabled);
    TdApi.Reaction getReaction (String emoji);
  }

  public static class RccView extends View {
    private static final int SIZE = Screen.dp(32f);
    private final BoolAnimator isSelected = new BoolAnimator(this, AnimatorUtils.ACCELERATE_DECELERATE_INTERPOLATOR, 210l);
    private Text reactionName;
    private boolean needAdditionalPadding;

    private final ReactionReceiver receiver;

    public RccView (Context context) {
      super(context);
      setWillNotDraw(false);
      receiver = new ReactionReceiver(this);
    }

    public void attach () {
      receiver.attach();
    }

    public void detach () {
      receiver.detach();
    }

    public void destroy () {
      receiver.destroy();
    }

    public void setReaction (TdApi.Reaction reaction, Delegate delegate, boolean animated) {
      reactionName = new Text.Builder(reaction.title, 0, Paints.robotoStyleProvider(13), TextColorSets.Regular.NORMAL).addFlags(Text.FLAG_ALIGN_CENTER).maxLineCount(2).build();
      isSelected.setValue(delegate.isReactionEnabled(reaction.reaction), animated);
      receiver.setReaction(delegate.provideTdlib(), reaction, SIZE);
    }

    @Override
    protected void onDraw (Canvas canvas) {
      float alpha = MathUtils.fromTo(0.5f, 1f, isSelected.getFloatValue());
      int cx = getWidth() / 2;
      int cy = getHeight() / 2;
      int cbPad = Screen.dp(2f);
      int baselinePad = Screen.dp(9f);

      receiver.setBounds(cx, cy - (baselinePad * 2) - Screen.dp(4f), SIZE);
      receiver.draw(canvas, alpha);

      if (!needAdditionalPadding) needAdditionalPadding = reactionName.getLineCount() == 1;
      reactionName.changeMaxWidth(getWidth() - (baselinePad * 2));
      reactionName.draw(canvas, 0, getWidth(), 0, cy + baselinePad + (needAdditionalPadding ? reactionName.getLineHeight() / 2 : 0), null, alpha);

      float checkboxScale = .75f;
      final double radians = Math.toRadians(45f);
      final int checkboxX = receiver.getMainReceiver().centerX() + (int) ((float) receiver.getMainReceiver().getWidth() / 2 * Math.sin(radians));
      final int checkboxY = receiver.getMainReceiver().centerY() + (int) ((float) receiver.getMainReceiver().getHeight() / 2 * Math.cos(radians)) + Screen.dp(4f);

      canvas.save();
      canvas.translate(cbPad, cbPad);
      canvas.scale(checkboxScale, checkboxScale, checkboxX, checkboxY);
      DrawAlgorithms.drawSimplestCheckBox(canvas, receiver.getMainReceiver(), isSelected.getFloatValue());
      canvas.restore();
    }

    public void onClick (String emoji, Delegate delegate) {
      int[] coords = Views.getLocationOnScreen(this);
      delegate.toggleReaction(emoji, new int[] {
        coords[0] + receiver.getMainReceiver().centerX(),
        coords[1] + receiver.getMainReceiver().centerY(),
      }, receiver::playGif);

      boolean isEnabled = delegate.isReactionEnabled(emoji);
      isSelected.setValue(isEnabled, true);
    }
  }
}
