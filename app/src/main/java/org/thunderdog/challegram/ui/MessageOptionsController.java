package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.OptionsLayout;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.CustomTextView;
import org.thunderdog.challegram.widget.EmojiPacksInfoView;
import org.thunderdog.challegram.widget.EmojiTextView;

import me.vkryl.core.StringUtils;

public class MessageOptionsController extends BottomSheetViewController.BottomSheetBaseRecyclerViewController<MessageOptionsController.Args> {
  private Options options;
  private long[] emojiPackIds;
  private long emojiPackFirstEmoji;
  private View.OnClickListener listener;
  private OptionsAdapter adapter;
  private ThemeListenerList themeProvider;
  private Runnable hideWindowDelegate;


  public MessageOptionsController (Context context, Tdlib tdlib, ThemeListenerList themeProvider) {
    super(context, tdlib);
    this.themeProvider = themeProvider;
  }

  @Override
  public void setArguments (MessageOptionsController.Args args) {
    super.setArguments(args);
    this.options = args.options;
    this.listener = args.listener;
    this.emojiPackIds = args.emojiPackIds;
    this.emojiPackFirstEmoji = args.emojiPackFirstEmoji;
    this.hideWindowDelegate = args.hideWindowDelegate;
  }

  @Override
  public boolean needBottomDecorationOffsets (RecyclerView parent) {
    return false;
  }

  @Override
  public int getId () {
    return R.id.controller_messageOptions;
  }

  @Override
  protected int getRecyclerBackground () {
    return ColorId.filling;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new OptionsAdapter(context, this, options, emojiPackFirstEmoji, emojiPackIds, listener, themeProvider);
    LinearLayoutManager manager = new LinearLayoutManager(context);
    addThemeInvalidateListener(recyclerView);
    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    recyclerView.setLayoutManager(manager);
    recyclerView.setAdapter(adapter);
  }

  @Override
  public boolean needsTempUpdates () {
    return true;
  }

  public static class Args {
    public Options options;
    public View.OnClickListener listener;
    public long[] emojiPackIds;
    public long emojiPackFirstEmoji;
    public Runnable hideWindowDelegate;

    public Args (Options options, View.OnClickListener listener, long emojiPackFirstEmoji, long[] emojiPackIds, Runnable hideWindowDelegate) {
      this.options = options;
      this.listener = listener;
      this.emojiPackIds = emojiPackIds;
      this.emojiPackFirstEmoji = emojiPackFirstEmoji;
      this.hideWindowDelegate = hideWindowDelegate;
    }
  }


  private static class OptionHolder extends RecyclerView.ViewHolder {
    public OptionHolder (@NonNull View itemView) {
      super(itemView);
    }

