/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
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
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.ThreadInfo;
import org.thunderdog.challegram.emoji.EmojiFilter;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.NavigationStack;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.CharacterStyleFilter;
import org.thunderdog.challegram.util.HapticMenuHelper;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.v.EditText;
import org.thunderdog.challegram.widget.FillingDecoration;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;
import org.thunderdog.challegram.widget.RadioView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.text.CodePointCountFilter;
import me.vkryl.android.text.RestrictFilter;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.td.Td;
import me.vkryl.td.TdConstants;

public class CreatePollController extends RecyclerViewController<CreatePollController.Args> implements View.OnClickListener, SettingsAdapter.TextChangeListener {
  public static class Args {
    public final long chatId;
    public final ThreadInfo messageThread;
    public final Callback callback;
    public final boolean forceRegular, forceQuiz;

    public Args (long chatId, ThreadInfo threadInfo, Callback callback) {
      this(chatId, threadInfo, callback, false, false);
    }

    public Args (long chatId, ThreadInfo threadInfo, Callback callback, boolean forceQuiz, boolean forceRegular) {
      if (callback == null)
        throw new IllegalArgumentException();
      this.chatId = chatId;
      this.messageThread = threadInfo;
      this.callback = callback;
      this.forceRegular = forceRegular;
      this.forceQuiz = forceQuiz;
    }
  }

