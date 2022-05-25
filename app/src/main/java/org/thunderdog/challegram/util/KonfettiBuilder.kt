@file:JvmName("KonfettiBuilder")

package org.thunderdog.challegram.util

import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import nl.dionsegijn.konfetti.core.models.Shape
import nl.dionsegijn.konfetti.core.models.Size
import org.thunderdog.challegram.R
import org.thunderdog.challegram.theme.Theme
import java.util.concurrent.TimeUnit

fun buildKonfettiParty (pivotX: Float, pivotY: Float): Party {
  return Party(
    speed = 1f,
    maxSpeed = 12f,
    timeToLive = 600L,
    shapes = listOf(Shape.Circle, Shape.Square),
    size = listOf(Size(12, 2f)),
    colors = listOf(
      Theme.getColor(R.id.theme_color_confettiRed),
      Theme.getColor(R.id.theme_color_confettiGreen),
      Theme.getColor(R.id.theme_color_confettiBlue),
      Theme.getColor(R.id.theme_color_confettiCyan),
      Theme.getColor(R.id.theme_color_confettiPurple),
      Theme.getColor(R.id.theme_color_confettiYellow)
    ),
    position = Position.Absolute(pivotX, pivotY),
    emitter = Emitter(duration = 150, TimeUnit.MILLISECONDS).max(150),
  )
}