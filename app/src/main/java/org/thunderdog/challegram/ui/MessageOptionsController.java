package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
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
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.navigation.OptionsLayout;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
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
import org.thunderdog.challegram.widget.NoScrollTextView;

import me.vkryl.core.StringUtils;

public class MessageOptionsController extends MessageOptionsPagerController.MessageBottomSheetBaseController<MessageOptionsController.Args> {
  private Options options;
  private View.OnClickListener listener;
  private OptionsAdapter adapter;
  private ThemeListenerList themeProvider;


  public MessageOptionsController (Context context, Tdlib tdlib, ThemeListenerList themeProvider) {
    super(context, tdlib);
    this.themeProvider = themeProvider;
  }

  @Override
  public void setArguments (MessageOptionsController.Args args) {
    super.setArguments(args);
    this.options = args.options;
    this.listener = args.listener;
  }

  @Override
  public int getId () {
    return R.id.controller_messageOptions;
  }

  @Override
  protected int getRecyclerBackground () {
    return R.id.theme_color_filling;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new OptionsAdapter(context, this, options, listener, themeProvider);
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

    public Args (Options options, View.OnClickListener listener) {
      this.options = options;
      this.listener = listener;
    }
  }


  private static class OptionHolder extends RecyclerView.ViewHolder {
    public OptionHolder (@NonNull View itemView) {
      super(itemView);
    }

    public static OptionHolder create (Context context, Tdlib tdlib, int viewType, View.OnClickListener onClickListener) {
      if (viewType == OptionsAdapter.TYPE_OPTION) {
        TextView text = new NoScrollTextView(context);
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
      } else {
        CustomTextView textView = new CustomTextView(context, tdlib);
        textView.setTextColorId(R.id.theme_color_textLight);
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
    private final ViewController<?> parent;
    @Nullable
    private final ThemeListenerList themeProvider;

    public static final int TYPE_OPTION = 0;
    public static final int TYPE_INFO = 1;

    OptionsAdapter (Context context, ViewController<?> parent, Options options, View.OnClickListener onClickListener, @Nullable ThemeListenerList themeProvider) {
      this.parent = parent;
      this.tdlib = parent.tdlib();
      this.onClickListener = onClickListener;
      this.context = context;
      this.options = options;
      this.themeProvider = themeProvider;
    }

    @NonNull
    @Override
    public OptionHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      return OptionHolder.create(context, tdlib, viewType, onClickListener);
    }

    @Override
    public void onBindViewHolder (@NonNull OptionHolder holder, int position) {
      int type = getItemViewType(position);

      if (type == TYPE_OPTION) {
        if (!StringUtils.isEmpty(options.info)) {
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
            final int drawableColorId = item.color == ViewController.OPTION_COLOR_NORMAL ? R.id.theme_color_icon : colorId;
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
        textView.setText(Emoji.instance().replaceEmoji(item.name));
      }

      if (type == TYPE_INFO) {
        CustomTextView textView = ((CustomTextView) holder.itemView);
        String str = options.info.toString();
        TextEntity[] parsed = TD.collectAllEntities(parent, tdlib, options.info, false, null);
        textView.setText(str, parsed, false);
        textView.setTextSize(15f);
        textView.setTextColorId(R.id.theme_color_textLight);
      }
    }

    @Override
    public int getItemViewType (int position) {
      if (position == 0 && !StringUtils.isEmpty(options.info)) {
        return TYPE_INFO;
      }

      return TYPE_OPTION;
    }

    @Override
    public int getItemCount () {
      return options.items.length + (StringUtils.isEmpty(options.info) ? 0 : 1);
    }
  }

  @Override
  public int getItemsHeight () {
    return adapter.getItemCount() * Screen.dp(54);
  }
}
