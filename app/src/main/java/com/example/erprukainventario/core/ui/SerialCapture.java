package com.example.erprukainventario.core.ui;

import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.erprukainventario.R;
import com.example.erprukainventario.core.domain.Serials;
import com.example.erprukainventario.databinding.DialogSerialCaptureBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Captura de números de serie, una unidad a la vez (guía §10). Mientras está
 * abierto, la pantalla que lo muestra le pasa con {@link #offer(String)} lo que
 * lea su cámara: así se escanea la etiqueta de serie de cada unidad sin cerrar
 * el diálogo. También se puede escribir la serie a mano.
 */
public final class SerialCapture {

    public interface Callback {
        void onDone(List<String> serials);
    }

    private final AppCompatActivity activity;
    private final DialogSerialCaptureBinding b;
    private final AlertDialog dialog;
    private final List<String> serials = new ArrayList<>();
    private final int min;
    private final int max;
    private final List<String> allowed;
    private final String[] productCodes;

    /**
     * @param min, max     cuántas series se aceptan (iguales = cantidad exacta).
     * @param initial      series ya capturadas (al corregir una línea), o null.
     * @param allowed      si no está vacía, solo se aceptan estas (lo enviado en una transferencia).
     * @param productCodes EAN y SKU del producto: leerlos no cuenta como serie.
     * @param onDismiss    se llama al cerrar, con o sin confirmar.
     */
    public static SerialCapture show(AppCompatActivity activity, String productName, int min, int max,
                                     List<String> initial, List<String> allowed, String[] productCodes,
                                     Callback onDone, Runnable onDismiss) {
        return new SerialCapture(activity, productName, min, max, initial, allowed, productCodes, onDone, onDismiss);
    }

    private SerialCapture(AppCompatActivity activity, String productName, int min, int max, List<String> initial,
                          List<String> allowed, String[] productCodes, Callback onDone, Runnable onDismiss) {
        this.activity = activity;
        this.min = min;
        this.max = max;
        this.allowed = allowed != null ? allowed : Collections.<String>emptyList();
        this.productCodes = productCodes != null ? productCodes : new String[0];
        if (initial != null) serials.addAll(initial);

        b = DialogSerialCaptureBinding.inflate(activity.getLayoutInflater());
        b.tvProduct.setText(productName);
        dialog = new AlertDialog.Builder(activity)
                .setView(b.getRoot())
                .setOnDismissListener(d -> {
                    if (onDismiss != null) onDismiss.run();
                })
                .create();

        b.btnAdd.setOnClickListener(v -> addTyped());
        b.etSerial.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addTyped();
                return true;
            }
            return false;
        });
        b.btnConfirm.setOnClickListener(v -> {
            if (serials.size() < min || serials.size() > max) return;
            dialog.dismiss();
            onDone.onDone(new ArrayList<>(serials));
        });
        refresh();
        dialog.show();
    }

    public boolean isShowing() {
        return dialog.isShowing();
    }

    /**
     * Agrega una serie leída por la cámara.
     * @return null si se agregó; "" si se ignoró (era el código del producto, que suele
     *         seguir frente a la cámara); el motivo si se rechazó.
     */
    public String offer(String raw) {
        for (String code : productCodes) {
            if (code != null && raw != null && raw.trim().equalsIgnoreCase(code.trim())) return "";
        }
        return add(raw);
    }

    private String add(String raw) {
        String serial = Serials.normalize(raw);
        String error = serials.size() >= max
                ? activity.getString(R.string.serial_capture_full_format, max)
                : Serials.validateNew(serial, serials, allowed, productCodes);
        if (error != null) {
            b.tilSerial.setError(error);
            return error;
        }
        b.tilSerial.setError(null);
        serials.add(serial);
        refresh();
        return null;
    }

    private void addTyped() {
        CharSequence text = b.etSerial.getText();
        if (add(text != null ? text.toString() : "") == null) b.etSerial.setText("");
    }

    private void refresh() {
        b.tvCounter.setText(min == max
                ? activity.getString(R.string.serial_capture_counter_exact_format, serials.size(), max)
                : activity.getString(R.string.serial_capture_counter_format, serials.size()));
        boolean valid = serials.size() >= min && serials.size() <= max;
        b.btnConfirm.setEnabled(valid);
        b.btnConfirm.setAlpha(valid ? 1f : 0.5f);

        b.llSerials.removeAllViews();
        int pad = (int) (8 * activity.getResources().getDisplayMetrics().density);
        for (int i = 0; i < serials.size(); i++) {
            final String serial = serials.get(i);
            TextView row = new TextView(activity);
            row.setText(activity.getString(R.string.serial_capture_row_format, i + 1, serial));
            row.setTextColor(activity.getColor(R.color.text_primary));
            row.setTextSize(14);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, pad, 0, pad);
            row.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_close_24, 0);
            row.setContentDescription(activity.getString(R.string.serial_capture_remove_format, serial));
            // Tocar la fila la quita (lectura equivocada).
            row.setOnClickListener(v -> {
                serials.remove(serial);
                refresh();
            });
            b.llSerials.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        b.llSerials.setVisibility(serials.isEmpty() ? View.GONE : View.VISIBLE);
    }
}
