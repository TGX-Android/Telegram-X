package org.thunderdog.challegram.ui;

import android.content.Context;
import android.text.InputType;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.widget.SliderWrapView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.widget.FrameLayoutFix;

public class EditChatLinkController extends EditBaseController<EditChatLinkController.Args> implements View.OnClickListener {
  private static final int[] PRESETS = new int[]{0, 3600, 3600 * 24, 3600 * 24 * 7, 1};
  private static final String[] DEFAULT_MEMBER_COUNT = new String[]{"1", "10", "50", "100", Lang.getString(R.string.Infinity)};

  private String[] memberCountSliderData = DEFAULT_MEMBER_COUNT;
  private int memberCountSliderIndex = DEFAULT_MEMBER_COUNT.length - 1;

  private SettingsAdapter adapter;
  private int expireDate;
  private int memberLimit;
  private boolean isCreation;

  public EditChatLinkController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private boolean isMemberLimitPreset () {
    return memberLimit == 0 || memberLimit == 1 || memberLimit == 10 || memberLimit == 50 || memberLimit == 100;
  }

  private void updateMemberCountSlider () {
    int[] data = isMemberLimitPreset() ? new int[]{1, 10, 50, 100, Integer.MAX_VALUE} : new int[]{1, 10, 50, 100, Integer.MAX_VALUE, memberLimit};

    Arrays.sort(data);

    StringList strings = new StringList(data.length);

    for (int datum : data) {
      strings.append(datum == Integer.MAX_VALUE ? Lang.getString(R.string.Infinity) : String.valueOf(datum));
    }

    memberCountSliderIndex = strings.indexOf(String.valueOf(memberLimit));

    if (memberCountSliderIndex == -1) {
      memberCountSliderIndex = data.length - 1;
    }

    memberCountSliderData = strings.get();
  }

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    isCreation = args.existingInviteLink == null;
    if (args.existingInviteLink != null) {
      expireDate = args.existingInviteLink.expireDate;
      memberLimit = args.existingInviteLink.memberLimit;
      updateMemberCountSlider();
    }
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.btn_inviteLinkDateLimit) {
      int[] ids = new int[PRESETS.length];
      int[] icons = new int[PRESETS.length];
      String[] strings = new String[PRESETS.length];

      for (int i = 0; i < PRESETS.length; i++) {
        ids[i] = i;
        final int expireIn = PRESETS[i];
        switch (expireIn) {
          case 0:
            icons[i] = R.drawable.baseline_cancel_24;
            strings[i] = Lang.getString(R.string.InviteLinkExpireNone);
            break;
          case 1:
            icons[i] = R.drawable.baseline_date_range_24;
            strings[i] = Lang.getString(R.string.InviteLinkExpireInCustomDate);
            break;
          default: {
            icons[i] = R.drawable.baseline_schedule_24;
            int stringRes;
            long num;
            if (expireIn < TimeUnit.DAYS.toSeconds(1)) {
              stringRes = R.string.InviteLinkExpireInHours;
              num = TimeUnit.SECONDS.toHours(expireIn);
            } else if (expireIn < TimeUnit.DAYS.toSeconds(7)) {
              stringRes = R.string.InviteLinkExpireInDays;
              num = TimeUnit.SECONDS.toDays(expireIn);
            } else {
              stringRes = R.string.InviteLinkExpireInWeeks;
              num = TimeUnit.SECONDS.toDays(expireIn) / 7;
            }
            strings[i] = Lang.plural(stringRes, num);
            break;
          }
        }
      }

      showOptions(null, ids, strings, null, icons, (itemView, id) -> {
        switch (id) {
          case 0:
            expireDate = 0;
            break;
          case 4:
            showDateTimePicker(Lang.getString(R.string.InviteLinkExpireHeader), R.string.InviteLinkExpireConfirm, R.string.InviteLinkExpireConfirm, R.string.InviteLinkExpireConfirm, millis -> {
              expireDate = (int) TimeUnit.MILLISECONDS.toSeconds(millis);
              adapter.updateValuedSettingById(R.id.btn_inviteLinkDateLimit);
            }, null);
            break;
          default:
            expireDate = (int) TimeUnit.MILLISECONDS.toSeconds(tdlib.currentTimeMillis() + TimeUnit.SECONDS.toMillis(PRESETS[id]));
            break;
        }

        adapter.updateValuedSettingById(R.id.btn_inviteLinkDateLimit);
        return true;
      });
    } else if (v.getId() == R.id.btn_inviteLinkUserLimit) {
      openInputAlert(Lang.getString(R.string.InviteLinkLimitedByUsersItem), Lang.getString(R.string.InviteLinkLimitedByUsersAlertHint), R.string.Done, R.string.Cancel, String.valueOf(memberLimit), (inputView, result) -> {
        memberLimit = Math.min(Math.max(0, Integer.parseInt(result)), 99999);
        updateMemberCountSlider();
        adapter.updateItemById(R.id.btn_inviteLinkUserSlider);
        return true;
      }, true).getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
    }
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(isCreation ? R.string.CreateLink : R.string.InviteLinkEdit);
  }

  @Override
  protected boolean onDoneClick () {
    setDoneInProgress(true);
    tdlib.client().send(
      isCreation ? new TdApi.CreateChatInviteLink(getArgumentsStrict().chatId, expireDate, memberLimit) : new TdApi.EditChatInviteLink(getArgumentsStrict().chatId, getArgumentsStrict().existingInviteLink.inviteLink, expireDate, memberLimit), result -> {
        runOnUiThreadOptional(() -> {
          if (result.getConstructor() == TdApi.ChatInviteLink.CONSTRUCTOR) {
            getArgumentsStrict().controller.onLinkCreated((TdApi.ChatInviteLink) result, getArgumentsStrict().existingInviteLink);
            navigateBack();
          } else if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
            UI.showError(result);
            setDoneInProgress(false);
          }
        });
      });

    return true;
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
          view.setData(expireDate > 0 ? Lang.getUntilDate(expireDate, TimeUnit.SECONDS) : Lang.getString(R.string.InviteLinkNoLimitSet));
        }
      }

      @Override
      protected void setSliderValues (ListItem item, SliderWrapView view) {
        view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), Screen.dp(16f), view.getPaddingBottom());
        view.setShowOnlyValue(true);
        view.setValues("", memberCountSliderData, memberCountSliderIndex);
      }

      @Override
      protected void onSliderValueChanged (ListItem item, SliderWrapView view, int newValue, int oldValue) {
        memberLimit = (newValue == memberCountSliderData.length - 1) ? 0 : Integer.parseInt(memberCountSliderData[newValue]);
      }
    };

    List<ListItem> items = new ArrayList<>();

    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.InviteLinkLimitedByPeriod));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_inviteLinkDateLimit, 0, R.string.InviteLinkLimitedByPeriodItem));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.InviteLinkLimitedByPeriodHint));

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.InviteLinkLimitedByUsers));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SLIDER, R.id.btn_inviteLinkUserSlider));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_inviteLinkUserLimit, 0, R.string.InviteLinkLimitedByUsersCustom));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.InviteLinkLimitedByUsersHint));

    adapter.setItems(items, false);
    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    recyclerView.setAdapter(adapter);
  }

  public static class Args {
    @Nullable
    public final TdApi.ChatInviteLink existingInviteLink;
    public final long chatId;
    public final ChatLinksController controller;

    public Args (@Nullable TdApi.ChatInviteLink existingInviteLink, long chatId, ChatLinksController controller) {
      this.existingInviteLink = existingInviteLink;
      this.chatId = chatId;
      this.controller = controller;
    }
  }

  @Override
  protected int getRecyclerBackgroundColorId () {
    return R.id.theme_color_background;
  }
}
