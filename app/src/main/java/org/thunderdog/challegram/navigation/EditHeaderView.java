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
 * File created on 03/02/2016 at 10:27
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.StringRes;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageFileLocal;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.v.HeaderEditText;

import me.vkryl.android.ViewUtils;
import me.vkryl.android.text.CodePointCountFilter;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.td.TdConstants;

public class EditHeaderView extends FrameLayoutFix implements RtlCheckListener, Destroyable, StretchyHeaderView, TextWatcher, HeaderView.OffsetChangeListener {
  private final ViewController<?> parent;
  private HeaderEditText input;
  private final ImageReceiver receiver;

  private final Drawable icon;

  public EditHeaderView (Context context, ViewController<?> parent) {
    super(context);
    this.icon = Drawables.get(getResources(), R.drawable.baseline_camera_alt_24);
    this.parent = parent;

    setWillNotDraw(false);

    receiver = new ImageReceiver(this, Screen.dp(30.5f));

    FrameLayoutFix.LayoutParams params;

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(62f));
    params.topMargin = Screen.dp(62f);

    setLayoutParams(params);

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.gravity = Lang.gravity() | Gravity.CENTER_VERTICAL;

    if (Lang.rtl()) {
      params.leftMargin = Screen.dp(20f);
      params.rightMargin = Screen.dp(96f);
    } else {
      params.rightMargin = Screen.dp(20f);
      params.leftMargin = Screen.dp(96f);
    }

