@file:JvmName("KonfettiBuilder")

package org.thunderdog.challegram.util

import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import nl.dionsegijn.konfetti.core.models.Shape
import nl.dionsegijn.konfetti.core.models.Size
import org.thunderdog.challegram.theme.ColorId
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
      Theme.getColor(ColorId.confettiRed),
      Theme.getColor(ColorId.confettiGreen),
      Theme.getColor(ColorId.confettiBlue),
      Theme.getColor(ColorId.confettiCyan),
      Theme.getColor(ColorId.confettiPurple),
      Theme.getColor(ColorId.confettiYellow)
    ),
    position = Position.Absolute(pivotX, pivotY),
    emitter = Emitter(duration = 150, TimeUnit.MILLISECONDS).max(150),
  )
}