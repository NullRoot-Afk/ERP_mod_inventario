package com.example.erprukainventario.adjustment.data;

import android.util.Log;

import com.example.erprukainventario.adjustment.domain.Adjustment;
import com.example.erprukainventario.adjustment.domain.AdjustmentReason;
import com.example.erprukainventario.adjustment.domain.AdjustmentStatus;
import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.core.data.DemoCatalog;
import com.example.erprukainventario.core.data.Docs;
import com.example.erprukainventario.core.data.FirestoreCollections;
import com.example.erprukainventario.core.data.InventoryWriter;
import com.example.erprukainventario.core.data.WorkContext;
import com.example.erprukainventario.core.domain.MovementType;
import com.example.erprukainventario.core.domain.OperationResult;
import com.example.erprukainventario.core.domain.Product;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Modelo: ADJUSTMENTS/{id} (companyId, warehouseId, productId, productCode, productName,
 *                           quantity (con signo), reason, notes, reference, status,
 *                           createdById, createdByName, createdAt,
 *                           reviewedById, reviewedByName, reviewedAt, reviewNote)
 *
 * Flujo de aprobación (guía §8): el operario reporta -> PENDIENTE; el supervisor
 * aprueba (mueve stock + MOVEMENTS en el mismo batch) o rechaza.
 */
@Singleton
public class AdjustmentRepositoryImpl implements AdjustmentRepository {

    private static final String TAG = "AdjustmentRepo";

    private final FirebaseFirestore firestore;
    private final WorkContext context;
    private final InventoryWriter inventory;
    private final AdjustmentWriter writer;
    private final DemoCatalog catalog;

    @Inject
    public AdjustmentRepositoryImpl(FirebaseFirestore firestore, WorkContext context, InventoryWriter inventory,
                                    AdjustmentWriter writer, DemoCatalog catalog) {
        this.firestore = firestore;
        this.context = context;
        this.inventory = inventory;
        this.writer = writer;
        this.catalog = catalog;
    }

