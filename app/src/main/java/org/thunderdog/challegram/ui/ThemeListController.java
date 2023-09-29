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
 * File created on 10/11/2018
 */
package org.thunderdog.challegram.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.style.ClickableSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.FillingDrawable;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.base.TogglerView;
import org.thunderdog.challegram.component.chat.Waveform;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.component.user.RemoveHelper;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.core.LangUtils;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.mediaview.SliderView;
import org.thunderdog.challegram.mediaview.paint.ColorPaletteView;
import org.thunderdog.challegram.mediaview.paint.widget.ColorPreviewView;
import org.thunderdog.challegram.mediaview.paint.widget.ColorToneView;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.PropertyId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColors;
import org.thunderdog.challegram.theme.ThemeCustom;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.theme.ThemeInfo;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.CustomTypefaceSpan;
import org.thunderdog.challegram.util.DrawModifier;
import org.thunderdog.challegram.util.LineDrawModifier;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.Letters;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.v.HeaderEditText;
import org.thunderdog.challegram.widget.CheckBoxView;
import org.thunderdog.challegram.widget.CircleButton;
import org.thunderdog.challegram.widget.ListInfoView;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.NonMaterialButton;
import org.thunderdog.challegram.widget.ProgressComponentView;
import org.thunderdog.challegram.widget.RadioView;
import org.thunderdog.challegram.widget.SimplestCheckBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.lambda.RunnableInt;
import me.vkryl.td.Td;

public class ThemeListController extends RecyclerViewController<ThemeListController.Args> implements Menu, View.OnClickListener, ClipboardManager.OnPrimaryClipChangedListener, View.OnLongClickListener {
  public static class Args {
    private final ThemeInfo theme;
    private int specificSectionId;
    private ThemeController lookupParent;

    public Args (ThemeInfo theme, int specificSectionId) {
      this.theme = theme;
      this.specificSectionId = specificSectionId;
    }

    public Args setLookupParent (ThemeController parent) {
      this.lookupParent = parent;
      return this;
    }
  }

  public ThemeListController (@NonNull Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_themeColors;
  }

  @Override
  public boolean allowThemeChanges () {
    return false;
  }

  @Override
  protected int getMenuId () {
    return isLookupMode ? R.id.menu_clear : 0;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (isLookupMode && id == R.id.menu_clear) {
      header.addClearButton(menu, this);
      return;
    }
    super.fillMenuItems(id, header, menu);
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (isLookupMode && id == R.id.menu_btn_clear) {
      ((HeaderEditText) headerCell).setText("");
      return;
    }
    super.onMenuItemPressed(id, view);
  }

  private SettingsAdapter adapter;
  // private DoubleHeaderView headerCell;

  /*@Override
  public View getCustomHeaderCell () {
    return headerCell;
  }*/

  private final float[] hsv = new float[3];

  private String getColorRepresentation (int color, boolean needFull) {
    Color.colorToHSV(color, hsv);
    return Strings.getColorRepresentation(Settings.instance().getColorFormat(), hsv[0], hsv[1], hsv[2], color, needFull);
  }

  @Override
  public void onPrimaryClipChanged () {
    if (colorPicker != null) {
      int i = adapter.indexOfView(colorPicker, editPosition);
      if (i == -1)
        return;
      ListItem item = adapter.getItems().get(i);
      View view = getRecyclerView().getLayoutManager().findViewByPosition(i);
      if (view instanceof ViewGroup && view.getTag() == item) {
        NonMaterialButton btn = view.findViewById(R.id.btn_colorPaste);
        if (btn != null) {
          btn.setEnabled(canPasteColor());
        }
      }
    }
  }

  public boolean highlightColor (int colorId) {
    int i = findColorCell(colorId);
    if (i == -1)
      return false;
    if (editPosition != i)
      setEditPosition(i, colorId, false);
    ((LinearLayoutManager) getRecyclerView().getLayoutManager()).scrollToPositionWithOffset(editPosition, 0);
    return true;
  }

  // private final float[] hsv = new float[3];

  private static ViewGroup findColorParent (View view) {
    do {
      if (view.getTag() instanceof ListItem)
        return ((ListItem) view.getTag()).getViewType() == ListItem.TYPE_COLOR_PICKER ? (ViewGroup) view : null;
      ViewParent parent = view.getParent();
      if (parent instanceof View) {
        view = (View) parent;
      } else {
        return null;
      }
    } while (true);
  }

  private int getPasteColor () {
    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    if (clipboard == null)
      throw new IllegalStateException();
    if (!clipboard.hasPrimaryClip() || !clipboard.getPrimaryClipDescription().hasMimeType("text/plain"))
      throw new IllegalStateException();
    ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
    CharSequence text = item.getText();
    if (text == null || text.length() > 256)
      throw new IllegalArgumentException();
    String input = text.toString().trim().toLowerCase();
    int equal = input.indexOf(':');
    if (equal != -1)
      input = input.substring(equal + 1).trim();
    return parseAnyColor(input);
  }

  private boolean canPasteColor () {
    try {
      getPasteColor();
      return true;
    } catch (IllegalArgumentException ignored) {
    } catch (Throwable t) {
      Log.w("Some error with Clipboard API");
    }
    return false;
  }

  private Pattern rgbHslPattern;

  private static int parseRgbArgument (String v) {
    int result;
    if (v.endsWith("%"))
      result = (int) ((float) Integer.parseInt(v.substring(0, v.length() - 1)) / 100f * 255f);
    else
      result = Integer.parseInt(v);
    if (result > 255 || result < 0)
      throw new IllegalArgumentException();
    return result;
  }

  private static int parseAlpha (String v) {
    if (v.endsWith("%"))
      return parseRgbArgument(v);
    float value = Float.parseFloat(v);
    if (value < 0f || value > 1f)
      throw new IllegalArgumentException();
    return (int) (255f * value);
  }

  private int parseAnyColor (String v) {
    v = v.trim();
    if (StringUtils.isEmpty(v))
      throw new IllegalArgumentException();

    if (v.startsWith("#"))
      return ColorUtils.parseHexColor(v.substring(1), false);
    boolean foundLitter = false;
    for (int i = 0; i < v.length(); ) {
      int codePoint = v.codePointAt(i);
      if (!((codePoint >= 'a' && codePoint <= 'z') || (codePoint >= 'A' && codePoint <= 'Z') || (codePoint >= '0' && codePoint <= '9') || codePoint == '_')) {
        foundLitter = true;
        break;
      }
      i += Character.charCount(codePoint);
    }
    if (!foundLitter) {
      int colorId = Theme.getIdResourceIdentifier("theme_color_" + v);
      if (colorId != 0) {
        try {
          return getTheme().getColor(colorId);
        } catch (Throwable ignored) {}
      }
      return ColorUtils.parseHexColor(v, true);
    }

    if (rgbHslPattern == null)
      rgbHslPattern = Pattern.compile("^(?:\\s+)?([A-Za-z]{0,3}[Aa]?)(?:\\s+)?\\((?:\\s+)?([\\d.]+%?)(?:\\s+)?,(?:\\s+)?([\\d.]+%?)(?:\\s+)?,(?:\\s+)?([\\d.]+%?)(?:\\s+)?(?:,(?:\\s+)?([\\d.]+%?)(?:\\s+)?)?\\)(?:\\s+)?$");
    Matcher matcher = rgbHslPattern.matcher(v);
    if (!matcher.find())
      throw new IllegalArgumentException();

    int groupCount = matcher.groupCount();
    if (groupCount < 5)
      throw new IllegalArgumentException();
    String type = matcher.group(1).toLowerCase();
    String arg1 = matcher.group(2);
    String arg2 = matcher.group(3);
    String arg3 = matcher.group(4);
    String arg4 = groupCount > 5 ? matcher.group(5) : null;
    int alpha = arg4 != null ? parseAlpha(arg4) : 255;
    switch (type) {
      case "rgb":
        return Color.argb(alpha, parseRgbArgument(arg1), parseRgbArgument(arg2), parseRgbArgument(arg3));
      case "hsl":
        float hue = Float.parseFloat(arg1);
        float saturation = Float.parseFloat(arg2.endsWith("%") ? arg2.substring(0, arg2.length() - 1) : arg2) / 100f;
        float lightness = Float.parseFloat(arg3.endsWith("%") ? arg3.substring(0, arg3.length() - 1) : arg3) / 100f;
        hsv[0] = hue;
        hsv[1] = saturation;
        hsv[2] = lightness;
        return Color.HSVToColor(alpha, hsv);
    }
    throw new IllegalArgumentException();
  }

