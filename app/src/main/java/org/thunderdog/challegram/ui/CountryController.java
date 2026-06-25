package org.thunderdog.challegram.ui;

import android.content.Context;
import android.os.CancellationSignal;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.user.BubbleHeaderView;
import org.thunderdog.challegram.component.user.BubbleView;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.TelegramViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.util.text.Highlight;
import org.thunderdog.challegram.widget.CheckBoxView;
import org.thunderdog.challegram.widget.CustomTextView;
import org.thunderdog.challegram.widget.SectionedRecyclerView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animatorx.BoolAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;

public class CountryController extends TelegramViewController<CountryController.Args> {
  public interface Callback {
    default boolean onCountrySelected (CountryController context, View view, TdApi.CountryInfo country) {
      return false;
    }
    default void onCountryUnselected (CountryController context, View view, TdApi.CountryInfo country) { }
  }

  public static class Args {
    private final Callback callback;
    private final TdApi.CountryInfo[] selectedCountries;

    public Args (Callback callback, TdApi.CountryInfo[] selectedCountries) {
      this.callback = callback;
      this.selectedCountries = selectedCountries;
    }
  }

  private TdApi.CountryInfo[] countries;

  public CountryController (@NonNull Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public boolean needAsynchronousAnimation () {
    return countries == null;
  }

  private void loadCountries () {
    tdlib.getCountries(countries -> {
      this.countries = countries;
      setCountries(countries, null, null);
      runOnUiThreadOptional(this::executeScheduledAnimation);
    });
  }

  private SectionedRecyclerView recyclerView;
  private CountryAdapter adapter;

  @Override
  public int getId () {
    return R.id.controller_country;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Country);
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override
  public boolean supportsBottomInset () {
    return true;
  }

  @Override
  protected void onBottomInsetChanged (int extraBottomInset, int extraBottomInsetWithoutIme, boolean isImeInset) {
    super.onBottomInsetChanged(extraBottomInset, extraBottomInsetWithoutIme, isImeInset);
    Views.applyBottomInset(recyclerView, extraBottomInset);
  }

  private static class CountrySection {
    public final int codePoint;
    public final String letter;
    public int countryCount;

    public CountrySection (int codePoint) {
      this.codePoint = codePoint;
      this.letter = Character.toString(codePoint);
    }
  }

  private static class CountryItem {
    public final TdApi.CountryInfo country;
    public final String emoji;

    public final String name;
    public final Highlight highlight;

    public CountryItem (TdApi.CountryInfo country, String emoji, String name, Highlight highlight) {
      this.country = country;
      this.emoji = emoji;
      this.name = name;
      this.highlight = highlight;
    }
  }

  private final Comparator<CountryItem> comparator = (c1, c2) -> {
    TdApi.CountryInfo o1 = c1.country;
    TdApi.CountryInfo o2 = c2.country;
    int cmp;
    cmp = o1.name.compareTo(o2.name);
    if (cmp != 0) {
      return cmp < 0 ? -1 : 1;
    }
    cmp = o1.englishName.compareTo(o2.englishName);
    if (cmp != 0) {
      return cmp < 0 ? -1 : 1;
    }
    cmp = o1.countryCode.compareTo(o2.countryCode);
    if (cmp != 0) {
      return cmp < 0 ? -1 : 1;
    }
    return 0;
  };

  private void setCountries (TdApi.CountryInfo[] countries, @Nullable String prefix, @Nullable CancellationSignal signal) {
    boolean search = !StringUtils.isEmpty(prefix);
    String numberPrefix = Strings.getNumber(prefix);

    List<CountryItem> list = new ArrayList<>();
    for (TdApi.CountryInfo country : countries) {
      if (signal != null && signal.isCanceled()) {
        return;
      }
      String emoji = Emoji.getEmojiFlagFromCountry(country.countryCode);
      String emojiPrefix = StringUtils.isEmpty(emoji) ? "" : emoji + " ";
      String displayText = emojiPrefix + country.name;
      Highlight displayHighlight = null;

      if (search) {
        boolean emojiMatches = emoji != null && emoji.equals(prefix);
        boolean countryCodeMatches = country.countryCode.equalsIgnoreCase(prefix);
        Highlight nameHighlight = Highlight.valueOfExactWord(country.name, prefix);
        Highlight nameInEnglishHighlight = Highlight.valueOfExactWord(country.englishName, prefix);

        boolean numberMatches = false;
        if (!StringUtils.isEmpty(numberPrefix)) {
          for (String callingCode : country.callingCodes) {
            if (callingCode.startsWith(numberPrefix)) {
              numberMatches = true;
              break;
            }
          }
        }

        if (!emojiMatches && !countryCodeMatches && !numberMatches && nameHighlight == null && nameInEnglishHighlight == null) {
          // No match for the search query, skip.
          continue;
        }

        displayHighlight = nameHighlight != null ? nameHighlight.offset(emojiPrefix.length()) : null;
      } else if (country.isHidden) {
        // Hidden and not result of a search, skip.
        continue;
      }

      CountryItem item = new CountryItem(
        country, emoji,
        displayText, displayHighlight
      );
      list.add(item);
    }
    list.sort(comparator);

    List<CountrySection> sections = new ArrayList<>();
    CountrySection section = null;

    for (CountryItem country : list) {
      int sectionCodePoint = country.country.name.isEmpty() ? 0 : country.country.name.codePointAt(0);
      if (section == null || section.codePoint != sectionCodePoint) {
        section = new CountrySection(sectionCodePoint);
        sections.add(section);
      }
      section.countryCount++;
    }

    executeOnUiThreadOptional(() -> {
      if (signal == null || !signal.isCanceled()) {
        boolean hadItems = adapter.hasCountries();
        adapter.setCountries(list, sections);
        if (hadItems) {
          ((LinearLayoutManager) recyclerView.getLayoutManager()).scrollToPositionWithOffset(0, 0);
        }
      }
    });
  }

  private static class CountryAdapter extends SectionedRecyclerView.SectionedAdapter {
    private final CountryController controller;

    public CountryAdapter (SectionedRecyclerView parentView, CountryController controller) {
      super(parentView);
      this.controller = controller;
    }

    public void updateCountry (TdApi.CountryInfo countryInfo) {
      int index = 0;
      for (CountryItem country : countries) {
        if (country.country.countryCode.equals(countryInfo.countryCode)) {
          updateViewByPosition(index);
          break;
        }
        index++;
      }
    }

    private List<CountryItem> countries;
    private List<CountrySection> sections;

    public boolean hasCountries () {
      return countries != null && !countries.isEmpty();
    }

    public void setCountries (List<CountryItem> list, List<CountrySection> sections) {
      int oldItemCount = getItemCount();

      this.countries = list;
      this.sections = sections;
      updateSections();

      int newItemCount = getItemCount();

      if (oldItemCount > 0 && newItemCount > 0) {
        notifyItemRangeChanged(0, Math.min(oldItemCount, newItemCount));
      }
      if (newItemCount > oldItemCount) {
        notifyItemRangeInserted(oldItemCount, newItemCount - oldItemCount);
      } else if (oldItemCount > newItemCount) {
        notifyItemRangeRemoved(newItemCount, oldItemCount - newItemCount);
      }
    }

    @Override
    public int getSectionCount () {
      return sections != null ? sections.size() : 0;
    }

    @NonNull
    @Override
    public String getSectionName (int section) {
      return sections != null ? sections.get(section).letter : "#";
    }

    @Override
    public int getRowsInSection (int section) {
      return sections != null ? sections.get(section).countryCount : 0;
    }

    @Override
    public int getItemHeight () {
      return SettingHolder.measureHeightForType(ListItem.TYPE_COUNTRY);
    }

    @Override
    public View createView (int viewType) {
      SettingHolder holder = SettingHolder.create(context, controller.tdlib(), ListItem.TYPE_COUNTRY, null, v -> {
        CountryItem item = (CountryItem) v.getTag();
        controller.selectUnselectCountry(v, item);
      }, null, controller, null, null);
      holder.itemView.setPadding(Screen.dp(56f), 0, 0, 0);
      int extraPadding = Screen.dp(6f);
      ((ViewGroup.MarginLayoutParams) holder.itemView.findViewById(R.id.code).getLayoutParams()).rightMargin += extraPadding;
      ((ViewGroup.MarginLayoutParams) holder.itemView.findViewById(R.id.btn_check).getLayoutParams()).rightMargin += extraPadding;
      return holder.itemView;
    }

    @Override
    public void updateView (SectionedRecyclerView.SectionViewHolder holder, int position, boolean isUpdate) {
      CountryItem item = countries.get(position);

      ViewGroup group = (ViewGroup) holder.itemView;
      group.setTag(item);

      CustomTextView nameView = (CustomTextView) group.getChildAt(0);
      TextView codeView = (TextView) group.getChildAt(1);
      CheckBoxView checkBoxView = (CheckBoxView) group.getChildAt(2);

      nameView.setText(item.name, null, item.highlight, false);
      codeView.setText(Strings.join(" ", item.country.callingCodes, callingCode -> "+" + callingCode));

      boolean isSelected = controller.pickedCountries.contains(item.country.countryCode);
      BoolAnimator animator = (BoolAnimator) checkBoxView.getTag();
      if (animator == null) {
        animator = new BoolAnimator(180L, AnimatorUtils.DECELERATE_INTERPOLATOR, (state, animatedValue, stateChanged, prevState) -> {
          codeView.setAlpha(1f - animatedValue);
          checkBoxView.setAlpha(animatedValue);

          if (stateChanged) {
            if (prevState == BoolAnimator.State.TRUE) {
              codeView.setVisibility(View.VISIBLE);
              ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) nameView.getLayoutParams();
              params.rightToLeft = R.id.code;
              nameView.setLayoutParams(params);
            } else if (state == BoolAnimator.State.TRUE) {
              ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) nameView.getLayoutParams();
              params.rightToLeft = R.id.btn_check;
              nameView.setLayoutParams(params);
              codeView.setVisibility(View.GONE);
            }
            if (prevState == BoolAnimator.State.FALSE) {
              checkBoxView.setVisibility(View.VISIBLE);
            } else if (state == BoolAnimator.State.FALSE) {
              checkBoxView.setVisibility(View.GONE);
            }
          }
        });
        checkBoxView.setTag(animator);
      }

      animator.changeValue(isSelected, isUpdate);
      checkBoxView.setChecked(isSelected, isUpdate);
    }
  }

  private BubbleHeaderView headerCell;

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }

  @Override
  protected View onCreateView (Context context) {
    FrameLayoutFix contentView = new FrameLayoutFix(context);
    ViewSupport.setThemedBackground(contentView, ColorId.filling, this);

    recyclerView = new SectionedRecyclerView(context);
    Views.applyBottomInset(recyclerView, extraBottomInset);
    recyclerView.setSectionedAdapter(adapter = new CountryAdapter(recyclerView, this));
    recyclerView.setItemAnimator(null);
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
        if (newState != RecyclerView.SCROLL_STATE_IDLE) {
          hideSoftwareKeyboard();
        }
      }
    });
    addThemeInvalidateListener(recyclerView);

    contentView.addView(recyclerView);

    // Header

    headerCell = new BubbleHeaderView(context, tdlib);
    headerCell.setHint(bindLocaleChanger(R.string.SearchCountry, headerCell.getInput(), true, false));
    headerCell.setCallback(new BubbleHeaderView.Callback() {
      @Override
      public View getTranslationView () {
        return recyclerView;
      }

      private String lastQuery;
      private CancellationSignal searchCancellation;

      @Override
      public void searchForItems (String q) {
        if (lastQuery == null) {
          lastQuery = "";
        }
        if (lastQuery.equals(q)) return;
        lastQuery = q;
        if (searchCancellation != null) {
          searchCancellation.cancel();
        }
        CancellationSignal signal = new CancellationSignal();
        searchCancellation = signal;
        Background.instance().post(() -> {
          setCountries(countries, q, signal);
        });
      }

      @Override
      public void onBubbleRemoved (@NonNull BubbleView.Entry entry) {
        unselectCountry(entry);
      }

      @Override
      public void setHeaderOffset (int offset) {
        if (headerOffset != offset) {
          headerOffset = offset;
          recyclerView.setTranslationY(offset);
          RecyclerView recyclerView = getChatSearchView();
          if (recyclerView != null) {
            recyclerView.setTranslationY(offset);
          }
          int height = getHeaderHeight();
          if (navigationController != null) {
            navigationController.getHeaderView().setBackgroundHeight(height);
            navigationController.getFloatingButton().updatePosition(height);
          }
        }
      }

      @Override
      public void applyHeaderOffset () {
        ((FrameLayoutFix.LayoutParams) recyclerView.getLayoutParams()).bottomMargin = (int) recyclerView.getTranslationY();
        recyclerView.requestLayout();
        RecyclerView recyclerView = getChatSearchView();
        if (recyclerView != null) {
          Views.setBottomMargin(recyclerView, (int) recyclerView.getTranslationY());
        }
      }

      @Override
      public void prepareHeaderOffset (int offset) {
        ((FrameLayoutFix.LayoutParams) recyclerView.getLayoutParams()).bottomMargin = offset;
        recyclerView.requestLayout();
        RecyclerView recyclerView = getChatSearchView();
        if (recyclerView != null) {
          Views.setBottomMargin(recyclerView, offset);
        }
      }
    });

    if (!pickedBubbles.isEmpty()) {
      headerCell.forceBubbles(pickedBubbles);
      headerOffset = headerCell.getCurrentWrapHeight();
      recyclerView.setTranslationY(headerOffset);
      ((FrameLayoutFix.LayoutParams) recyclerView.getLayoutParams()).bottomMargin = headerOffset;
    }

    // Data

    loadCountries();

    return contentView;
  }

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    pickedBubbles.clear();
    pickedCountries.clear();
    if (args.selectedCountries != null) {
      for (TdApi.CountryInfo selectedCountry : args.selectedCountries) {
        pickedBubbles.add(BubbleView.Entry.valueOf(tdlib, selectedCountry, Emoji.getEmojiFlagFromCountry(selectedCountry.countryCode)));
        pickedCountries.add(selectedCountry.countryCode);
      }
    }
  }

  private final Set<String> pickedCountries = new LinkedHashSet<>();
  private final List<BubbleView.Entry> pickedBubbles = new ArrayList<>();
  private int headerOffset;

  private void unselectCountry (BubbleView.Entry bubbleEntry) {
    CountryItem item = null;
    String countryCode = null;
    int index = -1;
    if (bubbleEntry.id.startsWith("country_")) {
      countryCode = bubbleEntry.id.substring("country_".length());
      int i = 0;
      for (CountryItem country : adapter.countries) {
        if (country.country.countryCode.equals(countryCode)) {
          item = country;
          index = i;
          break;
        }
        i++;
      }
    }
    if (item == null) {
      return;
    }

    pickedBubbles.remove(bubbleEntry);
    pickedCountries.remove(countryCode);
    getArgumentsStrict().callback.onCountryUnselected(this, null, item.country);
    adapter.updateViewByPosition(index);
  }

  private void selectUnselectCountry (View view, CountryItem item) {
    String id = "country_" + item.country.countryCode;
    for (int i = 0; i < pickedBubbles.size(); i++) {
      BubbleView.Entry entry = pickedBubbles.get(i);
      if (entry.id.equals(id)) {
        pickedBubbles.remove(i);
        pickedCountries.remove(item.country.countryCode);
        headerCell.removeBubble(entry);
        getArgumentsStrict().callback.onCountryUnselected(this, view, item.country);
        adapter.updateCountry(item.country);
        return;
      }
    }

    if (getArgumentsStrict().callback.onCountrySelected(this, view, item.country)) {
      BubbleView.Entry entry = BubbleView.Entry.valueOf(tdlib, item.country, Emoji.getEmojiFlagFromCountry(item.country.countryCode));
      pickedBubbles.add(entry);
      headerCell.addBubble(entry);
      pickedCountries.add(item.country.countryCode);
      adapter.updateCountry(item.country);
      if (adapter.hasCountries() && adapter.countries.size() == 1) {
        headerCell.getInput().setText("");
      }
    }
  }

  @Override
  protected int getHeaderHeight () {
    return Size.getHeaderPortraitSize() + headerOffset;
  }

  @Override
  protected int getMaximumHeaderHeight () {
    return Size.getHeaderBigPortraitSize(false);
  }

  @Override
  public void hideSoftwareKeyboard () {
    super.hideSoftwareKeyboard();
    if (headerCell != null) {
      Keyboard.hide(headerCell.getInput());
    }
  }
}
