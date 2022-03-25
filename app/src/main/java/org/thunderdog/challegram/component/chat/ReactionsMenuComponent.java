package org.thunderdog.challegram.component.chat;

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
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.ImageReceiverView;
import org.thunderdog.challegram.widget.PopupLayout;

public class ReactionsMenuComponent extends RecyclerView.Adapter<ReactionsMenuComponent.RmcViewHolder> {
  private final TGMessage message;
  private final TdApi.Chat sourceChat;
  private final String chosenReaction;
  private final PopupLayout layout;
  private final String[] availableReactions;

  public ReactionsMenuComponent (TGMessage message, TdApi.Chat sourceChat, PopupLayout layout, String[] availableReactions) {
    this.message = message;
    this.sourceChat = sourceChat;
    this.chosenReaction = message.getChosenReaction();
    this.layout = layout;
    this.availableReactions = availableReactions;
  }

  @NonNull
  @Override
  public RmcViewHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
    return new RmcViewHolder(RmcViewHolder.createItemView(parent));
  }

  @Override
  public void onBindViewHolder (@NonNull RmcViewHolder holder, int position) {
    holder.bind(message.tdlib(), availableReactions[position], message, layout, availableReactions[position].equals(chosenReaction));
  }

  @Override
  public void onViewAttachedToWindow (@NonNull RmcViewHolder holder) {
    holder.onAttach();
  }

  @Override
  public void onViewDetachedFromWindow (@NonNull RmcViewHolder holder) {
    holder.onDetached();
  }

  @Override
  public void onViewRecycled (@NonNull RmcViewHolder holder) {
    holder.onRecycle();
  }

  @Override
  public int getItemCount () {
    return availableReactions.length;
  }

  public static class RmcViewHolder extends RecyclerView.ViewHolder {
    public RmcViewHolder (@NonNull View itemView) {
      super(itemView);
    }

    public void bind (Tdlib tdlib, String reaction, TGMessage msg, PopupLayout layout, boolean isChosen) {
      ImageFile staticIconFile = new ImageFile(tdlib, tdlib.getReaction(reaction).staticIcon.sticker);
      staticIconFile.setSize(Screen.dp(18f));
      staticIconFile.setNoBlur();
      if (getEmojiView() != null) getEmojiView().getReceiver().requestFile(staticIconFile);

      RippleSupport.setCircleBackground(itemView, 36f, 3f, isChosen ? R.id.theme_color_fillingPositive : R.id.theme_color_filling);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) itemView.setStateListAnimator(null);

      itemView.setOnClickListener((v) -> {
        layout.hideWindow(true);
        tdlib.send(new TdApi.SetMessageReaction(msg.getChatId(), msg.getId(), reaction, false), (r) -> {});
      });
    }

    public void onAttach () {
      if (getEmojiView() != null) getEmojiView().attach();
    }

    public void onDetached () {
      if (getEmojiView() != null) getEmojiView().detach();
    }

    public void onRecycle () {
      if (getEmojiView() != null) getEmojiView().performDestroy();
    }

    private ImageReceiverView getEmojiView() {
      return (ImageReceiverView) ((ViewGroup) itemView).getChildAt(0);
    }

    private static ViewGroup createItemView (ViewGroup parent) {
      LinearLayout ll = new LinearLayout(parent.getContext());

      ImageReceiverView irv = new ImageReceiverView(parent.getContext());
      irv.setLayoutParams(new LinearLayout.LayoutParams(Screen.dp(18f), Screen.dp(18f)));
      ll.setGravity(Gravity.CENTER);
      ll.addView(irv);

      Views.setClickable(ll);

      ViewGroup.MarginLayoutParams mlp = new ViewGroup.MarginLayoutParams(Screen.dp(36f), Screen.dp(36f));
      mlp.setMargins(Screen.dp(10f), Screen.dp(10f), 0, Screen.dp(10f));
      ll.setLayoutParams(mlp);

      return ll;
    }
  }
}
