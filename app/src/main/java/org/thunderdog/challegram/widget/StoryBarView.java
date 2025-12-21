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
 * File created on 21/12/2024
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;

public class StoryBarView extends RecyclerView {

  private static final int ITEM_SIZE_DP = 72;
  private static final int AVATAR_SIZE_DP = 52;
  private static final int RING_WIDTH_DP = 2;
  private static final int RING_GAP_DP = 2;
  private static final int BAR_HEIGHT_DP = 100;

  private static final int VIEW_TYPE_ADD_STORY = 0;
  private static final int VIEW_TYPE_STORY = 1;

  private final Tdlib tdlib;
  private final StoryBarAdapter adapter;
  private final List<TdApi.ChatActiveStories> activeStoriesList = new ArrayList<>();
  private @Nullable StoryClickListener clickListener;
  private boolean canPostStory = false;

  public interface StoryClickListener {
    void onStoryClick (long chatId, int storyId, List<TdApi.ChatActiveStories> allStories, int position);
    void onAddStoryClick ();
  }

  public StoryBarView (@NonNull Context context, Tdlib tdlib) {
    super(context);
    this.tdlib = tdlib;

    setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
    setOverScrollMode(OVER_SCROLL_NEVER);
    setClipToPadding(false);
    setPadding(Screen.dp(8), Screen.dp(8), Screen.dp(8), Screen.dp(8));

    adapter = new StoryBarAdapter();
    setAdapter(adapter);
  }

  public void setClickListener (@Nullable StoryClickListener listener) {
    this.clickListener = listener;
  }

  public void setActiveStories (List<TdApi.ChatActiveStories> stories) {
    activeStoriesList.clear();
    if (stories != null) {
      activeStoriesList.addAll(stories);
    }
    adapter.notifyDataSetChanged();

    // Show/hide based on content and settings
    setVisibility(shouldShow() ? VISIBLE : GONE);
  }

  public void setCanPostStory (boolean canPost) {
    if (this.canPostStory != canPost) {
      this.canPostStory = canPost;
      adapter.notifyDataSetChanged();
      setVisibility(shouldShow() ? VISIBLE : GONE);
    }
  }

  public boolean shouldShow () {
    if (Settings.instance().hideStories()) {
      return false;
    }
    return canPostStory || !activeStoriesList.isEmpty();
  }

  private boolean hasAddButton () {
    return canPostStory;
  }

  public int getBarHeight () {
    return shouldShow() ? Screen.dp(BAR_HEIGHT_DP) : 0;
  }

  private class StoryBarAdapter extends RecyclerView.Adapter<ViewHolder> {

    @Override
    public int getItemViewType (int position) {
      if (hasAddButton() && position == 0) {
        return VIEW_TYPE_ADD_STORY;
      }
      return VIEW_TYPE_STORY;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
      if (viewType == VIEW_TYPE_ADD_STORY) {
        AddStoryItemView itemView = new AddStoryItemView(getContext(), tdlib);
        itemView.setLayoutParams(new LayoutParams(Screen.dp(ITEM_SIZE_DP), ViewGroup.LayoutParams.MATCH_PARENT));
        return new AddStoryViewHolder(itemView);
      } else {
        StoryAvatarItemView itemView = new StoryAvatarItemView(getContext(), tdlib);
        itemView.setLayoutParams(new LayoutParams(Screen.dp(ITEM_SIZE_DP), ViewGroup.LayoutParams.MATCH_PARENT));
        return new StoryItemViewHolder(itemView);
      }
    }

    @Override
    public void onBindViewHolder (@NonNull ViewHolder holder, int position) {
      if (holder instanceof AddStoryViewHolder) {
        ((AddStoryViewHolder) holder).bind();
      } else if (holder instanceof StoryItemViewHolder) {
        int storyIndex = hasAddButton() ? position - 1 : position;
        if (storyIndex >= 0 && storyIndex < activeStoriesList.size()) {
          TdApi.ChatActiveStories activeStories = activeStoriesList.get(storyIndex);
          ((StoryItemViewHolder) holder).bind(activeStories, storyIndex);
        }
      }
    }

    @Override
    public int getItemCount () {
      int count = activeStoriesList.size();
      if (hasAddButton()) {
        count++;
      }
      return count;
    }
  }

  private class AddStoryViewHolder extends ViewHolder {
    private final AddStoryItemView itemView;

    public AddStoryViewHolder (@NonNull AddStoryItemView itemView) {
      super(itemView);
      this.itemView = itemView;
    }

    public void bind () {
      itemView.setOnClickListener(v -> {
        if (clickListener != null) {
          clickListener.onAddStoryClick();
        }
      });
    }
  }