    public static OptionHolder create (Context context, ViewController<?> parent, int viewType, View.OnClickListener onClickListener) {
      if (viewType == OptionsAdapter.TYPE_OPTION) {
        EmojiTextView text = new EmojiTextView(context);
        text.setScrollDisabled(true);
        text.setTypeface(Fonts.getRobotoRegular());
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
        text.setOnClickListener(onClickListener);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setGravity(Lang.rtl() ? Gravity.RIGHT | Gravity.CENTER_VERTICAL : Gravity.LEFT | Gravity.CENTER_VERTICAL);
        text.setPadding(Screen.dp(17f), Screen.dp(1f), Screen.dp(17f), 0);
        text.setCompoundDrawablePadding(Screen.dp(18f));
        text.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(54f)));
        Views.setClickable(text);
        RippleSupport.setTransparentSelector(text);
        return new OptionHolder(text);
      } else if (viewType == OptionsAdapter.TYPE_EMOJI_PACK_INFO) {
        EmojiPacksInfoView textView= new EmojiPacksInfoView(context, parent, parent.tdlib());
        return new OptionHolder(textView);
      } else {
        CustomTextView textView = new CustomTextView(context, parent.tdlib());
        textView.setTextColorId(ColorId.textLight);
        textView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        textView.setPadding(Screen.dp(16f), Screen.dp(14f), Screen.dp(16f), Screen.dp(6f));
        return new OptionHolder(textView);
      }
    }
  }

  private static class OptionsAdapter extends RecyclerView.Adapter<OptionHolder> {
    private final Context context;
    private final View.OnClickListener onClickListener;
    private final Options options;
    private final Tdlib tdlib;
    private final MessageOptionsController parent;
    @Nullable
    private final ThemeListenerList themeProvider;
    private final long[] emojiPackIds;
    private final long emojiPackFirstEmoji;

    private final int textInfoPosition;
    private final int emojiInfoPosition;

    public static final int TYPE_OPTION = 0;
    public static final int TYPE_INFO = 1;
    public static final int TYPE_EMOJI_PACK_INFO = 2;

    OptionsAdapter (Context context, MessageOptionsController parent, Options options, long emojiPackFirstEmoji, long[] emojiPackIds, View.OnClickListener onClickListener, @Nullable ThemeListenerList themeProvider) {
      this.parent = parent;
      this.tdlib = parent.tdlib();
      this.onClickListener = onClickListener;
      this.context = context;
      this.options = options;
      this.themeProvider = themeProvider;
      this.emojiPackIds = emojiPackIds;
      this.emojiPackFirstEmoji = emojiPackFirstEmoji;

      this.emojiInfoPosition = emojiPackIds.length > 0 ? 0 : -1;
      this.textInfoPosition = StringUtils.isEmpty(options.info) ? -1 : (emojiInfoPosition + 1);
    }

    @NonNull
    @Override
    public OptionHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      return OptionHolder.create(context, this.parent, viewType, onClickListener);
    }

    @Override
    public void onBindViewHolder (@NonNull OptionHolder holder, int position) {
      int type = getItemViewType(position);

      if (type == TYPE_OPTION) {
        if (!StringUtils.isEmpty(options.info)) {
          position -= 1;
        }
        if (emojiInfoPosition > -1) {
          position -= 1;
        }

        OptionItem item = options.items[position];
        TextView textView = ((TextView) holder.itemView);
        textView.setId(item.id);
        final int colorId = OptionsLayout.getOptionColorId(item.color);
        textView.setTextColor(Theme.getColor(colorId));
        if (themeProvider != null)
          themeProvider.addThemeColorListener(textView, colorId);
        if (item.icon != 0) {
          Drawable drawable = Drawables.get(context.getResources(), item.icon);
          if (drawable != null) {
            final int drawableColorId = item.color == OptionColor.NORMAL ? ColorId.icon : colorId;
            drawable.setColorFilter(Paints.getColorFilter(Theme.getColor(drawableColorId)));
            if (themeProvider != null) {
              themeProvider.addThemeFilterListener(drawable, drawableColorId);
            }
            if (Lang.rtl()) {
              textView.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null);
            } else {
              textView.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
            }
          }
        } else {
          textView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
        }
        textView.setText(item.name);
      }

      if (type == TYPE_INFO) {
        CustomTextView textView = ((CustomTextView) holder.itemView);
        String str = options.info.toString();
        TextEntity[] parsed = TD.collectAllEntities(parent, tdlib, options.info, false, null);
        textView.setTextSize(15f);
        textView.setTextColorId(ColorId.textLight);
        textView.setText(str, parsed, false);
      }

      if (type == TYPE_EMOJI_PACK_INFO) {
        EmojiPacksInfoView textView = ((EmojiPacksInfoView) holder.itemView);
        textView.setId(R.id.btn_emojiPackInfoButton);
        textView.setTextSize(15f);
        textView.setTextColorId(ColorId.textLight);
        textView.update(emojiPackFirstEmoji, emojiPackIds, new ClickableSpan() {
          @Override
          public void onClick (@NonNull View widget) {
            parent.listener.onClick(textView);
          }
        }, false);
      }
    }

    @Override
    public int getItemViewType (int position) {
      if (position == textInfoPosition) {
        return TYPE_INFO;
      }
      if (position == emojiInfoPosition) {
        return TYPE_EMOJI_PACK_INFO;
      }

      return TYPE_OPTION;
    }

    @Override
    public int getItemCount () {
      return options.items.length + (textInfoPosition > -1 ? 1 : 0) + (emojiInfoPosition > -1 ? 1 : 0);
    }
  }

  @Override
  public int getItemsHeight (RecyclerView recyclerView) {
    int totalHeight = (options.items.length + 2) * Screen.dp(54);
    if (!StringUtils.isEmpty(options.info)) {
      View view = recyclerView.getLayoutManager().findViewByPosition(0);
      int hintHeight =
        view instanceof CustomTextView && ((CustomTextView) view).checkMeasuredWidth(recyclerView.getMeasuredWidth()) ?
          view.getMeasuredHeight() : 0;
      if (hintHeight > 0) {
        totalHeight += hintHeight;
      } else {
        int availWidth = recyclerView.getMeasuredWidth() - Screen.dp(16f) * 2;
        if (availWidth > 0) {
          totalHeight += CustomTextView.measureHeight(this, options.info, 15f, availWidth) + Screen.dp(14f) + Screen.dp(6f);
        } else {
          totalHeight += Screen.dp(14f) + Screen.dp(6f) + Screen.dp(15f);
        }
      }
    }
    if (emojiPackIds.length > 0) {
      totalHeight += Screen.dp(40);
    }
    return totalHeight;
  }
}
