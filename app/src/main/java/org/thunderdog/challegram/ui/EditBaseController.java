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
 * File created on 21/12/2016
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.DoneListener;
import org.thunderdog.challegram.widget.DoneButton;
import org.thunderdog.challegram.widget.MaterialEditText;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;

public abstract class EditBaseController<T> extends ViewController<T> implements FactorAnimator.Target, MaterialEditText.EnterKeyListener, DoneListener {
  public EditBaseController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  protected abstract void onCreateView (Context context, FrameLayoutFix contentView, RecyclerView recyclerView);

  private FrameLayoutFix contentView;
  protected RecyclerView recyclerView;
  private DoneButton doneButton;
  protected RecyclerView.ItemAnimator itemAnimator;

  protected @ThemeColorId int getRecyclerBackgroundColorId () {
    return R.id.theme_color_filling;
  }

  protected final DoneButton getDoneButton () {
    return isDoneVisible() ? doneButton : null;
  }

  @Override
  protected View onCreateView (Context context) {
    contentView = new FrameLayoutFix(context);
    ViewSupport.setThemedBackground(contentView, getRecyclerBackgroundColorId(), this);
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    recyclerView = (RecyclerView) Views.inflate(context(), R.layout.recycler, contentView);
    recyclerView.setItemAnimator(itemAnimator = new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180l));
    recyclerView.setHasFixedSize(true);
    recyclerView.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
    recyclerView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    contentView.addView(recyclerView);

    int padding = Screen.dp(4f);
    FrameLayoutFix.LayoutParams params;
    params = FrameLayoutFix.newParams(Screen.dp(56f) + padding * 2, Screen.dp(56f) + padding * 2, (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM);
    params.rightMargin = params.leftMargin = params.bottomMargin = Screen.dp(16f) - padding;

    doneButton = new DoneButton(context);
    doneButton.setId(R.id.btn_done);
    addThemeInvalidateListener(doneButton);
    doneButton.setOnClickListener(v -> {
      if (doneVisible) {
        onDoneClick(null);
      }
    });
    doneButton.setLayoutParams(params);
    doneButton.setMaximumAlpha(0f);
    contentView.addView(doneButton);

    onCreateView(context, contentView, recyclerView);

    FrameLayoutFix wrapper = new FrameLayoutFix(context);
    wrapper.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    wrapper.addView(contentView);
    return wrapper;
  }

  @Override
  public int getRootColorId () {
    return getRecyclerBackgroundColorId();
  }

  @Override
  public boolean onDoneClick (View v) {
    return onDoneClick();
  }

  @Override
  public boolean onEnterPressed (MaterialEditText v) {
    return onDoneClick(v);
  }

  @Override
  public View getViewForApplyingOffsets () {
    return contentView;
  }

  protected static class SimpleEditorActionListener implements TextView.OnEditorActionListener {

    private final int imeAction;
    private final DoneListener doneListener;

    public SimpleEditorActionListener (int imeAction, @Nullable DoneListener context) {
      this.imeAction = imeAction;
      this.doneListener = context;
    }

    public int getImeAction () {
      return imeAction;
    }

    public boolean hasContext () {
      return doneListener != null;
    }

    @Override
    public boolean onEditorAction (TextView v, int actionId, KeyEvent event) {
      return doneListener != null && actionId == imeAction && (event == null || event.getAction() == MotionEvent.ACTION_DOWN) && doneListener.onDoneClick(v);
    }
  }

  // Done button

  private static final int DONE_ANIMATOR = 0, DONE_FADE_ANIMATOR = 1;
  private boolean doneVisible;
  private float doneVisibilityFactor = 0f;

  private boolean needDoneFadeIn;

  protected boolean needShowAnimationDelay () {
    return true;
  }

  protected boolean isDoneVisible () {
    return doneVisible;
  }

