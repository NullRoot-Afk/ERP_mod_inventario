package com.example.erprukainventario.core.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AlertDialog;

import com.example.erprukainventario.R;
import com.example.erprukainventario.core.domain.Lot;
import com.example.erprukainventario.databinding.ItemLotBinding;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/** Pinta lotes (item_lot.xml) en un contenedor: lo usan la consulta de producto y el dashboard. */
public final class LotViews {

    public interface OnRetire {
        void retire(Lot lot);
    }

    private LotViews() {
    }

    /**
     * @param showProduct true en listas de varios productos (dashboard).
     * @param onRetire    null oculta el botón de retiro.
     */
    public static void fill(ViewGroup container, List<Lot> lots, boolean showProduct, OnRetire onRetire) {
        container.removeAllViews();
        Context ctx = container.getContext();
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        LayoutInflater inflater = LayoutInflater.from(ctx);

        for (Lot lot : lots) {
            ItemLotBinding b = ItemLotBinding.inflate(inflater, container, false);
            String title = (showProduct ? lot.getProductName() + " · " : "")
                    + "Lote " + lot.getLotNumber() + " · " + lot.getQuantity() + " un.";
            b.tvTitle.setText(title);

            String expiry = lot.getExpiryDate() != null
                    ? ctx.getString(R.string.lot_expires_format, format.format(lot.getExpiryDate()))
                    : ctx.getString(R.string.lot_no_expiry);
            String ref = lot.getReference().isEmpty() ? "" : " · " + lot.getReference();
            b.tvDetail.setText(expiry + ref);

            if (lot.isRetired()) {
                badge(b, ctx.getString(R.string.lot_retired), R.drawable.bg_badge_neutral, R.color.text_secondary);
            } else if (lot.getQuantity() <= 0) {
                badge(b, ctx.getString(R.string.lot_depleted), R.drawable.bg_badge_neutral, R.color.text_secondary);
            } else if (lot.isExpired()) {
                badge(b, ctx.getString(R.string.lot_expired_format, -lot.daysToExpiry()),
                        R.drawable.bg_badge_danger, R.color.text_danger);
            } else if (lot.needsAlert()) {
                badge(b, ctx.getResources().getQuantityString(R.plurals.lot_expiring_days,
                        lot.daysToExpiry(), lot.daysToExpiry()), R.drawable.bg_badge_warning, R.color.text_warning);
            }

            if (onRetire != null && lot.needsAlert()) {
                b.btnRetire.setVisibility(View.VISIBLE);
                b.btnRetire.setOnClickListener(v -> new AlertDialog.Builder(ctx)
                        .setTitle(R.string.lot_retire_title)
                        .setMessage(ctx.getString(R.string.lot_retire_message_format,
                                lot.getLotNumber(), lot.getProductName(), lot.getQuantity()))
                        .setPositiveButton(R.string.lot_retire, (d, w) -> onRetire.retire(lot))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show());
            }
            container.addView(b.getRoot());
        }
    }

    private static void badge(ItemLotBinding b, String text, int background, int color) {
        b.tvBadge.setVisibility(View.VISIBLE);
        b.tvBadge.setText(text);
        b.tvBadge.setBackgroundResource(background);
        b.tvBadge.setTextColor(b.getRoot().getContext().getColor(color));
    }
}