    input = HeaderEditText.create(this, false, null);
    input.setHint(Lang.getString(R.string.ChannelName));
    input.addTextChangedListener(this);
    input.checkRtl();
    input.setLayoutParams(params);
    input.setFilters(new InputFilter[] {new CodePointCountFilter(TdConstants.MAX_CHAT_TITLE_LENGTH)});
    addView(input);
  }

  @Override
  public void onHeaderOffsetChanged (HeaderView headerView, int newOffset) {
    Views.setTopMargin(this, newOffset + Screen.dp(62f));
  }

  @Override
  public void checkRtl () {
    input.setGravity(Gravity.CENTER_VERTICAL | Lang.gravity());
    int leftMargin = Screen.dp(96f);
    int rightMargin = Screen.dp(20f);
    if (Views.setGravity(input, Gravity.CENTER_VERTICAL | Lang.gravity()) && Views.setMargins(input, Lang.rtl() ? rightMargin : leftMargin, 0, Lang.rtl() ? leftMargin : rightMargin, 0)) {
      Views.updateLayoutParams(input);
    }
    invalidate();
  }

  @Override
  public void performDestroy () {
    receiver.requestFile(null);
  }

  @Override
  public void beforeTextChanged (CharSequence s, int start, int count, int after) { }

  @Override
  public void afterTextChanged (Editable s) { }

  @Override
  public void onTextChanged (CharSequence s, int start, int before, int count) {
    performReadyCallback(s.toString().trim().length() > 0);
  }

  private void onPhotoClicked () {
    if (input.isEnabled()) {
      Keyboard.hide(input);
      parent.tdlib().ui().showChangePhotoOptions(parent, file != null);
      ViewUtils.onClick(this);
    }
  }

  public interface ReadyCallback {
    void onReadyStateChanged (boolean ready);
  }

  private static final int FLAG_READY = 0x01;
  private static final int FLAG_IGNORE_READY = 0x02;
  private int flags;
  private ReadyCallback callback;

  private void performReadyCallback (boolean isReady) {
    if ((isReady && (flags & FLAG_READY) != 0) || !isReady && (flags & FLAG_READY) == 0) {
      return;
    }

    if (isReady) {
      flags |= FLAG_READY;
    } else {
      flags &= ~FLAG_READY;
    }

    if (callback != null && (flags & FLAG_IGNORE_READY) == 0) {
      callback.onReadyStateChanged(isReady);
    }
  }

  public void setReadyCallback (ReadyCallback callback) {
    this.callback = callback;
  }

  public boolean isInputEmpty () {
    return input.getText().toString().trim().length() == 0;
  }

  public String getInput () {
    return input.getText().toString();
  }

  public EditText getInputView () {
    return input;
  }

  public void setInput (String text) {
    if (text != null) {
      flags |= FLAG_IGNORE_READY;
      input.setText(text);
      Views.setSelection(input, text.length());
      flags &= ~FLAG_IGNORE_READY;
    }
  }

  public void setInputOptions (@StringRes int hintResId, int inputType) {
    if (hintResId != 0) {
      input.setHint(Lang.getString(hintResId));
    }
    if (inputType != 0) {
      input.setInputType(inputType);
    }
  }

  public void setNextField (int id) {
    input.setNextFocusDownId(id);
  }

  public void setImeOptions (int imeOptions) {
    input.setImeOptions(imeOptions);
  }

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
      layoutReceiver();
      scaleFactor = (1f - scaleFactor);
      if (scaleFactor == 0f) {
        setTranslationY(0f);
        input.setTranslationX(0f);
        input.setTranslationY(0f);
      } else {
        input.setTranslationX(scaleFactor * Screen.dp(20f));
        input.setTranslationY(scaleFactor * -Screen.dp(10f));
        setTranslationY(-Size.getHeaderPortraitSize() * scaleFactor);
      }
      invalidate();
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    layoutReceiver();
  }

  private int avatarRadius;

  private void layoutReceiver () {
    int margin = Screen.dp(36f);
    int paddingLeft = Screen.dp(4f);
    avatarRadius = Screen.dp(20.5f) + (int) ((float) Screen.dp(10f) * scaleFactor);
    int avatarSize = avatarRadius * 2;

    int avatarCenterXDiff = -Screen.dp(53.5f);
    int avatarLeft = paddingLeft + avatarRadius + margin + (int) ((float) avatarCenterXDiff * scaleFactor);
    if (Lang.rtl()) {
      avatarLeft = getMeasuredWidth() - avatarLeft - avatarSize;
    }

    receiver.setRadius(avatarRadius);
    receiver.setBounds(avatarLeft, 0, avatarLeft + avatarSize, avatarSize);
  }

  @Override
  protected void onDraw (Canvas c) {
    layoutReceiver();
    receiver.draw(c);
    int cx = receiver.centerX();
    int cy = receiver.centerY();
    c.drawCircle(cx, cy, avatarRadius, Paints.fillingPaint(0x20000000));
    Drawables.draw(c, icon, cx - (int) (icon.getMinimumWidth() * .5f), cy - (int) (icon.getMinimumHeight() * .5f), Paints.getPorterDuffPaint(0xffffffff));
  }

  public void setInputEnabled (boolean enabled) {
    input.setEnabled(enabled);
  }

  private ImageFile file;

  public void setPhoto (ImageFile file) {
    this.file = file;
    receiver.requestFile(file);
  }

  public boolean isPhotoChanged (boolean changedIfNull) {
    return (file == null && changedIfNull) || (file != null && file instanceof ImageFileLocal);
  }

  public String getPhoto () {
    return file == null || !(file instanceof ImageFileLocal) ? null : ((ImageFileLocal) file).getPath();
  }

  public ImageFile getImageFile () {
    return file;
  }

  private boolean caughtPhoto;
  private float cx, cy;

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    if (receiver != null) {
      receiver.attach();
    }
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    if (receiver != null) {
      receiver.detach();
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        float x = e.getX(), y = e.getY();
        if (receiver.isInsideReceiver(x, y)) {
          caughtPhoto = true;
          cx = x;
          cy = y;
          return true;
        } else {
          caughtPhoto = false;
        }
        break;
      }
      case MotionEvent.ACTION_MOVE: {
        if (caughtPhoto) {
          if (Math.max(Math.abs(e.getX() - cx), Math.abs(e.getY() - cy)) > Screen.getTouchSlop()) {
            caughtPhoto = false;
          }
        }
        break;
      }
      case MotionEvent.ACTION_UP: {
        if (caughtPhoto) {
          onPhotoClicked();
          caughtPhoto = false;
        }
        break;
      }
      case MotionEvent.ACTION_CANCEL: {
        caughtPhoto = false;
        break;
      }
    }
    return super.onTouchEvent(e);
  }
}
