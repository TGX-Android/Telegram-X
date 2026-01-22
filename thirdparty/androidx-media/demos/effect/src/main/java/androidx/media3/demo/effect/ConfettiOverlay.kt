/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.media3.demo.effect

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.os.Handler
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.Size
import androidx.media3.common.util.Util
import androidx.media3.effect.CanvasOverlay
import kotlin.math.abs
import kotlin.random.Random

/** Mimics an emitter of confetti, dropping from the center of the frame. */
internal class ConfettiOverlay : CanvasOverlay(/* useInputFrameSize= */ true) {

  private val confettiList = mutableListOf<Confetti>()
  private val paint = Paint()
  private val handler = Handler(Util.getCurrentOrMainLooper())

  private var addConfettiTask: (() -> Unit)? = null
  private var width = 0f
  private var height = 0f
  private var started = false

  override fun configure(videoSize: Size) {
    super.configure(videoSize)
    this.width = videoSize.width.toFloat()
    this.height = videoSize.height.toFloat()
  }

  @Synchronized
  override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
    if (!started) {
      start()
    }
    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

    confettiList.removeAll { confetti ->
      confetti.y > height / 2 || confetti.x <= 0 || confetti.x > width
    }
    for (confetti in confettiList) {
      confetti.draw(canvas, paint)
      confetti.update()
    }
  }

  @Throws(VideoFrameProcessingException::class)
  override fun release() {
    super.release()
    handler.post(this::stop)
  }

  /** Starts the confetti. */
  fun start() {
    addConfettiTask = this::addConfetti
    handler.post(checkNotNull(addConfettiTask))
    started = true
  }

  /** Stops the confetti. */
  fun stop() {
    handler.removeCallbacks(checkNotNull(addConfettiTask))
    confettiList.clear()
    started = false
    addConfettiTask = null
  }

  @Synchronized
  fun addConfetti() {
    repeat(5) {
      confettiList.add(
        Confetti(
          text = CONFETTI_TEXTS[abs(Random.nextInt()) % CONFETTI_TEXTS.size],
          x = width / 2f,
          y = EMITTER_POSITION_Y.toFloat(),
          size = CONFETTI_BASE_SIZE + Random.nextInt(CONFETTI_SIZE_VARIATION),
          color = Color.HSVToColor(floatArrayOf(Random.nextInt(360).toFloat(), 0.6f, 0.8f)),
        )
      )
    }
    handler.postDelayed(this::addConfetti, /* delayMillis= */ 100)
  }

  private class Confetti(
    private val text: String,
    private val size: Int,
    private val color: Int,
    var x: Float,
    var y: Float,
  ) {
    private val speedX = 4 * (Random.nextFloat() * 2 - 1) // Random speed in x direction
    private val speedY = 4 * Random.nextFloat() // Random speed in y direction
    private val rotationSpeed = (Random.nextFloat() - 0.5f) * 4f // Random rotation speed

    private var rotation = Random.nextFloat() * 360f

    /** Draws the [Confetti] on the [Canvas]. */
    fun draw(canvas: Canvas, paint: Paint) {
      canvas.save()
      paint.color = color
      canvas.translate(x, y)
      canvas.rotate(rotation)
      paint.textSize = size.toFloat()
      canvas.drawText(text, /* x= */ 0f, /* y= */ 0f, paint) // Only draw text
      canvas.restore()
    }

    /** Updates the [Confetti]. */
    fun update() {
      x += speedX
      y += speedY
      rotation += rotationSpeed
    }
  }

  private companion object {
    val CONFETTI_TEXTS = listOf("❊", "✿", "❊", "✦︎", "♥︎", "☕︎")
    const val EMITTER_POSITION_Y = -50
    const val CONFETTI_BASE_SIZE = 30
    const val CONFETTI_SIZE_VARIATION = 10
  }
}
