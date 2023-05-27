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
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.OptionDelegate;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;
import org.thunderdog.challegram.widget.SliderWrapView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.lambda.RunnableLong;
import me.vkryl.td.TdConstants;

public class EditChatLinkController extends EditBaseController<EditChatLinkController.Args> implements View.OnClickListener, SettingsAdapter.TextChangeListener {
  private static final int[] EXPIRE_DATE_PRESETS = new int[]{3600, 3600 * 24, 3600 * 24 * 7};
  private static final String[] DEFAULT_MEMBER_COUNT = new String[]{"1", "10", "50", "100", Lang.getString(R.string.Infinity)};
  private static final String[] DEFAULT_DATE_VALUES = new String[]{Lang.getDuration(EXPIRE_DATE_PRESETS[0]), Lang.getDuration(EXPIRE_DATE_PRESETS[1]), Lang.getDuration(EXPIRE_DATE_PRESETS[2]), Lang.getString(R.string.Infinity)};

  private String[] memberCountSliderData = DEFAULT_MEMBER_COUNT;
  private int memberCountSliderIndex = DEFAULT_MEMBER_COUNT.length - 1;

  private String[] expireDateSliderData = DEFAULT_DATE_VALUES;
  private int[] expireDateSliderDataInternal = EXPIRE_DATE_PRESETS;
  private int expireDateSliderIndex = DEFAULT_DATE_VALUES.length - 1;
  private int actualTdlibSeconds;

  private SettingsAdapter adapter;

  private int expireDate;
  private int memberLimit;
  private boolean isCreation;
  private boolean createsJoinRequest;
  private String linkName = "";

  private TdApi.ChatInviteLink existingInviteLink;

  public EditChatLinkController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private boolean isMemberLimitPreset () {
    return memberLimit == 0 || memberLimit == 1 || memberLimit == 10 || memberLimit == 50 || memberLimit == 100;
  }

  private boolean isExpireDatePreset () {
    return expireDate == 0 || expireDate == EXPIRE_DATE_PRESETS[0] || expireDate == EXPIRE_DATE_PRESETS[1] || expireDate == EXPIRE_DATE_PRESETS[2];
  }

  private void updateMemberCountSlider () {
    int[] data = isMemberLimitPreset() ? new int[]{1, 10, 50, 100, Integer.MAX_VALUE} : new int[]{1, 10, 50, 100, Integer.MAX_VALUE, memberLimit};

    Arrays.sort(data);

    StringList strings = new StringList(data.length);

    for (int datum : data) {
      if (existingInviteLink != null && datum < existingInviteLink.memberCount) continue;
      strings.append(datum == Integer.MAX_VALUE ? Lang.getString(R.string.Infinity) : String.valueOf(datum));
    }

    memberCountSliderIndex = strings.indexOf(String.valueOf(memberLimit));

    if (memberCountSliderIndex == -1) {
      memberCountSliderIndex = strings.get().length - 1;
    }

    memberCountSliderData = strings.get();
  }

  private void updateExpireDateSlider () {
    int[] data = isExpireDatePreset() ? new int[]{3600, 3600 * 24, 3600 * 24 * 7, Integer.MAX_VALUE} : new int[]{3600, 3600 * 24, 3600 * 24 * 7, Integer.MAX_VALUE, expireDate};

    Arrays.sort(data);

    StringList strings = new StringList(data.length);

    for (int i = 0; i < data.length; i++) {
      int datum = data[i];
      strings.append(datum == Integer.MAX_VALUE ? Lang.getString(R.string.Infinity) : Lang.getDuration(datum));
      if (expireDate == datum) expireDateSliderIndex = i;
    }

    if (expireDateSliderIndex == -1) {
      expireDateSliderIndex = data.length - 1;
    }

    expireDateSliderData = strings.get();
    expireDateSliderDataInternal = data;
    actualTdlibSeconds = (int) tdlib.currentTime(TimeUnit.SECONDS);
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    if (!isCreation && hasAnyChanges()) {
      showUnsavedChangesPromptBeforeLeaving(null);
      return true;
    }

    return false;
  }

