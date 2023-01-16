package org.thunderdog.challegram.ui;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.ShadowView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.widget.FrameLayoutFix;

public class ChatFolderIconSelector {

  private final Context context;
  private final Delegate delegate;
  private final View popupView;
  private final GridLayoutManager layoutManager;

  private PopupLayout popupLayout;

  public ChatFolderIconSelector (ViewController<?> owner, Delegate delegate) {
    this.context = owner.context();
    this.delegate = delegate;

    List<ListItem> items = new ArrayList<>(TD.ICON_NAMES.length);
    for (String iconName : TD.ICON_NAMES) {
      items.add(new ListItem(ListItem.TYPE_CUSTOM_SINGLE, 0, TD.iconByName(iconName, 0), 0).setStringValue(iconName));
    }
    SettingsAdapter popupAdapter = new SettingsAdapter(owner, null, owner) {
      @Override
      protected SettingHolder initCustom (ViewGroup parent) {
        ImageView imageView = new ImageView(parent.getContext()) {
          @Override
          protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
            int size = MeasureSpec.getSize(widthMeasureSpec);
            setMeasuredDimension(size, size);
          }
        };
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setColorFilter(Theme.getColor(R.id.theme_color_icon));
        owner.addThemeFilterListener(imageView, R.id.theme_color_icon);
        Views.setClickable(imageView);
        imageView.setOnClickListener(v -> {
          ListItem item = (ListItem) imageView.getTag();
          String iconName = item.getStringValue();
          delegate.onIconClick(iconName);
          hide(/* animated */ true);
        });
        RippleSupport.setTransparentSelector(imageView);
        return new SettingHolder(imageView);
      }

      @Override
      protected void setCustom (ListItem item, SettingHolder holder, int position) {
        ImageView imageView = (ImageView) holder.itemView;
        int iconResource = item.getIconResource();
        if (iconResource != 0) {
          imageView.setImageDrawable(Drawables.get(imageView.getResources(), iconResource));
        } else {
          imageView.setImageDrawable(null);
        }
      }
    };

    layoutManager = new GridLayoutManager(context, computeSpanCount(Screen.currentWidth()));
    popupAdapter.setItems(items, false);
    RecyclerView recyclerView = new RecyclerView(context);
    recyclerView.setLayoutManager(layoutManager);
    recyclerView.setAdapter(popupAdapter);
    ViewSupport.setThemedBackground(recyclerView, R.id.theme_color_background);

    ShadowView shadowView = new ShadowView(context);
    shadowView.setSimpleTopShadow(true);
    owner.addThemeInvalidateListener(shadowView);

    FrameLayoutFix popupView = new FrameLayoutFix(context);
    popupView.addView(shadowView, FrameLayoutFix.newParams(MATCH_PARENT, Screen.dp(7f), Gravity.TOP));
    popupView.addView(recyclerView, FrameLayoutFix.newParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP, 0, Screen.dp(7f), 0, 0));
    popupView.setLayoutParams(FrameLayoutFix.newParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM));
    this.popupView = popupView;
  }

  public void show () {
    int itemCount = layoutManager.getItemCount();
    int spanCount = layoutManager.getSpanCount();
    int rowCount = (itemCount + spanCount - 1) / spanCount;
    int popupHeight = rowCount * (Screen.currentWidth() / spanCount) + Screen.dp(7f);

    popupLayout = new PopupLayout(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        int popupWidth = getDefaultSize(Screen.currentWidth(), widthMeasureSpec);
        layoutManager.setSpanCount(computeSpanCount(popupWidth));
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
      }
    };
    popupLayout.init(true);
    popupLayout.setShowListener(popup -> delegate.onShow());
    popupLayout.setDismissListener(popup -> delegate.onDismiss());
    popupLayout.setHideKeyboard();
    popupLayout.setNeedRootInsets();
    popupLayout.showSimplePopupView(popupView, popupHeight);
  }

  public void hide (boolean animated) {
    if (popupLayout != null) {
      popupLayout.hideWindow(animated);
      popupLayout = null;
    }
  }

  private int computeSpanCount (int width) {
    int itemSize = Screen.dp(56f);
    return Math.max(width / itemSize, 3);
  }

  public interface Delegate {
    void onIconClick (String iconName);

    default void onShow () {}

    default void onDismiss () {}
  }

  public static ChatFolderIconSelector show (ViewController<?> owner, Delegate delegate) {
    ChatFolderIconSelector selector = new ChatFolderIconSelector(owner, delegate);
    selector.show();
    return selector;
  }
}
