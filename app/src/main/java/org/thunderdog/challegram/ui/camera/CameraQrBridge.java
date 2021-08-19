package org.thunderdog.challegram.ui.camera;

import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
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
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.tool.UI;

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

    public CameraQrBridge(CameraManager<?> manager) {
        this.delegate = manager.delegate;
        this.mainExecutor = ContextCompat.getMainExecutor(manager.context);

        if (U.isGooglePlayServicesAvailable(UI.getAppContext())) {
            try {
                barcodeScanner = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build());
            } catch (Exception e) {
                Log.e(Log.TAG_CAMERA, e);
            }
        }
    }

    public void destroy() {
        barcodeScanner.close();
        backgroundExecutor.shutdown();
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    public void processImage(ImageProxy proxy) {
        @SuppressLint("UnsafeOptInUsageError") Image mediaImage = proxy.getImage();

        if (mediaImage == null) {
            proxy.close();
            return;
        }

        if (isGmsImplementationSupported()) {
            gmsImplementation(InputImage.fromMediaImage(mediaImage, proxy.getImageInfo().getRotationDegrees()), proxy::close);
        } else {
            zxingImplementation(bufferAsBytes(proxy.getPlanes()[0].getBuffer()), proxy.getWidth(), proxy.getHeight());
        }
    }

    public void processImage(byte[] data, int previewWidth, int previewHeight) {
        if (isGmsImplementationSupported()) {
            gmsImplementation(InputImage.fromByteArray(data, previewWidth, previewHeight, 0, ImageFormat.NV21), null);
        } else {
            zxingImplementation(data, previewWidth, previewHeight);
        }
    }

    private boolean isGmsImplementationSupported() {
        return barcodeScanner != null;
    }

    private void gmsImplementation(InputImage image, @Nullable Runnable onCompleteListener) {
        barcodeScanner.process(image).addOnSuccessListener(mainExecutor, barcodes -> {
            if (barcodes.isEmpty()) return;
            delegate.onQrCodeFound(barcodes.get(0).getRawValue());
        }).addOnFailureListener(Log::e).addOnCompleteListener(result -> {
            if (onCompleteListener != null) onCompleteListener.run();
        });
    }

    private void zxingImplementation(byte[] data, int width, int height) {
        backgroundExecutor.submit(() -> {
            try {
                String match = zxingImplementationImpl(data, width, height);
                if (match != null && !match.isEmpty()) delegate.onQrCodeFound(match);
            } catch (Exception ex) { ex.printStackTrace(); }
        });
    }

    private String zxingImplementationImpl(byte[] data, int width, int height) throws FormatException, ChecksumException, NotFoundException {
        PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                data,
                width,
                height,
                0,
                0,
                width,
                height,
                false
        );

        BinaryBitmap bb = new BinaryBitmap(new HybridBinarizer(source));
        Result result = zxingReader.decode(bb);
        if (result == null) return null;
        return result.getText();
    }

    private byte[] bufferAsBytes(ByteBuffer buffer) {
        buffer.rewind();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }
}