  @Override
  protected boolean swipeNavigationEnabled () {
    return isCreation || !hasAnyChanges();
  }

  private void checkDoneButton () {
    if (!isCreation) {
      setDoneVisible(hasAnyChanges());
    }
  }

  private boolean hasAnyChanges () {
    if (existingInviteLink == null || isCreation) {
      return true;
    }

    return memberLimit != existingInviteLink.memberLimit || expireDate != (existingInviteLink.expirationDate == 0 ? 0 : existingInviteLink.expirationDate - actualTdlibSeconds) || !linkName.equals(existingInviteLink.name) || createsJoinRequest != existingInviteLink.createsJoinRequest;
  }

  @Override
  public void onTextChanged (int id, ListItem item, MaterialEditTextGroup v, String text) {
    if (id == R.id.btn_inviteLinkName) {
      linkName = v.getText().toString();
      checkDoneButton();
    }
  }

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    isCreation = args.existingInviteLink == null;
    if (args.existingInviteLink != null) {
      existingInviteLink = args.existingInviteLink;
      expireDate = Math.max(0, (int) (args.existingInviteLink.expirationDate - tdlib.currentTime(TimeUnit.SECONDS)));
      memberLimit = args.existingInviteLink.memberLimit;
      createsJoinRequest = args.existingInviteLink.createsJoinRequest;
      linkName = args.existingInviteLink.name;
      updateMemberCountSlider();
      updateExpireDateSlider();
    } else {
      actualTdlibSeconds = (int) tdlib.currentTime(TimeUnit.SECONDS);
    }
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.btn_inviteLinkDateLimit) {
      IntList ids = new IntList(4);
      StringList strings = new StringList(4);
      IntList icons = new IntList(4);

      RunnableLong act = (millis) -> {
        expireDate = (int) TimeUnit.MILLISECONDS.toSeconds(millis);
        updateExpireDateSlider();
        adapter.updateValuedSettingById(R.id.btn_inviteLinkDateLimit);
        adapter.updateItemById(R.id.btn_inviteLinkDateSlider);
        checkDoneButton();
      };

      ids.append(R.id.btn_expireIn12h);
      strings.append(Lang.plural(R.string.InviteLinkExpireInHours, 12));
      icons.append(R.drawable.baseline_schedule_24);

      ids.append(R.id.btn_expireIn2d);
      strings.append(Lang.plural(R.string.InviteLinkExpireInDays, 2));
      icons.append(R.drawable.baseline_schedule_24);

      ids.append(R.id.btn_expireIn1w);
      strings.append(Lang.plural(R.string.InviteLinkExpireInWeeks, 1));
      icons.append(R.drawable.baseline_schedule_24);

      ids.append(R.id.btn_expireIn2w);
      strings.append(Lang.plural(R.string.InviteLinkExpireInWeeks, 2));
      icons.append(R.drawable.baseline_schedule_24);

      ids.append(R.id.btn_sendScheduledCustom);
      strings.append(Lang.getString(R.string.InviteLinkExpireInCustomDate));
      icons.append(R.drawable.baseline_date_range_24);

      showOptions(null, ids.get(), strings.get(), null, icons.get(), new OptionDelegate() {
        @Override
        public boolean onOptionItemPressed (View optionItemView, int id) {
          long millis;
          if (id == R.id.btn_sendScheduledCustom) {
            showDateTimePicker(Lang.getString(R.string.InviteLinkExpireTitle), R.string.InviteLinkExpireToday, R.string.InviteLinkExpireTomorrow, R.string.InviteLinkExpireFuture, (currentMillis) -> act.runWithLong(currentMillis - tdlib.currentTimeMillis()), null);
            return true;
          } else if (id == R.id.btn_expireIn12h) {
            millis = TimeUnit.HOURS.toMillis(12);
          } else if (id == R.id.btn_expireIn2d) {
            millis = TimeUnit.DAYS.toMillis(2);
          } else if (id == R.id.btn_expireIn1w) {
            millis = TimeUnit.DAYS.toMillis(7);
          } else if (id == R.id.btn_expireIn2w) {
            millis = TimeUnit.DAYS.toMillis(14);
          } else {
            return false;
          }
          act.runWithLong(millis);
          return true;
        }
      });
    } else if (v.getId() == R.id.btn_inviteLinkUserLimit) {
      openInputAlert(Lang.getString(R.string.InviteLinkLimitedByUsersItem), Lang.getString(R.string.InviteLinkLimitedByUsersAlertHint), R.string.Done, R.string.Cancel, String.valueOf(memberLimit), (inputView, result) -> {
        int data = StringUtils.parseInt(result, -1);
        if (data < 0 || (isCanJoinNegative(data) && data != 0))
          return false;

        memberLimit = Math.min(data, TdConstants.MAX_CHAT_INVITE_LINK_USER_COUNT);
        updateMemberCountSlider();
        adapter.updateItemById(R.id.btn_inviteLinkUserSlider);
        adapter.updateValuedSettingById(R.id.btn_inviteLinkUserLimit);
        checkDoneButton();

        return true;
      }, true).getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
    } else if (v.getId() == R.id.btn_inviteLinkAdminApproval) {
      if (createsJoinRequest = adapter.toggleView(v)) {
        removeUserLimitItems();
      } else {
        addUserLimitItems();
      }

      checkDoneButton();
    }
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(isCreation ? R.string.CreateLink : R.string.InviteLinkEdit);
  }

  @Override
  protected boolean onDoneClick () {
    setDoneInProgress(true);
    int actualExpireDate = expireDate == 0 ? 0 : actualTdlibSeconds + expireDate;
    tdlib.client().send(
      isCreation ? new TdApi.CreateChatInviteLink(getArgumentsStrict().chatId, linkName, actualExpireDate, createsJoinRequest ? 0 : memberLimit, createsJoinRequest) : new TdApi.EditChatInviteLink(getArgumentsStrict().chatId, getArgumentsStrict().existingInviteLink.inviteLink, linkName, actualExpireDate, createsJoinRequest ? 0 : memberLimit, createsJoinRequest), result -> {
        runOnUiThreadOptional(() -> {
          switch (result.getConstructor()) {
            case TdApi.ChatInviteLink.CONSTRUCTOR: {
              if (getArgumentsStrict().controller != null) {
                getArgumentsStrict().controller.onLinkCreated((TdApi.ChatInviteLink) result, getArgumentsStrict().existingInviteLink);
              } else {
                // TODO: properly handle entering from Recent Actions
              }
              navigateBack();
              break;
            }
            case TdApi.Error.CONSTRUCTOR: {
              UI.showError(result); // TODO show as tooltip
              setDoneInProgress(false);
              break;
            }
          }
        });
      });

    return true;
  }

  private boolean isCanJoinNegative (int memberLimit) {
    if (existingInviteLink == null) return false;
    return (memberLimit - existingInviteLink.memberCount) < 0;
  }

  private void removeUserLimitItems () {
    int idx = adapter.indexOfViewById(R.id.btn_inviteLinkDateLimit);
    if (idx == -1) return;
    adapter.removeRange(idx + 3, 7);
  }

  private void addUserLimitItems () {
    int idx = adapter.indexOfViewById(R.id.btn_inviteLinkDateLimit);
    if (idx == -1) return;
    adapter.addItems(idx + 3, createUserLimitItems());
  }

  private ListItem[] createUserLimitItems () {
    return new ListItem[] {
      new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.InviteLinkLimitedByUsers),
      new ListItem(ListItem.TYPE_SHADOW_TOP),
      new ListItem(ListItem.TYPE_SLIDER, R.id.btn_inviteLinkUserSlider),
      new ListItem(ListItem.TYPE_SEPARATOR_FULL),
      new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_inviteLinkUserLimit, 0, R.string.InviteLinkLimitedByUsersCustom),
      new ListItem(ListItem.TYPE_SHADOW_BOTTOM),
      new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.InviteLinkLimitedByUsersHint)
    };
  }

  @Override
  public int getId () {
    return R.id.controller_editChatLink;
  }

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {
    setDoneIcon(R.drawable.baseline_check_24);
    setInstantDoneVisible(true);

    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getId() == R.id.btn_inviteLinkDateLimit) {
          view.setData(expireDate > 0 ? Lang.getUntilDate(actualTdlibSeconds + expireDate, TimeUnit.SECONDS) : Lang.getString(R.string.InviteLinkNoLimitSet));
        } else if (item.getId() == R.id.btn_inviteLinkUserLimit) {
          int newCanJoin = memberLimit;

          if (existingInviteLink != null) {
            newCanJoin = memberLimit - existingInviteLink.memberCount;
          }

          view.setData(memberLimit == 0 ? Lang.getString(R.string.InviteLinkNoLimitSet) : Lang.plural(R.string.InviteLinkRemains, newCanJoin));
        } else if (item.getId() == R.id.btn_inviteLinkAdminApproval) {
          view.getToggler().setRadioEnabled(createsJoinRequest, false);
        }
      }

      @Override
      protected void setSliderValues (ListItem item, SliderWrapView view) {
        view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), Screen.dp(16f), view.getPaddingBottom());
        view.setShowOnlyValue(true);
        if (item.getId() == R.id.btn_inviteLinkDateSlider) {
          view.setValues("", expireDateSliderData, expireDateSliderIndex);
        } else if (item.getId() == R.id.btn_inviteLinkUserSlider) {
          view.setValues("", memberCountSliderData, memberCountSliderIndex);
        }
      }

      @Override
      protected void onSliderValueChanged (ListItem item, SliderWrapView view, int newValue, int oldValue) {
        if (item.getId() == R.id.btn_inviteLinkDateSlider) {
          expireDate = (newValue == expireDateSliderData.length - 1) ? 0 : expireDateSliderDataInternal[newValue];
          adapter.updateValuedSettingById(R.id.btn_inviteLinkDateLimit);
        } else if (item.getId() == R.id.btn_inviteLinkUserSlider) {
          memberLimit = (newValue == memberCountSliderData.length - 1) ? 0 : Integer.parseInt(memberCountSliderData[newValue]);
          adapter.updateValuedSettingById(R.id.btn_inviteLinkUserLimit);
        }

        checkDoneButton();
      }

      @Override
      protected void modifyEditText (ListItem item, ViewGroup parent, MaterialEditTextGroup editText) {
        editText.setText(linkName);
      }
    };
    adapter.setTextChangeListener(this);

    List<ListItem> items = new ArrayList<>();

    items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_inviteLinkAdminApproval, 0, R.string.InviteLinkAdminApproval));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.InviteLinkAdminApprovalHint));

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_EDITTEXT_CHANNEL_DESCRIPTION, R.id.btn_inviteLinkName, 0, R.string.InviteLinkAdminName));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.InviteLinkAdminNameHint));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.InviteLinkLimitedByPeriod));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SLIDER, R.id.btn_inviteLinkDateSlider));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_inviteLinkDateLimit, 0, R.string.InviteLinkLimitedByPeriodItem));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.InviteLinkLimitedByPeriodHint));

    if (!(existingInviteLink != null && existingInviteLink.createsJoinRequest)) {
      items.addAll(Arrays.asList(createUserLimitItems()));
    }

    adapter.setItems(items, false);
    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    recyclerView.setAdapter(adapter);
    checkDoneButton();
  }

  public static class Args {
    @Nullable
    public final TdApi.ChatInviteLink existingInviteLink;
    public final long chatId;

    @Nullable
    public final ChatLinksController controller;

    public Args (@Nullable TdApi.ChatInviteLink existingInviteLink, long chatId, @Nullable ChatLinksController controller) {
      this.existingInviteLink = existingInviteLink;
      this.chatId = chatId;
      this.controller = controller;
    }
  }

  @Override
  protected int getRecyclerBackgroundColorId () {
    return ColorId.background;
  }
}
