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

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.emoji.GifView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGGif;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.HashSet;

import me.vkryl.core.StringUtils;

public final class PremiumSettingsController extends RecyclerViewController<Void>
  implements View.OnClickListener, TdlibCache.MyUserDataChangeListener {
  private static final String PREMIUM_FAQ = "https://telegram.org/faq_premium";
  private static final String DIRECT_TELEGRAM = "org.telegram.messenger.web";
  private static final String PLAY_TELEGRAM = "org.telegram.messenger";
  private static final int MAIN = 0, LIMITS = 1, BENEFIT = 2;

  private final TdApi.PremiumSource source;
  private SettingsAdapter adapter;
  private TdApi.PremiumFeatures catalog;
  private TdApi.PremiumState subscription;
  private TdApi.PremiumFeature selectedBenefit;
  private boolean knownPremium;
  private boolean loadingCatalog, loadingSubscription, checkingOffer;
  private boolean catalogError, subscriptionError;
  private boolean returningFromCheckout;
  private int section = MAIN;
  private int overviewScrollPosition;

  public PremiumSettingsController (Context context, Tdlib tdlib, TdApi.PremiumSource source) {
    super(context, tdlib);
    this.source = source;
    if (source instanceof TdApi.PremiumSourceFeature openedFromFeature) {
      selectedBenefit = openedFromFeature.feature;
      if (selectedBenefit != null) {
        section = BENEFIT;
      }
    }
  }

  @Override
  public int getId () {
    return R.id.controller_premiumSettings;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.TgxPremiumTitle);
  }

  @Override
  public boolean saveInstanceState (Bundle outState, String keyPrefix) {
    // The source can contain a referrer. Reopening restores the dashboard and reads fresh data.
    return true;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getData() instanceof TdApi.PremiumStatePaymentOption plan) {
          view.setData(planDetails(plan));
        } else if (item.getData() instanceof TdApi.PremiumLimit limit) {
          view.setData(Lang.getString(R.string.TgxPremiumLimitValues, Lang.formatNumber(limit.defaultValue),
            Lang.formatNumber(limit.premiumValue)));
        }
      }

      @Override
      protected SettingHolder initCustom (ViewGroup parent) {
        GifView preview = new GifView(parent.getContext());
        return new SettingHolder(preview) {
          @Override
          public void attach () {
            preview.attach();
          }

          @Override
          public void detach () {
            preview.detach();
          }

          @Override
          public void destroy () {
            preview.destroy();
          }
        };
      }

      @Override
      protected void setCustom (ListItem item, SettingHolder holder, int position) {
        GifView preview = (GifView) holder.itemView;
        TGGif gif = (TGGif) item.getData();
        preview.setGif(gif);
        preview.setContentDescription(Lang.getString(R.string.TgxPremiumPreviewDescription,
          Lang.getString(PremiumCatalog.featureTitle(selectedBenefit))));
      }
    };
    recyclerView.setAdapter(adapter);
    tdlib.cache().addMyUserListener(this);
    knownPremium = tdlib.hasPremium();
    reload();
  }

  @Override
  public void destroy () {
    tdlib.cache().removeMyUserListener(this);
    super.destroy();
  }

  @Override
  public void onActivityResume () {
    super.onActivityResume();
    if (returningFromCheckout && adapter != null) {
      returningFromCheckout = false;
      reload();
    }
  }

  @Override
  public void onMyUserUpdated (TdApi.User myUser) {
    runOnUiThreadOptional(() -> {
      if (knownPremium != tdlib.hasPremium()) {
        knownPremium = tdlib.hasPremium();
        reload();
      }
    });
  }

  private void reload () {
    if (!loadingCatalog) {
      loadingCatalog = true;
      catalogError = false;
      tdlib.send(new TdApi.GetPremiumFeatures(source), (loaded, error) -> runOnUiThreadOptional(() -> {
        loadingCatalog = false;
        catalogError = error != null;
        if (loaded != null) {
          catalog = loaded;
        }
        render();
      }));
    }
    if (!loadingSubscription && !checkingOffer) {
      loadingSubscription = true;
      subscriptionError = false;
      tdlib.send(new TdApi.GetPremiumState(), (loaded, error) -> runOnUiThreadOptional(() -> {
        loadingSubscription = false;
        subscriptionError = error != null;
        if (loaded != null) {
          subscription = loaded;
        }
        render();
      }));
    }
    render();
  }

  private void render () {
    ArrayList<ListItem> items = new ArrayList<>();
    if (section == LIMITS) {
      renderLimits(items);
    } else if (section == BENEFIT && selectedBenefit != null) {
      renderBenefit(items, selectedBenefit);
    } else {
      renderOverview(items);
    }
    adapter.setItems(items, false);
  }

  private void renderOverview (ArrayList<ListItem> items) {
    boolean premium = tdlib.hasPremium();
    CharSequence status = Lang.getString(premium ? R.string.TgxPremiumActive : R.string.TgxPremiumInactive);
    items.add(new ListItem(ListItem.TYPE_ICONIZED_EMPTY, 0, R.drawable.baseline_premium_star_96, status));
    if (subscription != null && subscription.state != null && !StringUtils.isEmpty(subscription.state.text)) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, TD.toCharSequence(subscription.state)));
    }

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.TgxPremiumPlans));
    boolean foundPlan = false, hasCheckout = false;
    if (subscription != null) {
      for (TdApi.PremiumStatePaymentOption plan : subscription.paymentOptions) {
        if (!PremiumCatalog.isDisplayablePlan(plan)) {
          continue;
        }
        foundPlan = true;
        hasCheckout |= PremiumCatalog.canOpenPlan(plan, premium);
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, plan.isCurrent ? 0 : R.id.btn_premiumPlan, 0,
          planTitle(plan)).setData(plan));
      }
    }
    if (!foundPlan && !loadingSubscription && !subscriptionError) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.TgxPremiumPlansUnavailable));
    }
    if (premium) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.TgxPremiumManageInfo));
    } else if (!hasCheckout && !catalogError && !loadingCatalog && !loadingSubscription) {
      if (PremiumCatalog.isPaymentLink(catalog != null ? catalog.paymentLink : null)) {
        items.add(new ListItem(ListItem.TYPE_BUTTON, R.id.btn_premiumContinue, 0, R.string.TgxPremiumViewOptions));
      } else if (foundPlan) {
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.TgxPremiumCheckoutUnavailable));
      }
    }

    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.TgxPremiumFeatures));
    if (catalog != null) {
      HashSet<Integer> shown = new HashSet<>();
      for (TdApi.PremiumFeature benefit : catalog.features) {
        if (benefit != null && shown.add(benefit.getConstructor())) {
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_premiumFeature, R.drawable.baseline_premium_star_24,
            PremiumCatalog.featureTitle(benefit)).setData(benefit));
        }
      }
      if (shown.isEmpty()) {
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.TgxPremiumNoFeatures));
      }
      if (catalog.limits.length > 0) {
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_premiumLimits, 0, R.string.TgxPremiumLimits));
      }
    }
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.TgxPremiumCapabilities));
    addLoadStatus(items);
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_premiumFaq, 0, R.string.TgxPremiumAbout));
  }

  private void addLoadStatus (ArrayList<ListItem> items) {
    if (catalogError || subscriptionError) {
      if (catalogError) {
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.TgxPremiumFeaturesError));
      }
      if (subscriptionError) {
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.TgxPremiumStateError));
      }
    }
    if (loadingCatalog || loadingSubscription || checkingOffer) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.LoadingInformation));
    } else {
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_premiumRetry, 0,
        catalogError || subscriptionError ? R.string.TgxPremiumRetry : R.string.TgxPremiumRefresh));
    }
  }

  private void renderLimits (ArrayList<ListItem> items) {
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.TgxPremiumLimits));
    if (catalog != null) {
      HashSet<Integer> shown = new HashSet<>();
      for (TdApi.PremiumLimit limit : catalog.limits) {
        if (limit != null && limit.type != null && shown.add(limit.type.getConstructor())) {
          items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, 0, 0, PremiumCatalog.limitTitle(limit.type))
            .setData(limit));
        }
      }
      if (shown.isEmpty()) {
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.TgxPremiumNoLimits));
      }
    }
    addLoadStatus(items);
  }

  private void renderBenefit (ArrayList<ListItem> items, TdApi.PremiumFeature benefit) {
    items.add(new ListItem(ListItem.TYPE_HEADER_MULTILINE, 0, 0, PremiumCatalog.featureTitle(benefit)));
    int details = PremiumCatalog.featureDescription(benefit);
    if (details != 0) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, details));
    }
    if (subscription != null) {
      for (TdApi.PremiumFeaturePromotionAnimation preview : subscription.animations) {
        if (preview != null && preview.feature != null && preview.feature.getConstructor() == benefit.getConstructor() &&
            preview.animation != null && preview.animation.animation != null) {
          TGGif gif = new TGGif(tdlib, preview.animation);
          gif.getGif().setScaleType(GifFile.FIT_CENTER);
          if (gif.getImage() != null) {
            gif.getImage().setScaleType(ImageFile.FIT_CENTER);
          }
          items.add(new ListItem(ListItem.TYPE_CUSTOM_SINGLE, R.id.btn_premiumPreview).setData(gif));
          break;
        }
      }
    }
    if (benefit instanceof TdApi.PremiumFeatureIncreasedLimits) {
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_premiumLimits, 0, R.string.TgxPremiumLimits));
    } else if (benefit instanceof TdApi.PremiumFeatureAccentColor && tdlib.hasPremium()) {
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_premiumNameColor, 0, R.string.TgxPremiumOpenColors));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_premiumProfileColor, 0, R.string.TgxPremiumOpenProfileColors));
    } else if (benefit instanceof TdApi.PremiumFeatureMessagePrivacy && tdlib.canSetNewChatPrivacySettings()) {
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_premiumPrivacy, 0, R.string.TgxPremiumOpenPrivacy));
    } else if (benefit instanceof TdApi.PremiumFeatureAdvancedChatManagement && Settings.instance().chatFoldersEnabled()) {
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_premiumFolders, 0, R.string.TgxPremiumOpenFolders));
    }
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.TgxPremiumCapabilities));
    addLoadStatus(items);
  }

  @Override
  public boolean performOnBackPressed (boolean fromTop, boolean commit) {
    if (section != MAIN) {
      if (commit) {
        section = section == LIMITS && selectedBenefit != null ? BENEFIT : MAIN;
        render();
        getRecyclerView().scrollToPosition(section == MAIN ? overviewScrollPosition : 0);
      }
      return true;
    }
    return super.performOnBackPressed(fromTop, commit);
  }

  @Override
  public boolean canSlideBackFrom (NavigationController navigation, float x, float y) {
    return section == MAIN && super.canSlideBackFrom(navigation, x, y);
  }

  @Override
  public void onClick (View view) {
    int id = view.getId();
    if (id == R.id.btn_premiumRetry) {
      reload();
    } else if (id == R.id.btn_premiumFaq) {
      Intents.openLink(PREMIUM_FAQ);
    } else if (id == R.id.btn_premiumFeature && view.getTag() instanceof ListItem row &&
               row.getData() instanceof TdApi.PremiumFeature benefit) {
      selectBenefit(benefit);
    } else if (id == R.id.btn_premiumLimits) {
      if (section == MAIN) {
        selectedBenefit = null;
        rememberOverviewPosition();
      }
      section = LIMITS;
      render();
      getRecyclerView().scrollToPosition(0);
    } else if (id == R.id.btn_premiumPlan && view.getTag() instanceof ListItem row &&
               row.getData() instanceof TdApi.PremiumStatePaymentOption plan) {
      confirmPlan(plan);
    } else if (id == R.id.btn_premiumContinue) {
      confirmCatalogCheckout();
    } else if (id == R.id.btn_premiumNameColor) {
      tdlib.ui().showUserAccentColorOptions(this);
    } else if (id == R.id.btn_premiumProfileColor) {
      tdlib.ui().showUserProfileAccentColorOptions(this);
    } else if (id == R.id.btn_premiumPrivacy) {
      SettingsPrivacyController privacy = new SettingsPrivacyController(context(), tdlib);
      privacy.addOneShotFocusListener(privacy::showNewChatPrivacyChoices);
      navigateTo(privacy);
    } else if (id == R.id.btn_premiumFolders && Settings.instance().chatFoldersEnabled()) {
      navigateTo(new SettingsFoldersController(context(), tdlib));
    }
  }

  private void rememberOverviewPosition () {
    if (getRecyclerView().getLayoutManager() instanceof LinearLayoutManager manager) {
      overviewScrollPosition = Math.max(0, manager.findFirstVisibleItemPosition());
    }
  }

  private void selectBenefit (TdApi.PremiumFeature benefit) {
    rememberOverviewPosition();
    selectedBenefit = benefit;
    section = BENEFIT;
    tdlib.send(new TdApi.ViewPremiumFeature(benefit), (ok, error) -> { });
    render();
    getRecyclerView().scrollToPosition(0);
  }

  private static String planTitle (TdApi.PremiumStatePaymentOption plan) {
    return Lang.plural(R.string.xMonths, plan.paymentOption.monthCount);
  }

  private static String planAmount (TdApi.PremiumStatePaymentOption plan) {
    return PremiumCatalog.formatAmount(plan.paymentOption, Lang.dateFormatLocale());
  }

  private static String planDetails (TdApi.PremiumStatePaymentOption plan) {
    String amount = planAmount(plan);
    if (plan.isCurrent) {
      return amount + " · " + Lang.getString(R.string.TgxPremiumCurrent);
    }
    if (plan.isUpgrade) {
      amount += " · " + Lang.getString(R.string.TgxPremiumUpgrade);
    }
    if (plan.paymentOption.discountPercentage > 0) {
      return amount + " · " + Lang.getString(R.string.TgxPremiumDiscount, plan.paymentOption.discountPercentage);
    }
    return amount;
  }

  private void confirmPlan (TdApi.PremiumStatePaymentOption plan) {
    if (checkingOffer || loadingSubscription || plan.isCurrent) {
      return;
    }
    if (subscriptionError || !PremiumCatalog.canOpenPlan(plan, tdlib.hasPremium())) {
      UI.showToast(R.string.TgxPremiumCheckoutUnavailable, Toast.LENGTH_SHORT);
      return;
    }
    showConfirm(Lang.getString(R.string.TgxPremiumConfirmPlan, planTitle(plan), planAmount(plan)),
      Lang.getString(R.string.Continue), () -> recheckPlan(plan));
  }

  private void recheckPlan (TdApi.PremiumStatePaymentOption expected) {
    if (checkingOffer || loadingSubscription || !isFocused()) {
      return;
    }
    long accountUserId = tdlib.myUserId();
    checkingOffer = true;
    render();
    tdlib.send(new TdApi.GetPremiumState(), (fresh, error) -> runOnUiThreadOptional(() -> {
      checkingOffer = false;
      subscriptionError = error != null;
      if (fresh != null) {
        subscription = fresh;
      }
      render();
      if (!isFocused() || accountUserId == 0 || accountUserId != tdlib.myUserId()) {
        return;
      }
      if (error != null) {
        UI.showError(error);
        return;
      }
      for (TdApi.PremiumStatePaymentOption current : fresh.paymentOptions) {
        if (PremiumCatalog.canOpenPlan(current, tdlib.hasPremium()) && PremiumCatalog.sameOffer(expected, current)) {
          openCheckout(current.paymentOption.paymentLink);
          return;
        }
      }
      UI.showToast(R.string.TgxPremiumPlanChanged, Toast.LENGTH_SHORT);
    }));
  }

  private void confirmCatalogCheckout () {
    if (checkingOffer || loadingCatalog || catalogError || tdlib.hasPremium()) {
      return;
    }
    showConfirm(Lang.getString(R.string.TgxPremiumOfficialApp), Lang.getString(R.string.Continue), () -> {
      if (checkingOffer || loadingCatalog || !isFocused() || tdlib.hasPremium()) {
        return;
      }
      long accountUserId = tdlib.myUserId();
      checkingOffer = true;
      render();
      tdlib.send(new TdApi.GetPremiumFeatures(null), (fresh, error) -> runOnUiThreadOptional(() -> {
        checkingOffer = false;
        catalogError = error != null;
        if (fresh != null) {
          catalog = fresh;
        }
        render();
        if (!isFocused() || tdlib.hasPremium() || accountUserId == 0 || accountUserId != tdlib.myUserId()) {
          return;
        }
        if (error != null) {
          UI.showError(error);
        } else if (PremiumCatalog.isPaymentLink(fresh.paymentLink)) {
          openCheckout(fresh.paymentLink);
        } else {
          UI.showToast(R.string.TgxPremiumCheckoutUnavailable, Toast.LENGTH_SHORT);
        }
      }));
    });
  }

  private void openCheckout (TdApi.InternalLinkType link) {
    if (!PremiumCatalog.isPaymentLink(link)) {
      return;
    }
    tdlib.send(new TdApi.ClickPremiumSubscriptionButton(), (ok, error) -> { });
    Uri.Builder url = new Uri.Builder().scheme("tg");
    if (link instanceof TdApi.InternalLinkTypeBotStart bot) {
      url.authority("resolve").appendQueryParameter("domain", bot.botUsername);
      if (!StringUtils.isEmpty(bot.startParameter)) {
        url.appendQueryParameter("start", bot.startParameter);
      }
    } else if (link instanceof TdApi.InternalLinkTypeInvoice invoice) {
      url.authority("invoice").appendQueryParameter("slug", invoice.invoiceName);
    }
    Uri checkout = url.build();
    returningFromCheckout = openOfficialClient(checkout, DIRECT_TELEGRAM) || openOfficialClient(checkout, PLAY_TELEGRAM);
    if (!returningFromCheckout) {
      showConfirm(Lang.getString(R.string.TgxPremiumCheckoutUnavailable), Lang.getString(R.string.TgxPremiumAbout),
        () -> Intents.openLink(PREMIUM_FAQ));
    }
  }

  private boolean openOfficialClient (Uri url, String appPackage) {
    try {
      context().startActivity(new Intent(Intent.ACTION_VIEW, url).setPackage(appPackage));
      return true;
    } catch (ActivityNotFoundException | SecurityException ignored) {
      return false;
    }
  }
}
