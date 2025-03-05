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
 * File created on 06/04/2017
 */
package org.thunderdog.challegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.animation.LayoutTransition;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import io.github.pytgcalls.AndroidUtils;
import io.github.pytgcalls.devices.JavaVideoCapturerModule;
import io.github.pytgcalls.media.SourceState;
import io.github.pytgcalls.media.StreamDevice;
import org.pytgcalls.ntgcallsx.VoIPFloatingLayout;
import org.pytgcalls.ntgcallsx.VoIPTextureView;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.charts.CubicBezierInterpolator;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.navigation.ActivityResultHandler;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.service.TGCallService;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.CustomTypefaceSpan;
import org.thunderdog.challegram.util.EmojiStatusHelper;
import org.thunderdog.challegram.util.RateLimiter;
import org.thunderdog.challegram.util.text.TextColorSetOverride;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.voip.annotation.CallState;
import org.thunderdog.challegram.voip.gui.CallSettings;
import org.thunderdog.challegram.widget.AvatarView;
import org.thunderdog.challegram.widget.EmojiTextView;
import org.thunderdog.challegram.widget.TextView;
import org.thunderdog.challegram.widget.voip.CallControlsLayout;
import org.webrtc.JavaI420Buffer;
import org.webrtc.RendererCommon;
import org.webrtc.TextureViewRenderer;
import org.webrtc.VideoFrame;

import java.util.ArrayList;
import java.util.function.Function;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ScrimUtil;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ViewHandler;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;

public class CallController extends ViewController<CallController.Arguments> implements TdlibCache.UserDataChangeListener, TdlibCache.CallStateChangeListener, View.OnClickListener, FactorAnimator.Target, Runnable, CallControlsLayout.CallControlCallback, ActivityResultHandler {
  private static final boolean DEBUG_FADE_BRANDING = true;
  private static final int SCREEN_CAPTURE_REQUEST_CODE = 1001;

  private static class ButtonView extends View implements FactorAnimator.Target {
    private Drawable icon;
    private float factor;
    private boolean needCross;

    public ButtonView (Context context) {
      super(context);
    }

    public void setIcon (@DrawableRes int icon) {
      this.icon = Drawables.get(icon);
    }

    public void setNeedCross (boolean needCross) {
      this.needCross = needCross;
    }

    public boolean toggleActive () {
      setIsActive(!isActive, true);
      return isActive;
    }

    private boolean isActive;

    public void setIsActive (boolean isActive, boolean animated) {
      if (this.isActive != isActive) {
        this.isActive = isActive;
        if (animated) {
          animateFactor(isActive ? 1f : 0f);
        } else {
          forceFactor(isActive ? 1f : 0f);
        }
      }
    }

    @Override
    public boolean onTouchEvent (MotionEvent event) {
      return getParent() != null && ((View) getParent()).getAlpha() == 1f && super.onTouchEvent(event);
    }

    private FactorAnimator animator;

    private void animateFactor (float toFactor) {
      if (animator == null) {
        animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.factor);
      }
      animator.animateTo(toFactor);
    }

    private void forceFactor (float toFactor) {
      if (animator != null) {
        animator.forceFactor(toFactor);
      }
      setFactor(toFactor);
    }

