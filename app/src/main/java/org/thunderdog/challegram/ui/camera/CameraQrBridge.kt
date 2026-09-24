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
 */
package org.thunderdog.challegram.ui.camera

import android.graphics.ImageFormat
import android.graphics.RectF
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.detector.FinderPattern
import org.thunderdog.challegram.Log
import org.thunderdog.challegram.U
import org.thunderdog.challegram.tool.UI
import org.thunderdog.challegram.ui.camera.legacy.CameraApiLegacy
import org.thunderdog.challegram.unsorted.Settings
import tgx.flavor.Barcode
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

class CameraQrBridge(manager: CameraManager<*>) {
  val backgroundExecutor: ExecutorService = Executors.newSingleThreadExecutor()

  private val delegate: CameraDelegate = manager.delegate
  private val mainExecutor: Executor = ContextCompat.getMainExecutor(manager.context)
  private val zxingReader = QRCodeReader()
  private var barcodeScanner: BarcodeScanner? = null
  private var mlkitFailed = false

  init {
    if (U.isGooglePlayServicesAvailable(UI.getAppContext()) && !Settings.instance().needForceZxingQrProcessing()) {
      try {
        barcodeScanner = BarcodeScanning.getClient(
          BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        )
      } catch (e: Exception) {
        Log.e(Log.TAG_CAMERA, e)
      }
    }
  }

  fun destroy() {
    barcodeScanner?.apply {
      close()
    }?.also {
      barcodeScanner = null
    }
    backgroundExecutor.shutdown()
    mlkitFailed = false
  }

  @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
  @Suppress("UnsafeOptInUsageError")
  fun processImage(proxy: ImageProxy) {
    proxy.use {
      val mediaImage = it.image
      if (mediaImage != null) {
        if (this.isGmsImplementationSupported) {
          gmsImplementation(
            InputImage.fromMediaImage(
              mediaImage,
              proxy.imageInfo.rotationDegrees
            ),
            bufferAsBytes(proxy.planes[0].buffer),
            U.isRotated(proxy.imageInfo.rotationDegrees)) {
          }
        } else {
          zxingImplementation(
            bufferAsBytes(proxy.planes[0].buffer),
            proxy.width,
            proxy.height,
            proxy.imageInfo.rotationDegrees) {
          }
        }
      }
    }
  }

  fun processImage(
    data: ByteArray,
    previewWidth: Int,
    previewHeight: Int,
    legacyApi: CameraApiLegacy
  ) {
    val rotation = delegate.getCurrentCameraOrientation()

    if (this.isGmsImplementationSupported) {
      gmsImplementation(
        InputImage.fromByteArray(
          data,
          previewWidth,
          previewHeight,
          rotation,
          ImageFormat.NV21
        ), data, U.isRotated(rotation)) {
        legacyApi.notifyCanReadNextFrame()
      }
    } else {
      zxingImplementation(
        data,
        previewWidth,
        previewHeight,
        rotation) {
        legacyApi.notifyCanReadNextFrame()
      }
    }
  }

  val isGmsImplementationSupported: Boolean
    get() = barcodeScanner != null && !mlkitFailed

  private fun gmsImplementation(
    image: InputImage,
    safeData: ByteArray,
    swapSizes: Boolean,
    onCompleteListener: (() -> Unit)?
  ) {
    barcodeScanner!!.process(image).addOnSuccessListener(
      mainExecutor,
      OnSuccessListener { barcodes: MutableList<Barcode?>? ->
        if (barcodes!!.isEmpty()) {
          delegate.onQrCodeNotFound()
        } else {
          val first: Barcode = barcodes[0]!!

          if (swapSizes) {
            delegate.onQrCodeFound(
              first.rawValue,
              RectF(first.boundingBox),
              image.width,
              image.height,
              0,
              false
            )
          } else {
            delegate.onQrCodeFound(
              first.rawValue,
              RectF(first.boundingBox),
              image.height,
              image.width,
              0,
              false
            )
          }
        }
      }).addOnFailureListener(OnFailureListener { ex: Exception? ->
      if (ex is MlKitException) {
        //Log.w("MlkitException - reverting to ZXing [code: %s, msg: %s]", ((MlKitException) ex).getErrorCode(), ex.getMessage());
        mlkitFailed = true
        zxingImplementation(
          safeData,
          image.width,
          image.height,
          image.rotationDegrees,
          onCompleteListener
        )
      } else {
        Log.e(Log.TAG_CAMERA, ex)
      }
    }).addOnCompleteListener { result ->
      if (onCompleteListener != null) onCompleteListener()
    }
  }

