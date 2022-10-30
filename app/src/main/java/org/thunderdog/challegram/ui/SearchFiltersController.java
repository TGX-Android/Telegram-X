package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.DoubleTextWrapper;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.v.HeaderEditText;
import org.thunderdog.challegram.v.MediaRecyclerView;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.TextView;

import java.util.ArrayList;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;

public class SearchFiltersController extends SharedMembersController implements TextWatcher, PopupLayout.AnimatedPopupProvider {
  private static final int ANIMATOR_ID = 0;
  private final boolean isSecret;

  private FactorAnimator revealAnimator;
  private PopupLayout popupLayout;
  private FilterPickListener listener;
  private FrameLayout rootView;
  private FrameLayout filterRoot;
  private HeaderEditText searchView;
  private static final Paint textPaint = new Paint();

  private final FilterData[] filters = {
    new FilterData(new TdApi.SearchMessagesFilterEmpty(), R.drawable.ic_baseline_filter_list_off_24, "All message type"),
    new FilterData(new SearchMessagesFilterText(), R.drawable.ic_baseline_title_24, "Text message"),
    new FilterData(new TdApi.SearchMessagesFilterPhoto(), R.drawable.baseline_image_24, "Photo"),
    new FilterData(new TdApi.SearchMessagesFilterVideo(), R.drawable.baseline_videocam_24, "Video"),
    new FilterData(new TdApi.SearchMessagesFilterVoiceNote(), R.drawable.baseline_mic_24, "Voice messages"),
    new FilterData(new TdApi.SearchMessagesFilterVideoNote(), R.drawable.deproko_baseline_msg_video_24, "Video messages"),
    new FilterData(new TdApi.SearchMessagesFilterDocument(), R.drawable.baseline_insert_drive_file_24, "Files"),
    new FilterData(new TdApi.SearchMessagesFilterAudio(), R.drawable.baseline_music_note_24, "Music"),
    new FilterData(new TdApi.SearchMessagesFilterAnimation(), R.drawable.deproko_baseline_gif_filled_24, "GIFs")
  };

  private DoubleTextWrapper selectedMember;
  private FilterData selectedFilter = filters[0];
  private FilterView videoNoteView;
  private FilterView textView;

  public SearchFiltersController (Context context, Tdlib tdlib, boolean isSecret) {
    super(context, tdlib);
    this.isSecret = isSecret;


  }

  public DoubleTextWrapper getSelectedMember () {
    return selectedMember;
  }

  public FilterData getSelectedFilter () {
    return selectedFilter;
  }

  public void setListener (FilterPickListener listener) {
    this.listener = listener;
  }

  @Override
  public void onClick (View view) {
    super.onClick(view);

    ListItem item = (ListItem) view.getTag();
    if (item == null || !(item.getData() instanceof DoubleTextWrapper)) {
      return;
    }

    var content = (DoubleTextWrapper) item.getData();

    if (content != null && content.getMember() != null) {
      if (selectedMember != null) {
        selectedMember.setChecked(false);
      }
      selectedMember = content;
      content.setChecked(true);
    }

    if (listener != null) {
      listener.onFilterSelected(selectedMember, selectedFilter);
      if (popupLayout != null) {
        popupLayout.hideWindow(true);
      }
    }
  }

  @Override
  protected void onCreateView (Context context, MediaRecyclerView recyclerView, SettingsAdapter adapter) {
    super.onCreateView(context, recyclerView, adapter);

    textPaint.setTextSize(Screen.dp(16));
    textPaint.setColor(Theme.getColor(R.id.theme_color_text));
  }

  @Override
  protected CharSequence buildTotalCount (ArrayList<DoubleTextWrapper> data) {
    return "";
  }

  @Override
  protected String getExplainedTitle () {
    return "";
  }

  private void createPopup () {
    rootView = new FrameLayout(context);
    View controllerView = get();

    searchView = HeaderEditText.createGreyStyled(rootView);
    FrameLayoutFix.LayoutParams params;

    int headerHeight = Size.getHeaderPortraitSize();
    int headerMarginTop = Screen.dp(24f);
    int spacing = Screen.dp(1f);

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, headerHeight);

    if (Lang.rtl()) {
      params.rightMargin = Screen.dp(63f);
      Screen.dp(49f);
    } else {
      params.leftMargin = Screen.dp(63);
      params.rightMargin = Screen.dp(49f);
    }

    FrameLayout header = new FrameLayout(context);
    FrameLayoutFix.LayoutParams headerParams;

