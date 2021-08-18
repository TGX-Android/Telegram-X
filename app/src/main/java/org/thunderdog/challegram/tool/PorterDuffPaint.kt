@file:JvmName("PorterDuffPaint")

package org.thunderdog.challegram.tool

import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import me.vkryl.core.alphaColor
import org.thunderdog.challegram.R
import org.thunderdog.challegram.core.Lang
import org.thunderdog.challegram.theme.PorterDuffThemeColorId
import org.thunderdog.challegram.theme.Theme

private fun Paint?.changePorterDuff (color: Int): Paint {
  if (this != null && this.color == color)
    return this
  val result = this ?: Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG)
  result.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
  result.color = color
  return result
}

@JvmName("get") @JvmOverloads fun getPorterDuffPaint (@PorterDuffThemeColorId colorId: Int, alpha: Float = 1.0f): Paint {
  val color = if (alpha != 1.0f) {
    alphaColor(alpha, Theme.getColor(colorId))
  } else {
    Theme.getColor(colorId)
  }
  return when (colorId) {
    R.id.theme_color_textLight -> if (paint_textLight != null) { paint_textLight.changePorterDuff(color) } else { paint_textLight = paint_textLight.changePorterDuff(color); paint_textLight!! }
    R.id.theme_color_textSecure -> if (paint_textSecure != null) { paint_textSecure.changePorterDuff(color) } else { paint_textSecure = paint_textSecure.changePorterDuff(color); paint_textSecure!! }
    R.id.theme_color_background_icon -> if (paint_background_icon != null) { paint_background_icon.changePorterDuff(color) } else { paint_background_icon = paint_background_icon.changePorterDuff(color); paint_background_icon!! }
    R.id.theme_color_icon -> if (paint_icon != null) { paint_icon.changePorterDuff(color) } else { paint_icon = paint_icon.changePorterDuff(color); paint_icon!! }
    R.id.theme_color_iconActive -> if (paint_iconActive != null) { paint_iconActive.changePorterDuff(color) } else { paint_iconActive = paint_iconActive.changePorterDuff(color); paint_iconActive!! }
    R.id.theme_color_iconLight -> if (paint_iconLight != null) { paint_iconLight.changePorterDuff(color) } else { paint_iconLight = paint_iconLight.changePorterDuff(color); paint_iconLight!! }
    R.id.theme_color_iconPositive -> if (paint_iconPositive != null) { paint_iconPositive.changePorterDuff(color) } else { paint_iconPositive = paint_iconPositive.changePorterDuff(color); paint_iconPositive!! }
    R.id.theme_color_iconNegative -> if (paint_iconNegative != null) { paint_iconNegative.changePorterDuff(color) } else { paint_iconNegative = paint_iconNegative.changePorterDuff(color); paint_iconNegative!! }
    R.id.theme_color_inlineIcon -> if (paint_inlineIcon != null) { paint_inlineIcon.changePorterDuff(color) } else { paint_inlineIcon = paint_inlineIcon.changePorterDuff(color); paint_inlineIcon!! }
    R.id.theme_color_playerCoverIcon -> if (paint_playerCoverIcon != null) { paint_playerCoverIcon.changePorterDuff(color) } else { paint_playerCoverIcon = paint_playerCoverIcon.changePorterDuff(color); paint_playerCoverIcon!! }
    R.id.theme_color_avatar_content -> if (paint_avatar_content != null) { paint_avatar_content.changePorterDuff(color) } else { paint_avatar_content = paint_avatar_content.changePorterDuff(color); paint_avatar_content!! }
    R.id.theme_color_headerText -> if (paint_headerText != null) { paint_headerText.changePorterDuff(color) } else { paint_headerText = paint_headerText.changePorterDuff(color); paint_headerText!! }
    R.id.theme_color_headerIcon -> if (paint_headerIcon != null) { paint_headerIcon.changePorterDuff(color) } else { paint_headerIcon = paint_headerIcon.changePorterDuff(color); paint_headerIcon!! }
    R.id.theme_color_headerButtonIcon -> if (paint_headerButtonIcon != null) { paint_headerButtonIcon.changePorterDuff(color) } else { paint_headerButtonIcon = paint_headerButtonIcon.changePorterDuff(color); paint_headerButtonIcon!! }
    R.id.theme_color_passcodeIcon -> if (paint_passcodeIcon != null) { paint_passcodeIcon.changePorterDuff(color) } else { paint_passcodeIcon = paint_passcodeIcon.changePorterDuff(color); paint_passcodeIcon!! }
    R.id.theme_color_ticks -> if (paint_ticks != null) { paint_ticks.changePorterDuff(color) } else { paint_ticks = paint_ticks.changePorterDuff(color); paint_ticks!! }
    R.id.theme_color_ticksRead -> if (paint_ticksRead != null) { paint_ticksRead.changePorterDuff(color) } else { paint_ticksRead = paint_ticksRead.changePorterDuff(color); paint_ticksRead!! }
    R.id.theme_color_chatListMute -> if (paint_chatListMute != null) { paint_chatListMute.changePorterDuff(color) } else { paint_chatListMute = paint_chatListMute.changePorterDuff(color); paint_chatListMute!! }
    R.id.theme_color_chatListIcon -> if (paint_chatListIcon != null) { paint_chatListIcon.changePorterDuff(color) } else { paint_chatListIcon = paint_chatListIcon.changePorterDuff(color); paint_chatListIcon!! }
    R.id.theme_color_chatListVerify -> if (paint_chatListVerify != null) { paint_chatListVerify.changePorterDuff(color) } else { paint_chatListVerify = paint_chatListVerify.changePorterDuff(color); paint_chatListVerify!! }
    R.id.theme_color_badgeText -> if (paint_badgeText != null) { paint_badgeText.changePorterDuff(color) } else { paint_badgeText = paint_badgeText.changePorterDuff(color); paint_badgeText!! }
    R.id.theme_color_badgeFailedText -> if (paint_badgeFailedText != null) { paint_badgeFailedText.changePorterDuff(color) } else { paint_badgeFailedText = paint_badgeFailedText.changePorterDuff(color); paint_badgeFailedText!! }
    R.id.theme_color_badgeMuted -> if (paint_badgeMuted != null) { paint_badgeMuted.changePorterDuff(color) } else { paint_badgeMuted = paint_badgeMuted.changePorterDuff(color); paint_badgeMuted!! }
    R.id.theme_color_badgeMutedText -> if (paint_badgeMutedText != null) { paint_badgeMutedText.changePorterDuff(color) } else { paint_badgeMutedText = paint_badgeMutedText.changePorterDuff(color); paint_badgeMutedText!! }
    R.id.theme_color_chatSendButton -> if (paint_chatSendButton != null) { paint_chatSendButton.changePorterDuff(color) } else { paint_chatSendButton = paint_chatSendButton.changePorterDuff(color); paint_chatSendButton!! }
    R.id.theme_color_messageAuthor -> if (paint_messageAuthor != null) { paint_messageAuthor.changePorterDuff(color) } else { paint_messageAuthor = paint_messageAuthor.changePorterDuff(color); paint_messageAuthor!! }
    R.id.theme_color_bubble_mediaTimeText -> if (paint_bubble_mediaTimeText != null) { paint_bubble_mediaTimeText.changePorterDuff(color) } else { paint_bubble_mediaTimeText = paint_bubble_mediaTimeText.changePorterDuff(color); paint_bubble_mediaTimeText!! }
    R.id.theme_color_bubble_mediaTimeText_noWallpaper -> if (paint_bubble_mediaTimeText_noWallpaper != null) { paint_bubble_mediaTimeText_noWallpaper.changePorterDuff(color) } else { paint_bubble_mediaTimeText_noWallpaper = paint_bubble_mediaTimeText_noWallpaper.changePorterDuff(color); paint_bubble_mediaTimeText_noWallpaper!! }
    R.id.theme_color_bubble_mediaOverlayText -> if (paint_bubble_mediaOverlayText != null) { paint_bubble_mediaOverlayText.changePorterDuff(color) } else { paint_bubble_mediaOverlayText = paint_bubble_mediaOverlayText.changePorterDuff(color); paint_bubble_mediaOverlayText!! }
    R.id.theme_color_bubbleIn_time -> if (paint_bubbleIn_time != null) { paint_bubbleIn_time.changePorterDuff(color) } else { paint_bubbleIn_time = paint_bubbleIn_time.changePorterDuff(color); paint_bubbleIn_time!! }
    R.id.theme_color_bubbleOut_time -> if (paint_bubbleOut_time != null) { paint_bubbleOut_time.changePorterDuff(color) } else { paint_bubbleOut_time = paint_bubbleOut_time.changePorterDuff(color); paint_bubbleOut_time!! }
    R.id.theme_color_bubbleOut_inlineIcon -> if (paint_bubbleOut_inlineIcon != null) { paint_bubbleOut_inlineIcon.changePorterDuff(color) } else { paint_bubbleOut_inlineIcon = paint_bubbleOut_inlineIcon.changePorterDuff(color); paint_bubbleOut_inlineIcon!! }
    R.id.theme_color_bubbleOut_waveformActive -> if (paint_bubbleOut_waveformActive != null) { paint_bubbleOut_waveformActive.changePorterDuff(color) } else { paint_bubbleOut_waveformActive = paint_bubbleOut_waveformActive.changePorterDuff(color); paint_bubbleOut_waveformActive!! }
    R.id.theme_color_bubbleOut_file -> if (paint_bubbleOut_file != null) { paint_bubbleOut_file.changePorterDuff(color) } else { paint_bubbleOut_file = paint_bubbleOut_file.changePorterDuff(color); paint_bubbleOut_file!! }
    R.id.theme_color_bubbleOut_ticks -> if (paint_bubbleOut_ticks != null) { paint_bubbleOut_ticks.changePorterDuff(color) } else { paint_bubbleOut_ticks = paint_bubbleOut_ticks.changePorterDuff(color); paint_bubbleOut_ticks!! }
    R.id.theme_color_bubbleOut_ticksRead -> if (paint_bubbleOut_ticksRead != null) { paint_bubbleOut_ticksRead.changePorterDuff(color) } else { paint_bubbleOut_ticksRead = paint_bubbleOut_ticksRead.changePorterDuff(color); paint_bubbleOut_ticksRead!! }
    R.id.theme_color_bubbleOut_messageAuthor -> if (paint_bubbleOut_messageAuthor != null) { paint_bubbleOut_messageAuthor.changePorterDuff(color) } else { paint_bubbleOut_messageAuthor = paint_bubbleOut_messageAuthor.changePorterDuff(color); paint_bubbleOut_messageAuthor!! }
    R.id.theme_color_file -> if (paint_file != null) { paint_file.changePorterDuff(color) } else { paint_file = paint_file.changePorterDuff(color); paint_file!! }
    R.id.theme_color_waveformActive -> if (paint_waveformActive != null) { paint_waveformActive.changePorterDuff(color) } else { paint_waveformActive = paint_waveformActive.changePorterDuff(color); paint_waveformActive!! }
    R.id.theme_color_waveformInactive -> if (paint_waveformInactive != null) { paint_waveformInactive.changePorterDuff(color) } else { paint_waveformInactive = paint_waveformInactive.changePorterDuff(color); paint_waveformInactive!! }
    R.id.theme_color_white -> if (paint_white != null) { paint_white.changePorterDuff(color) } else { paint_white = paint_white.changePorterDuff(color); paint_white!! }
    else -> throw IllegalArgumentException(Lang.getResourceEntryName(colorId))
  }
}

