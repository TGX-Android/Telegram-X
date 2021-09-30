package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.collection.SparseArrayCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.charts.BaseChartView;
import org.thunderdog.challegram.charts.Chart;
import org.thunderdog.challegram.charts.MiniChart;
import org.thunderdog.challegram.charts.view_data.ChartHeaderView;
import org.thunderdog.challegram.component.attach.MeasuredAdapterDelegate;
import org.thunderdog.challegram.component.attach.MediaLocationPlaceView;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.base.TogglerView;
import org.thunderdog.challegram.component.chat.MessagePreviewView;
import org.thunderdog.challegram.component.inline.CustomResultView;
import org.thunderdog.challegram.component.sharedmedia.MediaSmallView;
import org.thunderdog.challegram.component.sticker.StickerSmallView;
import org.thunderdog.challegram.component.user.UserView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.DoubleTextWrapper;
import org.thunderdog.challegram.data.InlineResult;
import org.thunderdog.challegram.data.PageBlock;
import org.thunderdog.challegram.data.PageBlockFile;
import org.thunderdog.challegram.mediaview.data.MediaItem;
import org.thunderdog.challegram.mediaview.paint.ColorPaletteView;
import org.thunderdog.challegram.mediaview.paint.widget.ColorToneView;
import org.thunderdog.challegram.navigation.DrawerItemView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibDelegate;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.FloatListener;
import org.thunderdog.challegram.util.HeightChangeListener;
import org.thunderdog.challegram.util.SelectableItemDelegate;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.AvatarView;
import org.thunderdog.challegram.widget.BetterChatView;
import org.thunderdog.challegram.widget.ChartLayout;
import org.thunderdog.challegram.widget.CheckBox;
import org.thunderdog.challegram.widget.CustomTextView;
import org.thunderdog.challegram.widget.DoubleTextView;
import org.thunderdog.challegram.widget.EmbeddableStickerView;
import org.thunderdog.challegram.widget.EmptySmartView;
import org.thunderdog.challegram.widget.FileProgressComponent;
import org.thunderdog.challegram.widget.JoinedUsersView;
import org.thunderdog.challegram.widget.ListInfoView;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;
import org.thunderdog.challegram.widget.NonMaterialButton;
import org.thunderdog.challegram.widget.PageBlockView;
import org.thunderdog.challegram.widget.PageBlockWrapView;
import org.thunderdog.challegram.widget.ProgressComponentView;
import org.thunderdog.challegram.widget.RadioView;
import org.thunderdog.challegram.widget.ScalableTextView;
import org.thunderdog.challegram.widget.SeparatorView;
import org.thunderdog.challegram.widget.SettingStupidView;
import org.thunderdog.challegram.widget.ShadowView;
import org.thunderdog.challegram.widget.SliderWrapView;
import org.thunderdog.challegram.widget.SmallChatView;
import org.thunderdog.challegram.widget.TimerView;
import org.thunderdog.challegram.widget.VerticalChatView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ClickHelper;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Filter;

/**
 * Date: 16/11/2016
 * Author: default
 */
public class SettingsAdapter extends RecyclerView.Adapter<SettingHolder> implements MeasuredAdapterDelegate, SliderWrapView.Callback, MaterialEditTextGroup.FocusListener, FactorAnimator.Target, TGLegacyManager.EmojiLoadListener, Lang.Listener, MaterialEditTextGroup.TextChangeListener, FloatListener, ColorToneView.ChangeListener, NonMaterialButton.PressureListener, ChartLayout.Delegate {
  private final Context context;
  private final Tdlib tdlib;
  // private final RecyclerView parentView;

  protected final List<RecyclerView> parentViews = new ArrayList<>();
  private final View.OnClickListener onClickListener;
  private final List<ListItem> items;

  private @Nullable View.OnLongClickListener onLongClickListener;

  private @Nullable ViewController<?> lockFocusOn;
  private boolean showKeyboardAlways = true;
  private @Nullable TextChangeListener textChangeListener;
  private @Nullable FileProgressComponent.SimpleListener simpleListener;
  private @Nullable ViewController<?> themeProvider;
  private @Nullable RecyclerView.OnScrollListener innerOnScrollListener;
  private @Nullable ClickHelper.Delegate clickHelperDelegate;
  private boolean noEmptyProgress;

  private @Nullable HeightChangeListener heightChangeListener;

  private @Nullable SliderWrapView.RealTimeChangeListener sliderChangeListener;

  public interface TextChangeListener {
    void onTextChanged (int id, ListItem item, MaterialEditTextGroup v, String text);
  }

  public SettingsAdapter (ViewController<?> context) {
    this(context, context instanceof View.OnClickListener ? (View.OnClickListener) context : null, context);
    if (context instanceof View.OnLongClickListener) {
      setOnLongClickListener((View.OnLongClickListener) context);
    }
  }

  public SettingsAdapter (TdlibDelegate context, View.OnClickListener onClickListener, @Nullable ViewController<?> themeProvider) {
    this.context = context.context();
    this.tdlib = context.tdlib();
    this.onClickListener = onClickListener;
    this.items = new ArrayList<>(5);
    this.themeProvider = themeProvider;
  }

