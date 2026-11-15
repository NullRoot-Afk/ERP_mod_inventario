package com.example.erprukainventario.core.scan;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

/**
 * Feedback sonoro y vibratorio al escanear (guía §2): en bodega el operario
 * suele no mirar la pantalla, así que un "bip" corto confirma y un tono doble
 * con vibración larga avisa que el código no corresponde.
 */
public class ScanFeedback {

    private static final String TAG = "ScanFeedback";

    private final Vibrator vibrator;
    private ToneGenerator toneGenerator;

    public ScanFeedback(Context context) {
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        try {
            this.toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90);
        } catch (RuntimeException e) {
            // Algunos emuladores no tienen salida de audio: se sigue solo con vibración.
            Log.w(TAG, "ToneGenerator no disponible", e);
            this.toneGenerator = null;
        }
    }

    public void success() {
        tone(ToneGenerator.TONE_PROP_BEEP, 120);
        vibrate(new long[]{0, 60});
    }

    public void error() {
        tone(ToneGenerator.TONE_PROP_NACK, 300);
        vibrate(new long[]{0, 120, 80, 120});
    }

    public void release() {
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
    }

    private void tone(int type, int durationMs) {
        if (toneGenerator != null) toneGenerator.startTone(type, durationMs);
    }

    @SuppressWarnings("deprecation")
    private void vibrate(long[] pattern) {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
        } else {
            vibrator.vibrate(pattern, -1);
        }
    }
}