  private View headerCell;

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }

  @Nullable
  private ThemeController findThemeController () {
    ViewController<?> c = getParentOrSelf();
    return c instanceof ThemeController ? (ThemeController) c : null;
  }

  private boolean isLookupMode;

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    this.isLookupMode = args.lookupParent != null;
  }

  @Override
  protected int getHeaderColorId () {
    return isLookupMode ? ColorId.filling : super.getHeaderColorId();
  }

  @Override
  protected int getHeaderIconColorId () {
    return isLookupMode ? ColorId.icon : super.getHeaderIconColorId();
  }

  @Override
  protected int getHeaderTextColorId () {
    return isLookupMode ? ColorId.text : super.getHeaderTextColorId();
  }

  @Override
  protected void handleLanguageDirectionChange () {
    super.handleLanguageDirectionChange();
    if (isLookupMode) {
      HeaderView.updateEditTextDirection(headerCell, Screen.dp(68f), Screen.dp(49f));
    }
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    Args args = getArgumentsStrict();

    if (args.specificSectionId == R.id.theme_category_settings) {
      DoubleHeaderView headerCell = new DoubleHeaderView(context);
      headerCell.setThemedTextColor(this);
      headerCell.initWithMargin(0, true);
      headerCell.setTitle(args.theme.getName());
      headerCell.setSubtitle(R.string.ThemeAdvanced);
      this.headerCell = headerCell;
    } else if (isLookupMode) {
      HeaderEditText editText = HeaderView.genGreySearchHeader(recyclerView, this);
      editText.setHint(Lang.getString(bindLocaleChanger(getSearchHint(), editText, true, false)));
      editText.addTextChangedListener(new TextWatcher() {
        @Override
        public void beforeTextChanged (CharSequence s, int start, int count, int after) { }

        @Override
        public void afterTextChanged (Editable s) { }

        @Override
        public void onTextChanged (CharSequence s, int start, int before, int count) {
          searchColors(s.toString().trim().toLowerCase(), null);
          if (headerView != null) {
            headerView.updateMenuClear(R.id.menu_clear, R.id.menu_btn_clear, s.length() > 0, true);
          }
        }
      });
      setLockFocusView(editText, true);
      this.headerCell = editText;
    }

    adapter = new SettingsAdapter(this) {
      @Override
      public void updateView (SettingHolder holder, int position, int viewType) {
        setChildAlpha(holder.itemView);
        super.updateView(holder, position, viewType);
      }

      @Override
      public void onPressStateChanged (NonMaterialButton btn, boolean isPressed) {
        if (!isInTransparentMode())
          return;
        if ((btn.getId() == R.id.btn_colorUndo || btn.getId() == R.id.btn_colorRedo) && opaqueChild == btn.getParent()) {
          if (isPressed) {
            cancelClearTransparentMode();
          } else {
            clearTransparentModeDelayed();
          }
        }
      }

      @Override
      protected void setInfo (ListItem item, int position, ListInfoView infoView) {
        final int resId;
        final int specificSectionId = getArgumentsStrict().specificSectionId;
        if (specificSectionId == 0 ||
          specificSectionId == R.id.theme_category_bubbles ||
          specificSectionId == R.id.theme_category_navigation) {
          resId = R.string.xItem;
        } else if (specificSectionId == R.id.theme_category_settings) {
          resId = R.string.xProperty;
        } else {
          resId = R.string.xColors;
        }
        infoView.showInfo(Lang.pluralBold(resId, itemCount));
      }

      @Override
      protected void modifyCustom (SettingHolder holder, int position, ListItem item, int customViewType, View view, boolean isUpdate) {
        super.updateView(holder, position, ListItem.TYPE_VALUED_SETTING_COMPACT);
        int id = getDataId(item);
        switch (customViewType) {
          case VIEW_TYPE_INLINE_OUTLINE:
            /*int backgroundColorId = id == ColorId.bubbleOut_inlineOutline ? ColorId.bubbleOut_background : 0;
            ViewGroup parent = (ViewGroup) ((ViewGroup) holder.itemView).getChildAt(0);
            Theme.changeBackgroundColorId(parent, backgroundColorId);
            NonMaterialButton btn = ((NonMaterialButton) parent.getChildAt(0));*/

            break;
          case VIEW_TYPE_PROGRESS:
          case VIEW_TYPE_NEW:
            // nothing to do
            break;
          case VIEW_TYPE_CONTROLS:
          case VIEW_TYPE_INPUT:
          case VIEW_TYPE_CIRCLE: {
            ViewGroup viewGroup = (ViewGroup) ((ViewGroup) holder.itemView).getChildAt(0);
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
              View v = viewGroup.getChildAt(i);
              boolean checked = id == ColorId.controlActive;
              if (v instanceof CheckBoxView) {
                ((CheckBoxView) v).setChecked(checked, false);
              } else if (v instanceof RadioView) {
                ((RadioView) v).setChecked(checked, false);
              } else if (v instanceof MaterialEditTextGroup) {
                boolean isActive = id != ColorId.inputInactive;
                ((MaterialEditTextGroup) v).setAlwaysActive(isActive);
                ((MaterialEditTextGroup) v).setText(isActive ? Lang.getString(R.string.Demo) : "");
                ((MaterialEditTextGroup) v).getEditText().setForceColorId(isActive ? id : 0);
              } else if (v instanceof CircleButton) {
                CircleButton btn = (CircleButton) v;
                if (btn.getId() != id) {
                  int iconRes, iconColorId;
                  float size;
                  float rotation = 0f;
                  switch (id) {
                    case ColorId.headerButton:
                      size = 52f;
                      iconRes = R.drawable.baseline_create_24;
                      iconColorId = ColorId.headerButtonIcon;
                      break;
                    case ColorId.circleButtonRegular:
                      size = 52f;
                      iconRes = R.drawable.baseline_create_24;
                      iconColorId = ColorId.circleButtonRegularIcon;
                      break;
                    case ColorId.circleButtonChat:
                      size = 48f;
                      iconRes = R.drawable.baseline_arrow_downward_24;
                      iconColorId = ColorId.circleButtonChatIcon;
                      break;
                    case ColorId.circleButtonOverlay:
                      size = 46f;
                      iconRes = R.drawable.baseline_backspace_24;
                      iconColorId = ColorId.circleButtonOverlayIcon;
                      break;
                    case ColorId.circleButtonTheme:
                      size = 52f;
                      iconRes = R.drawable.baseline_palette_24;
                      iconColorId = ColorId.circleButtonThemeIcon;
                      break;
                    case ColorId.circleButtonNewSecret:
                      size = 40f;
                      iconRes = R.drawable.baseline_lock_24;
                      iconColorId = ColorId.circleButtonNewSecretIcon;
                      break;
                    case ColorId.circleButtonNewChat:
                      size = 40f;
                      iconRes = R.drawable.baseline_person_24;
                      iconColorId = ColorId.circleButtonNewChatIcon;
                      break;
                    case ColorId.circleButtonNewGroup:
                      iconRes = R.drawable.baseline_group_24;
                      iconColorId = ColorId.circleButtonNewGroupIcon;
                      size = 40f;
                      break;
                    case ColorId.circleButtonNewChannel:
                      iconRes = R.drawable.baseline_bullhorn_24;
                      iconColorId = ColorId.circleButtonNewChannelIcon;
                      size = 40f;
                      break;
                    case ColorId.circleButtonPositive:
                    case ColorId.circleButtonNegative:
                      iconColorId = id == ColorId.circleButtonPositive ? ColorId.circleButtonPositiveIcon : ColorId.circleButtonNegativeIcon;
                      iconRes = R.drawable.baseline_phone_24;
                      size = 52f;
                      rotation = id == ColorId.circleButtonNegative ? 135f : 0f;
                      break;
                    default:
                      return;
                  }
                  btn.setId(id);
                  btn.init(iconRes, size, 4f, id, iconColorId);
                  btn.setIconRotation(rotation, false);
                  final int padding = Screen.dp(4f);
                  int result = Screen.dp(size) + padding * 2;
                  ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) btn.getLayoutParams();
                  int maxSize = Screen.dp(56f);
                  int margin = Screen.dp(12f) - padding + (maxSize / 2 - result / 2);
                  if (params.width != result || params.height != result || params.rightMargin != margin) {
                    params.width = params.height = result;
                    params.rightMargin = margin;
                    btn.setLayoutParams(params);
                  }
                }
              }
            }
            break;
          }
        }
      }

      @Override
      protected SettingHolder initCustom (ViewGroup parent, int customViewType) {
        FrameLayout.LayoutParams fp = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        SettingHolder holder = adapter.onCreateViewHolder(parent, ListItem.TYPE_VALUED_SETTING_COMPACT);
        LinearLayout ll = new LinearLayout(context);
        ll.setGravity(Gravity.CENTER_VERTICAL);
        ll.setOrientation(LinearLayout.HORIZONTAL);
        ll.setLayoutParams(fp);
        ll.setPadding(Screen.dp(12f), 0, 0, 0);

        LinearLayout.LayoutParams lp;

        switch (customViewType) {
          case VIEW_TYPE_INLINE_OUTLINE: {
            ViewSupport.setThemedBackground(ll, ColorId.NONE, ThemeListController.this);

            NonMaterialButton btn;

            lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, NonMaterialButton.defaultHeight());
            lp.rightMargin = Screen.dp(6f);

            btn = new NonMaterialButton(context);
            addThemeInvalidateListener(btn);
            btn.setText(R.string.Demo);
            btn.setInProgress(true, false);
            btn.setLayoutParams(lp);
            ll.addView(btn);

            lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, NonMaterialButton.defaultHeight());
            lp.rightMargin = Screen.dp(12f);

            btn = new NonMaterialButton(context);
            addThemeInvalidateListener(btn);
            btn.setText(R.string.Demo);
            btn.setForceActive(true);
            btn.setInProgress(true, false);
            btn.setLayoutParams(lp);
            ll.addView(btn);
            break;
          }
          case VIEW_TYPE_SLIDER: {
            lp = new LinearLayout.LayoutParams(Screen.dp(122f), NonMaterialButton.defaultHeight());

            SliderView sliderView = new SliderView(context);
            addThemeInvalidateListener(sliderView);
            sliderView.setSlideEnabled(true, false);
            int count = 5;
            sliderView.setValueCount(count);
            sliderView.setPadding(Screen.dp(12f), 0, Screen.dp(12f), 0);
            sliderView.setValue((float) Math.round(.5f * (float) (count - 1)) / (float) (count - 1));
            sliderView.setListener(new SliderView.Listener() {
              @Override
              public void onSetStateChanged (SliderView view, boolean isSetting) {
                if (!isSetting) {
                  int index = Math.round(view.getValue() * (float) (count - 1));
                  view.animateValue((float) index / (float) (count - 1));
                }
              }

              @Override
              public void onValueChanged (SliderView view, float factor) {
                /*int index = Math.round(factor * (float) (Settings.CHAT_FONT_SIZES.length - 1));
                if (Settings.instance().setChatFontSize(Settings.CHAT_FONT_SIZES[index])) {
                  manager.rebuildLayouts();
                }*/
              }

              @Override
              public boolean allowSliderChanges (SliderView view) {
                return true;
              }
            });
            sliderView.setLayoutParams(lp);
            sliderView.setForceBackgroundColorId(ColorId.sliderInactive);
            sliderView.setColorId(ColorId.sliderActive, false);
            ll.addView(sliderView);
            break;
          }
          case VIEW_TYPE_PROGRESS: {
            for (int i = 0; i < 4; i++) {
              ProgressComponentView progressView = new ProgressComponentView(context);
              addThemeInvalidateListener(progressView);
              lp = new LinearLayout.LayoutParams(Screen.dp(32f), Screen.dp(64f));
              lp.rightMargin = Screen.dp(4f);
              switch (i) {
                case 0:
                  progressView.initSmall(1f);
                  progressView.getProgress().setSlowerDurations();
                  lp.width = Screen.dp(12f);
                  break;
                case 1:
                  progressView.initMedium(1f);
                  lp.width = Screen.dp(18f);
                  break;
                case 2:
                  progressView.initBig(1f);
                  lp.width = Screen.dp(22f);
                  break;
                case 3:
                  progressView.initLarge(1f);
                  lp.rightMargin = Screen.dp(12f);
                  lp.width = Screen.dp(42f);
                  break;
              }
              progressView.getProgress().setMonotonic(true);
              progressView.setLayoutParams(lp);
              ll.addView(progressView);
            }
            break;
          }
          case VIEW_TYPE_CONTROLS: {
            lp = new LinearLayout.LayoutParams(Screen.dp(18f), Screen.dp(18f));
            lp.rightMargin = Screen.dp(4f);

            CheckBoxView checkBox = CheckBoxView.simpleCheckBox(context);
            addThemeInvalidateListener(checkBox);
            checkBox.setLayoutParams(lp);
            lp.gravity = Gravity.CENTER_VERTICAL;
            ll.addView(checkBox);

            lp = new LinearLayout.LayoutParams(Screen.dp(22f), Screen.dp(22f));
            lp.gravity = Gravity.CENTER_VERTICAL;
            lp.rightMargin = Screen.dp(18f);
            RadioView radioView = RadioView.simpleRadioView(context);
            addThemeInvalidateListener(radioView);
            radioView.setLayoutParams(lp);
            ll.addView(radioView);

            break;
          }
          case VIEW_TYPE_INPUT: {
            lp = new LinearLayout.LayoutParams(Screen.dp(82f), ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Screen.dp(12f);
            fp.bottomMargin = Screen.dp(4f);

            MaterialEditTextGroup editText = new MaterialEditTextGroup(context) {
              @Override
              public boolean onInterceptTouchEvent (MotionEvent ev) {
                return true;
              }
            };
            editText.setHint(R.string.Demo);
            editText.getEditText().setEnabled(false);
            editText.getEditText().getLayoutParams().height = Screen.dp(40f);
            editText.setLayoutParams(lp);
            editText.addThemeListeners(ThemeListController.this);

            ll.addView(editText);
            break;
          }
          case VIEW_TYPE_NEW: {
            lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(16f));
            lp.rightMargin = Screen.dp(18f);

            TextView newView = new NoScrollTextView(context);
            ViewSupport.setThemedBackground(newView, ColorId.promo, ThemeListController.this).setCornerRadius(3f);
            newView.setId(R.id.btn_new);
            newView.setSingleLine(true);
            newView.setPadding(Screen.dp(4f), Screen.dp(1f), Screen.dp(4f), 0);
            newView.setTextColor(Theme.getColor(ColorId.promoContent));
            addThemeTextColorListener(newView, ColorId.promoContent);
            addThemeInvalidateListener(newView);
            newView.setTypeface(Fonts.getRobotoBold());
            newView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10f);
            newView.setText(Lang.getString(R.string.New).toUpperCase());
            newView.setLayoutParams(lp);

            ll.addView(newView);

            break;
          }
          case VIEW_TYPE_CIRCLE: {
            CircleButton done = new CircleButton(context);
            done.setEnabled(false);
            addThemeInvalidateListener(done);

            FrameLayout.LayoutParams params;
            final int padding = Screen.dp(4);
            params = FrameLayoutFix.newParams(Screen.dp(23f) * 2 + padding * 2, Screen.dp(23f) * 2 + padding * 2, Gravity.RIGHT | Gravity.BOTTOM);
            params.rightMargin = Screen.dp(12f) - padding;
            done.setLayoutParams(params);

            ll.addView(done);
            break;
          }
          default:
            throw new IllegalArgumentException("customViewType == " + customViewType);
        }
        ((SettingView) holder.itemView).addView(ll);
        return holder;
      }

      @Override
      public void onValuesChangeStarted (View view, boolean isChanging) {
        setInTransparentMode(isChanging, view);
      }

      @Override
      public void onValuesChangeStarted (ColorToneView view, boolean isChanging) {
        setInTransparentMode(isChanging, view);
      }

      @Override
      public void onValuesChanged (ColorToneView view, float saturation, float value, boolean isFinished) {
        ViewGroup contentView = findColorParent(view);
        if (contentView == null)
          return;
        ListItem item = (ListItem) contentView.getTag();
        ColorState state = (ColorState) item.getData();
        if (state == null)
          return;

        boolean changed;
        changed = state.setHsv(1, saturation, isFinished);
        changed = state.setHsv(2, value, isFinished) || changed;
        if (changed) {
          saveColor(state, !isFinished);
        }
        ignoreTextEvents = true;
        setColor(item, -1, contentView, view);
        ignoreTextEvents = false;
      }

      @Override
      public void onValueChange (View view, float value, boolean isFinished) {
        ViewGroup contentView = findColorParent(view);
        if (contentView == null)
          return;
        ListItem item = (ListItem) contentView.getTag();
        ColorState state = (ColorState) item.getData();
        if (state == null)
          return;
        final int id = view.getId();
        if (id == R.id.color_alphaPalette) {
          int alpha = (int) (value * 255f);
          int newColor = ColorUtils.color(alpha, state.getColor());
          if (!changeColor(item, contentView, view, state, newColor, !isFinished, false)) {
            ignoreTextEvents = true;
            setColor(item, -1, contentView, view);
            ignoreTextEvents = false;
          }
        } else if (id == R.id.color_huePalette) {
          float degrees = 360f * value;
          if (!changeHsv(item, contentView, view, state, 0, degrees, !isFinished, false)) {
            ignoreTextEvents = true;
            setColor(item, -1, contentView, view);
            ignoreTextEvents = false;
          }
        }

      }

      private boolean ignoreTextEvents;

      @Override
      public void onTextChanged (MaterialEditTextGroup view, CharSequence charSequence) {
        if (ignoreTextEvents || getParentOrSelf().getLockFocusView() != view.getEditText())
          return;
        ViewGroup contentView = findColorParent(view);
        if (contentView == null)
          return;
        ListItem item = (ListItem) contentView.getTag();
        ColorState state = (ColorState) item.getData();
        if (state == null)
          return;
        final String v = charSequence.toString().trim();
        final int id = view.getId();
        final int currentColor = state.getColor();
        int newColor = 0;
        boolean success = false;
        try {
          // RGBA 0..255
          if (id == R.id.color_hex) {// Hex
            newColor = ColorUtils.parseHexColor(v, false);
            success = true;
          } else if (id == R.id.color_red || id == R.id.color_green || id == R.id.color_blue || id == R.id.color_alpha) {
            int value = v.isEmpty() ? 0 : Integer.parseInt(v);
            if (value >= 0 && value <= 255) {
              int alpha = id != R.id.color_alpha ? Color.alpha(currentColor) : value;
              int red = id != R.id.color_red ? Color.red(currentColor) : value;
              int green = id != R.id.color_green ? Color.green(currentColor) : value;
              int blue = id != R.id.color_blue ? Color.blue(currentColor) : value;
              newColor = Color.argb(alpha, red, green, blue);
              success = true;
            }
          } else if (id == R.id.color_hue || id == R.id.color_saturation || id == R.id.color_lightness || id == R.id.color_alphaPercentage) {
            if (!v.isEmpty()) {
              float value = Float.parseFloat(v);
              success = id == R.id.color_hue ? value >= 0f && value <= 360f : value >= 0f && value <= 100f;
              if (success) {
                if (id == R.id.color_alphaPercentage) {
                  newColor = ColorUtils.color((int) (255f * (value / 100f)), currentColor);
                  success = true;
                } else {
                  int prop = id == R.id.color_hue ? 0 : id == R.id.color_saturation ? 1 : 2;
                  if (id != R.id.color_hue) {
                    value /= 100f;
                  }
                  view.setInErrorState(false);
                  if (!changeHsv(item, contentView, view, state, prop, value, false, false)) {
                    setColor(item, -1, contentView, view);
                  }
                  // float hue = id == R.id.color_hue ? value : U.parseFloat(((MaterialEditTextGroup) contentView.findViewById(R.id.color_hue)).getText().toString(), state.getHsv(0));
                  // float saturation = (id == R.id.color_saturation ? value : U.parseFloat(((MaterialEditTextGroup) contentView.findViewById(R.id.color_saturation)).getText().toString(), state.getHsv(1) * 100f)) / 100f;
                  // float lightness = (id == R.id.color_lightness ? value : U.parseFloat(((MaterialEditTextGroup) contentView.findViewById(R.id.color_lightness)).getText().toString(), state.getHsv(2) * 100f)) / 100f;
                    /*Color.colorToHSV(currentColor, hsv);
                    float hue = id == R.id.color_hue ? value : U.parseFloat(((MaterialEditTextGroup) contentView.findViewById(R.id.color_hue)).getText().toString(), hsv[0]);
                    */
                    /*hsv[0] = hue;
                    hsv[1] = saturation / 100f;
                    hsv[2] = lightness / 100f;*/
                  // newColor = Color.HSVToColor(Color.alpha(currentColor), hsv);

                  return;
                }
              }
            }
          } else {
            throw Theme.newError(id, "viewId");
          }
        } catch (Throwable t) {
          Log.i("Cannot parse color input", t);
          success = false;
        }
        view.setInErrorState(!success);
        if (success) {
          changeColor(item, contentView, view, state, newColor, false, false);
        }
      }

      @Override
      protected void setColor (ListItem item, int position, ViewGroup contentView, View updatedView, ColorToneView toneView, ColorPaletteView paletteView, ColorPaletteView transparencyView, MaterialEditTextGroup hexView, MaterialEditTextGroup redView, MaterialEditTextGroup greenView, MaterialEditTextGroup blueView, MaterialEditTextGroup alphaView, MaterialEditTextGroup defaultView, MaterialEditTextGroup hueView, MaterialEditTextGroup saturationView, MaterialEditTextGroup lightnessView, MaterialEditTextGroup alphaPercentageView, NonMaterialButton clearButton, NonMaterialButton undoButton, NonMaterialButton redoButton, NonMaterialButton copyButton, NonMaterialButton pasteButton, NonMaterialButton opacityButton, NonMaterialButton saveButton) {
        ThemeCustom theme = getTheme();
        int colorId = getDataId(item);
        int color = theme.getColor(colorId);
        int defaultColor = theme.getParentTheme().getColor(colorId);
        String colorName = Theme.getColorName(colorId);

        ColorState state = (ColorState) item.getData();
        if (state == null) {
          state = new ColorState(theme.getId(), colorId, color, defaultColor);
          item.setData(state);
        }
        color = state.getColor();

        int backgroundColorId = getPickerBackgroundColorId(colorId, colorName);
        if (undoButton != null)
          undoButton.setBackgroundColorId(backgroundColorId);
        if (redoButton != null)
          redoButton.setBackgroundColorId(backgroundColorId);

        float[] hsv = state.getHsv();
        if (updatedView != toneView)
          toneView.setColor(color, hsv);
        if (updatedView != paletteView)
          paletteView.setHue(hsv[0]);
        if (updatedView != transparencyView)
          transparencyView.setTransparentColor(color);

        setText(hexView, Strings.getHexColor(color, false).substring(1), updatedView);
        setText(redView, String.valueOf(Color.red(color)), updatedView);
        setText(greenView, String.valueOf(Color.green(color)), updatedView);
        setText(blueView, String.valueOf(Color.blue(color)), updatedView);
        setText(alphaView, String.valueOf(Color.alpha(color)), updatedView);

        if (updatedView == null) // Not an update
          defaultView.setBlockedText(Strings.getHexColor(defaultColor, false).substring(1));

        if (updatedView != hueView && updatedView != saturationView && updatedView != lightnessView) {
          setText(hueView, U.formatFloat(hsv[0], false), updatedView);
          setText(saturationView, U.formatFloat(hsv[1] * 100f, false), updatedView);
          setText(lightnessView, U.formatFloat(hsv[2] * 100f, false), updatedView);
        }
        setText(alphaPercentageView, U.formatFloat((float) Color.alpha(color) / 255f * 100f, false), updatedView);

        /*int v = state.canClear() ? View.VISIBLE : View.INVISIBLE;
        if (clearButton.getVisibility() != v) {
          clearButton.setVisibility(v); //
        }*/
        clearButton.setEnabled(state.canClear());
        undoButton.setEnabled(state.canUndo());
        redoButton.setEnabled(state.canRedo());
        if (updatedView == null)
          pasteButton.setEnabled(canPasteColor());
        opacityButton.setEnabled(state.hasTransparency());
        copyButton.setEnabled(!hexView.inErrorState() && hexView.getText().length() > 0);
        saveButton.setEnabled(state.canSaveStack());
      }

      private void setText (MaterialEditTextGroup editText, CharSequence value, View updatedView) {
        if (updatedView != editText && !editText.getText().toString().equals(value.toString())) {
          ignoreTextEvents = true;
          editText.setText(value);
          editText.getEditText().setScrollX(0);
          ignoreTextEvents = false;
        }
      }

      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        view.setDrawModifier(item.getDrawModifier());
        ThemeCustom theme = getTheme();
        final int viewId = item.getId();
        if (viewId == R.id.btn_property) {
          final int propertyId = getDataId(item);
          final float value = theme.getProperty(propertyId);
          boolean set = false;
          if (propertyId == PropertyId.PARENT_THEME) {
            try {
              view.setData(ThemeManager.getBuiltinThemeName((int) value));
              set = true;
            } catch (Throwable ignored) {
            }
          }
          if (!set) {
            view.setData(U.formatFloat(value, true));
          }
          if (ThemeManager.isBoolProperty(propertyId)) {
            view.getToggler().setUseNegativeState(false);
            view.getToggler().setRadioEnabled(value == 1f, isUpdate);
          }
        } else if (viewId == R.id.btn_color) {
          final ItemModifier modifier = (ItemModifier) item.getDrawModifier();
          final int colorId = getDataId(item);
          final int color = theme.getColor(colorId);
          String colorName = view.getName().toString();

          final boolean needStaticFilling = colorId == ColorId.fillingPressed;
          boolean hasColor = item.getViewType() >= 0;
          boolean colorVisible = true;

          switch (colorId) {
            case ColorId.togglerActive:
              view.getToggler().setUseNegativeState(false).setRadioEnabled(true, false);
              hasColor = false;
              break;
            case ColorId.togglerInactive:
              view.getToggler().setUseNegativeState(false).setRadioEnabled(false, false);
              hasColor = false;
              break;
            case ColorId.togglerPositive:
              view.getToggler().setUseNegativeState(true).setRadioEnabled(true, false);
              hasColor = false;
              break;
            case ColorId.togglerNegative:
              view.getToggler().setUseNegativeState(true).setRadioEnabled(false, false);
              hasColor = false;
              break;
            case ColorId.togglerActiveBackground:
            case ColorId.togglerInactiveBackground:
            case ColorId.togglerPositiveBackground:
            case ColorId.togglerPositiveContent:
            case ColorId.togglerNegativeBackground:
            case ColorId.togglerNegativeContent:
              colorVisible = false;
              break;
            case ColorId.filling:
              colorVisible = false;
              break;
          }
          if (modifier.noColorPreview || modifier.drawables != null || modifier.circleColorId != 0 || colorName.startsWith("avatar") || colorName.startsWith("name") || colorName.startsWith("file")) {
            colorVisible = false;
          }

          int backgroundColorId = getBackgroundColorId(colorId, colorName);
          Theme.changeBackgroundColorId(view, backgroundColorId);
          Theme.changeSelector(view, needStaticFilling, backgroundColorId);

          if (hasColor) {
            ColorPreviewView previewView = (ColorPreviewView) view.getChildAt(0);
            previewView.setColor(color, -1);
            int visibility = colorVisible ? View.VISIBLE : View.INVISIBLE;
            if (previewView.getVisibility() != visibility) {
              previewView.setVisibility(visibility);
            }
          }

          view.setData(getColorRepresentation(color, false));

          int dataColorId;
          boolean dataIsSubtitle = false;
          switch (colorId) {
            case ColorId.background:
            case ColorId.background_text:
            case ColorId.background_textLight:
            case ColorId.background_icon:
              dataColorId = ColorId.background_textLight;
              break;
            case ColorId.caption_textLink:
            case ColorId.caption_textLinkPressHighlight:
              dataColorId = ColorId.caption_textLink;
              break;
              /*case ColorId.headerRemoveBackground:
                dataColorId = ColorId.headerText;
                dataIsSubtitle = true;
                break;*/
              /*case ColorId.headerBackground:
              case ColorId.headerText:
              case ColorId.headerIcon:
                dataColorId = ColorId.headerText;
                dataIsSubtitle = true;
                break;
              case ColorId.headerLightBackground:
              case ColorId.headerLightText:
              case ColorId.headerLightIcon:
                dataColorId = ColorId.headerLightText;
                dataIsSubtitle = true;
                break;*/
            default:
              if (colorName.startsWith("iv_")) {
                dataColorId = ColorId.iv_caption;
              } else {
                dataColorId = ColorId.textLight;
              }
              break;
          }
          view.setDataColorId(dataColorId, dataIsSubtitle);
        }
      }
    };
    adapter.setOnLongClickListener(this);
    if (!isLookupMode) {
      adapter.setLockFocusOn(getParentOrSelf(), false);
    }
    recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
      @Override
      public void onDraw (@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        LinearLayoutManager manager = (LinearLayoutManager) parent.getLayoutManager();
        int first = manager.findFirstVisibleItemPosition();
        int last = manager.findLastVisibleItemPosition();
        if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION)
          return;
        int right = parent.getMeasuredWidth();
        int top = 0, bottom = 0;
        View lastView = null;
        for (int i = 0; i < manager.getChildCount(); i++) {
          View view = manager.getChildAt(i);
          if (view == null)
            continue;
          ListItem item = (ListItem) view.getTag();
          if (item == null)
            continue;

          int newTop = view.getTop() + (int) view.getTranslationY();
          int newBottom = view.getBottom() + (int) view.getTranslationY();

          if (newTop > bottom) {
            int backgroundColorId = Theme.getBackgroundColorId(lastView);
            if (backgroundColorId != 0) {
              c.drawRect(0, bottom, right, newTop, Paints.fillingPaint(ColorUtils.alphaColor(getAlpha(), Theme.getColor(backgroundColorId))));
            }
          }

          top = newTop;
          bottom = newBottom;

          if (item.getViewType() == ListItem.TYPE_COLOR_PICKER && bottom > top) {
            int colorId = getDataId(item);
            int backgroundColorId = getPickerBackgroundColorId(colorId, Theme.getColorName(colorId));
            c.drawRect(0, top, right, bottom, Paints.fillingPaint(ColorUtils.alphaColor(getAlpha(), Theme.getColor(backgroundColorId))));
          }
          lastView = view;
        }
      }
    });
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged (@NonNull RecyclerView recyclerView, int newState) {
        if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
          if (getParentOrSelf().getLockFocusView() != null) {
            getParentOrSelf().hideSoftwareKeyboard();
            context().requestBlankFocus();
            context().hideBlankFocusKeyboard();
          }
        }
      }
    });

    List<ListItem> items = new ArrayList<>();
    //noinspection WrongConstant
    this.itemCount = buildCells(items, args.theme.getId(), currentQuery);

    adapter.setItems(items, false);

    items.add(new ListItem(ListItem.TYPE_SETTING, ColorId.filling));

    recyclerView.setItemAnimator(isLookupMode ? null : new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 120l));
    recyclerView.setAdapter(adapter);

    RemoveHelper.attach(recyclerView, new RemoveHelper.Callback() {
      @Override
      public boolean canRemove (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, int position) {
        ListItem item = (ListItem) viewHolder.itemView.getTag();
        return item.getId() == R.id.btn_color && getDataId(item) == ColorId.fillingNegative;
      }

      @Override
      public void onRemove (RecyclerView.ViewHolder viewHolder) {

      }
    });
  }

  @Override
  public void setParentWrapper (@Nullable ViewController<?> parentWrapper) {
    super.setParentWrapper(parentWrapper);
    if (adapter != null)
      adapter.setLockFocusOn(getParentOrSelf(), false);
  }

  private static int getPickerBackgroundColorId (@ColorId int colorId, @NonNull String colorName) {
    int backgroundColorId = getBackgroundColorId(colorId, colorName);
    switch (backgroundColorId) {
      case ColorId.black:
        return ColorId.filling;
    }
    return backgroundColorId;
  }

  private static int getBackgroundColorId (@ColorId int colorId, @NonNull String colorName) {
    switch (colorId) {
      case ColorId.background:
      case ColorId.background_text:
      case ColorId.background_textLight:
      case ColorId.background_icon:
        return ColorId.background;
      /*case ColorId.headerRemoveBackground:
        return ColorId.headerBackground;
      case ColorId.headerBackground:
      case ColorId.headerText:
      case ColorId.headerIcon:
        return ColorId.headerBackground;
      case ColorId.headerLightBackground:
      case ColorId.headerLightText:
      case ColorId.headerLightIcon:
        return ColorId.headerLightBackground;*/
      case ColorId.iv_preBlockBackground:
      case ColorId.iv_textCodeBackground:
        return colorId;
      case ColorId.caption_textLink:
      case ColorId.caption_textLinkPressHighlight:
        return ColorId.black;
      /*case ColorId.snackbarUpdate:
      case ColorId.snackbarUpdateAction:
      case ColorId.snackbarUpdateText:
        return ColorId.snackbarUpdate;*/
    }
    return ColorId.filling;
  }

  private static class ItemModifier implements DrawModifier {
    protected final ThemeCustom theme;
    protected final int colorId;

    private boolean hasHistory;
    private DrawModifier otherModifier;

    private int backgroundColorId;
    private int circleColorId, circleIconColorId = ColorId.white;
    private Letters letters;
    private float lettersWidth;
    private Drawable circleIcon;
    private Path playPausePath;

    private Drawable[] drawables;

    private boolean noColorPreview, needOnline;

    public ItemModifier (ThemeCustom theme, int colorId) {
      this.theme = theme;
      this.colorId = colorId;
      checkHistory();
    }

    public void setLetters (String letters) {
      if (letters != null) {
        this.letters = new Letters(letters);
        this.lettersWidth = Paints.measureLetters(this.letters, 20f);
      } else {
        this.letters = null;
        this.lettersWidth = 0f;
      }
    }

    public void setCircle (int colorId, int icon) {
      this.circleColorId = colorId;
      this.circleIcon = icon != 0 ? Drawables.get(icon) : null;
    }

    public void setCircle (int colorId, String letters) {
      this.circleColorId = colorId;
      setLetters(letters);
    }

    public void setPlayPausePath () {
      this.playPausePath = new Path();
      DrawAlgorithms.buildPlayPause(playPausePath, Screen.dp(13f), -1f, 0f);
    }

    private Paint iconPaint;
    private int iconColor;
    private int iconBackgroundColorId;

    public void setIcons (int... resIds) {
      drawables = new Drawable[resIds.length];
      int i = 0;
      for (int resId : resIds) {
        drawables[i] = Drawables.get(resId);
        i++;
      }
    }

    private Counter counter;

    public void setCounter (int count) {
      this.counter = new Counter.Builder().build();
      this.counter.setCount(count, colorId == ColorId.badgeMuted, false);
    }

    public void setHasHistory (boolean hasHistory) {
      this.hasHistory = hasHistory;
    }

    public void checkHistory () {
      this.hasHistory = Settings.instance().hasColorHistory(ThemeManager.resolveCustomThemeId(theme.getId()), colorId);
    }

    public void setOtherModifier (DrawModifier modifier) {
      this.otherModifier = modifier;
    }

    @Override
    public void beforeDraw (View view, Canvas c) {
      if (otherModifier != null) {
        otherModifier.beforeDraw(view, c);
      }
    }

    @Override
    public void afterDraw (View view, Canvas c) {
      if (otherModifier != null) {
        otherModifier.afterDraw(view, c);
      }
      boolean isOverridden = theme.hasColor(colorId, true);
      if (isOverridden || hasHistory) {
        float cx = Screen.dp(8f);
        float cy = view.getMeasuredHeight() / 2 - Screen.dp(9f);
        // Drawables.draw(c, editDrawable, cx, cy, Paints.getIconGrayLightPorterDuffPaint());
        c.drawCircle(cx, cy, Screen.dp(3f), Paints.fillingPaint(theme.getColor(isOverridden ? ColorId.iconActive : ColorId.iconLight)));
      }
      if (circleColorId != 0) {
        int circleRadius = ChatView.getAvatarRadius(Settings.CHAT_MODE_2LINE);
        int cx = view.getMeasuredWidth() - circleRadius - Screen.dp(12f);
        int cy = view.getMeasuredHeight() / 2;
        if (backgroundColorId != 0)
          c.drawRect(cx - circleRadius - Screen.dp(12f), 0, view.getMeasuredWidth(), view.getMeasuredHeight(), Paints.fillingPaint(theme.getColor(backgroundColorId)));
        c.drawCircle(cx, cy, circleRadius, Paints.fillingPaint(theme.getColor(circleColorId)));
        if (circleIcon != null) {
          int color = Theme.getColor(circleIconColorId);
          Paint paint = iconColor == color && iconPaint != null ? iconPaint : (iconPaint = Paints.createPorterDuffPaint(iconPaint, iconColor = color));
          Drawables.draw(c, circleIcon, cx - circleIcon.getMinimumWidth() / 2, cy - circleIcon.getMinimumHeight() / 2, paint);
        } else if (letters != null) {
          Paint paint = Paints.whiteMediumPaint(20, letters.needFakeBold, false);
          int saved = paint.getColor();
          paint.setColor(Theme.getColor(ColorId.avatar_content));
          c.drawText(letters.text, cx - lettersWidth / 2, cy + Screen.dp(7f), paint);
          paint.setColor(saved);
        } else if (playPausePath != null) {
          DrawAlgorithms.drawPlayPause(c, cx, cy, Screen.dp(13f), playPausePath, 0f, 0f, 0f, 0xffffffff);
        }
        if (needOnline) {
          DrawAlgorithms.drawOnline(c, view.getMeasuredWidth() - ChatView.getAvatarRadius(Settings.CHAT_MODE_2LINE) - Screen.dp(12f), view.getMeasuredHeight() / 2, ChatView.getAvatarRadius(Settings.CHAT_MODE_2LINE), 1f);
        }
      }
      if (counter != null) {
        int circleRadius = Screen.dp(18f);
        int cx = view.getMeasuredWidth() - circleRadius - Screen.dp(12f);
        int cy = view.getMeasuredHeight() / 2;
        counter.draw(c, cx, cy, Gravity.LEFT, 1f);
      }
      if (drawables != null) {
        int cx = view.getMeasuredWidth() - Screen.dp(18f);
        for (Drawable drawable : drawables) {
          cx -= drawable.getMinimumWidth();
          int color = Theme.getColor(colorId);
          int y = view.getMeasuredHeight() / 2 - drawable.getMinimumHeight() / 2;
          int iconWidth = drawable.getMinimumWidth();
          int iconHeight = drawable.getMinimumHeight();
          if (iconBackgroundColorId != 0) {
            RectF rectF = Paints.getRectF();
            rectF.left = cx;
            rectF.right = cx + iconWidth;
            rectF.top = y;
            rectF.bottom = y + iconHeight;

            int dx = iconWidth / 2;
            int dy = iconHeight / 2;
            rectF.left -= dx; rectF.right += dx;
            rectF.top -= dy; rectF.bottom += dy;

            c.drawRoundRect(rectF, Screen.dp(3f), Screen.dp(3f), Paints.fillingPaint(theme.getColor(iconBackgroundColorId)));
          }
          Paint paint = iconColor == color && iconPaint != null ? iconPaint : (iconPaint = Paints.createPorterDuffPaint(iconPaint, iconColor = color));
          Drawables.draw(c, drawable, cx, y, paint);
          cx -= Screen.dp(6f);
        }
      }
    }
  }

  private Drawable editDrawable;

  private static final int VIEW_TYPE_INLINE_OUTLINE = 0;
  private static final int VIEW_TYPE_PROGRESS = 1;
  private static final int VIEW_TYPE_CONTROLS = 2;
  private static final int VIEW_TYPE_NEW = 3;
  private static final int VIEW_TYPE_INPUT = 4;
  private static final int VIEW_TYPE_SLIDER = 5;
  private static final int VIEW_TYPE_CIRCLE = 6;

  private ListItem newItem (ThemeDelegate theme, int id, boolean isProperty) {
    ListItem item;
    String name = isProperty ? Theme.getPropertyName(id) : Theme.getColorName(id);
    int viewType;
    if (isProperty) {
      viewType = ListItem.TYPE_VALUED_SETTING_COMPACT;
      if (ThemeManager.isBoolProperty(id)) {
        viewType = ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER;
      }
      item = new ListItem(viewType, R.id.btn_property, 0, name, false);
      item.setLongId(id);
      return item;
    }
    CustomTypefaceSpan span = null;
    if (editDrawable == null)
      editDrawable = Drawables.get(R.drawable.baseline_edit_12);
    final ItemModifier modifier = new ItemModifier((ThemeCustom) theme, id);
    int spanStart = 0, spanEnd = name.length();
    viewType = ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_COLOR;
    switch (id) {
      case ColorId.togglerActive:
      case ColorId.togglerInactive:
      case ColorId.togglerPositive:
      case ColorId.togglerNegative:
        viewType = ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER;
        break;

      case ColorId.background_text:
      case ColorId.background_textLight:
      case ColorId.caption_textLink:
        span = new CustomTypefaceSpan(null, id);
        break;
      /*case ColorId.headerBackground:
      case ColorId.headerText:
      case ColorId.headerIcon:
        span = new CustomTypefaceSpan(null, ColorId.headerText);
        break;
      case ColorId.headerLightBackground:
      case ColorId.headerLightText:
      case ColorId.headerLightIcon:
        span = new CustomTypefaceSpan(null, ColorId.headerLightText);
        break;*/
      case ColorId.background:
      case ColorId.background_icon:
        span = new CustomTypefaceSpan(null, ColorId.background_text);
        if (id == ColorId.background_icon) {
          modifier.setIcons(R.drawable.baseline_devices_other_24);
        } else {
          modifier.noColorPreview = true;
        }
        break;
      case ColorId.icon:
        modifier.setIcons(R.drawable.baseline_settings_24, R.drawable.baseline_alternate_email_24, R.drawable.deproko_baseline_pin_24);
        break;
      case ColorId.iconLight:
        modifier.setIcons(R.drawable.deproko_baseline_clock_24, R.drawable.baseline_visibility_14, R.drawable.baseline_edit_12);
        break;
      case ColorId.playerButton:
        modifier.setIcons(R.drawable.baseline_skip_next_24_white, R.drawable.baseline_pause_24, R.drawable.baseline_skip_previous_24_white);
        break;
      case ColorId.playerButtonActive:
        modifier.setIcons(R.drawable.round_repeat_24, R.drawable.round_shuffle_24, R.drawable.round_repeat_one_24);
        break;
      case ColorId.iconActive:
        modifier.setIcons(R.drawable.deproko_baseline_mosaic_group_24, R.drawable.baseline_emoticon_outline_24, R.drawable.baseline_restaurant_menu_24);
        break;
      case ColorId.iconPositive:
        modifier.setIcons(R.drawable.baseline_call_made_18, R.drawable.baseline_call_received_18);
        break;
      case ColorId.iconNegative:
        modifier.setIcons(R.drawable.baseline_call_made_18, R.drawable.baseline_call_received_18, R.drawable.baseline_call_missed_18);
        break;
      case ColorId.ticks:
        modifier.setIcons(R.drawable.deproko_baseline_check_single_24);
        break;
      case ColorId.ticksRead:
        modifier.setIcons(R.drawable.deproko_baseline_check_double_24);
        break;
      case ColorId.bubbleOut_ticks:
        modifier.setIcons(R.drawable.deproko_baseline_check_single_24);
        modifier.iconBackgroundColorId = ColorId.bubbleOut_background;
        break;
      case ColorId.bubbleOut_ticksRead:
        modifier.setIcons(R.drawable.deproko_baseline_check_double_24);
        modifier.iconBackgroundColorId = ColorId.bubbleOut_background;
        break;
      case ColorId.chatListVerify:
        modifier.setIcons(R.drawable.deproko_baseline_verify_chat_24);
        break;
      case ColorId.chatSendButton:
        modifier.setIcons(R.drawable.deproko_baseline_send_24);
        break;
      case ColorId.chatListMute:
        modifier.setIcons(R.drawable.deproko_baseline_notifications_off_24);
        break;
      case ColorId.chatListIcon:
        modifier.setIcons(R.drawable.baseline_camera_alt_16, R.drawable.baseline_videocam_16, R.drawable.baseline_collections_16, R.drawable.ivanliana_baseline_video_collections_16, R.drawable.ivanliana_baseline_audio_collections_16, R.drawable.ivanliana_baseline_file_collections_16);
        break;
      case ColorId.badge:
      case ColorId.badgeMuted:
      case ColorId.badgeFailed:
        modifier.setCounter(id == ColorId.badgeFailed ? Tdlib.CHAT_FAILED : 1);
        modifier.noColorPreview = true;
        break;
      case ColorId.textSelectionHighlight:
        span = new CustomTypefaceSpan(null, ColorId.text).setBackgroundColorId(id);
        break;
      case ColorId.textLinkPressHighlight:
        span = new CustomTypefaceSpan(null, ColorId.textLink).setBackgroundColorId(id);
        break;
      case ColorId.iv_textMarkedBackground:
        span = new CustomTypefaceSpan(null, ColorId.iv_textMarked).setBackgroundColorId(id);
        break;
      case ColorId.iv_textMarkedLinkPressHighlight:
        span = new CustomTypefaceSpan(null, ColorId.iv_textMarkedLink).setBackgroundColorId(id);
        break;
      case ColorId.iv_textLinkPressHighlight:
        span = new CustomTypefaceSpan(null, ColorId.iv_textLink).setBackgroundColorId(id);
        break;
      case ColorId.textSearchQueryHighlight:
        span = new CustomTypefaceSpan(null, id);
        spanEnd = name.length() / 2; // name.indexOf("Prefix") + "Prefix".length();
        break;
      case ColorId.caption_textLinkPressHighlight:
        span = new CustomTypefaceSpan(null, ColorId.caption_textLink).setBackgroundColorId(id);
        break;
      case ColorId.snackbarUpdate:
      case ColorId.snackbarUpdateAction:
        span = new CustomTypefaceSpan(null, ColorId.snackbarUpdateAction).setBackgroundColorId(ColorId.snackbarUpdate);
        break;
      case ColorId.snackbarUpdateText:
        span = new CustomTypefaceSpan(null, id).setBackgroundColorId(ColorId.snackbarUpdate);
        break;

      case ColorId.iv_pageTitle:
      case ColorId.iv_pageSubtitle:
        span = new CustomTypefaceSpan(null, id).setTextSizeDp(18f);
        break;
      case ColorId.iv_pullQuote:
        span = new CustomTypefaceSpan(null, id).setFakeBold(true);
        break;

      case ColorId.iv_blockQuoteLine:
      case ColorId.messageVerticalLine:
      case ColorId.bubbleOut_chatVerticalLine:
        modifier.setOtherModifier(new LineDrawModifier(id, theme));
        break;
      case ColorId.iv_preBlockBackground:
      case ColorId.iv_textCodeBackground:
      case ColorId.iv_separator:
      case ColorId.ivHeaderIcon:
      case ColorId.iv_header:
        // Do nothing
        break;
      case ColorId.checkActive:
        modifier.noColorPreview = true;
        modifier.setOtherModifier(new DrawModifier() {
          @Override
          public void afterDraw (View view, Canvas c) {
            SimplestCheckBox.draw(c, view.getMeasuredWidth() - Screen.dp(32f), view.getMeasuredHeight() / 2, 1f, null);
          }
        });
        break;
      case ColorId.online:
        modifier.setCircle(ColorId.avatarSavedMessages, StringUtils.random(name, 2).toUpperCase());
        modifier.needOnline = true;
        modifier.noColorPreview = true;
        break;
      case ColorId.inlineOutline:
        viewType = ListItem.TYPE_CUSTOM - VIEW_TYPE_INLINE_OUTLINE;
        break;
      case ColorId.progress:
        viewType = ListItem.TYPE_CUSTOM - VIEW_TYPE_PROGRESS;
        break;
      case ColorId.controlActive:
      case ColorId.controlInactive:
        viewType = ListItem.TYPE_CUSTOM - VIEW_TYPE_CONTROLS;
        break;
      case ColorId.promo:
        viewType = ListItem.TYPE_CUSTOM - VIEW_TYPE_NEW;
        break;
      case ColorId.inputActive:
      case ColorId.inputInactive:
      case ColorId.inputPositive:
      case ColorId.inputNegative:
        viewType = ListItem.TYPE_CUSTOM - VIEW_TYPE_INPUT;
        break;
      case ColorId.sliderActive:
        viewType = ListItem.TYPE_CUSTOM - VIEW_TYPE_SLIDER;
        break;
      case ColorId.playerCoverIcon:
        modifier.setIcons(R.drawable.baseline_music_note_24);
        modifier.iconBackgroundColorId = ColorId.playerCoverPlaceholder;
        break;
      case ColorId.headerButton:
      case ColorId.circleButtonRegular:
      case ColorId.circleButtonOverlay:
      case ColorId.circleButtonChat:
      case ColorId.circleButtonTheme:
      case ColorId.circleButtonNewSecret:
      case ColorId.circleButtonNewChat:
      case ColorId.circleButtonNewGroup:
      case ColorId.circleButtonNewChannel:
      case ColorId.circleButtonPositive:
      case ColorId.circleButtonNegative:
        viewType = ListItem.TYPE_CUSTOM - VIEW_TYPE_CIRCLE;
        break;

      case ColorId.placeholder:
        modifier.setCircle(id, 0);
        break;
      case ColorId.seekDone:
        modifier.setOtherModifier(new DrawModifier() {
          @Override
          public void afterDraw (View view, Canvas c) {
            int width = Screen.dp(122f);
            int cx = view.getMeasuredWidth() - Screen.dp(12f) - width;
            int cy = view.getMeasuredHeight() / 2;
            final int seekStroke = Screen.dp(2f);
            c.drawLine(cx, cy, cx + width, cy, Paints.getProgressPaint(theme.getColor(ColorId.seekEmpty), seekStroke));
            c.drawLine(cx, cy, cx + width / 3 * 2, cy, Paints.getProgressPaint(theme.getColor(ColorId.seekReady), seekStroke));
            c.drawLine(cx, cy, cx + width / 3, cy, Paints.getProgressPaint(theme.getColor(ColorId.seekDone), seekStroke));
            c.drawCircle(cx + width / 3, cy, Screen.dp(6f), Paints.fillingPaint(theme.getColor(ColorId.seekDone)));
          }
        });
        modifier.noColorPreview = true;
        break;
      case ColorId.introSectionActive:
        modifier.noColorPreview = true;
        modifier.setOtherModifier(new DrawModifier() {
          @Override
          public void afterDraw (View view, Canvas c) {
            int spacing = Screen.dp(4f);
            int radius = Screen.dp(2.5f);
            int cx = view.getMeasuredWidth() - Screen.dp(18f);
            int cy = view.getMeasuredHeight() / 2;
            int count = 6;
            for (int i = 0; i < count; i++) {
               cx -= radius;
               c.drawCircle(cx, cy, radius, Paints.fillingPaint(Theme.getColor(i == count - 1 ? ColorId.introSectionActive : ColorId.introSection)));
               cx -= radius + spacing;
            }
          }
        });
        break;
      case ColorId.headerRemoveBackground:
        // span = new CustomTypefaceSpan(null, ColorId.headerText);
        modifier.noColorPreview = true;
        modifier.setOtherModifier(new DrawModifier() {
          private String text;
          private int textWidth;
          private Drawable icon;

          @Override
          public void afterDraw (View view, Canvas c) {
            TextPaint paint = Paints.getRegularTextPaint(14f, 0xffffffff);
            if (text == null) {
              text = Lang.getString(R.string.Demo);
              textWidth = (int) U.measureText(text, paint);
              icon = Drawables.get(R.drawable.baseline_close_18);
            }
            int avatarRadius = Screen.dp(16f);
            int padding = Screen.dp(7f);
            RectF rectF = Paints.getRectF();

            int width = avatarRadius * 2 + padding + textWidth + Screen.dp(10f);
            int cx = view.getMeasuredWidth() - Screen.dp(12f) - width;
            int cy = view.getMeasuredHeight() / 2 - avatarRadius;
            int endX = view.getMeasuredWidth();
            c.drawRect(cx - (endX - cx - width), 0, endX, view.getMeasuredHeight(), Paints.fillingPaint(theme.getColor(ColorId.headerBackground)));
            rectF.set(cx, cy, cx + width, cy + avatarRadius + avatarRadius);
            c.drawRoundRect(rectF, avatarRadius, avatarRadius, Paints.fillingPaint(theme.getColor(ColorId.headerRemoveBackground)));
            c.drawCircle(cx + avatarRadius, cy + avatarRadius, avatarRadius, Paints.fillingPaint(theme.getColor(ColorId.headerRemoveBackgroundHighlight)));
            Drawables.draw(c, icon, cx + avatarRadius - icon.getMinimumWidth() / 2, cy + avatarRadius - icon.getMinimumHeight() / 2, Paints.getPorterDuffPaint(0xffffffff));
            c.drawText(text, cx + avatarRadius * 2 + padding, cy + avatarRadius + Screen.dp(5f), paint);
          }
        });
        break;
      case ColorId.waveformActive:
        modifier.noColorPreview = true;
        modifier.setOtherModifier(new DrawModifier() {
          private Waveform waveform;
          @Override
          public void afterDraw (View view, Canvas c) {
            int width = Screen.dp(122f);
            if (waveform == null) {
              waveform = new Waveform(TD.newRandomWaveform(), Waveform.MODE_RECT, false);
              waveform.layout(width);
            }
            waveform.draw(c, .5f, view.getMeasuredWidth() - Screen.dp(12f) - width, view.getMeasuredHeight() / 2);
          }
        });
        break;
      case ColorId.bubbleOut_waveformActive:
        modifier.noColorPreview = true;
        modifier.setOtherModifier(new DrawModifier() {
          private Waveform waveform;
          @Override
          public void afterDraw (View view, Canvas c) {
            int width = Screen.dp(122f);
            c.drawRect(view.getMeasuredWidth() - width - Screen.dp(12f) * 2, 0, view.getMeasuredWidth(), view.getMeasuredHeight(), Paints.fillingPaint(theme.getColor(ColorId.bubbleOut_background)));
            if (waveform == null) {
              waveform = new Waveform(TD.newRandomWaveform(), Waveform.MODE_RECT, true);
              waveform.layout(width);
            }
            waveform.draw(c, .5f, view.getMeasuredWidth() - Screen.dp(12f) - width, view.getMeasuredHeight() / 2);
          }
        });
        break;
      case ColorId.headerRemoveBackgroundHighlight:
      case ColorId.checkContent:
      case ColorId.textPlaceholder:
      case ColorId.promoContent:
      case ColorId.controlContent:
      case ColorId.inlineText:
      case ColorId.inlineIcon:
      case ColorId.inlineContentActive:
      case ColorId.badgeText:
      case ColorId.badgeMutedText:
      case ColorId.badgeFailedText:
      case ColorId.sliderInactive:
      case ColorId.playerCoverPlaceholder:
      case ColorId.seekEmpty:
      case ColorId.seekReady:
      case ColorId.introSection:
      case ColorId.headerButtonIcon:
      case ColorId.waveformInactive:
      case ColorId.bubbleOut_waveformInactive:
      case ColorId.filling:
      case ColorId.circleButtonRegularIcon:
      case ColorId.circleButtonChatIcon:
      case ColorId.circleButtonOverlayIcon:
      case ColorId.circleButtonThemeIcon:
      case ColorId.circleButtonNewSecretIcon:
      case ColorId.circleButtonNewChatIcon:
      case ColorId.circleButtonNewGroupIcon:
      case ColorId.circleButtonNewChannelIcon:
      case ColorId.circleButtonPositiveIcon:
      case ColorId.circleButtonNegativeIcon:
        modifier.noColorPreview = true;
        break;
      case ColorId.fileAttach:
        modifier.setCircle(id, R.drawable.baseline_location_on_24);
        break;
      case ColorId.attachContact:
        modifier.setCircle(id, R.drawable.baseline_person_24);
        modifier.circleIconColorId = ColorId.attachText;
        break;
      case ColorId.attachFile:
        modifier.setCircle(id, R.drawable.baseline_insert_drive_file_24);
        modifier.circleIconColorId = ColorId.attachText;
        break;
      case ColorId.attachInlineBot:
        modifier.setCircle(id, R.drawable.deproko_baseline_bots_24);
        modifier.circleIconColorId = ColorId.attachText;
        break;
      case ColorId.attachPhoto:
        modifier.setCircle(id, R.drawable.baseline_image_24);
        modifier.circleIconColorId = ColorId.attachText;
        break;
      case ColorId.attachLocation:
        modifier.setCircle(id, R.drawable.baseline_location_on_24);
        modifier.circleIconColorId = ColorId.attachText;
        break;
      case ColorId.messageAuthor:
      case ColorId.messageAuthorPsa:
        span = new CustomTypefaceSpan(null, id).setFakeBold(true);
        break;
      case ColorId.bubbleOut_messageAuthor:
      case ColorId.bubbleOut_messageAuthorPsa:
        span = new CustomTypefaceSpan(null, id).setFakeBold(true).setBackgroundColorId(ColorId.bubbleOut_background);
        break;
      case ColorId.bubbleOut_file:
        modifier.setCircle(id, R.drawable.baseline_insert_drive_file_24);
        // modifier.backgroundColorId = ColorId.bubbleOut_background;
        break;

      default:
        if (name.startsWith("text") || name.startsWith("iv_"))
          span = new CustomTypefaceSpan(null, id);
        else if (name.startsWith("name")) {
          span = new CustomTypefaceSpan(null, id).setFakeBold(true);
          /*int resId = Theme.getIdResourceIdentifier("theme_color_avatar" + name.substring("name".length()));
          if (resId != 0) {
            modifier.setCircle(resId, Strings.random(name, 2).toUpperCase());
          }*/
        } else if (name.startsWith("avatar")) {
          switch (id) {
            case ColorId.avatar_content: {
              modifier.setCircle(0, 0);
              break;
            }
            case ColorId.avatarArchive:
            case ColorId.avatarArchivePinned:
            case ColorId.avatarReplies:
            case ColorId.avatarReplies_big:
            case ColorId.avatarSavedMessages:
            case ColorId.avatarSavedMessages_big: {
              int circleIcon;

              switch (id) {
                case ColorId.avatarArchive:
                case ColorId.avatarArchivePinned:
                  circleIcon = R.drawable.baseline_archive_24;
                  break;
                case ColorId.avatarReplies:
                case ColorId.avatarReplies_big:
                  circleIcon = R.drawable.baseline_reply_24;
                  break;
                default:
                  circleIcon = R.drawable.baseline_bookmark_24;
                  break;
              }

              modifier.setCircle(id, circleIcon);
              modifier.circleIconColorId = ColorId.avatar_content;
              break;
            }
            default: {
              modifier.setCircle(id, StringUtils.random(name, 2).toUpperCase());
              String colorName = name.substring("avatar".length());
              if (colorName.endsWith("_big")) {
                colorName = colorName.substring(0, colorName.length() - "_big".length());
              }
              int resId = Theme.getIdResourceIdentifier("theme_color_name" + colorName);
              if (resId != 0) {
                span = new CustomTypefaceSpan(null, resId).setFakeBold(true);
              }
              break;
            }
          }
        } else if (name.startsWith("file")) {
          switch (id) {
            case ColorId.file:
              modifier.setCircle(id, null);
              modifier.setPlayPausePath();
              break;
            case ColorId.fileYellow:
              modifier.setCircle(id, R.drawable.baseline_file_download_24);
              break;
            case ColorId.fileGreen:
              modifier.setCircle(id, R.drawable.deproko_baseline_close_24);
              break;
            case ColorId.fileRed:
              modifier.setCircle(id, R.drawable.baseline_insert_drive_file_24);
              break;
          }
        }
        break;
    }
    CharSequence colorName;
    if (span != null) {
      span.setForcedTheme(theme);
      SpannableStringBuilder b = new SpannableStringBuilder(name);
      b.setSpan(span, spanStart, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      colorName = b;
    } else {
      colorName = name;
    }
    item = new ListItem(viewType, R.id.btn_color, 0, colorName, false);
    item.setLongId(id);
    item.setRadioColorId(id);
    item.setDrawModifier(modifier);
    return item;
  }

  private static int getDataId (ListItem item) {
    return (int) item.getLongId();
  }

  private static boolean matches (int id, boolean isProperty, @Nullable String query) {
    if (StringUtils.isEmpty(query))
      return true;
    String name = isProperty ? Theme.getPropertyName(id) : Theme.getColorName(id);
    String nameLower = name.toLowerCase();
    if (nameLower.startsWith(query))
      return true;
    for (int i = 0; i < name.length(); ) {
      int codePoint = name.codePointAt(i);
      if (Character.isUpperCase(codePoint) && nameLower.startsWith(query, i))
        return true;
      i += Character.charCount(codePoint);
    }
    return Strings.findWord(name, query);
    /*if (Strings.findWord(name, query))
      return true;
    int stringRes = LangUtils.getThemeDescription(id);
    return stringRes != 0 && Strings.findWord(Lang.getString(stringRes), query);*/
  }

  private CharSequence makeDescription (@StringRes int resId) {
    CharSequence text = Strings.replaceBoldTokens(Lang.getString(resId));
    TdApi.TextEntity[] entities = Td.findEntities(text.toString());
    if (entities != null) {
      List<Object> spans = new ArrayList<>();
      for (TdApi.TextEntity entity : entities) {
        if (Td.isMention(entity.type)) {
          String username = text.subSequence(entity.offset + 1, entity.offset + entity.length).toString();
          spans.add(new ClickableSpan() {
            @Override
            public void onClick (@NonNull View widget) {
              tdlib.ui().switchInline(ThemeListController.this, username, "", true);
            }
          });
          spans.add(new CustomTypefaceSpan(null, ColorId.textLink).setEntityType(entity.type).setRemoveUnderline(true));
        }
        if (!spans.isEmpty()) {
          if (!(text instanceof SpannableStringBuilder)) {
            text = new SpannableStringBuilder(text);
          }
          for (Object span : spans) {
            ((SpannableStringBuilder) text).setSpan(span, entity.offset, entity.offset + entity.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
          }
          spans.clear();
        }
      }
    }
    return text;
  }

  private static boolean needSeparate (@ColorId int prevColorId, @ColorId int colorId) {
    return prevColorId != ColorId.caption_textLink || colorId != ColorId.caption_textLinkPressHighlight;
  }

  private int addGroup (List<ListItem> items, @IdRes int sectionId, @StringRes int sectionName, @StringRes int sectionDesc, int[] themeContentIds, boolean areProperties, @Nullable String searchQuery, boolean needSeparators, @Nullable List<Integer> sortedIds) {
    if (sortedIds != null) {
      ArrayUtils.ensureCapacity(sortedIds, sortedIds.size() + themeContentIds.length);
    }
    int addedColorCount = 0;
    boolean first = true;
    int prevThemeContentId = 0;
    for (final int themeContentId : themeContentIds) {
      if (sortedIds != null)
        sortedIds.add(themeContentId);
      if (canDisplay(sectionId, themeContentId, areProperties) && matches(themeContentId, areProperties, searchQuery)) {
        addedColorCount++;
        int descriptionRes = areProperties ?
          LangUtils.getPropertyIdDescription(themeContentId) :
          LangUtils.getColorIdDescription(themeContentId);
        if (first) {
          if (sectionName != 0 && !items.isEmpty()) {
            items.add(new ListItem(items.isEmpty() ? ListItem.TYPE_HEADER_PADDED : ListItem.TYPE_HEADER, 0, 0, sectionName));
            sectionName = 0;
          }
          if (needSeparators && !items.isEmpty())
            items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
          first = false;
        } else if (needSeparators && !areProperties && (prevThemeContentId == 0 || needSeparate(prevThemeContentId, themeContentId))) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        }
        items.add(newItem(getArgumentsStrict().theme.getTheme(), themeContentId, areProperties));
        if (descriptionRes != 0) {
          CharSequence text = makeDescription(descriptionRes);
          items.add(new ListItem(ListItem.TYPE_DESCRIPTION_SMALL, 0, 0, text, false).setTextColorId(ColorId.textLight));
        }
        prevThemeContentId = themeContentId;
      }
    }
    if (!first) {
      if (needSeparators)
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      if (sectionDesc != 0) {
        CharSequence text = Strings.replaceBoldTokens(Lang.getString(sectionDesc));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, text, false));
      }
    }
    return addedColorCount;
  }

  private static void trackColors (List<Integer> items, int[] colorIds) {
    ArrayUtils.ensureCapacity(items, items.size() + colorIds.length);
    for (int colorId : colorIds) {
      items.add(colorId);
    }
  }

  private String currentQuery;

  public void searchColors (String query, RunnableInt after) {
    this.currentQuery = query;
    if (adapter == null) {
      return;
    }
    Background.instance().post(() -> {
      List<ListItem> items = new ArrayList<>();
      int resultCount = buildCells(items, getTheme().getId(), query);
      tdlib.ui().post(() -> {
        if (!isDestroyed() && StringUtils.equalsOrBothEmpty(currentQuery, query)) {
          if (editPosition != -1) {
            forceClosePicker();
          }
          itemCount = resultCount;
          adapter.setItems(items, false);
          ((LinearLayoutManager) getRecyclerView().getLayoutManager()).scrollToPositionWithOffset(0, 0);
          if (after != null) {
            after.runWithInt(resultCount);
          }
        }
      });
    });
  }

  private int buildCells (List<ListItem> items, @ThemeId int themeId, @Nullable String searchQuery) {
    int sectionId = getArgumentsStrict().specificSectionId;
    List<Integer> sortedIds;
    if (sectionId == R.id.theme_category_other || sectionId == R.id.theme_category_main || (sectionId == 0 && !StringUtils.isEmpty(searchQuery)))
      sortedIds = new ArrayList<>();
    else
      sortedIds = null;

    if (sectionId == R.id.theme_category_main) {
      items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.ThemeCategoryAccent_info));
    }

    int count;
    
    int totalCount = 0;

    // MAIN
    int[] mainColorIds = new int[] {
      ColorId.filling,
      ColorId.separator,
      ColorId.fillingPressed,
      ColorId.placeholder,
      ColorId.previewBackground,
      ColorId.overlayFilling,
      ColorId.fillingNegative,
      ColorId.fillingPositive,
      ColorId.fillingPositiveContent,
    };
    totalCount += addGroup(items, R.id.theme_category_content,0, 0, mainColorIds, false, searchQuery, true, sortedIds);
    
    // TEXT

    int[] textColorIds = new int[] {
      ColorId.text,
      ColorId.textSelectionHighlight,
      ColorId.textLight,
      ColorId.textSecure,
      ColorId.textLink,
      ColorId.textLinkPressHighlight,
      ColorId.textNeutral,
      ColorId.textNegative,
      ColorId.textSearchQueryHighlight,
    };
    totalCount += addGroup(items, R.id.theme_category_content, R.string.ThemeSectionText, 0, textColorIds, false, searchQuery, true, sortedIds);

    // BACKGROUND
    
    int[] backgroundTextColorIds = new int[] {
      ColorId.background,
      ColorId.background_text,
      ColorId.background_textLight,
      ColorId.background_icon,
    };
    totalCount += addGroup(items, R.id.theme_category_content, R.string.ThemeSectionBackground, 0, backgroundTextColorIds, false, searchQuery, false, sortedIds);

    // ICONS

    int[] iconColorIds = new int[] {
      ColorId.icon,
      ColorId.iconLight,
      ColorId.iconActive,
      ColorId.iconPositive,
      ColorId.iconNegative,
    };
    totalCount += addGroup(items, R.id.theme_category_content, R.string.ThemeSectionIcons, 0, iconColorIds, false, searchQuery, true, sortedIds);

    // NAVIGATION

    int[] headerColorIds = new int[] {
      ColorId.headerBackground,
      ColorId.headerText,
      ColorId.headerIcon,
    };
    int[] headerLightColorIds = new int[] {
      ColorId.headerLightBackground,
      ColorId.headerLightText,
      ColorId.headerLightIcon,
    };
    int[] headerPickerColorIds = new int[] {
      ColorId.headerPickerBackground,
      ColorId.headerPickerText,
    };
    int[] headerButtonColorIds = new int[] {
      ColorId.headerButton,
      ColorId.headerButtonIcon,
    };
    int[] headerRemoveColorIds = new int[] {
      ColorId.headerRemoveBackground,
      ColorId.headerRemoveBackgroundHighlight,
    };
    int[] headerBarColorIds = new int[] {
      ColorId.headerBarCallIncoming,
      ColorId.headerBarCallActive,
      ColorId.headerBarCallMuted,
    };
    int[] headerOtherColorIds = new int[] {
      ColorId.headerPlaceholder,
      ColorId.statusBarLegacy,
      ColorId.statusBarLegacyContent,
      ColorId.statusBar,
      ColorId.statusBarContent,
    };
    int[] lightStatusBarId = new int[] {
      PropertyId.LIGHT_STATUS_BAR
    };
    int[] headerTabColorIds = new int[] {
      ColorId.headerTabActive,
      ColorId.headerTabActiveText,
      ColorId.headerTabInactiveText,
    };
    int[] profileColorIds = new int[] {
      ColorId.profileSectionActive,
      ColorId.profileSectionActiveContent,
    };
    int[] drawerColorIds = new int[] {
      ColorId.drawer,
      ColorId.drawerText,
    };
    int[] passcodeColorIds = new int[] {
      ColorId.passcode,
      ColorId.passcodeIcon,
      ColorId.passcodeText,
    };
    int[] notificationColorIds = new int[] {
      ColorId.notification,
      ColorId.notificationPlayer,
      ColorId.notificationSecure,
    };
    count = 0;
    count += addGroup(items, R.id.theme_category_navigation, R.string.ThemeCategoryNavigation, 0, headerColorIds, false, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_navigation, count == 0 ? R.string.ThemeCategoryNavigation : 0, 0, headerTabColorIds, false, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_navigation, count == 0 ? R.string.ThemeCategoryNavigation : 0, 0, headerLightColorIds, false, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_navigation, count == 0 ? R.string.ThemeCategoryNavigation : 0, 0, headerPickerColorIds, false, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_navigation, count == 0 ? R.string.ThemeCategoryNavigation : 0, 0, headerButtonColorIds, false, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_navigation, count == 0 ? R.string.ThemeCategoryNavigation : 0, 0, headerRemoveColorIds, false, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_navigation, count == 0 ? R.string.ThemeCategoryNavigation : 0, 0, headerBarColorIds, false, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_navigation, count == 0 ? R.string.ThemeCategoryNavigation : 0, 0, headerOtherColorIds, false, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_navigation, count == 0 ? R.string.ThemeCategoryNavigation : 0, 0, lightStatusBarId, true, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_navigation, count == 0 ? R.string.ThemeCategoryNavigation : 0, 0, profileColorIds, false, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_navigation, count == 0 ? R.string.ThemeCategoryNavigation : 0, 0, drawerColorIds, false, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_navigation, count == 0 ? R.string.ThemeCategoryNavigation : 0, 0, passcodeColorIds, false, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_navigation, count == 0 ? R.string.ThemeCategoryNavigation : 0, 0, notificationColorIds, false, searchQuery, true, sortedIds);
    totalCount += count;
    
    // CONTROLS

    int[] progressColorIds = new int[] {
      ColorId.progress,
    };
    int[] controlColorIds = new int[] {
      ColorId.controlInactive,
      ColorId.controlActive,
      ColorId.controlContent,
    };
    int[] checkColorIds = new int[] {
      ColorId.checkActive,
      ColorId.checkContent,
    };
    int[] sliderColorIds = new int[] {
      ColorId.sliderActive,
      ColorId.sliderInactive,
    };
    int[] togglerActiveColorIds = new int[] {
      ColorId.togglerActive,
      ColorId.togglerActiveBackground,
    };
    int[] togglerInactiveColorIds = new int[] {
      ColorId.togglerInactive,
      ColorId.togglerInactiveBackground,
    };
    int[] togglerPositiveColorIds = new int[] {
      ColorId.togglerPositive,
      ColorId.togglerPositiveBackground,
      ColorId.togglerPositiveContent,
    };
    int[] togglerNegativeColorIds = new int[] {
      ColorId.togglerNegative,
      ColorId.togglerNegativeBackground,
      ColorId.togglerNegativeContent,
    };
    int[] inputColorIds = new int[] {
      ColorId.inputInactive,
      ColorId.inputActive,
      ColorId.inputPositive,
      ColorId.inputNegative,
      ColorId.textPlaceholder,
    };
    int[] inlineColorIds = new int[] {
      ColorId.inlineOutline,
      ColorId.inlineText,
      ColorId.inlineIcon,
      ColorId.inlineContentActive,
    };
    int[] circleColorIds = new int[] {
      ColorId.circleButtonRegular,
      ColorId.circleButtonRegularIcon,
      ColorId.circleButtonNewChat,
      ColorId.circleButtonNewChatIcon,
      ColorId.circleButtonNewGroup,
      ColorId.circleButtonNewGroupIcon,
      ColorId.circleButtonNewChannel,
      ColorId.circleButtonNewChannelIcon,
      ColorId.circleButtonNewSecret,
      ColorId.circleButtonNewSecretIcon,
      ColorId.circleButtonPositive,
      ColorId.circleButtonPositiveIcon,
      ColorId.circleButtonNegative,
      ColorId.circleButtonNegativeIcon,
      ColorId.circleButtonOverlay,
      ColorId.circleButtonOverlayIcon,
      ColorId.circleButtonChat,
      ColorId.circleButtonChatIcon,
      ColorId.circleButtonTheme,
      ColorId.circleButtonThemeIcon,
    };
    int[] statusColorIds = new int[] {
      ColorId.online,
      ColorId.promo,
      ColorId.promoContent,
    };
    int[] introColorIds = new int[] {
      ColorId.introSectionActive,
      ColorId.introSection,
    };
    count = 0;
    count += addGroup(items, R.id.theme_category_controls, R.string.ThemeCategoryControls, 0, progressColorIds, false, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_controls, count == 0 ? R.string.ThemeCategoryControls : 0, 0, controlColorIds, false, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_controls, count == 0 ? R.string.ThemeCategoryControls : 0, 0, checkColorIds, false, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_controls, count == 0 ? R.string.ThemeCategoryControls : 0, 0, sliderColorIds, false, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_controls, count == 0 ? R.string.ThemeCategoryControls : 0, 0, togglerActiveColorIds, false, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_controls, count == 0 ? R.string.ThemeCategoryControls : 0, 0, togglerInactiveColorIds, false, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_controls, count == 0 ? R.string.ThemeCategoryControls : 0, 0, togglerPositiveColorIds, false, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_controls, count == 0 ? R.string.ThemeCategoryControls : 0, 0, togglerNegativeColorIds, false, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_controls, count == 0 ? R.string.ThemeCategoryControls : 0, 0, inputColorIds, false, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_controls, count == 0 ? R.string.ThemeCategoryControls : 0, 0, inlineColorIds, false, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_controls, count == 0 ? R.string.ThemeCategoryControls : 0, 0, circleColorIds, false, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_controls, count == 0 ? R.string.ThemeCategoryControls : 0, 0, statusColorIds, false, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_controls, count == 0 ? R.string.ThemeCategoryControls : 0, 0, introColorIds, false, searchQuery, true, sortedIds);
    totalCount += count;

    // PLAYER

    int[] playerColorIds = new int[] {
      ColorId.seekDone,
      ColorId.seekReady,
      ColorId.seekEmpty,
      ColorId.playerButtonActive,
      ColorId.playerButton,
      ColorId.playerCoverIcon,
      ColorId.playerCoverPlaceholder,
    };

    totalCount += addGroup(items, R.id.theme_category_controls, R.string.ThemeSectionPlayer, 0, playerColorIds, false, searchQuery, true, sortedIds);

    // PROPERTIES

    int[] propertyIds = new int[] {
      PropertyId.PARENT_THEME,
      PropertyId.BUBBLE_CORNER,
      PropertyId.BUBBLE_CORNER_MERGED,
      PropertyId.BUBBLE_CORNER_LEGACY,
      // TODO PropertyId.BUBBLE_OUTER_MARGIN,
      PropertyId.BUBBLE_OUTLINE,
      PropertyId.BUBBLE_OUTLINE_SIZE,
      PropertyId.BUBBLE_DATE_CORNER,
      PropertyId.BUBBLE_UNREAD_SHADOW,
      PropertyId.AVATAR_RADIUS,
      PropertyId.AVATAR_RADIUS_FORUM,
      PropertyId.AVATAR_RADIUS_CHAT_LIST,
      PropertyId.AVATAR_RADIUS_CHAT_LIST_FORUM,
      PropertyId.LIGHT_STATUS_BAR,
      PropertyId.IMAGE_CORNER,
      PropertyId.DATE_CORNER,
      PropertyId.REPLACE_SHADOWS_WITH_SEPARATORS,
      PropertyId.SHADOW_DEPTH,
      PropertyId.SUBTITLE_ALPHA,
      PropertyId.WALLPAPER_USAGE_ID,
      PropertyId.WALLPAPER_ID,
      PropertyId.DARK,
      PropertyId.WALLPAPER_OVERRIDE_UNREAD,
      PropertyId.WALLPAPER_OVERRIDE_DATE,
      PropertyId.WALLPAPER_OVERRIDE_BUTTON,
      PropertyId.WALLPAPER_OVERRIDE_MEDIA_REPLY,
      PropertyId.WALLPAPER_OVERRIDE_TIME,
      PropertyId.WALLPAPER_OVERRIDE_OVERLAY,
    };
    totalCount += addGroup(items, R.id.theme_category_settings, R.string.ThemeAdvanced, 0, propertyIds, true, searchQuery, true, sortedIds);

    // CHAT

    int[] chatListColorIds = new int[] {
      ColorId.chatListAction,
      ColorId.chatListMute,
      ColorId.chatListIcon,
      ColorId.chatListVerify,

      ColorId.ticks,
      ColorId.ticksRead,

      ColorId.badge,
      ColorId.badgeText,
      ColorId.badgeFailed,
      ColorId.badgeFailedText,
      ColorId.badgeMuted,
      ColorId.badgeMutedText,
    };
    int[] chatColorIds = new int[] {
      ColorId.chatSendButton,
      ColorId.chatKeyboard,
      ColorId.chatKeyboardButton,
    };
    int[] plainColorIds = new int[] {
      ColorId.chatBackground,
      ColorId.chatSeparator,

      ColorId.unread,
      ColorId.unreadText,

      ColorId.messageVerticalLine,
      ColorId.messageSelection,
      ColorId.messageSwipeBackground,
      ColorId.messageSwipeContent,
      ColorId.messageAuthor,
      ColorId.messageAuthorPsa,
    };
    int[] chatOtherColorIds = new int[] {
      ColorId.shareSeparator
    };
    int[] bubbleColorIds = new int[] {
      ColorId.bubble_chatBackground,
      ColorId.bubble_chatSeparator,
      ColorId.bubble_messageSelection,
      ColorId.bubble_messageSelectionNoWallpaper,
      ColorId.bubble_messageCheckOutline,
      ColorId.bubble_messageCheckOutlineNoWallpaper,
    };
    int[] bubbleInColorIds = new int[] {
      ColorId.bubbleIn_background,
      ColorId.bubbleIn_time,
      ColorId.bubbleIn_progress,
      ColorId.bubbleIn_text,
      ColorId.bubbleIn_textLink,
      ColorId.bubbleIn_textLinkPressHighlight,
      ColorId.bubbleIn_outline,
      ColorId.bubbleIn_pressed,
      ColorId.bubbleIn_separator,
    };
    int[] bubbleOutColorIds = new int[] {
      ColorId.bubbleOut_background,
      ColorId.bubbleOut_ticks,
      ColorId.bubbleOut_ticksRead,
      ColorId.bubbleOut_time,
      ColorId.bubbleOut_progress,
      ColorId.bubbleOut_text,
      ColorId.bubbleOut_textLink,
      ColorId.bubbleOut_textLinkPressHighlight,
      ColorId.bubbleOut_messageAuthor,
      ColorId.bubbleOut_messageAuthorPsa,
      ColorId.bubbleOut_chatVerticalLine,
      ColorId.bubbleOut_inlineOutline,
      ColorId.bubbleOut_inlineText,
      ColorId.bubbleOut_inlineIcon,
      ColorId.bubbleOut_waveformActive,
      ColorId.bubbleOut_waveformInactive,
      ColorId.bubbleOut_file,
      ColorId.bubbleOut_outline,
      ColorId.bubbleOut_pressed,
      ColorId.bubbleOut_separator,
    };
    int[] bubbleOverlayNoWallpaperColorIds = new int[] {
      ColorId.bubble_unread_noWallpaper,
      ColorId.bubble_unreadText_noWallpaper,
      ColorId.bubble_date_noWallpaper,
      ColorId.bubble_dateText_noWallpaper,
      ColorId.bubble_button_noWallpaper,
      ColorId.bubble_buttonRipple_noWallpaper,
      ColorId.bubble_buttonText_noWallpaper,
      ColorId.bubble_mediaReply_noWallpaper,
      ColorId.bubble_mediaReplyText_noWallpaper,
      ColorId.bubble_mediaTime_noWallpaper,
      ColorId.bubble_mediaTimeText_noWallpaper,
      ColorId.bubble_overlay_noWallpaper,
      ColorId.bubble_overlayText_noWallpaper,
    };
    int[] bubbleOverlayColorIds = new int[] {
      ColorId.bubble_unread,
      ColorId.bubble_unreadText,
      ColorId.bubble_date,
      ColorId.bubble_dateText,
      ColorId.bubble_button,
      ColorId.bubble_buttonRipple,
      ColorId.bubble_buttonText,
      ColorId.bubble_mediaReply,
      ColorId.bubble_mediaReplyText,
      ColorId.bubble_mediaTime,
      ColorId.bubble_mediaTimeText,
      ColorId.bubble_mediaOverlay,
      ColorId.bubble_mediaOverlayText,
      ColorId.bubble_overlay,
      ColorId.bubble_overlayText,
    };
    int[] bubbleVisualProperties = new int[] {
      PropertyId.BUBBLE_CORNER,
      PropertyId.BUBBLE_CORNER_MERGED,
      PropertyId.BUBBLE_CORNER_LEGACY,
      // TODO PropertyId.BUBBLE_OUTER_MARGIN,
      PropertyId.BUBBLE_OUTLINE,
      PropertyId.BUBBLE_OUTLINE_SIZE,
      PropertyId.BUBBLE_DATE_CORNER,
      PropertyId.BUBBLE_UNREAD_SHADOW,
      PropertyId.IMAGE_CORNER,
      PropertyId.DATE_CORNER,
      PropertyId.WALLPAPER_USAGE_ID,
      PropertyId.WALLPAPER_ID,
      PropertyId.WALLPAPER_OVERRIDE_UNREAD,
      PropertyId.WALLPAPER_OVERRIDE_DATE,
      PropertyId.WALLPAPER_OVERRIDE_BUTTON,
      PropertyId.WALLPAPER_OVERRIDE_MEDIA_REPLY,
      PropertyId.WALLPAPER_OVERRIDE_TIME,
      PropertyId.WALLPAPER_OVERRIDE_OVERLAY,
    };
    count = 0;
    count += addGroup(items, R.id.theme_category_chat, 0, 0, chatListColorIds, false, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_chat, 0, 0, chatColorIds, false, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_chat, 0, 0, plainColorIds, false, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_chat, 0, 0, chatOtherColorIds, false, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_bubbles, 0, 0, bubbleColorIds, false, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_bubbles, 0, 0, bubbleInColorIds, false, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_bubbles, 0, 0, bubbleOutColorIds, false, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_bubbles, 0, 0, bubbleOverlayColorIds, false, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_bubbles, 0, R.string.g_noWallpaper, bubbleOverlayNoWallpaperColorIds, false, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_bubbles, R.string.ThemeAdvanced, 0, bubbleVisualProperties, true, searchQuery, true, sortedIds);
    totalCount += count;

    // USERS

    int[] placeholderColorIds = new int[] {
      ColorId.avatar_content,

      ColorId.avatarArchive,
      ColorId.avatarArchivePinned,

      ColorId.avatarSavedMessages,
      ColorId.avatarSavedMessages_big,

      ColorId.avatarReplies,
      ColorId.avatarReplies_big,

      ColorId.avatarInactive,
      ColorId.avatarInactive_big,
      ColorId.nameInactive,

      ColorId.avatarRed,
      ColorId.avatarRed_big,
      ColorId.nameRed,

      ColorId.avatarOrange,
      ColorId.avatarOrange_big,
      ColorId.nameOrange,

      ColorId.avatarYellow,
      ColorId.avatarYellow_big,
      ColorId.nameYellow,

      ColorId.avatarGreen,
      ColorId.avatarGreen_big,
      ColorId.nameGreen,

      ColorId.avatarCyan,
      ColorId.avatarCyan_big,
      ColorId.nameCyan,

      ColorId.avatarBlue,
      ColorId.avatarBlue_big,
      ColorId.nameBlue,

      ColorId.avatarViolet,
      ColorId.avatarViolet_big,
      ColorId.nameViolet,

      ColorId.avatarPink,
      ColorId.avatarPink_big,
      ColorId.namePink,
    };
    
    count = 0;
    count += addGroup(items, R.id.theme_category_colors, 0, 0, placeholderColorIds, false, searchQuery, true, sortedIds);
    // count += addGroup(items, R.id.theme_category_colors, count == 0 ? R.string.ThemeSectionUsers : 0, 0, nameColorIds, false, searchQuery, true, sortedIds);
    totalCount += count;
    
    // MEDIA
    
    int[] fileColorIds = new int[] {
      ColorId.file,
      ColorId.fileYellow,
      ColorId.fileGreen,
      ColorId.fileRed,
    };
    int[] waveformColorIds = new int[] {
      ColorId.waveformActive,
      ColorId.waveformInactive,
    };
    count = 0;
    count += addGroup(items, R.id.theme_category_colors, R.string.ThemeSectionMedia, 0, fileColorIds, false, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_colors, count == 0 ? R.string.ThemeSectionMedia : 0, 0, waveformColorIds, false, searchQuery, true, sortedIds);
    totalCount += count;

    // ATTACH MENU

    int[] attachColorIds = new int[] {
      ColorId.attachPhoto,
      ColorId.attachFile,
      ColorId.attachLocation,
      ColorId.attachContact,
      ColorId.attachInlineBot,
      ColorId.attachText,
      ColorId.fileAttach,
    };
    totalCount += addGroup(items, R.id.theme_category_colors, R.string.ThemeSectionAttach, 0, attachColorIds, false, searchQuery, true, sortedIds);

    // INSTANT VIEW

    int[] ivColorIds = new int[] {
      ColorId.iv_pageTitle,
      ColorId.iv_pageSubtitle,

      ColorId.iv_text,
      ColorId.iv_textLink,
      ColorId.iv_textLinkPressHighlight,
      ColorId.iv_textMarked,
      ColorId.iv_textMarkedBackground,
      ColorId.iv_textMarkedLink,
      ColorId.iv_textMarkedLinkPressHighlight,
      ColorId.iv_textReference,
      ColorId.iv_textReferenceBackground,
      ColorId.iv_textReferenceBackgroundPressed,
      ColorId.iv_textReferenceOutline,
      ColorId.iv_textReferenceOutlinePressed,
      ColorId.iv_textCode,
      ColorId.iv_pageAuthor,
      ColorId.iv_caption,
      ColorId.iv_pageFooter,
      ColorId.iv_header,

      ColorId.iv_pullQuote,
      ColorId.iv_blockQuote,
      ColorId.iv_blockQuoteLine,

      ColorId.iv_preBlockBackground,
      ColorId.iv_textCodeBackground,
      ColorId.iv_textCodeBackgroundPressed,
      ColorId.iv_separator,

      ColorId.ivHeaderIcon,
      ColorId.ivHeader
    };
    totalCount += addGroup(items, R.id.theme_category_iv, R.string.ThemeCategoryIV, 0, ivColorIds, false, searchQuery, true, sortedIds);
    
    // THEME RADIOS
    
    int[] themeColorIds = new int[] {
      ColorId.themeClassic,
      ColorId.themeBlue,
      ColorId.themeRed,
      ColorId.themeOrange,
      ColorId.themeGreen,
      ColorId.themePink,
      ColorId.themeCyan,
      ColorId.themeNightBlue,
      ColorId.themeNightBlack,

      ColorId.themeBlackWhite,
      ColorId.themeWhiteBlack,
    };
    count = addGroup(items, R.id.theme_category_other, R.string.ThemeCategoryOther, R.string.ThemeSectionRadios_info, themeColorIds, false, searchQuery, true, sortedIds);
    
    // Wallpaper overlays
    
    int[] wallpaperColorIds = new int[] {
      ColorId.wp_cats,
      ColorId.wp_catsPink,
      ColorId.wp_catsGreen,
      ColorId.wp_catsOrange,
      ColorId.wp_catsBeige,
      ColorId.wp_circlesBlue,
    };
    count += addGroup(items, R.id.theme_category_other, count == 0 ? R.string.ThemeCategoryOther : 0, R.string.ThemeSectionWP_info, wallpaperColorIds, false, searchQuery, true, sortedIds);

    int[] scrollBarColorIds = new int[] {
      ColorId.sectionedScrollBar,
      ColorId.sectionedScrollBarActive,
      ColorId.sectionedScrollBarActiveContent,
    };
    count += addGroup(items, R.id.theme_category_other, count == 0 ? R.string.ThemeCategoryOther : 0, 0, scrollBarColorIds, false, searchQuery, true, sortedIds);

    int[] snackBarColorIds = new int[] {
      ColorId.snackbarUpdate,
      ColorId.snackbarUpdateAction,
      ColorId.snackbarUpdateText,
    };
    count += addGroup(items, R.id.theme_category_other, count == 0 ? R.string.ThemeCategoryOther : 0, 0, snackBarColorIds, false, searchQuery, true, sortedIds);

    totalCount += count;

    // INTERNAL

    int[] internalColorIds = new int[] {
      ColorId.caption_textLink,
      ColorId.caption_textLinkPressHighlight,

      ColorId.videoSliderActive,
      ColorId.videoSliderInactive,

      ColorId.white,
      ColorId.black,
      ColorId.transparentEditor,
    };
    int[] photoShadowColorIds = new int[] {
      ColorId.photoShadowTint1,
      ColorId.photoShadowTint2,
      ColorId.photoShadowTint3,
      ColorId.photoShadowTint4,
      ColorId.photoShadowTint5,
      ColorId.photoShadowTint6,
      ColorId.photoShadowTint7,
      ColorId.photoHighlightTint1,
      ColorId.photoHighlightTint2,
      ColorId.photoHighlightTint3,
      ColorId.photoHighlightTint4,
      ColorId.photoHighlightTint5,
      ColorId.photoHighlightTint6,
      ColorId.photoHighlightTint7,
    };
    int[] ledColorIds = new int[] {
      ColorId.ledBlue,
      ColorId.ledOrange,
      ColorId.ledYellow,
      ColorId.ledGreen,
      ColorId.ledCyan,
      ColorId.ledRed,
      ColorId.ledPurple,
      ColorId.ledPink,
      ColorId.ledWhite,
    };

    count = 0;
    count += addGroup(items, R.id.theme_category_internal, R.string.ThemeCategoryInternal, 0, internalColorIds, false, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_internal, count == 0 ? R.string.ThemeCategoryInternal : 0, 0, photoShadowColorIds, false, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_internal, count == 0 ? R.string.ThemeCategoryInternal : 0, 0, ledColorIds, false, searchQuery, true, sortedIds);
    totalCount += count;

    // <item type="id" name="([^"]+)" />
    // R.id.$1,
    
    // UNSORTED

    if (sortedIds != null) {
      Set<Integer> unsortedColorIds = ThemeColors.getAll();
      unsortedColorIds.removeAll(sortedIds);
      if (!unsortedColorIds.isEmpty()) {
        int[] colorIds = new int[unsortedColorIds.size()];
        int i = 0;
        for (int colorId : unsortedColorIds) {
          colorIds[i] = colorId;
          i++;
        }
        totalCount += addGroup(items, R.id.theme_category_other, R.string.ThemeSectionUnsorted, 0, colorIds, false, searchQuery, true, sortedIds);
      }
    }

    items.add(new ListItem(ListItem.TYPE_LIST_INFO_VIEW));

    return totalCount;
  }

  private int itemCount;

  private boolean ignorePropertyEvents;

  @Override
  public void onThemePropertyChanged (int themeId, int propertyId, float value, boolean isDefault) {
    if (!ignorePropertyEvents) {
      adapter.updateValuedSettingByLongId(propertyId);
    }
  }

  @Override
  public void onClick (final View v) {
    if (isInTransparentMode() && !(v.getId() == R.id.btn_colorUndo || v.getId() == R.id.btn_colorRedo)) {
      return;
    }
    ListItem foundItem = (ListItem) v.getTag();
    final int viewId = v.getId();
    if (viewId == R.id.btn_color) {
      if (isLookupMode) {
        getArgumentsStrict().lookupParent.highlightColor(getDataId(foundItem));
        hideSoftwareKeyboard();
        tdlib.ui().postDelayed(() -> {
          navigateBack();
        }, 120);
      } else {
        editColor(getRecyclerView().getChildAdapterPosition(v), getDataId(foundItem));
      }
      return;
    } else if (viewId == R.id.btn_property) {
      int propertyId = getDataId(foundItem);
      if (ThemeManager.isBoolProperty(propertyId)) {
        TogglerView togglerView = ((SettingView) v).getToggler();
        if (togglerView != null) {
          ignorePropertyEvents = true;
          if (saveProperty(propertyId, togglerView.isEnabled() ? 0f : 1f)) {
            adapter.setValuedSetting(foundItem, (SettingView) v, true);
          }
          ignorePropertyEvents = false;
        }
      } else {
        editProperty(propertyId);
      }
      return;
    }
    if (foundItem != null)
      return;
    final ViewGroup contentView = findColorParent(v);
    if (contentView == null)
      return;
    final ListItem item = (ListItem) contentView.getTag();
    final ColorState state = (ColorState) item.getData();
    if (state == null)
      return;

    final int currentEditPosition = editPosition;

    if (viewId == R.id.btn_colorCopy) {
      int color = state.getColor();
      UI.copyText(Theme.getColorName(state.getColorId()) + ": " + getColorRepresentation(color, true), R.string.CopiedColor);
    } else if (viewId == R.id.btn_colorPaste) {
      try {
        int newColor = getPasteColor();
        if (state.getColor() != newColor)
          state.saveLastColor();
        changeColor(item, contentView, v, state, newColor, false, false);
      } catch (Throwable ignored) {
        // Should be unreachable
      }
    } else if (viewId == R.id.btn_colorSave) {
      if (state.saveLastColor()) {
        v.setEnabled(false);
      }
    } else if (viewId == R.id.btn_colorUndo || viewId == R.id.btn_colorRedo) {
      boolean done = viewId == R.id.btn_colorUndo ? state.undo() : state.redo();
      if (done && !changeColor(item, contentView, v, state, state.getColor(), false, false)) {
        adapter.setColor(item, -1, contentView, v);
      }
      ViewGroup viewGroup = (ViewGroup) v.getParent();
      ThemeController c = findThemeController();
      if (c != null && c.isDetached()) {
        if (!isInTransparentMode() || opaqueChild == viewGroup) {
          setInTransparentMode(true, viewGroup);
          clearTransparentModeDelayed();
        }
      }
    } else if (viewId == R.id.btn_colorClear) {
      removeColor(v, !state.canUndo());
    } else if (viewId == R.id.btn_colorCalculate) {
      openInputAlert(Lang.getString(R.string.ThemeCalcTitle), Lang.getString(R.string.ThemeCalcHint), R.string.ThemeCalcSave, R.string.Cancel, Strings.getHexColor(getTheme().getColor(ColorId.filling), false), (inputView, result) -> {
        try {
          int parsedColor = parseAnyColor(result);
          if (Color.alpha(parsedColor) == 255 && currentEditPosition == editPosition) {
            changeColor(item, contentView, v, state, ColorUtils.compositeColor(parsedColor, state.getColor()), false, false);
            return true;
          }
        } catch (Throwable ignored) {
        }
        return false;
      }, true);
    }
  }

  @Override
  public boolean onLongClick (View v) {
    if (v.getId() == R.id.btn_colorClear) {
      removeColor(v, true);
      return true;
    }
    return false;
  }

  private void removeColor (View v, boolean clearAll) {
    final ViewGroup contentView = findColorParent(v);
    if (contentView == null)
      return;
    final ListItem item = (ListItem) contentView.getTag();
    final ColorState state = (ColorState) item.getData();
    if (state == null)
      return;
    int currentEditPosition = editPosition;
    if (clearAll) {
      // Remove all colors
      showOptions(Lang.plural(R.string.ColorClearAll, state.getVersionCount(false), Lang.boldCreator(), Theme.getColorName(state.getColorId())), new int[]{
        R.id.btn_done, R.id.btn_cancel
      }, new String[]{
        Lang.plural(R.string.ColorClearDone, state.getVersionCount(false)),
        Lang.getString(R.string.Cancel)
      }, new int[]{
        OPTION_COLOR_RED, OPTION_COLOR_NORMAL
      }, new int[]{
        R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24
      }, (itemView, optionId) -> {
        if (optionId == R.id.btn_done && currentEditPosition == editPosition && state.clear() && !changeColor(item, contentView, v, state, state.getColor(), false, true)) {
          contentView.findViewById(R.id.btn_colorUndo).setEnabled(state.canUndo());
          contentView.findViewById(R.id.btn_colorRedo).setEnabled(state.canRedo());
          contentView.findViewById(R.id.btn_colorClear).setEnabled(state.canClear());
          findColorCell(state.getColorId());
        }
        return true;
      });
    } else {
      // Remove current color
      showOptions(Lang.getStringBold(R.string.ColorRemove, Theme.getColorName(state.getColorId())), new int[] {
        R.id.btn_done, R.id.btn_cancel
      }, new String[] {
        Lang.getString(R.string.ColorRemoveDone),
        Lang.getString(R.string.Cancel)
      }, new int[] {
        OPTION_COLOR_RED, OPTION_COLOR_NORMAL
      }, new int[] {
        R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24
      }, (itemView, optionId) -> {
        if (optionId == R.id.btn_done && currentEditPosition == editPosition && state.removeCurrent() && !changeColor(item, contentView, v, state, state.getColor(), false, true)) {
          contentView.findViewById(R.id.btn_colorUndo).setEnabled(state.canUndo());
          contentView.findViewById(R.id.btn_colorRedo).setEnabled(state.canRedo());
          contentView.findViewById(R.id.btn_colorClear).setEnabled(state.canClear());
        }
        return true;
      });
    }
  }

  private int editPosition = -1;

  private void setEditPosition (int position, int colorId, boolean needScroll) {
    if (this.editPosition != position) {
      if (this.editPosition != -1) {
        removeColorPicker(editPosition);
        if (position != -1 && position > editPosition) {
          position -= EDITOR_CELL_COUNT;
        }
      }
      this.editPosition = position;
      if (position != -1) {
        context().requestBlankFocus();
        addColorPicker(position, colorId, needScroll);
        ThemeController c = findThemeController();
        if (c != null) {
          c.closeOtherEditors(this, colorId);
        }
      }
    } else if (position != -1) {
      removeColorPicker(position);
      this.editPosition = -1;
    }
    /*if (getParentOrSelf() instanceof ViewPagerController)
      ((ViewPagerController) getParentOrSelf()).getViewPager().setPagingEnabled(position == -1);*/
  }

  private static final int EDITOR_CELL_COUNT = 1;
  private ListItem colorPicker;

  private void addColorPicker (int position, int colorId, boolean needScroll) {
    int topCellCount = 1;
    if (adapter.getItems().get(position + 1).getViewType() == ListItem.TYPE_DESCRIPTION_SMALL) {
      topCellCount++;
    }
    adapter.getItems().add(position + topCellCount, colorPicker = new ListItem(ListItem.TYPE_COLOR_PICKER).setLongId(colorId));
    adapter.notifyItemRangeInserted(position + topCellCount, EDITOR_CELL_COUNT);

    if (!needScroll)
      return;

    LinearLayoutManager manager = (LinearLayoutManager) getRecyclerView().getLayoutManager();
    View topView = manager.findViewByPosition(position);
    if (topView != null && topView.getTop() < 0) {
      manager.scrollToPositionWithOffset(position, 0);
    } else {
      int colorPickerHeight =
        SettingHolder.measureHeightForType(ListItem.TYPE_COLOR_PICKER);

      int nextIndex = -1;
      int addHeight = 0;
      for (int i = position + topCellCount + 1; i < adapter.getItems().size() && nextIndex == -1; i++) {
        int viewType = adapter.getItems().get(i).getViewType();
        try {
          addHeight += SettingHolder.measureHeightForType(viewType);
          switch (viewType) {
            case ListItem.TYPE_VALUED_SETTING_COMPACT:
            case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_COLOR:
            case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_RADIO:
            case ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER:
              nextIndex = i;
              break;
            default:
              if (viewType <= ListItem.TYPE_CUSTOM)
                nextIndex = i;
              break;
          }
        } catch (Throwable ignored) {
          break;
        }
      }
      if (nextIndex != -1) {
        colorPickerHeight += addHeight;
      }

      View bottomView = topCellCount == 1 ? topView : manager.findViewByPosition(position + 1);
      if (bottomView == null || bottomView.getBottom() + colorPickerHeight > getRecyclerView().getMeasuredHeight()) {
        manager.scrollToPositionWithOffset(position + topCellCount, getRecyclerView().getMeasuredHeight() - colorPickerHeight);
      }
    }
  }

  private void removeColorPicker (int position) {
    if (adapter.getItems().get(position + 1).getViewType() == ListItem.TYPE_DESCRIPTION_SMALL)
      position++;
    position++;
    adapter.removeRange(position, EDITOR_CELL_COUNT);
    ColorState state = (ColorState) colorPicker.getData();
    if (state != null) {
      saveChanges(colorPicker, state);
    }
    colorPicker = null;
    if (getParentOrSelf().getLockFocusView() != null) {
      getParentOrSelf().hideSoftwareKeyboard();
      context().requestBlankFocus();
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    if (colorPicker != null) {
      ColorState state = (ColorState) colorPicker.getData();
      saveChanges(colorPicker, state);
    }
  }

  private void editColor (int position, @ColorId int colorId) {
    setEditPosition(position, colorId, true);
    /*if (position != -1)
      return;
    String colorName = Theme.getColorName(colorId);
    int color = getTheme().getColor(colorId);
    String hexColor = getHexColor(color);
    openInputAlert(colorName, Lang.getString(R.string.Color), R.string.Save, R.string.Cancel, hexColor, (inputView, result) -> {
      if (!result.startsWith("#"))
        result = "#" + result;
      if (hexColor.length() != result.length())
        return false;
      int newColor;
      try {
        newColor = Color.parseColor(result.length() == 8 ? result.substring(6) + result.substring(0, 6) : result);
      } catch (Throwable t) {
        return false;
      }
      setColor(colorId, newColor, false);
      return true;
    }, true);*/
  }

  private ThemeCustom getTheme () {
    return (ThemeCustom) getArgumentsStrict().theme.getTheme();
  }

  private boolean canDisplay (int sectionId, int id, boolean isProperty) {
    Args args = getArgumentsStrict();
    return args.specificSectionId == 0 || args.specificSectionId == sectionId || (!isProperty && args.specificSectionId == R.id.theme_category_main && isMainColor(id));
  }

  public static boolean isMainColor (int colorId) {
    switch (colorId) {
      case ColorId.profileSectionActive:
      case ColorId.profileSectionActiveContent:
      case ColorId.textLink:
      case ColorId.textLinkPressHighlight:
      case ColorId.textSearchQueryHighlight:
      case ColorId.textNeutral:
      case ColorId.iconActive:
      case ColorId.headerBackground:
      case ColorId.fillingPositive:
      case ColorId.ticks:
      case ColorId.passcode:
      case ColorId.bubbleOut_ticks:
      case ColorId.messageVerticalLine:
      case ColorId.chatSendButton:
      case ColorId.messageSwipeBackground:
      case ColorId.unread:
      case ColorId.unreadText:
      case ColorId.messageAuthor:
      case ColorId.messageAuthorPsa:
      case ColorId.chatListAction:
      case ColorId.badge:
      case ColorId.online:
      case ColorId.messageSelection:
      case ColorId.textSelectionHighlight:
      case ColorId.bubbleOut_background:
      case ColorId.bubbleOut_time:
      case ColorId.bubbleOut_progress:
      case ColorId.bubbleIn_progress:
      case ColorId.bubbleIn_textLink:
      case ColorId.bubbleIn_textLinkPressHighlight:
      case ColorId.checkActive:
      case ColorId.file:
      case ColorId.playerButtonActive:
      case ColorId.bubbleOut_file:
      case ColorId.waveformActive:
      case ColorId.bubbleOut_waveformActive:
      case ColorId.waveformInactive:
      case ColorId.bubbleOut_waveformInactive:
      case ColorId.sliderActive:
      case ColorId.seekReady:
      case ColorId.seekDone:
      case ColorId.inlineText:
      case ColorId.inlineOutline:
      case ColorId.inlineIcon:
      case ColorId.progress:
      case ColorId.togglerActive:
      case ColorId.togglerActiveBackground:
      case ColorId.circleButtonRegular:
      case ColorId.circleButtonTheme:
      case ColorId.inputActive:
      case ColorId.controlActive:
      case ColorId.promo:
      case ColorId.headerBarCallActive:
      case ColorId.chatListVerify:
      case ColorId.bubbleOut_textLink:
      case ColorId.bubbleOut_textLinkPressHighlight:
      case ColorId.bubbleOut_messageAuthor:
      case ColorId.bubbleOut_messageAuthorPsa:
      case ColorId.bubbleOut_chatVerticalLine:
      case ColorId.bubbleOut_inlineOutline:
      case ColorId.bubbleOut_inlineIcon:
      case ColorId.bubbleOut_inlineText:
      case ColorId.notification:
      case ColorId.notificationPlayer:
        return true;
    }
    return false;
  }

  // Passing from color picker

  private boolean changeColor (ListItem item, ViewGroup contentView, View view, ColorState state, int newColor, boolean isTemporaryChange, boolean forceSave) {
    boolean changed = state.setCurrentColor(newColor, !isTemporaryChange);
    if (changed) {
      saveColor(state, isTemporaryChange);
      if (!isDestroyed()) {
        adapter.setColor(item, -1, contentView, view);
      }
      return true;
    } else if (forceSave) {
      saveColor(state, isTemporaryChange);
    }
    return false;
  }

  private boolean changeHsv (ListItem item, ViewGroup contentView, View view, ColorState state, int prop, float newValue, boolean isTemporaryChange, boolean forceSave) {
    boolean changed = state.setHsv(prop, newValue, !isTemporaryChange);
    if (changed) {
      saveColor(state, isTemporaryChange);
      if (!isDestroyed()) {
        adapter.setColor(item, -1, contentView, view);
      }
      return true;
    } else if (forceSave) {
      saveColor(state, isTemporaryChange);
    }
    return false;
  }

  private void saveChanges (ListItem item, ColorState state) {
    if (state.setCurrentColor(state.getColor(), true)) {
      saveColor(state, false);
    }
  }

  // Passing color to theme

  private void checkColorHistory (int position, boolean isDefault, boolean value) {
    if (position == -1)
      return;
    DrawModifier modifier = adapter.getItems().get(position).getDrawModifier();
    if (modifier instanceof ItemModifier) {
      if (isDefault) {
        ((ItemModifier) modifier).checkHistory();
      } else {
        ((ItemModifier) modifier).setHasHistory(value);
      }
    }
  }

  public void updateColorValue (int colorId, boolean updateEditor) {
    int position = findColorCell(colorId);
    if (position != -1) {
      adapter.updateValuedSettingByPosition(position);
      if (updateEditor && editPosition != -1 && getDataId(adapter.getItems().get(editPosition)) == colorId && colorPicker != null) {
        int i = adapter.indexOfView(colorPicker, editPosition);
        if (i != -1) {
          View view = getRecyclerView().getLayoutManager().findViewByPosition(i);
          if (view instanceof ViewGroup) {
            adapter.setColor(colorPicker, -1, (ViewGroup) view, null);
          }
        }
      }
    }
  }

  public void forceClosePicker () {
    if (colorPicker != null) {
      forceClosePicker(((ColorState) colorPicker.getData()).getColorId());
    }
  }

  public void forceClosePicker (int colorId) {
    if (colorPicker != null && ((ColorState) colorPicker.getData()).getColorId() == colorId) {
      RecyclerView.ItemAnimator animator = getRecyclerView().getItemAnimator();
      if (animator != null)
        getRecyclerView().setItemAnimator(null);
      setEditPosition(-1, colorId, true);
      if (animator != null)
        tdlib.ui().postDelayed(() -> getRecyclerView().setItemAnimator(animator), 100);
    }
  }

  private int findColorCell (int colorId) {
    if (editPosition != -1 && getDataId(adapter.getItems().get(editPosition)) == colorId) {
      ColorState state = (ColorState) colorPicker.getData();
      if (state != null) {
        checkColorHistory(editPosition, false, state.hasHistory());
      } else {
        checkColorHistory(editPosition, true, false);
      }
      return editPosition;
    } else {
      int i = adapter.indexOfViewByLongId(colorId);
      checkColorHistory(i, true, false);
      return i;
    }
  }

  private void saveColor (@NonNull ColorState state, boolean isTemporaryChange) {
    ThemeCustom theme = getTheme();
    int colorId = state.getColorId();
    int currentColor = theme.getColor(colorId);
    int newColor = state.getColor();
    int themeId = theme.getId();
    int customThemeId = ThemeManager.resolveCustomThemeId(themeId);
    Integer color = state.isDefault() ? null : newColor;
    if (!isTemporaryChange) {
      int[] stack = state.getNewStack();
      boolean stackChanged = state.saveStack(stack);
      if (stackChanged) {
        SharedPreferences.Editor editor = Settings.instance().edit();
        Settings.instance().setCustomThemeColor(customThemeId, colorId, color);
        Settings.instance().setColorHistory(customThemeId, colorId, state.getNewStack());
        editor.apply();
      } else {
        Settings.instance().setCustomThemeColor(customThemeId, colorId, color);
      }
    }
    if (currentColor != newColor) {
      theme.setColor(colorId, color);
      ThemeManager.instance().notifyColorChanged(theme.getId(), state, isTemporaryChange);
      if (!isDestroyed()) {
        updateColorValue(colorId, false);
      }
    } else if (!isTemporaryChange) {
      ThemeManager.instance().notifyColorChanged(theme.getId(), state, false);
      findColorCell(colorId);
    }

    switch (colorId) {
      case ColorId.filling:
      case ColorId.background:
      case ColorId.iv_preBlockBackground:
      case ColorId.iv_textCodeBackground:
        getRecyclerView().invalidate();
        break;
    }
  }

  // Properties

  private void editProperty (@PropertyId int propertyId) {
    ThemeCustom theme = getTheme();
    float currentValue = theme.getProperty(propertyId);
    float originalValue = theme.getParentTheme().getProperty(propertyId);
    String defaultValue = U.formatFloat(originalValue, true);
    if (propertyId == PropertyId.PARENT_THEME)
      defaultValue = null;
    openInputAlert(Lang.getString(R.string.ThemeAdvancedEdit), Theme.getPropertyName(propertyId), R.string.Save, R.string.Cancel, U.formatFloat(currentValue,  true), defaultValue, (v, result) -> {
      float rawValue;
      if (result.equals("true")) {
        rawValue = 1;
      } else if (result.equals("false")) {
        rawValue = 0;
      } else {
        try {
          rawValue = Float.parseFloat(result);
        } catch (Throwable ignored) {
          return false;
        }
      }
      return saveProperty(propertyId, rawValue);
    }, true, null, null);
  }

  private boolean saveProperty (@PropertyId int propertyId, float rawValue) {
    if (propertyId == PropertyId.WALLPAPER_ID && !tdlib.wallpaper().isValidWallpaperId((int) rawValue)) {
      return false;
    }
    if (!ThemeManager.isValidProperty(propertyId, rawValue)) {
      return false;
    }
    ThemeCustom theme = getTheme();
    float originalValue = theme.getProperty(propertyId);
    Float value;
    if (originalValue != rawValue || propertyId == PropertyId.PARENT_THEME) {
      value = rawValue;
    } else {
      value = null;
    }
    theme.setProperty(propertyId, value);
    Settings.instance().setCustomThemeProperty(ThemeManager.resolveCustomThemeId(theme.getId()), propertyId, value);
    if (propertyId == PropertyId.DARK) {
      TdlibManager.instance().checkThemeId(theme.getId(), rawValue == 1f, theme.getParentTheme().getId());
    }
    ThemeManager.instance().notifyPropertyChanged(theme.getId(), propertyId, rawValue, originalValue);
    if (propertyId == PropertyId.DARK) {
      context().forceNightMode(rawValue == 1f);
    }
    if (propertyId == PropertyId.WALLPAPER_ID) {
      tdlib.wallpaper().notifyDefaultWallpaperChanged(theme.getId());
    }
    return true;
  }

  private float getAlpha () {
    return transparentAnimator != null ? 1f - transparentAnimator.getFloatValue() : 1f;
  }

  private void setChildAlpha (View view) {
    if (view == null)
      return;

    float alpha = getAlpha();
    if (view != opaqueView) {
      view.setAlpha(alpha);
      return;
    }
    float opaque = .7f + .3f * alpha;
    if (view instanceof ColorToneView) {
      view.setAlpha(opaque);
    } else {
      view.setAlpha(1f);
    }

    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        view = group.getChildAt(i);
        if (view == opaqueChild) {
          view.setAlpha(opaqueChild instanceof ColorToneView ? opaque : 1f);
        } else if (view != null) {
          view.setAlpha(alpha);
        }
      }
    }
  }

  private BoolAnimator transparentAnimator;
  private View opaqueView, opaqueChild;

  private boolean forcePreview;

  private void setForcePreview (boolean force) {
    if (this.forcePreview != force) {
      this.forcePreview = force;
      if (force) {
        getParentOrSelf().navigationController().forcePreviewPreviouewItem();
      } else {
        getParentOrSelf().navigationController().closePreviousPreviewItem();
      }
      setStackLocked(force);
    }
  }

  private boolean isInTransparentMode () {
    return transparentAnimator != null && transparentAnimator.getValue();
  }

  private CancellableRunnable clearRunnable;

  private void cancelClearTransparentMode () {
    if (clearRunnable != null) {
      clearRunnable.cancel();
      clearRunnable = null;
    }
  }

  private void clearTransparentModeDelayed () {
    cancelClearTransparentMode();
    clearRunnable = new CancellableRunnable() {
      @Override
      public void act () {
        setInTransparentMode(false, null);
      }
    };
    tdlib.ui().postDelayed(clearRunnable, 1500l);
  }

  private void setInTransparentMode (boolean inTransparentMode, View ignoreView) {
    cancelClearTransparentMode();
    ThemeController c = findThemeController();
    if (c == null || !c.isDetached())
      return;
    if (transparentAnimator == null) {
      transparentAnimator = new BoolAnimator(0, (id, factor, fraction, callee) -> {
        for (int i = 0; i < getRecyclerView().getChildCount(); i++) {
          View view = getRecyclerView().getChildAt(i);
          setChildAlpha(view);
        }
        FillingDrawable drawable;
        drawable = Theme.findFillingDrawable(getValue());
        if (drawable != null)
          drawable.setAlphaFactor(1f - factor);
        setForcePreview(factor > 0f);
        /*drawable = Theme.findFillingDrawable(getWrap());
        if (drawable != null) {
          drawable.setAlphaFactor(1f - factor);
        }*/
        getRecyclerView().invalidate();
      }, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
    }
    if (inTransparentMode) {
      opaqueView = findColorParent(ignoreView);
      opaqueChild = ignoreView;
    }
    getRecyclerView().setScrollDisabled(inTransparentMode, false);
    c.getViewPager().setPagingEnabled(!inTransparentMode);
    transparentAnimator.setValue(inTransparentMode, true);
    // getRecyclerView().setLayoutFrozen(inTransparentMode);
  }


}
