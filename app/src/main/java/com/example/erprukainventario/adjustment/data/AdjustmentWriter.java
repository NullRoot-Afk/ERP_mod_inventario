package com.example.erprukainventario.adjustment.data;

import com.example.erprukainventario.adjustment.domain.AdjustmentReason;
import com.example.erprukainventario.adjustment.domain.AdjustmentStatus;
import com.example.erprukainventario.core.data.FirestoreCollections;
import com.example.erprukainventario.core.data.Writes;
import com.example.erprukainventario.core.data.WorkContext;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Agrega una solicitud de ajuste PENDIENTE a un batch. Lo usan el formulario de
 * ajustes y el cierre de conteos (cada diferencia queda esperando aprobación).
 */
@Singleton
public class AdjustmentWriter {

    private final FirebaseFirestore firestore;
    private final WorkContext context;

    @Inject
    public AdjustmentWriter(FirebaseFirestore firestore, WorkContext context) {
        this.firestore = firestore;
        this.context = context;
    }

    public void addPending(WriteBatch batch, String productId, String productCode, String productName,
                           int signedQuantity, AdjustmentReason reason, String notes, String reference) {
        addPending(Writes.of(batch), productId, productCode, productName, signedQuantity, reason, notes, reference);
    }

    public void addPending(Writes w, String productId, String productCode, String productName,
                           int signedQuantity, AdjustmentReason reason, String notes, String reference) {
        addPending(w, productId, productCode, productName, signedQuantity, reason, notes, reference, null, null, null);
    }

    /**
     * @param lotId   lote afectado (la caja dañada, el lote retirado), o null. Al aprobar,
     *                la rebaja sale de ese lote; sin lote indicado, por FEFO.
     * @param serials series de las unidades (producto con número de serie), o null.
     */
    public void addPending(Writes w, String productId, String productCode, String productName,
                           int signedQuantity, AdjustmentReason reason, String notes, String reference,
                           String lotId, String lotNumber, java.util.List<String> serials) {
        Map<String, Object> data = new HashMap<>();
        data.put("companyId", context.companyId());
        data.put("warehouseId", context.warehouseId());
        data.put("productId", productId != null ? productId : "");
        data.put("productCode", productCode);
        data.put("productName", productName);
        data.put("quantity", signedQuantity);
        data.put("reason", reason.name());
        data.put("notes", notes != null ? notes : "");
        data.put("reference", reference != null ? reference : "");
        data.put("lotId", lotId != null ? lotId : "");
        data.put("lotNumber", lotNumber != null ? lotNumber : "");
        if (serials != null && !serials.isEmpty()) data.put("serials", serials);
        data.put("status", AdjustmentStatus.PENDIENTE.name());
        data.put("createdById", context.userId());
        data.put("createdByName", context.userName());
        data.put("createdAt", FieldValue.serverTimestamp());
        w.set(firestore.collection(FirestoreCollections.ADJUSTMENTS).document(), data);
    }
}
