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
 * File created on 10/09/2015 at 16:45
 */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.widget.EmptyTextView;
import org.thunderdog.challegram.widget.NoScrollTextView;

import me.vkryl.android.ViewUtils;
import me.vkryl.android.widget.FrameLayoutFix;

public class MessagesHolder extends RecyclerView.ViewHolder {
  public static final int TYPE_EMPTY = 0;

  public static final int TYPE_MESSAGE = 1;
  public static final int TYPE_MESSAGE_MEDIA = 2;
  public static final int TYPE_MESSAGE_COMPLEX_MEDIA = 3;
  public static final int TYPE_MESSAGE_VIEW_GROUP = 10;

  public static final int TYPE_SECRET_CHAT_INFO = 100;
  public static final int TYPE_SECRET_CHAT_INFO_BUBBLE = 101;

  public MessagesHolder (View view) {
    super(view);
  }

  public void setMessage (TGMessage message) {
    final int viewType = getItemViewType();
    if (viewType >= TYPE_MESSAGE_VIEW_GROUP) {
      ((MessageViewGroup) itemView).setMessage(message);
    } else {
      ((MessageView) itemView).setMessage(message);
    }
  }

  public static MessageView findMessageView (View view) {
    if (view instanceof MessageView) {
      return (MessageView) view;
    } else if (view instanceof MessageViewGroup) {
      return ((MessageViewGroup) view).getMessageView();
    } else {
      return null;
    }
  }

  public static boolean isMessageType (int type) {
    switch (type) {
      case TYPE_MESSAGE:
      case TYPE_MESSAGE_MEDIA:
      case TYPE_MESSAGE_COMPLEX_MEDIA:
        return true;
      default: {
        return type >= TYPE_MESSAGE_VIEW_GROUP && type < TYPE_SECRET_CHAT_INFO && isMessageType(type - TYPE_MESSAGE_VIEW_GROUP);
      }
    }
  }

  public void attach () {
    final int viewType = getItemViewType();
    if (isMessageType(viewType)) {
      if (viewType >= TYPE_MESSAGE_VIEW_GROUP) {
        ((MessageViewGroup) itemView).attach();
      } else {
        ((MessageView) itemView).onAttachedToRecyclerView();
      }
    }
  }

  public void detach () {
    final int viewType = getItemViewType();
    if (isMessageType(viewType)) {
      if (viewType >= TYPE_MESSAGE_VIEW_GROUP) {
        ((MessageViewGroup) itemView).detach();
      } else {
        ((MessageView) itemView).onDetachedFromRecyclerView();
      }
    }
  }

  private static MessageView createMessageView (Context context, int type, MessagesManager manager, @Nullable ViewController<?> themeProvider) {
    switch (type) {
      case TYPE_MESSAGE: {
        MessageView view = new MessageView(context);
        view.setManager(manager);
        if (themeProvider != null) {
          themeProvider.addThemeInvalidateListener(view);
        }
        return view;
      }
      case TYPE_MESSAGE_MEDIA: {
        MessageView view = new MessageView(context);
        view.setManager(manager);
        if (themeProvider != null) {
          themeProvider.addThemeInvalidateListener(view);
        }
        view.setUseReceivers();
        return view;
      }
      case TYPE_MESSAGE_COMPLEX_MEDIA: {
        MessageView view = new MessageView(context);
        view.setManager(manager);
        if (themeProvider != null) {
          themeProvider.addThemeInvalidateListener(view);
        }
        view.setUseComplexReceiver();
        return view;
      }
    }
    throw new IllegalArgumentException("");
  }

  public static MessagesHolder create (Context context, final MessagesManager manager, int type, @Nullable ViewController<?> themeProvider) {
    switch (type) {
      case TYPE_EMPTY: {
        final EmptyTextView view = new EmptyTextView(context) {
          @Override
          protected void onDraw (Canvas c) {
            int textColor;
            if (manager.useBubbles()) {
              DrawAlgorithms.drawBackground(this, c, TGMessage.getBubbleTransparentColor(manager));
              textColor = TGMessage.getBubbleTransparentTextColor(manager);
            } else {
              textColor = Theme.textDecentColor();
            }
            setTextColorIfNeeded(textColor);
            super.onDraw(c);
          }
        };
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, Settings.instance().getChatFontSize());
        view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (themeProvider != null) {
          themeProvider.addThemeInvalidateListener(view);
        }
        return new MessagesHolder(view);
      }
      case TYPE_SECRET_CHAT_INFO:
      case TYPE_SECRET_CHAT_INFO_BUBBLE: {
        FrameLayoutFix contentView;

        contentView = new FrameLayoutFix(context) {
          @Override
          protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
            int measuredHeight = getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec);
            int minHeight = Screen.dp(200f);
            if (minHeight > measuredHeight) {
              super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(minHeight, MeasureSpec.UNSPECIFIED));
            } else {
              super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
          }
        };
        contentView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final boolean forBubble = type == TYPE_SECRET_CHAT_INFO_BUBBLE;

