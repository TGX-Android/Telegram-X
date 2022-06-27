package org.thunderdog.challegram.component.chat;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.sticker.StickerTinyView;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.CircleDrawable;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.NoScrollTextView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.widget.FrameLayoutFix;

@SuppressLint("ViewConstructor")
public class MessageReactionsBar extends LinearLayout {

  private final OnSelectReactionCallback callback;

  public MessageReactionsBar (@NonNull Context context, ViewController<?> parent, TGMessage message, OnSelectReactionCallback callback) {
    super(context);
    this.callback = callback;
    setOrientation(LinearLayout.HORIZONTAL);

    HorizontalScrollView horizontalScrollView = initScrollView(context);
    addView(horizontalScrollView);

    TdApi.MessageReaction chosenReaction = null;
    for (TdApi.MessageReaction reaction : message.getReactions()) {
      if (reaction.isChosen) {
        chosenReaction = reaction;
        break;
      }
    }

    TdApi.MessageReaction finalChosenReaction = chosenReaction;
    parent.tdlib().getMessageAvailableReactions(message.getChatId(), message.getId(), result -> { //tdlib.cache()
      List<TdApi.Reaction> availableReactions = mapReactions(parent.tdlib(), result.reactions);
      initReactionList(parent.tdlib(), horizontalScrollView, availableReactions, finalChosenReaction);
    });
  }

  @NonNull
  private List<TdApi.Reaction> mapReactions (Tdlib tdlib, String[] reactionIds) {
    List<TdApi.Reaction> availableReactions = new ArrayList<>();
    TdApi.Reaction[] supportReactions = tdlib.getSupportedReactions();
    for (String reaction : reactionIds) {
      for (TdApi.Reaction supportReaction : supportReactions) {
        if (supportReaction.reaction.equals(reaction)) {
          availableReactions.add(supportReaction);
          break;
        }
      }
    }
    return availableReactions;
  }

  @NonNull
  private HorizontalScrollView initScrollView (@NonNull Context context) {
    HorizontalScrollView horizontalScrollView = new HorizontalScrollView(context);
    horizontalScrollView.setLayoutParams(new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    horizontalScrollView.setHorizontalScrollBarEnabled(false);
    return horizontalScrollView;
  }

  private void initReactionList (Tdlib tdlib, HorizontalScrollView horizontalScrollView, List<TdApi.Reaction> availableReactions, @Nullable TdApi.MessageReaction chosenReaction) {
    LinearLayout linearLayout = new LinearLayout(getContext());
    linearLayout.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    linearLayout.setGravity(Gravity.CENTER_VERTICAL);
    linearLayout.setOrientation(LinearLayout.HORIZONTAL);

    for (TdApi.Reaction reaction : availableReactions) {
      StickerTinyView sticker = initSticker(tdlib, reaction, chosenReaction);
      linearLayout.addView(sticker);
    }
    horizontalScrollView.addView(linearLayout);
  }

  @NonNull
  private StickerTinyView initSticker (Tdlib tdlib, TdApi.Reaction reaction, @Nullable TdApi.MessageReaction chosenReaction) {
    StickerTinyView sticker = new StickerTinyView(getContext());
    sticker.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(40), Screen.dp(40)));
    sticker.setSticker(new TGStickerObj(tdlib, reaction.activateAnimation, "", reaction.activateAnimation.type));
    if (chosenReaction != null && chosenReaction.reaction.equals(reaction.reaction)) {
      sticker.setBackground(new CircleDrawable(R.id.theme_color_headerButton, 40f, true));
    }

    sticker.setCallback(new StickerTinyView.OnTouchCallback() {
      @Override
      public void onSingleTap () {
        callback.onSelectReaction(reaction, false);
      }

      @Override
      public void onLongRelease () {
        callback.onSelectReaction(reaction, true);
      }
    });

    return sticker;
  }

  public static TextView getCounterText (Context context, int color, int icon) {
    TextView text = new NoScrollTextView(context);
    text.setVisibility(View.GONE);
    text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
    final int colorId = getOptionColorId(color);
    text.setTextColor(Theme.getColor(colorId));
    text.setGravity(Gravity.CENTER_VERTICAL);

    text.setCompoundDrawablePadding(Screen.dp(8f));
    text.setClickable(false);

    if (icon != 0) {
      Drawable drawable = Drawables.get(context.getResources(), icon);
      if (drawable != null) {
        final int drawableColorId = color == ViewController.OPTION_COLOR_NORMAL ? R.id.theme_color_icon : colorId;
        drawable.setColorFilter(Paints.getColorFilter(Theme.getColor(drawableColorId)));
        if (Lang.rtl()) {
          text.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null);
        } else {
          text.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
        }
      }
    }
    return text;
  }

  private static @ThemeColorId
  int getOptionColorId (int color) {
    switch (color) {
      case ViewController.OPTION_COLOR_NORMAL: {
        return R.id.theme_color_text;
      }
      case ViewController.OPTION_COLOR_RED: {
        return R.id.theme_color_textNegative;
      }
      case ViewController.OPTION_COLOR_BLUE: {
        return R.id.theme_color_textNeutral;
      }
    }
    throw new IllegalArgumentException("color == " + color);
  }

  public interface OnSelectReactionCallback {
    void onSelectReaction (TdApi.Reaction reaction, boolean isBig);
  }
}
