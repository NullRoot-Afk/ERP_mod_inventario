package com.example.erprukainventario.core.data;

import android.util.Log;

import com.example.erprukainventario.adjustment.data.AdjustmentWriter;
import com.example.erprukainventario.adjustment.domain.AdjustmentReason;
import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.core.domain.Fefo;
import com.example.erprukainventario.core.domain.Lot;
import com.example.erprukainventario.core.domain.LotTake;
import com.example.erprukainventario.core.domain.OperationResult;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

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
 *         productName, lotNumber, expiryDate, quantity, reference, status ACTIVO|AGOTADO|RETIRADO,
 *         createdByName, createdAt, retiredByName, retiredAt)
 *
 * quantity = unidades del lote que siguen en bodega: suma al recibir y resta al
 * despachar o transferir (FEFO). Los ajustes manuales (merma, daño) no descuentan
 * lote porque no se sabe de cuál salieron; un lote vencido se retira a mano.
 */
@Singleton
public class LotRepositoryImpl implements LotRepository {

    private static final String TAG = "LotRepo";
    static final String ACTIVE = "ACTIVO";
    static final String DEPLETED = "AGOTADO";
    static final String RETIRED = "RETIRADO";

    private final FirebaseFirestore firestore;
    private final WorkContext context;
    private final AdjustmentWriter adjustments;
    private final GuardedCommit guarded;

    @Inject
    public LotRepositoryImpl(FirebaseFirestore firestore, WorkContext context, AdjustmentWriter adjustments,
                             GuardedCommit guarded) {
        this.firestore = firestore;
        this.context = context;
        this.adjustments = adjustments;
        this.guarded = guarded;
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
                .whereEqualTo("productId", productId), true, false, callback);
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
                .whereEqualTo("status", ACTIVE), false, true, callback);
    }

    private void query(Query query, boolean onlyMyWarehouse, boolean onlyWithStock,
                       RepositoryCallback<List<Lot>> callback) {
        String warehouse = context.warehouseId();
        query.get().addOnCompleteListener(task -> {
            List<Lot> lots = new ArrayList<>();
            if (task.isSuccessful() && task.getResult() != null) {
                for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                    Lot lot = toLot(doc);
                    if (onlyMyWarehouse && !warehouse.equals(lot.getWarehouseId())) continue;
                    if (onlyWithStock && lot.getQuantity() <= 0) continue;
                    lots.add(lot);
                }
                Collections.sort(lots, (a, b) -> Integer.compare(a.daysToExpiry(), b.daysToExpiry()));
            } else {
                Log.e(TAG, "Consulta de lotes falló", task.getException());
            }
            callback.onResult(lots);
        });
    }

    @Override
    public void addLotReceipt(Writes writes, String productId, String productCode, String productName,
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
        data.put("status", ACTIVE); // un lote AGOTADO que vuelve a ingresar se reactiva
        data.put("createdByName", context.userName());
        data.put("createdAt", FieldValue.serverTimestamp());
        writes.merge(firestore.collection(FirestoreCollections.LOTS).document(id), data);
    }

    @Override
    public void planFefo(Map<String, Integer> quantities, RepositoryCallback<Map<String, List<LotTake>>> callback) {
        getActiveLots(lots -> callback.onResult(Fefo.plan(lots, quantities)));
    }

    @Override
    public String applyTakes(Writes writes, List<LotTake> takes) {
        if (takes == null || takes.isEmpty()) return null;
        StringBuilder label = new StringBuilder();
        for (LotTake t : takes) {
            Map<String, Object> data = new HashMap<>();
            data.put("quantity", FieldValue.increment(-t.getQuantity()));
            if (t.depletes()) data.put("status", DEPLETED);
            writes.update(firestore.collection(FirestoreCollections.LOTS).document(t.getLotId()), data);
            if (label.length() > 0) label.append(", ");
            label.append(t.getLotNumber()).append("×").append(t.getQuantity());
        }
        return label.toString();
    }

    @Override
    public void retire(Lot lot, RepositoryCallback<OperationResult> callback) {
        if (lot == null) {
            callback.onResult(OperationResult.error("Lote inválido."));
            return;
        }
        DocumentReference ref = firestore.collection(FirestoreCollections.LOTS).document(lot.getId());
        guarded.run(ref,
                current -> {
                    if (!current.exists()) return "El lote ya no existe.";
                    if (!ACTIVE.equals(Docs.str(current, ACTIVE, "status"))) return "El lote ya fue retirado o agotado.";
                    return Docs.num(current, "quantity") > 0 ? null : "El lote ya no tiene unidades.";
                },
                (current, w) -> {
                    int qty = Docs.num(current, "quantity"); // vigente, no la de la pantalla
                    Map<String, Object> data = new HashMap<>();
                    data.put("status", RETIRED);
                    data.put("retiredByName", context.userName());
                    data.put("retiredAt", FieldValue.serverTimestamp());
                    w.update(ref, data);

                    String expiry = lot.getExpiryDate() != null
                            ? new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(lot.getExpiryDate()) : "s/f";
                    adjustments.addPending(w, lot.getProductId(), lot.getProductCode(), lot.getProductName(),
                            -qty, AdjustmentReason.VENCIDO,
                            "Retiro de lote " + lot.getLotNumber() + " (vence " + expiry + ")",
                            "Lote " + lot.getLotNumber());
                    return "Lote " + lot.getLotNumber() + " retirado. Se creó un ajuste de "
                            + qty + " un. pendiente de aprobación.";
                },
                "No se pudo retirar el lote.", callback);
    }

    private static Lot toLot(DocumentSnapshot doc) {
        String status = Docs.str(doc, ACTIVE, "status");
        return new Lot(
                doc.getId(),
                Docs.str(doc, "", "productId"),
                Docs.str(doc, "", "productCode"),
                Docs.str(doc, "Producto", "productName"),
                Docs.str(doc, doc.getId(), "lotNumber"),
                Docs.date(doc, "expiryDate"),
                Math.max(0, Docs.num(doc, "quantity")),
                Docs.str(doc, "", "warehouseId"),
                Docs.str(doc, "", "reference"),
                RETIRED.equals(status)
        );
    }
}
