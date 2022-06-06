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
 * File created on 08/02/2016 at 08:19
 */
package org.thunderdog.challegram.component.user;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import androidx.annotation.StringRes;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.RtlCheckListener;
import org.thunderdog.challegram.navigation.StretchyHeaderView;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.v.HeaderEditText;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.widget.FrameLayoutFix;

public class BubbleHeaderView extends FrameLayoutFix implements RtlCheckListener, StretchyHeaderView, TextWatcher, HeaderView.OffsetChangeListener {
  private HeaderEditText editText;
  private ScrollView scrollView;
  private BubbleWrapView bubbleWrap;

  Callback callback;
  private ArrayList<TGUser> users;
  private int maxBubbleHeight;

  public BubbleHeaderView (Context context) {
    super(context);

    users = new ArrayList<>(10);

    FrameLayoutFix.LayoutParams params;

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    bubbleWrap = new BubbleWrapView(context);
    bubbleWrap.setHeaderView(this);
    bubbleWrap.setLayoutParams(params);

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, maxBubbleHeight = Screen.dp(BubbleWrapView.START_Y) + Screen.dp(BubbleWrapView.SPACING) + Screen.dp(16f) * 4);
    if (Lang.rtl()) {
      params.rightMargin = Screen.dp(60f);
    } else {
      params.leftMargin = Screen.dp(60f);
    }

    scrollView = new ScrollView(context) {
      @Override
      public boolean onTouchEvent (MotionEvent ev) {
        return ev.getAction() == MotionEvent.ACTION_DOWN ? (ev.getY() < lastHeight && super.onTouchEvent(ev)) : super.onTouchEvent(ev);
      }
    };
    scrollView.setVerticalScrollBarEnabled(false);
    scrollView.addView(bubbleWrap);
    scrollView.setLayoutParams(params);

