package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.drawable.DrawableCompat;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.EmojiToneHelper;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.ReactionListController;

import java.util.function.Consumer;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;

public class ReactionsLayout extends LinearLayout {
  private static final int BAR_HEIGHT = Screen.dp(60f);
  private static final int ICON_SIZE = Screen.dp(40f);
  private static final int TEXT_WIDTH_S = Screen.dp(30f);
  private static final int TEXT_WIDTH_M = Screen.dp(40f);
  private static final int TEXT_WIDTH_L = Screen.dp(50f);
  private static final int PADDING = Screen.dp(6f);

  private boolean useDarkMode;
  private ViewController<?> parentController;
  private String[] reactions;
  private Context context;
  private Tdlib tdlib;
  private TdApi.Message msg;
  private Consumer<String> onReactionClick;
  private Runnable onReactedClick;
  private Runnable onBackClick;

  public ReactionsLayout (Context context) {
    super(context);
    this.context = context;
  }

  public void init (
      ViewController<?> parentController,
      Tdlib tdlib,
      boolean useDarkMode,
      String[] reactions,
      TdApi.Message msg,
      Consumer<String> onReactionClick,
      Runnable onReactedClick,
      Runnable onBackClick
  ) {
    this.useDarkMode = useDarkMode;
    this.parentController = parentController;
    this.reactions = reactions;
    this.tdlib = tdlib;
    this.msg = msg;
    this.onReactionClick = onReactionClick;
    this.onReactedClick = onReactedClick;
    this.onBackClick = onBackClick;

    setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, BAR_HEIGHT));
    goToOptions();
  }

  private void goToUsers () {
    removeAllViews();

    setGravity(Gravity.CENTER_VERTICAL);

    ImageView backIcon = new ImageView(context);
    Drawable iconDrawable = getResources().getDrawable(R.drawable.baseline_arrow_back_24);
    int iconColor = Theme.getColor(R.id.theme_color_text);
    DrawableCompat.setTint(iconDrawable, iconColor);
    backIcon.setImageDrawable(iconDrawable);
    backIcon.setPadding(PADDING, 0, PADDING, 0);
    backIcon.setLayoutParams(new LinearLayout.LayoutParams(ICON_SIZE, ICON_SIZE));

    Views.setClickable(backIcon);
    RippleSupport.setTransparentSelector(backIcon);
    backIcon.setOnClickListener(view -> {
      goToOptions();
      onBackClick.run();
    });

    addView(backIcon);
  }

  private void goToOptions () {
    removeAllViews();

    LinearLayout reactedWrapper = new LinearLayout(context);
    reactedWrapper.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    reactedWrapper.setGravity(Gravity.CENTER_VERTICAL);

    ImageView reactedIcon = new ImageView(context);
    Drawable iconDrawable = getResources().getDrawable(R.drawable.baseline_favorite_20);
    int iconColor = Theme.getColor(R.id.theme_color_text);
    iconColor = ColorUtils.alphaColor(0.6f, iconColor);
    DrawableCompat.setTint(iconDrawable, iconColor);
    reactedIcon.setImageDrawable(iconDrawable);
    reactedIcon.setPadding(PADDING, 0, PADDING, 0);
    reactedIcon.setLayoutParams(new LinearLayout.LayoutParams(ICON_SIZE, ICON_SIZE));
    reactedWrapper.addView(reactedIcon);

    CustomTextView reactedText = new CustomTextView(context, tdlib);
    int reactedCount = 0;
    if (msg.interactionInfo != null) {
      for (int i = 0; i < msg.interactionInfo.reactions.length; i++) {
        reactedCount += msg.interactionInfo.reactions[i].totalCount;
      }
    }
    reactedText.setBoldText(String.valueOf(reactedCount), null, false);
    reactedText.setTextSize(18f);
    reactedText.setPadding(PADDING, 0, PADDING, 0);
    int textWidth = TEXT_WIDTH_S;
    if (reactedCount > 99) {
      textWidth = TEXT_WIDTH_L;
    } else if (reactedCount > 9) {
      textWidth = TEXT_WIDTH_M;
    }
    reactedText.setLayoutParams(new LinearLayout.LayoutParams(textWidth, ViewGroup.LayoutParams.WRAP_CONTENT));
    reactedWrapper.addView(reactedText);

    Views.setClickable(reactedWrapper);
    RippleSupport.setTransparentSelector(reactedWrapper);
    reactedWrapper.setOnClickListener(view -> {
      goToUsers();
      onReactedClick.run();
    });

    ReactionListController controller = new ReactionListController(context, tdlib);
    controller.setArguments(this);
    controller.get().setLayoutParams(new LinearLayout.LayoutParams(
        Screen.currentWidth() - ICON_SIZE - textWidth,
        BAR_HEIGHT
    ));

    addView(controller.get());
    addView(reactedWrapper);
  }

  public boolean useDarkMode () {
    return useDarkMode;
  }

  public String[] getReactions () {
    return reactions;
  }

  public Consumer<String> getOnReactionClick () {
    return onReactionClick;
  }

  public EmojiToneHelper.Delegate getToneDelegate () {
    return parentController != null && parentController instanceof EmojiToneHelper.Delegate ? (EmojiToneHelper.Delegate) parentController : null;
  }
}
