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
import org.thunderdog.challegram.ui.ReactionCategoryListController;
import org.thunderdog.challegram.ui.ReactionListController;

import java.util.function.Consumer;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;

public class ReactionsLayout extends LinearLayout {
  private static final int BAR_HEIGHT = Screen.dp(60f);
  private static final int ICON_SIZE = Screen.dp(30f);
  private static final int BACK_ICON_SIZE = Screen.dp(40f);
  private static final int TEXT_WIDTH_S = Screen.dp(30f);
  private static final int TEXT_WIDTH_M = Screen.dp(40f);
  private static final int TEXT_WIDTH_L = Screen.dp(50f);
  private static final int PADDING = Screen.dp(6f);

  private boolean useDarkMode;
  private ViewController<?> parentController;
  private String[] reactions;
  private final Context context;
  private Tdlib tdlib;
  private TdApi.Message msg;
  private TdApi.Chat chat;
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
      TdApi.Chat chat,
      boolean options,
      Consumer<String> onReactionClick,
      Runnable onReactedClick,
      Runnable onBackClick
  ) {
    this.useDarkMode = useDarkMode;
    this.parentController = parentController;
    this.reactions = reactions;
    this.tdlib = tdlib;
    this.msg = msg;
    this.chat = chat;
    this.onReactionClick = onReactionClick;
    this.onReactedClick = onReactedClick;
    this.onBackClick = onBackClick;

    setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, BAR_HEIGHT));
    setGravity(Gravity.CENTER_VERTICAL);
    setBackgroundColor(Theme.getColor(R.id.theme_color_background));
    setOnClickListener(view -> {
    });

    if (options) {
      initOptions();
    } else {
      initUsers();
    }
  }

  private void initOptions () {
    removeAllViews();

    LinearLayout reactedWrapper = new LinearLayout(context);
    int wrapperWidth = 0;
    if (chat.type.getConstructor() != TdApi.ChatTypePrivate.CONSTRUCTOR) {
      reactedWrapper.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
      reactedWrapper.setGravity(Gravity.CENTER_VERTICAL);

      ImageView viewedIcon = new ImageView(context);
      Drawable viewedIconDrawable = getResources().getDrawable(R.drawable.baseline_visibility_24);
      int viewedIconColor = Theme.getColor(R.id.theme_color_text);
      viewedIconColor = ColorUtils.alphaColor(0.6f, viewedIconColor);
      DrawableCompat.setTint(viewedIconDrawable, viewedIconColor);
      viewedIcon.setImageDrawable(viewedIconDrawable);
      viewedIcon.setPadding(PADDING, 0, PADDING, 0);
      viewedIcon.setLayoutParams(new LinearLayout.LayoutParams(ICON_SIZE, ICON_SIZE));
      reactedWrapper.addView(viewedIcon);

      CustomTextView viewedText = new CustomTextView(context, tdlib);
      int viewedCount = 0;
      if (msg.interactionInfo != null) {
        viewedCount = msg.interactionInfo.viewCount;
      }
      viewedText.setBoldText(getFormattedCount(viewedCount), null, false);
      viewedText.setTextSize(16f);
      viewedText.setPadding(PADDING, 0, PADDING, 0);
      int viewedTextWidth = TEXT_WIDTH_S;
      if (viewedCount > 99) {
        viewedTextWidth = TEXT_WIDTH_L;
      } else if (viewedCount > 9) {
        viewedTextWidth = TEXT_WIDTH_M;
      }
      viewedText.setLayoutParams(new LinearLayout.LayoutParams(viewedTextWidth, ViewGroup.LayoutParams.WRAP_CONTENT));
      reactedWrapper.addView(viewedText);

      ImageView reactedIcon = new ImageView(context);
      Drawable reactedIconDrawable = getResources().getDrawable(R.drawable.baseline_favorite_20);
      int reactedIconColor = Theme.getColor(R.id.theme_color_text);
      reactedIconColor = ColorUtils.alphaColor(0.6f, reactedIconColor);
      DrawableCompat.setTint(reactedIconDrawable, reactedIconColor);
      reactedIcon.setImageDrawable(reactedIconDrawable);
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
      reactedText.setBoldText(getFormattedCount(reactedCount), null, false);
      reactedText.setTextSize(16f);
      reactedText.setPadding(PADDING, 0, PADDING, 0);
      int reactedTextWidth = TEXT_WIDTH_S;
      if (reactedCount > 99) {
        reactedTextWidth = TEXT_WIDTH_L;
      } else if (reactedCount > 9) {
        reactedTextWidth = TEXT_WIDTH_M;
      }
      reactedText.setLayoutParams(new LinearLayout.LayoutParams(reactedTextWidth, ViewGroup.LayoutParams.WRAP_CONTENT));
      reactedWrapper.addView(reactedText);

      if (msg.canGetAddedReactions) {
        Views.setClickable(reactedWrapper);
        RippleSupport.setTransparentSelector(reactedWrapper);
        reactedWrapper.setOnClickListener(view -> {
          onReactedClick.run();
        });
      }

      wrapperWidth = 2 * ICON_SIZE + viewedTextWidth + reactedTextWidth;
    }

    ReactionListController controller = new ReactionListController(context, tdlib);
    controller.setArguments(this);
    controller.get().setLayoutParams(new LinearLayout.LayoutParams(
        Screen.currentWidth() - wrapperWidth,
        BAR_HEIGHT
    ));

    addView(controller.get());
    addView(reactedWrapper);
  }

  private void initUsers () {
    removeAllViews();

    setGravity(Gravity.CENTER_VERTICAL);

    ReactionCategoryListController controller = new ReactionCategoryListController(context, tdlib);

    ImageView backIcon = new ImageView(context);
    Drawable backIconDrawable = getResources().getDrawable(R.drawable.baseline_arrow_back_24);
    int backIconColor = Theme.getColor(R.id.theme_color_text);
    DrawableCompat.setTint(backIconDrawable, backIconColor);
    backIcon.setImageDrawable(backIconDrawable);
    backIcon.setPadding(PADDING, 0, PADDING, 0);
    backIcon.setLayoutParams(new LinearLayout.LayoutParams(BACK_ICON_SIZE, BACK_ICON_SIZE));

    Views.setClickable(backIcon);
    RippleSupport.setTransparentSelector(backIcon);
    backIcon.setOnClickListener(view -> {
      onBackClick.run();
    });

    addView(backIcon);

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
    reactedText.setBoldText(getFormattedCount(reactedCount), null, false);
    reactedText.setTextSize(16f);
    reactedText.setPadding(PADDING, 0, PADDING, 0);
    int textWidth = TEXT_WIDTH_S;
    if (reactedCount > 99) {
      textWidth = TEXT_WIDTH_L;
    } else if (reactedCount > 9) {
      textWidth = TEXT_WIDTH_M;
    }
    reactedText.setLayoutParams(new LinearLayout.LayoutParams(textWidth, ViewGroup.LayoutParams.WRAP_CONTENT));
    reactedWrapper.addView(reactedText);

    addView(reactedWrapper);

    Views.setClickable(reactedWrapper);
    RippleSupport.setTransparentSelector(reactedWrapper);
    reactedWrapper.setOnClickListener(view -> {
      onReactionClick.accept("");
      controller.removeSelection();
    });

    controller.setArguments(this);
    controller.get().setLayoutParams(new LinearLayout.LayoutParams(
        Screen.currentWidth() - BACK_ICON_SIZE - ICON_SIZE - textWidth,
        BAR_HEIGHT
    ));
    addView(controller.get());
  }

  private String getFormattedCount (int count) {
    String text = String.valueOf(count);
    if (count > 999999) {
      text = count / 1000000 + "M";
    } else if (count > 999) {
      text = count / 1000 + "K";
    }
    return text;
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
