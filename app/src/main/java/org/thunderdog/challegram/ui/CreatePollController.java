/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 28/02/2019
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Rect;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.NavigationStack;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.CharacterStyleFilter;
import org.thunderdog.challegram.util.HapticMenuHelper;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.CheckBoxView;
import org.thunderdog.challegram.widget.FillingDecoration;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.text.CodePointCountFilter;
import me.vkryl.android.text.RestrictFilter;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.lambda.RunnableData;
import tgx.td.Td;
import tgx.td.TdConstants;

public class CreatePollController extends RecyclerViewController<CreatePollController.Args> implements View.OnClickListener, SettingsAdapter.TextChangeListener {
  public static class Args {
    public final long chatId;
    public final TdApi.MessageTopic messageTopicId;
    public final TdApi.InputSuggestedPostInfo inputSuggestedPostInfo;
    public final Callback callback;
    public final boolean forceRegular, forceQuiz;

    public Args (long chatId, @Nullable TdApi.MessageTopic messageTopicId, TdApi.InputSuggestedPostInfo inputSuggestedPostInfo, Callback callback) {
      this(chatId, messageTopicId, inputSuggestedPostInfo, callback, false, false);
    }

    public Args (long chatId, @Nullable TdApi.MessageTopic messageTopicId, TdApi.InputSuggestedPostInfo inputSuggestedPostInfo, Callback callback, boolean forceQuiz, boolean forceRegular) {
      if (callback == null)
        throw new IllegalArgumentException();
      this.chatId = chatId;
      this.messageTopicId = messageTopicId;
      this.inputSuggestedPostInfo = inputSuggestedPostInfo;
      this.callback = callback;
      this.forceRegular = forceRegular;
      this.forceQuiz = forceQuiz;
    }
  }

  public interface Callback {
    boolean onSendPoll (CreatePollController context, long chatId, @Nullable TdApi.MessageTopic topicId, TdApi.InputMessagePoll poll, TdApi.MessageSendOptions sendOptions, RunnableData<TdApi.Message> after);
    boolean areScheduledOnly (CreatePollController context);
    TdApi.ChatList provideChatList (CreatePollController context);
  }

  public CreatePollController (@NonNull Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_createPoll;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(isQuiz ? R.string.CreateQuiz : R.string.CreatePoll);
  }

  @Override
  public boolean passNameToHeader() {
    return true;
  }