        final ImageView[] imageViewHolder = new ImageView[1];
        final TextView[] textViewHolder = new TextView[2];
        final View[] wrapViewHolder = new View[1];
        final RelativeLayout layout = new RelativeLayout(context);
        if (forBubble) {
          ViewUtils.setBackground(layout, new Drawable() {
            @Override
            public void draw (@NonNull Canvas c) {
              int top = imageViewHolder[0].getTop();
              int left = Math.min(textViewHolder[0].getLeft(), textViewHolder[1].getLeft());
              int right = Math.max(textViewHolder[0].getRight(), textViewHolder[1].getRight());
              int bottom = wrapViewHolder[0].getBottom();

              int radius = Screen.dp(6f);
              int horizontalPadding = Screen.dp(9f);
              left -= horizontalPadding;
              right += horizontalPadding;
              top -= Screen.dp(12f);
              bottom += Screen.dp(8f);

              RectF rectF = Paints.getRectF();
              rectF.set(left, top, right, bottom);
              c.drawRoundRect(rectF, radius, radius, Paints.fillingPaint(TGMessage.getBubbleTransparentColor(manager)));

              int textColor = TGMessage.getBubbleTransparentTextColor(manager);
              if (textViewHolder[0].getCurrentTextColor() != textColor) {
                textViewHolder[0].setTextColor(textColor);
                imageViewHolder[0].setColorFilter(textColor);
              }
            }

            @Override
            public void setAlpha (@IntRange(from = 0, to = 255) int alpha) {

            }

            @Override
            public void setColorFilter (@Nullable ColorFilter colorFilter) {

            }

            @Override
            public int getOpacity () {
              return PixelFormat.UNKNOWN;
            }
          });
          if (themeProvider != null) {
            themeProvider.addThemeInvalidateListener(layout);
          }
        }
        layout.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(50f));
        params.addRule(RelativeLayout.CENTER_HORIZONTAL);
        params.topMargin = Screen.dp(16f);

        ImageView imageView = new ImageView(context);
        imageViewHolder[0] = imageView;
        imageView.setId(R.id.secret_icon);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setImageResource(R.drawable.baseline_lock_48);
        if (forBubble) {
          imageView.setColorFilter(0xffffffff);
        } else {
          imageView.setColorFilter(Theme.textSecureColor());
          if (themeProvider != null) {
            themeProvider.addThemeFilterListener(imageView, R.id.theme_color_textSecure);
          }
        }
        imageView.setLayoutParams(params);
        layout.addView(imageView);

        params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.CENTER_HORIZONTAL);
        params.addRule(RelativeLayout.BELOW, R.id.secret_icon);

        TextView headerView = new NoScrollTextView(context);
        textViewHolder[0] = headerView;
        headerView.setId(R.id.secret_title);
        if (forBubble) {
          headerView.setTextColor(0xffffffff);
        } else {
          headerView.setTextColor(Theme.textSecureColor());
          if (themeProvider != null) {
            themeProvider.addThemeTextColorListener(headerView, R.id.theme_color_textSecure);
          }
        }
        headerView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
        headerView.setTypeface(Fonts.getRobotoMedium());
        headerView.setText(Lang.getString(R.string.SecretChats));
        headerView.setGravity(Gravity.CENTER);
        headerView.setPadding(0, Screen.dp(5f), 0, Screen.dp(10f));
        headerView.setLayoutParams(params);
        layout.addView(headerView);

        params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.BELOW, R.id.secret_title);
        params.bottomMargin = Screen.dp(16f);

        FrameLayoutFix wrapView = new FrameLayoutFix(context);
        wrapViewHolder[0] = wrapView;
        wrapView.setLayoutParams(params);

        TextView textView = new NoScrollTextView(context);
        textViewHolder[1] = textView;
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
        textView.setGravity(Gravity.LEFT);
        textView.setTypeface(Fonts.getRobotoRegular());
        textView.setText(Lang.getString(R.string.format_EncryptedDescription, Lang.getString(R.string.EncryptedDescription1), Lang.getString(R.string.EncryptedDescription2), Lang.getString(R.string.EncryptedDescription3), Lang.getString(R.string.EncryptedDescription4)));
        if (forBubble) {
          textView.setTextColor(0xffffffff);
        } else {
          textView.setTextColor(Theme.textAccentColor());
          if (themeProvider != null) {
            themeProvider.addThemeTextAccentColorListener(textView);
          }
        }
        textView.setLineSpacing(Screen.dp(4f), 1f);
        textView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        wrapView.addView(textView);

        layout.addView(wrapView);

        contentView.addView(layout);

        return new MessagesHolder(contentView);
      }
      default: {
        if (type >= TYPE_MESSAGE_VIEW_GROUP) {
          MessageViewGroup viewGroup = new MessageViewGroup(context);
          viewGroup.initWithView(createMessageView(context, type - TYPE_MESSAGE_VIEW_GROUP, manager, themeProvider), manager, themeProvider);
          return new MessagesHolder(viewGroup);
        } else {
          MessageView messageView = createMessageView(context, type, manager, themeProvider);
          return new MessagesHolder(messageView);
        }
      }
    }
  }
}
