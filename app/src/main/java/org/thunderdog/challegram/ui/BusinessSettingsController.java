/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.Toast;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.RunnableData;
import tgx.td.Td;

public final class BusinessSettingsController extends RecyclerViewController<Void> implements View.OnClickListener {
  private SettingsAdapter adapter;
  private TdApi.BusinessInfo info;
  private TdApi.BusinessFeature[] features;
  private TdApi.BusinessChatLink[] links;
  private boolean loading, busy, profileError, linksError;
  private boolean shortcutsLoading;
  private Runnable afterShortcuts;
  private final Runnable shortcutListener = () -> runOnUiThreadOptional(() -> {
    if (adapter != null) {
      render();
    }
  });

  public BusinessSettingsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () { return R.id.controller_tgxBusiness; }

  @Override
  public CharSequence getName () { return Lang.getString(R.string.TgxBusinessTitle); }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getData() instanceof String value) {
          view.setData(value);
        } else if (item.getData() instanceof TdApi.BusinessChatLink link) {
          view.setData(Lang.getString(R.string.TgxBusinessLinkCount, Lang.formatNumber(link.viewCount)));
        }
      }
    };
    recyclerView.setAdapter(adapter);
    tdlib.addQuickReplyListener(shortcutListener);
    refresh();
  }

  @Override
  public void destroy () {
    tdlib.removeQuickReplyListener(shortcutListener);
    super.destroy();
  }

  @Override
  public void onActivityResume () {
    super.onActivityResume();
    if (adapter != null && isFocused()) {
      refresh();
    }
  }

  private void refresh () {
    if (loading || busy || tdlib.myUserId() == 0) {
      return;
    }
    loading = true;
    profileError = false;
    linksError = false;
    render();
    final long userId = tdlib.myUserId();
    tdlib.send(new TdApi.GetBusinessFeatures(null), (result, error) -> runOnUiThreadOptional(() -> {
      if (userId == tdlib.myUserId() && result != null) {
        features = result.features;
        render();
      }
    }));
    tdlib.send(new TdApi.GetUserFullInfo(userId), (full, error) -> runOnUiThreadOptional(() -> {
      if (userId != tdlib.myUserId()) {
        loading = false;
        return;
      }
      profileError = error != null;
      if (full != null) {
        info = full.businessInfo;
      }
      if (tdlib.hasPremium() || info != null) {
        loadLinks(userId);
        loadShortcuts(null);
      } else {
        loading = false;
        links = null;
        render();
      }
    }));
  }

  private void loadLinks (long userId) {
    tdlib.send(new TdApi.GetBusinessChatLinks(), (result, error) -> runOnUiThreadOptional(() -> {
      loading = false;
      if (userId == tdlib.myUserId()) {
        linksError = error != null;
        if (result != null) {
          links = result.links;
        }
        render();
      }
    }));
  }

  private void render () {
    ArrayList<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_ICONIZED_EMPTY, 0, R.drawable.baseline_premium_star_96,
      Lang.getString(R.string.TgxBusinessIntro)));
    if (!tdlib.hasPremium() && info == null) {
      items.add(new ListItem(ListItem.TYPE_BUTTON, R.id.btn_tgxBusinessPremium, 0, R.string.TgxPremiumTitle));
    }
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.TgxBusinessProfile));
    addRow(items, R.id.btn_tgxBusinessLocation, R.string.TgxBusinessLocation,
      info != null && info.location != null ? info.location.address : null);
    addRow(items, R.id.btn_tgxBusinessHours, R.string.TgxBusinessHours,
      info != null && info.openingHours != null ? info.openingHours.timeZoneId : null);
    addRow(items, R.id.btn_tgxBusinessStart, R.string.TgxBusinessStart,
      info != null && info.startPage != null ? info.startPage.title : null);
    addRow(items, R.id.btn_tgxBusinessGreeting, R.string.TgxBusinessGreeting,
      info != null && info.greetingMessageSettings != null ? Lang.getString(R.string.TgxBusinessConfigured) : null);
    addRow(items, R.id.btn_tgxBusinessAway, R.string.TgxBusinessAway,
      info != null && info.awayMessageSettings != null ? Lang.getString(R.string.TgxBusinessConfigured) : null);

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.TgxBusinessLinks));
    if (links != null) {
      for (TdApi.BusinessChatLink link : links) {
        String name = StringUtils.isEmpty(link.title) ? link.link : link.title;
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_tgxBusinessLink, 0, name).setData(link));
      }
      if (links.length < tdlib.options().businessChatLinkCountMax) {
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgxBusinessAddLink, 0, R.string.TgxBusinessAddLink));
      }
    }
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_businessBotManage, 0, R.string.TgxBusinessBots));
    if (features != null) {
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.TgxBusinessTools));
      for (TdApi.BusinessFeature feature : features) {
        int title = switch (feature.getConstructor()) {
          case TdApi.BusinessFeatureQuickReplies.CONSTRUCTOR -> R.string.TgxBusinessQuickReplies;
          case TdApi.BusinessFeatureChatFolderTags.CONSTRUCTOR -> R.string.TgxBusinessFolderTags;
          case TdApi.BusinessFeatureEmojiStatus.CONSTRUCTOR -> R.string.TgxPremiumFeatureEmojiStatus;
          case TdApi.BusinessFeatureUpgradedStories.CONSTRUCTOR -> R.string.TgxPremiumFeatureUpgradedStories;
          default -> 0;
        };
        if (title != 0) {
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgxBusinessTool, 0, title).setData(feature));
        }
      }
    }
    if (profileError || linksError) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.TgxBusinessLoadError));
    }
    if (loading || busy) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.LoadingInformation));
    } else {
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgxBusinessRefresh, 0, R.string.TgxBusinessRefresh));
    }
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgxBusinessOfficial, 0, R.string.TgxPremiumViewOptions));
    adapter.setItems(items, false);
  }

  private void addRow (ArrayList<ListItem> items, int id, int title, String detail) {
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, id, 0, title)
      .setData(StringUtils.isEmpty(detail) ? Lang.getString(R.string.TgxBusinessNotConfigured) : detail));
  }

  @Override
  public void onClick (View view) {
    if (busy) {
      return;
    }
    int id = view.getId();
    if (id == R.id.btn_tgxBusinessPremium) {
      tdlib.ui().openPremiumSettings(this, new TdApi.PremiumSourceFeature(new TdApi.PremiumFeatureBusiness()));
    } else if (id == R.id.btn_tgxBusinessRefresh) {
      refresh();
    } else if (id == R.id.btn_businessBotManage) {
      showConnectedBot();
    } else if (id == R.id.btn_tgxBusinessOfficial) {
      openOfficial();
    } else if (id == R.id.btn_tgxBusinessTool && view.getTag() instanceof ListItem item &&
               item.getData() instanceof TdApi.BusinessFeature feature) {
      if (feature instanceof TdApi.BusinessFeatureQuickReplies && (tdlib.hasPremium() || info != null)) {
        loadShortcuts(this::showQuickReplies);
      } else {
        openOfficial();
      }
    } else if (loading || profileError) {
      return;
    } else if (!tdlib.hasPremium() && info == null) {
      tdlib.ui().openPremiumSettings(this, new TdApi.PremiumSourceFeature(new TdApi.PremiumFeatureBusiness()));
    } else if (id == R.id.btn_tgxBusinessLocation) {
      TdApi.BusinessLocation current = info != null ? info.location : null;
      offerEdit(current != null, () -> editLocation(current), () ->
        saveProfile(business -> business != null ? business.location : null, current, new TdApi.SetBusinessLocation(null)));
    } else if (id == R.id.btn_tgxBusinessHours) {
      TdApi.BusinessOpeningHours current = info != null ? info.openingHours : null;
      offerEdit(current != null, () -> selectTimeZone(current), () ->
        saveProfile(business -> business != null ? business.openingHours : null, current, new TdApi.SetBusinessOpeningHours(null)));
    } else if (id == R.id.btn_tgxBusinessStart) {
      TdApi.BusinessStartPage current = info != null ? info.startPage : null;
      offerEdit(current != null, () -> editStartPage(current), () ->
        saveProfile(business -> business != null ? business.startPage : null, current, new TdApi.SetBusinessStartPage(null)));
    } else if (id == R.id.btn_tgxBusinessGreeting) {
      TdApi.BusinessGreetingMessageSettings current = info != null ? info.greetingMessageSettings : null;
      offerEdit(current != null, () -> configureGreeting(current), () ->
        saveProfile(business -> business != null ? business.greetingMessageSettings : null, current,
          new TdApi.SetBusinessGreetingMessageSettings(null)));
    } else if (id == R.id.btn_tgxBusinessAway) {
      TdApi.BusinessAwayMessageSettings current = info != null ? info.awayMessageSettings : null;
      offerEdit(current != null, () -> configureAway(current), () ->
        saveProfile(business -> business != null ? business.awayMessageSettings : null, current,
          new TdApi.SetBusinessAwayMessageSettings(null)));
    } else if (id == R.id.btn_tgxBusinessAddLink && !linksError) {
      editLink(null);
    } else if (id == R.id.btn_tgxBusinessLink && view.getTag() instanceof ListItem item &&
               item.getData() instanceof TdApi.BusinessChatLink link) {
      showLink(link);
    }
  }

  private void loadShortcuts (Runnable after) {
    if (after != null) {
      afterShortcuts = after;
    }
    if (shortcutsLoading) {
      return;
    }
    shortcutsLoading = true;
    tdlib.send(new TdApi.LoadQuickReplyShortcuts(), (ok, error) -> runOnUiThreadOptional(() -> {
      shortcutsLoading = false;
      Runnable action = afterShortcuts;
      afterShortcuts = null;
      if (action != null && isFocused()) {
        if (error != null) {
          UI.showError(error);
        } else {
          action.run();
        }
      }
    }));
  }

  private void showQuickReplies () {
    TdApi.QuickReplyShortcut[] shortcuts = tdlib.quickReplyShortcuts();
    Options.Builder options = new Options.Builder().info(Lang.getString(R.string.TgxBusinessQuickReplies));
    for (int i = 0; i < shortcuts.length; i++) {
      TdApi.QuickReplyShortcut shortcut = shortcuts[i];
      options.item(new OptionItem(i + 1, "/" + shortcut.name + " · " +
        Lang.getString(R.string.TgxBusinessQuickReplyCount, Lang.formatNumber(shortcut.messageCount)), OptionColor.NORMAL, 0));
    }
    if (shortcuts.length < tdlib.options().quickReplyShortcutCountMax) {
      options.item(new OptionItem(R.id.btn_tgxBusinessAddQuickReply, Lang.getString(R.string.TgxBusinessAddQuickReply),
        OptionColor.NORMAL, 0));
    }
    options.cancelItem();
    showOptions(options.build(), (view, id) -> {
      if (id == R.id.btn_tgxBusinessAddQuickReply) {
        editShortcut(null);
      } else if (id > 0 && id <= shortcuts.length) {
        TdApi.QuickReplyShortcut shortcut = shortcuts[id - 1];
        TdApi.MessageContent content = shortcut.firstMessage != null ? shortcut.firstMessage.content : null;
        String preview = content instanceof TdApi.MessageText text ? text.text.text :
          Lang.getString(R.string.TgxBusinessQuickReplyMedia);
        showConfirm("/" + shortcut.name + "\n" + preview, Lang.getString(R.string.TgxBusinessAppendQuickReply),
          () -> editShortcut(shortcut));
      }
      return true;
    });
  }

  private void editShortcut (TdApi.QuickReplyShortcut existing) {
    if (existing != null) {
      editShortcutMessage(existing.name, existing.id);
      return;
    }
    askText(R.string.TgxBusinessQuickReplyName, "", false, name -> {
      String slug = name.trim();
      if (slug.isEmpty() || Arrays.stream(tdlib.quickReplyShortcuts()).anyMatch(shortcut -> shortcut.name.equalsIgnoreCase(slug))) {
        UI.showToast(R.string.TgxBusinessQuickReplyNameInvalid, Toast.LENGTH_SHORT);
        return;
      }
      tdlib.send(new TdApi.CheckQuickReplyShortcutName(slug), (ok, error) -> runOnUiThreadOptional(() -> {
        if (isFocused()) {
          if (error != null) {
            UI.showError(error);
          } else {
            editShortcutMessage(slug, 0);
          }
        }
      }));
    });
  }

  private void editShortcutMessage (String name, int expectedId) {
    askText(R.string.TgxBusinessQuickReplyMessage, "", true, message -> {
      if (message.trim().isEmpty()) {
        return;
      }
      showConfirm(Lang.getString(R.string.TgxBusinessQuickReplyConfirm, "/" + name), Lang.getString(R.string.Save), () -> {
        if (busy || !isFocused()) {
          return;
        }
        TdApi.QuickReplyShortcut matching = Arrays.stream(tdlib.quickReplyShortcuts())
          .filter(shortcut -> shortcut.name.equalsIgnoreCase(name)).findFirst().orElse(null);
        if (expectedId == 0 ? matching != null : matching == null || matching.id != expectedId) {
          UI.showToast(R.string.TgxBusinessChanged, Toast.LENGTH_SHORT);
          return;
        }
        busy = true;
        render();
        TdApi.FormattedText text = new TdApi.FormattedText(message, new TdApi.TextEntity[0]);
        tdlib.send(new TdApi.AddQuickReplyShortcutMessage(name, 0, new TdApi.InputMessageText(text, null, false)),
          (created, error) -> runOnUiThreadOptional(() -> finish(error, error == null)));
      });
    });
  }

  private void chooseShortcut (int selectedId, RunnableData<Integer> callback) {
    loadShortcuts(() -> {
      TdApi.QuickReplyShortcut[] shortcuts = tdlib.quickReplyShortcuts();
      if (shortcuts.length == 0) {
        showConfirm(Lang.getString(R.string.TgxBusinessNeedQuickReply), Lang.getString(R.string.TgxBusinessAddQuickReply),
          () -> editShortcut(null));
        return;
      }
      Options.Builder options = new Options.Builder().info(Lang.getString(R.string.TgxBusinessQuickReplies));
      for (int i = 0; i < shortcuts.length; i++) {
        TdApi.QuickReplyShortcut shortcut = shortcuts[i];
        options.item(new OptionItem(i + 1, "/" + shortcut.name, OptionColor.NORMAL,
          shortcut.id == selectedId ? R.drawable.baseline_check_24 : 0));
      }
      options.cancelItem();
      showOptions(options.build(), (view, id) -> {
        if (id > 0 && id <= shortcuts.length) {
          callback.runWithData(shortcuts[id - 1].id);
        }
        return true;
      });
    });
  }

  private TdApi.BusinessRecipients defaultAutomationRecipients () {
    return new TdApi.BusinessRecipients(new long[0], new long[0], true, true, true, true, false);
  }

  private void configureGreeting (TdApi.BusinessGreetingMessageSettings current) {
    chooseShortcut(current != null ? current.shortcutId : 0, shortcutId -> {
      Options.Builder options = new Options.Builder().info(Lang.getString(R.string.TgxBusinessGreetingDelay));
      for (int days : new int[] {7, 14, 21, 28}) {
        options.item(new OptionItem(days, Lang.plural(R.string.xDays, days), OptionColor.NORMAL,
          current != null && days == current.inactivityDays ? R.drawable.baseline_check_24 : 0));
      }
      options.cancelItem();
      showOptions(options.build(), (view, days) -> {
        if (days == 7 || days == 14 || days == 21 || days == 28) {
          TdApi.BusinessRecipients recipients = current != null ? current.recipients : defaultAutomationRecipients();
          TdApi.BusinessGreetingMessageSettings updated = new TdApi.BusinessGreetingMessageSettings(shortcutId, recipients, days);
          chooseAutomationRecipients(current != null, recipients, selected -> saveProfile(
            business -> business != null ? business.greetingMessageSettings : null, current,
            new TdApi.SetBusinessGreetingMessageSettings(new TdApi.BusinessGreetingMessageSettings(
              updated.shortcutId, selected, updated.inactivityDays))));
        }
        return true;
      });
    });
  }

  private void configureAway (TdApi.BusinessAwayMessageSettings current) {
    chooseShortcut(current != null ? current.shortcutId : 0, shortcutId -> {
      Options.Builder options = new Options.Builder().info(Lang.getString(R.string.TgxBusinessAwaySchedule));
      options.item(new OptionItem(1, Lang.getString(R.string.TgxBusinessAlways), OptionColor.NORMAL, 0));
      if (info != null && info.openingHours != null) {
        options.item(new OptionItem(2, Lang.getString(R.string.TgxBusinessOutsideHours), OptionColor.NORMAL, 0));
      }
      if (current != null && current.schedule != null) {
        options.item(new OptionItem(3, Lang.getString(R.string.TgxBusinessKeepSchedule), OptionColor.NORMAL, 0));
      }
      options.cancelItem();
      showOptions(options.build(), (view, id) -> {
        TdApi.BusinessAwayMessageSchedule schedule = switch (id) {
          case 1 -> new TdApi.BusinessAwayMessageScheduleAlways();
          case 2 -> new TdApi.BusinessAwayMessageScheduleOutsideOfOpeningHours();
          case 3 -> current != null ? current.schedule : null;
          default -> null;
        };
        if (schedule != null) {
          selectAwayAvailability(current, shortcutId, schedule);
        }
        return true;
      });
    });
  }

  private void selectAwayAvailability (TdApi.BusinessAwayMessageSettings current, int id,
                                       TdApi.BusinessAwayMessageSchedule schedule) {
    Options.Builder options = new Options.Builder().info(Lang.getString(R.string.TgxBusinessAwaySchedule));
    options.item(new OptionItem(1, Lang.getString(R.string.TgxBusinessOfflineOnly), OptionColor.NORMAL, 0));
    options.item(new OptionItem(2, Lang.getString(R.string.TgxBusinessRegardlessOnline), OptionColor.NORMAL, 0));
    options.cancelItem();
    showOptions(options.build(), (view, mode) -> {
      if (mode == 1 || mode == 2) {
        TdApi.BusinessRecipients recipients = current != null ? current.recipients : defaultAutomationRecipients();
        TdApi.BusinessAwayMessageSettings updated = new TdApi.BusinessAwayMessageSettings(id, recipients, schedule, mode == 1);
        chooseAutomationRecipients(current != null, recipients, selected -> saveProfile(
          business -> business != null ? business.awayMessageSettings : null, current,
          new TdApi.SetBusinessAwayMessageSettings(new TdApi.BusinessAwayMessageSettings(
            updated.shortcutId, selected, updated.schedule, updated.offlineOnly))));
      }
      return true;
    });
  }

  private void chooseAutomationRecipients (boolean existing, TdApi.BusinessRecipients recipients,
                                           RunnableData<TdApi.BusinessRecipients> save) {
    Options.Builder options = new Options.Builder().info(Lang.getString(R.string.TgxBusinessCustomRecipients));
    options.item(new OptionItem(1, Lang.getString(existing ? R.string.TgxBusinessBotKeepScope : R.string.TgxBusinessBotAllChats),
      OptionColor.NORMAL, 0));
    options.item(new OptionItem(2, Lang.getString(R.string.TgxBusinessCustomRecipients), OptionColor.NORMAL, 0));
    options.cancelItem();
    showOptions(options.build(), (view, option) -> {
      if (option == 1) {
        confirmAutomation(existing, () -> save.runWithData(recipients));
      } else if (option == 2) {
        openRecipients(recipients, false, chosen -> showConfirm(Lang.getString(R.string.TgxBusinessApplyCustomRecipients),
          Lang.getString(R.string.Save), () -> save.runWithData(chosen)));
      }
      return true;
    });
  }

  private void confirmAutomation (boolean preserveRecipients, Runnable save) {
    showConfirm(Lang.getString(preserveRecipients ? R.string.TgxBusinessKeepRecipients : R.string.TgxBusinessAllRecipients),
      Lang.getString(R.string.Save), save);
  }

  private void offerEdit (boolean existing, Runnable edit, Runnable clear) {
    if (!existing) {
      edit.run();
      return;
    }
    showOptions(new int[] {R.id.btn_tgxBusinessEdit, R.id.btn_tgxBusinessRemove, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.TgxBusinessEdit), Lang.getString(R.string.Delete), Lang.getString(R.string.Cancel)},
      (view, id) -> {
        if (id == R.id.btn_tgxBusinessEdit) {
          edit.run();
        } else if (id == R.id.btn_tgxBusinessRemove) {
          showConfirm(Lang.getString(R.string.TgxBusinessRemoveConfirm), Lang.getString(R.string.Delete), clear);
        }
        return true;
      });
  }

  private void askText (int title, String initial, boolean multiline, RunnableData<String> next) {
    var input = openInputAlert(Lang.getString(title), Lang.getString(title), R.string.Continue, R.string.Cancel,
      initial, (view, value) -> {
        tdlib.ui().post(() -> {
          if (isFocused()) {
            next.runWithData(value != null ? value : "");
          }
        });
        return true;
      }, true);
    if (multiline) {
      input.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
      input.getEditText().setMaxLines(8);
    }
  }

  private void editLocation (TdApi.BusinessLocation current) {
    askText(R.string.TgxBusinessLocation, current != null ? current.address : "", false, address -> {
      if (StringUtils.isEmpty(address.trim())) {
        UI.showToast(R.string.TgxBusinessInvalidAddress, Toast.LENGTH_SHORT);
        return;
      }
      TdApi.Location pin = current != null ? current.location : null;
      String previous = pin != null ? pin.latitude + ", " + pin.longitude : "";
      askText(R.string.TgxBusinessCoordinates, previous, false, coordinates -> {
        TdApi.Location location = null;
        if (!coordinates.trim().isEmpty()) {
          try {
            String[] parts = coordinates.trim().split(",", -1);
            if (parts.length != 2) {
              throw new IllegalArgumentException();
            }
            double latitude = Double.parseDouble(parts[0].trim()), longitude = Double.parseDouble(parts[1].trim());
            if (!(latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180)) {
              throw new IllegalArgumentException();
            }
            location = new TdApi.Location(latitude, longitude, 0);
          } catch (IllegalArgumentException error) {
            UI.showToast(R.string.TgxBusinessCoordinates, Toast.LENGTH_LONG);
            return;
          }
        }
        TdApi.BusinessLocation updated = new TdApi.BusinessLocation(location, address.trim());
        confirmSave(() -> saveProfile(business -> business != null ? business.location : null, current,
          new TdApi.SetBusinessLocation(updated)));
      });
    });
  }

  private void editStartPage (TdApi.BusinessStartPage current) {
    askText(R.string.TgxBusinessIntroTitle, current != null ? current.title : "", false, title ->
      askText(R.string.TgxBusinessIntroMessage, current != null ? current.message : "", true, message -> {
        if (title.codePointCount(0, title.length()) > tdlib.options().businessStartPageTitleLengthMax ||
            message.codePointCount(0, message.length()) > tdlib.options().businessStartPageMessageLengthMax) {
          UI.showToast(R.string.TgxBusinessIntroTooLong, Toast.LENGTH_SHORT);
          return;
        }
        TdApi.InputFile sticker = current != null && current.sticker != null ?
          new TdApi.InputFileId(current.sticker.sticker.id) : null;
        if (title.trim().isEmpty() && message.trim().isEmpty() && sticker == null) {
          UI.showToast(R.string.TgxBusinessIntroEmpty, Toast.LENGTH_SHORT);
          return;
        }
        confirmSave(() -> saveProfile(business -> business != null ? business.startPage : null, current,
          new TdApi.SetBusinessStartPage(new TdApi.InputBusinessStartPage(title, message, sticker))));
      }));
  }

  private void selectTimeZone (TdApi.BusinessOpeningHours current) {
    tdlib.send(new TdApi.GetTimeZones(), (result, error) -> runOnUiThreadOptional(() -> {
      if (!isFocused()) {
        return;
      }
      if (error != null) {
        UI.showError(error);
        return;
      }
      askText(R.string.TgxBusinessZone, current != null ? current.timeZoneId : java.util.TimeZone.getDefault().getID(),
        false, zone -> chooseZone(current, result.timeZones, zone.trim()));
    }));
  }

  private void chooseZone (TdApi.BusinessOpeningHours current, TdApi.TimeZone[] zones, String query) {
    String normalized = query.toLowerCase(java.util.Locale.ROOT);
    ArrayList<TdApi.TimeZone> matches = new ArrayList<>();
    for (TdApi.TimeZone zone : zones) {
      if (query.equalsIgnoreCase(zone.id)) {
        editHours(current, zone.id);
        return;
      }
      if (!normalized.isEmpty() && (zone.id.toLowerCase(java.util.Locale.ROOT).contains(normalized) ||
          zone.name.toLowerCase(java.util.Locale.ROOT).contains(normalized))) {
        matches.add(zone);
      }
    }
    if (matches.isEmpty() || matches.size() > 25) {
      showConfirm(Lang.getString(R.string.TgxBusinessZoneRetry), Lang.getString(R.string.Continue),
        () -> askText(R.string.TgxBusinessZone, query, false, next -> chooseZone(current, zones, next.trim())));
      return;
    }
    Options.Builder options = new Options.Builder().info(Lang.getString(R.string.TgxBusinessZone));
    for (int i = 0; i < matches.size(); i++) {
      TdApi.TimeZone zone = matches.get(i);
      options.item(new OptionItem(i + 1, zone.name + " · " + zone.id, OptionColor.NORMAL, 0));
    }
    showOptions(options.build(), (view, id) -> {
      if (id > 0 && id <= matches.size()) {
        editHours(current, matches.get(id - 1).id);
      }
      return true;
    });
  }

  private void editHours (TdApi.BusinessOpeningHours current, String zone) {
    editHours(current, zone, BusinessHours.format(current));
  }

  private void editHours (TdApi.BusinessOpeningHours current, String zone, String initial) {
    askText(R.string.TgxBusinessHoursFormat, initial, true, text -> {
      TdApi.BusinessOpeningHoursInterval[] periods;
      try {
        periods = BusinessHours.parse(text);
        if (periods.length == 0) {
          throw new IllegalArgumentException();
        }
      } catch (IllegalArgumentException error) {
        showConfirm(Lang.getString(R.string.TgxBusinessInvalidHours), Lang.getString(R.string.TgxBusinessEdit),
          () -> editHours(current, zone, text));
        return;
      }
      confirmSave(() -> saveProfile(business -> business != null ? business.openingHours : null, current,
        new TdApi.SetBusinessOpeningHours(new TdApi.BusinessOpeningHours(zone, periods))));
    });
  }

  private void confirmSave (Runnable action) {
    showConfirm(Lang.getString(R.string.TgxBusinessSaveConfirm), Lang.getString(R.string.Save), action);
  }

  private void saveProfile (Function<TdApi.BusinessInfo, TdApi.Object> accessor, TdApi.Object previous,
                            TdApi.Function<TdApi.Ok> request) {
    if (busy || !isFocused() || tdlib.myUserId() == 0) {
      return;
    }
    busy = true;
    render();
    final long userId = tdlib.myUserId();
    tdlib.send(new TdApi.GetUserFullInfo(userId), (fresh, error) -> runOnUiThreadOptional(() -> {
      if (!isFocused() || userId != tdlib.myUserId() || error != null) {
        finish(error, false);
      } else if (!sameObject(previous, accessor.apply(fresh.businessInfo))) {
        finish(null, false);
      } else {
        tdlib.send(request, (ok, setError) -> runOnUiThreadOptional(() -> finish(setError, setError == null)));
      }
    }));
  }

  private static boolean sameObject (TdApi.Object first, TdApi.Object second) {
    if (first == second) {
      return true;
    }
    if (first == null || second == null || first.getConstructor() != second.getConstructor()) {
      return false;
    }
    if (first instanceof TdApi.BusinessStartPage a && second instanceof TdApi.BusinessStartPage b) {
      return Objects.equals(a.title, b.title) && Objects.equals(a.message, b.message) &&
        (a.sticker == null ? 0 : a.sticker.sticker.id) == (b.sticker == null ? 0 : b.sticker.sticker.id);
    }
    if (first instanceof TdApi.BusinessLocation a && second instanceof TdApi.BusinessLocation b) {
      return Objects.equals(a.address, b.address) && (a.location == b.location ||
        (a.location != null && b.location != null && a.location.latitude == b.location.latitude &&
          a.location.longitude == b.location.longitude));
    }
    if (first instanceof TdApi.BusinessOpeningHours a && second instanceof TdApi.BusinessOpeningHours b) {
      if (!Objects.equals(a.timeZoneId, b.timeZoneId) || a.openingHours.length != b.openingHours.length) {
        return false;
      }
      for (int i = 0; i < a.openingHours.length; i++) {
        if (a.openingHours[i].startMinute != b.openingHours[i].startMinute ||
            a.openingHours[i].endMinute != b.openingHours[i].endMinute) {
          return false;
        }
      }
      return true;
    }
    if (first instanceof TdApi.BusinessGreetingMessageSettings a && second instanceof TdApi.BusinessGreetingMessageSettings b) {
      return a.shortcutId == b.shortcutId && a.inactivityDays == b.inactivityDays && sameRecipients(a.recipients, b.recipients);
    }
    if (first instanceof TdApi.BusinessAwayMessageSettings a && second instanceof TdApi.BusinessAwayMessageSettings b) {
      return a.shortcutId == b.shortcutId && a.offlineOnly == b.offlineOnly && sameRecipients(a.recipients, b.recipients) &&
        sameSchedule(a.schedule, b.schedule);
    }
    return false;
  }

  private static boolean sameRecipients (TdApi.BusinessRecipients a, TdApi.BusinessRecipients b) {
    return a == b || (a != null && b != null && Arrays.equals(a.chatIds, b.chatIds) &&
      Arrays.equals(a.excludedChatIds, b.excludedChatIds) && a.selectContacts == b.selectContacts &&
      a.selectNonContacts == b.selectNonContacts && a.selectExistingChats == b.selectExistingChats &&
      a.selectNewChats == b.selectNewChats && a.excludeSelected == b.excludeSelected);
  }

  private static boolean sameSchedule (TdApi.BusinessAwayMessageSchedule a, TdApi.BusinessAwayMessageSchedule b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null || a.getConstructor() != b.getConstructor()) {
      return false;
    }
    if (a instanceof TdApi.BusinessAwayMessageScheduleCustom customA &&
        b instanceof TdApi.BusinessAwayMessageScheduleCustom customB) {
      return customA.startDate == customB.startDate && customA.endDate == customB.endDate;
    }
    return true;
  }

  private void showLink (TdApi.BusinessChatLink link) {
    Options.Builder options = new Options.Builder();
    options.info(link.link + "\n" + link.text.text);
    options.item(new OptionItem(R.id.btn_tgxBusinessCopy, Lang.getString(R.string.Copy), OptionColor.NORMAL, 0));
    options.item(new OptionItem(R.id.btn_tgxBusinessShare, Lang.getString(R.string.Share), OptionColor.NORMAL, 0));
    options.item(new OptionItem(R.id.btn_tgxBusinessEdit, Lang.getString(R.string.TgxBusinessEdit), OptionColor.NORMAL, 0));
    options.item(new OptionItem(R.id.btn_tgxBusinessRemove, Lang.getString(R.string.Delete), OptionColor.RED, 0));
    options.cancelItem();
    showOptions(options.build(), (view, id) -> {
      if (id == R.id.btn_tgxBusinessCopy) {
        UI.copyText(link.link, R.string.CopiedLink);
      } else if (id == R.id.btn_tgxBusinessShare) {
        ShareController share = new ShareController(context(), tdlib);
        share.setArguments(new ShareController.Args(link.link).setShare(link.link, null));
        share.show();
      } else if (id == R.id.btn_tgxBusinessEdit) {
        editLink(link);
      } else if (id == R.id.btn_tgxBusinessRemove) {
        showConfirm(Lang.getString(R.string.TgxBusinessDeleteLink), Lang.getString(R.string.Delete),
          () -> mutateLink(link, new TdApi.DeleteBusinessChatLink(link.link)));
      }
      return true;
    });
  }

  private void editLink (TdApi.BusinessChatLink link) {
    askText(R.string.TgxBusinessLinkTitle, link != null ? link.title : "", false, title ->
      askText(R.string.TgxBusinessLinkDraft, link != null ? link.text.text : "", true, draft -> {
        TdApi.FormattedText text = link != null && Objects.equals(link.text.text, draft) ? link.text :
          new TdApi.FormattedText(draft, new TdApi.TextEntity[0]);
        TdApi.InputBusinessChatLink updated = new TdApi.InputBusinessChatLink(text, title.trim());
        confirmSave(() -> {
          if (link == null) {
            mutateLink(null, new TdApi.CreateBusinessChatLink(updated));
          } else {
            mutateLink(link, new TdApi.EditBusinessChatLink(link.link, updated));
          }
        });
      }));
  }

  private <T extends TdApi.Object> void mutateLink (TdApi.BusinessChatLink expected, TdApi.Function<T> request) {
    if (busy || !isFocused() || tdlib.myUserId() == 0) {
      return;
    }
    busy = true;
    render();
    final long userId = tdlib.myUserId();
    tdlib.send(new TdApi.GetBusinessChatLinks(), (fresh, error) -> runOnUiThreadOptional(() -> {
      if (!isFocused() || userId != tdlib.myUserId() || error != null) {
        finish(error, false);
        return;
      }
      boolean canSave = expected == null ? fresh.links.length < tdlib.options().businessChatLinkCountMax :
        Arrays.stream(fresh.links).anyMatch(link -> Objects.equals(link.link, expected.link) &&
          Objects.equals(link.title, expected.title) && Td.equalsTo(link.text, expected.text));
      if (!canSave) {
        finish(null, false);
      } else {
        tdlib.send(request, (updated, setError) -> runOnUiThreadOptional(() -> finish(setError, setError == null)));
      }
    }));
  }

  private void finish (TdApi.Error error, boolean success) {
    busy = false;
    if (isFocused()) {
      if (error != null) {
        UI.showError(error);
      } else if (!success) {
        UI.showToast(R.string.TgxBusinessChanged, Toast.LENGTH_SHORT);
      }
    }
    refresh();
  }

  private void showConnectedBot () {
    tdlib.send(new TdApi.GetBusinessConnectedBot(), (connected, error) -> runOnUiThreadOptional(() -> {
      if (!isFocused()) {
        return;
      }
      if (error != null) {
        UI.showError(error);
        return;
      }
      if (connected.bot == null) {
        showConfirm(Lang.getString(R.string.TgxBusinessNoBot), Lang.getString(R.string.TgxBusinessConnectBot),
          () -> chooseBusinessBot(connected));
        return;
      }
      long botUserId = connected.bot.botUserId;
      String status = Lang.getString(R.string.TgxBusinessConnectedBot, tdlib.cache().userName(botUserId),
        Lang.dateYearShortTime(connected.connectionDate, TimeUnit.SECONDS));
      showOptions(status, new int[] {R.id.btn_businessBotEdit, R.id.btn_businessBotConnect, R.id.btn_tgxBusinessDisconnect, R.id.btn_cancel},
        new String[] {Lang.getString(R.string.TgxBusinessBotPermissions), Lang.getString(R.string.TgxBusinessReplaceBot),
          Lang.getString(R.string.TgxBusinessDisconnect), Lang.getString(R.string.Cancel)}, (view, id) -> {
            if (id == R.id.btn_businessBotEdit) {
              configureBotScope(connected, botUserId);
            } else if (id == R.id.btn_businessBotConnect) {
              chooseBusinessBot(connected);
            } else if (id == R.id.btn_tgxBusinessDisconnect) {
              showConfirm(Lang.getString(R.string.TgxBusinessDisconnectConfirm), Lang.getString(R.string.TgxBusinessDisconnect),
                () -> disconnectBot(botUserId, connected.connectionDate));
            }
            return true;
          });
    }));
  }

  private void chooseBusinessBot (TdApi.BusinessConnectedBotInfo expected) {
    askText(R.string.TgxBusinessBotUsername, "", false, input -> {
      String username = input.trim().replaceFirst("^@", "");
      if (!username.matches("[A-Za-z0-9_]{1,32}")) {
        UI.showToast(R.string.TgxBusinessBotNotEligible, Toast.LENGTH_SHORT);
        return;
      }
      tdlib.send(new TdApi.SearchPublicChat(username), (chat, error) -> runOnUiThreadOptional(() -> {
        if (!isFocused() || busy) {
          return;
        }
        if (error != null) {
          UI.showError(error);
          return;
        }
        TdApi.User bot = tdlib.chatUser(chat);
        if (bot == null || !(bot.type instanceof TdApi.UserTypeBot type) || !type.canConnectToBusiness) {
          UI.showToast(R.string.TgxBusinessBotNotEligible, Toast.LENGTH_SHORT);
          return;
        }
        configureBotScope(expected, bot.id);
      }));
    });
  }

  private void configureBotScope (TdApi.BusinessConnectedBotInfo expected, long botId) {
    boolean sameBot = expected != null && expected.bot != null && expected.bot.botUserId == botId;
    Options.Builder options = new Options.Builder().info(Lang.getString(R.string.TgxBusinessBotScope));
    if (sameBot) {
      options.item(new OptionItem(1, Lang.getString(R.string.TgxBusinessBotKeepScope), OptionColor.NORMAL, 0));
    }
    options.item(new OptionItem(2, Lang.getString(R.string.TgxBusinessBotContacts), OptionColor.NORMAL, 0));
    options.item(new OptionItem(3, Lang.getString(R.string.TgxBusinessBotNonContacts), OptionColor.NORMAL, 0));
    options.item(new OptionItem(4, Lang.getString(R.string.TgxBusinessBotAllChats), OptionColor.NORMAL, 0));
    options.item(new OptionItem(5, Lang.getString(R.string.TgxBusinessCustomRecipients), OptionColor.NORMAL, 0));
    options.cancelItem();
    showOptions(options.build(), (view, id) -> {
      if (id == 5) {
        TdApi.BusinessRecipients current = sameBot && expected.bot.recipients != null ? expected.bot.recipients :
          new TdApi.BusinessRecipients(new long[0], new long[0], false, false, false, false, false);
        openRecipients(current, true, value -> configureBotPermissions(expected, botId, value));
      } else if (id == 1 && sameBot) {
        configureBotPermissions(expected, botId, expected.bot.recipients);
      } else if (id >= 2 && id <= 4) {
        configureBotPermissions(expected, botId, new TdApi.BusinessRecipients(new long[0], new long[0],
          true, true, id != 3, id != 2, false));
      }
      return true;
    });
  }

  private void openRecipients (TdApi.BusinessRecipients source, boolean forBot, RunnableData<TdApi.BusinessRecipients> done) {
    navigateTo(new BusinessRecipientsController(context(), tdlib, source, forBot, done));
  }

  private void configureBotPermissions (TdApi.BusinessConnectedBotInfo expected, long botId, TdApi.BusinessRecipients recipients) {
    TdApi.BusinessBotRights old = expected != null && expected.bot != null && expected.bot.botUserId == botId
      ? expected.bot.rights : new TdApi.BusinessBotRights();
    if (old == null) {
      old = new TdApi.BusinessBotRights();
    }
    int[] labels = {
      R.string.TgxBusinessBotReply, R.string.TgxBusinessBotRead, R.string.TgxBusinessBotDeleteSent,
      R.string.TgxBusinessBotDeleteAll, R.string.TgxBusinessBotEditName, R.string.TgxBusinessBotEditBio,
      R.string.TgxBusinessBotEditPhoto, R.string.TgxBusinessBotEditUsername, R.string.TgxBusinessBotViewGifts,
      R.string.TgxBusinessBotSellGifts, R.string.TgxBusinessBotGiftSettings, R.string.TgxBusinessBotTransferGifts,
      R.string.TgxBusinessBotTransferStars, R.string.TgxBusinessBotStories
    };
    boolean[] previous = botRights(old);
    ListItem[] fields = new ListItem[labels.length];
    int[] ids = new int[labels.length];
    for (int i = 0; i < labels.length; i++) {
      ids[i] = androidx.core.view.ViewCompat.generateViewId();
      fields[i] = new ListItem(ListItem.TYPE_CHECKBOX_OPTION, ids[i], 0, labels[i], previous[i]);
    }
    showSettings(new SettingsWrapBuilder(R.id.btn_businessBotEdit).setRawItems(fields).setSaveStr(R.string.Continue)
      .setIntDelegate((id, result) -> {
        boolean[] selected = new boolean[ids.length];
        ArrayList<String> granted = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
          selected[i] = result.get(ids[i]) != 0;
          if (selected[i]) {
            granted.add(Lang.getString(labels[i]));
          }
        }
        TdApi.BusinessBotRights rights = new TdApi.BusinessBotRights(selected[0], selected[1], selected[2], selected[3],
          selected[4], selected[5], selected[6], selected[7], selected[8], selected[9], selected[10], selected[11],
          selected[12], selected[13]);
        String permissions = granted.isEmpty() ? Lang.getString(R.string.TgxBusinessBotNoRights) :
          android.text.TextUtils.join("\n", granted);
        showConfirm(Lang.getString(R.string.TgxBusinessBotConfirm, tdlib.cache().userName(botId), permissions),
          Lang.getString(R.string.Save), () -> saveBusinessBot(expected, new TdApi.BusinessConnectedBot(botId, recipients, rights)));
      }));
  }

  private static boolean[] botRights (TdApi.BusinessBotRights rights) {
    return new boolean[] {rights.canReply, rights.canReadMessages, rights.canDeleteSentMessages, rights.canDeleteAllMessages,
      rights.canEditName, rights.canEditBio, rights.canEditProfilePhoto, rights.canEditUsername, rights.canViewGiftsAndStars,
      rights.canSellGifts, rights.canChangeGiftSettings, rights.canTransferAndUpgradeGifts, rights.canTransferStars,
      rights.canManageStories};
  }

  private static boolean sameBotConnection (TdApi.BusinessConnectedBotInfo a, TdApi.BusinessConnectedBotInfo b) {
    TdApi.BusinessConnectedBot first = a != null ? a.bot : null;
    TdApi.BusinessConnectedBot second = b != null ? b.bot : null;
    if (first == null || second == null) {
      return first == second;
    }
    return first.botUserId == second.botUserId && a.connectionDate == b.connectionDate &&
      sameRecipients(first.recipients, second.recipients) &&
      (first.rights == second.rights || (first.rights != null && second.rights != null &&
        Arrays.equals(botRights(first.rights), botRights(second.rights))));
  }

  private void saveBusinessBot (TdApi.BusinessConnectedBotInfo expected, TdApi.BusinessConnectedBot updated) {
    long userId = tdlib.myUserId();
    if (busy || !isFocused() || userId == 0) {
      return;
    }
    busy = true;
    render();
    tdlib.send(new TdApi.GetBusinessConnectedBot(), (fresh, error) -> runOnUiThreadOptional(() -> {
      if (!isFocused() || userId != tdlib.myUserId() || error != null || !sameBotConnection(expected, fresh)) {
        finish(error, false);
        return;
      }
      tdlib.send(new TdApi.SetBusinessConnectedBot(updated), (ok, saveError) ->
        runOnUiThreadOptional(() -> finish(saveError, saveError == null)));
    }));
  }

  private void disconnectBot (long expectedBotId, int expectedConnectionDate) {
    if (busy || !isFocused() || tdlib.myUserId() == 0) {
      return;
    }
    busy = true;
    final long userId = tdlib.myUserId();
    render();
    tdlib.send(new TdApi.GetBusinessConnectedBot(), (current, error) -> runOnUiThreadOptional(() -> {
      if (error != null || !isFocused() || userId != tdlib.myUserId() ||
          current.bot == null || current.bot.botUserId != expectedBotId || current.connectionDate != expectedConnectionDate) {
        finish(error, false);
      } else {
        tdlib.send(new TdApi.DeleteBusinessConnectedBot(expectedBotId), (ok, deleteError) ->
          runOnUiThreadOptional(() -> finish(deleteError, deleteError == null)));
      }
    }));
  }

  private void openOfficial () {
    showConfirm(Lang.getString(R.string.TgxBusinessOfficial), Lang.getString(R.string.Continue),
      () -> TelegramOfficialLink.open(this, new TdApi.InternalLinkTypeSettings(new TdApi.SettingsSectionBusiness(""))));
  }
}