  private fun zxingImplementation(
    data: ByteArray,
    width: Int,
    height: Int,
    rotation: Int,
    onFinish: Runnable?
  ) {
    backgroundExecutor.submit {
      try {
        val sensorRotation = delegate.getCurrentCameraSensorOrientation()
        val match = zxingImplementationImpl(data, width, height, rotation)
        if (match != null && !match.text.isNullOrEmpty()) {
          val zxingBox: RectF?
          if (sensorRotation != rotation && U.isRotated(sensorRotation)) {
            zxingBox = zxingBoundingBox(match, rotation, true, width, height)
            mainExecutor.execute {
              delegate.onQrCodeFound(
                match.text,
                zxingBox,
                height,
                width,
                rotation,
                true
              )
            }
          } else {
            zxingBox = zxingBoundingBox(match, rotation, false, width, height)
            mainExecutor.execute {
              delegate.onQrCodeFound(
                match.text,
                zxingBox,
                width,
                height,
                rotation,
                true
              )
            }
          }
        } else {
          mainExecutor.execute { delegate.onQrCodeNotFound() }
        }
      } catch (ex: Exception) {
        if (ex is NotFoundException) {
          mainExecutor.execute { delegate.onQrCodeNotFound() }
        } else {
          Log.e(Log.TAG_CAMERA, ex)
        }
      } finally {
        onFinish?.run()
      }
    }
  }

  private fun zxingBoundingBox(
    result: Result,
    rotation: Int,
    sensorRotationInverted: Boolean,
    width: Int,
    height: Int
  ): RectF? {
    // ordered in: bottom-left, top-left, top-right
    if (result.resultPoints.size < 3) return null

    val points = result.resultPoints
    val bottomLeft: ResultPoint
    val topLeft: ResultPoint
    val topRight: ResultPoint
    val moduleSize: Int

    if (U.isRotated(rotation)) {
      bottomLeft = points[2]
      topLeft = points[1]
      topRight = points[0]
    } else {
      bottomLeft = points[0]
      topLeft = points[1]
      topRight = points[2]
    }

    if (bottomLeft is FinderPattern) {
      moduleSize = bottomLeft.getEstimatedModuleSize().toInt() * 2
    } else {
      moduleSize = 0
    }

    var x1 = min(min(topLeft.getX(), topRight.getX()), bottomLeft.getX()).toInt()
    var x2 = max(max(topLeft.getX(), topRight.getX()), bottomLeft.getX()).toInt()
    val y1 = min(min(topLeft.getY(), topRight.getY()), bottomLeft.getY()).toInt()
    val y2 = max(max(topLeft.getY(), topRight.getY()), bottomLeft.getY()).toInt()

    if (sensorRotationInverted) {
      // these rotations need inverting zones
      val px1 = width - x1
      val px2 = width - x2
      x1 = px2
      x2 = px1
    }

    return RectF(
      (x1 - moduleSize).toFloat(),
      (y1 - moduleSize).toFloat(),
      (x2 + moduleSize).toFloat(),
      (y2 + moduleSize).toFloat()
    )
  }

  @Throws(FormatException::class, ChecksumException::class, NotFoundException::class)
  private fun zxingImplementationImpl(
    data: ByteArray,
    width: Int,
    height: Int,
    rotation: Int
  ): Result? {
    var data = data
    var width = width
    var height = height
    if (rotation != 0) {
      data = rotateYuvImage(data, width, height, rotation)
    }

    if (U.isRotated(rotation)) {
      val prevWidth = width
      width = height
      height = prevWidth
    }

    val source = PlanarYUVLuminanceSource(
      data,
      width,
      height,
      0,
      0,
      width,
      height,
      rotation == 180
    )

    return zxingReader.decode(BinaryBitmap(HybridBinarizer(source)))
  }

  private fun bufferAsBytes(buffer: ByteBuffer): ByteArray {
    buffer.rewind()
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return bytes
  }

  private fun rotateYuvImage(data: ByteArray, width: Int, height: Int, degrees: Int): ByteArray {
    if (degrees == 0 || degrees % 90 != 0) return data

    val rotatedData = ByteArray(data.size)

    for (y in 0..<height) {
      for (x in 0..<width) {
        when (degrees) {
          90 -> {
            rotatedData[x * height + height - y - 1] = data[x + y * width]
          }

          180 -> {
            rotatedData[width * (height - y - 1) + width - x - 1] = data[x + y * width]
          }

          270 -> {
            rotatedData[y + x * height] = data[y * width + width - x - 1]
          }
        }
      }
    }

    return rotatedData
  }
}