private var paint_textLight: Paint? = null
private var paint_textSecure: Paint? = null
private var paint_background_icon: Paint? = null
private var paint_icon: Paint? = null
private var paint_iconActive: Paint? = null
private var paint_iconLight: Paint? = null
private var paint_iconPositive: Paint? = null
private var paint_iconNegative: Paint? = null
private var paint_inlineIcon: Paint? = null
private var paint_playerCoverIcon: Paint? = null
private var paint_avatar_content: Paint? = null
private var paint_headerText: Paint? = null
private var paint_headerIcon: Paint? = null
private var paint_headerButtonIcon: Paint? = null
private var paint_passcodeIcon: Paint? = null
private var paint_ticks: Paint? = null
private var paint_ticksRead: Paint? = null
private var paint_chatListMute: Paint? = null
private var paint_chatListIcon: Paint? = null
private var paint_chatListVerify: Paint? = null
private var paint_badgeText: Paint? = null
private var paint_badgeFailedText: Paint? = null
private var paint_badgeMuted: Paint? = null
private var paint_badgeMutedText: Paint? = null
private var paint_chatSendButton: Paint? = null
private var paint_messageAuthor: Paint? = null
private var paint_bubble_mediaTimeText: Paint? = null
private var paint_bubble_mediaTimeText_noWallpaper: Paint? = null
private var paint_bubble_mediaOverlayText: Paint? = null
private var paint_bubbleIn_time: Paint? = null
private var paint_bubbleOut_time: Paint? = null
private var paint_bubbleOut_inlineIcon: Paint? = null
private var paint_bubbleOut_waveformActive: Paint? = null
private var paint_bubbleOut_file: Paint? = null
private var paint_bubbleOut_ticks: Paint? = null
private var paint_bubbleOut_ticksRead: Paint? = null
private var paint_bubbleOut_messageAuthor: Paint? = null
private var paint_file: Paint? = null
private var paint_waveformActive: Paint? = null
private var paint_waveformInactive: Paint? = null
private var paint_white: Paint? = null