  public interface Callback {
    boolean onSendPoll (CreatePollController context, long chatId, long messageThreadId, TdApi.InputMessagePoll poll, TdApi.MessageSendOptions sendOptions, RunnableData<TdApi.Message> after);
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
  private ListItem addItem, descriptionItem;
  private ListItem questionItem, explanationItem;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void modifyEditText (ListItem item, ViewGroup parent, MaterialEditTextGroup editText) {
        switch (item.getId()) {
          case R.id.text_subtitle: {
            editText.addLengthCounter(false);
            editText.setEmptyHint(R.string.QuizExplanationEmpty);
            editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            Views.setSingleLine(editText.getEditText(), false);
            editText.setMaxLength(TdConstants.MAX_QUIZ_EXPLANATION_LENGTH);
            editText.setAlwaysActive(true);
            editText.getEditText().setLineDisabled(true);
            break;
          }
          case R.id.title: {
            editText.setEmptyHint(R.string.PollQuestionEmpty);
            editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            Views.setSingleLine(editText.getEditText(), false);
            editText.setMaxLength(TdConstants.MAX_POLL_QUESTION_LENGTH);
            editText.setAlwaysActive(true);
            editText.getEditText().setLineDisabled(true);
            break;
          }
          case R.id.optionAdd: {
            editText.setRadioVisible(isQuiz, false);
            editText.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            Views.setSingleLine(editText.getEditText(), false);
            editText.getEditText().setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_NEXT);
            editText.setNeedNextButton(v -> true);
            break;
          }
          case R.id.option: {
            editText.setRadioVisible(isQuiz, false);
            editText.setRadioActive(item == correctOptionItem, false);
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
                  switch (id) {
                    case R.id.option:
                    case R.id.optionAdd:
                      return true;
                  }
                }
              }
              return false;
            });
            editText.getEditText().setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_NEXT);
            break;
          }
        }
      }

      @Override
      protected void onEditTextRadioClick (ListItem item, ViewGroup parent, MaterialEditTextGroup editText, RadioView radioView) {
        if (item.getId() == R.id.option) {
          setCorrectOption(item);
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
        switch (item.getId()) {
          case R.id.btn_pollSetting_anonymous: {
            view.getToggler().setRadioEnabled(isAnonymousVoting, isUpdate);
            break;
          }
          case R.id.btn_pollSetting_multi: {
            view.getToggler().setRadioEnabled(isMultiChoiceVote, isUpdate);
            view.setEnabledAnimated(!isQuiz, isUpdate);
            break;
          }
          case R.id.btn_pollSetting_quiz: {
            view.getToggler().setRadioEnabled(isQuiz, isUpdate);
            view.setEnabledAnimated(canChangePollType(), isUpdate);
            break;
          }
        }
      }
    };
    adapter.setTextChangeListener(this);

    List<ListItem> items = new ArrayList<>();

    decoration = new FillingDecoration(recyclerView, this) {
      @Override
      public void getItemOffsets (@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int i = parent.getChildAdapterPosition(view);
        outRect.top = view.getId() == R.id.text_subtitle ? Screen.dp(10f) : 0;
        outRect.bottom = i != -1 && i == adapter.getItemCount() - 1 ? Screen.dp(56f) : 0;
      }
    };

    items.add(questionItem = new ListItem(ListItem.TYPE_EDITTEXT_COUNTERED, R.id.title, 0, R.string.PollQuestion));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    decoration.addRange(0, 1);

    items.add(new ListItem(ListItem.TYPE_HEADER, R.id.text_title, 0, isQuiz ? R.string.QuizOptions : R.string.PollOptions));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));

    items.add(createNewOption());
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(addItem = new ListItem(ListItem.TYPE_EDITTEXT_POLL_OPTION_ADD, R.id.optionAdd).setInputFilters(new InputFilter[] {new CodePointCountFilter(0)}));
    decoration.addRange(items.size() - 3, items.size());

    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(descriptionItem = new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.plural(R.string.PollOptionsLimit, TdConstants.MAX_POLL_OPTION_COUNT - options.size()), false));

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    int settingCount = 0;
    if (!tdlib.isChannel(getArgumentsStrict().chatId)) {
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_pollSetting_anonymous, 0, R.string.PollSettingAnonymous));
      settingCount++;
    } else {
      isAnonymousVoting = true;
    }
    if (!getArgumentsStrict().forceQuiz) {
      if (settingCount > 0)
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_pollSetting_multi, 0, R.string.PollSettingMultiple));
      settingCount++;
    }
    if (settingCount > 0)
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_pollSetting_quiz, 0, R.string.PollSettingQuiz));
    settingCount++;
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.PollSettingQuizInfo));

    if (isQuiz) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(explanationItem = newExplanationItem());
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0,0, R.string.QuizExplanationInfo));
      decoration.addRange(items.size() - 3, items.size() - 2);
    }

    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));

    adapter.setItems(items, false);
    adapter.setLockFocusOn(this, true);

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
      }, null, this::send, null)
              .attachToView(getDoneButton());
    }
  }

  private boolean isAnonymousVoting, isMultiChoiceVote, isQuiz;
  private ListItem correctOptionItem;

  private void setCorrectOption (final ListItem option) {
    if (this.correctOptionItem != option) {
      ListItem oldOptionItem = this.correctOptionItem;
      if (oldOptionItem != null) {
        setRadioActive(oldOptionItem, false);
      }
      this.correctOptionItem = option;
      if (option != null) {
        setRadioActive(option, true);
      }
      checkSend();
    }
  }

  private void setRadioActive (ListItem item, boolean isActive) {
    int adapterPosition = adapter.indexOfView(item);
    if (adapterPosition == -1) {
      return;
    }
    View view = getRecyclerView().getLayoutManager().findViewByPosition(adapterPosition);
    if (view != null && view.getTag() == item) {
      ((MaterialEditTextGroup) ((ViewGroup) view).getChildAt(0)).setRadioActive(isActive, true);
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
  public boolean onBackPressed (boolean fromTop) {
    if (hasUnsavedPoll()) {
      showOptions(Lang.getString(isQuiz ? R.string.QuizDiscardPrompt : R.string.PollDiscardPrompt), new int[] {R.id.btn_done, R.id.btn_cancel}, new String[] {Lang.getString(isQuiz ? R.string.QuizDiscard : R.string.PollDiscard), Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
        if (id == R.id.btn_done) {
          navigateBack();
        }
        return true;
      });
      return true;
    }
    return super.onBackPressed(fromTop);
  }

  private static ListItem newExplanationItem () {
    return new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING_REUSABLE, R.id.text_subtitle, 0, R.string.QuizExplanation);
  }

  @Override
  public void onClick (View v) {
    switch (v.getId()) {
      case R.id.optionAdd: {
        addOption();
        break;
      }
      case R.id.btn_pollSetting_anonymous: {
        isAnonymousVoting = adapter.toggleView(v);
        break;
      }
      case R.id.btn_pollSetting_multi: {
        isMultiChoiceVote = adapter.toggleView(v);
        break;
      }
      case R.id.btn_pollSetting_quiz: {
        if (canChangePollType()) {
          isQuiz = adapter.toggleView(v);

          int explanationIndex = adapter.indexOfViewById(R.id.text_subtitle);
          if (isQuiz) {
            // Show hint

            int i = ((LinearLayoutManager) getRecyclerView().getLayoutManager()).findFirstVisibleItemPosition();
            if (i != RecyclerView.NO_POSITION) {
              i = adapter.indexOfViewById(R.id.option, i);
              i = i != -1 ? options.indexOf(adapter.getItem(i)) : -1;
              if (i != -1) {
                int firstVisibleOptionId = i;
                while (i < options.size() && StringUtils.isEmpty(StringUtils.trim(options.get(i).getStringValue()))) {
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
                    editTextGroup.showRadioHint(this, tdlib, R.string.QuizOptionHint);
                  }
                }
              }
            }

            // Add explanation item
            if (explanationIndex == -1) {
              if (explanationItem == null)
                explanationItem = newExplanationItem();
              int index = adapter.getItems().size();
              adapter.addItems(index - 1,
                new ListItem(ListItem.TYPE_SHADOW_TOP),
                explanationItem,
                new ListItem(ListItem.TYPE_SHADOW_BOTTOM),
                new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.QuizExplanationInfo)
              );
              decoration.addRange(index, index + 1);
              getRecyclerView().invalidateItemDecorations();
              // adapter.notifyItemChanged(index - 2);
            }
          } else {
            // Remove explanation item
            if (explanationIndex != -1) {
              decoration.removeLastRange();
              adapter.removeRange(explanationIndex - 1, 4);
              getRecyclerView().invalidateItemDecorations();
              // adapter.notifyItemChanged(explanationIndex - 2);
            }
          }

          int titleIndex = adapter.indexOfViewById(R.id.text_title);
          if (titleIndex != -1 && adapter.getItems().get(titleIndex).setStringIfChanged(isQuiz ? R.string.QuizOptions : R.string.PollOptions)) {
            adapter.notifyItemChanged(titleIndex);
          }

          if (isQuiz) {
            isMultiChoiceVote = false;
          }
          adapter.updateValuedSettingById(R.id.btn_pollSetting_multi);

          if (headerView != null)
            headerView.updateTextTitle(getId(), getName());

          checkSend();

          int position = 0;
          for (ListItem item : adapter.getItems()) {
            switch (item.getId()) {
              case R.id.option:
              case R.id.optionAdd: {
                View view = getRecyclerView().getLayoutManager().findViewByPosition(position);
                if (view != null && view.getTag() == item) {
                  ((MaterialEditTextGroup) ((ViewGroup) view).getChildAt(0)).setRadioVisible(isQuiz, true);
                } else {
                  adapter.notifyItemChanged(position);
                }
                break;
              }
            }
            position++;
          }
        }
        break;
      }
    }
  }

  @Override
  public int getRootColorId () {
    return R.id.theme_color_background;
  }

  private boolean isAdding;

  private boolean addOption () {
    if (options.size() >= TdConstants.MAX_POLL_OPTION_COUNT || isAdding)
      return false;
    int i = adapter.indexOfViewById(R.id.optionAdd);
    isAdding = true;
    int targetIndex;
    if (options.size() + 1 == TdConstants.MAX_POLL_OPTION_COUNT) {
      adapter.setItem(targetIndex = i, createNewOption());
    } else {
      adapter.getItems().add(i - 1, createNewOption());
      adapter.getItems().add(i - 1, new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      decoration.rangeAt(1)[1] += 2;
      if (isQuiz) {
        decoration.lastRange()[0] += 2;
        decoration.lastRange()[1] += 2;
      }
      targetIndex = i;
      adapter.notifyItemRangeInserted(i, 2);
      getRecyclerView().invalidateItemDecorations();
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
    if (correctOptionItem == option) {
      correctOptionItem = null;
    }
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
    if (options.size() == TdConstants.MAX_POLL_OPTION_COUNT - 1) {
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
      decoration.rangeAt(1)[1] -= 2;
      if (isQuiz) {
        decoration.lastRange()[0] -= 2;
        decoration.lastRange()[1] -= 2;
      }
      adapter.removeRange(adapterPosition, 2);
      getRecyclerView().invalidateItemDecorations();
    }
    if (hideKeyboard) {
      hideSoftwareKeyboard();
    }
    updateLimit();
  }

  @Override
  public void onTextChanged (int id, ListItem item, MaterialEditTextGroup v, String text) {
    checkSend();
  }

  private void updateLimit () {
    if (descriptionItem.setStringIfChanged(TdConstants.MAX_POLL_OPTION_COUNT <= options.size() ? Lang.getString(R.string.PollOptionsMax) : Lang.plural(R.string.PollOptionsLimit, TdConstants.MAX_POLL_OPTION_COUNT - options.size()))) {
      adapter.updateValuedSetting(descriptionItem);
    }
  }

  private List<ListItem> options = new ArrayList<>();

  private ListItem createNewOption () {
    ListItem option = new ListItem(ListItem.TYPE_EDITTEXT_POLL_OPTION, R.id.option).setInputFilters(new InputFilter[] {
      new CodePointCountFilter(TdConstants.MAX_POLL_OPTION_LENGTH),
      new EmojiFilter(),
      new CharacterStyleFilter(),
      new RestrictFilter(new char[] {'\n'})
    }).setOnEditorActionListener(new EditBaseController.SimpleEditorActionListener(EditorInfo.IME_ACTION_NEXT, v -> addOption()));
    this.options.add(option);
    return option;
  }

  private boolean canSendPoll () {
    if (isQuiz && correctOptionItem == null)
      return false;
    String title = StringUtils.trim(questionItem.getStringValue());
    if (StringUtils.isEmpty(title))
      return false;
    if (isQuiz) {
      TdApi.FormattedText formattedText = getExplanation(false);
      if (formattedText != null && (Td.parseMarkdown(formattedText) ? formattedText.text.length() : formattedText.text.trim().length()) > TdConstants.MAX_QUIZ_EXPLANATION_LENGTH)
        return false;
    }
    int count = 0;
    boolean foundCorrectOption = !isQuiz;
    for (ListItem optionItem : options) {
      String option = StringUtils.trim(optionItem.getStringValue());
      if (!StringUtils.isEmpty(option)) {
        if (correctOptionItem == optionItem)
          foundCorrectOption = true;
        count++;
      }
    }
    return foundCorrectOption && count > 1;
  }

  private boolean hasUnsavedPoll () {
    String title = StringUtils.trim(questionItem.getStringValue());
    if (!StringUtils.isEmpty(title))
      return true;
    if (isQuiz && !Td.isEmpty(getExplanation(false)))
      return true;
    for (ListItem optionItem : options) {
      if (!StringUtils.isEmptyOrBlank(optionItem.getStringValue())) {
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

  private TdApi.FormattedText getExplanation (boolean parseMarkdown) {
    String explanationText = isQuiz ? explanationItem.getStringValue() : null;
    if (!StringUtils.isEmpty(explanationText)) {
      TdApi.FormattedText explanation = new TdApi.FormattedText(explanationText, null);
      if (parseMarkdown)
        Td.parseMarkdown(explanation);
      return explanation;
    }
    return null;
  }

  private void send (TdApi.MessageSendOptions sendOptions, boolean disableMarkdown) {
    if (getDoneButton().isInProgress())
      return;
    String question = StringUtils.trim(questionItem.getStringValue());
    if (StringUtils.isEmpty(question) || question.length() > TdConstants.MAX_POLL_QUESTION_LENGTH) {
      requestFocus(questionItem);
      return;
    }
    int correctOptionId = -1;
    List<String> options = new ArrayList<>(TdConstants.MAX_POLL_OPTION_COUNT);
    for (ListItem optionItem : this.options) {
      String option = StringUtils.trim(optionItem.getStringValue());
      if (StringUtils.isEmpty(option))
        continue;
      if (option.length() > TdConstants.MAX_POLL_OPTION_LENGTH) {
        requestFocus(optionItem);
        return;
      }
      if (optionItem == correctOptionItem) {
        correctOptionId = options.size();
      }
      options.add(option);
    }
    if (options.size() < 2)
      return;
    Args args = getArgumentsStrict();
    final long chatId = args.chatId;
    final ThreadInfo messageThread = args.messageThread;
    if (sendOptions.schedulingState == null && args.callback.areScheduledOnly(this)) {
      tdlib.ui().showScheduleOptions(this, chatId, false, (modifiedSendOptions, disableMarkdown1) -> send(modifiedSendOptions, disableMarkdown), sendOptions, null);
      return;
    }

    TdApi.FormattedText explanation = getExplanation(!disableMarkdown);

    // TODO lock texts
    getDoneButton().setInProgress(true);
    hideSoftwareKeyboard();

    String[] optionsArray = options.toArray(new String[0]);
    TdApi.InputMessagePoll poll = new TdApi.InputMessagePoll(question, optionsArray, isAnonymousVoting, isQuiz ? new TdApi.PollTypeQuiz(correctOptionId, explanation) : new TdApi.PollTypeRegular(isMultiChoiceVote), 0, 0, false);

    RunnableData<TdApi.Message> after = message -> {
      tdlib.ui().post(() -> {
        if (!isDestroyed()) {
          getDoneButton().setInProgress(false);
          if (message != null) {
            if (sendOptions.schedulingState != null && !args.callback.areScheduledOnly(this)) {
              NavigationStack stack = navigationStack();
              if (stack != null) {
                MessagesController c = new MessagesController(context, tdlib);
                c.setArguments(new MessagesController.Arguments(args.callback.provideChatList(this), tdlib.chatStrict(args.chatId), messageThread, null, MessagesManager.HIGHLIGHT_MODE_NONE, null).setScheduled(true));
                stack.insertBack(c);
              }
            }
            navigateBack();
          }
        }
      });
    };
    final TdApi.MessageSendOptions finalSendOptions = Td.newSendOptions(sendOptions, tdlib.chatDefaultDisableNotifications(chatId));
    if (!getArgumentsStrict().callback.onSendPoll(this, chatId, messageThread != null ? messageThread.getMessageThreadId() : 0, poll, finalSendOptions, after)) {
      tdlib.sendMessage(chatId, messageThread != null ? messageThread.getMessageThreadId() : 0, 0, finalSendOptions, poll, after);
    }
  }
}
