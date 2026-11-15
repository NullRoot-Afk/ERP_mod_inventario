package com.example.erprukainventario.core.data;

import android.util.Log;

import com.example.erprukainventario.adjustment.data.AdjustmentWriter;
import com.example.erprukainventario.adjustment.domain.AdjustmentReason;
import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.core.domain.Lot;
import com.example.erprukainventario.core.domain.OperationResult;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Modelo: LOTS/{companyId_bodega_producto_lote} (companyId, warehouseId, productId, productCode,
 *         productName, lotNumber, expiryDate, quantity, reference, status ACTIVO|RETIRADO,
 *         createdByName, createdAt, retiredByName, retiredAt)
 *
 * La cantidad del lote es lo recibido: las salidas (picking/despacho) todavía no
 * descuentan por lote. Por eso un lote vencido se retira a mano, y el retiro
 * genera el ajuste de stock que aprueba el supervisor.
 */
@Singleton
public class LotRepositoryImpl implements LotRepository {

    private static final String TAG = "LotRepo";
    private static final String ACTIVE = "ACTIVO";
    private static final String RETIRED = "RETIRADO";

    private final FirebaseFirestore firestore;
    private final WorkContext context;
    private final AdjustmentWriter adjustments;

    @Inject
    public LotRepositoryImpl(FirebaseFirestore firestore, WorkContext context, AdjustmentWriter adjustments) {
        this.firestore = firestore;
        this.context = context;
        this.adjustments = adjustments;
    }

    @Override
    public void getLotsForProduct(String productId, RepositoryCallback<List<Lot>> callback) {
        String companyId = context.companyId();
        if (companyId == null || productId == null || productId.isEmpty()) {
            callback.onResult(new ArrayList<>());
            return;
        }
        query(firestore.collection(FirestoreCollections.LOTS)
                .whereEqualTo("companyId", companyId)
                .whereEqualTo("productId", productId), true, callback);
    }

    @Override
    public void getActiveLots(RepositoryCallback<List<Lot>> callback) {
        String companyId = context.companyId();
        if (companyId == null) {
            callback.onResult(new ArrayList<>());
            return;
        }
        query(firestore.collection(FirestoreCollections.LOTS)
                .whereEqualTo("companyId", companyId)
                .whereEqualTo("warehouseId", context.warehouseId())
                .whereEqualTo("status", ACTIVE), false, callback);
    }

    private void query(Query query, boolean onlyMyWarehouse, RepositoryCallback<List<Lot>> callback) {
        String warehouse = context.warehouseId();
        query.get().addOnCompleteListener(task -> {
            List<Lot> lots = new ArrayList<>();
            if (task.isSuccessful() && task.getResult() != null) {
                for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                    Lot lot = toLot(doc);
                    if (!onlyMyWarehouse || warehouse.equals(lot.getWarehouseId())) lots.add(lot);
                }
                Collections.sort(lots, (a, b) -> Integer.compare(a.daysToExpiry(), b.daysToExpiry()));
            } else {
                Log.e(TAG, "Consulta de lotes falló", task.getException());
            }
            callback.onResult(lots);
        });
    }

    @Override
    public void addLotReceipt(WriteBatch batch, String productId, String productCode, String productName,
                              String lotNumber, Date expiryDate, int quantity, String reference) {
        if (quantity <= 0) return;
        String lot = lotNumber != null ? lotNumber.trim().toUpperCase(Locale.ROOT) : "";
        if (lot.isEmpty()) {
            if (expiryDate == null) return; // sin lote ni vencimiento: nada que trazar
            lot = "S/L-" + new SimpleDateFormat("ddMMyyyy", Locale.ROOT).format(expiryDate);
        }
        String owner = productId != null && !productId.isEmpty() ? productId : productCode;
        // "/" no es válido en un id de documento Firestore.
        String id = (context.companyId() + "_" + context.warehouseId() + "_" + owner + "_" + lot)
                .replace('/', '-');

        Map<String, Object> data = new HashMap<>();
        data.put("companyId", context.companyId());
        data.put("warehouseId", context.warehouseId());
        data.put("productId", productId != null ? productId : "");
        data.put("productCode", productCode);
        data.put("productName", productName);
        data.put("lotNumber", lot);
        if (expiryDate != null) data.put("expiryDate", expiryDate);
        data.put("quantity", FieldValue.increment(quantity));
        data.put("reference", reference != null ? reference : "");
        data.put("status", ACTIVE);
        data.put("createdByName", context.userName());
        data.put("createdAt", FieldValue.serverTimestamp());
        batch.set(firestore.collection(FirestoreCollections.LOTS).document(id), data, SetOptions.merge());
    }

    @Override
    public void retire(Lot lot, RepositoryCallback<OperationResult> callback) {
        if (lot == null || lot.isRetired()) {
            callback.onResult(OperationResult.error("El lote ya fue retirado."));
            return;
        }
        WriteBatch batch = firestore.batch();

        Map<String, Object> data = new HashMap<>();
        data.put("status", RETIRED);
        data.put("retiredByName", context.userName());
        data.put("retiredAt", FieldValue.serverTimestamp());
        batch.update(firestore.collection(FirestoreCollections.LOTS).document(lot.getId()), data);

        String expiry = lot.getExpiryDate() != null
                ? new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(lot.getExpiryDate()) : "s/f";
        adjustments.addPending(batch, lot.getProductId(), lot.getProductCode(), lot.getProductName(),
                -lot.getQuantity(), AdjustmentReason.VENCIDO,
                "Retiro de lote " + lot.getLotNumber() + " (vence " + expiry + ")",
                "Lote " + lot.getLotNumber());

        batch.commit()
                .addOnSuccessListener(v -> callback.onResult(OperationResult.ok(
                        "Lote " + lot.getLotNumber() + " retirado. Se creó un ajuste de "
                                + lot.getQuantity() + " un. pendiente de aprobación.")))
                .addOnFailureListener(e -> callback.onResult(OperationResult.error("No se pudo retirar el lote.")));
    }

    private static Lot toLot(DocumentSnapshot doc) {
        return new Lot(
                doc.getId(),
                Docs.str(doc, "", "productId"),
                Docs.str(doc, "", "productCode"),
                Docs.str(doc, "Producto", "productName"),
                Docs.str(doc, doc.getId(), "lotNumber"),
                Docs.date(doc, "expiryDate"),
                Docs.num(doc, "quantity"),
                Docs.str(doc, "", "warehouseId"),
                Docs.str(doc, "", "reference"),
                RETIRED.equals(Docs.str(doc, ACTIVE, "status"))
        );
    }
}