  private class StoryItemViewHolder extends ViewHolder {
    private final StoryAvatarItemView itemView;

    public StoryItemViewHolder (@NonNull StoryAvatarItemView itemView) {
      super(itemView);
      this.itemView = itemView;
    }

    public void bind (TdApi.ChatActiveStories activeStories, int position) {
      itemView.setActiveStories(activeStories);
      itemView.setOnClickListener(v -> {
        if (clickListener != null && activeStories.stories.length > 0) {
          // Find the first unread story, or the last one if all are read
          int storyIndex = 0;
          for (int i = 0; i < activeStories.stories.length; i++) {
            // StoryInfo doesn't have isViewed field - need to check differently
            // For now, just use the first story
            storyIndex = 0;
            break;
          }
          clickListener.onStoryClick(
            activeStories.chatId,
            activeStories.stories[storyIndex].storyId,
            activeStoriesList,
            position
          );
        }
      });
    }
  }

  /**
   * Individual story avatar item with ring indicator
   */
  public static class StoryAvatarItemView extends FrameLayoutFix {

    private final Tdlib tdlib;
    private final AvatarView avatarView;
    private final TextView nameView;
    private final Paint ringPaint;
    private final RectF ringRect;

    private @Nullable TdApi.ChatActiveStories activeStories;
    private boolean hasUnread = false;

    // Gradient colors for unread ring
    private static final int[] GRADIENT_COLORS = {
      0xFF7B68EE, // Medium slate blue
      0xFF00CED1, // Dark turquoise
      0xFF00FA9A  // Medium spring green
    };

    // Gray for read ring
    private static final int READ_RING_COLOR = 0xFFAAAAAA;

    public StoryAvatarItemView (@NonNull Context context, Tdlib tdlib) {
      super(context);
      this.tdlib = tdlib;

      setWillNotDraw(false);

      ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      ringPaint.setStyle(Paint.Style.STROKE);
      ringPaint.setStrokeWidth(Screen.dp(RING_WIDTH_DP));
      ringRect = new RectF();

      // Avatar view in center
      avatarView = new AvatarView(context);
      int avatarSize = Screen.dp(AVATAR_SIZE_DP);
      LayoutParams avatarParams = new LayoutParams(avatarSize, avatarSize);
      avatarParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
      avatarParams.topMargin = Screen.dp(8);
      avatarView.setLayoutParams(avatarParams);
      addView(avatarView);

      // Name below avatar
      nameView = new TextView(context);
      nameView.setTextSize(11);
      nameView.setTextColor(Theme.textAccentColor());
      nameView.setTypeface(Fonts.getRobotoRegular());
      nameView.setGravity(Gravity.CENTER);
      nameView.setSingleLine(true);
      nameView.setMaxLines(1);
      nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
      LayoutParams nameParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      nameParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
      nameParams.bottomMargin = Screen.dp(4);
      nameParams.leftMargin = Screen.dp(2);
      nameParams.rightMargin = Screen.dp(2);
      nameView.setLayoutParams(nameParams);
      addView(nameView);
    }

    public void setActiveStories (@Nullable TdApi.ChatActiveStories activeStories) {
      this.activeStories = activeStories;

      if (activeStories != null) {
        TdApi.Chat chat = tdlib.chat(activeStories.chatId);
        if (chat != null) {
          avatarView.setChat(tdlib, chat);
          String name = tdlib.chatTitle(chat);
          // Truncate name if too long
          if (name.length() > 10) {
            name = name.substring(0, 9) + "...";
          }
          nameView.setText(name);
        }

        // Check if there are unread stories
        hasUnread = activeStories.maxReadStoryId < getMaxStoryId(activeStories);
        updateRingGradient();
      }

      invalidate();
    }

    private int getMaxStoryId (TdApi.ChatActiveStories activeStories) {
      int maxId = 0;
      for (TdApi.StoryInfo info : activeStories.stories) {
        if (info.storyId > maxId) {
          maxId = info.storyId;
        }
      }
      return maxId;
    }

    private void updateRingGradient () {
      if (hasUnread) {
        int width = getWidth();
        int height = getHeight();
        if (width > 0 && height > 0) {
          LinearGradient gradient = new LinearGradient(
            0, 0, width, height,
            GRADIENT_COLORS,
            null,
            Shader.TileMode.CLAMP
          );
          ringPaint.setShader(gradient);
        }
      } else {
        ringPaint.setShader(null);
        ringPaint.setColor(READ_RING_COLOR);
      }
    }

    @Override
    protected void onSizeChanged (int w, int h, int oldw, int oldh) {
      super.onSizeChanged(w, h, oldw, oldh);
      updateRingGradient();
    }