    addView(scrollView);

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Size.getHeaderPortraitSize());
    if (Lang.rtl()) {
      params.rightMargin = Screen.dp(68f);
    } else {
      params.leftMargin = Screen.dp(68f);
    }
    editText = HeaderEditText.create(this, false, null);
    editText.setPadding(Screen.dp(5f), 0, Screen.dp(5f), 0);
    editText.addTextChangedListener(this);
    editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
    editText.setLayoutParams(params);
    addView(editText);
  }

  private int currentTopOffset;

  @Override
  public void onHeaderOffsetChanged (HeaderView headerView, int newOffset) {
    currentTopOffset = newOffset;
    Views.setTopMargin(scrollView, newOffset);
    Views.setTopMargin(editText, newOffset);
  }

  @Override
  public void checkRtl () {
    if (editText != null && editText.getGravity() != (Lang.gravity() | Gravity.CENTER_VERTICAL)) {
      editText.checkRtl();
      if (Views.setMargins((FrameLayout.LayoutParams) editText.getLayoutParams(), Lang.rtl() ? 0 : Screen.dp(68f), currentTopOffset, Lang.rtl() ? Screen.dp(68f) : 0, 0)) {
        Views.updateLayoutParams(editText);
      }
    }
    if (bubbleWrap != null) {
      bubbleWrap.invalidate();
    }
    if (scrollView != null && Views.setMargins((FrameLayout.LayoutParams) scrollView.getLayoutParams(), Lang.rtl() ? 0 : Screen.dp(60f), currentTopOffset, Lang.rtl() ? Screen.dp(60f) : 0, 0)) {
      Views.updateLayoutParams(scrollView);
    }
  }

  public void forceUsers (List<TGUser> users) {
    for (TGUser user : users) {
      this.users.add(user);
      bubbleWrap.addBubbleForce(user);
    }

    bubbleWrap.buildLayout();
    ignoreFirstUpdate = true;
    scrollView.addOnLayoutChangeListener(new OnLayoutChangeListener() {
      @Override
      public void onLayoutChange (View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
        scrollView.scrollTo(0, bubbleWrap.getMeasuredHeight() - scrollView.getMeasuredHeight());
        scrollView.removeOnLayoutChangeListener(this);
      }
    });
    // forceHeight(Math.min(maxBubbleHeight, bubbleWrap.getCurrentHeight()));
  }

  public int getCurrentWrapHeight () {
    return Math.min(maxBubbleHeight, bubbleWrap.getCurrentHeight());
  }

  public void destroy () {
    bubbleWrap.destroy();
  }

  public void setHint (@StringRes int hintResId) {
    editText.setHint(Lang.getString(hintResId));
  }

  public void setCallback (Callback callback) {
    this.callback = callback;
  }

  public HeaderEditText getInput () {
    return editText;
  }

  public boolean areBubblesAnimating () {
    return bubbleWrap.isAnimating();
  }

  // public changers

  public void addUser (TGUser user) {
    users.add(user);
    bubbleWrap.addBubble(user);
  }

  private void removeUser (int index) {
    TGUser removed = users.remove(index);
    bubbleWrap.removeBubble(removed);
  }

  public void removeUser (TGUser user) {
    long userId = user.getUserId();
    int i = 0;
    for (TGUser u : users) {
      if (u.getUserId() == userId) {
        removeUser(i);
        break;
      }
      i++;
    }
  }

  private int lastHeight;
  private int heightDiff;
  private float factor;
  private boolean overflow;
  private int fromOffset;
  private boolean applyHeight;

  boolean prepareChangeHeight (int height, boolean byTouch) {
    factor = 0f;
    if (lastHeight == maxBubbleHeight && height >= lastHeight) {
      fromOffset = scrollView.getScrollY();
      heightDiff = height - maxBubbleHeight - fromOffset; // 0
      overflow = true;
      if (byTouch && heightDiff > 0) {
        heightDiff = 0;
      }
    } else {
      heightDiff = height - lastHeight;
      overflow = false;
      if (height < lastHeight && callback != null) {
        callback.prepareHeaderOffset(height);
        applyHeight = false;
      } else {
        applyHeight = true;
      }
    }
    return heightDiff != 0;
  }

  void completeChangeHeight () {
    if (!overflow) {
      lastHeight = lastHeight + heightDiff;
      if (applyHeight && callback != null) {
        callback.applyHeaderOffset();
      }
    }
  }

  private boolean ignoreFirstUpdate;

  void forceHeight (int height) {
    if (ignoreFirstUpdate) {
      ignoreFirstUpdate = false;
      lastHeight = Math.min(maxBubbleHeight, height);
      scrollView.scrollTo(0, height);
      editText.setTranslationY(lastHeight);
      return;
    }
    if (prepareChangeHeight(height, false)) {
      setFactor(1f);
      // applyHeight = true;
      completeChangeHeight();
    }
  }

  public void setFactor (float factor) {
    if (this.factor != factor) {
      if (overflow) {
        scrollView.scrollTo(0, fromOffset + (int) ((float) heightDiff * factor));
      } else {
        int height = lastHeight + (int) ((float) heightDiff * factor);
        editText.setTranslationY(height);
        if (callback != null) {
          callback.setHeaderOffset(height);
        }
      }
    }
  }

  public float getFactor () {
    return factor;
  }

  // TextWatcher

  @Override
  public void onTextChanged (CharSequence s, int start, int before, int count) {
    if (callback != null) {
      callback.searchUser(s.toString());
    }
  }

  @Override
  public void beforeTextChanged (CharSequence s, int start, int count, int after) {

  }

  @Override
  public void afterTextChanged (Editable s) {

  }

  public void clearSearchInput () {
    editText.setText("");
  }

  public HeaderEditText getSearchInput () {
    return editText;
  }

  // Simple transition

  private float scaleFactor;

  /*@Override
  public float getScaleFactor () {
    return scaleFactor;
  }*/

  @Override
  public void setScaleFactor (float scaleFactor, float fromFactor, float toScaleFactor, boolean byScroll) {
    scaleFactor = Size.convertExpandedFactor(scaleFactor);
    if (this.scaleFactor != scaleFactor) {
      this.scaleFactor = scaleFactor;

      if (lastHeight != 0) {
        float origScale = scaleFactor / ((float) lastHeight / (float) Size.getHeaderSizeDifference(false));
        setTranslationY(-lastHeight * (1f - origScale));
      }
    }
  }

  // Callback

  public interface Callback {
    View getTranslationView ();
    void searchUser (String q);
    void onBubbleRemoved (long chatId);
    void setHeaderOffset (int offset);
    void applyHeaderOffset ();
    void prepareHeaderOffset (int height);
  }
}