  public void addBoundShadow (RecyclerView recyclerView, final View shadowView, final int height) {
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled (RecyclerView recyclerView, int dx, int dy) {
        LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
        int i = manager.findFirstVisibleItemPosition();
        if (i != RecyclerView.NO_POSITION) {
          float factor;
          if (i == 0) {
            View view = manager.findViewByPosition(0);
            int top = view != null ? -view.getTop() : 0;
            factor = MathUtils.clamp((float) top / (float) height);
          } else {
            factor = 1f;
          }
          shadowView.setAlpha(factor);
        }
      }
    });
  }

  public void setHeightChangeListener (@Nullable HeightChangeListener heightChangeListener) {
    this.heightChangeListener = heightChangeListener;
  }

  @Nullable
  public HeightChangeListener getHeightChangeListener () {
    return heightChangeListener;
  }

  public void setSliderChangeListener (@Nullable SliderWrapView.RealTimeChangeListener sliderChangeListener) {
    this.sliderChangeListener = sliderChangeListener;
  }

  public @Nullable SliderWrapView.RealTimeChangeListener getSliderChangeListener () {
    return sliderChangeListener;
  }

  public void setNoEmptyProgress () {
    this.noEmptyProgress = true;
  }

  @Override
  public void onAttachedToRecyclerView (RecyclerView recyclerView) {
    parentViews.add(recyclerView);
  }

  @Override
  public void onDetachedFromRecyclerView (RecyclerView recyclerView) {
    parentViews.remove(recyclerView);
  }

  public void setInnerOnScrollListener (@Nullable RecyclerView.OnScrollListener innerOnScrollListener) {
    this.innerOnScrollListener = innerOnScrollListener;
  }

  public void setClickHelperDelegate (@Nullable ClickHelper.Delegate clickHelperDelegate) {
    this.clickHelperDelegate = clickHelperDelegate;
  }

  @Override
  public void onEmojiPartLoaded () {
    for (RecyclerView parentView : parentViews) {
      LinearLayoutManager manager = (LinearLayoutManager) parentView.getLayoutManager();
      final int first = manager.findFirstVisibleItemPosition();
      final int last = manager.findLastVisibleItemPosition();
      for (int i = first; i <= last; i++) {
        View view = manager.findViewByPosition(i);
        if (view != null) {
          view.invalidate();
        }
      }
      if (first > 0) {
        notifyItemRangeChanged(0, first);
      }
      if (last < getItemCount() - 1) {
        notifyItemRangeChanged(last, getItemCount() - last);
      }
    }
  }

  public final void invalidateItemDecorations () {
    for (RecyclerView parentView : parentViews) {
      parentView.invalidateItemDecorations();
    }
  }

  public void setOnLongClickListener (@Nullable View.OnLongClickListener onLongClickListener) {
    this.onLongClickListener = onLongClickListener;
  }

  public void setSimpleListener (@Nullable FileProgressComponent.SimpleListener listener) {
    this.simpleListener = listener;
  }

  public void setLockFocusOn (@Nullable ViewController<?> c, boolean showAlways) {
    this.lockFocusOn = c;
    this.showKeyboardAlways = showAlways;
  }

  void setLockFocusView (View view) {
    if (lockFocusOn != null && lockFocusOn.getLockFocusView() == null) {
      lockFocusOn.setLockFocusView(view, showKeyboardAlways);
    }
  }

  private BaseChartView.SharedUiComponents sharedUiComponents;

  @Override
  public final BaseChartView.SharedUiComponents provideSharedComponents () {
    return sharedUiComponents != null ? sharedUiComponents : (sharedUiComponents = new BaseChartView.SharedUiComponents());
  }

  public void setTextChangeListener (@Nullable TextChangeListener textChangeListener) {
    this.textChangeListener = textChangeListener;
  }

  @Override
  public void onTextChanged (MaterialEditTextGroup v, CharSequence charSequence) {
    String text = charSequence.toString();
    int id = ((ViewGroup) v.getParent()).getId();
    //
    ListItem item = v.getParent() != null && ((ViewGroup) v.getParent()).getTag() instanceof ListItem ? (ListItem) ((ViewGroup) v.getParent()).getTag() : null;
    if (item == null) {
      int i = indexOfViewById(id);
      if (i != -1) {
        item = items.get(i);
      }
    }
    boolean changed = true;
    if (item != null) {
      if (!StringUtils.equalsOrBothEmpty(item.getStringValue(), text)) {
        item.setStringValue(text);
      } else {
        changed = false;
      }
    }
    if (changed && textChangeListener != null) {
      textChangeListener.onTextChanged(id, item, v, text);
    }
  }

  @Override
  public void onPressStateChanged (NonMaterialButton btn, boolean isPressed) {
    // Override
  }

  @Override
  public void onValueChange (View view, float value, boolean isFinished) {
    // Override
  }

  @Override
  public void onValuesChanged (ColorToneView view, float saturation, float value, boolean isFinished) {
    // Override
  }

  protected void setShadowVisibility (ListItem item, ShadowView view) {
    // Override
  }

  protected void setEmailPattern (ListItem item, TextView textView) {
    // Override
  }

  protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
    // Override
  }

  protected void modifyDescription (ListItem item, TextView textView) {
    // Override
  }

  protected void setText (ListItem item, CustomTextView view, boolean isUpdate) {
    view.setBoldText(item.getString(), null, false);
  }

  protected void setHeaderText (ListItem item, TextView view, boolean isUpdate) {
    Views.setMediumText(view, item.getString());
  }

  protected void setPlace (ListItem item, int position, MediaLocationPlaceView view, boolean isUpdate) {
    // Override
  }

  protected void setDrawerItem (ListItem item, DrawerItemView view, TimerView timerView, boolean isUpdate) {
    // Override
  }

  protected void setSliderValues (ListItem item, SliderWrapView view) {
    view.setValues(item.getString(), item.getSliderValues(), item.getSliderValue());
  }

  protected void setBuildNo (ListItem item, TextView textView, boolean isUpdate) {
    textView.setText(item.getString());
  }

  protected void setButtonText (ListItem item, ScalableTextView view, boolean isUpdate) {
    CharSequence text = item.getString() != null ? item.getString().toString().toUpperCase() : null;
    if (isUpdate) {
      view.replaceText(text);
    } else {
      Views.setMediumText(view, text);
    }
  }

  protected void setStupidValuedSetting (ListItem item, SettingStupidView view, boolean isUpdate) {
    // Override
  }

  protected void setSession (ListItem item, int position, RelativeLayout parent, boolean isUpdate, TextView timeView, TextView titleView, TextView subtextView, TextView locationView, ProgressComponentView progressView, @Nullable AvatarView avatarView) {
    // Override
  }

  private void setSession (ListItem item, int position, RelativeLayout parent, boolean isUpdate) {
    TextView timeView = (TextView) parent.getChildAt(0);
    TextView titleView = (TextView) parent.getChildAt(1);
    TextView subtextView = (TextView) parent.getChildAt(2);
    TextView locationView = (TextView) parent.getChildAt(3);
    ProgressComponentView progressView = (ProgressComponentView) parent.getChildAt(4);
    AvatarView avatarView = item.getViewType() == ListItem.TYPE_SESSION_WITH_AVATAR ? (AvatarView) parent.getChildAt(5) : null;
    setSession(item, position, parent, isUpdate, timeView, titleView, subtextView, locationView, progressView, avatarView);
  }

  protected void setStickerSet (ListItem item, int position, DoubleTextView group, boolean isArchived, boolean isUpdate) {
    // Override
  }

  protected void setUser (ListItem item, int position, UserView userView, boolean isUpdate) {
    // Override
  }

  protected void setEmbedSticker (ListItem item, int position, EmbeddableStickerView userView, boolean isUpdate) {
    // Override
  }

  protected void setCustom (ListItem item, SettingHolder holder, int position) {
    // Override
  }

  protected void setMembersList (ListItem item, int position, RecyclerView recyclerView) {
    // Override
  }

  protected void setMessagePreview (ListItem item, int position, MessagePreviewView previewView) {
    // Override
  }

  /*protected void setPagerTopView (SettingItem item, int position, ViewPagerTopView topView, boolean visibilityOnly) {
    // Override
  }*/

  protected void setInfo (ListItem item, int position, ListInfoView infoView) {
    // Override
  }

  public final void setColor (ListItem item, int position, ViewGroup contentView, @Nullable View updatedView) {
    ColorToneView toneView = (ColorToneView) contentView.getChildAt(0);
    ColorPaletteView colorView = (ColorPaletteView) contentView.getChildAt(1);
    ColorPaletteView transparencyView = (ColorPaletteView) contentView.getChildAt(2);

    ViewGroup viewGroup;

    MaterialEditTextGroup hexView = (MaterialEditTextGroup) contentView.getChildAt(3);
    viewGroup = (ViewGroup) contentView.getChildAt(4);
    MaterialEditTextGroup redView = (MaterialEditTextGroup) viewGroup.getChildAt(0);
    MaterialEditTextGroup greenView = (MaterialEditTextGroup) viewGroup.getChildAt(1);
    MaterialEditTextGroup blueView = (MaterialEditTextGroup) viewGroup.getChildAt(2);
    MaterialEditTextGroup alphaView = (MaterialEditTextGroup) viewGroup.getChildAt(3);

    MaterialEditTextGroup defaultView = (MaterialEditTextGroup) contentView.getChildAt(5);
    viewGroup = (ViewGroup) contentView.getChildAt(6);
    MaterialEditTextGroup hueView = (MaterialEditTextGroup) viewGroup.getChildAt(0);
    MaterialEditTextGroup saturationView = (MaterialEditTextGroup) viewGroup.getChildAt(1);
    MaterialEditTextGroup lightnessView = (MaterialEditTextGroup) viewGroup.getChildAt(2);
    MaterialEditTextGroup alphaPercentageView = (MaterialEditTextGroup) viewGroup.getChildAt(3);

    viewGroup = (ViewGroup) contentView.getChildAt(7);
    NonMaterialButton undoButton = (NonMaterialButton) viewGroup.getChildAt(0);
    NonMaterialButton redoButton = (NonMaterialButton) viewGroup.getChildAt(1);

    viewGroup = (ViewGroup) contentView.getChildAt(8);
    NonMaterialButton copyButton = (NonMaterialButton) viewGroup.getChildAt(0);
    NonMaterialButton pasteButton = (NonMaterialButton) viewGroup.getChildAt(1);
    NonMaterialButton opacityButton = (NonMaterialButton) viewGroup.getChildAt(2);
    NonMaterialButton clearButton = (NonMaterialButton) viewGroup.getChildAt(3);
    NonMaterialButton saveButton = (NonMaterialButton) viewGroup.getChildAt(4);

    setColor(item, position, contentView, updatedView, toneView, colorView, transparencyView, hexView, redView, greenView, blueView, alphaView, defaultView, hueView, saturationView, lightnessView, alphaPercentageView, clearButton, undoButton, redoButton, copyButton, pasteButton, opacityButton, saveButton);
  }

  protected void setColor (ListItem item, int position, ViewGroup contentView, @Nullable View updatedView, ColorToneView toneView, ColorPaletteView paletteView, ColorPaletteView transparencyView, MaterialEditTextGroup hexView, MaterialEditTextGroup redView, MaterialEditTextGroup greenView, MaterialEditTextGroup blueView, MaterialEditTextGroup alphaView, MaterialEditTextGroup defaultView, MaterialEditTextGroup hueView, MaterialEditTextGroup saturationView, MaterialEditTextGroup lightnessView, MaterialEditTextGroup alphaPercentageView, NonMaterialButton clearButton, NonMaterialButton undoButton, NonMaterialButton redoButton, NonMaterialButton copyButton, NonMaterialButton pasteButton, NonMaterialButton opacityButton, NonMaterialButton saveButton) {
    // Override
  }

  protected void setSeparatorOptions (ListItem item, int position, SeparatorView separatorView) {
    separatorView.setOffsets(Screen.dp(72f), 0);
  }

  protected void setChatData (ListItem item, VerticalChatView chatView) {
    // Override
  }

  protected void setChatData (ListItem item, int position, BetterChatView chatView) {
    // Override
  }

  protected void setDoubleText (ListItem item, int position, DoubleTextView textView, boolean isUpdate) {
    // Override
  }

  protected void modifyEditText (ListItem item, ViewGroup parent, MaterialEditTextGroup editText) {
    // Override
  }

  protected void onEditTextRadioClick (ListItem item, ViewGroup parent, MaterialEditTextGroup editText, RadioView radioView) {
    // Override
  }

  protected void modifyHeaderTextView (TextView textView, int viewHeight, int paddingTop) {
    // Override
  }

  protected void modifyChatView (ListItem item, SmallChatView chatView, @Nullable CheckBox checkBox, boolean isUpdate) {
    // Override
  }

  protected SettingHolder initCustom (ViewGroup parent) {
    throw new RuntimeException("Stub!");
  }

  protected SettingHolder initCustom (ViewGroup parent, int customViewType) {
    throw new RuntimeException("Stub!");
  }

  protected void modifyCustom (SettingHolder holder, int position, ListItem item, int customViewType, View view, boolean isUpdate) {

  }

  @Override
  public void onFocusChanged (MaterialEditTextGroup v, boolean isFocused) {
    if (lockFocusOn != null && isFocused) {
      lockFocusOn.setLockFocusView(v.getEditText(), showKeyboardAlways);
    }
  }

  public void removeRange (int index, int itemCount) {
    for (int i = index + itemCount - 1; i >= index; i--) {
      items.remove(i);
    }
    notifyItemRangeRemoved(index, itemCount);
  }

  public void replaceItems (final List<ListItem> items) {
    int oldItemCount = getItemCount();
    this.items.clear();
    if (items != null) {
      this.items.addAll(items);
    }
    U.replaceItems(this, oldItemCount);
  }

  public int setItems (final List<ListItem> items, boolean hasCheckedItems) {
    int oldItemCount = getItemCount();
    this.items.clear();
    ArrayUtils.ensureCapacity(this.items, items.size());
    this.items.addAll(items);
    int checkedIndex = -1;
    boolean moreThanOneChecked = false;
    if (hasCheckedItems) {
      int i = 0;
      for (ListItem item : items) {
        if (item.getViewType() == ListItem.TYPE_SLIDER) {
          putCheckedInt(item.getId(), item.getSliderValue());
        } else if (item.isSelected()) {
          if (item.getStringCheckResult() != null) {
            putCheckedString(item.getCheckId(), item.getStringCheckResult());
          } else {
            putCheckedInt(item.getCheckId(), item.getId());
          }
          if (!moreThanOneChecked) {
            if (checkedIndex == -1) {
              checkedIndex = i;
            } else {
              checkedIndex = -1;
              moreThanOneChecked = true;
            }
          }
        }
        i++;
      }
    }
    U.notifyItemsReplaced(this, oldItemCount);
    return checkedIndex;
  }

  public void setItems (final ListItem[] items, boolean hasCheckedItems) {
    int oldItemCount = getItemCount();
    this.items.clear();
    ArrayUtils.ensureCapacity(this.items, items.length);
    Collections.addAll(this.items, items);
    if (hasCheckedItems) {
      for (ListItem item : items) {
        if (item.isSelected()) {
          if (item.getStringCheckResult() != null) {
            putCheckedString(item.getCheckId(), item.getStringCheckResult());
          } else {
            putCheckedInt(item.getCheckId(), item.getId());
          }
        }
      }
    }
    U.notifyItemsReplaced(this, oldItemCount);
  }

  public void updateAllValuedSettings () {
    if (!items.isEmpty()) {
      int i = 0;
      for (ListItem item : items) {
        if (SettingHolder.isValuedType(item.getViewType())) {
          updateValuedSettingByPosition(i);
        }
        i++;
      }
    }
  }

  public void updateEditTextById (int id, boolean isGood, boolean isError) {
    int index = indexOfViewById(id);
    if (index != -1) {
      for (RecyclerView parentView : parentViews) {
        View view = parentView.getLayoutManager().findViewByPosition(index);
        if (view != null) {
          MaterialEditTextGroup editText = (MaterialEditTextGroup) ((ViewGroup) view).getChildAt(0);
          editText.setInGoodState(isGood);
          editText.setInErrorState(isError);
        } else {
          // TODO notifyItemChanged?
        }
      }
    }
  }

  public void updateLockEditTextById (int id, @Nullable String text) {
    int index = indexOfViewById(id);
    if (index != -1) {
      for (RecyclerView parentView : parentViews) {
        View view = parentView.getLayoutManager().findViewByPosition(index);
        if (view != null) {
          MaterialEditTextGroup editText = (MaterialEditTextGroup) ((ViewGroup) view).getChildAt(0);
          editText.setBlockedText(text);
        } else {
          // TODO notifyItemChanged?
        }
      }
    }
  }

  public void updateSimpleItemById (int id) {
    int index = indexOfViewById(id);
    if (index != -1) {
      updateSimpleItemByPosition(index);
    }
  }

  public void updateSimpleItemByPosition (int position) {
    if (position != -1) {
      boolean needNotify = false;
      for (RecyclerView parentView : parentViews) {
        View view = parentView.getLayoutManager().findViewByPosition(position);
        if (view != null) {
          RecyclerView.ViewHolder holder = parentView.getChildViewHolder(view);
          if (holder != null && holder instanceof SettingHolder) {
            onBindViewHolder((SettingHolder) holder, position);
          } else {
            needNotify = true;
          }
        } else {
          needNotify = true;
        }
      }
      if (needNotify) {
        notifyItemChanged(position);
      }
    }
  }

  public int updateUserViewByLongId (long id, boolean isStatusUpdate) {
    int index = indexOfViewByLongId(id);
    if (index != -1) {
      updateUserViewByPosition(index, isStatusUpdate);
    }
    return index;
  }

  public void updateUserViewByPosition (int position, boolean isStatusUpdate) {
    if (position != -1) {
      boolean needNotify = false;
      for (RecyclerView parentView : parentViews) {
        View view = parentView.getLayoutManager().findViewByPosition(position);
        if (view != null && view instanceof UserView) {
          if (isStatusUpdate) {
            ((UserView) view).updateSubtext();
          } else {
            ((UserView) view).updateAll();
          }
          view.invalidate();
        } else {
          needNotify = true;
        }
      }
      if (needNotify) {
        notifyItemChanged(position);
      }
    }
  }

  public void updateValuedSettingByData (Object data) {
    int position = indexOfViewByData(data);
    if (position != -1) {
      updateValuedSettingByPosition(position);
    }
  }

  public void modifySettingView (int viewType, SettingView settingView) {
    // override if needed
  }

  public void updateValuedSetting (ListItem item) {
    int position = indexOfView(item);
    if (position != -1) {
      updateValuedSettingByPosition(position);
    }
  }

  public void updateValuedSettingByPosition (int position) {
    ListItem item = getItem(position);
    if (item != null) {
      boolean needNotify = false;
      for (RecyclerView parentView : parentViews) {
        View view = parentView.getLayoutManager().findViewByPosition(position);
        if (view != null && view.getId() == item.getId()) {
          if (view instanceof SettingView) {
            setValuedSetting(item, (SettingView) view, true);
          } else {
            boolean ok = false;
            switch (item.getViewType()) {
              case ListItem.TYPE_BUTTON: {
                if (ok = view instanceof ViewGroup && ((ViewGroup) view).getChildAt(0) instanceof ScalableTextView) {
                  setButtonText(item, (ScalableTextView) ((ViewGroup) view).getChildAt(0), true);
                }
                break;
              }
              case ListItem.TYPE_BUILD_NO: {
                if (ok = view instanceof TextView) {
                  setBuildNo(item, (TextView) view, true);
                }
                break;
              }
              case ListItem.TYPE_DRAWER_ITEM: {
                if (ok = view instanceof DrawerItemView && !((DrawerItemView) view).hasAvatar()) {
                  setDrawerItem(item, (DrawerItemView) view, null, true);
                }
                break;
              }
              case ListItem.TYPE_DRAWER_ITEM_WITH_AVATAR: {
                if (ok = view instanceof DrawerItemView && ((DrawerItemView) view).hasAvatar()) {
                  setDrawerItem(item, (DrawerItemView) view, null, true);
                }
                break;
              }
              case ListItem.TYPE_ATTACH_LOCATION:
              case ListItem.TYPE_ATTACH_LOCATION_BIG: {
                if (ok = view instanceof MediaLocationPlaceView) {
                  setPlace(item, position, (MediaLocationPlaceView) view, true);
                }
                break;
              }
            }
            if (!ok) {
              SettingHolder holder = (SettingHolder) parentView.getChildViewHolder(view);
              int actualPosition = holder != null ? holder.getAdapterPosition() : -1;
              if (actualPosition != RecyclerView.NO_POSITION) {
                onBindViewHolder(holder, actualPosition);
              } else {
                notifyItemChanged(position);
              }
            }
          }
        } else {
          needNotify = true;
        }
      }
      if (needNotify) {
        notifyItemChanged(position);
      }
    }
  }

  public void updateValuedSettingByLongId (long id) {
    int position = indexOfViewByLongId(id);
    if (position != -1) {
      updateValuedSettingByPosition(position);
    }
  }

  public void updateValuedSettingById (int id) {
    int index = indexOfViewById(id);
    if (index != -1) {
      updateValuedSettingByPosition(index);
    }
  }

  public void updateAllValuedSettingsById (int id) {
    int index = -1;
    while ((index = indexOfViewById(id, index + 1)) != -1) {
      updateValuedSettingByPosition(index);
    }
  }

  /*public void updateShadowsVisibility () {
    int i = 0;
    x: for (SettingItem item : items) {
      switch (item.getId()) {
        case R.id.shadowTop: {
          updateShadowVisibilityByPosition(i);
          break;
        }
        case R.id.shadowBottom: {
          updateShadowVisibilityByPosition(i);
          break x;
        }
      }
      i++;
    }
  }*/

  /*public void updateTopViewVisibility (int position) {
    if (position != -1) {
      View view = parentView.getLayoutManager().findViewByPosition(position);
      if (view != null && view instanceof ViewPagerTopView) {
        setPagerTopView(items.get(position), position, (ViewPagerTopView) view, true);
      } else {
        notifyItemChanged(position);
      }
    }
  }*/

  /*public void updateShadowVisibilityById (int id) {
    int index = indexOfViewById(id);
    if (index != -1) {
      updateShadowVisibilityByPosition(index);
    }
  }

  public void updateShadowVisibilityByPosition (int position) {
    if (position != -1) {
      boolean needNotify = false;
      for (RecyclerView parentView : parentViews) {
        View view = parentView.getLayoutManager().findViewByPosition(position);
        if (view != null && view instanceof ShadowView) {
          setShadowVisibility(items.get(position), (ShadowView) view);
        } else {
          needNotify = true;
        }
      }
      if (needNotify) {
        notifyItemChanged(position);
      }
    }
  }*/

  /*public int indexOfSmallChatByUserId (int userId) {
    int i = 0;
    for (SettingItem item : items) {
      switch (item.getViewType()) {
        case SettingItem.TYPE_CHAT_SMALL:
        case SettingItem.TYPE_CHAT_SMALL_SELECTABLE:
          if (((DoubleTextWrapper) item.getData()).getUserId() == userId) {
            return i;
          }
          break;
      }
      i++;
    }
    return -1;
  }

  public void updateSmallChatByUserId (int userId) {
    int i = indexOfSmallChatByUserId(userId);
    if (i != -1) {
      updateSmallChatByPosition(i);
    }
  }

  public void updateSmallChatByPosition (int position) {
    if (position != -1) {
      boolean needNotify = false;
      for (RecyclerView parentView : parentViews) {
        View view = parentView.getLayoutManager().findViewByPosition(position);
        if (view != null) {
          SettingItem item = items.get(position);
          switch (item.getViewType()) {
            case SettingItem.TYPE_CHAT_SMALL_SELECTABLE:
              if (view instanceof FrameLayoutFix) {
                View child1 = ((ViewGroup) view).getChildAt(0);
                View child2 = ((ViewGroup) view).getChildAt(1);
                if (child1 instanceof SmallChatView && child2 instanceof CheckBox) {
                  modifyChatView(item, (SmallChatView) child1, (CheckBox) child2, true);
                  break;
                }
              }
              break;
            case SettingItem.TYPE_CHAT_SMALL:
              if (view instanceof SmallChatView) {
                modifyChatView(item, ((SmallChatView) view), null, true);
                break;
              }
            default:
              view = null;
          }
        }
        if (view == null) {
          needNotify = true;
        }
      }
      if (needNotify) {
        notifyItemChanged(position);
      }
    }
  }

  public void updateValuedSettingByPosition (int position, int id, boolean checkId) {
    if (position != -1) {
      boolean needNotify = false;
      SettingItem item = items.get(position);
      for (RecyclerView parentView : parentViews) {
        View view = parentView.getLayoutManager().findViewByPosition(position);
        boolean ok = false;
        if (view != null && (!checkId || view.getId() == id)) {
          // FIXME replace with updateValuedSettingByPosition(position);
          if (view instanceof SettingView) {
            setValuedSetting(items.get(position), (SettingView) view, true);
            ok = true;
          } else {
            switch (item.getViewType()) {

            }
          }
        }
        if (!ok) {
          needNotify = true;
        }
      }
      if (needNotify) {
        notifyItemChanged(position);
      }
    }
  }*/

  public void updateCheckOptionByStringValue (String string, boolean isChecked) {
    int index = indexOfViewByStringCheckResult(string);
    if (index != -1) {
      setCheckInternal(index, isChecked);
    }
  }

  public void updateCheckOptionById (int id, boolean isChecked) {
    int index = indexOfViewById(id);
    if (index != -1) {
      setCheckInternal(index, isChecked);
    }
  }

  private void setCheckInternal (int index, boolean isChecked) {
    boolean needNotify = false;
    items.get(index).setSelected(isChecked);
    for (RecyclerView parentView : parentViews) {
      View view = parentView.getLayoutManager().findViewByPosition(index);
      if (view == null) {
        needNotify = true;
        continue;
      }

      if (view instanceof SettingView && ((SettingView) view).getChildCount() > 0 && view.getId() == items.get(index).getId()) {
        View child = ((SettingView) view).getChildAt(0);
        if (child instanceof CheckBox) {
          ((CheckBox) child).setChecked(isChecked, true);
          continue;
        }
        if (child instanceof RadioView) {
          ((RadioView) child).setChecked(isChecked, true);
          continue;
        }
      }

      if (view instanceof FrameLayoutFix &&
        ((FrameLayoutFix) view).getChildCount() == 2 &&
        view.getId() == items.get(index).getId()) {
        switch (items.get(index).getViewType()) {
          case ListItem.TYPE_DRAWER_ITEM_WITH_RADIO:
          case ListItem.TYPE_DRAWER_ITEM_WITH_RADIO_SEPARATED: {
            View child = ((FrameLayoutFix) view).getChildAt(1);
            if (child instanceof TogglerView) {
              ((TogglerView) child).setRadioEnabled(isChecked, true);
              ((TogglerView) child).checkRtl(true);
              continue;
            }
            break;
          }
        }
      }

      if (view instanceof DrawerItemView && items.get(index).getViewType() == ListItem.TYPE_DRAWER_ITEM_WITH_AVATAR) {
        ((DrawerItemView) view).setChecked(isChecked, true);
      }

      needNotify = true;
    }
    if (needNotify) {
      notifyItemChanged(index);
    }
  }

  public void updateAllSessions () {
    int i = 0;
    for (ListItem item : items) {
      switch (item.getViewType()) {
        case ListItem.TYPE_SESSION_WITH_AVATAR:
        case ListItem.TYPE_SESSION:
          updateSessionByPosition(i);
          break;
      }
      i++;
    }
  }

  public void updateSessionByLongId (long id) {
    int position = indexOfViewByLongId(id);
    if (position != -1) {
      updateSessionByPosition(position);
    }
  }

  public void updateSessionByPosition (int position) {
    if (position == -1) {
      return;
    }
    boolean needNotify = false;
    for (RecyclerView parentView : parentViews) {
      View view = parentView.getLayoutManager().findViewByPosition(position);
      if (view != null && view.getTag() == items.get(position) && view instanceof RelativeLayout) {
        setSession(items.get(position), position, (RelativeLayout) view, true);
      } else {
        needNotify = true;
      }
    }
    if (needNotify) {
      notifyItemChanged(position);
    }
  }

  public void updateStickerSetById (long stickerSetId) {
    int i = indexOfViewByLongId(stickerSetId);
    if (i != -1) {
      updateStickerSetByPosition(i);
    }
  }

  public void updateStickerSetByPosition (int position) {
    boolean needNotify = false;
    for (RecyclerView parentView : parentViews) {
      View view = parentView.getLayoutManager().findViewByPosition(position);
      if (view != null && view instanceof DoubleTextView) {
        setStickerSet(items.get(position), position, (DoubleTextView) view, items.get(position).getViewType() == ListItem.TYPE_ARCHIVED_STICKER_SET, true);
      } else {
        needNotify = true;
      }
    }
    if (needNotify) {
      notifyItemChanged(position);
    }
  }

  public boolean toggleView (View v, ListItem item) {
    if (v == null) {
      return false;
    }
    if (item == null && v.getTag() instanceof ListItem) {
      item = (ListItem) v.getTag();
    }
    if (item == null) {
      return false;
    }
    if (v instanceof SettingView && ((SettingView) v).getToggler() != null) {
      return ((SettingView) v).toggleRadio();
    }
    switch (item.getViewType()) {
      case ListItem.TYPE_RADIO_OPTION:
      case ListItem.TYPE_RADIO_OPTION_LEFT:
      case ListItem.TYPE_RADIO_OPTION_WITH_AVATAR: {
        if (!(v instanceof SettingView)) {
          return false;
        }
        RadioView radioView = (RadioView) ((SettingView) v).getChildAt(0);
        if (!radioView.isChecked()) {
          radioView.toggleChecked();
        }
        return true;
      }
      case ListItem.TYPE_CHECKBOX_OPTION:
      case ListItem.TYPE_CHECKBOX_OPTION_REVERSE:
      case ListItem.TYPE_CHECKBOX_OPTION_DOUBLE_LINE:
      case ListItem.TYPE_CHECKBOX_OPTION_WITH_AVATAR:
      case ListItem.TYPE_CHECKBOX_OPTION_MULTILINE: {
        return v instanceof SettingView && ((CheckBox) ((SettingView) v).getChildAt(0)).toggle();
      }
      case ListItem.TYPE_DRAWER_ITEM_WITH_RADIO:
      case ListItem.TYPE_DRAWER_ITEM_WITH_RADIO_SEPARATED: {
        return v instanceof SettingView && ((TogglerView) ((FrameLayoutFix) v).getChildAt(0)).toggle(true);
      }
      case ListItem.TYPE_DRAWER_ITEM_WITH_AVATAR: {
        return v instanceof DrawerItemView && ((DrawerItemView) v).hasAvatar() && ((DrawerItemView) v).toggle(true);
      }
    }
    return false;
  }

  public boolean toggleView (View v) {
    return toggleView(v, v.getTag() instanceof ListItem ? (ListItem) v.getTag() : findItemById(v.getId()));
  }

  // selectable stuff

  private static final int SELECTABLE_ANIMATOR = 0;

  private boolean inSelectMode;
  private float selectableFactor;
  private int animateFromIndex = -1, animateToIndex = -1;
  private FactorAnimator selectableAnimator;
  private boolean animateSelectable;
  private FactorAnimator.Target additionalTarget;

  public void setInSelectMode (boolean inSelectMode, boolean animated, FactorAnimator.Target additionalTarget) {
    if (this.inSelectMode != inSelectMode) {
      this.inSelectMode = inSelectMode;
      this.animateSelectable = animated;
      this.additionalTarget = additionalTarget;
      animateSelectableFactor(inSelectMode ? 1f : 0f);
    }
  }

  public boolean isInSelectMode () {
    return inSelectMode;
  }

  public void setIsSelected (int index, boolean isSelected, int selectionIndex) {
    boolean needNotify = false;
    for (RecyclerView parentView : parentViews) {
      View view = parentView.getLayoutManager().findViewByPosition(index);
      if (view != null && view instanceof SelectableItemDelegate) {
        ((SelectableItemDelegate) view).setIsItemSelected(isSelected, selectionIndex);
      } else {
        needNotify = true;
      }
    }
    if (needNotify) {
      notifyItemChanged(index);
    }
  }

  public void clearSelectedItems () {
    int i = 0;
    for (ListItem item : items) {
      if (item.isSelected()) {
        item.setSelected(false);
        setIsSelected(i, false, item.getSelectionIndex());
      }
      i++;
    }
  }

  private void animateSelectableFactor (float toFactor) {
    if (selectableAnimator != null) {
      selectableAnimator.cancel();
    }
    if (animateSelectable) {
      for (RecyclerView parentView : parentViews) {
        LinearLayoutManager manager = (LinearLayoutManager) parentView.getLayoutManager();
        animateFromIndex = manager.findFirstVisibleItemPosition();
        animateToIndex = manager.findLastVisibleItemPosition();
        if (animateFromIndex != RecyclerView.NO_POSITION && animateToIndex != RecyclerView.NO_POSITION) {
          if (animateToIndex > 0) {
            notifyItemRangeChanged(0, animateFromIndex);
          }
          if (animateToIndex + 1 < getItemCount() - 1) {
            notifyItemRangeChanged(animateToIndex + 1, getItemCount() - animateToIndex - 1);
          }
          if (selectableAnimator == null) {
            selectableAnimator = new FactorAnimator(SELECTABLE_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, selectableFactor);
          }
          selectableAnimator.animateTo(toFactor);
        } else {
          if (selectableAnimator != null) {
            selectableAnimator.forceFactor(toFactor);
          }
          setSelectableFactor(toFactor);
        }
      }
    } else {
      if (selectableAnimator == null) {
        selectableAnimator = new FactorAnimator(SELECTABLE_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, selectableFactor);
      }
      selectableAnimator.animateTo(toFactor);
    }
  }

  private void setSelectableFactor (float factor) {
    if (this.selectableFactor != factor) {
      this.selectableFactor = factor;
      for (RecyclerView parentView : parentViews) {
        for (int i = animateFromIndex; i <= animateToIndex; i++) {
          View view = parentView.getLayoutManager().findViewByPosition(i);
          if (view != null) {
            if (view instanceof MediaSmallView) {
              ((MediaSmallView) view).setSelectableFactor(factor);
            }
          }
        }
      }
    }
  }

  // Drawing

  public static class BackgroundDecoration extends RecyclerView.ItemDecoration {
    private @ThemeColorId int colorId;

    public BackgroundDecoration (int colorId) {
      this.colorId = colorId;
    }

    protected boolean needsBackground (ListItem item) {
      switch (item.getViewType()) {
        case ListItem.TYPE_SHADOW_TOP:
        case ListItem.TYPE_SHADOW_BOTTOM:
        case ListItem.TYPE_HEADER:
        case ListItem.TYPE_HEADER_PADDED:
        case ListItem.TYPE_HEADER_WITH_ACTION: {
          return true;
        }
      }
      return false;
    }

    @Override
    public void onDraw (Canvas c, RecyclerView parent, RecyclerView.State state) {
      int color = Theme.getColor(colorId);

      Paint paint = Paints.fillingPaint(color);
      final int childCount = parent.getChildCount();
      for (int i = 0; i < childCount; i++) {
        View view = parent.getChildAt(i);
        if (view != null) {
          Object tag = view.getTag();
          if (tag != null && tag instanceof ListItem) {
            if (needsBackground((ListItem) tag)) {
              c.drawRect(view.getLeft(), view.getTop(), view.getRight(), view.getBottom(), paint);
            }
          }
        }
      }
    }
  }

  // Animator

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case SELECTABLE_ANIMATOR: {
        setSelectableFactor(factor);
        if (additionalTarget != null) {
          additionalTarget.onFactorChanged(id, factor, fraction, callee);
        }
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  // adapter stuff

  @Override
  public SettingHolder onCreateViewHolder (ViewGroup parent, int viewType) {
    switch (viewType) {
      case ListItem.TYPE_CUSTOM_SINGLE: {
        return initCustom(parent);
      }
      default: {
        if (viewType <= ListItem.TYPE_CUSTOM) {
          return initCustom(parent, ListItem.TYPE_CUSTOM - viewType);
        }
      }
    }
    return SettingHolder.create(context, tdlib, viewType, this, onClickListener, onLongClickListener, themeProvider, innerOnScrollListener, clickHelperDelegate);
  }

  @Override
  public int getItemViewType (int position) {
    return items.isEmpty() ? ListItem.TYPE_PROGRESS : items.get(position).getViewType();
  }

  @Override
  public void onViewAttachedToWindow (SettingHolder holder) {
    holder.attach();
  }

  @Override
  public void onViewDetachedFromWindow (SettingHolder holder) {
    holder.detach();
  }

  @Override
  public void onViewRecycled (SettingHolder holder) {
    holder.destroy();
  }

  public void updateView (SettingHolder holder, int position, int viewType) {
    if (position >= items.size()) {
      return;
    }
    final ListItem item = items.get(position);
    holder.itemView.setId(item.getId());
    holder.itemView.setTag(item);
    switch (viewType) {
      case ListItem.TYPE_SHADOWED_OFFSET: {
        if (holder.itemView.getMeasuredHeight() != Screen.currentHeight() / 2) {
          holder.itemView.requestLayout();
        }
        break;
      }
      case ListItem.TYPE_SHADOW_BOTTOM:
      case ListItem.TYPE_SHADOW_TOP: {
        setShadowVisibility(item, (ShadowView) holder.itemView);
        int colorId = item.getTextColorId(ThemeColorId.NONE);
        if (colorId != 0) {
          holder.itemView.setBackgroundColor(Theme.getColor(colorId));
        }
        break;
      }
      case ListItem.TYPE_PADDING: {
        ((SettingHolder.PaddingView) holder.itemView).setItem(item);
        break;
      }
      case ListItem.TYPE_LIST_INFO_VIEW: {
        setInfo(item, position, (ListInfoView) holder.itemView);
        break;
      }
      case ListItem.TYPE_CHAT_BETTER: {
        setChatData(item, position, (BetterChatView) holder.itemView);
        break;
      }
      case ListItem.TYPE_BUILD_NO: {
        setBuildNo(item, (TextView) holder.itemView, false);
        break;
      }
      case ListItem.TYPE_SMALL_MEDIA: {
        ((MediaSmallView) holder.itemView).setListener(simpleListener);
        ((MediaSmallView) holder.itemView).setItem((MediaItem) item.getData());
        ((MediaSmallView) holder.itemView).setSelectionFactor(inSelectMode ? 1f : 0f, item.isSelected() ? 1f : 0f);
        break;
      }
      case ListItem.TYPE_CUSTOM_INLINE: {
        Object data = item.getData();
        ((CustomResultView) holder.itemView).setInlineResult(data instanceof InlineResult ? (InlineResult<?>) data : data instanceof PageBlockFile ? ((PageBlockFile) data).getFile() : null);
        ((CustomResultView) holder.itemView).forceSelected(item.isSelected(), item.getSelectionIndex());
        break;
      }
      case ListItem.TYPE_PAGE_BLOCK:
      case ListItem.TYPE_PAGE_BLOCK_GIF:
      case ListItem.TYPE_PAGE_BLOCK_MEDIA:
      case ListItem.TYPE_PAGE_BLOCK_COLLAGE:
      case ListItem.TYPE_PAGE_BLOCK_AVATAR: {
        ((PageBlockView) holder.itemView).setBlock((PageBlock) item.getData());
        break;
      }
      case ListItem.TYPE_CHAT_SMALL: {
        ((SmallChatView) holder.itemView).setChat((DoubleTextWrapper) item.getData());
        modifyChatView(item, (SmallChatView) holder.itemView, null, false);
        break;
      }
      case ListItem.TYPE_CHAT_SMALL_SELECTABLE: {
        FrameLayoutFix wrapView = (FrameLayoutFix) holder.itemView;
        ((SmallChatView) wrapView.getChildAt(0)).setChat((DoubleTextWrapper) item.getData());
        modifyChatView(item, ((SmallChatView) wrapView.getChildAt(0)), ((CheckBox) wrapView.getChildAt(1)), false);
        break;
      }
      case ListItem.TYPE_PAGE_BLOCK_EMBEDDED:
      case ListItem.TYPE_PAGE_BLOCK_VIDEO:
      case ListItem.TYPE_PAGE_BLOCK_SLIDESHOW:
      case ListItem.TYPE_PAGE_BLOCK_TABLE: {
        ((PageBlockWrapView) holder.itemView).setBlock((PageBlock) item.getData());
        break;
      }
      case ListItem.TYPE_MEMBERS_LIST: {
        setMembersList(item, position, (RecyclerView) holder.itemView);
        break;
      }
      case ListItem.TYPE_MESSAGE_PREVIEW:
      case ListItem.TYPE_STATS_MESSAGE_PREVIEW: {
        setMessagePreview(item, position, (MessagePreviewView) holder.itemView);
        break;
      }
      case ListItem.TYPE_FAKE_PAGER_TOPVIEW: {
        // setPagerTopView(item, position, (ViewPagerTopView) holder.itemView, false);
        break;
      }
      case ListItem.TYPE_EDITTEXT:
      case ListItem.TYPE_EDITTEXT_REUSABLE:
      case ListItem.TYPE_EDITTEXT_POLL_OPTION:
      case ListItem.TYPE_EDITTEXT_NO_PADDING:
      case ListItem.TYPE_EDITTEXT_NO_PADDING_REUSABLE:
      case ListItem.TYPE_EDITTEXT_COUNTERED:
      case ListItem.TYPE_EDITTEXT_CHANNEL_DESCRIPTION:
      case ListItem.TYPE_EDITTEXT_WITH_PHOTO:
      case ListItem.TYPE_EDITTEXT_WITH_PHOTO_SMALLER: {
        MaterialEditTextGroup editText = (MaterialEditTextGroup) ((ViewGroup) holder.itemView).getChildAt(0);
        editText.applyRtl(Lang.rtl());
        editText.setHint(item.getString());
        editText.setText(item.getStringValue());
        EditBaseController.SimpleEditorActionListener editorActionListener = item.getOnEditorActionListener();
        if (editorActionListener != null) {
          editText.getEditText().setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | editorActionListener.getImeAction());
          editText.getEditText().setOnEditorActionListener(editorActionListener.hasContext() ? editorActionListener : null);
        } else {
          editText.getEditText().setOnEditorActionListener(null);
          editText.getEditText().setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        }
        if (item.getInputFilters() != null) {
          editText.getEditText().setFilters(item.getInputFilters());
        } else {
          // FIXME maybe editText.getEditText().setFilters(new InputFilter[0]);
        }
        modifyEditText(item, (ViewGroup) holder.itemView, editText);
        break;
      }
      case ListItem.TYPE_CUSTOM_SINGLE: {
        setCustom(item, holder, position);
        break;
      }
      case ListItem.TYPE_WEBSITES_EMPTY:
      case ListItem.TYPE_ICONIZED_EMPTY: {
        ViewGroup group = (ViewGroup) ((ViewGroup) holder.itemView).getChildAt(0);
        if (viewType == ListItem.TYPE_ICONIZED_EMPTY) {
          ((ImageView) group.getChildAt(0)).setImageResource(item.getIconResource());
        }
        ((TextView) group.getChildAt(1)).setText(item.getString());
        break;
      }
      case ListItem.TYPE_CHATS_PLACEHOLDER: {
        ViewGroup group = (ViewGroup) ((ViewGroup) holder.itemView).getChildAt(0);
        ((JoinedUsersView) group.getChildAt(0)).setJoinedText(item.getString());
        break;
      }
      case ListItem.TYPE_USER: {
        setUser(item, position, (UserView) holder.itemView, false);
        break;
      }
      case ListItem.TYPE_EMBED_STICKER: {
        setEmbedSticker(item, position, (EmbeddableStickerView) holder.itemView, false);
        break;
      }
      case ListItem.TYPE_INFO: {
        ((CustomTextView) holder.itemView).setText(item.getString(), null, false);
        break;
      }
      case ListItem.TYPE_SLIDER_BRIGHTNESS: {
        float currentValue = Float.intBitsToFloat(item.getSliderValue());
        float maxValue = Float.intBitsToFloat(item.getIntValue());
        ((SliderWrapView) holder.itemView).setValue(currentValue, maxValue);
        break;
      }
      case ListItem.TYPE_SEPARATOR: {
        setSeparatorOptions(item, position, (SeparatorView) holder.itemView);
        break;
      }
      case ListItem.TYPE_SLIDER: {
        setSliderValues(item, (SliderWrapView) holder.itemView);
        break;
      }
      case ListItem.TYPE_DRAWER_ITEM: {
        // SettingItem item = settingItems.get(position - 1);
        DrawerItemView view = (DrawerItemView) holder.itemView;
        view.setIcon(Screen.dp(18f), Screen.dp(13.5f), item.getIconResource());
        view.setText(item.getString().toString());
        setDrawerItem(item, view, null, false);
        break;
      }
      case ListItem.TYPE_DRAWER_ITEM_WITH_AVATAR: {
        DrawerItemView view = (DrawerItemView) holder.itemView;
        setDrawerItem(item, view, null, false);
        break;
      }
      case ListItem.TYPE_ATTACH_LOCATION:
      case ListItem.TYPE_ATTACH_LOCATION_BIG: {
        MediaLocationPlaceView view = (MediaLocationPlaceView) holder.itemView;
        setPlace(item, position, view, false);
        break;
      }
      case ListItem.TYPE_LIVE_LOCATION_TARGET: {
        FrameLayoutFix frameLayoutFix = (FrameLayoutFix) holder.itemView;
        DrawerItemView view = (DrawerItemView) frameLayoutFix.getChildAt(0);
        TimerView timerView = (TimerView) frameLayoutFix.getChildAt(1);
        setDrawerItem(item, view, timerView, false);
        view.setTag(item);
        break;
      }
      case ListItem.TYPE_DRAWER_ITEM_WITH_RADIO:
      case ListItem.TYPE_DRAWER_ITEM_WITH_RADIO_SEPARATED: {
        FrameLayoutFix frameLayoutFix = (FrameLayoutFix) holder.itemView;
        DrawerItemView view = (DrawerItemView) frameLayoutFix.getChildAt(0);
        view.setIcon(Screen.dp(18f), Screen.dp(13.5f), item.getIconResource());
        view.setText(item.getString().toString());
        TogglerView button = (TogglerView) frameLayoutFix.getChildAt(1);
        button.setRadioEnabled(item.isSelected(), false);
        button.setId(item.getId());
        button.checkRtl(true);
        break;
      }
      case ListItem.TYPE_COLOR_PICKER: {
        setColor(item, position, (ViewGroup) holder.itemView, null);
        break;
      }
      case ListItem.TYPE_SETTING:
      case ListItem.TYPE_CHECKBOX_OPTION:
      case ListItem.TYPE_CHECKBOX_OPTION_MULTILINE:
      case ListItem.TYPE_CHECKBOX_OPTION_REVERSE:
      case ListItem.TYPE_RADIO_OPTION:
      case ListItem.TYPE_RADIO_OPTION_LEFT:
      case ListItem.TYPE_RADIO_OPTION_WITH_AVATAR:
      case ListItem.TYPE_CHECKBOX_OPTION_DOUBLE_LINE:
      case ListItem.TYPE_CHECKBOX_OPTION_WITH_AVATAR: {
        ((SettingView) holder.itemView).setIcon(item.getIconResource());
        ((SettingView) holder.itemView).setName(item.getString());
        ((SettingView) holder.itemView).setIgnoreEnabled(false);
        ((SettingView) holder.itemView).setTextColorId(item.getTextColorId(R.id.theme_color_text));
        holder.itemView.setEnabled(true);
        setValuedSetting(item, (SettingView) holder.itemView, false);
        switch (viewType) {
          case ListItem.TYPE_CHECKBOX_OPTION_WITH_AVATAR:
          case ListItem.TYPE_RADIO_OPTION_WITH_AVATAR: {
            AvatarView avatarView = ((AvatarView) ((SettingView) holder.itemView).getChildAt(1));
            if (item.getData() instanceof TdlibAccount) {
              TdlibAccount account = (TdlibAccount) item.getData();
              avatarView.setUser(account);
            } else {
              Tdlib tdlib = this.tdlib;
              if (item.getData() instanceof Tdlib) {
                tdlib = (Tdlib) item.getData();
              }
              avatarView.setUser(tdlib, item.getLongValue(), false);
            }
            break;
          }
        }
        switch (viewType) {
          case ListItem.TYPE_CHECKBOX_OPTION:
          case ListItem.TYPE_CHECKBOX_OPTION_MULTILINE:
          case ListItem.TYPE_CHECKBOX_OPTION_REVERSE:
          case ListItem.TYPE_CHECKBOX_OPTION_DOUBLE_LINE:
          case ListItem.TYPE_CHECKBOX_OPTION_WITH_AVATAR: {
            CheckBox checkBox = ((CheckBox) ((SettingView) holder.itemView).getChildAt(0));
            checkBox.setChecked(item.isSelected(), false);
            Views.setGravity(checkBox, ((viewType == ListItem.TYPE_CHECKBOX_OPTION_REVERSE) != Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL);
            break;
          }
          case ListItem.TYPE_RADIO_OPTION:
          case ListItem.TYPE_RADIO_OPTION_LEFT:
          case ListItem.TYPE_RADIO_OPTION_WITH_AVATAR: {
            RadioView radioView = ((SettingView) holder.itemView).findRadioView();
            radioView.setChecked(item.isSelected(), false);
            radioView.setColorId(item.getRadioColorId());
            Views.setGravity(radioView, (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL);
            break;
          }
        }
        break;
      }
      case ListItem.TYPE_SMART_EMPTY: {
        ((EmptySmartView) holder.itemView).setMode(item.getIntValue(), item.getBoolValue(), item.getStringValue());
        break;
      }
      case ListItem.TYPE_EMPTY: {
        ((TextView) holder.itemView).setText(item.getString());
        ((TextView) holder.itemView).setTextColor(Theme.getColor(item.getTextColorId(R.id.theme_color_background_textLight)));
        break;
      }
      case ListItem.TYPE_2FA_EMAIL: {
        setEmailPattern(item, (TextView) ((RelativeLayout) ((FrameLayoutFix) holder.itemView).getChildAt(0)).getChildAt(2));
        break;
      }
      case ListItem.TYPE_BUTTON: {
        setButtonText(item, (ScalableTextView) ((ViewGroup) holder.itemView).getChildAt(0), false);
        break;
      }
      case ListItem.TYPE_SESSION:
      case ListItem.TYPE_SESSION_WITH_AVATAR: {
        RelativeLayout relativeLayout = (RelativeLayout) holder.itemView;
        setSession(item, position, relativeLayout, false);
        // TODO update RTL?
        break;
      }
      case ListItem.TYPE_STICKER_SET:
      case ListItem.TYPE_ARCHIVED_STICKER_SET: {
        DoubleTextView viewGroup = (DoubleTextView) holder.itemView;
        viewGroup.checkRtl();
        setStickerSet(item, position, viewGroup, viewType == ListItem.TYPE_ARCHIVED_STICKER_SET, false);
        break;
      }
      case ListItem.TYPE_DOUBLE_TEXTVIEW:
      case ListItem.TYPE_DOUBLE_TEXTVIEW_ROUNDED: {
        DoubleTextView viewGroup = (DoubleTextView) holder.itemView;
        setDoubleText(item, position, viewGroup, false);
        break;
      }
      case ListItem.TYPE_VALUED_SETTING:
      case ListItem.TYPE_VALUED_SETTING_WITH_RADIO:
      case ListItem.TYPE_VALUED_SETTING_COMPACT:
      case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_COLOR:
      case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_RADIO:
      case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER:
      case ListItem.TYPE_INFO_MULTILINE:
      case ListItem.TYPE_VALUED_SETTING_RED:
      case ListItem.TYPE_INFO_SETTING: {
        SettingView settingView = (SettingView) holder.itemView;
        settingView.setIcon(item.getIconResource());
        settingView.setName(item.getString());
        holder.itemView.setEnabled(true);
        switch (viewType) {
          case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_COLOR: {
            View view = ((ViewGroup) (holder.itemView)).getChildAt(0);
            Views.setGravity(view, Gravity.CENTER_VERTICAL | (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT));
            break;
          }
          case ListItem.TYPE_VALUED_SETTING_RED: {
            settingView.setTextColorId(item.getTextColorId(R.id.theme_color_textNegative));
            break;
          }
          case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_RADIO: {
            View view = ((ViewGroup) (holder.itemView)).getChildAt(0);
            Views.setGravity(view, Gravity.CENTER_VERTICAL | (Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT));
            break;
          }
          case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER: {
            ((SettingView) holder.itemView).checkRtl(true);
            break;
          }
        }
        setValuedSetting(item, settingView, false);
        break;
      }
      case ListItem.TYPE_CHART_HEADER: {
        ((ChartHeaderView) holder.itemView).setChart((Chart) item.getData());
        break;
      }
      case ListItem.TYPE_CHART_HEADER_DETACHED: {
        ((ChartHeaderView) holder.itemView).setChart((MiniChart) item.getData());
        break;
      }
      case ListItem.TYPE_CHART_LINEAR:
      case ListItem.TYPE_CHART_DOUBLE_LINEAR:
      case ListItem.TYPE_CHART_STACK_BAR:
      case ListItem.TYPE_CHART_STACK_PIE: {
        ((ChartLayout) holder.itemView).setChart((Chart) item.getData());
        break;
      }
      case ListItem.TYPE_VALUED_SETTING_RED_STUPID: {
        holder.itemView.setEnabled(true);
        SettingStupidView stupidView = (SettingStupidView) holder.itemView;
        stupidView.setTitle(item.getString());
        stupidView.checkRtl();
        setStupidValuedSetting(item, stupidView, false);
        break;
      }
      case ListItem.TYPE_TEXT_VIEW: {
        CustomTextView textView = (CustomTextView) holder.itemView;
        setText(item, textView, false);
        break;
      }
      case ListItem.TYPE_HEADER:
      case ListItem.TYPE_HEADER_MULTILINE:
      case ListItem.TYPE_HEADER_PADDED: {
        TextView textView = (TextView) holder.itemView;
        setHeaderText(item, textView, false);
        textView.setTextColor(Theme.getColor(item.getTextColorId(R.id.theme_color_background_textLight)));
        textView.setGravity(Lang.gravity(Gravity.CENTER_VERTICAL));
        break;
      }
      case ListItem.TYPE_HEADER_WITH_ACTION: {
        TextView textView = ((TextView) ((FrameLayoutFix) holder.itemView).getChildAt(0));
        textView.setGravity(Lang.gravity(Gravity.CENTER_VERTICAL));
        setHeaderText(item, textView, false);
        textView.setTextColor(Theme.getColor(item.getTextColorId(R.id.theme_color_background_textLight)));
        ImageView imageView = (ImageView) ((FrameLayoutFix) holder.itemView).getChildAt(1);
        imageView.setId(item.getId());
        imageView.setImageResource(item.getIconResource());
        imageView.setTag(item);
        Views.setIsRtl(imageView, Lang.rtl());
        Views.setGravity((FrameLayout.LayoutParams) imageView.getLayoutParams(), Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT);
        break;
      }
      case ListItem.TYPE_COUNTRY: {
        ViewGroup group = (ViewGroup) holder.itemView;
        ((TextView) group.getChildAt(0)).setText(item.getString());
        ((TextView) group.getChildAt(1)).setText((String) item.getData());
        break;
      }
      case ListItem.TYPE_DESCRIPTION:
      case ListItem.TYPE_DESCRIPTION_SMALL:
      case ListItem.TYPE_DESCRIPTION_CENTERED: {
        TextView textView = (TextView) holder.itemView;
        textView.setTextColor(Theme.getColor(item.getTextColorId(viewType == ListItem.TYPE_DESCRIPTION_CENTERED ? R.id.theme_color_textLight : R.id.theme_color_background_textLight)));
        int padding = Screen.dp(16f) + item.getTextPaddingLeft();
        textView.setText(item.getString());
        if (holder.itemView.getPaddingLeft() != padding) {
          holder.itemView.setPadding(padding, holder.itemView.getPaddingTop(), holder.itemView.getPaddingRight(), holder.itemView.getPaddingBottom());
        }
        if (viewType != ListItem.TYPE_DESCRIPTION_CENTERED) {
          textView.setGravity(Lang.gravity(Gravity.CENTER_VERTICAL));
        }
        modifyDescription(item, textView);
        break;
      }
      case ListItem.TYPE_RADIO_SETTING:
      case ListItem.TYPE_RADIO_SETTING_WITH_NEGATIVE_STATE: {
        SettingView settingView = (SettingView) holder.itemView;
        settingView.setName(item.getString());
        settingView.getToggler().checkRtl(true);
        holder.itemView.setEnabled(true);
        setValuedSetting(item, (SettingView) holder.itemView, false);
        break;
      }
      case ListItem.TYPE_RECYCLER_HORIZONTAL: {
        initRecycler(item, (CustomRecyclerView) holder.itemView);
        break;
      }
      case ListItem.TYPE_CHAT_VERTICAL:
      case ListItem.TYPE_CHAT_VERTICAL_FULLWIDTH: {
        setChatData(item, (VerticalChatView) holder.itemView);
        break;
      }
      default: {
        if (viewType <= ListItem.TYPE_CUSTOM) {
          modifyCustom(holder, position, item, ListItem.TYPE_CUSTOM - viewType, holder.itemView, false);
        }
        break;
      }
    }
  }

  @Override
  public void onBindViewHolder (SettingHolder holder, int position) {
    updateView(holder, position, holder.getItemViewType());
  }

  public void resetRecyclerScrollById (int id) {
    int position = indexOfViewById(id);
    if (position != -1) {
      resetRecyclerScrollByPosition(position);
    }
  }

  public void resetRecyclerScrollByPosition (int position) {
    if (position != -1) {
      for (RecyclerView parentView : parentViews) {
        View view = parentView.getLayoutManager().findViewByPosition(position);
        if (view != null && view instanceof RecyclerView) {
          ((LinearLayoutManager) ((RecyclerView) view).getLayoutManager()).scrollToPositionWithOffset(0, 0);
        }
      }
    }
  }

  private void initRecycler (ListItem item, CustomRecyclerView recyclerView) {
    final boolean isInitialization = recyclerView.getAdapter() == null;
    if (isInitialization) {
      recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled (RecyclerView recyclerView, int dx, int dy) {
          int i = ((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
          int offsetInPixels = 0;
          if (i != -1) {
            View view = recyclerView.getLayoutManager().findViewByPosition(i);
            if (view != null) {
              offsetInPixels = view.getLeft();
            }
          }
          ListItem item = (ListItem) recyclerView.getTag();
          if (item.getBoolValue()) {
            item.setRecyclerPosition(i, offsetInPixels);
          }
        }
      });
    }
    int firstVisiblePosition = item.getFirstVisiblePosition();
    int offsetInPixels = item.getOffsetInPixels();
    if (firstVisiblePosition != -1) {
      ((LinearLayoutManager) recyclerView.getLayoutManager()).scrollToPositionWithOffset(firstVisiblePosition, offsetInPixels);
    } else {
      ((LinearLayoutManager) recyclerView.getLayoutManager()).scrollToPositionWithOffset(0, 0);
    }
    setRecyclerViewData(item, recyclerView, isInitialization);
    LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
    if (manager.getReverseLayout() != Lang.rtl()) {
      manager.setReverseLayout(Lang.rtl());
    }
  }

  protected void setRecyclerViewData(ListItem item, RecyclerView recyclerView, boolean isInitialization) {
    // Override in children
  }

  public void updateItemById (int id) {
    updateItem(indexOfViewById(id));
  }

  public void updateItem (final int index) {
    if (index != -1) {
      if (isComputingLayout()) {
        UI.post(() -> updateItem(index));
      } else {
        notifyItemChanged(index);
      }
    }
  }

  private boolean isComputingLayout () {
    for (RecyclerView parentView : parentViews) {
      if (parentView.isComputingLayout()) {
        return true;
      }
    }
    return false;
  }

  public void setItem (final int index, final ListItem item) {
    if (isComputingLayout()) {
      UI.post(() -> setItem(index, item));
    } else {
      items.set(index, item);
      notifyItemChanged(index);
    }
  }

  public void addItem (final int index, final ListItem item) {
    if (isComputingLayout()) {
      UI.post(() -> addItem(index, item));
    } else {
      items.add(index, item);
      notifyItemInserted(index);
    }
  }

  public void addItems (final int index, final ListItem... items) {
    if (isComputingLayout()) {
      UI.post(() -> addItems(index, items));
    } else {
      this.items.addAll(index, Arrays.asList(items));
      notifyItemRangeInserted(index, items.length);
    }
  }

  public void removeItemByLongId (final long id) {
    if (isComputingLayout()) {
      UI.post(() -> removeItemByLongId(id));
    } else {
      int i = indexOfViewByLongId(id);
      if (i != -1) {
        items.remove(i);
        notifyItemRemoved(i);
      }
    }
  }

  public void removeItemById (final int id) {
    if (isComputingLayout()) {
      UI.post(() -> removeItemById(id));
    } else {
      int i = indexOfViewById(id);
      if (i != -1) {
        items.remove(i);
        notifyItemRemoved(i);
      }
    }
  }

  public void moveItem (final int fromIndex, final int toIndex, boolean notify) {
    ArrayUtils.move(items, fromIndex, toIndex);
    if (notify) {
      notifyItemMoved(fromIndex, toIndex);
    }
  }

  public void moveItem (final int fromIndex, final int toIndex) {
    if (false && isComputingLayout()) {
      UI.post(() -> moveItem(fromIndex, toIndex));
    } else {
      ArrayUtils.move(items, fromIndex, toIndex);
      notifyItemMoved(fromIndex, toIndex);
    }
  }

  public void removeItem (final int index) {
    if (isComputingLayout()) {
      UI.post(() -> removeItem(index));
    } else {
      items.remove(index);
      notifyItemRemoved(index);
    }
  }

  @Override
  public int measureHeight (int maxHeight) {
    final int itemCount = getItemCount();
    final int size = items.size();
    int height = 0;
    if (size == 0) {
      for (int i = 0; i < itemCount; i++) {
        height += SettingHolder.measureHeightForType(getItemViewType(i));
      }
    } else {
      for (int i = 0; i < itemCount && i < size; i++) {
        height += SettingHolder.measureHeightForType(items.get(i));
      }
    }
    return maxHeight < 0 ? height : Math.min(maxHeight, height);
  }

  @Override
  public int measureScrollTop (int position) {
    if (items.isEmpty()) {
      return SettingHolder.measureHeightForType(getItemViewType(position));
    }
    int top = 0;
    final int size = items.size();
    for (int i = 0; i < position && i < size; i++) {
      top += SettingHolder.measureHeightForType(items.get(i));
    }
    return top;
  }

  @Override
  public int getItemCount () {
    return items.isEmpty() ? noEmptyProgress ? 0 : 1 : items.size();
  }

  // Locale changes

  public void notifyDataLocaleChanged () {
    // TODO better performace
    if (getItemCount() > 0) {
      notifyItemRangeChanged(0, getItemCount());
    }
  }

  // Slider callback

  @Override
  public final void onSliderValueChanged (SliderWrapView v, int value) {
    final int id = v.getId();
    putCheckedInt(id, value);
    ListItem item = (ListItem) v.getTag();
    int oldValue = item.getSliderValue();
    item.setSliderValueIndex(value);
    onSliderValueChanged(item, v, value, oldValue);
  }

  protected void onSliderValueChanged (ListItem item, SliderWrapView view, int newValue, int oldValue) {

  }

  // Toggle and checkboxes

  private @Nullable SparseIntArray checkIntResults;
  private @Nullable SparseArrayCompat<String> checkStringResults;

  public static final int SETTINGS_RESULT_UNKNOWN = -1;
  public static final int SETTINGS_RESULT_INTS = 0;
  public static final int SETTINGS_RESULT_STRING  = 1;

  public int getCheckResultType () {
    return checkIntResults != null ? SETTINGS_RESULT_INTS : checkStringResults != null ? SETTINGS_RESULT_STRING : SETTINGS_RESULT_UNKNOWN;
  }

  private void putCheckedInt (int checkId, int id) {
    if (checkIntResults == null) {
      checkIntResults = new SparseIntArray();
    }
    checkIntResults.put(checkId, id);
  }

  public SparseIntArray getCheckIntResults () {
    if (checkIntResults == null) {
      checkIntResults = new SparseIntArray();
    }
    return checkIntResults;
  }

  public void setIntResult (@IdRes int checkId, @IdRes int resultId) {
    if (checkIntResults == null) {
      return;
    }
    int oldResultId = checkIntResults.get(checkId);
    if (oldResultId != resultId) {
      checkIntResults.put(checkId, resultId);
      updateCheckOptionById(oldResultId, false);
      updateCheckOptionById(resultId, true);
    }
  }

  private void putCheckedString (int checkId, String string) {
    if (checkStringResults == null) {
      checkStringResults = new SparseArrayCompat<>();
    }
    checkStringResults.put(checkId, string);
  }

  public SparseArrayCompat<String> getCheckStringResults () {
    if (checkStringResults == null) {
      checkStringResults = new SparseArrayCompat<>();
    }
    return checkStringResults;
  }

  public int getFirstToggledItem () {
    return checkIntResults != null && checkIntResults.size() > 0 ? checkIntResults.valueAt(0) : 0;
  }

  public void setToggledById (int id, boolean toggled) {
    ListItem item = findItemById(id);
    if (item != null)
      setToggled(item, toggled);
  }

  public void setToggled (@NonNull ListItem item, boolean toggled) {
    processToggle(null, item, toggled);
    if (!toggled) {
      updateCheckOptionById(item.getId(), false);
    }
  }

  public boolean processToggle (@NonNull View v) {
    ListItem item = (ListItem) v.getTag();
    return item != null && processToggle(v, item, toggleView(v));
  }

  public boolean processToggle (@Nullable View v, @NonNull ListItem item, boolean toggleResult) {
    if (toggleResult) {
      if (item.getStringCheckResult() == null) {
        final int prevId;
        switch (item.getViewType()) {
          case ListItem.TYPE_RADIO_OPTION:
          case ListItem.TYPE_RADIO_OPTION_LEFT:
          case ListItem.TYPE_RADIO_OPTION_WITH_AVATAR:
            prevId = checkIntResults != null ? checkIntResults.get(item.getCheckId()) : 0;
            break;
          default:
            prevId = 0;
            break;
        }
        if (item.getId() != prevId) {
          item.setSelected(true);
          putCheckedInt(item.getCheckId(), item.getId());
          updateCheckOptionById(item.getId(), true);

          if (prevId != 0) {
            ListItem prevItem = findItemById(prevId);
            if (prevItem != null) {
              prevItem.setSelected(false);
            }
            updateCheckOptionById(prevId, false);
          }
        }
      } else {
        final String prevString;
        switch (item.getViewType()) {
          case ListItem.TYPE_RADIO_OPTION:
          case ListItem.TYPE_RADIO_OPTION_LEFT:
          case ListItem.TYPE_RADIO_OPTION_WITH_AVATAR:
            prevString = checkStringResults != null ? checkStringResults.get(item.getCheckId()) : null;
            break;
          default:
            prevString = null;
            break;
        }
        if (!StringUtils.equalsOrBothEmpty(item.getStringCheckResult(), prevString)) {
          item.setSelected(true);
          putCheckedString(item.getCheckId(), item.getStringCheckResult());
          updateCheckOptionByStringValue(item.getStringCheckResult(), true);

          if (prevString != null) {
            ListItem prevItem = findItemByCheckResult(prevString);
            if (prevItem != null) {
              prevItem.setSelected(false);
            }
            updateCheckOptionByStringValue(prevString, false);
          }
        }
      }
    } else if (item.getStringCheckResult() != null && checkStringResults != null) {
      String prevResult = checkStringResults.get(item.getCheckId());
      if (prevResult != null) {
        ListItem prevItem = findItemByCheckResult(item.getStringCheckResult());
        if (prevItem != null) {
          prevItem.setSelected(false);
        }
        checkStringResults.remove(item.getCheckId());
        return true;
      }
    } else if (item.getStringCheckResult() == null && checkIntResults != null) {
      int prevId = checkIntResults.get(item.getCheckId());
      if (prevId != 0) {
        ListItem prevItem = findItemById(prevId);
        if (prevItem != null) {
          prevItem.setSelected(false);
        }
        checkIntResults.delete(item.getCheckId());
        return true;
      }
    }
    return true;
  }

  private interface CellFilterImpl {
    int ABORTED = -1;
    int ACCEPTED = 0;
    int REJECTED = 1;

    int accept (ListItem item);
  }

  public void updateAllValuedSettings (@NonNull Filter<ListItem> filter) {
    int i = 0;
    for (ListItem item : items) {
      if (filter.accept(item)) {
        updateValuedSettingByPosition(i);
      }
      i++;
    }
  }

  public void notifyItemsChanged (@NonNull Filter<ListItem> filter) {
    notifyItemsChangedImpl(item -> filter.accept(item) ? wouldUpdateItem(item) : CellFilterImpl.REJECTED);
  }

  private void notifyItemsChangedImpl (@NonNull CellFilterImpl filter) {
    for (ListItem item : items) {
      if (filter.accept(item) == CellFilterImpl.ABORTED) {
        notifyDataSetChanged();
        return;
      }
    }
    int itemCount = 0;
    int startIndex = -1;
    int i = 0;
    for (ListItem item : items) {
      if (filter.accept(item) != CellFilterImpl.REJECTED) {
        if (itemCount == 0)
          startIndex = i;
        itemCount++;
      } else if (itemCount > 0) {
        notifyItemRangeChanged(startIndex, itemCount);
        itemCount = 0;
      }
      i++;
    }
    if (itemCount > 0) {
      notifyItemRangeChanged(startIndex, itemCount);
    }
  }

  public void notifyAllStringsChanged () {
    notifyItemsChangedImpl(SettingsAdapter::wouldUpdateItem);
  }

  public void notifyStringChanged (@StringRes int stringRes) {
    notifyItemsChanged(item -> item.hasStringResource(stringRes));
  }

  private static int wouldUpdateItem (ListItem item) {
    int viewType = item.getViewType();
    if (SettingHolder.isValuedType(viewType))
      return CellFilterImpl.ACCEPTED;
    switch (viewType) {
      case ListItem.TYPE_BUILD_NO:
      case ListItem.TYPE_SEPARATOR:
      case ListItem.TYPE_RECYCLER_HORIZONTAL:
      case ListItem.TYPE_CHAT_BETTER:
      case ListItem.TYPE_HEADER:
      case ListItem.TYPE_HEADER_PADDED:
      case ListItem.TYPE_HEADER_MULTILINE:
      case ListItem.TYPE_HEADER_WITH_ACTION:
      case ListItem.TYPE_LIST_INFO_VIEW:
      case ListItem.TYPE_DESCRIPTION:
      case ListItem.TYPE_DESCRIPTION_SMALL:
      case ListItem.TYPE_ARCHIVED_STICKER_SET:
      case ListItem.TYPE_STICKER_SET:
        return CellFilterImpl.ACCEPTED;
      case ListItem.TYPE_EDITTEXT:
      case ListItem.TYPE_EDITTEXT_REUSABLE:
      case ListItem.TYPE_EDITTEXT_POLL_OPTION:
      case ListItem.TYPE_EDITTEXT_NO_PADDING:
      case ListItem.TYPE_EDITTEXT_NO_PADDING_REUSABLE:
      case ListItem.TYPE_EDITTEXT_COUNTERED:
      case ListItem.TYPE_EDITTEXT_CHANNEL_DESCRIPTION:
      case ListItem.TYPE_EDITTEXT_WITH_PHOTO:
      case ListItem.TYPE_EDITTEXT_WITH_PHOTO_SMALLER:
        return CellFilterImpl.ABORTED;
    }
    if (item.hasStringResources())
      return CellFilterImpl.ACCEPTED;
    return CellFilterImpl.REJECTED;
  }

  @Override
  public void onLanguagePackEvent (int event, int arg1) {
    switch (event) {
      case Lang.EVENT_PACK_CHANGED:
      case Lang.EVENT_DIRECTION_CHANGED:
        notifyAllStringsChanged();
        break;
      case Lang.EVENT_STRING_CHANGED:
        notifyStringChanged(arg1);
        break;
      case Lang.EVENT_DATE_FORMAT_CHANGED:
        // Nothing to change
        break;
    }
  }

  // Lookup helpers

  public List<ListItem> getItems () {
    return items;
  }

  public @Nullable
  ListItem getItem (int index) {
    return index >= 0 && index < items.size() ? items.get(index) : null;
  }

  public int indexOfView (@NonNull Filter<ListItem> filter, int startIndex, boolean reverse) {
    if (reverse) {
      for (int i = startIndex == -1 ? items.size() - 1 : Math.min(items.size() - 1, startIndex); i >= 0; i--) {
        if (filter.accept(items.get(i))) {
          return i;
        }
      }
    } else if (startIndex <= 0) {
      int index = 0;
      for (ListItem item : items) {
        if (filter.accept(item)) {
          return index;
        }
        index++;
      }
    } else {
      for (int i = startIndex; i < items.size(); i++) {
        if (filter.accept(items.get(i))) {
          return i;
        }
      }
    }
    return -1;
  }

  public int indexOfViewByIdAndValue (int id, int intValue) {
    return indexOfView(item -> item.getId() == id && item.getIntValue() == intValue);
  }

  public int indexOfViewByType (int viewType) {
    return indexOfView(item -> item.getViewType() == viewType);
  }

  public int indexOfViewByIdReverse (int id) {
    return indexOfView(item -> item.getId() == id, -1, true);
  }

  public int indexOfViewById (int id) {
    return indexOfViewById(id, -1);
  }

  public int indexOfViewById (int id, int startIndex) {
    return indexOfView(item -> item.getId() == id, startIndex, false);
  }

  public int indexOfViewByStringCheckResult (String value) {
    return indexOfView(item -> value.equals(item.getStringCheckResult()));
  }

  public int indexOfViewByLongId (long id) {
    return indexOfView(item -> item.getLongId() == id);
  }

  public int indexOfViewByData (Object data) {
    return indexOfViewByData(data, 0);
  }

  public int indexOfViewByData (Object data, int startIndex) {
    return indexOfView(item -> item.getData() == data, startIndex, false);
  }

  public int indexOfView (@NonNull Filter<ListItem> filter) {
    return indexOfView(filter, -1, false);
  }

  public int indexOfView (ListItem existingItem) {
    return indexOfView(existingItem, 0);
  }

  public int indexOfView (ListItem existingItem, int startIndex) {
    return indexOfView(item -> item == existingItem, startIndex, false);
  }

  public @Nullable
  ListItem findItemByCheckResult (String result) {
    return getItem(indexOfViewByStringCheckResult(result));
  }

  public @Nullable
  ListItem findItemById (int id) {
    return getItem(indexOfViewById(id));
  }
}
