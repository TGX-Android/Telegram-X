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
 * File created on 26/12/2016
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.chat.EncryptionKeyDrawable;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.CustomTypefaceSpan;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.ShadowView;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;

public class EncryptionKeyController extends ViewController<EncryptionKeyController.Args> implements TGLegacyManager.EmojiLoadListener {
  public static class Args {
    public long userId;
    public byte[] keyHash;

    public Args (long userId, byte[] keyHash) {
      this.userId = userId;
      this.keyHash = keyHash;
    }
  }

  public EncryptionKeyController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }
  private byte[] keyHash;

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    this.keyHash = args.keyHash;
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.EncryptionKey);
  }

  @Override
  protected View onCreateView (Context context) {
    final RelativeLayout contentView = new RelativeLayout(context);
    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    ViewSupport.setThemedBackground(contentView, ColorId.background, this);

    final FrameLayoutFix keyView = new FrameLayoutFix(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        int spec = MeasureSpec.makeMeasureSpec(Math.min(width, height), MeasureSpec.EXACTLY);
        super.onMeasure(spec, spec);
      }
    };
    keyView.setId(R.id.btn_encryptionKey);
    int padding = Screen.dp(12f);
    keyView.setPadding(padding, padding, padding, padding);
    ViewSupport.setThemedBackground(keyView, ColorId.filling, this);

    RelativeLayout.LayoutParams params;

    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
    params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
    keyView.setLayoutParams(params);

    final ImageView keyImageView = new ImageView(context);
    keyImageView.setScaleType(ImageView.ScaleType.FIT_XY);
    keyImageView.setImageDrawable(new EncryptionKeyDrawable(getArgumentsStrict().keyHash));
    keyImageView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    keyView.addView(keyImageView);

    contentView.addView(keyView);

    // Shadows

    ShadowView bottomShadow = new ShadowView(context);
    bottomShadow.setSimpleBottomTransparentShadow(true);
    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, bottomShadow.getLayoutParams().height);
    params.addRule(RelativeLayout.BELOW, R.id.btn_encryptionKey);
    bottomShadow.setLayoutParams(params);
    contentView.addView(bottomShadow);

    ShadowView rightShadow = new ShadowView(context);
    rightShadow.setSimpleRightShadow(true);
    params = new RelativeLayout.LayoutParams(rightShadow.getLayoutParams().width, ViewGroup.LayoutParams.MATCH_PARENT);
    params.addRule(RelativeLayout.RIGHT_OF, R.id.btn_encryptionKey);
    rightShadow.setLayoutParams(params);
    contentView.addView(rightShadow);

    CharSequence sequence = buildSequence();

    // Text

    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    params.addRule(RelativeLayout.BELOW, R.id.btn_encryptionKey);
    bottomText = new NoScrollTextView(context);
    bottomText.setGravity(Gravity.CENTER);
    bottomText.setPadding(padding, 0, padding, 0);
    bottomText.setTextColor(Theme.textDecent2Color());
    bottomText.setText(sequence);
    bottomText.setLayoutParams(params);
    contentView.addView(bottomText);

    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    params.addRule(RelativeLayout.RIGHT_OF, R.id.btn_encryptionKey);
    rightText = new NoScrollTextView(context);
    rightText.setGravity(Gravity.CENTER);
    rightText.setPadding(padding, padding, padding, padding);
    rightText.setText(sequence);
    rightText.setTextColor(Theme.textDecent2Color());
    rightText.setLayoutParams(params);
    contentView.addView(rightText);

    TGLegacyManager.instance().addEmojiListener(this);

    return contentView;
  }

  private TextView bottomText, rightText;

  @Override
  public void destroy () {
    super.destroy();
    TGLegacyManager.instance().removeEmojiListener(this);
  }

  @Override
  public void onEmojiUpdated (boolean isPackSwitch) {
    if (bottomText != null)
      bottomText.invalidate();
    if (rightText != null)
      rightText.invalidate();
  }

  private CharSequence buildSequence () {
    CharSequence text = Emoji.instance().replaceEmoji(Lang.getStringBold(R.string.EncryptionKeyDescription, tdlib.cache().userFirstName(getArgumentsStrict().userId)));

    SpannableStringBuilder b;
    if (text instanceof SpannableStringBuilder)
      b = (SpannableStringBuilder) text;
    else
      b = new SpannableStringBuilder(text);

    String hash = U.buildHex(keyHash);
    if (!StringUtils.isEmpty(hash)) {
      if (b.length() > 0)
        b.insert(0, "\n");
      b.insert(0, hash);
      CustomTypefaceSpan span = new CustomTypefaceSpan(Fonts.getRobotoMono(), ColorId.background_textLight);
      addThemeTextColorListener(span, ColorId.background_textLight);
      b.setSpan(span, 0, hash.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    return b;
  }

  @Override
  public int getId () {
    return R.id.controller_encryptionKey;
  }
}
