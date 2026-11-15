package com.example.erprukainventario.adjustment.data;

import android.util.Log;

import com.example.erprukainventario.adjustment.domain.Adjustment;
import com.example.erprukainventario.adjustment.domain.AdjustmentReason;
import com.example.erprukainventario.adjustment.domain.AdjustmentStatus;
import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.core.data.DemoCatalog;
import com.example.erprukainventario.core.data.Docs;
import com.example.erprukainventario.core.data.FirestoreCollections;
import com.example.erprukainventario.core.data.GuardedCommit;
import com.example.erprukainventario.core.data.InventoryWriter;
import com.example.erprukainventario.core.data.LotRepository;
import com.example.erprukainventario.core.data.SerialRepository;
import com.example.erprukainventario.core.data.WorkContext;
import com.example.erprukainventario.core.data.Writes;
import com.example.erprukainventario.core.domain.Fefo;
import com.example.erprukainventario.core.domain.Lot;
import com.example.erprukainventario.core.domain.LotTake;
import com.example.erprukainventario.core.domain.MovementType;
import com.example.erprukainventario.core.domain.OperationResult;
import com.example.erprukainventario.core.domain.Product;
import com.example.erprukainventario.core.domain.SerialStatus;
import com.example.erprukainventario.core.domain.Serials;
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
 *                           quantity (con signo), reason, notes, reference, lotId, lotNumber, serials, status,
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
    private final GuardedCommit guarded;
    private final LotRepository lots;
    private final SerialRepository serialRepository;

    @Inject
    public AdjustmentRepositoryImpl(FirebaseFirestore firestore, WorkContext context, InventoryWriter inventory,
                                    AdjustmentWriter writer, DemoCatalog catalog, GuardedCommit guarded,
                                    LotRepository lots, SerialRepository serialRepository) {
        this.serialRepository = serialRepository;
        this.firestore = firestore;
        this.context = context;
        this.inventory = inventory;
        this.writer = writer;
        this.catalog = catalog;
        this.guarded = guarded;
        this.lots = lots;
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
    public void create(Product product, Lot lot, int signedQuantity, AdjustmentReason reason, String notes,
                       List<String> serials, RepositoryCallback<OperationResult> callback) {
        if (product == null || signedQuantity == 0 || context.companyId() == null) {
            callback.onResult(OperationResult.error("No se pudo registrar el ajuste."));
            return;
        }
        List<String> serialList = serials != null ? serials : new ArrayList<>();
        if (product.isSerialized() && serialList.size() != Math.abs(signedQuantity)) {
            callback.onResult(OperationResult.error("Indica una serie por unidad (" + Math.abs(signedQuantity) + ")."));
            return;
        }
        checkSerials(product.getId(), signedQuantity, serialList, error -> {
            if (error != null) {
                callback.onResult(OperationResult.error(error));
                return;
            }
            WriteBatch batch = firestore.batch();
            writer.addPending(Writes.of(batch), product.getId(), product.getCode(), product.getName(),
                    signedQuantity, reason, notes, "Manual",
                    lot != null ? lot.getId() : null, lot != null ? lot.getLotNumber() : null, serialList);
            // Sin red responde al tiro: el reporte queda en la cola offline.
            guarded.commitLocally(batch, "", "No se pudo registrar el ajuste.", callback);
        });
    }

    /** Una rebaja exige series que estén en esta bodega; una entrada, series que no estén en la empresa. */
    private void checkSerials(String productId, int signedQuantity, List<String> serialList,
                              RepositoryCallback<String> callback) {
        if (serialList == null || serialList.isEmpty()) {
            callback.onResult(null);
            return;
        }
        Map<String, List<String>> byProduct = new HashMap<>();
        byProduct.put(productId, serialList);
        if (signedQuantity < 0) serialRepository.checkInMyWarehouse(byProduct, callback);
        else serialRepository.checkReceivable(byProduct, callback);
    }

    @Override
    public void approve(String adjustmentId, RepositoryCallback<OperationResult> callback) {
        if (!context.canApproveAdjustments()) {
            callback.onResult(OperationResult.error("Tu rol no permite aprobar ajustes."));
            return;
        }
        DocumentReference ref = firestore.collection(FirestoreCollections.ADJUSTMENTS).document(adjustmentId);
        // Los lotes del producto (y el estado de sus series) se leen antes: la transacción
        // no admite consultas, igual que el plan FEFO del despacho. Sin red, salen del caché.
        ref.get().addOnCompleteListener(read -> {
            DocumentSnapshot snap = read.isSuccessful() ? read.getResult() : null;
            Adjustment pre = snap != null && snap.exists() ? toAdjustment(snap) : null;
            String productId = pre != null ? pre.getProductId() : null;
            boolean pending = pre != null && pre.getStatus() == AdjustmentStatus.PENDIENTE;
            // Las series pudieron moverse desde que se reportó (ej. se despacharon).
            checkSerials(productId, pre != null ? pre.getQuantity() : 0,
                    pending ? pre.getSerials() : null, serialError -> {
                if (serialError != null) {
                    callback.onResult(OperationResult.error(serialError));
                    return;
                }
                lots.getLotsForProduct(productId, productLots ->
                        // Transacción: si dos supervisores aprueban a la vez, solo uno mueve el stock.
                        guarded.run(ref, AdjustmentRepositoryImpl::pendingOrError, (doc, w) -> {
                            Adjustment adj = toAdjustment(doc);
                            w.update(ref, review(AdjustmentStatus.APROBADO, ""));
                            String lotLabel = applyToLots(w, adj, productLots);
                            String serialLabel = applyToSerials(w, adj);
                            inventory.apply(w, MovementType.AJUSTE, adj.getProductId(), adj.getProductCode(),
                                    adj.getProductName(), adj.getQuantity(),
                                    adj.getReason().getLabel() + " · aprobado por " + context.userName(),
                                    context.warehouseId(), lotLabel, serialLabel);
                            return "Ajuste aprobado: " + adj.signedQuantity() + " un. de " + adj.getProductName()
                                    + (lotLabel != null ? "\nLotes: " + lotLabel : "")
                                    + (serialLabel.isEmpty() ? "" : "\nSeries: " + serialLabel);
                        }, "No se pudo aprobar.", callback));
            });
        });
    }

    /** Rebaja: las series quedan dadas de baja. Entrada: se dan de alta en esta bodega. */
    private String applyToSerials(Writes w, Adjustment adj) {
        if (adj.getSerials().isEmpty()) return "";
        String reference = "Ajuste: " + adj.getReason().getLabel();
        if (adj.getQuantity() < 0) {
            serialRepository.move(w, adj.getProductId(), adj.getSerials(), SerialStatus.BAJA,
                    context.warehouseId(), reference);
        } else {
            serialRepository.addReceived(w, adj.getProductId(), adj.getProductCode(), adj.getProductName(),
                    adj.getSerials(), reference);
        }
        return Serials.join(adj.getSerials());
    }

    /**
     * Efecto del ajuste en los lotes (guía §10). Una rebaja sale del lote indicado
     * (la caja dañada, el lote retirado) y lo que falte por FEFO; sin lote indicado,
     * FEFO puro. Una entrada solo suma a un lote si se indicó cuál.
     *
     * @return texto de lotes para el movimiento, o null si no tocó ninguno.
     */
    private String applyToLots(Writes w, Adjustment adj, List<Lot> productLots) {
        if (adj.getQuantity() < 0) {
            List<LotTake> takes = Fefo.planFor(productLots, adj.getProductId(), -adj.getQuantity(),
                    adj.hasLot() ? adj.getLotId() : null);
            return lots.applyTakes(w, takes);
        }
        if (!adj.hasLot()) return null;
        for (Lot lot : productLots) {
            if (lot.getId().equals(adj.getLotId())) return lots.addToLot(w, lot, adj.getQuantity());
        }
        return null;
    }

    private static String pendingOrError(DocumentSnapshot doc) {
        if (!doc.exists()) return "El ajuste ya no existe.";
        return toAdjustment(doc).getStatus() == AdjustmentStatus.PENDIENTE
                ? null : "Este ajuste ya fue revisado por otro usuario.";
    }

    @Override
    public void reject(String adjustmentId, String note, RepositoryCallback<OperationResult> callback) {
        if (!context.canApproveAdjustments()) {
            callback.onResult(OperationResult.error("Tu rol no permite rechazar ajustes."));
            return;
        }
        // También con guardia: rechazar un ajuste ya aprobado dejaría el stock movido sin respaldo.
        DocumentReference ref = firestore.collection(FirestoreCollections.ADJUSTMENTS).document(adjustmentId);
        guarded.run(ref, AdjustmentRepositoryImpl::pendingOrError, (doc, w) -> {
            w.update(ref, review(AdjustmentStatus.RECHAZADO, note));
            return "Ajuste rechazado.";
        }, "No se pudo rechazar.", callback);
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
                Docs.str(doc, "", "reviewNote"),
                Docs.str(doc, "", "lotId"),
                Docs.str(doc, "", "lotNumber"),
                Docs.strList(doc, "serials")
        );
    }
}