    private void setFactor (float factor) {
      if (this.factor != factor) {
        this.factor = factor;
        invalidate();
      }
    }

    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      setFactor(factor);
    }

    @Override
    public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

    @Override
    protected void onDraw (Canvas c) {
      if (icon == null) {
        return;
      }
      float cx = getMeasuredWidth() / 2;
      float cy = getMeasuredHeight() / 2;
      int backgroundColor = ColorUtils.fromToArgb(0x00ffffff, 0xffffffff, factor);
      if (factor != 0f) {
        c.drawCircle(cx, cy, Screen.dp(18f), Paints.fillingPaint(backgroundColor));
      }
      int iconColor = ColorUtils.fromToArgb(0xffffffff, 0xff000000, factor);
      Drawables.draw(c, icon, cx - icon.getMinimumWidth() / 2, cy - icon.getMinimumHeight() / 2, Paints.getPorterDuffPaint(iconColor));
      if (factor != 0f && needCross) {
        DrawAlgorithms.drawCross(c, cx, cy, factor, iconColor, backgroundColor);
      }
    }
  }

  @Override
  protected boolean useDropShadow () {
    return false;
  }

  public static class Arguments {
    private TdApi.Call call;

    public Arguments (TdApi.Call call) {
      this.call = call;
    }
  }

  public CallController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private TdApi.Call call;
  private @Nullable TdApi.User user;
  private CallSettings callSettings;
  private boolean hadEmojiSinceStart;

  @Override
  public void setArguments (Arguments args) {
    super.setArguments(args);
    this.call = args.call;
    setCallBarsCount(tdlib.context().calls().getCallBarsCount(tdlib, call.id));
    this.hadEmojiSinceStart = call.state.getConstructor() == TdApi.CallStateReady.CONSTRUCTOR;
    this.user = tdlib.cache().user(call.userId);
  }

  @Override
  public int getId () {
    return R.id.controller_call;
  }

  private AvatarView avatarView;
  private TextView nameView, stateView;
  private EmojiStatusHelper emojiStatusHelper;
  private float nameTextWidth;
  private TextPaint nameTextPaint;
  private LinearLayout brandWrap;
  private TextView debugView;
  private CallStrengthView strengthView;
  private TextureViewRenderer callingUserMiniTextureRenderer;
  private VoIPFloatingLayout callingUserMiniFloatingLayout, currentUserCameraFloatingLayout;
  private VoIPTextureView currentUserTextureView, callingUserTextureView;

  private static class CallStrengthView extends View {
    private final ViewHandler handler;

    public CallStrengthView (Context context) {
      super(context);
      handler = new ViewHandler();
    }

    private int barCount = -1;
    private long lastUpdateTime;

    private static final long MAX_FREQUENCY_MS = 1000l;

    public void setBarsCount (int callStrength) {
      boolean changed = Math.max(this.barCount, 0) != Math.max(callStrength, 0);
      this.barCount = callStrength;
      if (changed) {
        long now = SystemClock.elapsedRealtime();

        if (lastUpdateTime == 0 || now - lastUpdateTime >= MAX_FREQUENCY_MS) {
          lastUpdateTime = now;
          handler.cancelInvalidate(this);
          invalidate();
        } else {
          handler.invalidate(this, MAX_FREQUENCY_MS - (now - lastUpdateTime));
        }
      }
    }

    private static final int MAX_BARS_COUNT = 4;

    @Override
    protected void onDraw (Canvas c) {
      int size = Screen.dp(3f);
      int spacing = Screen.dp(1f);
      int totalSize = size * MAX_BARS_COUNT + spacing * (MAX_BARS_COUNT - 1);
      int startX = getMeasuredWidth() / 2 - totalSize / 2;
      int startY = getMeasuredHeight() / 2 + size * 2;
      int cx = startX;
      for (int i = 0; i < 4; i++) {
        RectF rectF = Paints.getRectF();
        rectF.set(cx, startY - size * (i + 1), cx + size, startY);
        c.drawRoundRect(rectF, spacing, spacing, Paints.fillingPaint(barCount > i ? 0xffffffff : 0x7fffffff));
        cx += size + spacing;
      }
    }
  }

  private TextView emojiViewSmall, emojiViewBig, emojiViewHint;
  private CallControlsLayout callControlsLayout;

  private LinearLayout buttonWrap;
  private ButtonView muteButtonView, speakerButtonView, videoButtonView, flipCameraButtonView;
  private LinearLayout videoButtonContainer, flipCameraButtonContainer, messageButtonContainer, otherOptionsContainer, speakerButtonContainer;

  private float lastHeaderFactor;

  @Override
  protected void applyCustomHeaderAnimations (float factor) {
    if (lastHeaderFactor != factor) {
      lastHeaderFactor = factor;
      updateControlsAlpha();
      updateEmojiFactors();
      if (DEBUG_FADE_BRANDING) {
        brandWrap.setAlpha(factor);
      }
      avatarView.invalidate();
    }
  }

  private BoolAnimator strengthAnimator;
  private static final int ANIMATOR_STRENGTH = 6;

  private void updateCallStrength () {
    boolean isVisible = callStrength >= 0 && call != null && call.state.getConstructor() == TdApi.CallStateReady.CONSTRUCTOR && callDuration >= 0;
    if (!isVisible == (strengthAnimator != null && strengthAnimator.getValue())) {
      if (strengthAnimator == null) {
        strengthAnimator = new BoolAnimator(ANIMATOR_STRENGTH, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
      }
      strengthAnimator.setValue(isVisible, strengthView != null && lastHeaderFactor > 0f);
    }
  }

  private int callStrength = -1;

  private void setCallBarsCount (int count) {
    if (this.callStrength != count) {
      this.callStrength = count;
      if (strengthView != null) {
        strengthView.setBarsCount(count);
      }
      updateCallStrength();
    }
  }

  @Override
  protected View onCreateView (final Context context) {
    final FrameLayoutFix contentView = new FrameLayoutFix(context) {
      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        updateEmojiPosition();
      }

      @Override
      protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updateEmojiPosition();
      }
    };
    ViewSupport.setThemedBackground(contentView, ColorId.headerBackground, this);

    avatarView = new AvatarView(context) {
      private final Drawable topShadow = ScrimUtil.makeCubicGradientScrimDrawable(0xff000000, 2, Gravity.TOP, false);

      @Override
      protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = getMeasuredWidth();
        int shadowSize = Screen.dp(212f);
        if (topShadow.getBounds().right != width || topShadow.getBounds().bottom != shadowSize) {
          topShadow.setBounds(0, 0, width, shadowSize);
        }
      }

      @Override
      public boolean onTouchEvent (MotionEvent event) {
        super.onTouchEvent(event);
        switch (event.getAction()) {
          case MotionEvent.ACTION_DOWN:
            return true;
          case MotionEvent.ACTION_UP:
            if (emojiExpandFactor == 1f && isEmojiExpanded) {
              setEmojiExpanded(false);
            }
            return true;
        }
        return false;
      }

      @Override
      protected void onDraw(Canvas c){
        super.onDraw(c);
        int alpha = (int) (255f * lastHeaderFactor * .5f);
        Drawables.setAlpha(topShadow, alpha);
        topShadow.draw(c);
      }
    };
    avatarView.setNoRound(true);
    avatarView.setNoPlaceholders(true);
    avatarView.setNeedFull(true);
    avatarView.setUser(tdlib, user, false);
    avatarView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    contentView.addView(avatarView);

    callingUserTextureView = new VoIPTextureView(context, false, true, false);
    callingUserTextureView.renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
    callingUserTextureView.renderer.setEnableHardwareScaler(true);
    callingUserTextureView.renderer.setRotateTextureWithScreen(true);
    callingUserTextureView.scaleType = VoIPTextureView.SCALE_TYPE_FIT;
    callingUserTextureView.setVisibility(View.GONE);
    contentView.addView(callingUserTextureView);

    currentUserCameraFloatingLayout = new VoIPFloatingLayout(context);
    currentUserCameraFloatingLayout.setDelegate((progress, value) -> currentUserTextureView.setScreenShareMiniProgress(progress, value));
    currentUserCameraFloatingLayout.setRelativePosition(1f, 1f);
    currentUserCameraFloatingLayout.setVisibility(View.GONE);
    currentUserCameraFloatingLayout.setTag(VoIPFloatingLayout.STATE_GONE);
    currentUserCameraFloatingLayout.setOnTapListener(view -> {
      if (callSettings == null) {
        callSettings = new CallSettings(tdlib, call.id);
      }
      if (callSettings.getRemoteCameraState() != VoIPFloatingLayout.STATE_GONE && callSettings.getLocalCameraState() == VoIPFloatingLayout.STATE_FLOATING) {
        callSettings.setLocalCameraState(VoIPFloatingLayout.STATE_FULLSCREEN);
        callSettings.setRemoteCameraState(VoIPFloatingLayout.STATE_FLOATING);
        currentUserCameraFloatingLayout.saveRelativePosition();
        callingUserMiniFloatingLayout.saveRelativePosition();
        callingUserMiniFloatingLayout.setRelativePosition(currentUserCameraFloatingLayout);
        showFloatingLayout(true);
        showMiniFloatingLayout(true);
        currentUserCameraFloatingLayout.restoreRelativePosition();
        callingUserMiniFloatingLayout.restoreRelativePosition();
      }
    });

    currentUserTextureView = new VoIPTextureView(context, true, false);
    currentUserTextureView.renderer.setUseCameraRotation(true);
    currentUserCameraFloatingLayout.addView(currentUserTextureView);
    contentView.addView(currentUserCameraFloatingLayout, FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    callingUserMiniFloatingLayout = new VoIPFloatingLayout(context);
    callingUserMiniFloatingLayout.alwaysFloating = true;
    callingUserMiniFloatingLayout.setRelativePosition(1f, 1f);
    callingUserMiniFloatingLayout.setFloatingMode(true, false);
    callingUserMiniFloatingLayout.setVisibility(View.GONE);
    callingUserMiniFloatingLayout.setOnTapListener(view -> {
      if (callSettings == null) {
        callSettings = new CallSettings(tdlib, call.id);
      }
      if (callSettings.getRemoteCameraState() != VoIPFloatingLayout.STATE_GONE && callSettings.getLocalCameraState() == VoIPFloatingLayout.STATE_FULLSCREEN) {
        callSettings.setLocalCameraState(VoIPFloatingLayout.STATE_FLOATING);
        callSettings.setRemoteCameraState(VoIPFloatingLayout.STATE_FULLSCREEN);
        currentUserCameraFloatingLayout.saveRelativePosition();
        callingUserMiniFloatingLayout.saveRelativePosition();
        currentUserCameraFloatingLayout.setRelativePosition(callingUserMiniFloatingLayout);
        showFloatingLayout(true);
        showMiniFloatingLayout(true);
        currentUserCameraFloatingLayout.restoreRelativePosition();
        callingUserMiniFloatingLayout.restoreRelativePosition();
      }
    });

    callingUserMiniTextureRenderer = new TextureViewRenderer(context);
    callingUserMiniTextureRenderer.setEnableHardwareScaler(true);
    callingUserMiniTextureRenderer.setFpsReduction(30);
    callingUserMiniTextureRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);

    View backgroundView = new View(context);
    backgroundView.setBackgroundColor(0xff1b1f23);
    callingUserMiniFloatingLayout.addView(backgroundView, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    callingUserMiniFloatingLayout.addView(callingUserMiniTextureRenderer, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

    contentView.addView(callingUserMiniFloatingLayout);
    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

    // Top-left corner

    params.topMargin = Screen.dp(76f);
    params.leftMargin = params.rightMargin = Screen.dp(18f);

    nameView = new EmojiTextView(context) {
      @Override
      protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        nameTextWidth = U.measureText(TD.getUserName(user), nameTextPaint);
        if (nameTextWidth > getMeasuredWidth() - getPaddingRight()) {
          CharSequence text = getText().subSequence(0, getLayout().getEllipsisStart(0)) + "...";
          nameTextWidth = U.measureText(text, nameTextPaint);
        }
      }

      @Override
      protected void onDraw (Canvas canvas) {
        super.onDraw(canvas);
        emojiStatusHelper.draw(canvas, (int) Math.min(getMeasuredWidth() - emojiStatusHelper.getWidth(0), nameTextWidth + Screen.dp(7)), Screen.dp(9));
      }
    };
    nameView.setScrollDisabled(true);
    nameView.setSingleLine(true);
    nameView.setTextColor(0xffffffff);
    nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 40);
    nameView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
    Views.setSimpleShadow(nameView);
    nameView.setEllipsize(TextUtils.TruncateAt.END);
    nameView.setLayoutParams(params);
    contentView.addView(nameView);

    nameTextPaint = new TextPaint();
    nameTextPaint.setTextSize(Screen.dp(40));
    nameTextPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
    emojiStatusHelper = new EmojiStatusHelper(tdlib, nameView, null);

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.topMargin = Screen.dp(136f);
    params.leftMargin = params.rightMargin = Screen.dp(18f);

    stateView = new TextView(context);
    stateView.setScrollDisabled(true);
    // stateView.setSingleLine(true);
    stateView.setMaxLines(2);
    stateView.setLineSpacing(Screen.dp(3f), 1f);
    stateView.setTextColor(0xffffffff);
    stateView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
    stateView.setTypeface(Fonts.getRobotoRegular());
    Views.setSimpleShadow(stateView);
    // stateView.setEllipsize(TextUtils.TruncateAt.END);
    stateView.setLayoutParams(params);
    contentView.addView(stateView);

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.topMargin = Screen.dp(42f);
    params.leftMargin = params.rightMargin = Screen.dp(18f);
    brandWrap = new LinearLayout(context);
    if (DEBUG_FADE_BRANDING) {
      brandWrap.setAlpha(0f);
    }
    brandWrap.setOrientation(LinearLayout.HORIZONTAL);
    brandWrap.setLayoutParams(params);
    contentView.addView(brandWrap);

    LinearLayout.LayoutParams lp;

    lp = new LinearLayout.LayoutParams(Screen.dp(14f), Screen.dp(14f));
    lp.topMargin = Screen.dp(2f);

    ImageView brandIcon = new ImageView(context);
    brandIcon.setScaleType(ImageView.ScaleType.CENTER);
    brandIcon.setImageResource(R.drawable.deproko_logo_telegram_18);
    brandIcon.setLayoutParams(lp);
    brandWrap.addView(brandIcon);

    lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.leftMargin = Screen.dp(9f);

    TextView brandView = new TextView(context);
    brandView.setScrollDisabled(true);
    brandView.setSingleLine(true);
    brandView.setTextColor(0xffffffff);
    brandView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
    brandView.setTypeface(Fonts.getRobotoRegular());
    Views.setSimpleShadow(brandView);
    brandView.setEllipsize(TextUtils.TruncateAt.END);
    brandView.setLayoutParams(lp);
    brandView.setText(Lang.getString(R.string.VoipBranding).toUpperCase());
    if (Log.checkLogLevel(Log.LEVEL_INFO) || BuildConfig.EXPERIMENTAL) {
      brandView.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick (View v) {
          TGCallService.markLogViewed();
          if (debugView != null) {
            contentView.removeView(debugView);
            debugView = null;
          } else {
            final TextView view = new TextView(context);
            view.setScrollDisabled(true);
            view.setBackgroundColor(0xaaffffff);
            view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setTextColor(0xff000000);
            view.setPadding(Screen.dp(16f), Screen.dp(16f), Screen.dp(16f), Screen.dp(16f));
            view.post(new Runnable() {
              @Override
              public void run () {
                TGCallService service = TGCallService.currentInstance();

                SpannableStringBuilder b = new SpannableStringBuilder();
                if (service != null) {
                  b.append(service.getLibraryNameAndVersion());
                } else {
                  b.append("service unavailable");
                }
                b.setSpan(new CustomTypefaceSpan(Fonts.getRobotoBold(), 0), 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (service != null) {
                  CharSequence log = service.getDebugString();
                  if (!StringUtils.isEmpty(log)) {
                    b.append("\n\n");
                    b.append(log);
                  }
                }
                view.setText(b);
                if (view.getParent() != null) {
                  view.postDelayed(this, 500l);
                }
              }
            });
            debugView = view;
            contentView.addView(debugView);
          }
        }
      });
    }
    brandWrap.addView(brandView);

    lp = new LinearLayout.LayoutParams(Screen.dp(18f), Screen.dp(18f));
    // lp.topMargin = Screen.dp(2f);
    lp.leftMargin = Screen.dp(8f);
    strengthView = new CallStrengthView(context);
    strengthView.setLayoutParams(lp);
    if (callStrength < 0) {
      strengthView.setAlpha(0f);
    }
    strengthView.setBarsCount(callStrength);
    brandWrap.addView(strengthView);

    // Emoji corner

    emojiViewSmall = new EmojiTextView(context) {
      @Override
      public boolean onTouchEvent (MotionEvent event) {
        return (event.getAction() != MotionEvent.ACTION_DOWN || emojiExpandFactor == 0f) && super.onTouchEvent(event);
      }
    };
    emojiViewSmall.setScrollDisabled(true);
    emojiViewSmall.setSingleLine(true);
    emojiViewSmall.setTextColor(0xffffffff);
    emojiViewSmall.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
    emojiViewSmall.setTypeface(Fonts.getRobotoRegular());
    Views.setSimpleShadow(emojiViewSmall);
    emojiViewSmall.setEllipsize(TextUtils.TruncateAt.END);
    emojiViewSmall.setPadding(Screen.dp(18f), Screen.dp(18f), Screen.dp(18f), Screen.dp(18f));
    emojiViewSmall.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
    emojiViewSmall.setOnClickListener(this);
    emojiViewSmall.setId(R.id.btn_emoji);
    contentView.addView(emojiViewSmall);

    emojiViewBig = new EmojiTextView(context);
    emojiViewBig.setScrollDisabled(true);
    emojiViewBig.setSingleLine(true);
    emojiViewBig.setScaleX(1f / EMOJI_EXPAND_FACTOR);
    emojiViewBig.setScaleY(1f / EMOJI_EXPAND_FACTOR);
    emojiViewBig.setAlpha(0f);
    emojiViewBig.setTextColor(0xffffffff);
    int emojiBigSize = (int) (16f * DESIRED_EMOJI_EXPAND_FACTOR);
    EMOJI_EXPAND_FACTOR = (float) emojiBigSize / 16f;
    emojiViewBig.setTextSize(TypedValue.COMPLEX_UNIT_DIP, emojiBigSize);
    emojiViewBig.setTypeface(Fonts.getRobotoRegular());
    Views.setSimpleShadow(emojiViewBig);
    emojiViewBig.setEllipsize(TextUtils.TruncateAt.END);
    emojiViewBig.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
    contentView.addView(emojiViewBig);

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL);
    params.topMargin = Screen.dp(24f) * 2;
    params.rightMargin = params.leftMargin = Screen.dp(48f);

    emojiViewHint = new EmojiTextView(context);
    emojiViewHint.setScrollDisabled(true);
    emojiViewHint.setAlpha(0f);
    emojiViewHint.setTextColor(0xffffffff);
    emojiViewHint.setGravity(Gravity.CENTER);
    emojiViewHint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
    emojiViewHint.setTypeface(Fonts.getRobotoRegular());
    Views.setSimpleShadow(emojiViewHint);
    emojiViewHint.setLayoutParams(params);
    contentView.addView(emojiViewHint);

    // Call settings buttons

    LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(Screen.dp(72f), Screen.dp(72f));

    ButtonView otherOptions = new ButtonView(context);
    otherOptions.setId(R.id.btn_other_options);
    otherOptions.setOnClickListener(this);
    otherOptions.setIcon(R.drawable.baseline_more_vert_24);
    otherOptions.setLayoutParams(buttonParams);

    muteButtonView = new ButtonView(context);
    muteButtonView.setId(R.id.btn_mute);
    muteButtonView.setOnClickListener(this);
    muteButtonView.setIcon(R.drawable.baseline_mic_24);
    muteButtonView.setNeedCross(true);
    muteButtonView.setLayoutParams(buttonParams);

    ButtonView messageButtonView = new ButtonView(context);
    messageButtonView.setId(R.id.btn_openChat);
    messageButtonView.setOnClickListener(this);
    messageButtonView.setVisibility(View.VISIBLE);
    messageButtonView.setIcon(R.drawable.baseline_chat_bubble_24);
    messageButtonView.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(72f), Screen.dp(72f), Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM));

    muteButtonView = new ButtonView(context);
    muteButtonView.setId(R.id.btn_mute);
    muteButtonView.setOnClickListener(this);
    muteButtonView.setIcon(R.drawable.baseline_mic_24);
    muteButtonView.setNeedCross(true);
    muteButtonView.setLayoutParams(buttonParams);

    speakerButtonView = new ButtonView(context);
    speakerButtonView.setId(R.id.btn_speaker);
    speakerButtonView.setOnClickListener(this);
    speakerButtonView.setIcon(R.drawable.baseline_volume_up_24);
    speakerButtonView.setLayoutParams(buttonParams);

    videoButtonView = new ButtonView(context);
    videoButtonView.setId(R.id.btn_camera);
    videoButtonView.setOnClickListener(this);
    videoButtonView.setIcon(R.drawable.baseline_videocam_24);
    videoButtonView.setLayoutParams(buttonParams);

    flipCameraButtonView = new ButtonView(context);
    flipCameraButtonView.setId(R.id.btn_flip_camera);
    flipCameraButtonView.setOnClickListener(this);
    flipCameraButtonView.setIcon(R.drawable.baseline_camera_front_24);
    flipCameraButtonView.setLayoutParams(buttonParams);

    Function<ButtonView, LinearLayout> wrapButton = (ButtonView buttonView) -> {
      LinearLayout buttonWrap = new LinearLayout(context);
      buttonWrap.setOrientation(LinearLayout.HORIZONTAL);
      buttonWrap.setGravity(Gravity.CENTER);
      buttonWrap.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
      buttonWrap.addView(buttonView);
      return buttonWrap;
    };

    videoButtonContainer = wrapButton.apply(videoButtonView);
    videoButtonContainer.setVisibility(View.GONE);

    messageButtonContainer = wrapButton.apply(messageButtonView);
    otherOptionsContainer = wrapButton.apply(otherOptions);
    otherOptionsContainer.setVisibility(View.GONE);

    flipCameraButtonContainer = wrapButton.apply(flipCameraButtonView);
    flipCameraButtonContainer.setVisibility(View.GONE);

    speakerButtonContainer = wrapButton.apply(speakerButtonView);

    buttonWrap = new LinearLayout(context);
    LayoutTransition layoutTransition = new LayoutTransition();
    layoutTransition.enableTransitionType(LayoutTransition.APPEARING);
    layoutTransition.enableTransitionType(LayoutTransition.DISAPPEARING);
    layoutTransition.setDuration(LayoutTransition.APPEARING, 300);
    layoutTransition.setDuration(LayoutTransition.DISAPPEARING, 300);
    buttonWrap.setLayoutTransition(layoutTransition);

    buttonWrap.setGravity(Gravity.CENTER);
    buttonWrap.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(76f), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
    buttonWrap.addView(otherOptionsContainer);
    buttonWrap.addView(videoButtonContainer);
    buttonWrap.addView(flipCameraButtonContainer);
    buttonWrap.addView(speakerButtonContainer);
    buttonWrap.addView(messageButtonContainer);
    buttonWrap.addView(wrapButton.apply(muteButtonView));
    Drawable drawable = ScrimUtil.makeCubicGradientScrimDrawable(0xff000000, 2, Gravity.BOTTOM, false);
    drawable.setAlpha((int) (255f * .3f));
    ViewUtils.setBackground(buttonWrap, drawable);
    contentView.addView(buttonWrap);

    // Answer controls

    callControlsLayout = new CallControlsLayout(context, this);
    callControlsLayout.setCallback(this);
    callControlsLayout.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    contentView.addView(callControlsLayout);
    callControlsLayout.setCall(tdlib, call, false);

    // Data

    tdlib.cache().subscribeToCallUpdates(call.id, this);
    tdlib.cache().addUserDataListener(call.userId, this);

    this.callSettings = tdlib.cache().getCallSettings(call.id);

    setTexts();
    updateCallState();

    if (callSettings != null) {
      muteButtonView.setIsActive(callSettings.isMicMuted(), false);
      speakerButtonView.setIsActive(callSettings.isSpeakerModeEnabled(), false);
      if (callSettings.isScreenSharing()) {
        videoButtonView.setIcon(R.drawable.baseline_share_arrow_24);
      }
      videoButtonView.setIsActive((callSettings.getLocalCameraState() != VoIPFloatingLayout.STATE_GONE || callSettings.isScreenSharing()), false);
      flipCameraButtonView.setIsActive(!callSettings.isCameraFrontFacing(), false);
      flipCameraButtonView.setIcon(callSettings != null && !callSettings.isCameraFrontFacing() ? R.drawable.baseline_camera_rear_24 : R.drawable.baseline_camera_front_24);
      if (callSettings.getLocalCameraState() != VoIPFloatingLayout.STATE_GONE) {
        currentUserTextureView.renderer.init(JavaVideoCapturerModule.getSharedEGLContext(), null);
      }
      if (callSettings.isCameraSharing()) {
        speakerButtonContainer.setVisibility(View.GONE);
        flipCameraButtonContainer.setVisibility(View.VISIBLE);
      }
      if (callSettings.getRemoteCameraState() != VoIPFloatingLayout.STATE_GONE) {
        callingUserTextureView.renderer.init(JavaVideoCapturerModule.getSharedEGLContext(), null);
        callingUserMiniTextureRenderer.init(JavaVideoCapturerModule.getSharedEGLContext(), null);
        callingUserTextureView.setVisibility(View.VISIBLE);
      }
      currentUserTextureView.renderer.setMirror(callSettings.isCameraFrontFacing() && callSettings.isCameraSharing());
      currentUserTextureView.setIsCamera(callSettings.isCameraSharing());
      currentUserTextureView.setIsScreencast(callSettings.isScreenSharing());
      showFloatingLayout(false);
      showMiniFloatingLayout(false);
    }

    return contentView;
  }

  private void setTexts () {
    if (emojiStatusHelper != null) {
      this.emojiStatusHelper.updateEmoji(tdlib, user, new TextColorSetOverride(TextColorSets.Regular.NORMAL) {
        @Override
        public long mediaTextComplexColor () {
          return Theme.newComplexColor(true, ColorId.white);
        }
      }, R.drawable.baseline_premium_star_28, 32);
    }
    if (nameView != null) {
      this.nameView.setText(TD.getUserName(user));
      this.nameView.setPadding(0, 0, user != null && user.isPremium ? emojiStatusHelper.getWidth(Screen.dp(7)) : 0, 0);
      this.nameView.requestLayout();
    }
    if (emojiViewHint != null)
      this.emojiViewHint.setText(Lang.getString(R.string.CallEmojiHint, TD.getUserSingleName(call.userId, user)));
  }

  @Override
  public void onCallAccept (TdApi.Call call) {
    tdlib.context().calls().acceptCall(context(), tdlib, call.id);
  }

  @Override
  public void onCallDecline (TdApi.Call call, boolean isHangUp) {
    tdlib.context().calls().hangUp(tdlib, call.id);
  }

  @Override
  public void onCallRestart (TdApi.Call call) {
    tdlib.context().calls().makeCall(this, call.userId, null);
  }

  public boolean compareUserId (long userId) {
    return call.userId == userId;
  }

  @Override
  public void onCallClose (TdApi.Call call) {
    closeCall();
  }

  @Override
  public void onPrepareToShow () {
    super.onPrepareToShow();
    if (!UI.isTablet()) {
      context().setOrientation(BaseActivity.getAndroidOrientationPortrait());
    }
  }

  @Override
  public void onCleanAfterHide () {
    super.onCleanAfterHide();
    if (!UI.isTablet()) {
      context().setOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }
  }

  private void updateLoop () {
    setIsLooping(!isDestroyed() && call.state.getConstructor() == TdApi.CallStateReady.CONSTRUCTOR);
  }

  private boolean isLooping;

  private void setIsLooping (boolean isLooping) {
    if (this.isLooping != isLooping) {
      this.isLooping = isLooping;
      if (isLooping) {
        UI.post(this);
      } else {
        UI.removePendingRunnable(this);
      }
    }
  }

  @Override
  public void run () {
    if (!isDestroyed()) {
      updateCallState();
      if (isLooping) {
        UI.post(this, tdlib.context().calls().getTimeTillNextCallDurationUpdate(tdlib, call.id));
      }
    }
  }

  private boolean buttonsVisible;
  private FactorAnimator buttonsAnimator;
  private static final int ANIMATOR_BUTTONS_ID = 0;
  private float buttonsFactor;

  private void setButtonsVisible (boolean areVisible, boolean animated) {
    if (this.buttonsVisible != areVisible) {
      this.buttonsVisible = areVisible;
      if (animated) {
        if (buttonsAnimator == null) {
          buttonsAnimator = new FactorAnimator(ANIMATOR_BUTTONS_ID, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.buttonsFactor);
        }
        buttonsAnimator.animateTo(areVisible ? 1f : 0f);
      } else {
        if (buttonsAnimator != null) {
          buttonsAnimator.forceFactor(areVisible ? 1f : 0f);
        }
        setButtonsFactor(areVisible ? 1f : 0f);
      }
    }
  }

  private void setButtonsFactor (float factor) {
    this.buttonsFactor = factor;
    updateControlsAlpha();
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_BUTTONS_ID: {
        setButtonsFactor(factor);
        break;
      }
      case ANIMATOR_FLASH_ID: {
        stateView.setAlpha(factor <= .5f ? 1f - (factor / .5f) : (factor - .5f) / .5f);
        break;
      }
      case ANIMATOR_EMOJI_VISIBILITY_ID: {
        setEmojiVisibilityFactor(factor);
        break;
      }
      case ANIMATOR_EMOJI_EXPAND_ID: {
        setEmojiExpandFactor(Math.max(0f, factor));
        break;
      }
      case ANIMATOR_STRENGTH: {
        if (strengthView != null) {
          strengthView.setAlpha(factor);
        }
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_FLASH_ID: {
        if (finalFactor == 1f) {
          flashLimiter.run();
        }
        break;
      }
    }
  }

  @Override
  public void onClick (View v) {
    final int viewId = v.getId();
    handleMenuClick(viewId);
  }

  public void handleMenuClick (int viewId) {
    if (viewId == R.id.btn_emoji) {
      if (isEmojiVisible) {
        setEmojiExpanded(true);
      }
    } else if (viewId == R.id.btn_mute) {
      if (!TD.isFinished(call)) {
        if (callSettings == null) {
          callSettings = new CallSettings(tdlib, call.id);
        }
        callSettings.setMicMuted(!callSettings.isMicMuted());
      }
    } else if (viewId == R.id.btn_speaker) {
      if (!TD.isFinished(call)) {
        if (callSettings == null) {
          callSettings = new CallSettings(tdlib, call.id);
        }
        if (callSettings.isSpeakerModeEnabled()) {
          callSettings.setSpeakerMode(CallSettings.SPEAKER_MODE_NONE);
        } else {
          callSettings.toggleSpeakerMode(this);
        }
      }
    } else if (viewId == R.id.btn_camera) {
      if (!TD.isFinished(call)) {
        if (callSettings == null) {
          callSettings = new CallSettings(tdlib, call.id);
        }
        callSettings.setLocalCameraState(callSettings.getLocalCameraState() == VoIPFloatingLayout.STATE_GONE ? callSettings.getAvailableLocalCameraState(): VoIPFloatingLayout.STATE_GONE);
        if (callSettings.getLocalCameraState() != VoIPFloatingLayout.STATE_GONE) {
          currentUserTextureView.renderer.setMirror(callSettings.isCameraFrontFacing());
          currentUserTextureView.setIsCamera(true);
          currentUserTextureView.setIsScreencast(false);
          currentUserTextureView.renderer.init(JavaVideoCapturerModule.getSharedEGLContext(), null);
        } else {
          currentUserTextureView.stopCapturing();
        }

        if (callSettings.isScreenSharing()) {
          callSettings.setScreenSharing(false);
          videoButtonView.setIcon(R.drawable.baseline_videocam_24);
          otherOptionsContainer.setVisibility(View.VISIBLE);
          messageButtonContainer.setVisibility(View.GONE);
        } else {
          callSettings.setCameraSharing(!callSettings.isCameraSharing());
          flipCameraButtonContainer.setVisibility(callSettings.isCameraSharing() ? View.VISIBLE : View.GONE);
          speakerButtonContainer.setVisibility(callSettings.isCameraSharing() ? View.GONE : View.VISIBLE);
        }
        showFloatingLayout(true);
      }
    } else if (viewId == R.id.btn_other_options) {
      if (navigationController != null) {
        if (callSettings == null) {
          callSettings = new CallSettings(tdlib, call.id);
        }
        ArrayList<Integer> ids = new ArrayList<>();
        ArrayList<String> titles = new ArrayList<>();
        ArrayList<Integer> icons = new ArrayList<>();
        if (canShowScreenSharing()) {
          ids.add(R.id.btn_screenCapture);
          titles.add("Screen Sharing");
          icons.add(R.drawable.baseline_share_arrow_24);
        }
        ids.add(R.id.btn_openChat);
        titles.add("Send Message");
        icons.add(R.drawable.baseline_chat_bubble_24);
        if (callSettings.isCameraSharing()) {
          ids.add(R.id.btn_speaker);
          switch (callSettings.getSpeakerMode()) {
            case CallSettings.SPEAKER_MODE_NONE:
              titles.add(Lang.getString(R.string.VoipAudioRoutingEarpiece));
              icons.add(R.drawable.baseline_phone_in_talk_24);
              break;
            case CallSettings.SPEAKER_MODE_SPEAKER:
            case CallSettings.SPEAKER_MODE_SPEAKER_DEFAULT:
              titles.add(Lang.getString(R.string.VoipAudioRoutingSpeaker));
              icons.add(R.drawable.baseline_volume_up_24);
              break;
            case CallSettings.SPEAKER_MODE_BLUETOOTH:
              titles.add(Lang.getString(R.string.VoipAudioRoutingBluetooth));
              icons.add(R.drawable.baseline_bluetooth_24);
              break;
          }
        }
        showOptions(null, ids.stream().mapToInt(Integer::intValue).toArray(), titles.toArray(new String[0]), null, icons.stream().mapToInt(Integer::intValue).toArray(), (itemView, id) -> {
          handleMenuClick(id);
          return true;
        });
      }
    } else if (viewId == R.id.btn_openChat) {
      tdlib.ui().openPrivateChat(this, call.userId, null);
    } else if (viewId == R.id.btn_flip_camera) {
      if (!TD.isFinished(call)) {
        if (callSettings == null) {
          callSettings = new CallSettings(tdlib, call.id);
        }
        callSettings.setCameraFrontFacing(!callSettings.isCameraFrontFacing());
        currentUserTextureView.showWaitFrame();
        currentUserTextureView.renderer.setMirror(callSettings.isCameraFrontFacing());
      }
    } else if (viewId == R.id.btn_screenCapture) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
        return;
      }
      MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) UI.getContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
      UI.startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), SCREEN_CAPTURE_REQUEST_CODE);
    }
  }

  private boolean canShowScreenSharing() {
    TGCallService service = TGCallService.currentInstance();
    return Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP && !callSettings.isCameraSharing() && !callSettings.isScreenSharing() && service != null && service.isVideoSupported();
  }

  @Override
  public void onActivityResult (int requestCode, int resultCode, Intent data) {
    if (requestCode == SCREEN_CAPTURE_REQUEST_CODE) {
      if (resultCode == Activity.RESULT_OK && !callSettings.isCameraSharing()) {
        JavaVideoCapturerModule.setMediaProjectionPermissionResult(data);
        if (callSettings == null) {
          callSettings = new CallSettings(tdlib, call.id);
        }
        callSettings.setScreenSharing(true);
        videoButtonView.setIcon(R.drawable.baseline_share_arrow_24);
        videoButtonView.setIsActive(true, true);
        callSettings.setLocalCameraState(callSettings.getAvailableLocalCameraState());
        otherOptionsContainer.setVisibility(View.GONE);
        messageButtonContainer.setVisibility(View.VISIBLE);
        currentUserTextureView.renderer.init(JavaVideoCapturerModule.getSharedEGLContext(), null);
        currentUserTextureView.setIsCamera(false);
        currentUserTextureView.setIsScreencast(true);
        showFloatingLayout(true);
      }
    }
  }


  private void showMiniFloatingLayout(boolean animated) {
    var state = callSettings.getRemoteCameraState();
    if (state == VoIPFloatingLayout.STATE_FLOATING) {
      callingUserMiniFloatingLayout.setIsActive(true);
      if (animated) {
        if (callingUserMiniFloatingLayout.getVisibility() != View.VISIBLE) {
          callingUserMiniFloatingLayout.setVisibility(View.VISIBLE);
          callingUserMiniFloatingLayout.setAlpha(0f);
          callingUserMiniFloatingLayout.setScaleX(0.5f);
          callingUserMiniFloatingLayout.setScaleY(0.5f);
        }
        callingUserMiniFloatingLayout.animate().setListener(null).cancel();
        callingUserMiniFloatingLayout.isAppearing = true;
        callingUserMiniFloatingLayout.animate()
          .alpha(1f).scaleX(1f).scaleY(1f)
          .setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).setStartDelay(150)
          .withEndAction(() -> {
            callingUserMiniFloatingLayout.isAppearing = false;
            callingUserMiniFloatingLayout.invalidate();
          }).start();
      } else {
        callingUserMiniFloatingLayout.setAlpha(1f);
        callingUserMiniFloatingLayout.setScaleX(1f);
        callingUserMiniFloatingLayout.setScaleY(1f);
        callingUserMiniFloatingLayout.setVisibility(View.VISIBLE);
      }
      callingUserMiniFloatingLayout.setTag(1);
    } else if (state == VoIPFloatingLayout.STATE_FULLSCREEN) {
      callingUserMiniFloatingLayout.setIsActive(false);
      if (animated) {
        callingUserMiniFloatingLayout.animate().alpha(0).scaleX(0.5f).scaleY(0.5f).setListener(new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            if (callingUserMiniFloatingLayout.getTag() == null) {
              callingUserMiniFloatingLayout.setVisibility(View.GONE);
            }
          }
        }).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
      } else {
        callingUserMiniFloatingLayout.setVisibility(View.GONE);
      }
      callingUserMiniFloatingLayout.setTag(null);
    }
  }

  private Animator cameraShowingAnimator;
  private void showFloatingLayout(boolean animated) {
    var state = callSettings.getLocalCameraState();
    if (!animated && cameraShowingAnimator != null) {
      cameraShowingAnimator.removeAllListeners();
      cameraShowingAnimator.cancel();
    }
    if (state == VoIPFloatingLayout.STATE_GONE) {
      if (animated) {
        if (currentUserCameraFloatingLayout.getTag() != null && (int) currentUserCameraFloatingLayout.getTag() != VoIPFloatingLayout.STATE_GONE) {
          if (cameraShowingAnimator != null) {
            cameraShowingAnimator.removeAllListeners();
            cameraShowingAnimator.cancel();
          }
          AnimatorSet animatorSet = new AnimatorSet();
          animatorSet.playTogether(
            ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.ALPHA, currentUserCameraFloatingLayout.getAlpha(), 0)
          );
          if (currentUserCameraFloatingLayout.getTag() != null && (int) currentUserCameraFloatingLayout.getTag() == VoIPFloatingLayout.STATE_FLOATING) {
            animatorSet.playTogether(
              ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.SCALE_X, currentUserCameraFloatingLayout.getScaleX(), 0.7f),
              ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.SCALE_Y, currentUserCameraFloatingLayout.getScaleX(), 0.7f)
            );
          }
          cameraShowingAnimator = animatorSet;
          cameraShowingAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
              currentUserCameraFloatingLayout.setTranslationX(0);
              currentUserCameraFloatingLayout.setTranslationY(0);
              currentUserCameraFloatingLayout.setScaleY(1f);
              currentUserCameraFloatingLayout.setScaleX(1f);
              currentUserCameraFloatingLayout.setVisibility(View.GONE);
            }
          });
          cameraShowingAnimator.setDuration(250).setInterpolator(CubicBezierInterpolator.DEFAULT);
          cameraShowingAnimator.setStartDelay(50);
          cameraShowingAnimator.start();
        }
      } else {
        currentUserCameraFloatingLayout.setVisibility(View.GONE);
      }
    } else {
      boolean switchToFloatAnimated = animated;
      if (currentUserCameraFloatingLayout.getTag() == null || (int) currentUserCameraFloatingLayout.getTag() == VoIPFloatingLayout.STATE_GONE) {
        switchToFloatAnimated = false;
      }
      if (animated) {
        if (currentUserCameraFloatingLayout.getTag() != null && (int) currentUserCameraFloatingLayout.getTag() == VoIPFloatingLayout.STATE_GONE) {
          if (currentUserCameraFloatingLayout.getVisibility() == View.GONE) {
            currentUserCameraFloatingLayout.setAlpha(0f);
            currentUserCameraFloatingLayout.setScaleX(0.7f);
            currentUserCameraFloatingLayout.setScaleY(0.7f);
            currentUserCameraFloatingLayout.setVisibility(View.VISIBLE);
          }
          if (cameraShowingAnimator != null) {
            cameraShowingAnimator.removeAllListeners();
            cameraShowingAnimator.cancel();
          }
          AnimatorSet animatorSet = new AnimatorSet();
          animatorSet.playTogether(
            ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.ALPHA, 0.0f, 1f),
            ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.SCALE_X, 0.7f, 1f),
            ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.SCALE_Y, 0.7f, 1f)
          );
          cameraShowingAnimator = animatorSet;
          cameraShowingAnimator.setDuration(150).start();
        }
      } else {
        currentUserCameraFloatingLayout.setVisibility(View.VISIBLE);
      }
      if ((currentUserCameraFloatingLayout.getTag() == null || (int) currentUserCameraFloatingLayout.getTag() != VoIPFloatingLayout.STATE_FLOATING) && currentUserCameraFloatingLayout.relativePositionToSetX < 0) {
        currentUserCameraFloatingLayout.setRelativePosition(1f, 1f);
      }
      currentUserCameraFloatingLayout.setFloatingMode(state == VoIPFloatingLayout.STATE_FLOATING, switchToFloatAnimated);
    }
    currentUserCameraFloatingLayout.setTag(state);
  }

  @Override
  public void onUserUpdated (final TdApi.User user) {
    tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        setTexts();
      }
    });
  }

  @Override
  public void onCallUpdated (final TdApi.Call call) {
    if (!isDestroyed()) {
      updateCall(call);
      updateCallState();
    }
  }

  private boolean isClosed;

  private void closeCall () {
    isClosed = true;
    tdlib.cache().unsubscribeFromCallUpdates(call.id, this);
    navigateBack();
  }

  private TdApi.CallState previousCallState;

  private void updateCall (TdApi.Call call) {
    if (isClosed) {
      return;
    }
    this.previousCallState = this.call.state;
    boolean prevIsActive = this.call.state.getConstructor() == TdApi.CallStateReady.CONSTRUCTOR;
    boolean callEnded = call.state.getConstructor() == TdApi.CallStateHangingUp.CONSTRUCTOR || (call.state.getConstructor() == TdApi.CallStateDiscarded.CONSTRUCTOR && ((TdApi.CallStateDiscarded) call.state).reason.getConstructor() == TdApi.CallDiscardReasonHungUp.CONSTRUCTOR);

    this.call = call;
    this.callDuration = 0;
    setCallBarsCount(tdlib.context().calls().getCallBarsCount(tdlib, call.id));
    updateCallStrength();
    if (TD.isCancelled(call) || TD.isAcceptedOnOtherDevice(call) || TD.isDeclined(call) || (prevIsActive && callEnded) || TD.isMissed(call) || call.state.getConstructor() == TdApi.CallStateHangingUp.CONSTRUCTOR) {
      closeCall();
    } else {
      callControlsLayout.setCall(tdlib, call, navigationController != null);
    }
  }

  @Override
  public void onCallStateChanged (final int callId, final int newState) {
    if (!isDestroyed()) {
      updateCallState();
      if (newState == CallState.ESTABLISHED) {
        AndroidUtils.runOnUIThread(() -> {
          TGCallService service = TGCallService.currentInstance();
          if (service != null) {
            if (service.isVideoSupported()) {
              videoButtonContainer.setVisibility(View.VISIBLE);
            }
            otherOptionsContainer.setVisibility(View.VISIBLE);
            messageButtonContainer.setVisibility(View.GONE);
            if (callSettings == null) {
              callSettings = new CallSettings(tdlib, call.id);
            }
            service.setRemoteSourceChangeCallback((chatId, remoteSource) -> AndroidUtils.runOnUIThread(() -> {
              if (remoteSource.device == StreamDevice.CAMERA || remoteSource.device == StreamDevice.SCREEN) {
                var currentUserActive = callSettings.getLocalCameraState() != VoIPFloatingLayout.STATE_GONE;
                if (remoteSource.state == SourceState.ACTIVE) {
                  callSettings.setRemoteCameraState(VoIPFloatingLayout.STATE_FULLSCREEN);
                  callingUserTextureView.setVisibility(View.VISIBLE);
                  callingUserTextureView.renderer.init(JavaVideoCapturerModule.getSharedEGLContext(), null);
                  callingUserMiniTextureRenderer.init(JavaVideoCapturerModule.getSharedEGLContext(), null);
                  if (currentUserActive) callSettings.setLocalCameraState(VoIPFloatingLayout.STATE_FLOATING);
                } else if (remoteSource.state == SourceState.INACTIVE) {
                  callSettings.setRemoteCameraState(VoIPFloatingLayout.STATE_GONE);
                  callingUserTextureView.setVisibility(View.GONE);
                  callingUserTextureView.stopCapturing();
                  callingUserMiniTextureRenderer.release();
                  if (currentUserActive) callSettings.setLocalCameraState(VoIPFloatingLayout.STATE_FULLSCREEN);
                }
                if (currentUserActive) showFloatingLayout(true);
                showMiniFloatingLayout(true);
              }
            }));
            service.setFrameCallback((chatId, streamMode, streamDevice, frameList) -> {
              var isVideo = streamDevice == StreamDevice.CAMERA || streamDevice == StreamDevice.SCREEN;
              if (isVideo) {
                var rawFrame = frameList.get(0);
                int ySize = rawFrame.frameData.width * rawFrame.frameData.height;
                int uvSize = ySize / 4;
                var i420Buffer = JavaI420Buffer.allocate(rawFrame.frameData.width, rawFrame.frameData.height);
                i420Buffer.getDataY().put(rawFrame.data, 0, ySize).flip();
                i420Buffer.getDataU().put(rawFrame.data, ySize, uvSize).flip();
                i420Buffer.getDataV().put(rawFrame.data, ySize + uvSize, uvSize).flip();
                VideoFrame frame = new VideoFrame(i420Buffer, rawFrame.frameData.rotation, System.nanoTime());

                switch (streamMode) {
                  case CAPTURE:
                    if (callSettings.getLocalCameraState() != VoIPFloatingLayout.STATE_GONE) {
                      currentUserTextureView.onFrame(frame);
                    }
                    break;
                  case PLAYBACK:
                    switch (callSettings.getRemoteCameraState()) {
                      case VoIPFloatingLayout.STATE_FULLSCREEN:
                        callingUserTextureView.onFrame(frame);
                        break;
                      case VoIPFloatingLayout.STATE_FLOATING:
                        callingUserMiniTextureRenderer.onFrame(frame);
                        break;
                    }
                    break;
                }
                i420Buffer.release();
              }
            });
          }
        });
      }
    }
  }

  @Override
  public void onCallBarsCountChanged (int callId, int barsCount) {
    if (!isDestroyed() && this.call != null && this.call.id == callId) {
      setCallBarsCount(barsCount);
    }
  }

  @Override
  public void onCallSettingsChanged (final int callId, final CallSettings settings) {
    if (!isDestroyed()) {
      callSettings = settings;
      updateCallButtons();
    }
  }

  private boolean isFlashing;
  private FactorAnimator flashAnimator;
  private static final int ANIMATOR_FLASH_ID = 1;

  public static final long CALL_FLASH_DURATION = 1100;
  public static final long CALL_FLASH_DELAY = 650l;

  private final RateLimiter flashLimiter = new RateLimiter(() -> {
    if (isFlashing) {
      flashAnimator.forceFactor(0f);
      if (isFlashing) {
        flashAnimator.animateTo(1f);
      }
    }
  }, 100l, null);

  private void setFlashing (boolean isFlashing) {
    if (this.isFlashing != isFlashing) {
      this.isFlashing = isFlashing;
      if (isFlashing) {
        if (flashAnimator == null) {
          flashAnimator = new FactorAnimator(ANIMATOR_FLASH_ID, this, AnimatorUtils.DECELERATE_INTERPOLATOR, CALL_FLASH_DURATION);
          flashAnimator.setStartDelay(CALL_FLASH_DELAY);
        }
        if (!flashAnimator.isAnimating()) {
          flashAnimator.forceFactor(0f);
          flashAnimator.animateTo(1f);
        }
      } else {
        if (flashAnimator != null && flashAnimator.getFactor() == 0f) {
          flashAnimator.forceFactor(0f);
        }
      }
    }
  }

  private long callDuration;

  private void updateCallState () {
    updateLoop();
    String str;
    callDuration = tdlib.context().calls().getCallDuration(tdlib, call.id);
    if (previousCallState != null && call.state.getConstructor() == TdApi.CallStateHangingUp.CONSTRUCTOR) {
      str = TD.getCallState2(call, previousCallState, callDuration, false);
    } else {
      str = TD.getCallState(call, callDuration, false);
      if (!call.isOutgoing && call.state.getConstructor() == TdApi.CallStatePending.CONSTRUCTOR && tdlib.context().isMultiUser()) {
        String longName = tdlib.accountLongName();
        if (longName != null) {
          str = str + "\n" + Lang.getString(R.string.VoipAnsweringAsAccount, longName);
        }
      }
    }
    stateView.setText(str.toUpperCase());
    setButtonsVisible(!TD.isFinished(call) && !(call.state.getConstructor() == TdApi.CallStatePending.CONSTRUCTOR && !call.isOutgoing), isFocused());
    updateEmoji();
    updateFlashing();
    updateCallStrength();
  }

  private boolean hadFocus;

  private void updateFlashing () {
    hadFocus = isFocused() || hadFocus;
    setFlashing(TD.getCallNeedsFlashing(call) && hadFocus);
  }

  @Override
  protected void onFocusStateChanged () {
    updateFlashing();
  }

  private void updateControlsAlpha () {
    float alpha = lastHeaderFactor * buttonsFactor;
    buttonWrap.setAlpha(alpha);
    buttonWrap.setTranslationY((1f - lastHeaderFactor) * buttonWrap.getMeasuredHeight() * .2f);
  }

  private void updateCallButtons () {
    if (buttonWrap != null) {
      muteButtonView.setIsActive(callSettings != null && callSettings.isMicMuted(), isFocused());
      speakerButtonView.setIsActive(callSettings != null && callSettings.isSpeakerModeEnabled(), isFocused());
      videoButtonView.setIsActive(callSettings != null && callSettings.getLocalCameraState() != VoIPFloatingLayout.STATE_GONE, isFocused());
      flipCameraButtonView.setIsActive(callSettings != null && !callSettings.isCameraFrontFacing(), isFocused());
      flipCameraButtonView.setIcon(callSettings != null && !callSettings.isCameraFrontFacing() ? R.drawable.baseline_camera_rear_24 : R.drawable.baseline_camera_front_24);
    }
  }

  private void updateEmoji () {
    boolean emojiVisible = (call.state.getConstructor() == TdApi.CallStateReady.CONSTRUCTOR);
    if (emojiVisible && StringUtils.isEmpty(emojiViewSmall.getText())) {
      TdApi.CallStateReady ready = (TdApi.CallStateReady) call.state;

      StringBuilder b = new StringBuilder();
      for (String emoji : ready.emojis) {
        if (b.length() > 0) {
          b.append("  ");
        }
        b.append(emoji);
      }

      CharSequence result = Emoji.instance().replaceEmoji(b.toString());

      emojiViewSmall.setText(result);
      emojiViewBig.setText(result);

      if (!hadEmojiSinceStart) {
        showEmojiTooltip();
      }
    }
    updateEmojiFactors();
    setEmojiVisible(emojiVisible, isFocused());
  }

  private void showEmojiTooltip () {
    context().tooltipManager().builder(emojiViewSmall).controller(this).show(tdlib, Lang.getStringBold(R.string.CallEmojiHint, TD.getUserSingleName(call.userId, user)));
  }

  private boolean isEmojiVisible;
  private FactorAnimator emojiVisibilityAnimator;

  private static final int ANIMATOR_EMOJI_VISIBILITY_ID = 3;
  private static final int ANIMATOR_EMOJI_EXPAND_ID = 4;

  private void setEmojiVisible (boolean isVisible, boolean animated) {
    if (this.isEmojiVisible != isVisible) {
      this.isEmojiVisible = isVisible;
      float toFactor = isVisible ? 1f : 0f;

      if (animated) {
        if (emojiVisibilityAnimator == null) {
          emojiVisibilityAnimator = new FactorAnimator(ANIMATOR_EMOJI_VISIBILITY_ID, this, AnimatorUtils.DECELERATE_INTERPOLATOR, DURATION_EMOJI_VISIBILITY, this.emojiVisibilityFactor);
        }
        emojiVisibilityAnimator.animateTo(toFactor);
      } else {
        if (emojiVisibilityAnimator != null) {
          emojiVisibilityAnimator.forceFactor(toFactor);
        }
        setEmojiVisibilityFactor(toFactor);
      }
    }
  }

  private static final long DURATION_EMOJI_VISIBILITY = 180l;

  private boolean isEmojiExpanded;
  private FactorAnimator emojiExpandAnimator;

  private void setEmojiExpanded (boolean isExpanded) {
    if (this.isEmojiExpanded != isExpanded) {
      this.isEmojiExpanded = isExpanded;
      if (emojiExpandAnimator == null) {
        emojiExpandAnimator = new FactorAnimator(ANIMATOR_EMOJI_EXPAND_ID, this, new OvershootInterpolator(1.02f), 310l, this.emojiExpandFactor);
      }
      emojiExpandAnimator.animateTo(isExpanded ? 1f : 0f);
    }
  }

  private float emojiVisibilityFactor;
  private float emojiExpandFactor;

  private void setEmojiVisibilityFactor (float factor) {
    if (this.emojiVisibilityFactor != factor) {
      this.emojiVisibilityFactor = factor;
      updateEmojiFactors();
    }
  }

  private void setEmojiExpandFactor (float factor) {
    if (this.emojiExpandFactor != factor) {
      this.emojiExpandFactor = factor;
      updateEmojiFactors();
    }
  }

  private static final float DESIRED_EMOJI_EXPAND_FACTOR = 2.25f;
  private static float EMOJI_EXPAND_FACTOR = DESIRED_EMOJI_EXPAND_FACTOR;

  private void updateEmojiFactors () {
    emojiViewSmall.setAlpha(MathUtils.clamp(emojiVisibilityFactor * (1f - Math.max(1f - lastHeaderFactor, emojiExpandFactor >= .5f ? (emojiExpandFactor - .5f) / .5f : 0f))));
    emojiViewSmall.setScaleX(1f + emojiExpandFactor * (EMOJI_EXPAND_FACTOR - 1f));
    emojiViewSmall.setScaleY(1f + emojiExpandFactor * (EMOJI_EXPAND_FACTOR - 1f));

    float bigAlpha = MathUtils.clamp(emojiVisibilityFactor * emojiExpandFactor);
    emojiViewBig.setAlpha(bigAlpha);
    emojiViewHint.setAlpha(bigAlpha);
    float factor = 1f / EMOJI_EXPAND_FACTOR;
    emojiViewBig.setScaleX(factor + (1f - factor) * emojiExpandFactor);
    emojiViewBig.setScaleY(factor + (1f - factor) * emojiExpandFactor);
    avatarView.setMainAlpha(1f - MathUtils.clamp(emojiVisibilityFactor * emojiExpandFactor));
    updateEmojiPosition();
  }

  private void updateEmojiPosition () {
    final int parentWidth = ((View) emojiViewBig.getParent()).getMeasuredWidth();
    final int parentHeight = ((View) emojiViewBig.getParent()).getMeasuredHeight();

    final int viewWidth = emojiViewBig.getMeasuredWidth();
    final int viewHeight = emojiViewBig.getMeasuredHeight();

    final int viewWidthSmall = emojiViewSmall.getMeasuredWidth();
    final int viewHeightSmall = emojiViewSmall.getMeasuredHeight();

    final int startLeft = parentWidth - viewWidthSmall;
    final int startTop = Screen.dp(42f) - emojiViewSmall.getPaddingTop();

    final int fromCenterX = startLeft + viewWidthSmall / 2;
    final int fromCenterY = startTop + viewHeightSmall / 2;

    final int toCenterX = parentWidth / 2;
    final int toCenterY = parentHeight / 2 - Screen.dp(24f);

    final int centerX = (int) (fromCenterX + (float) (toCenterX - fromCenterX) * emojiExpandFactor);
    final int centerY = (int) (fromCenterY + (float) (toCenterY - fromCenterY) * emojiExpandFactor);

    int x = centerX - viewWidth / 2;
    int y = centerY - viewHeight / 2;

    emojiViewBig.setTranslationX(x);
    emojiViewBig.setTranslationY(y);

    int xSmall = centerX - viewWidthSmall / 2;
    int ySmall = centerY - viewHeightSmall / 2;

    emojiViewSmall.setTranslationX(xSmall);
    emojiViewSmall.setTranslationY(ySmall);
  }

  private boolean oneShot;

  @Override
  public void onFocus () {
    super.onFocus();
    if (!oneShot) {
      destroyStackItemByIdExcludingLast(R.id.controller_call);
      ViewController<?> c = previousStackItem();
      if (c != null && c.getId() == R.id.controller_contacts) {
        destroyStackItemById(R.id.controller_contacts);
      }
      oneShot = true;
    }
    tdlib.context().calls().acknowledgeCurrentCall(call.id);
  }

  @Override
  protected int getPopupRestoreColor () {
    return 0xff000000;
  }

  @Override
  protected boolean forceFadeMode () {
    ViewController<?> c = previousStackItem();
    return c != null && c.getId() == R.id.controller_call;
  }

  public void replaceCall (TdApi.Call call) {
    tdlib.cache().unsubscribeFromCallUpdates(this.call.id, this);
    previousCallState = null;
    updateCall(call);
    tdlib.cache().subscribeToCallUpdates(call.id, this);
    tdlib.context().calls().acknowledgeCurrentCall(call.id);
    updateCallState();
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.cache().unsubscribeFromCallUpdates(call.id, this);
    tdlib.cache().removeUserDataListener(call.userId, this);
    avatarView.performDestroy();
  }

  @Override
  protected boolean usePopupMode () {
    return true;
  }
}
