package org.thunderdog.challegram.ui.camera;

import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.Image;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.camera.core.ImageProxy;
import androidx.core.content.ContextCompat;

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

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.camera.legacy.CameraApiLegacy;

import java.nio.ByteBuffer;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraQrBridge {
    public final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    private final CameraDelegate delegate;
    private final Executor mainExecutor;
    private final QRCodeReader zxingReader = new QRCodeReader();
    private BarcodeScanner barcodeScanner;

    public CameraQrBridge (CameraManager<?> manager) {
        this.delegate = manager.delegate;
        this.mainExecutor = ContextCompat.getMainExecutor(manager.context);

        if (U.isGooglePlayServicesAvailable(UI.getAppContext()) && !Config.QR_FORCE_ZXING) {
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
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    public void processImage (ImageProxy proxy) {
        @SuppressLint("UnsafeOptInUsageError") Image mediaImage = proxy.getImage();

        if (mediaImage == null) {
            proxy.close();
            return;
        }

        if (isGmsImplementationSupported()) {
            gmsImplementation(InputImage.fromMediaImage(mediaImage, proxy.getImageInfo().getRotationDegrees()), U.isRotated(proxy.getImageInfo().getRotationDegrees()), proxy::close);
        } else {
            zxingImplementation(bufferAsBytes(proxy.getPlanes()[0].getBuffer()), proxy.getWidth(), proxy.getHeight(), proxy.getImageInfo().getRotationDegrees(), proxy::close);
        }
    }

    public void processImage (byte[] data, int previewWidth, int previewHeight, CameraApiLegacy legacyApi) {
        int rotation = delegate.getCurrentCameraOrientation();

        if (isGmsImplementationSupported()) {
            gmsImplementation(InputImage.fromByteArray(data, previewWidth, previewHeight, rotation, ImageFormat.NV21), U.isRotated(rotation), legacyApi::notifyCanReadNextFrame);
        } else {
            zxingImplementation(data, previewWidth, previewHeight, rotation, legacyApi::notifyCanReadNextFrame);
        }
    }

    private boolean isGmsImplementationSupported () {
        return barcodeScanner != null;
    }

    private void gmsImplementation (InputImage image, boolean swapSizes, @Nullable Runnable onCompleteListener) {
        barcodeScanner.process(image).addOnSuccessListener(mainExecutor, barcodes -> {
            if (barcodes.isEmpty()) {
                delegate.onQrCodeNotFound();
            } else {
                Barcode first = barcodes.get(0);

                if (swapSizes) {
                    delegate.onQrCodeFound(first.getRawValue(), first.getBoundingBox(), image.getWidth(), image.getHeight());
                } else {
                    delegate.onQrCodeFound(first.getRawValue(), first.getBoundingBox(), image.getHeight(), image.getWidth());
                }
            }
        }).addOnFailureListener(ex -> Log.e(Log.TAG_CAMERA, ex)).addOnCompleteListener(result -> {
            if (onCompleteListener != null) onCompleteListener.run();
        });
    }

    @SuppressWarnings("SuspiciousNameCombination")
    private void zxingImplementation (byte[] data, int width, int height, int rotation, @Nullable Runnable onFinish) {
        backgroundExecutor.submit(() -> {
            try {
                Result match = zxingImplementationImpl(data, width, height, rotation);
                if (match != null && match.getText() != null && !match.getText().isEmpty()) {
                    mainExecutor.execute(() -> delegate.onQrCodeFound(match.getText(), zxingBoundingBox(match), width, height));
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

    private Rect zxingBoundingBox (Result result) {
        // ordered in: bottom-left, top-left, top-right
        if (result.getResultPoints().length < 3) return null;

        ResultPoint[] points = result.getResultPoints();
        return new Rect(
                (int) points[0].getX(), // left
                (int) points[1].getY(), // top
                (int) points[2].getX(), // right
                (int) points[0].getY() // bottom
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
