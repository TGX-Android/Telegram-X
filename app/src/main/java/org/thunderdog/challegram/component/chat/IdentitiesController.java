package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.Identity;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.CustomTextView;
import org.thunderdog.challegram.widget.ShadowView;
import org.thunderdog.challegram.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;

public class IdentitiesController extends ViewController<IdentitiesController.Arguments>
  implements View.OnClickListener, FactorAnimator.Target {
  private final static float TOP_BAR_ICON_SIZE = 24f;
  private final static float BAR_Y_PADDING = 20f;
  private static final long ANIMATION_DURATION = 220l;
  private static final int ANIMATOR_TOP_BAR_SWITCH = 0;
  private static final int ANIMATOR_CLEAR_BUTTON_DISAPPEARANCE = 1;

  private LinearLayout layout;
  private View defaultTopBar;
  private View searchTopBar;
  private View content;
  private LinearLayout noResultsBanner;
  private ShadowView contentShadow;
  private IdentityAdapter identityAdapter;
  private EditText searchText;
  private ImageView clearButton;
  private ArrayList<Identity> filteredIdentities;
  private Identity selectedIdentity;

  private boolean isSearching;

  private BoolAnimator topBarSwitchAnimator = new BoolAnimator(
    ANIMATOR_TOP_BAR_SWITCH,
    this,
    AnimatorUtils.DECELERATE_INTERPOLATOR,
    ANIMATION_DURATION
  );

  private BoolAnimator clearButtonAnimator = new BoolAnimator(
    ANIMATOR_CLEAR_BUTTON_DISAPPEARANCE,
    this,
    AnimatorUtils.DECELERATE_INTERPOLATOR,
    ANIMATION_DURATION
  );

  public IdentitiesController (@NonNull Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_identities;
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_TOP_BAR_SWITCH: {
        defaultTopBar.setAlpha(1f - factor);
        searchTopBar.setAlpha(factor);
        break;
      }
      case ANIMATOR_CLEAR_BUTTON_DISAPPEARANCE: {
        clearButton.setAlpha(factor);
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_TOP_BAR_SWITCH: {
        if (finalFactor == 1f) {
          defaultTopBar.setVisibility(View.GONE);
        } else {
          searchTopBar.setVisibility(View.GONE);
        }
        break;
      }
    }
  }

  @Override
  protected View onCreateView (Context context) {
    FrameLayoutFix wrapper = new FrameLayoutFix(context);
    RelativeLayout.LayoutParams wrapperParams = new RelativeLayout.LayoutParams(
      FrameLayoutFix.LayoutParams.MATCH_PARENT,
      FrameLayoutFix.LayoutParams.WRAP_CONTENT
    );
    wrapperParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
    wrapper.setLayoutParams(wrapperParams);
    wrapper.setBackgroundColor(Theme.backgroundColor());
    addThemeBackgroundColorListener(wrapper, R.id.theme_color_background);

    FrameLayoutFix topBarWrapper = new FrameLayoutFix(context);
    topBarWrapper.setLayoutParams(
      new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    );
    topBarWrapper.setForegroundGravity(Gravity.BOTTOM);
    topBarWrapper.setBackgroundColor(Theme.fillingColor());
    addThemeBackgroundColorListener(topBarWrapper, R.id.theme_color_filling);

    layout = new LinearLayout(context);
    FrameLayoutFix.LayoutParams layoutParams = new FrameLayoutFix.LayoutParams(
      RelativeLayout.LayoutParams.MATCH_PARENT,
      Screen.dp(400f)
    );
    layout.setLayoutParams(layoutParams);
    layout.setOrientation(LinearLayout.VERTICAL);

    defaultTopBar = createDefaultTopBar(context);
    searchTopBar = createSearchTopBar(context);
    searchTopBar.setAlpha(0f);
    searchTopBar.setVisibility(View.GONE);

    topBarWrapper.addView(defaultTopBar);
    topBarWrapper.addView(searchTopBar);

    filteredIdentities = new ArrayList<>();
    filteredIdentities.addAll(getArguments().identities);
    content = createContent(filteredIdentities, context);

    contentShadow = createShadowView(context);

    layout.addView(topBarWrapper);
    layout.addView(content);
    layout.addView(contentShadow);

    wrapper.addView(layout);
    return wrapper;
  }

  private View createContent (List<Identity> identities, Context context) {
    FrameLayoutFix wrapper = new FrameLayoutFix(context);
    wrapper.setLayoutParams(new LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.MATCH_PARENT
    ));

    RecyclerView recycler = new RecyclerView(context);
    selectedIdentity = getArguments().selectedIdentity;
    identityAdapter = new IdentityAdapter(tdlib, identities, selectedIdentity,
      identity -> {
        identityAdapter.setUseAnimations(true);
        getArguments().onIdentityClick.accept(identity);
        int oldIdentityPos = getSelectedIdentityIndexInList();
        selectedIdentity = identity;
        int newIdentityPos = getSelectedIdentityIndexInList();
        identityAdapter.setSelectedIdentity(identity);
        identityAdapter.notifyItemChanged(oldIdentityPos);
        identityAdapter.notifyItemChanged(newIdentityPos);
      },
      () -> {
        getArguments().close.run();
      }
    );
    recycler.setAdapter(identityAdapter);
    recycler.setLayoutManager(new LinearLayoutManager(context));
    ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    );
    recycler.setLayoutParams(params);
    recycler.setBackgroundColor(Theme.fillingColor());

    noResultsBanner = new LinearLayout(context);
    noResultsBanner.setLayoutParams(params);
    noResultsBanner.setOrientation(LinearLayout.VERTICAL);
    noResultsBanner.setGravity(Gravity.CENTER);
    noResultsBanner.setVisibility(View.INVISIBLE);

    ImageView noResultsIcon = new ImageView(context);
    LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
      Screen.dp(96f),
      Screen.dp(96f)
    );
    noResultsIcon.setScaleType(ImageView.ScaleType.CENTER);
    noResultsIcon.setImageResource(R.drawable.baseline_search_96);
    noResultsIcon.setColorFilter(Theme.iconColor());
    noResultsIcon.setLayoutParams(iconParams);
    addThemeFilterListener(noResultsIcon, R.id.theme_color_icon);

    TextView noResultsText = new TextView(context);
    noResultsText.setText(Lang.getString(R.string.NoResultsToShow));
    noResultsText.setLayoutParams(new LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ));
    noResultsText.setPadding(Screen.dp(20f), Screen.dp(10f), Screen.dp(20f), Screen.dp(10f));
    noResultsText.setGravity(Gravity.CENTER_VERTICAL);
    noResultsText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
    noResultsText.setTextColor(Theme.iconColor());
    addThemeTextColorListener(noResultsText, R.id.theme_color_icon);

    TextView noResultsDescriptionText = new TextView(context);
    noResultsDescriptionText.setText(Lang.getString(R.string.NoResultsToShowDescription));
    noResultsDescriptionText.setLayoutParams(new LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ));
    noResultsDescriptionText.setPadding(Screen.dp(20f), Screen.dp(10f), Screen.dp(20f), Screen.dp(10f));
    noResultsDescriptionText.setGravity(Gravity.CENTER_VERTICAL);
    noResultsDescriptionText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
    noResultsDescriptionText.setTextColor(Theme.iconColor());
    addThemeTextColorListener(noResultsDescriptionText, R.id.theme_color_icon);

    noResultsBanner.addView(noResultsIcon);
    noResultsBanner.addView(noResultsText);
    noResultsBanner.addView(noResultsDescriptionText);

    ShadowView topBarShadow = createShadowView(context);

    wrapper.addView(recycler);
    wrapper.addView(noResultsBanner);
    wrapper.addView(topBarShadow);

    return wrapper;
  }

  private int getSelectedIdentityIndexInList () {
    for (int i = 0; i < filteredIdentities.size(); i++) {
      if (filteredIdentities.get(i).getId() == selectedIdentity.getId()) {
        return i;
      }
    }
    return -1;
  }

  private View createDefaultTopBar (Context context) {
    RelativeLayout topBar = new RelativeLayout(context);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      Screen.dp(56f)
    );
    topBar.setGravity(Gravity.CENTER_VERTICAL);
    topBar.setLayoutParams(params);

    LinearLayout leftWrapper = new LinearLayout(context);
    RelativeLayout.LayoutParams leftWrapperParams = new RelativeLayout.LayoutParams(
      LinearLayout.LayoutParams.WRAP_CONTENT,
      LinearLayout.LayoutParams.MATCH_PARENT
    );
    leftWrapperParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
    leftWrapper.setOrientation(LinearLayout.HORIZONTAL);
    leftWrapper.setGravity(Gravity.CENTER_VERTICAL);
    leftWrapper.setLayoutParams(leftWrapperParams);

    LinearLayout rightWrapper = new LinearLayout(context);
    RelativeLayout.LayoutParams rightWrapperParams = new RelativeLayout.LayoutParams(
      LinearLayout.LayoutParams.WRAP_CONTENT,
      LinearLayout.LayoutParams.MATCH_PARENT
    );
    rightWrapperParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
    rightWrapper.setOrientation(LinearLayout.HORIZONTAL);
    rightWrapper.setGravity(Gravity.CENTER_VERTICAL);
    rightWrapper.setLayoutParams(rightWrapperParams);

    ImageView closeButton = createButtonIcon(
      R.id.btn_close,
      Screen.dp(TOP_BAR_ICON_SIZE) + Screen.dp(20f) * 2,
      R.drawable.baseline_close_24);
    closeButton.setPadding(
      Screen.dp(20f), Screen.dp(BAR_Y_PADDING), Screen.dp(20f), Screen.dp(BAR_Y_PADDING)
    );
    RippleSupport.setTransparentSelector(closeButton);

    CustomTextView sendAsText = new CustomTextView(context, tdlib);
    int backButtonWidth = Screen.dp(TOP_BAR_ICON_SIZE) + Screen.dp(20f) * 2;
    int searchButtonWidth = Screen.dp(TOP_BAR_ICON_SIZE) + Screen.dp(20f) * 2;
    int sendAsWidth = Screen.currentWidth() - backButtonWidth - searchButtonWidth;
    LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
      sendAsWidth,
      ViewGroup.LayoutParams.WRAP_CONTENT
    );
    sendAsText.setPadding(Screen.dp(20f), 0, Screen.dp(20f), 0);
    sendAsText.setId(R.id.msg_sendAs);
    sendAsText.setBoldText(Lang.getString(R.string.MessageAsX, ""), null, false);
    sendAsText.setTextSize(18f);
    sendAsText.setLayoutParams(textParams);
    addThemeTextColorListener(sendAsText, R.id.theme_color_text);

    leftWrapper.addView(closeButton);
    leftWrapper.addView(sendAsText);

    ImageView searchButton = createButtonIcon(
      R.id.btn_search,
      Screen.dp(TOP_BAR_ICON_SIZE) + Screen.dp(20f) * 2,
      R.drawable.baseline_search_24);
    searchButton.setPadding(
      Screen.dp(20f), Screen.dp(BAR_Y_PADDING), Screen.dp(20f), Screen.dp(BAR_Y_PADDING)
    );
    RippleSupport.setTransparentSelector(searchButton);

    rightWrapper.addView(searchButton);

    topBar.addView(leftWrapper);
    topBar.addView(rightWrapper);

    return topBar;
  }

  private View createSearchTopBar (Context context) {
    RelativeLayout topBar = new RelativeLayout(context);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      Screen.dp(56f)
    );
    topBar.setGravity(Gravity.CENTER_VERTICAL);
    topBar.setLayoutParams(params);

    LinearLayout leftWrapper = new LinearLayout(context);
    RelativeLayout.LayoutParams leftWrapperParams = new RelativeLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,
      LinearLayout.LayoutParams.MATCH_PARENT
    );
    leftWrapperParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
    leftWrapper.setOrientation(LinearLayout.HORIZONTAL);
    leftWrapper.setLayoutParams(leftWrapperParams);

    LinearLayout rightWrapper = new LinearLayout(context);
    RelativeLayout.LayoutParams rightWrapperParams = new RelativeLayout.LayoutParams(
      LinearLayout.LayoutParams.WRAP_CONTENT,
      LinearLayout.LayoutParams.MATCH_PARENT
    );
    rightWrapperParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
    rightWrapper.setOrientation(LinearLayout.HORIZONTAL);
    rightWrapper.setLayoutParams(rightWrapperParams);

    ImageView backButton = createButtonIcon(
      R.id.btn_back,
      Screen.dp(TOP_BAR_ICON_SIZE) + Screen.dp(20f) * 2,
      R.drawable.baseline_arrow_back_24);
    backButton.setPadding(
      Screen.dp(20f), Screen.dp(BAR_Y_PADDING), Screen.dp(20f), Screen.dp(BAR_Y_PADDING)
    );
    RippleSupport.setTransparentSelector(backButton);

    searchText = new EditText(context);
    int backButtonWidth = Screen.dp(TOP_BAR_ICON_SIZE) + Screen.dp(20f) * 2;
    int searchButtonWidth = Screen.dp(TOP_BAR_ICON_SIZE) + Screen.dp(20f) * 2;
    int searchWidth = Screen.currentWidth() - backButtonWidth - searchButtonWidth;
    LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
      searchWidth, ViewGroup.LayoutParams.MATCH_PARENT
    );
    searchText.setPadding(Screen.dp(20f), 0, Screen.dp(20f), 0);
    searchText.setId(R.id.edit_searchIdentity);
    searchText.setHint(Lang.getString(R.string.Search));
    searchText.setLayoutParams(textParams);
    searchText.setGravity(Gravity.CENTER_VERTICAL);
    searchText.setBackground(null);
    searchText.setMaxLines(1);
    searchText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    searchText.setTextColor(Theme.textAccentColor());
    searchText.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    searchText.setOnFocusChangeListener((view, hasFocus) -> {
      if (!hasFocus) {
        InputMethodManager inputManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(searchText.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
      }
    });
    searchText.addTextChangedListener(new TextWatcher() {
      @Override
      public void afterTextChanged(Editable s) {}

      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        clearButtonAnimator.setValue(s.length() != 0, true);
        filterIdentities(s.toString());
      }
    });

    clearButton = createButtonIcon(
      R.id.btn_clear,
      Screen.dp(TOP_BAR_ICON_SIZE) + Screen.dp(20f) * 2,
      R.drawable.baseline_close_24);
    clearButton.setPadding(
      Screen.dp(20f), Screen.dp(BAR_Y_PADDING), Screen.dp(20f), Screen.dp(BAR_Y_PADDING)
    );
    clearButton.setColorFilter(Theme.textAccentColor());
    RippleSupport.setTransparentSelector(clearButton);
    clearButton.setAlpha(0f);

    leftWrapper.addView(backButton);
    leftWrapper.addView(searchText);

    rightWrapper.addView(clearButton);

    topBar.addView(leftWrapper);
    topBar.addView(rightWrapper);

    return topBar;
  }

  private ImageView createButtonIcon (int id, int width, int resource) {
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT);
    ImageView button = new ImageView(context);
    button.setId(id);
    button.setScaleType(ImageView.ScaleType.CENTER);
    button.setImageResource(resource);
    button.setColorFilter(Theme.iconColor());
    button.setOnClickListener(this);
    button.setLayoutParams(params);
    addThemeFilterListener(button, R.id.theme_color_icon);
    return button;
  }

  private void updateTopBar () {
    defaultTopBar.setVisibility(View.VISIBLE);
    searchTopBar.setVisibility(View.VISIBLE);
    topBarSwitchAnimator.setValue(isSearching, true);
  }

  private ShadowView createShadowView (Context context) {
    ShadowView shadowView = new ShadowView(context);
    ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
      RelativeLayout.LayoutParams.MATCH_PARENT,
      RelativeLayout.LayoutParams.WRAP_CONTENT
    );
    shadowView.setLayoutParams(params);
    addThemeInvalidateListener(shadowView);
    shadowView.setSimpleBottomTransparentShadow(true);
    return shadowView;
  }

  private void filterIdentities (String filter) {
    List<Identity> newFiltered = getArguments().identities.stream().filter(identity -> {
        String name = identity.getName().toLowerCase();
        return name.contains(filter.toLowerCase());
      }
    ).collect(Collectors.toList());
    runOnUiThread(() -> {
      filteredIdentities.clear();
      filteredIdentities.addAll(newFiltered);
      identityAdapter.setUseAnimations(false);
      identityAdapter.notifyDataSetChanged();
      if (newFiltered.isEmpty()) {
        noResultsBanner.setVisibility(View.VISIBLE);
        contentShadow.setVisibility(View.INVISIBLE);
      } else {
        noResultsBanner.setVisibility(View.INVISIBLE);
        contentShadow.setVisibility(View.VISIBLE);
      }
    });
  }

  @Override
  public void onClick (View v) {
    switch (v.getId()) {
      case R.id.btn_search: {
        isSearching = true;
        updateTopBar();
        getArguments().goToSearchMode.run();
        break;
      }
      case R.id.btn_back: {
        isSearching = false;
        updateTopBar();
        getArguments().quitSearchMode.run();
        break;
      }
      case R.id.btn_close: {
        getArguments().close.run();
        break;
      }
      case R.id.btn_clear: {
        searchText.setText("");
        searchText.clearFocus();
      }
    }
  }

  public static class Arguments {
    private final List<Identity> identities;
    private final Identity selectedIdentity;
    private final Runnable close;
    private final Runnable goToSearchMode;
    private final Runnable quitSearchMode;
    private final Consumer<Identity> onIdentityClick;

    public Arguments (
      List<Identity> identities,
      Identity selectedIdentity,
      Runnable close,
      Runnable goToSearchMode,
      Runnable quitSearchMode,
      Consumer<Identity> onIdentityClick
    ) {
      this.identities = identities;
      this.selectedIdentity = selectedIdentity;
      this.close = close;
      this.goToSearchMode = goToSearchMode;
      this.quitSearchMode = quitSearchMode;
      this.onIdentityClick = onIdentityClick;
    }
  }

  private static class IdentityAdapter extends RecyclerView.Adapter<IdentityAdapter.ItemHolder> {
    private final Tdlib tdlib;
    private final List<Identity> identities;
    private Identity selectedIdentity;
    private final Consumer<Identity> onItemClick;
    private final Runnable close;
    private boolean useAnimations = false;

    public IdentityAdapter (
      Tdlib tdlib, List<Identity> identities, Identity selectedIdentity, Consumer<Identity> onItemClick, Runnable close
    ) {
      this.tdlib = tdlib;
      this.identities = identities;
      this.selectedIdentity = selectedIdentity;
      this.onItemClick = onItemClick;
      this.close = close;
    }

    public void setUseAnimations (boolean useAnimations) {
      this.useAnimations = useAnimations;
    }

    public void setSelectedIdentity (Identity identity) {
      this.selectedIdentity = identity;
    }

    @NonNull
    @Override
    public ItemHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      IdentityView identityView = new IdentityView(parent.getContext(), tdlib);
      identityView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(60f)));
      identityView.setPadding(Screen.dp(IdentityView.OUT_X_PADDING), 0 , Screen.dp(IdentityView.OUT_X_PADDING), 0);
      RippleSupport.setTransparentSelector(identityView);
      identityView.init(close);
      return new ItemHolder(identityView);
    }

    @Override
    public void onBindViewHolder (@NonNull ItemHolder holder, int position) {
      Identity identity = identities.get(position);
      holder.identityView.setIdentity(identity);
      holder.identityView.setIsChecked(identity.getId() == selectedIdentity.getId(), useAnimations);
      holder.identityView.setIsLast(position == identities.size() - 1);
      if (identity.isLocked()) {
        holder.identityView.setClickable(false);
      } else {
        holder.identityView.setClickable(true);
        holder.identityView.setOnClickListener(view -> {
          if (selectedIdentity.getId() != identity.getId()) {
            onItemClick.accept(identity);
          }
        });
      }
    }

    @Override
    public int getItemCount () {
      return identities.size();
    }

    private static class ItemHolder extends RecyclerView.ViewHolder {
      public IdentityView identityView;

      public ItemHolder (IdentityView identityView) {
        super(identityView);
        this.identityView = identityView;
      }
    }
  }
}
