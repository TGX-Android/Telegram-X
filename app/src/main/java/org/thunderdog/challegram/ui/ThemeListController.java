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

import org.drinkless.td.libcore.telegram.TdApi;
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
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.theme.ThemeColors;
import org.thunderdog.challegram.theme.ThemeCustom;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.theme.ThemeInfo;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.theme.ThemeProperty;
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
    String arg4 = groupCount > 5 ? matcher.group(5): null;
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
    return isLookupMode ? R.id.theme_color_filling : super.getHeaderColorId();
  }

  @Override
  protected int getHeaderIconColorId () {
    return isLookupMode ? R.id.theme_color_icon : super.getHeaderIconColorId();
  }

  @Override
  protected int getHeaderTextColorId () {
    return isLookupMode ? R.id.theme_color_text : super.getHeaderTextColorId();
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
        switch (getArgumentsStrict().specificSectionId) {
          case 0:
          case R.id.theme_category_bubbles:
          case R.id.theme_category_navigation:
            resId = R.string.xItem;
            break;
          case R.id.theme_category_settings:
            resId = R.string.xProperty;
            break;
          default:
            resId = R.string.xColors;
            break;
        }
        infoView.showInfo(Lang.pluralBold(resId, itemCount));
      }

      @Override
      protected void modifyCustom (SettingHolder holder, int position, ListItem item, int customViewType, View view, boolean isUpdate) {
        super.updateView(holder, position, ListItem.TYPE_VALUED_SETTING_COMPACT);
        int id = getDataId(item);
        switch (customViewType) {
          case VIEW_TYPE_INLINE_OUTLINE:
            /*int backgroundColorId = id == R.id.theme_color_bubbleOut_inlineOutline ? R.id.theme_color_bubbleOut_background : 0;
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
              boolean checked = id == R.id.theme_color_controlActive;
              if (v instanceof CheckBoxView) {
                ((CheckBoxView) v).setChecked(checked, false);
              } else if (v instanceof RadioView) {
                ((RadioView) v).setChecked(checked, false);
              } else if (v instanceof MaterialEditTextGroup) {
                boolean isActive = id != R.id.theme_color_inputInactive;
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
                    case R.id.theme_color_headerButton:
                      size = 52f;
                      iconRes = R.drawable.baseline_create_24;
                      iconColorId = R.id.theme_color_headerButtonIcon;
                      break;
                    case R.id.theme_color_circleButtonRegular:
                      size = 52f;
                      iconRes = R.drawable.baseline_create_24;
                      iconColorId = R.id.theme_color_circleButtonRegularIcon;
                      break;
                    case R.id.theme_color_circleButtonChat:
                      size = 48f;
                      iconRes = R.drawable.baseline_arrow_downward_24;
                      iconColorId = R.id.theme_color_circleButtonChatIcon;
                      break;
                    case R.id.theme_color_circleButtonOverlay:
                      size = 46f;
                      iconRes = R.drawable.baseline_backspace_24;
                      iconColorId = R.id.theme_color_circleButtonOverlayIcon;
                      break;
                    case R.id.theme_color_circleButtonTheme:
                      size = 52f;
                      iconRes = R.drawable.baseline_palette_24;
                      iconColorId = R.id.theme_color_circleButtonThemeIcon;
                      break;
                    case R.id.theme_color_circleButtonNewSecret:
                      size = 40f;
                      iconRes = R.drawable.baseline_lock_24;
                      iconColorId = R.id.theme_color_circleButtonNewSecretIcon;
                      break;
                    case R.id.theme_color_circleButtonNewChat:
                      size = 40f;
                      iconRes = R.drawable.baseline_person_24;
                      iconColorId = R.id.theme_color_circleButtonNewChatIcon;
                      break;
                    case R.id.theme_color_circleButtonNewGroup:
                      iconRes = R.drawable.baseline_group_24;
                      iconColorId = R.id.theme_color_circleButtonNewGroupIcon;
                      size = 40f;
                      break;
                    case R.id.theme_color_circleButtonNewChannel:
                      iconRes = R.drawable.baseline_bullhorn_24;
                      iconColorId = R.id.theme_color_circleButtonNewChannelIcon;
                      size = 40f;
                      break;
                    case R.id.theme_color_circleButtonPositive:
                    case R.id.theme_color_circleButtonNegative:
                      iconColorId = id == R.id.theme_color_circleButtonPositive ? R.id.theme_color_circleButtonPositiveIcon : R.id.theme_color_circleButtonNegativeIcon;
                      iconRes = R.drawable.baseline_phone_24;
                      size = 52f;
                      rotation = id == R.id.theme_color_circleButtonNegative ? 135f : 0f;
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
            ViewSupport.setThemedBackground(ll, 0, ThemeListController.this);

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
            sliderView.setForceBackgroundColorId(R.id.theme_color_sliderInactive);
            sliderView.setColorId(R.id.theme_color_sliderActive, false);
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
            ViewSupport.setThemedBackground(newView, R.id.theme_color_promo, ThemeListController.this).setCornerRadius(3f);
            newView.setId(R.id.btn_new);
            newView.setSingleLine(true);
            newView.setPadding(Screen.dp(4f), Screen.dp(1f), Screen.dp(4f), 0);
            newView.setTextColor(Theme.getColor(R.id.theme_color_promoContent));
            addThemeTextColorListener(newView, R.id.theme_color_promoContent);
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
        switch (id) {
          case R.id.color_alphaPalette: {
            int alpha = (int) (value * 255f);
            int newColor = ColorUtils.color(alpha, state.getColor());
            if (!changeColor(item, contentView, view, state, newColor, !isFinished, false)) {
              ignoreTextEvents = true;
              setColor(item, -1, contentView, view);
              ignoreTextEvents = false;
            }
            break;
          }
          case R.id.color_huePalette:
            float degrees = 360f * value;
            if (!changeHsv(item, contentView, view, state, 0, degrees, !isFinished, false)) {
              ignoreTextEvents = true;
              setColor(item, -1, contentView, view);
              ignoreTextEvents = false;
            }
            break;
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
          switch (id) {
            case R.id.color_hex: { // Hex
              newColor = ColorUtils.parseHexColor(v, false);
              success = true;
              break;
            }
            case R.id.color_red: // RGBA 0..255
            case R.id.color_green:
            case R.id.color_blue:
            case R.id.color_alpha: {
              int value = v.isEmpty() ? 0 : Integer.parseInt(v);
              if (value >= 0 && value <= 255) {
                int alpha = id != R.id.color_alpha ? Color.alpha(currentColor) : value;
                int red = id != R.id.color_red ? Color.red(currentColor) : value;
                int green = id != R.id.color_green ? Color.green(currentColor) : value;
                int blue = id != R.id.color_blue ? Color.blue(currentColor) : value;
                newColor = Color.argb(alpha, red, green, blue);
                success = true;
              }
              break;
            }
            case R.id.color_hue:
            case R.id.color_saturation:
            case R.id.color_lightness:
            case R.id.color_alphaPercentage:
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
              break;

            default:
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
        switch (item.getId()) {
          case R.id.btn_property: {
            final int propertyId = getDataId(item);
            final float value = theme.getProperty(propertyId);
            boolean set = false;
            if (propertyId == ThemeProperty.PARENT_THEME) {
              try {
                view.setData(ThemeManager.getBuiltinThemeName((int) value));
                set = true;
              } catch (Throwable ignored) { }
            }
            if (!set) {
              view.setData(U.formatFloat(value, true));
            }
            if (ThemeManager.isBoolProperty(propertyId)) {
              view.getToggler().setUseNegativeState(false);
              view.getToggler().setRadioEnabled(value == 1f, isUpdate);
            }
            break;
          }
          case R.id.btn_color: {
            final ItemModifier modifier = (ItemModifier) item.getDrawModifier();
            final int colorId = getDataId(item);
            final int color = theme.getColor(colorId);
            String colorName = view.getName().toString();

            final boolean needStaticFilling = colorId == R.id.theme_color_fillingPressed;
            boolean hasColor = item.getViewType() >= 0;
            boolean colorVisible = true;

            switch (colorId) {
              case R.id.theme_color_togglerActive:
                view.getToggler().setUseNegativeState(false).setRadioEnabled(true, false);
                hasColor = false;
                break;
              case R.id.theme_color_togglerInactive:
                view.getToggler().setUseNegativeState(false).setRadioEnabled(false, false);
                hasColor = false;
                break;
              case R.id.theme_color_togglerPositive:
                view.getToggler().setUseNegativeState(true).setRadioEnabled(true, false);
                hasColor = false;
                break;
              case R.id.theme_color_togglerNegative:
                view.getToggler().setUseNegativeState(true).setRadioEnabled(false, false);
                hasColor = false;
                break;
              case R.id.theme_color_togglerActiveBackground:
              case R.id.theme_color_togglerInactiveBackground:
              case R.id.theme_color_togglerPositiveBackground:
              case R.id.theme_color_togglerPositiveContent:
              case R.id.theme_color_togglerNegativeBackground:
              case R.id.theme_color_togglerNegativeContent:
                colorVisible = false;
                break;
              case R.id.theme_color_filling:
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
              case R.id.theme_color_background:
              case R.id.theme_color_background_text:
              case R.id.theme_color_background_textLight:
              case R.id.theme_color_background_icon:
                dataColorId = R.id.theme_color_background_textLight;
                break;
              case R.id.theme_color_caption_textLink:
              case R.id.theme_color_caption_textLinkPressHighlight:
                dataColorId = R.id.theme_color_caption_textLink;
                break;
              /*case R.id.theme_color_headerRemoveBackground:
                dataColorId = R.id.theme_color_headerText;
                dataIsSubtitle = true;
                break;*/
              /*case R.id.theme_color_headerBackground:
              case R.id.theme_color_headerText:
              case R.id.theme_color_headerIcon:
                dataColorId = R.id.theme_color_headerText;
                dataIsSubtitle = true;
                break;
              case R.id.theme_color_headerLightBackground:
              case R.id.theme_color_headerLightText:
              case R.id.theme_color_headerLightIcon:
                dataColorId = R.id.theme_color_headerLightText;
                dataIsSubtitle = true;
                break;*/
              default:
                if (colorName.startsWith("iv_")) {
                  dataColorId = R.id.theme_color_iv_caption;
                } else {
                  dataColorId = R.id.theme_color_textLight;
                }
                break;
            }
            view.setDataColorId(dataColorId, dataIsSubtitle);
            break;
          }
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
    this.itemCount = buildCells(items, args.theme.getId(), currentQuery);

    adapter.setItems(items, false);

    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.theme_color_filling));

    recyclerView.setItemAnimator(isLookupMode ? null : new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 120l));
    recyclerView.setAdapter(adapter);

    RemoveHelper.attach(recyclerView, new RemoveHelper.Callback() {
      @Override
      public boolean canRemove (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, int position) {
        ListItem item = (ListItem) viewHolder.itemView.getTag();
        return item.getId() == R.id.btn_color && getDataId(item) == R.id.theme_color_fillingNegative;
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

  private static int getPickerBackgroundColorId (@ThemeColorId int colorId, @NonNull String colorName) {
    int backgroundColorId = getBackgroundColorId(colorId, colorName);
    switch (backgroundColorId) {
      case R.id.theme_color_black:
        return R.id.theme_color_filling;
    }
    return backgroundColorId;
  }

  private static int getBackgroundColorId (@ThemeColorId int colorId, @NonNull String colorName) {
    switch (colorId) {
      case R.id.theme_color_background:
      case R.id.theme_color_background_text:
      case R.id.theme_color_background_textLight:
      case R.id.theme_color_background_icon:
        return R.id.theme_color_background;
      /*case R.id.theme_color_headerRemoveBackground:
        return R.id.theme_color_headerBackground;
      case R.id.theme_color_headerBackground:
      case R.id.theme_color_headerText:
      case R.id.theme_color_headerIcon:
        return R.id.theme_color_headerBackground;
      case R.id.theme_color_headerLightBackground:
      case R.id.theme_color_headerLightText:
      case R.id.theme_color_headerLightIcon:
        return R.id.theme_color_headerLightBackground;*/
      case R.id.theme_color_iv_preBlockBackground:
      case R.id.theme_color_iv_textCodeBackground:
        return colorId;
      case R.id.theme_color_caption_textLink:
      case R.id.theme_color_caption_textLinkPressHighlight:
        return R.id.theme_color_black;
      /*case R.id.theme_color_snackbarUpdate:
      case R.id.theme_color_snackbarUpdateAction:
      case R.id.theme_color_snackbarUpdateText:
        return R.id.theme_color_snackbarUpdate;*/
    }
    return R.id.theme_color_filling;
  }

  private static class ItemModifier implements DrawModifier {
    protected final ThemeCustom theme;
    protected final int colorId;

    private boolean hasHistory;
    private DrawModifier otherModifier;

    private int backgroundColorId;
    private int circleColorId, circleIconColorId = R.id.theme_color_white;
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
      this.counter.setCount(count, colorId == R.id.theme_color_badgeMuted, false);
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
        c.drawCircle(cx, cy, Screen.dp(3f), Paints.fillingPaint(theme.getColor(isOverridden ? R.id.theme_color_iconActive : R.id.theme_color_iconLight)));
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
          paint.setColor(Theme.getColor(R.id.theme_color_avatar_content));
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
      case R.id.theme_color_togglerActive:
      case R.id.theme_color_togglerInactive:
      case R.id.theme_color_togglerPositive:
      case R.id.theme_color_togglerNegative:
        viewType = ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER;
        break;

      case R.id.theme_color_background_text:
      case R.id.theme_color_background_textLight:
      case R.id.theme_color_caption_textLink:
        span = new CustomTypefaceSpan(null, id);
        break;
      /*case R.id.theme_color_headerBackground:
      case R.id.theme_color_headerText:
      case R.id.theme_color_headerIcon:
        span = new CustomTypefaceSpan(null, R.id.theme_color_headerText);
        break;
      case R.id.theme_color_headerLightBackground:
      case R.id.theme_color_headerLightText:
      case R.id.theme_color_headerLightIcon:
        span = new CustomTypefaceSpan(null, R.id.theme_color_headerLightText);
        break;*/
      case R.id.theme_color_background:
      case R.id.theme_color_background_icon:
        span = new CustomTypefaceSpan(null, R.id.theme_color_background_text);
        if (id == R.id.theme_color_background_icon) {
          modifier.setIcons(R.drawable.baseline_devices_other_24);
        } else {
          modifier.noColorPreview = true;
        }
        break;
      case R.id.theme_color_icon:
        modifier.setIcons(R.drawable.baseline_settings_24, R.drawable.baseline_alternate_email_24, R.drawable.deproko_baseline_pin_24);
        break;
      case R.id.theme_color_iconLight:
        modifier.setIcons(R.drawable.deproko_baseline_clock_24, R.drawable.baseline_visibility_14, R.drawable.baseline_edit_12);
        break;
      case R.id.theme_color_playerButton:
        modifier.setIcons(R.drawable.baseline_skip_next_24_white, R.drawable.baseline_pause_24, R.drawable.baseline_skip_previous_24_white);
        break;
      case R.id.theme_color_playerButtonActive:
        modifier.setIcons(R.drawable.round_repeat_24, R.drawable.round_shuffle_24, R.drawable.round_repeat_one_24);
        break;
      case R.id.theme_color_iconActive:
        modifier.setIcons(R.drawable.deproko_baseline_mosaic_group_24, R.drawable.baseline_emoticon_outline_24, R.drawable.baseline_restaurant_menu_24);
        break;
      case R.id.theme_color_iconPositive:
        modifier.setIcons(R.drawable.baseline_call_made_18, R.drawable.baseline_call_received_18);
        break;
      case R.id.theme_color_iconNegative:
        modifier.setIcons(R.drawable.baseline_call_made_18, R.drawable.baseline_call_received_18, R.drawable.baseline_call_missed_18);
        break;
      case R.id.theme_color_ticks:
        modifier.setIcons(R.drawable.deproko_baseline_check_single_24);
        break;
      case R.id.theme_color_ticksRead:
        modifier.setIcons(R.drawable.deproko_baseline_check_double_24);
        break;
      case R.id.theme_color_bubbleOut_ticks:
        modifier.setIcons(R.drawable.deproko_baseline_check_single_24);
        modifier.iconBackgroundColorId = R.id.theme_color_bubbleOut_background;
        break;
      case R.id.theme_color_bubbleOut_ticksRead:
        modifier.setIcons(R.drawable.deproko_baseline_check_double_24);
        modifier.iconBackgroundColorId = R.id.theme_color_bubbleOut_background;
        break;
      case R.id.theme_color_chatListVerify:
        modifier.setIcons(R.drawable.deproko_baseline_verify_chat_24);
        break;
      case R.id.theme_color_chatSendButton:
        modifier.setIcons(R.drawable.deproko_baseline_send_24);
        break;
      case R.id.theme_color_chatListMute:
        modifier.setIcons(R.drawable.deproko_baseline_notifications_off_24);
        break;
      case R.id.theme_color_chatListIcon:
        modifier.setIcons(R.drawable.baseline_camera_alt_16, R.drawable.baseline_videocam_16, R.drawable.baseline_collections_16, R.drawable.ivanliana_baseline_video_collections_16, R.drawable.ivanliana_baseline_audio_collections_16, R.drawable.ivanliana_baseline_file_collections_16);
        break;
      case R.id.theme_color_badge:
      case R.id.theme_color_badgeMuted:
      case R.id.theme_color_badgeFailed:
        modifier.setCounter(id == R.id.theme_color_badgeFailed ? Tdlib.CHAT_FAILED: 1);
        modifier.noColorPreview = true;
        break;
      case R.id.theme_color_textSelectionHighlight:
        span = new CustomTypefaceSpan(null, R.id.theme_color_text).setBackgroundColorId(id);
        break;
      case R.id.theme_color_textLinkPressHighlight:
        span = new CustomTypefaceSpan(null, R.id.theme_color_textLink).setBackgroundColorId(id);
        break;
      case R.id.theme_color_iv_textMarkedBackground:
        span = new CustomTypefaceSpan(null, R.id.theme_color_iv_textMarked).setBackgroundColorId(id);
        break;
      case R.id.theme_color_iv_textMarkedLinkPressHighlight:
        span = new CustomTypefaceSpan(null, R.id.theme_color_iv_textMarkedLink).setBackgroundColorId(id);
        break;
      case R.id.theme_color_iv_textLinkPressHighlight:
        span = new CustomTypefaceSpan(null, R.id.theme_color_iv_textLink).setBackgroundColorId(id);
        break;
      case R.id.theme_color_textSearchQueryHighlight:
        span = new CustomTypefaceSpan(null, id);
        spanEnd = name.length() / 2; // name.indexOf("Prefix") + "Prefix".length();
        break;
      case R.id.theme_color_caption_textLinkPressHighlight:
        span = new CustomTypefaceSpan(null, R.id.theme_color_caption_textLink).setBackgroundColorId(id);
        break;
      case R.id.theme_color_snackbarUpdate:
      case R.id.theme_color_snackbarUpdateAction:
        span = new CustomTypefaceSpan(null, R.id.theme_color_snackbarUpdateAction).setBackgroundColorId(R.id.theme_color_snackbarUpdate);
        break;
      case R.id.theme_color_snackbarUpdateText:
        span = new CustomTypefaceSpan(null, id).setBackgroundColorId(R.id.theme_color_snackbarUpdate);
        break;

      case R.id.theme_color_iv_pageTitle:
      case R.id.theme_color_iv_pageSubtitle:
        span = new CustomTypefaceSpan(null, id).setTextSizeDp(18f);
        break;
      case R.id.theme_color_iv_pullQuote:
        span = new CustomTypefaceSpan(null, id).setFakeBold(true);
        break;

      case R.id.theme_color_iv_blockQuoteLine:
      case R.id.theme_color_messageVerticalLine:
      case R.id.theme_color_bubbleOut_chatVerticalLine:
        modifier.setOtherModifier(new LineDrawModifier(id, theme));
        break;
      case R.id.theme_color_iv_preBlockBackground:
      case R.id.theme_color_iv_textCodeBackground:
      case R.id.theme_color_iv_separator:
      case R.id.theme_color_ivHeaderIcon:
      case R.id.theme_color_iv_header:
        // Do nothing
        break;
      case R.id.theme_color_checkActive:
        modifier.noColorPreview = true;
        modifier.setOtherModifier(new DrawModifier() {
          @Override
          public void afterDraw (View view, Canvas c) {
            SimplestCheckBox.draw(c, view.getMeasuredWidth() - Screen.dp(32f), view.getMeasuredHeight() / 2, 1f, null);
          }
        });
        break;
      case R.id.theme_color_online:
        modifier.setCircle(R.id.theme_color_avatarSavedMessages, StringUtils.random(name, 2).toUpperCase());
        modifier.needOnline = true;
        modifier.noColorPreview = true;
        break;
      case R.id.theme_color_inlineOutline:
        viewType = ListItem.TYPE_CUSTOM - VIEW_TYPE_INLINE_OUTLINE;
        break;
      case R.id.theme_color_progress:
        viewType = ListItem.TYPE_CUSTOM - VIEW_TYPE_PROGRESS;
        break;
      case R.id.theme_color_controlActive:
      case R.id.theme_color_controlInactive:
        viewType = ListItem.TYPE_CUSTOM - VIEW_TYPE_CONTROLS;
        break;
      case R.id.theme_color_promo:
        viewType = ListItem.TYPE_CUSTOM - VIEW_TYPE_NEW;
        break;
      case R.id.theme_color_inputActive:
      case R.id.theme_color_inputInactive:
      case R.id.theme_color_inputPositive:
      case R.id.theme_color_inputNegative:
        viewType = ListItem.TYPE_CUSTOM - VIEW_TYPE_INPUT;
        break;
      case R.id.theme_color_sliderActive:
        viewType = ListItem.TYPE_CUSTOM - VIEW_TYPE_SLIDER;
        break;
      case R.id.theme_color_playerCoverIcon:
        modifier.setIcons(R.drawable.baseline_music_note_24);
        modifier.iconBackgroundColorId = R.id.theme_color_playerCoverPlaceholder;
        break;
      case R.id.theme_color_headerButton:
      case R.id.theme_color_circleButtonRegular:
      case R.id.theme_color_circleButtonOverlay:
      case R.id.theme_color_circleButtonChat:
      case R.id.theme_color_circleButtonTheme:
      case R.id.theme_color_circleButtonNewSecret:
      case R.id.theme_color_circleButtonNewChat:
      case R.id.theme_color_circleButtonNewGroup:
      case R.id.theme_color_circleButtonNewChannel:
      case R.id.theme_color_circleButtonPositive:
      case R.id.theme_color_circleButtonNegative:
        viewType = ListItem.TYPE_CUSTOM - VIEW_TYPE_CIRCLE;
        break;

      case R.id.theme_color_placeholder:
        modifier.setCircle(id, 0);
        break;
      case R.id.theme_color_seekDone:
        modifier.setOtherModifier(new DrawModifier() {
          @Override
          public void afterDraw (View view, Canvas c) {
            int width = Screen.dp(122f);
            int cx = view.getMeasuredWidth() - Screen.dp(12f) - width;
            int cy = view.getMeasuredHeight() / 2;
            final int seekStroke = Screen.dp(2f);
            c.drawLine(cx, cy, cx + width, cy, Paints.getProgressPaint(theme.getColor(R.id.theme_color_seekEmpty), seekStroke));
            c.drawLine(cx, cy, cx + width / 3 * 2, cy, Paints.getProgressPaint(theme.getColor(R.id.theme_color_seekReady), seekStroke));
            c.drawLine(cx, cy, cx + width / 3, cy, Paints.getProgressPaint(theme.getColor(R.id.theme_color_seekDone), seekStroke));
            c.drawCircle(cx + width / 3, cy, Screen.dp(6f), Paints.fillingPaint(theme.getColor(R.id.theme_color_seekDone)));
          }
        });
        modifier.noColorPreview = true;
        break;
      case R.id.theme_color_introSectionActive:
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
               c.drawCircle(cx, cy, radius, Paints.fillingPaint(Theme.getColor(i == count - 1 ? R.id.theme_color_introSectionActive : R.id.theme_color_introSection)));
               cx -= radius + spacing;
            }
          }
        });
        break;
      case R.id.theme_color_headerRemoveBackground:
        // span = new CustomTypefaceSpan(null, R.id.theme_color_headerText);
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
            c.drawRect(cx - (endX - cx - width), 0, endX, view.getMeasuredHeight(), Paints.fillingPaint(theme.getColor(R.id.theme_color_headerBackground)));
            rectF.set(cx, cy, cx + width, cy + avatarRadius + avatarRadius);
            c.drawRoundRect(rectF, avatarRadius, avatarRadius, Paints.fillingPaint(theme.getColor(R.id.theme_color_headerRemoveBackground)));
            c.drawCircle(cx + avatarRadius, cy + avatarRadius, avatarRadius, Paints.fillingPaint(theme.getColor(R.id.theme_color_headerRemoveBackgroundHighlight)));
            Drawables.draw(c, icon, cx + avatarRadius - icon.getMinimumWidth() / 2, cy + avatarRadius - icon.getMinimumHeight() / 2, Paints.getPorterDuffPaint(0xffffffff));
            c.drawText(text, cx + avatarRadius * 2 + padding, cy + avatarRadius + Screen.dp(5f), paint);
          }
        });
        break;
      case R.id.theme_color_waveformActive:
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
      case R.id.theme_color_bubbleOut_waveformActive:
        modifier.noColorPreview = true;
        modifier.setOtherModifier(new DrawModifier() {
          private Waveform waveform;
          @Override
          public void afterDraw (View view, Canvas c) {
            int width = Screen.dp(122f);
            c.drawRect(view.getMeasuredWidth() - width - Screen.dp(12f) * 2, 0, view.getMeasuredWidth(), view.getMeasuredHeight(), Paints.fillingPaint(theme.getColor(R.id.theme_color_bubbleOut_background)));
            if (waveform == null) {
              waveform = new Waveform(TD.newRandomWaveform(), Waveform.MODE_RECT, true);
              waveform.layout(width);
            }
            waveform.draw(c, .5f, view.getMeasuredWidth() - Screen.dp(12f) - width, view.getMeasuredHeight() / 2);
          }
        });
        break;
      case R.id.theme_color_headerRemoveBackgroundHighlight:
      case R.id.theme_color_checkContent:
      case R.id.theme_color_textPlaceholder:
      case R.id.theme_color_promoContent:
      case R.id.theme_color_controlContent:
      case R.id.theme_color_inlineText:
      case R.id.theme_color_inlineIcon:
      case R.id.theme_color_inlineContentActive:
      case R.id.theme_color_badgeText:
      case R.id.theme_color_badgeMutedText:
      case R.id.theme_color_badgeFailedText:
      case R.id.theme_color_sliderInactive:
      case R.id.theme_color_playerCoverPlaceholder:
      case R.id.theme_color_seekEmpty:
      case R.id.theme_color_seekReady:
      case R.id.theme_color_introSection:
      case R.id.theme_color_headerButtonIcon:
      case R.id.theme_color_waveformInactive:
      case R.id.theme_color_bubbleOut_waveformInactive:
      case R.id.theme_color_filling:
      case R.id.theme_color_circleButtonRegularIcon:
      case R.id.theme_color_circleButtonChatIcon:
      case R.id.theme_color_circleButtonOverlayIcon:
      case R.id.theme_color_circleButtonThemeIcon:
      case R.id.theme_color_circleButtonNewSecretIcon:
      case R.id.theme_color_circleButtonNewChatIcon:
      case R.id.theme_color_circleButtonNewGroupIcon:
      case R.id.theme_color_circleButtonNewChannelIcon:
      case R.id.theme_color_circleButtonPositiveIcon:
      case R.id.theme_color_circleButtonNegativeIcon:
        modifier.noColorPreview = true;
        break;
      case R.id.theme_color_fileAttach:
        modifier.setCircle(id, R.drawable.baseline_location_on_24);
        break;
      case R.id.theme_color_attachContact:
        modifier.setCircle(id, R.drawable.baseline_person_24);
        modifier.circleIconColorId = R.id.theme_color_attachText;
        break;
      case R.id.theme_color_attachFile:
        modifier.setCircle(id, R.drawable.baseline_insert_drive_file_24);
        modifier.circleIconColorId = R.id.theme_color_attachText;
        break;
      case R.id.theme_color_attachInlineBot:
        modifier.setCircle(id, R.drawable.deproko_baseline_bots_24);
        modifier.circleIconColorId = R.id.theme_color_attachText;
        break;
      case R.id.theme_color_attachPhoto:
        modifier.setCircle(id, R.drawable.baseline_image_24);
        modifier.circleIconColorId = R.id.theme_color_attachText;
        break;
      case R.id.theme_color_attachLocation:
        modifier.setCircle(id, R.drawable.baseline_location_on_24);
        modifier.circleIconColorId = R.id.theme_color_attachText;
        break;
      case R.id.theme_color_messageAuthor:
      case R.id.theme_color_messageAuthorPsa:
        span = new CustomTypefaceSpan(null, id).setFakeBold(true);
        break;
      case R.id.theme_color_bubbleOut_messageAuthor:
      case R.id.theme_color_bubbleOut_messageAuthorPsa:
        span = new CustomTypefaceSpan(null, id).setFakeBold(true).setBackgroundColorId(R.id.theme_color_bubbleOut_background);
        break;
      case R.id.theme_color_bubbleOut_file:
        modifier.setCircle(id, R.drawable.baseline_insert_drive_file_24);
        // modifier.backgroundColorId = R.id.theme_color_bubbleOut_background;
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
            case R.id.theme_color_avatar_content: {
              modifier.setCircle(0, 0);
              break;
            }
            case R.id.theme_color_avatarArchive:
            case R.id.theme_color_avatarArchivePinned:
            case R.id.theme_color_avatarReplies:
            case R.id.theme_color_avatarReplies_big:
            case R.id.theme_color_avatarSavedMessages:
            case R.id.theme_color_avatarSavedMessages_big: {
              int circleIcon;

              switch (id) {
                case R.id.theme_color_avatarArchive:
                case R.id.theme_color_avatarArchivePinned:
                  circleIcon = R.drawable.baseline_archive_24;
                  break;
                case R.id.theme_color_avatarReplies:
                case R.id.theme_color_avatarReplies_big:
                  circleIcon = R.drawable.baseline_reply_24;
                  break;
                default:
                  circleIcon = R.drawable.baseline_bookmark_24;
                  break;
              }

              modifier.setCircle(id, circleIcon);
              modifier.circleIconColorId = R.id.theme_color_avatar_content;
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
            case R.id.theme_color_file:
              modifier.setCircle(id, null);
              modifier.setPlayPausePath();
              break;
            case R.id.theme_color_fileYellow:
              modifier.setCircle(id, R.drawable.baseline_file_download_24);
              break;
            case R.id.theme_color_fileGreen:
              modifier.setCircle(id, R.drawable.deproko_baseline_close_24);
              break;
            case R.id.theme_color_fileRed:
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
        switch (entity.type.getConstructor()) {
          case TdApi.TextEntityTypeMention.CONSTRUCTOR: {
            String username = text.subSequence(entity.offset + 1, entity.offset + entity.length).toString();
            spans.add(new ClickableSpan() {
              @Override
              public void onClick (@NonNull View widget) {
                tdlib.ui().switchInline(ThemeListController.this, username, "", true);
              }
            });
            spans.add(new CustomTypefaceSpan(null, R.id.theme_color_textLink).setEntityType(entity.type).setRemoveUnderline(true));
            break;
          }
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

  private static boolean needSeparate (int prevColorId, int colorId) {
    return prevColorId != R.id.theme_color_caption_textLink || colorId != R.id.theme_color_caption_textLinkPressHighlight;
  }

  private int addGroup (List<ListItem> items, @IdRes int sectionId, @StringRes int sectionName, @StringRes int sectionDesc, int[] ids, boolean areProperties, @Nullable String searchQuery, boolean needSeparators, @Nullable List<Integer> sortedIds) {
    if (sortedIds != null) {
      ArrayUtils.ensureCapacity(sortedIds, sortedIds.size() + ids.length);
    }
    int addedColorCount = 0;
    boolean first = true;
    int prevColorId = 0;
    for (final int id : ids) {
      if (sortedIds != null)
        sortedIds.add(id);
      if (canDisplay(sectionId, id, areProperties) && matches(id, areProperties, searchQuery)) {
        addedColorCount++;
        int descriptionRes = LangUtils.getThemeDescription(id);
        if (first) {
          if (sectionName != 0 && !items.isEmpty()) {
            items.add(new ListItem(items.isEmpty() ? ListItem.TYPE_HEADER_PADDED : ListItem.TYPE_HEADER, 0, 0, sectionName));
            sectionName = 0;
          }
          if (needSeparators && !items.isEmpty())
            items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
          first = false;
        } else if (needSeparators && (prevColorId == 0 || needSeparate(prevColorId, id))) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        }
        items.add(newItem(getArgumentsStrict().theme.getTheme(), id, areProperties));
        if (descriptionRes != 0) {
          CharSequence text = makeDescription(descriptionRes);
          items.add(new ListItem(ListItem.TYPE_DESCRIPTION_SMALL, 0, 0, text, false).setTextColorId(R.id.theme_color_textLight));
        }
        prevColorId = id;
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
      R.id.theme_color_filling,
      R.id.theme_color_separator,
      R.id.theme_color_fillingPressed,
      R.id.theme_color_placeholder,
      R.id.theme_color_previewBackground,
      R.id.theme_color_overlayFilling,
      R.id.theme_color_fillingNegative,
      R.id.theme_color_fillingPositive,
      R.id.theme_color_fillingPositiveContent,
    };
    totalCount += addGroup(items, R.id.theme_category_content,0, 0, mainColorIds, false, searchQuery, true, sortedIds);
    
    // TEXT

    int[] textColorIds = new int[] {
      R.id.theme_color_text,
      R.id.theme_color_textSelectionHighlight,
      R.id.theme_color_textLight,
      R.id.theme_color_textSecure,
      R.id.theme_color_textLink,
      R.id.theme_color_textLinkPressHighlight,
      R.id.theme_color_textNeutral,
      R.id.theme_color_textNegative,
      R.id.theme_color_textSearchQueryHighlight,
    };
    totalCount += addGroup(items, R.id.theme_category_content, R.string.ThemeSectionText, 0, textColorIds, false, searchQuery, true, sortedIds);

    // BACKGROUND
    
    int[] backgroundTextColorIds = new int[] {
      R.id.theme_color_background,
      R.id.theme_color_background_text,
      R.id.theme_color_background_textLight,
      R.id.theme_color_background_icon,
    };
    totalCount += addGroup(items, R.id.theme_category_content, R.string.ThemeSectionBackground, 0, backgroundTextColorIds, false, searchQuery, false, sortedIds);

    // ICONS

    int[] iconColorIds = new int[] {
      R.id.theme_color_icon,
      R.id.theme_color_iconLight,
      R.id.theme_color_iconActive,
      R.id.theme_color_iconPositive,
      R.id.theme_color_iconNegative,
    };
    totalCount += addGroup(items, R.id.theme_category_content, R.string.ThemeSectionIcons, 0, iconColorIds, false, searchQuery, true, sortedIds);

    // NAVIGATION

    int[] headerColorIds = new int[] {
      R.id.theme_color_headerBackground,
      R.id.theme_color_headerText,
      R.id.theme_color_headerIcon,
    };
    int[] headerLightColorIds = new int[] {
      R.id.theme_color_headerLightBackground,
      R.id.theme_color_headerLightText,
      R.id.theme_color_headerLightIcon,
    };
    int[] headerPickerColorIds = new int[] {
      R.id.theme_color_headerPickerBackground,
      R.id.theme_color_headerPickerText,
    };
    int[] headerButtonColorIds = new int[] {
      R.id.theme_color_headerButton,
      R.id.theme_color_headerButtonIcon,
    };
    int[] headerRemoveColorIds = new int[] {
      R.id.theme_color_headerRemoveBackground,
      R.id.theme_color_headerRemoveBackgroundHighlight,
    };
    int[] headerBarColorIds = new int[] {
      R.id.theme_color_headerBarCallIncoming,
      R.id.theme_color_headerBarCallActive,
      R.id.theme_color_headerBarCallMuted,
    };
    int[] headerOtherColorIds = new int[] {
      R.id.theme_color_headerPlaceholder,
      R.id.theme_color_statusBarLegacy,
      R.id.theme_color_statusBarLegacyContent,
      R.id.theme_color_statusBar,
      R.id.theme_color_statusBarContent,
    };
    int[] lightStatusBarId = new int[] {
      ThemeProperty.LIGHT_STATUS_BAR
    };
    int[] headerTabColorIds = new int[] {
      R.id.theme_color_headerTabActive,
      R.id.theme_color_headerTabActiveText,
      R.id.theme_color_headerTabInactiveText,
    };
    int[] profileColorIds = new int[] {
      R.id.theme_color_profileSectionActive,
      R.id.theme_color_profileSectionActiveContent,
    };
    int[] drawerColorIds = new int[] {
      R.id.theme_color_drawer,
      R.id.theme_color_drawerText,
    };
    int[] passcodeColorIds = new int[] {
      R.id.theme_color_passcode,
      R.id.theme_color_passcodeIcon,
      R.id.theme_color_passcodeText,
    };
    int[] notificationColorIds = new int[] {
      R.id.theme_color_notification,
      R.id.theme_color_notificationPlayer,
      R.id.theme_color_notificationSecure,
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
      R.id.theme_color_progress,
    };
    int[] controlColorIds = new int[] {
      R.id.theme_color_controlInactive,
      R.id.theme_color_controlActive,
      R.id.theme_color_controlContent,
    };
    int[] checkColorIds = new int[] {
      R.id.theme_color_checkActive,
      R.id.theme_color_checkContent,
    };
    int[] sliderColorIds = new int[] {
      R.id.theme_color_sliderActive,
      R.id.theme_color_sliderInactive,
    };
    int[] togglerActiveColorIds = new int[] {
      R.id.theme_color_togglerActive,
      R.id.theme_color_togglerActiveBackground,
    };
    int[] togglerInactiveColorIds = new int[] {
      R.id.theme_color_togglerInactive,
      R.id.theme_color_togglerInactiveBackground,
    };
    int[] togglerPositiveColorIds = new int[] {
      R.id.theme_color_togglerPositive,
      R.id.theme_color_togglerPositiveBackground,
      R.id.theme_color_togglerPositiveContent,
    };
    int[] togglerNegativeColorIds = new int[] {
      R.id.theme_color_togglerNegative,
      R.id.theme_color_togglerNegativeBackground,
      R.id.theme_color_togglerNegativeContent,
    };
    int[] inputColorIds = new int[] {
      R.id.theme_color_inputInactive,
      R.id.theme_color_inputActive,
      R.id.theme_color_inputPositive,
      R.id.theme_color_inputNegative,
      R.id.theme_color_textPlaceholder,
    };
    int[] inlineColorIds = new int[] {
      R.id.theme_color_inlineOutline,
      R.id.theme_color_inlineText,
      R.id.theme_color_inlineIcon,
      R.id.theme_color_inlineContentActive,
    };
    int[] circleColorIds = new int[] {
      R.id.theme_color_circleButtonRegular,
      R.id.theme_color_circleButtonRegularIcon,
      R.id.theme_color_circleButtonNewChat,
      R.id.theme_color_circleButtonNewChatIcon,
      R.id.theme_color_circleButtonNewGroup,
      R.id.theme_color_circleButtonNewGroupIcon,
      R.id.theme_color_circleButtonNewChannel,
      R.id.theme_color_circleButtonNewChannelIcon,
      R.id.theme_color_circleButtonNewSecret,
      R.id.theme_color_circleButtonNewSecretIcon,
      R.id.theme_color_circleButtonPositive,
      R.id.theme_color_circleButtonPositiveIcon,
      R.id.theme_color_circleButtonNegative,
      R.id.theme_color_circleButtonNegativeIcon,
      R.id.theme_color_circleButtonOverlay,
      R.id.theme_color_circleButtonOverlayIcon,
      R.id.theme_color_circleButtonChat,
      R.id.theme_color_circleButtonChatIcon,
      R.id.theme_color_circleButtonTheme,
      R.id.theme_color_circleButtonThemeIcon,
    };
    int[] statusColorIds = new int[] {
      R.id.theme_color_online,
      R.id.theme_color_promo,
      R.id.theme_color_promoContent,
    };
    int[] introColorIds = new int[] {
      R.id.theme_color_introSectionActive,
      R.id.theme_color_introSection,
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
      R.id.theme_color_seekDone,
      R.id.theme_color_seekReady,
      R.id.theme_color_seekEmpty,
      R.id.theme_color_playerButtonActive,
      R.id.theme_color_playerButton,
      R.id.theme_color_playerCoverIcon,
      R.id.theme_color_playerCoverPlaceholder,
    };

    totalCount += addGroup(items, R.id.theme_category_controls, R.string.ThemeSectionPlayer, 0, playerColorIds, false, searchQuery, true, sortedIds);

    // PROPERTIES

    int[] propertyIds = new int[] {
      ThemeProperty.PARENT_THEME,
      ThemeProperty.BUBBLE_CORNER,
      ThemeProperty.BUBBLE_CORNER_MERGED,
      ThemeProperty.BUBBLE_CORNER_LEGACY,
      // TODO ThemeProperty.BUBBLE_OUTER_MARGIN,
      ThemeProperty.BUBBLE_OUTLINE,
      ThemeProperty.BUBBLE_OUTLINE_SIZE,
      ThemeProperty.BUBBLE_DATE_CORNER,
      ThemeProperty.BUBBLE_UNREAD_SHADOW,
      ThemeProperty.AVATAR_RADIUS,
      ThemeProperty.AVATAR_RADIUS_FORUM,
      ThemeProperty.AVATAR_RADIUS_CHAT_LIST,
      ThemeProperty.AVATAR_RADIUS_CHAT_LIST_FORUM,
      ThemeProperty.LIGHT_STATUS_BAR,
      ThemeProperty.IMAGE_CORNER,
      ThemeProperty.DATE_CORNER,
      ThemeProperty.REPLACE_SHADOWS_WITH_SEPARATORS,
      ThemeProperty.SHADOW_DEPTH,
      ThemeProperty.SUBTITLE_ALPHA,
      ThemeProperty.WALLPAPER_USAGE_ID,
      ThemeProperty.WALLPAPER_ID,
      ThemeProperty.DARK,
      ThemeProperty.WALLPAPER_OVERRIDE_UNREAD,
      ThemeProperty.WALLPAPER_OVERRIDE_DATE,
      ThemeProperty.WALLPAPER_OVERRIDE_BUTTON,
      ThemeProperty.WALLPAPER_OVERRIDE_MEDIA_REPLY,
      ThemeProperty.WALLPAPER_OVERRIDE_TIME,
      ThemeProperty.WALLPAPER_OVERRIDE_OVERLAY,
    };
    totalCount += addGroup(items, R.id.theme_category_settings, R.string.ThemeAdvanced, 0, propertyIds, true, searchQuery, true, sortedIds);

    // CHAT

    int[] chatListColorIds = new int[] {
      R.id.theme_color_chatListAction,
      R.id.theme_color_chatListMute,
      R.id.theme_color_chatListIcon,
      R.id.theme_color_chatListVerify,

      R.id.theme_color_ticks,
      R.id.theme_color_ticksRead,

      R.id.theme_color_badge,
      R.id.theme_color_badgeText,
      R.id.theme_color_badgeFailed,
      R.id.theme_color_badgeFailedText,
      R.id.theme_color_badgeMuted,
      R.id.theme_color_badgeMutedText,
    };
    int[] chatColorIds = new int[] {
      R.id.theme_color_chatSendButton,
      R.id.theme_color_chatKeyboard,
      R.id.theme_color_chatKeyboardButton,
    };
    int[] plainColorIds = new int[] {
      R.id.theme_color_chatBackground,
      R.id.theme_color_chatSeparator,

      R.id.theme_color_unread,
      R.id.theme_color_unreadText,

      R.id.theme_color_messageVerticalLine,
      R.id.theme_color_messageSelection,
      R.id.theme_color_messageSwipeBackground,
      R.id.theme_color_messageSwipeContent,
      R.id.theme_color_messageAuthor,
      R.id.theme_color_messageAuthorPsa,
    };
    int[] chatOtherColorIds = new int[] {
      R.id.theme_color_shareSeparator
    };
    int[] bubbleColorIds = new int[] {
      R.id.theme_color_bubble_chatBackground,
      R.id.theme_color_bubble_chatSeparator,
      R.id.theme_color_bubble_messageSelection,
      R.id.theme_color_bubble_messageSelectionNoWallpaper,
      R.id.theme_color_bubble_messageCheckOutline,
      R.id.theme_color_bubble_messageCheckOutlineNoWallpaper,
    };
    int[] bubbleInColorIds = new int[] {
      R.id.theme_color_bubbleIn_background,
      R.id.theme_color_bubbleIn_time,
      R.id.theme_color_bubbleIn_progress,
      R.id.theme_color_bubbleIn_text,
      R.id.theme_color_bubbleIn_textLink,
      R.id.theme_color_bubbleIn_textLinkPressHighlight,
      R.id.theme_color_bubbleIn_outline,
      R.id.theme_color_bubbleIn_pressed,
      R.id.theme_color_bubbleIn_separator,
    };
    int[] bubbleOutColorIds = new int[] {
      R.id.theme_color_bubbleOut_background,
      R.id.theme_color_bubbleOut_ticks,
      R.id.theme_color_bubbleOut_ticksRead,
      R.id.theme_color_bubbleOut_time,
      R.id.theme_color_bubbleOut_progress,
      R.id.theme_color_bubbleOut_text,
      R.id.theme_color_bubbleOut_textLink,
      R.id.theme_color_bubbleOut_textLinkPressHighlight,
      R.id.theme_color_bubbleOut_messageAuthor,
      R.id.theme_color_bubbleOut_messageAuthorPsa,
      R.id.theme_color_bubbleOut_chatVerticalLine,
      R.id.theme_color_bubbleOut_inlineOutline,
      R.id.theme_color_bubbleOut_inlineText,
      R.id.theme_color_bubbleOut_inlineIcon,
      R.id.theme_color_bubbleOut_waveformActive,
      R.id.theme_color_bubbleOut_waveformInactive,
      R.id.theme_color_bubbleOut_file,
      R.id.theme_color_bubbleOut_outline,
      R.id.theme_color_bubbleOut_pressed,
      R.id.theme_color_bubbleOut_separator,
    };
    int[] bubbleOverlayNoWallpaperColorIds = new int[] {
      R.id.theme_color_bubble_unread_noWallpaper,
      R.id.theme_color_bubble_unreadText_noWallpaper,
      R.id.theme_color_bubble_date_noWallpaper,
      R.id.theme_color_bubble_dateText_noWallpaper,
      R.id.theme_color_bubble_button_noWallpaper,
      R.id.theme_color_bubble_buttonRipple_noWallpaper,
      R.id.theme_color_bubble_buttonText_noWallpaper,
      R.id.theme_color_bubble_mediaReply_noWallpaper,
      R.id.theme_color_bubble_mediaReplyText_noWallpaper,
      R.id.theme_color_bubble_mediaTime_noWallpaper,
      R.id.theme_color_bubble_mediaTimeText_noWallpaper,
      R.id.theme_color_bubble_overlay_noWallpaper,
      R.id.theme_color_bubble_overlayText_noWallpaper,
    };
    int[] bubbleOverlayColorIds = new int[] {
      R.id.theme_color_bubble_unread,
      R.id.theme_color_bubble_unreadText,
      R.id.theme_color_bubble_date,
      R.id.theme_color_bubble_dateText,
      R.id.theme_color_bubble_button,
      R.id.theme_color_bubble_buttonRipple,
      R.id.theme_color_bubble_buttonText,
      R.id.theme_color_bubble_mediaReply,
      R.id.theme_color_bubble_mediaReplyText,
      R.id.theme_color_bubble_mediaTime,
      R.id.theme_color_bubble_mediaTimeText,
      R.id.theme_color_bubble_mediaOverlay,
      R.id.theme_color_bubble_mediaOverlayText,
      R.id.theme_color_bubble_overlay,
      R.id.theme_color_bubble_overlayText,
    };
    int[] bubbleVisualProperties = new int[] {
      ThemeProperty.BUBBLE_CORNER,
      ThemeProperty.BUBBLE_CORNER_MERGED,
      ThemeProperty.BUBBLE_CORNER_LEGACY,
      // TODO ThemeProperty.BUBBLE_OUTER_MARGIN,
      ThemeProperty.BUBBLE_OUTLINE,
      ThemeProperty.BUBBLE_OUTLINE_SIZE,
      ThemeProperty.BUBBLE_DATE_CORNER,
      ThemeProperty.BUBBLE_UNREAD_SHADOW,
      ThemeProperty.IMAGE_CORNER,
      ThemeProperty.DATE_CORNER,
      ThemeProperty.WALLPAPER_USAGE_ID,
      ThemeProperty.WALLPAPER_ID,
      ThemeProperty.WALLPAPER_OVERRIDE_UNREAD,
      ThemeProperty.WALLPAPER_OVERRIDE_DATE,
      ThemeProperty.WALLPAPER_OVERRIDE_BUTTON,
      ThemeProperty.WALLPAPER_OVERRIDE_MEDIA_REPLY,
      ThemeProperty.WALLPAPER_OVERRIDE_TIME,
      ThemeProperty.WALLPAPER_OVERRIDE_OVERLAY,
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
      R.id.theme_color_avatar_content,

      R.id.theme_color_avatarArchive,
      R.id.theme_color_avatarArchivePinned,

      R.id.theme_color_avatarSavedMessages,
      R.id.theme_color_avatarSavedMessages_big,

      R.id.theme_color_avatarReplies,
      R.id.theme_color_avatarReplies_big,

      R.id.theme_color_avatarInactive,
      R.id.theme_color_avatarInactive_big,
      R.id.theme_color_nameInactive,

      R.id.theme_color_avatarRed,
      R.id.theme_color_avatarRed_big,
      R.id.theme_color_nameRed,

      R.id.theme_color_avatarOrange,
      R.id.theme_color_avatarOrange_big,
      R.id.theme_color_nameOrange,

      R.id.theme_color_avatarYellow,
      R.id.theme_color_avatarYellow_big,
      R.id.theme_color_nameYellow,

      R.id.theme_color_avatarGreen,
      R.id.theme_color_avatarGreen_big,
      R.id.theme_color_nameGreen,

      R.id.theme_color_avatarCyan,
      R.id.theme_color_avatarCyan_big,
      R.id.theme_color_nameCyan,

      R.id.theme_color_avatarBlue,
      R.id.theme_color_avatarBlue_big,
      R.id.theme_color_nameBlue,

      R.id.theme_color_avatarViolet,
      R.id.theme_color_avatarViolet_big,
      R.id.theme_color_nameViolet,

      R.id.theme_color_avatarPink,
      R.id.theme_color_avatarPink_big,
      R.id.theme_color_namePink,
    };
    
    count = 0;
    count += addGroup(items, R.id.theme_category_colors, 0, 0, placeholderColorIds, false, searchQuery, true, sortedIds);
    // count += addGroup(items, R.id.theme_category_colors, count == 0 ? R.string.ThemeSectionUsers : 0, 0, nameColorIds, false, searchQuery, true, sortedIds);
    totalCount += count;
    
    // MEDIA
    
    int[] fileColorIds = new int[] {
      R.id.theme_color_file,
      R.id.theme_color_fileYellow,
      R.id.theme_color_fileGreen,
      R.id.theme_color_fileRed,
    };
    int[] waveformColorIds = new int[] {
      R.id.theme_color_waveformActive,
      R.id.theme_color_waveformInactive,
    };
    count = 0;
    count += addGroup(items, R.id.theme_category_colors, R.string.ThemeSectionMedia, 0, fileColorIds, false, searchQuery, true, sortedIds);
    count += addGroup(items, R.id.theme_category_colors, count == 0 ? R.string.ThemeSectionMedia : 0, 0, waveformColorIds, false, searchQuery, true, sortedIds);
    totalCount += count;

    // ATTACH MENU

    int[] attachColorIds = new int[] {
      R.id.theme_color_attachPhoto,
      R.id.theme_color_attachFile,
      R.id.theme_color_attachLocation,
      R.id.theme_color_attachContact,
      R.id.theme_color_attachInlineBot,
      R.id.theme_color_attachText,
      R.id.theme_color_fileAttach,
    };
    totalCount += addGroup(items, R.id.theme_category_colors, R.string.ThemeSectionAttach, 0, attachColorIds, false, searchQuery, true, sortedIds);

    // INSTANT VIEW

    int[] ivColorIds = new int[] {
      R.id.theme_color_iv_pageTitle,
      R.id.theme_color_iv_pageSubtitle,

      R.id.theme_color_iv_text,
      R.id.theme_color_iv_textLink,
      R.id.theme_color_iv_textLinkPressHighlight,
      R.id.theme_color_iv_textMarked,
      R.id.theme_color_iv_textMarkedBackground,
      R.id.theme_color_iv_textMarkedLink,
      R.id.theme_color_iv_textMarkedLinkPressHighlight,
      R.id.theme_color_iv_textReference,
      R.id.theme_color_iv_textReferenceBackground,
      R.id.theme_color_iv_textReferenceBackgroundPressed,
      R.id.theme_color_iv_textReferenceOutline,
      R.id.theme_color_iv_textReferenceOutlinePressed,
      R.id.theme_color_iv_textCode,
      R.id.theme_color_iv_pageAuthor,
      R.id.theme_color_iv_caption,
      R.id.theme_color_iv_pageFooter,
      R.id.theme_color_iv_header,

      R.id.theme_color_iv_pullQuote,
      R.id.theme_color_iv_blockQuote,
      R.id.theme_color_iv_blockQuoteLine,

      R.id.theme_color_iv_preBlockBackground,
      R.id.theme_color_iv_textCodeBackground,
      R.id.theme_color_iv_textCodeBackgroundPressed,
      R.id.theme_color_iv_separator,

      R.id.theme_color_ivHeaderIcon,
      R.id.theme_color_ivHeader
    };
    totalCount += addGroup(items, R.id.theme_category_iv, R.string.ThemeCategoryIV, 0, ivColorIds, false, searchQuery, true, sortedIds);
    
    // THEME RADIOS
    
    int[] themeColorIds = new int[] {
      R.id.theme_color_themeClassic,
      R.id.theme_color_themeBlue,
      R.id.theme_color_themeRed,
      R.id.theme_color_themeOrange,
      R.id.theme_color_themeGreen,
      R.id.theme_color_themePink,
      R.id.theme_color_themeCyan,
      R.id.theme_color_themeNightBlue,
      R.id.theme_color_themeNightBlack,

      R.id.theme_color_themeBlackWhite,
      R.id.theme_color_themeWhiteBlack,
    };
    count = addGroup(items, R.id.theme_category_other, R.string.ThemeCategoryOther, R.string.ThemeSectionRadios_info, themeColorIds, false, searchQuery, true, sortedIds);
    
    // Wallpaper overlays
    
    int[] wallpaperColorIds = new int[] {
      R.id.theme_color_wp_cats,
      R.id.theme_color_wp_catsPink,
      R.id.theme_color_wp_catsGreen,
      R.id.theme_color_wp_catsOrange,
      R.id.theme_color_wp_catsBeige,
      R.id.theme_color_wp_circlesBlue,
    };
    count += addGroup(items, R.id.theme_category_other, count == 0 ? R.string.ThemeCategoryOther : 0, R.string.ThemeSectionWP_info, wallpaperColorIds, false, searchQuery, true, sortedIds);

    int[] scrollBarColorIds = new int[] {
      R.id.theme_color_sectionedScrollBar,
      R.id.theme_color_sectionedScrollBarActive,
      R.id.theme_color_sectionedScrollBarActiveContent,
    };
    count += addGroup(items, R.id.theme_category_other, count == 0 ? R.string.ThemeCategoryOther : 0, 0, scrollBarColorIds, false, searchQuery, true, sortedIds);

    int[] snackBarColorIds = new int[] {
      R.id.theme_color_snackbarUpdate,
      R.id.theme_color_snackbarUpdateAction,
      R.id.theme_color_snackbarUpdateText,
    };
    count += addGroup(items, R.id.theme_category_other, count == 0 ? R.string.ThemeCategoryOther : 0, 0, snackBarColorIds, false, searchQuery, true, sortedIds);

    totalCount += count;

    // INTERNAL

    int[] internalColorIds = new int[] {
      R.id.theme_color_caption_textLink,
      R.id.theme_color_caption_textLinkPressHighlight,

      R.id.theme_color_videoSliderActive,
      R.id.theme_color_videoSliderInactive,

      R.id.theme_color_white,
      R.id.theme_color_black,
      R.id.theme_color_transparentEditor,
    };
    int[] photoShadowColorIds = new int[] {
      R.id.theme_color_photoShadowTint1,
      R.id.theme_color_photoShadowTint2,
      R.id.theme_color_photoShadowTint3,
      R.id.theme_color_photoShadowTint4,
      R.id.theme_color_photoShadowTint5,
      R.id.theme_color_photoShadowTint6,
      R.id.theme_color_photoShadowTint7,
      R.id.theme_color_photoHighlightTint1,
      R.id.theme_color_photoHighlightTint2,
      R.id.theme_color_photoHighlightTint3,
      R.id.theme_color_photoHighlightTint4,
      R.id.theme_color_photoHighlightTint5,
      R.id.theme_color_photoHighlightTint6,
      R.id.theme_color_photoHighlightTint7,
    };
    int[] ledColorIds = new int[] {
      R.id.theme_color_ledBlue,
      R.id.theme_color_ledOrange,
      R.id.theme_color_ledYellow,
      R.id.theme_color_ledGreen,
      R.id.theme_color_ledCyan,
      R.id.theme_color_ledRed,
      R.id.theme_color_ledPurple,
      R.id.theme_color_ledPink,
      R.id.theme_color_ledWhite,
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
    switch (v.getId()) {
      case R.id.btn_color: {
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
      }
      case R.id.btn_property: {
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

    int id = v.getId();
    switch (id) {
      case R.id.btn_colorCopy: {
        int color = state.getColor();
        UI.copyText(Theme.getColorName(state.getColorId()) + ": " + getColorRepresentation(color, true), R.string.CopiedColor);
        break;
      }
      case R.id.btn_colorPaste: {
        try {
          int newColor = getPasteColor();
          if (state.getColor() != newColor)
            state.saveLastColor();
          changeColor(item, contentView, v, state, newColor, false, false);
        } catch (Throwable ignored) {
          // Should be unreachable
        }
        break;
      }
      case R.id.btn_colorSave: {
        if (state.saveLastColor()) {
          v.setEnabled(false);
        }
        break;
      }
      case R.id.btn_colorUndo:
      case R.id.btn_colorRedo: {
        boolean done = id == R.id.btn_colorUndo ? state.undo() : state.redo();
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
        break;
      }
      case R.id.btn_colorClear: {
        removeColor(v, !state.canUndo());
        break;
      }
      case R.id.btn_colorCalculate: {
        openInputAlert(Lang.getString(R.string.ThemeCalcTitle), Lang.getString(R.string.ThemeCalcHint), R.string.ThemeCalcSave, R.string.Cancel, Strings.getHexColor(getTheme().getColor(R.id.theme_color_filling), false), (inputView, result) -> {
          try {
            int parsedColor = parseAnyColor(result);
            if (Color.alpha(parsedColor) == 255 && currentEditPosition == editPosition) {
              changeColor(item, contentView, v, state, ColorUtils.compositeColor(parsedColor, state.getColor()), false, false);
              return true;
            }
          } catch (Throwable ignored) { }
          return false;
        }, true);
        break;
      }
    }
  }

  @Override
  public boolean onLongClick (View v) {
    switch (v.getId()) {
      case R.id.btn_colorClear: {
        removeColor(v, true);
        return true;
      }
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

  private void editColor (int position, @ThemeColorId int colorId) {
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
      case R.id.theme_color_profileSectionActive:
      case R.id.theme_color_profileSectionActiveContent:
      case R.id.theme_color_textLink:
      case R.id.theme_color_textLinkPressHighlight:
      case R.id.theme_color_textSearchQueryHighlight:
      case R.id.theme_color_textNeutral:
      case R.id.theme_color_iconActive:
      case R.id.theme_color_headerBackground:
      case R.id.theme_color_fillingPositive:
      case R.id.theme_color_ticks:
      case R.id.theme_color_passcode:
      case R.id.theme_color_bubbleOut_ticks:
      case R.id.theme_color_messageVerticalLine:
      case R.id.theme_color_chatSendButton:
      case R.id.theme_color_messageSwipeBackground:
      case R.id.theme_color_unread:
      case R.id.theme_color_unreadText:
      case R.id.theme_color_messageAuthor:
      case R.id.theme_color_messageAuthorPsa:
      case R.id.theme_color_chatListAction:
      case R.id.theme_color_badge:
      case R.id.theme_color_online:
      case R.id.theme_color_messageSelection:
      case R.id.theme_color_textSelectionHighlight:
      case R.id.theme_color_bubbleOut_background:
      case R.id.theme_color_bubbleOut_time:
      case R.id.theme_color_bubbleOut_progress:
      case R.id.theme_color_bubbleIn_progress:
      case R.id.theme_color_bubbleIn_textLink:
      case R.id.theme_color_bubbleIn_textLinkPressHighlight:
      case R.id.theme_color_checkActive:
      case R.id.theme_color_file:
      case R.id.theme_color_playerButtonActive:
      case R.id.theme_color_bubbleOut_file:
      case R.id.theme_color_waveformActive:
      case R.id.theme_color_bubbleOut_waveformActive:
      case R.id.theme_color_waveformInactive:
      case R.id.theme_color_bubbleOut_waveformInactive:
      case R.id.theme_color_sliderActive:
      case R.id.theme_color_seekReady:
      case R.id.theme_color_seekDone:
      case R.id.theme_color_inlineText:
      case R.id.theme_color_inlineOutline:
      case R.id.theme_color_inlineIcon:
      case R.id.theme_color_progress:
      case R.id.theme_color_togglerActive:
      case R.id.theme_color_togglerActiveBackground:
      case R.id.theme_color_circleButtonRegular:
      case R.id.theme_color_circleButtonTheme:
      case R.id.theme_color_inputActive:
      case R.id.theme_color_controlActive:
      case R.id.theme_color_promo:
      case R.id.theme_color_headerBarCallActive:
      case R.id.theme_color_chatListVerify:
      case R.id.theme_color_bubbleOut_textLink:
      case R.id.theme_color_bubbleOut_textLinkPressHighlight:
      case R.id.theme_color_bubbleOut_messageAuthor:
      case R.id.theme_color_bubbleOut_messageAuthorPsa:
      case R.id.theme_color_bubbleOut_chatVerticalLine:
      case R.id.theme_color_bubbleOut_inlineOutline:
      case R.id.theme_color_bubbleOut_inlineIcon:
      case R.id.theme_color_bubbleOut_inlineText:
      case R.id.theme_color_notification:
      case R.id.theme_color_notificationPlayer:
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
      case R.id.theme_color_filling:
      case R.id.theme_color_background:
      case R.id.theme_color_iv_preBlockBackground:
      case R.id.theme_color_iv_textCodeBackground:
        getRecyclerView().invalidate();
        break;
    }
  }

  // Properties

  private void editProperty (@ThemeProperty int propertyId) {
    ThemeCustom theme = getTheme();
    float currentValue = theme.getProperty(propertyId);
    float originalValue = theme.getParentTheme().getProperty(propertyId);
    String defaultValue = U.formatFloat(originalValue, true);
    if (propertyId == ThemeProperty.PARENT_THEME)
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

  private boolean saveProperty (@ThemeProperty int propertyId, float rawValue) {
    if (propertyId == ThemeProperty.WALLPAPER_ID && !tdlib.wallpaper().isValidWallpaperId((int) rawValue)) {
      return false;
    }
    if (!ThemeManager.isValidProperty(propertyId, rawValue)) {
      return false;
    }
    ThemeCustom theme = getTheme();
    float originalValue = theme.getProperty(propertyId);
    Float value;
    if (originalValue != rawValue || propertyId == ThemeProperty.PARENT_THEME) {
      value = rawValue;
    } else {
      value = null;
    }
    theme.setProperty(propertyId, value);
    Settings.instance().setCustomThemeProperty(ThemeManager.resolveCustomThemeId(theme.getId()), propertyId, value);
    if (propertyId == ThemeProperty.DARK) {
      TdlibManager.instance().checkThemeId(theme.getId(), rawValue == 1f, theme.getParentTheme().getId());
    }
    ThemeManager.instance().notifyPropertyChanged(theme.getId(), propertyId, rawValue, originalValue);
    if (propertyId == ThemeProperty.DARK) {
      context().forceNightMode(rawValue == 1f);
    }
    if (propertyId == ThemeProperty.WALLPAPER_ID) {
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