    @Override
    protected void onDraw (Canvas canvas) {
      super.onDraw(canvas);

      if (activeStories == null || activeStories.stories.length == 0) {
        return;
      }

      // Draw ring around avatar
      int centerX = getWidth() / 2;
      int avatarTopMargin = Screen.dp(8);
      int avatarSize = Screen.dp(AVATAR_SIZE_DP);
      int centerY = avatarTopMargin + avatarSize / 2;

      float ringRadius = avatarSize / 2f + Screen.dp(RING_GAP_DP) + Screen.dp(RING_WIDTH_DP) / 2f;
      float strokeHalf = Screen.dp(RING_WIDTH_DP) / 2f;

      ringRect.set(
        centerX - ringRadius,
        centerY - ringRadius,
        centerX + ringRadius,
        centerY + ringRadius
      );

      canvas.drawOval(ringRect, ringPaint);
    }
  }

  /**
   * "Add Story" item view with plus icon and gradient ring
   */
  public static class AddStoryItemView extends FrameLayoutFix {

    private final Tdlib tdlib;
    private final ImageView addIcon;
    private final TextView nameView;
    private final Paint ringPaint;
    private final Paint bgPaint;
    private final RectF ringRect;

    // Gradient colors for add story ring (same as unread)
    private static final int[] GRADIENT_COLORS = {
      0xFF7B68EE, // Medium slate blue
      0xFF00CED1, // Dark turquoise
      0xFF00FA9A  // Medium spring green
    };

    public AddStoryItemView (@NonNull Context context, Tdlib tdlib) {
      super(context);
      this.tdlib = tdlib;

      setWillNotDraw(false);

      ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      ringPaint.setStyle(Paint.Style.STROKE);
      ringPaint.setStrokeWidth(Screen.dp(RING_WIDTH_DP));
      ringRect = new RectF();

      bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      bgPaint.setColor(Theme.fillingColor());

      // Plus icon in center
      addIcon = new ImageView(context);
      addIcon.setImageResource(R.drawable.baseline_add_24);
      addIcon.setColorFilter(Theme.iconColor());
      addIcon.setScaleType(ImageView.ScaleType.CENTER);
      int avatarSize = Screen.dp(AVATAR_SIZE_DP);
      LayoutParams iconParams = new LayoutParams(avatarSize, avatarSize);
      iconParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
      iconParams.topMargin = Screen.dp(8);
      addIcon.setLayoutParams(iconParams);
      addView(addIcon);

      // "Add Story" text below
      nameView = new TextView(context);
      nameView.setText(R.string.AddStory);
      nameView.setTextSize(11);
      nameView.setTextColor(Theme.textAccentColor());
      nameView.setTypeface(Fonts.getRobotoRegular());
      nameView.setGravity(Gravity.CENTER);
      nameView.setSingleLine(true);
      nameView.setMaxLines(1);
      nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
      LayoutParams nameParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      nameParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
      nameParams.bottomMargin = Screen.dp(4);
      nameParams.leftMargin = Screen.dp(2);
      nameParams.rightMargin = Screen.dp(2);
      nameView.setLayoutParams(nameParams);
      addView(nameView);
    }

    private void updateRingGradient () {
      int width = getWidth();
      int height = getHeight();
      if (width > 0 && height > 0) {
        LinearGradient gradient = new LinearGradient(
          0, 0, width, height,
          GRADIENT_COLORS,
          null,
          Shader.TileMode.CLAMP
        );
        ringPaint.setShader(gradient);
      }
    }

    @Override
    protected void onSizeChanged (int w, int h, int oldw, int oldh) {
      super.onSizeChanged(w, h, oldw, oldh);
      updateRingGradient();
    }

    @Override
    protected void onDraw (Canvas canvas) {
      super.onDraw(canvas);

      // Draw ring around icon area
      int centerX = getWidth() / 2;
      int avatarTopMargin = Screen.dp(8);
      int avatarSize = Screen.dp(AVATAR_SIZE_DP);
      int centerY = avatarTopMargin + avatarSize / 2;

      float ringRadius = avatarSize / 2f + Screen.dp(RING_GAP_DP) + Screen.dp(RING_WIDTH_DP) / 2f;

      ringRect.set(
        centerX - ringRadius,
        centerY - ringRadius,
        centerX + ringRadius,
        centerY + ringRadius
      );

      // Draw background circle
      float bgRadius = avatarSize / 2f;
      canvas.drawCircle(centerX, centerY, bgRadius, bgPaint);

      // Draw gradient ring
      canvas.drawOval(ringRect, ringPaint);
    }
  }
}