  private SettingsAdapter adapter;
  private FillingDecoration decoration;
  private ListItem addItem, itemsListHint;
  private ListItem questionItem, descriptionItem, descriptionHintItem, explanationItem, explanationHintItem;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void modifyEditText (ListItem item, ViewGroup parent, MaterialEditTextGroup editText) {
        final int itemId = item.getId();
        if (itemId == R.id.text_subtitle || itemId == R.id.text_explanation) {
          boolean isExplanation = itemId == R.id.text_explanation;
          editText.addLengthCounter(false);
          editText.setEmptyHint(isExplanation ? R.string.QuizExplanationEmpty : R.string.PollDescriptionEmpty);
          editText.setHint(isExplanation ? R.string.QuizExplanation : R.string.PollDescription);
          editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
          Views.setSingleLine(editText.getEditText(), false);
          editText.setMaxLength(TdConstants.MAX_QUIZ_EXPLANATION_LENGTH);
          editText.setAlwaysActive(true);
          editText.getEditText().setLineDisabled(true);
        } else if (itemId == R.id.title) {
          editText.setEmptyHint(R.string.PollQuestionEmpty);
          editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
          Views.setSingleLine(editText.getEditText(), false);
          editText.addLengthCounter(false);
          editText.setMaxLength(TdConstants.MAX_POLL_QUESTION_LENGTH);
          editText.setAlwaysActive(true);
          editText.addCheckbox().setEnabled(true);
          editText.getEditText().setLineDisabled(true);
        } else if (itemId == R.id.optionAdd) {
          editText.setCheckboxVisible(isQuiz, !isMultiAnswers(),false);
          editText.addCheckbox().setEnabled(false);
          editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
          Views.setSingleLine(editText.getEditText(), false);
          editText.getEditText().setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_NEXT);
          editText.setNeedNextButton(v -> true);
        } else if (itemId == R.id.option) {
          editText.setCheckboxVisible(isQuiz,  !isMultiAnswers(),false);
          editText.setCheckboxSelected(correctOptionItems.contains(item), false);
          editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
          Views.setSingleLine(editText.getEditText(), false);
          editText.setAlwaysActive(true);
          editText.getEditText().setLineDisabled(true);
          editText.getEditText().setBackspaceListener((v, text, selectionStart, selectionEnd) -> {
            if (options.size() > 1 && (text.length() == 0 || text.toString().trim().isEmpty())) {
              removeOption((ListItem) ((ViewGroup) v.getParent().getParent()).getTag());
              return true;
            }
            return false;
          });
          editText.setNeedNextButton((v) -> {
            ListItem listItem = (ListItem) ((ViewGroup) v.getParent()).getTag();
            if (listItem != null && listItem.getId() == R.id.option) {
              int i = adapter.indexOfView(listItem);
              if (i != -1 && i + 2 < adapter.getItems().size()) {
                int id = adapter.getItems().get(i + 2).getId();
                return id == R.id.option || id == R.id.optionAdd;
              }
            }
            return false;
          });
          editText.getEditText().setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_NEXT);
        }
      }

      @Override
      protected void onEditTextCheckBoxClick (ListItem item, ViewGroup parent, MaterialEditTextGroup editText, CheckBoxView checkBoxVIew) {
        if (item.getId() == R.id.option) {
          toggleCorrectOptionEnabled(item);
        }
      }

      @Override
      public void onFocusChanged (MaterialEditTextGroup v, boolean isFocused) {
        super.onFocusChanged(v, isFocused);
        if (isFocused && ((ViewGroup) v.getParent()).getId() == R.id.optionAdd) {
          addOption();
        }
      }

      @Override
      protected void setValuedSetting(ListItem item, SettingView view, boolean isUpdate) {
        int itemId = item.getId();
        if (
          itemId == R.id.btn_pollSetting_duration ||
          itemId == R.id.btn_pollSetting_hideResults ||
          itemId == R.id.btn_pollSetting_multiAnswers ||
          itemId == R.id.btn_pollSetting_revoting ||
          itemId == R.id.btn_pollSetting_shuffle ||
          itemId == R.id.btn_pollSetting_members ||
          itemId == R.id.btn_pollSetting_countries ||
          itemId == R.id.btn_pollSetting_showVoters ||
          itemId == R.id.btn_pollSetting_quiz
        ) {
          view.getToggler().setRadioEnabled(item.getBoolValue(), isUpdate);
        } else if (
          itemId == R.id.btn_pollSetting_additions
        ) {
          view.getToggler().setRadioEnabled(!isQuiz && item.getBoolValue(), isUpdate);
        }

        if (itemId == R.id.btn_pollSetting_showVoters) {
          view.setData(item.getBoolValue() ? R.string.PollSettingShowVotersOn : R.string.PollSettingShowVotersOff);
        } else if (itemId == R.id.btn_pollSetting_multiAnswers) {
          view.setData(item.getBoolValue() ? R.string.PollSettingMultipleOn : R.string.PollSettingMultipleOff);
        } else if (itemId == R.id.btn_pollSetting_revoting) {
          view.setData(item.getBoolValue() ? R.string.PollSettingRevotingOn : R.string.PollSettingRevotingOff);
        } else if (itemId == R.id.btn_pollSetting_shuffle) {
          view.setData(item.getBoolValue() ? R.string.PollSettingShuffleOn : R.string.PollSettingShuffleOff);
        } else if (itemId == R.id.btn_pollSetting_members) {
          view.setData(item.getBoolValue() ? R.string.PollSettingMembersOn : R.string.PollSettingMembersOff);
        } else if (itemId == R.id.btn_pollSetting_countries) {
          if (item.getBoolValue()) {
            view.setData(Lang.getString(R.string.PollSettingCountriesOnSpecific, Strings.join(Lang.getConcatSeparator(), selectedCountries.values(), country -> Emoji.getEmojiFlagFromCountry(country.countryCode) + " " + country.countryCode)));
          } else {
            view.setData(item.getBoolValue() ? R.string.PollSettingCountriesOn : R.string.PollSettingCountriesOff);
          }
        } else if (itemId == R.id.btn_pollSetting_hideResults) {
          view.setData(item.getBoolValue() ? R.string.PollSettingHideResultsOn : R.string.PollSettingHideResultsOff);
        } else if (itemId == R.id.btn_pollSetting_quiz) {
          view.setEnabledAnimated(canChangePollType(), isUpdate);
          view.setData(item.getBoolValue() ? R.string.PollSettingQuizOn : R.string.PollSettingQuizOff);
        } else if (itemId == R.id.btn_pollSetting_additions) {
          view.setEnabledAnimated(!isQuiz, isUpdate);
          view.setData(!isQuiz && item.getBoolValue() ? R.string.PollSettingAddingOn : R.string.PollSettingAddingOff);
        }
      }
    };
    adapter.setTextChangeListener(this);

    List<ListItem> items = new ArrayList<>();

    decoration = new FillingDecoration(recyclerView, this) {
      @Override
      public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int i = parent.getChildAdapterPosition(view);
        outRect.top = view.getId() == R.id.text_subtitle || view.getId() == R.id.text_explanation ? Screen.dp(10f) : 0;
        outRect.bottom = i != -1 && i == adapter.getItemCount() - 1 ? Screen.dp(56f) : 0;
      }
    };
    decoration.addId(R.id.text_subtitle);
    decoration.addId(R.id.text_explanation);

    items.add(questionItem = new ListItem(ListItem.TYPE_EDITTEXT, R.id.title, 0, R.string.PollQuestion));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    decoration.addId(R.id.title);

    items.add(new ListItem(ListItem.TYPE_HEADER, R.id.text_title, 0, isQuiz ? R.string.QuizOptions : R.string.PollOptions));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));

    items.add(createNewOption());
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(addItem = new ListItem(ListItem.TYPE_EDITTEXT_POLL_OPTION_ADD, R.id.optionAdd).setInputFilters(new InputFilter[] {new CodePointCountFilter(0)}));
    decoration.addRange(items.size() - 3, items.size());

    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(itemsListHint = new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.plural(R.string.PollOptionsLimit, tdlib.options().pollAnswerCountMax - options.size()), false));

    final boolean isChannel = tdlib.isChannel(getArgumentsStrict().chatId);

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    int settingCount = 0;
    if (!isChannel) {
      items.add(settingShowVoters = new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER, R.id.btn_pollSetting_showVoters, 0, R.string.PollSettingShowVoters).setBoolValue(true));
      settingCount++;
    }

    if (!getArgumentsStrict().forceQuiz) {
      if (settingCount > 0)
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(settingMultiAnswers = new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER, R.id.btn_pollSetting_multiAnswers, 0, R.string.PollSettingMultiple));
      settingCount++;
    }

    if (!getArgumentsStrict().forceQuiz && !isChannel) {
      if (settingCount > 0)
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(settingAdditions = new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER, R.id.btn_pollSetting_additions, 0, R.string.PollSettingAdding));
      settingCount++;
    }

    if (settingCount > 0)
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(settingRevoting = new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER, R.id.btn_pollSetting_revoting, 0, R.string.PollSettingRevoting).setBoolValue(true));
    settingCount++;

    if (settingCount > 0)
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(settingQuiz = new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER, R.id.btn_pollSetting_quiz, 0, R.string.PollSettingQuiz));
    settingCount++;

    if (settingCount > 0)
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(settingShuffle = new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER, R.id.btn_pollSetting_shuffle, 0, R.string.PollSettingShuffle).setBoolValue(true));
    settingCount++;

    if (false) {
      // TODO: limit duration
      if (settingCount > 0)
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(settingDuration = new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER, R.id.btn_pollSetting_duration, 0, R.string.PollSettingDuration));
      settingCount++;
    }

    if (isChannel) {
      if (settingCount > 0)
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(settingOnlyMembers = new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER, R.id.btn_pollSetting_members, 0, R.string.PollSettingMembers));
      settingCount++;

      if (settingCount > 0)
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(settingCountries = new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER, R.id.btn_pollSetting_countries, 0, R.string.PollSettingCountries));
      settingCount++;
    }

    if (settingCount > 0)
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(settingHideResults = new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER, R.id.btn_pollSetting_hideResults, 0, R.string.PollSettingHideResults));
    settingCount++;

    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(descriptionItem = newDescriptionItem(false));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(descriptionHintItem = new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, isQuiz ? R.string.QuizDescriptionInfo : R.string.PollDescriptionInfo));

    if (isQuiz) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(explanationItem = newDescriptionItem(true));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(explanationHintItem = new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.QuizExplanationInfo));
    }

    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));

    adapter.setItems(items, false);
    adapter.setLockFocusOn(this, false);

    recyclerView.setAdapter(adapter);
    recyclerView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180l));
    recyclerView.addItemDecoration(decoration);
  }

  private HapticMenuHelper currentMenu;

  @Override
  public void onFocus () {
    super.onFocus();
    if (currentMenu == null) {
      currentMenu = tdlib.ui().createSimpleHapticMenu(this, getArgumentsStrict().chatId, this::canSendPoll, () -> {
        TdApi.FormattedText explanation = getExplanation(false);
        return explanation != null && explanation.text.trim().length() <= TdConstants.MAX_QUIZ_EXPLANATION_LENGTH && Td.parseMarkdown(explanation);
      }, null, null, this::send, null)
              .attachToView(getDoneButton());
    }
  }


  private boolean isQuiz;
  private ListItem settingQuiz, settingShowVoters, settingMultiAnswers, settingRevoting, settingShuffle, settingHideResults, settingAdditions, settingDuration, settingOnlyMembers, settingCountries;
  private Set<ListItem> correctOptionItems = new LinkedHashSet<>();

  private void toggleCorrectOptionEnabled (final ListItem option) {
    boolean wasEnabled = correctOptionItems.contains(option);
    boolean multiAnswers = isMultiAnswers();
    boolean nowEnabled = !multiAnswers || !wasEnabled;
    setCorrectOptionEnabled(option, nowEnabled);
  }

  private void setCorrectOptionEnabled (final ListItem option, boolean nowEnabled) {
    boolean wasEnabled = correctOptionItems.contains(option);
    if (wasEnabled == nowEnabled) {
      return;
    }
    boolean multiAnswers = isMultiAnswers();
    if (!multiAnswers && nowEnabled) {
      while (!correctOptionItems.isEmpty()) {
        ListItem item = correctOptionItems.iterator().next();
        correctOptionItems.remove(item);
        setCheckboxActive(item, false);
      }
    }
    if (nowEnabled) {
      correctOptionItems.add(option);
    } else {
      correctOptionItems.remove(option);
    }
    setCheckboxActive(option, nowEnabled);
    checkSend();
  }

  private void setCheckboxActive (ListItem item, boolean isActive) {
    int adapterPosition = adapter.indexOfView(item);
    if (adapterPosition == -1) {
      return;
    }
    View view = getRecyclerView().getLayoutManager().findViewByPosition(adapterPosition);
    if (view != null && view.getTag() == item) {
      ((MaterialEditTextGroup) ((ViewGroup) view).getChildAt(0)).setCheckboxSelected(isActive, true);
    } else {
      adapter.notifyItemChanged(adapterPosition);
    }
  }

  @Override
  public void setArguments(Args args) {
    super.setArguments(args);
    this.isQuiz = args.forceQuiz;
  }

  private boolean canChangePollType () {
    return !(getArgumentsStrict().forceQuiz || getArgumentsStrict().forceRegular);
  }

  @Override
  public boolean performOnBackPressed (boolean fromTop, boolean commit) {
    if (hasUnsavedPoll()) {
      if (commit) {
        showOptions(Lang.getString(isQuiz ? R.string.QuizDiscardPrompt : R.string.PollDiscardPrompt), new int[] {R.id.btn_done, R.id.btn_cancel}, new String[] {Lang.getString(isQuiz ? R.string.QuizDiscard : R.string.PollDiscard), Lang.getString(R.string.Cancel)}, new int[] {OptionColor.RED, OptionColor.NORMAL}, new int[] {R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
          if (id == R.id.btn_done) {
            navigateBack();
          }
          return true;
        });
      }
      return true;
    }
    return super.performOnBackPressed(fromTop, commit);
  }

  private static ListItem newDescriptionItem (boolean isQuizExplanation) {
    return new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING_REUSABLE, isQuizExplanation ? R.id.text_explanation : R.id.text_subtitle, 0, R.string.PollDescription);
  }

  private final Map<String, TdApi.CountryInfo> selectedCountries = new LinkedHashMap<>();

  @Override
  public void onClick (View v) {
    int itemId = v.getId();
    ListItem item = (ListItem) v.getTag();

    if (itemId == R.id.optionAdd) {
      addOption();
    } else if (itemId == R.id.btn_pollSetting_countries) {
      // Open country picker
      CountryController c = new CountryController(context, tdlib);
      c.setArguments(new CountryController.Args(new CountryController.Callback() {
        @Override
        public void onCountryUnselected (CountryController context, View view, TdApi.CountryInfo country) {
          selectedCountries.remove(country.countryCode);
          settingCountries.setBoolValue(!selectedCountries.isEmpty());
          adapter.updateValuedSettingById(R.id.btn_pollSetting_countries);
        }

        @Override
        public boolean onCountrySelected (CountryController context, View view, TdApi.CountryInfo country) {
          if (selectedCountries.size() >= tdlib.options().pollCountryCountMax) {
            context.showErrorTooltip(view, Lang.getMarkdownPlural(CreatePollController.this, R.string.CountryLimit, tdlib.options().pollCountryCountMax));
            return false;
          }
          selectedCountries.put(country.countryCode, country);
          settingCountries.setBoolValue(true);
          adapter.updateValuedSettingById(R.id.btn_pollSetting_countries);
          return true;
        }
      }, selectedCountries.values().toArray(new TdApi.CountryInfo[0])));
      navigateTo(c);
    } else if (
      itemId == R.id.btn_pollSetting_duration ||
      itemId == R.id.btn_pollSetting_hideResults ||
      itemId == R.id.btn_pollSetting_members ||
      itemId == R.id.btn_pollSetting_multiAnswers ||
      itemId == R.id.btn_pollSetting_shuffle ||
      itemId == R.id.btn_pollSetting_revoting ||
      itemId == R.id.btn_pollSetting_showVoters ||
      (itemId == R.id.btn_pollSetting_quiz && canChangePollType()) ||
      (itemId == R.id.btn_pollSetting_additions && !isQuiz)
    ) {
      if (!item.getBoolValue() && itemId == R.id.btn_pollSetting_duration) {
        long maxCloseDate = tdlib.currentTime(TimeUnit.SECONDS) + tdlib.options().pollOpenPeriodMax;

        // TODO Open up duration picker

        return;
      }

      boolean value = adapter.toggleView(v);

      item.setBoolValue(value);
      adapter.updateValuedSetting(item);

      if (itemId == R.id.btn_pollSetting_multiAnswers) {
        if (!value) {
          while (correctOptionItems.size() > 1) {
            setCorrectOptionEnabled(correctOptionItems.iterator().next(), false);
          }
        }
        updateCheckBoxes();
      } else if (itemId == R.id.btn_pollSetting_quiz) {
        isQuiz = value;

        if (value) {
          // Add items
          int index = adapter.indexOfView(descriptionItem);
          if (index != -1) {
            index = Math.max(0, index - 1);
            adapter.addItems(index,
              new ListItem(ListItem.TYPE_SHADOW_TOP),
              explanationItem != null ? explanationItem : (explanationItem = newDescriptionItem(true)),
              new ListItem(ListItem.TYPE_SHADOW_BOTTOM),
              explanationHintItem != null ? explanationHintItem : (explanationHintItem = new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.QuizExplanationInfo))
            );
          }
        } else {
          // Remove items
          int index = adapter.indexOfView(explanationItem);
          if (index != -1) {
            adapter.removeRange(index - 1, 4);
          }
        }

        if (isQuiz) {
          // Show hint
          int i = ((LinearLayoutManager) getRecyclerView().getLayoutManager()).findFirstVisibleItemPosition();
          if (i != RecyclerView.NO_POSITION) {
            i = adapter.indexOfViewById(R.id.option, i);
            i = i != -1 ? options.indexOf(adapter.getItem(i)) : -1;
            if (i != -1) {
              int firstVisibleOptionId = i;
              while (i < options.size() && StringUtils.isEmpty(StringUtils.trim(options.get(i).getCharSequenceValue()))) {
                i++;
              }
              if (i != options.size()) {
                firstVisibleOptionId = i;
              }
              i = adapter.indexOfView(options.get(firstVisibleOptionId));
              if (i != -1) {
                View view = getRecyclerView().getLayoutManager().findViewByPosition(i);
                if (view instanceof ViewGroup && view.getTag() == options.get(firstVisibleOptionId)) {
                  MaterialEditTextGroup editTextGroup = ((MaterialEditTextGroup) ((ViewGroup) view).getChildAt(0));
                  editTextGroup.showCheckboxHint(this, tdlib, R.string.QuizOptionHint);
                }
              }
            }
          }
        }

        if (settingAdditions != null) {
          adapter.updateValuedSetting(settingAdditions);
        }

        if (settingRevoting != null) {
          settingRevoting.setBoolValue(!value);
          adapter.updateValuedSetting(settingRevoting);
        }

        int titleIndex = adapter.indexOfViewById(R.id.text_title);
        if (titleIndex != -1 && adapter.getItems().get(titleIndex).setStringIfChanged(isQuiz ? R.string.QuizOptions : R.string.PollOptions)) {
          adapter.notifyItemChanged(titleIndex);
        }

        int descHintIndex = adapter.indexOfView(descriptionHintItem);
        if (descHintIndex != -1 && descriptionHintItem.setStringIfChanged(isQuiz ? R.string.QuizDescriptionInfo : R.string.PollDescriptionInfo)) {
          adapter.notifyItemChanged(descHintIndex);
        }

        descHintIndex = adapter.indexOfView(descriptionItem);
        if (descHintIndex != -1 && descriptionItem.setStringIfChanged(isQuiz ? R.string.QuizExplanation : R.string.PollDescription)) {
          adapter.updateValuedSetting(descriptionItem);
        }

        if (headerView != null)
          headerView.updateTextTitle(getId(), getName());

        checkSend();

        updateCheckBoxes();
      }
    }
  }

  private void updateCheckBoxes () {
    int position = 0;
    for (ListItem otherItem : adapter.getItems()) {
      int otherItemId = otherItem.getId();
      if (otherItemId == R.id.option || otherItemId == R.id.optionAdd) {
        View view = getRecyclerView().getLayoutManager().findViewByPosition(position);
        if (view != null && view.getTag() == otherItem) {
          MaterialEditTextGroup editTextGroup = (MaterialEditTextGroup) ((ViewGroup) view).getChildAt(0);
          editTextGroup.setCheckboxVisible(isQuiz, !isMultiAnswers(), true);
        } else {
          adapter.notifyItemChanged(position);
        }
      }
      position++;
    }
  }

  @Override
  public int getRootColorId () {
    return ColorId.background;
  }

  private boolean isAdding;

  private boolean addOption () {
    final int maxPollOptionCount = tdlib.options().pollAnswerCountMax;
    if (options.size() >= maxPollOptionCount || isAdding)
      return false;
    int i = adapter.indexOfViewById(R.id.optionAdd);
    isAdding = true;
    int targetIndex;
    if (options.size() + 1 == maxPollOptionCount) {
      adapter.setItem(targetIndex = i, createNewOption());
    } else {
      adapter.getItems().add(i - 1, createNewOption());
      adapter.getItems().add(i - 1, new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      decoration.lastRange()[1] += 2;
      targetIndex = i;
      adapter.notifyItemRangeInserted(i, 2);
      // getRecyclerView().invalidateItemDecorations();
    }
    updateLimit();
    tdlib.ui().postDelayed(() -> {
      isAdding = false;
      View view = getRecyclerView().getLayoutManager().findViewByPosition(targetIndex);
      if (view instanceof ViewGroup) {
        Keyboard.show(((MaterialEditTextGroup) ((ViewGroup) view).getChildAt(0)).getEditText());
      }
    }, 180);
    return true;
  }

  private void removeOption (ListItem option) {
    final int i = options.indexOf(option);
    if (i == -1)
      return;
    setCorrectOptionEnabled(option, false);
    options.remove(i);
    boolean hideKeyboard = true;
    if (!options.isEmpty()) {
      int nextIndex = adapter.indexOfView(options.get(i > 0 ? i - 1 : 0));
      View view = getRecyclerView().getLayoutManager().findViewByPosition(nextIndex);
      if (view instanceof ViewGroup) {
        Keyboard.show(((MaterialEditTextGroup) ((ViewGroup) view).getChildAt(0)).getEditText());
        hideKeyboard = false;
      }
    }
    int adapterPosition = adapter.indexOfView(option);
    if (adapterPosition == -1)
      throw new AssertionError();
    if (options.size() == tdlib.options().pollAnswerCountMax - 1) {
      if (i == options.size()) {
        adapter.setItem(adapterPosition, addItem);
      } else {
        adapter.removeRange(adapterPosition, 2);
        int lastAdapterPosition = adapter.indexOfView(options.get(options.size() - 1));
        adapter.getItems().add(lastAdapterPosition + 1, addItem);
        adapter.getItems().add(lastAdapterPosition + 1, new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        adapter.notifyItemRangeInserted(lastAdapterPosition + 1, 2);
      }
    } else {
      decoration.lastRange()[1] -= 2;
      adapter.removeRange(adapterPosition, 2);
    }
    if (hideKeyboard) {
      hideSoftwareKeyboard();
    }
    updateLimit();
  }

  @Override
  public void onTextChanged (int id, ListItem item, MaterialEditTextGroup v) {
    checkSend();
  }

  private void updateLimit () {
    final int maxPollOptionCount = tdlib.options().pollAnswerCountMax;
    if (itemsListHint.setStringIfChanged(maxPollOptionCount <= options.size() ? Lang.getString(R.string.PollOptionsMax) : Lang.plural(R.string.PollOptionsLimit, maxPollOptionCount - options.size()))) {
      adapter.updateValuedSetting(itemsListHint);
    }
  }

  private final List<ListItem> options = new ArrayList<>();

  private ListItem createNewOption () {
    ListItem option = new ListItem(ListItem.TYPE_EDITTEXT_POLL_OPTION, R.id.option).setInputFilters(new InputFilter[] {
      new CodePointCountFilter(TdConstants.MAX_POLL_OPTION_LENGTH),
      new CharacterStyleFilter(),
      new RestrictFilter(new char[] {'\n'})
    }).setOnEditorActionListener(new EditBaseController.SimpleEditorActionListener(EditorInfo.IME_ACTION_NEXT, v -> addOption()));
    this.options.add(option);
    return option;
  }

  private boolean canSendPoll () {
    if (isQuiz && correctOptionItems.isEmpty())
      return false;
    CharSequence title = StringUtils.trim(questionItem.getCharSequenceValue());
    if (StringUtils.isEmpty(title))
      return false;
    if (isQuiz) {
      TdApi.FormattedText formattedText = getExplanation(false);
      if (formattedText != null && (Td.parseMarkdown(formattedText) ? formattedText.text.length() : formattedText.text.trim().length()) > TdConstants.MAX_QUIZ_EXPLANATION_LENGTH)
        return false;
    }
    int count = 0;
    int correctOptionCount = 0;
    for (ListItem optionItem : options) {
      CharSequence option = StringUtils.trim(optionItem.getCharSequenceValue());
      if (!StringUtils.isEmpty(option)) {
        if (correctOptionItems.contains(optionItem))
          correctOptionCount++;
        count++;
      }
    }
    boolean multiAnswers = isMultiAnswers();
    return count > 1 && !isQuiz || (correctOptionCount == 1) || (correctOptionCount > 1 && multiAnswers);
  }

  private boolean isMultiAnswers () {
    return settingMultiAnswers != null && settingMultiAnswers.getBoolValue();
  }

  private boolean hasUnsavedPoll () {
    CharSequence title = StringUtils.trim(questionItem.getCharSequenceValue());
    if (!StringUtils.isEmpty(title))
      return true;
    if (isQuiz && !Td.isEmpty(getExplanation(false)))
      return true;
    for (ListItem optionItem : options) {
      if (!StringUtils.isEmptyOrBlank(optionItem.getCharSequenceValue())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean canSlideBackFrom (NavigationController navigationController, float x, float y) {
    return !hasUnsavedPoll() && !getDoneButton().isInProgress();
  }

  private void checkSend () {
    boolean canSendPoll = canSendPoll();
    if (canSendPoll) {
      getDoneButton().setIcon(R.drawable.deproko_baseline_send_24, Screen.dp(1.5f));
    } else if (currentMenu != null) {
      currentMenu.hideMenu();
    }
    setDoneVisible(canSendPoll, true);
  }

  private void requestFocus (ListItem item) {
    int i = adapter.indexOfView(item);
    View view = getRecyclerView().getLayoutManager().findViewByPosition(i);
    if (view instanceof ViewGroup) {
      Keyboard.show(((MaterialEditTextGroup) ((ViewGroup) view).getChildAt(0)).getEditText());
    }
  }

  @Override
  protected void onDoneClick () {
    send(Td.newSendOptions(), false);
  }

  @Nullable
  private TdApi.FormattedText getExplanation (boolean parseMarkdown) {
    return isQuiz ? getFormattedText(explanationItem, parseMarkdown) : null;
  }

  @Nullable
  private TdApi.FormattedText getDescription (boolean parseMarkdown) {
    return getFormattedText(descriptionItem, parseMarkdown);
  }

  @Nullable
  private TdApi.FormattedText getFormattedText (ListItem item, boolean parseMarkdown) {
    CharSequence explanationText = item != null ? item.getCharSequenceValue() : null;
    if (!StringUtils.isEmpty(explanationText)) {
      TdApi.FormattedText explanation = TD.toFormattedText(explanationText, false);
      if (parseMarkdown)
        Td.parseMarkdown(explanation);
      return explanation;
    }
    return null;
  }

  private void send (TdApi.MessageSendOptions sendOptions, boolean disableMarkdown) {
    if (getDoneButton().isInProgress())
      return;
    TdApi.FormattedText question = TD.toFormattedText(StringUtils.trim(questionItem.getCharSequenceValue()), false);
    if (Td.isEmpty(question) || Td.codePointCount(question) > TdConstants.MAX_POLL_QUESTION_LENGTH) {
      requestFocus(questionItem);
      return;
    }
    boolean hasCustomEmoji = TD.hasCustomEmoji(question);
    IntList correctOptionIds = new IntList(tdlib.options().pollAnswerCountMax);
    List<TdApi.InputPollOption> options = new ArrayList<>(tdlib.options().pollAnswerCountMax);
    for (ListItem optionItem : this.options) {
      CharSequence cs = StringUtils.trim(optionItem.getCharSequenceValue());
      if (StringUtils.isEmpty(cs))
        continue;
      TdApi.FormattedText option = TD.toFormattedText(cs, false);
      if (Td.isEmpty(option))
        continue;
      if (Td.codePointCount(option) > TdConstants.MAX_POLL_OPTION_LENGTH) {
        requestFocus(optionItem);
        return;
      }
      if (!hasCustomEmoji && TD.hasCustomEmoji(option)) {
        hasCustomEmoji = true;
      }
      if (correctOptionItems.contains(optionItem)) {
        int correctOptionId = options.size();
        correctOptionIds.append(correctOptionId);
      }
      options.add(new TdApi.InputPollOption(option, null));
    }
    if (options.size() < 2)
      return;

    if (hasCustomEmoji && !tdlib.hasPremium()) {
      tdlib.ui().showPremiumAlert(this, getDoneButton(), TdlibUi.PremiumFeature.CUSTOM_EMOJI);
      return;
    }

    Args args = getArgumentsStrict();
    final long chatId = args.chatId;
    final TdApi.MessageTopic messageTopicId = args.messageTopicId;
    final TdApi.InputSuggestedPostInfo suggestedPostInfo = args.inputSuggestedPostInfo;
    if (sendOptions.schedulingState == null && args.callback.areScheduledOnly(this)) {
      tdlib.ui().showScheduleOptions(this, chatId, false, (modifiedSendOptions, disableMarkdown1) -> send(modifiedSendOptions, disableMarkdown), sendOptions, null);
      return;
    }

    final CharSequence slowModeRestrictionText = tdlib.getSlowModeRestrictionText(chatId, sendOptions.schedulingState);
    if (slowModeRestrictionText != null) {
      context().tooltipManager().builder(getDoneButton()).controller(this).show(tdlib, slowModeRestrictionText).hideDelayed();
      return;
    }

    TdApi.FormattedText description = getDescription(!disableMarkdown);

    // @param openPeriod Amount of time the poll will be active after creation, in seconds; 0-getOption(&quot;poll_open_period_max&quot;); pass 0 if not specified.
    // @param closeDate Point in time (Unix timestamp) when the poll will automatically be closed; must be 0-getOption(&quot;poll_open_period_max&quot;) seconds in the future; pass 0 if not specified.
    // The list of two-letter ISO 3166-1 alpha-2 codes of countries, users from which will be able to vote; for channel chats only. If empty, then all users can participate in the poll. There can be up to getOption(&quot;poll_country_count_max&quot;) chosen countries.
    int openPeriod = 0;
    int closeDate = 0;

    // TODO lock texts
    getDoneButton().setInProgress(true);
    hideSoftwareKeyboard();

    TdApi.InputPollOption[] optionsArray = options.toArray(new TdApi.InputPollOption[0]);
    TdApi.InputPollType inputPollType = isQuiz ?
      new TdApi.InputPollTypeQuiz(correctOptionIds.get(), getExplanation(!disableMarkdown), null) :
      new TdApi.InputPollTypeRegular(settingAdditions != null && settingAdditions.getBoolValue());
    TdApi.InputMessagePoll poll = new TdApi.InputMessagePoll(
      question,
      optionsArray,
      description,
      null,
      settingShowVoters == null || !settingShowVoters.getBoolValue(),
      isMultiAnswers(),
      settingRevoting != null && settingRevoting.getBoolValue(),
      settingOnlyMembers != null && settingOnlyMembers.getBoolValue(),
      settingCountries != null && settingCountries.getBoolValue() ? selectedCountries.keySet().toArray(new String[0]) : new String[0],
      settingShuffle != null && settingShuffle.getBoolValue(),
      settingHideResults != null && settingHideResults.getBoolValue(),
      inputPollType,
      openPeriod,
      closeDate,
      false
    );

    RunnableData<TdApi.Message> after = message -> runOnUiThreadOptional(() -> {
      getDoneButton().setInProgress(false);
      if (message != null) {
        if (sendOptions.schedulingState != null && !args.callback.areScheduledOnly(this)) {
          NavigationStack stack = navigationStack();
          if (stack != null) {
            MessagesController c = new MessagesController(context, tdlib);
            c.setArguments(new MessagesController.Arguments(args.callback.provideChatList(this), tdlib.chatStrict(args.chatId), /* messageThread */ null, messageTopicId, null, MessagesManager.HIGHLIGHT_MODE_NONE, null).setScheduled(true));
            stack.insertBack(c);
          }
        }
        navigateBack();
      }
    });
    final TdApi.MessageSendOptions finalSendOptions = Td.newSendOptions(sendOptions, suggestedPostInfo, tdlib.chatDefaultDisableNotifications(chatId));
    if (!getArgumentsStrict().callback.onSendPoll(this, chatId, messageTopicId, poll, finalSendOptions, after)) {
      tdlib.sendMessage(chatId, messageTopicId, null, finalSendOptions, poll, after);
    }
  }
}
