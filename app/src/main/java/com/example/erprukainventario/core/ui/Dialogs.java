package com.example.erprukainventario.core.ui;

import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.erprukainventario.databinding.DialogQuantityInputBinding;

import java.util.LinkedHashMap;
import java.util.Map;

/** Diálogos reutilizados por todos los módulos (cantidad, texto, formulario corto). */
public final class Dialogs {

    public interface IntCallback { void onValue(int value); }
    public interface TextCallback { void onValue(String value); }
    public interface FormCallback { void onValues(Map<String, String> values); }

    private Dialogs() {
    }

    /**
     * Diálogo de cantidad con botones +/- (mismo layout que Recepción).
     * @param minValue 0 permite dejar una línea en cero (corrección), 1 para sumar.
     */
    public static void quantity(AppCompatActivity activity, String title, String subtitle, String meta,
                                String label, String confirmLabel, int initial, int minValue,
                                IntCallback onConfirm, Runnable onDismiss) {
        DialogQuantityInputBinding b = DialogQuantityInputBinding.inflate(activity.getLayoutInflater());
        b.tvDialogTitle.setText(title);
        b.tvDialogSubtitle.setText(subtitle);
        b.tvDialogMeta.setText(meta);
        b.tvQuantityLabel.setText(label);
        b.btnConfirmQuantity.setText(confirmLabel);
        b.etQuantity.setText(String.valueOf(initial));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(b.getRoot())
                .setOnDismissListener(d -> {
                    if (onDismiss != null) onDismiss.run();
                })
                .create();

        b.btnDialogMinus.setOnClickListener(v -> {
            int current = parse(b.etQuantity.getText());
            if (current > minValue) b.etQuantity.setText(String.valueOf(current - 1));
        });
        b.btnDialogPlus.setOnClickListener(v ->
                b.etQuantity.setText(String.valueOf(Math.max(minValue, parse(b.etQuantity.getText())) + 1)));
        b.btnConfirmQuantity.setOnClickListener(v -> {
            int value = parse(b.etQuantity.getText());
            if (value < minValue) {
                Toast.makeText(activity, "Ingresa una cantidad válida.", Toast.LENGTH_SHORT).show();
                return;
            }
            onConfirm.onValue(value);
            dialog.dismiss();
        });
        dialog.show();
    }

    /** Un campo de texto. onCancel puede ser null. */
    public static void textInput(Context context, String title, String hint, String confirmLabel,
                                 TextCallback onConfirm, Runnable onCancel) {
        EditText input = new EditText(context);
        input.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);

        new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(padded(context, input))
                .setPositiveButton(confirmLabel, (d, w) -> {
                    String value = input.getText().toString().trim();
                    if (value.isEmpty()) {
                        if (onCancel != null) onCancel.run();
                    } else {
                        onConfirm.onValue(value);
                    }
                })
                .setNegativeButton(android.R.string.cancel, (d, w) -> {
                    if (onCancel != null) onCancel.run();
                })
                .setOnCancelListener(d -> {
                    if (onCancel != null) onCancel.run();
                })
                .show();
    }

    /** Varios campos obligatorios; entrega {etiqueta: valor}. */
    public static void form(Context context, String title, String[] labels, String confirmLabel,
                            FormCallback onConfirm) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        final EditText[] fields = new EditText[labels.length];
        for (int i = 0; i < labels.length; i++) {
            EditText field = new EditText(context);
            field.setHint(labels[i]);
            field.setSingleLine(true);
            field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            fields[i] = field;
            container.addView(field);
        }

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(padded(context, container))
                .setPositiveButton(confirmLabel, null) // se reemplaza abajo para validar sin cerrar
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Map<String, String> values = new LinkedHashMap<>();
            for (int i = 0; i < labels.length; i++) {
                String value = fields[i].getText().toString().trim();
                if (value.isEmpty()) {
                    fields[i].setError("Obligatorio");
                    return;
                }
                values.put(labels[i], value);
            }
            dialog.dismiss();
            onConfirm.onValues(values);
        }));
        dialog.show();
    }

    private static LinearLayout padded(Context context, android.view.View child) {
        int pad = (int) (20 * context.getResources().getDisplayMetrics().density);
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setPadding(pad, pad / 2, pad, 0);
        wrapper.addView(child, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return wrapper;
    }

    private static int parse(CharSequence text) {
        try {
            return text == null ? 0 : Integer.parseInt(text.toString().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
