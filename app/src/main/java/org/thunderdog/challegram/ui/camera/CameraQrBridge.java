package org.thunderdog.challegram.ui.camera;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.Image;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.camera.core.ImageProxy;
import androidx.core.content.ContextCompat;

import com.google.mlkit.common.MlKitException;
import com.google.mlkit.vision.barcode.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.google.zxing.qrcode.detector.AlignmentPattern;
import com.google.zxing.qrcode.detector.FinderPattern;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.camera.legacy.CameraApiLegacy;
import org.thunderdog.challegram.unsorted.Settings;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraQrBridge {
  public final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

  private final CameraDelegate delegate;
  private final Executor mainExecutor;
  private final QRCodeReader zxingReader = new QRCodeReader();
  private BarcodeScanner barcodeScanner;
  private boolean mlkitFailed;

  public CameraQrBridge (CameraManager<?> manager) {
    this.delegate = manager.delegate;
    this.mainExecutor = ContextCompat.getMainExecutor(manager.context);

    if (U.isGooglePlayServicesAvailable(UI.getAppContext()) && !Settings.instance().needForceZxingQrProcessing()) {
      try {
        barcodeScanner = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build());
      } catch (Exception e) {
        Log.e(Log.TAG_CAMERA, e);
      }
    }
  }

  public void destroy () {
    if (barcodeScanner != null) barcodeScanner.close();
    backgroundExecutor.shutdown();
    mlkitFailed = false;
  }

  @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
  public void processImage (ImageProxy proxy) {
    @SuppressWarnings("UnsafeOptInUsageError") Image mediaImage = proxy.getImage();

    if (mediaImage == null) {
      proxy.close();
      return;
    }

    if (isGmsImplementationSupported()) {
      gmsImplementation(InputImage.fromMediaImage(mediaImage, proxy.getImageInfo().getRotationDegrees()), bufferAsBytes(proxy.getPlanes()[0].getBuffer()), U.isRotated(proxy.getImageInfo().getRotationDegrees()), proxy::close);
    } else {
      zxingImplementation(bufferAsBytes(proxy.getPlanes()[0].getBuffer()), proxy.getWidth(), proxy.getHeight(), proxy.getImageInfo().getRotationDegrees(), proxy::close);
    }
  }

  public void processImage (byte[] data, int previewWidth, int previewHeight, CameraApiLegacy legacyApi) {
    int rotation = delegate.getCurrentCameraOrientation();

    if (isGmsImplementationSupported()) {
      gmsImplementation(InputImage.fromByteArray(data, previewWidth, previewHeight, rotation, ImageFormat.NV21), data, U.isRotated(rotation), legacyApi::notifyCanReadNextFrame);
    } else {
      zxingImplementation(data, previewWidth, previewHeight, rotation, legacyApi::notifyCanReadNextFrame);
    }
  }

  public boolean isGmsImplementationSupported () {
    return barcodeScanner != null && !mlkitFailed;
  }

  private void gmsImplementation (InputImage image, byte[] safeData, boolean swapSizes, @Nullable Runnable onCompleteListener) {
    barcodeScanner.process(image).addOnSuccessListener(mainExecutor, barcodes -> {
      if (barcodes.isEmpty()) {
        delegate.onQrCodeNotFound();
      } else {
        Barcode first = barcodes.get(0);

        if (swapSizes) {
          delegate.onQrCodeFound(first.getRawValue(), new RectF(first.getBoundingBox()), image.getWidth(), image.getHeight(), 0,  false);
        } else {
          delegate.onQrCodeFound(first.getRawValue(), new RectF(first.getBoundingBox()), image.getHeight(), image.getWidth(), 0, false);
        }
      }
    }).addOnFailureListener(ex -> {
      if (ex instanceof MlKitException) {
        //Log.w("MlkitException - reverting to ZXing [code: %s, msg: %s]", ((MlKitException) ex).getErrorCode(), ex.getMessage());
        mlkitFailed = true;
        zxingImplementation(safeData, image.getWidth(), image.getHeight(), image.getRotationDegrees(), onCompleteListener);
      } else {
        Log.e(Log.TAG_CAMERA, ex);
      }
    }).addOnCompleteListener(result -> {
      if (onCompleteListener != null) onCompleteListener.run();
    });
  }

  @SuppressWarnings("SuspiciousNameCombination")
  private void zxingImplementation (byte[] data, int width, int height, int rotation, @Nullable Runnable onFinish) {
    backgroundExecutor.submit(() -> {
      try {
        int sensorRotation = delegate.getCurrentCameraSensorOrientation();
        Result match = zxingImplementationImpl(data, width, height, rotation);
        if (match != null && match.getText() != null && !match.getText().isEmpty()) {
          RectF zxingBox;
          if (sensorRotation != rotation && U.isRotated(sensorRotation)) {
            zxingBox = zxingBoundingBox(match, rotation, true, width, height);
            mainExecutor.execute(() -> delegate.onQrCodeFound(match.getText(), zxingBox, height, width, rotation, true));
          } else {
            zxingBox = zxingBoundingBox(match, rotation, false, width, height);
            mainExecutor.execute(() -> delegate.onQrCodeFound(match.getText(), zxingBox, width, height, rotation, true));
          }
        } else {
          mainExecutor.execute(delegate::onQrCodeNotFound);
        }
      } catch (Exception ex) {
        if (ex instanceof NotFoundException) {
          mainExecutor.execute(delegate::onQrCodeNotFound);
        } else {
          Log.e(Log.TAG_CAMERA, ex);
        }
      } finally {
        if (onFinish != null) onFinish.run();
      }
    });
  }

  private RectF zxingBoundingBox (Result result, int rotation, boolean sensorRotationInverted, int width, int height) {
    // ordered in: bottom-left, top-left, top-right
    if (result.getResultPoints().length < 3) return null;

    ResultPoint[] points = result.getResultPoints();
    ResultPoint bottomLeft, topLeft, topRight;
    int moduleSize;

    if (U.isRotated(rotation)) {
      bottomLeft = points[2];
      topLeft = points[1];
      topRight = points[0];
    } else {
      bottomLeft = points[0];
      topLeft = points[1];
      topRight = points[2];
    }

    if (bottomLeft instanceof FinderPattern) {
      moduleSize = (int) ((FinderPattern) bottomLeft).getEstimatedModuleSize() * 2;
    } else {
      moduleSize = 0;
    }

    int x1 = (int) Math.min(Math.min(topLeft.getX(), topRight.getX()), bottomLeft.getX());
    int x2 = (int) Math.max(Math.max(topLeft.getX(), topRight.getX()), bottomLeft.getX());
    int y1 = (int) Math.min(Math.min(topLeft.getY(), topRight.getY()), bottomLeft.getY());
    int y2 = (int) Math.max(Math.max(topLeft.getY(), topRight.getY()), bottomLeft.getY());

    if (sensorRotationInverted) {
      // these rotations need inverting zones
      int px1 = width - x1;
      int px2 = width - x2;
      x1 = px2;
      x2 = px1;
    }

    return new RectF(
      x1 - moduleSize, y1 - moduleSize, x2 + moduleSize, y2 + moduleSize
    );
  }

  @SuppressWarnings("SuspiciousNameCombination")
  private Result zxingImplementationImpl (byte[] data, int width, int height, int rotation) throws FormatException, ChecksumException, NotFoundException {
    if (rotation != 0) {
      data = rotateYuvImage(data, width, height, rotation);
    }

    if (U.isRotated(rotation)) {
      int prevWidth = width;
      width = height;
      height = prevWidth;
    }

    PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
      data,
      width,
      height,
      0,
      0,
      width,
      height,
      rotation == 180
    );

    return zxingReader.decode(new BinaryBitmap(new HybridBinarizer(source)));
  }

  private byte[] bufferAsBytes (ByteBuffer buffer) {
    buffer.rewind();
    byte[] bytes = new byte[buffer.remaining()];
    buffer.get(bytes);
    return bytes;
  }

  private byte[] rotateYuvImage (byte[] data, int width, int height, int degrees) {
    if (degrees == 0 || degrees % 90 != 0) return data;

    byte[] rotatedData = new byte[data.length];

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        switch (degrees) {
          case 90: {
            rotatedData[x * height + height - y - 1] = data[x + y * width];
            break;
          }

          case 180: {
            rotatedData[width * (height - y - 1) + width - x - 1] = data[x + y * width];
            break;
          }

          case 270: {
            rotatedData[y + x * height] = data[y * width + width - x - 1];
            break;
          }
        }
      }
    }

    return rotatedData;
  }
}