    @Override
    public void getAdjustments(RepositoryCallback<List<Adjustment>> callback) {
        String companyId = context.companyId();
        if (companyId == null) {
            callback.onResult(new ArrayList<>());
            return;
        }
        firestore.collection(FirestoreCollections.ADJUSTMENTS)
                .whereEqualTo("companyId", companyId)
                .whereEqualTo("warehouseId", context.warehouseId())
                .get()
                .addOnCompleteListener(task -> {
                    List<Adjustment> list = new ArrayList<>();
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                            list.add(toAdjustment(doc));
                        }
                        Collections.sort(list, (a, b) -> {
                            if (a.getCreatedAt() == null) return -1;
                            if (b.getCreatedAt() == null) return 1;
                            return b.getCreatedAt().compareTo(a.getCreatedAt());
                        });
                    } else {
                        Log.e(TAG, "Consulta falló", task.getException());
                    }
                    callback.onResult(list);
                });
    }

    @Override
    public void create(Product product, int signedQuantity, AdjustmentReason reason, String notes,
                       RepositoryCallback<Boolean> callback) {
        if (product == null || signedQuantity == 0 || context.companyId() == null) {
            callback.onResult(false);
            return;
        }
        WriteBatch batch = firestore.batch();
        writer.addPending(batch, product.getId(), product.getCode(), product.getName(),
                signedQuantity, reason, notes, "Manual");
        batch.commit()
                .addOnSuccessListener(v -> callback.onResult(true))
                .addOnFailureListener(e -> callback.onResult(false));
    }

    @Override
    public void approve(String adjustmentId, RepositoryCallback<OperationResult> callback) {
        if (!context.canApproveAdjustments()) {
            callback.onResult(OperationResult.error("Tu rol no permite aprobar ajustes."));
            return;
        }
        DocumentReference ref = firestore.collection(FirestoreCollections.ADJUSTMENTS).document(adjustmentId);
        ref.get().addOnSuccessListener(doc -> {
            if (!doc.exists()) {
                callback.onResult(OperationResult.error("El ajuste ya no existe."));
                return;
            }
            Adjustment adj = toAdjustment(doc);
            // Evita aplicar dos veces el mismo ajuste si dos supervisores aprueban a la vez.
            if (adj.getStatus() != AdjustmentStatus.PENDIENTE) {
                callback.onResult(OperationResult.error("Este ajuste ya fue revisado."));
                return;
            }
            WriteBatch batch = firestore.batch();
            batch.update(ref, review(AdjustmentStatus.APROBADO, ""));
            inventory.apply(batch, MovementType.AJUSTE, adj.getProductId(), adj.getProductCode(),
                    adj.getProductName(), adj.getQuantity(), adj.getReason().getLabel() + " · aprobado por " + context.userName());
            batch.commit()
                    .addOnSuccessListener(v -> callback.onResult(OperationResult.ok(
                            "Ajuste aprobado: " + adj.signedQuantity() + " un. de " + adj.getProductName())))
                    .addOnFailureListener(e -> callback.onResult(OperationResult.error("No se pudo aprobar.")));
        }).addOnFailureListener(e -> callback.onResult(OperationResult.error("Sin conexión con el servidor.")));
    }

    @Override
    public void reject(String adjustmentId, String note, RepositoryCallback<OperationResult> callback) {
        if (!context.canApproveAdjustments()) {
            callback.onResult(OperationResult.error("Tu rol no permite rechazar ajustes."));
            return;
        }
        firestore.collection(FirestoreCollections.ADJUSTMENTS).document(adjustmentId)
                .update(review(AdjustmentStatus.RECHAZADO, note))
                .addOnSuccessListener(v -> callback.onResult(OperationResult.ok("Ajuste rechazado.")))
                .addOnFailureListener(e -> callback.onResult(OperationResult.error("No se pudo rechazar.")));
    }

    private Map<String, Object> review(AdjustmentStatus status, String note) {
        Map<String, Object> data = new HashMap<>();
        data.put("status", status.name());
        data.put("reviewedById", context.userId());
        data.put("reviewedByName", context.userName());
        data.put("reviewedAt", FieldValue.serverTimestamp());
        data.put("reviewNote", note != null ? note : "");
        return data;
    }

    @Override
    public void seedDemoData(RepositoryCallback<Boolean> callback) {
        catalog.ensureCatalog(ok -> {
            if (!ok) {
                callback.onResult(false);
                return;
            }
            WriteBatch batch = firestore.batch();
            addSeed(batch, "PROD-105", -3, AdjustmentReason.DANADO, "Guantes rotos en caja abierta");
            addSeed(batch, "PROD-101", -1, AdjustmentReason.MERMA, "Caja humedecida");
            addSeed(batch, "PROD-108", 2, AdjustmentReason.INGRESO_NO_REGISTRADO, "Encontrado en pasillo C");
            batch.commit()
                    .addOnSuccessListener(v -> callback.onResult(true))
                    .addOnFailureListener(e -> callback.onResult(false));
        });
    }

    private void addSeed(WriteBatch batch, String code, int qty, AdjustmentReason reason, String notes) {
        String[] row = DemoCatalog.row(code);
        if (row == null) return;
        writer.addPending(batch, catalog.productId(code), code, row[1], qty, reason, notes, "Demo");
    }

    private static Adjustment toAdjustment(DocumentSnapshot doc) {
        return new Adjustment(
                doc.getId(),
                Docs.str(doc, "", "productId"),
                Docs.str(doc, "", "productCode"),
                Docs.str(doc, "Producto", "productName"),
                Docs.num(doc, "quantity"),
                AdjustmentReason.fromName(Docs.str(doc, "OTRO", "reason")),
                Docs.str(doc, "", "notes"),
                Docs.str(doc, "", "reference"),
                Docs.status(doc, AdjustmentStatus.class, AdjustmentStatus.PENDIENTE),
                Docs.str(doc, "", "createdById"),
                Docs.str(doc, "", "createdByName"),
                Docs.date(doc, "createdAt"),
                Docs.str(doc, "", "reviewedByName"),
                Docs.str(doc, "", "reviewNote")
        );
    }
}