  protected void setDoneVisible (boolean isVisible) {
    if (this.doneVisible != isVisible) {
      this.doneVisible = isVisible;
      if (contentView.getParent() != null && doneButton.getMeasuredWidth() != 0 && isFocused()) {
        this.doneVisibilityFactor = 1f;
        doneButton.setMaximumAlpha(1f);
        doneButton.setIsVisible(isVisible, true);
      } else {
        if (isVisible) {
          if (needShowAnimationDelay()) {
            this.doneVisibilityFactor = 0f;
            doneButton.setMaximumAlpha(0f);
            this.needDoneFadeIn = true;
          } else {
            this.doneVisibilityFactor = 1f;
            doneButton.setMaximumAlpha(1f);
          }
        }
        doneButton.setIsVisible(isVisible, false);
      }
    }
  }

  protected void setInstantDoneVisible (boolean isVisible) {
    if (this.doneVisible != isVisible) {
      this.doneVisible = isVisible;
      this.doneVisibilityFactor = 1f;
      doneButton.setMaximumAlpha(1f);
      doneButton.setIsVisible(isVisible, false);
    }
  }

  private boolean inProgress;

  protected final boolean isInProgress () {
    return inProgress;
  }

  protected final void setInProgress (boolean inProgress) {
    if (this.inProgress != inProgress) {
      this.inProgress = inProgress;
      setDoneInProgress(inProgress);
      onProgressStateChanged(inProgress);
    }
  }

  protected void onProgressStateChanged (boolean inProgress) {
    // override
  }

  protected final void setDoneInProgress (boolean inProgress) {
    doneButton.setInProgress(inProgress);
  }

  protected final boolean isDoneInProgress () {
    return doneButton.isInProgress();
  }

  @Override
  public final void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case DONE_FADE_ANIMATOR: {
        setDoneVisibilityFactor(factor);
        break;
      }
      default: {
        onChildFactorChanged(id, factor, fraction);
        break;
      }
    }
  }

  @Override
  public final void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case DONE_ANIMATOR: {
        break;
      }
      default: {
        onChildFactorChangeFinished(id, finalFactor);
        break;
      }
    }
  }

  protected void onChildFactorChanged (int id, float factor, float fraction) {
    // override
  }

  protected void onChildFactorChangeFinished (int id, float finalFator) {
    // override
  }

  private void setDoneVisibilityFactor (float factor) {
    if (this.doneVisibilityFactor != factor) {
      this.doneVisibilityFactor = factor;
      doneButton.setMaximumAlpha(factor);
    }
  }

  protected void setDoneIcon (@DrawableRes int icon) {
    setDoneIcon(icon, 0);
  }

  protected void setDoneIcon (@DrawableRes int icon, int offsetLeft) {
    if (doneButton.getAlpha() != 0f) {
      doneButton.replaceIcon(icon, offsetLeft);
    } else {
      doneButton.setIcon(icon, offsetLeft);
    }
  }

  protected boolean onDoneClick () {
    // override
    return false;
  }

  protected final void onSaveCompleted () {
    if (getKeyboardState() && getLockFocusView() != null) {
      Keyboard.hide(getLockFocusView());
      tdlib().ui().postDelayed(this::navigateBack, 120);
    } else {
      navigateBack();
    }
  }

  @Override
  public void onFocus () {
    super.onFocus();
    if (needDoneFadeIn) {
      needDoneFadeIn = false;
      FactorAnimator animator = new FactorAnimator(DONE_FADE_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
      animator.setStartDelay(120l);
      animator.animateTo(1f);
    }
  }

  @Override
  protected void handleLanguageDirectionChange () {
    super.handleLanguageDirectionChange();
    if (Views.setGravity(doneButton, (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM)) {
      Views.updateLayoutParams(doneButton);
    }
  }

  @Override
  public void handleLanguagePackEvent (int event, int arg1) {
    if (recyclerView != null && recyclerView.getAdapter() instanceof SettingsAdapter) {
      SettingsAdapter adapter = (SettingsAdapter) recyclerView.getAdapter();
      switch (event) {
        case Lang.EVENT_PACK_CHANGED:
          adapter.notifyAllStringsChanged();
          break;
        case Lang.EVENT_DIRECTION_CHANGED:
          adapter.notifyAllStringsChanged();
          break;
        case Lang.EVENT_STRING_CHANGED:
          adapter.notifyStringChanged(arg1);
          break;
        case Lang.EVENT_DATE_FORMAT_CHANGED:
          // Nothing to change
          break;
      }
    }
  }
}