    headerParams = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, headerHeight);
    headerParams.topMargin = headerMarginTop;
    header.setLayoutParams(headerParams);

    searchView.setPadding(Screen.dp(5f), 0, Screen.dp(5f), 0);
    searchView.setHint(Lang.getString(R.string.SearchPeople, searchView, true, false));
    searchView.addTextChangedListener(this);
    searchView.setLayoutParams(params);
    searchView.setTextColor(Theme.textAccentColor());

    BackHeaderButton backButton = new BackHeaderButton(context);
    backButton.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(56f), Size.getHeaderPortraitSize(), Gravity.TOP | (Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT)));
    backButton.setButtonFactor(BackHeaderButton.TYPE_BACK);
    backButton.setOnClickListener(v -> popupLayout.hideWindow(true));
    RippleSupport.setSimpleWhiteBackground(backButton, this);
    backButton.setColor(Theme.getColor(R.id.theme_color_icon));
    Views.setClickable(backButton);

    header.addView(backButton);
    header.addView(searchView);

    FrameLayout.LayoutParams lpc = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    lpc.bottomMargin = headerHeight + spacing;
    lpc.topMargin = headerHeight + headerMarginTop - Screen.dp(5);
    controllerView.setLayoutParams(lpc);

    FrameLayout footer = new FrameLayout(context);
    footer.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, headerHeight, Gravity.BOTTOM));

    TextView cancelTextView = new TextView(context);
    cancelTextView.setText(Lang.getString(R.string.Cancel));
    cancelTextView.setLayoutParams(FrameLayoutFix.newParams(FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | (Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT), Gravity.LEFT | Gravity.CENTER));
    cancelTextView.setAllCaps(true);
    cancelTextView.setTextColor(Theme.textLinkColor());
    cancelTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    cancelTextView.setPadding(Screen.dp(16f), 0, Screen.dp(16f), 0);
    cancelTextView.setTypeface(Fonts.getRobotoMedium());
    cancelTextView.setOnClickListener(view -> popupLayout.hideWindow(true));
    footer.addView(cancelTextView);

    TextView clearTextView = new TextView(context);
    clearTextView.setText(Lang.getString(R.string.Clear));
    clearTextView.setLayoutParams(FrameLayoutFix.newParams(FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | (Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT), Gravity.RIGHT | Gravity.CENTER));
    clearTextView.setAllCaps(true);
    clearTextView.setTextColor(Theme.textLinkColor());
    clearTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    clearTextView.setPadding(Screen.dp(16f), 0, Screen.dp(16f), 0);
    clearTextView.setTypeface(Fonts.getRobotoMedium());
    clearTextView.setOnClickListener(view -> {
      if (selectedMember != null) {
        selectedMember.setChecked(false);
        selectedMember = null;
      }

      if (listener != null) {
        listener.onFilterSelected(null, selectedFilter);
      }
      popupLayout.hideWindow(true);
    });

    footer.addView(clearTextView);

    rootView.addView(header);
    rootView.addView(controllerView);
    rootView.addView(footer);

    ViewSupport.setThemedBackground(rootView, R.id.theme_color_background, this);
    ViewSupport.setThemedBackground(header, R.id.theme_color_filling, this);
    ViewSupport.setThemedBackground(footer, R.id.theme_color_filling, this);
  }

  public static SearchFiltersController create (@NonNull Context context, Tdlib tdlib, long chatId, long messageThreadId, boolean secret, @Nullable FilterPickListener l) {
    SearchFiltersController controller = new SearchFiltersController(context, tdlib, secret);
    controller.setArguments(new SharedBaseController.Args(chatId, messageThreadId));
    controller.setListener(l);
    controller.createPopup();
    return controller;
  }

  public void showFiltersPopup (String searchQuery) {
    if (filterRoot == null) {
      filterRoot = new FrameLayout(context);
      filterRoot.setLayoutParams(FrameLayoutFix.newParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
      ViewSupport.setThemedBackground(filterRoot, R.id.theme_color_transparentEditor, this);
      filterRoot.setOnClickListener(v -> {
        if (popupLayout != null) {
          popupLayout.hideWindow(true);
        }
      });

      ScrollView filtersView = new ScrollView(context);
      filtersView.setLayoutParams(FrameLayoutFix.newParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
      ViewSupport.setThemedBackground(filtersView, R.id.theme_color_filling, this);
      LinearLayout linearLayout = new LinearLayout(context);
      linearLayout.setOrientation(LinearLayout.VERTICAL);

      for (FilterData filter : filters) {
        FilterView filterView = new FilterView(context, filter);
        if (filter.filter.getConstructor() == TdApi.SearchMessagesFilterVideoNote.CONSTRUCTOR) {
          videoNoteView = filterView;
        }
        if (filter.filter.getConstructor() == SearchMessagesFilterText.CONSTRUCTOR) {
          textView = filterView;
        }

        filterView.setLayoutParams(FrameLayoutFix.newParams(FrameLayout.LayoutParams.MATCH_PARENT, Screen.dp(52)));
        linearLayout.addView(filterView);

        filterView.setOnClickListener(v -> {
          if (listener != null) {
            this.selectedFilter = filter;
            listener.onFilterSelected(selectedMember, filter);
          }
          if (popupLayout != null) {
            popupLayout.hideWindow(true);
          }
        });
      }

      filtersView.addView(linearLayout);
      filterRoot.addView(filtersView);
    }

    if (videoNoteView != null) {
      videoNoteView.setVisibility(searchQuery != null && !searchQuery.isEmpty() ? View.GONE : View.VISIBLE);
    }

    if (textView != null) {
      textView.setVisibility((isSecret && (searchQuery == null || searchQuery.isEmpty())) ? View.GONE : View.VISIBLE);
    }

    if (popupLayout != null) {
      popupLayout.restoreIfHidden();
    }

    popupLayout = new PopupLayout(context()) {
      @Override
      public void onCustomShowComplete () {
        super.onCustomShowComplete();
      }
    };
    //popupLayout.setNeedRootInsets();
    // popupLayout.setHideKeyboard();

    tdlib.ui().post(() -> popupLayout.showAnimatedPopupView(filterRoot, this));
  }

  public void showMemberPopup () {
    if (popupLayout != null) {
      popupLayout.restoreIfHidden();
    }

    popupLayout = new PopupLayout(context()) {
      @Override
      public void onCustomShowComplete () {
        super.onCustomShowComplete();
      }
    };
    popupLayout.setNeedRootInsets();
    tdlib.ui().post(() -> {
      popupLayout.showAnimatedPopupView(rootView, this);

      try {
        if (searchView != null) {
          searchView.requestFocus();
        }
      } catch (Exception ignore) {
      }

    });
  }

  public void destroy () {
    super.destroy();
    if (popupLayout != null) {
      popupLayout.hideWindow(false);
      popupLayout = null;
    }

    if (selectedMember != null) {
      selectedMember.setChecked(false);
      selectedMember = null;
    }

    videoNoteView = null;
    textView = null;
    selectedFilter = filters[0];
    rootView = null;
    filterRoot = null;
    revealAnimator = null;
    listener = null;
  }


  @Override
  public void beforeTextChanged (CharSequence charSequence, int i, int i1, int i2) {
  }

  @Override
  public void onTextChanged (CharSequence charSequence, int i, int i1, int i2) {
    search(String.valueOf(charSequence));
  }

  @Override
  public void afterTextChanged (Editable editable) {
  }

  @Override
  public void prepareShowAnimation () {
    revealAnimator = new FactorAnimator(ANIMATOR_ID, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180);
  }

  @Override
  public void launchShowAnimation (PopupLayout popup) {
    revealAnimator.animateTo(1f);
  }

  @Override
  public boolean launchHideAnimation (PopupLayout popup, FactorAnimator originalAnimator) {
    if (originalAnimator != null) {
      originalAnimator.cancel();
    }
    return false;
  }

  public void resetFilter () {
    selectedFilter = filters[0];
  }

  public void reset () {
    resetFilter();
    if (selectedMember != null) {
      selectedMember.setChecked(false);
      selectedMember = null;
    }

    if (searchView != null) {
      searchView.setText("");
    }
  }

  interface FilterPickListener {
    void onFilterSelected (DoubleTextWrapper member, FilterData filter);
  }

  private class FilterView extends View {
    private final Drawable icon;
    FilterData filterData;

    public FilterView (Context context, FilterData filterData) {
      super(context);
      icon = Drawables.get(UI.getResources(), filterData.resourceId);
      this.filterData = filterData;
    }

    @Override
    public void draw (Canvas canvas) {
      super.draw(canvas);

      int margin = Screen.dp(16f);
      int centerY = getMeasuredHeight() / 2;
      int iconSize = Screen.dp(20f);

      if (SearchFiltersController.this.selectedFilter.filter == filterData.filter &&
        filterData.filter.getConstructor() != TdApi.SearchMessagesFilterEmpty.CONSTRUCTOR) {
        canvas.drawColor(Theme.backgroundColor());
      }

      icon.setBounds(margin, centerY - iconSize / 2, iconSize + margin, centerY + iconSize / 2);
      Drawables.draw(canvas, icon, margin, centerY - iconSize / 2f, Paints.getIconLightPorterDuffPaint());
      canvas.drawText(filterData.text, Screen.dp(62), getMeasuredHeight() / 2f + Screen.dp(16) / 2f, textPaint);
    }
  }

  public static class FilterData {
    public TdApi.SearchMessagesFilter filter;
    public int resourceId;
    public String text;

    public FilterData (TdApi.SearchMessagesFilter searchMessagesFilter, int resourceId, String text) {
      this.filter = searchMessagesFilter;
      this.resourceId = resourceId;
      this.text = text;
    }
  }

  /**
   * Filtered locally
   */
  public static class SearchMessagesFilterText extends TdApi.SearchMessagesFilter {

    public SearchMessagesFilterText () {
    }

    public static final int CONSTRUCTOR = TdApi.SearchMessagesFilterFailedToSend.CONSTRUCTOR;

    @Override
    public int getConstructor () {
      return CONSTRUCTOR;
    }
  }
}