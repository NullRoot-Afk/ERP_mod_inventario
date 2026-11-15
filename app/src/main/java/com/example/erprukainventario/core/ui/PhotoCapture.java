package com.example.erprukainventario.core.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Toma una foto con la app de cámara del sistema y la entrega como JPEG reducido
 * (máx. {@link #MAX_SIDE_PX} px por lado y bajo {@link #MAX_BYTES}), listo para
 * guardarse como evidencia en Firestore.
 *
 * Debe construirse en onCreate (registra un ActivityResultLauncher).
 */
public class PhotoCapture {

    public interface Callback {
        /** En el hilo principal. */
        void onPhoto(byte[] jpeg, Bitmap preview);
    }

    private static final String TAG = "PhotoCapture";
    private static final int MAX_SIDE_PX = 1024;
    private static final int MAX_BYTES = 600 * 1024;

    private final AppCompatActivity activity;
    private final ActivityResultLauncher<Uri> launcher;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private File pendingFile;
    private Callback pendingCallback;

    public PhotoCapture(AppCompatActivity activity) {
        this.activity = activity;
        this.launcher = activity.registerForActivityResult(new ActivityResultContracts.TakePicture(), saved -> {
            File file = pendingFile;
            Callback callback = pendingCallback;
            pendingFile = null;
            pendingCallback = null;
            if (Boolean.TRUE.equals(saved) && file != null && callback != null) {
                process(file, callback);
            }
        });
    }

    public void take(Callback callback) {
        try {
            File dir = new File(activity.getCacheDir(), "evidence");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("No se pudo crear " + dir);
            pendingFile = File.createTempFile("evidencia_", ".jpg", dir);
            pendingCallback = callback;
            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", pendingFile);
            launcher.launch(uri);
        } catch (Exception e) {
            Log.e(TAG, "No se pudo abrir la cámara", e);
            Toast.makeText(activity, "No se pudo abrir la cámara.", Toast.LENGTH_SHORT).show();
        }
    }

    private void process(File file, Callback callback) {
        executor.execute(() -> {
            try {
                Bitmap bitmap = decodeScaled(file);
                if (bitmap == null) throw new IllegalStateException("Imagen ilegible");
                bitmap = rotateByExif(file, bitmap);
                byte[] jpeg = compress(bitmap);
                Bitmap result = bitmap;
                main.post(() -> callback.onPhoto(jpeg, result));
            } catch (Exception e) {
                Log.e(TAG, "No se pudo procesar la foto", e);
                main.post(() -> Toast.makeText(activity, "No se pudo procesar la foto.", Toast.LENGTH_SHORT).show());
            } finally {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        });
    }

    private static Bitmap decodeScaled(File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);

        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE_PX) sample *= 2;

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap decoded = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        if (decoded == null) return null;

        int longest = Math.max(decoded.getWidth(), decoded.getHeight());
        if (longest <= MAX_SIDE_PX) return decoded;
        float scale = (float) MAX_SIDE_PX / longest;
        return Bitmap.createScaledBitmap(decoded, Math.round(decoded.getWidth() * scale),
                Math.round(decoded.getHeight() * scale), true);
    }

    /** Muchas cámaras guardan la foto "acostada" y marcan la rotación en EXIF. */
    private static Bitmap rotateByExif(File file, Bitmap bitmap) {
        try (InputStream in = new java.io.FileInputStream(file)) {
            int orientation = new ExifInterface(in).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            int degrees = orientation == ExifInterface.ORIENTATION_ROTATE_90 ? 90
                    : orientation == ExifInterface.ORIENTATION_ROTATE_180 ? 180
                    : orientation == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
            if (degrees == 0) return bitmap;
            Matrix m = new Matrix();
            m.postRotate(degrees);
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
        } catch (Exception e) {
            return bitmap;
        }
    }

    private static byte[] compress(Bitmap bitmap) {
        int quality = 75;
        byte[] bytes;
        do {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
            bytes = out.toByteArray();
            quality -= 15;
        } while (bytes.length > MAX_BYTES && quality >= 30);
        return bytes;
    }
}
