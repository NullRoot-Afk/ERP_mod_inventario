package com.example.erprukainventario.core.data;

import android.util.Log;

import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.core.domain.OperationResult;
import com.example.erprukainventario.core.domain.SerialNumber;
import com.example.erprukainventario.core.domain.SerialStatus;
import com.example.erprukainventario.core.domain.Serials;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Modelo: SERIALS/{companyId_productId_serie} (companyId, productId, productCode, productName,
 *         serialNumber, status EN_BODEGA|EN_TRANSITO|DESPACHADO|BAJA, warehouseId, reference,
 *         receivedReference, updatedByName, updatedAt)
 *
 * El id determinista hace que una serie de un producto exista una sola vez en la
 * empresa. El historial completo (quién, cuándo, qué) queda en MOVEMENTS.serials;
 * aquí solo se guarda dónde está ahora.
 */
@Singleton
public class SerialRepositoryImpl implements SerialRepository {

    private static final String TAG = "SerialRepo";

    private final FirebaseFirestore firestore;
    private final WorkContext context;
    private final GuardedCommit guarded;

    @Inject
    public SerialRepositoryImpl(FirebaseFirestore firestore, WorkContext context, GuardedCommit guarded) {
        this.firestore = firestore;
        this.context = context;
        this.guarded = guarded;
    }

    @Override
    public void getSerialsForProduct(String productId, RepositoryCallback<List<SerialNumber>> callback) {
        String companyId = context.companyId();
        if (companyId == null || productId == null || productId.isEmpty()) {
            callback.onResult(new ArrayList<>());
            return;
        }
        query(firestore.collection(FirestoreCollections.SERIALS)
                .whereEqualTo("companyId", companyId)
                .whereEqualTo("productId", productId), callback);
    }

    @Override
    public void findBySerial(String serial, RepositoryCallback<List<SerialNumber>> callback) {
        String companyId = context.companyId();
        if (companyId == null || serial == null || serial.isEmpty()) {
            callback.onResult(new ArrayList<>());
            return;
        }
        query(firestore.collection(FirestoreCollections.SERIALS)
                .whereEqualTo("companyId", companyId)
                .whereEqualTo("serialNumber", serial), callback);
    }

    private void query(Query query, RepositoryCallback<List<SerialNumber>> callback) {
        query.get().addOnCompleteListener(task -> {
            List<SerialNumber> list = new ArrayList<>();
            if (task.isSuccessful() && task.getResult() != null) {
                for (DocumentSnapshot doc : task.getResult().getDocuments()) list.add(toSerial(doc));
                Collections.sort(list, (a, b) -> {
                    Date da = a.getUpdatedAt();
                    Date db = b.getUpdatedAt();
                    if (da == null && db == null) return a.getSerial().compareTo(b.getSerial());
                    if (da == null) return -1; // pendiente de sincronizar = recién movida
                    if (db == null) return 1;
                    return db.compareTo(da);
                });
            } else {
                Log.e(TAG, "Consulta de series falló", task.getException());
            }
            callback.onResult(list);
        });
    }

    @Override
    public void checkReceivable(Map<String, List<String>> serialsByProduct, RepositoryCallback<String> callback) {
        check(serialsByProduct, (serial, doc) -> {
            if (doc == null || !doc.exists()) return null;
            SerialStatus status = SerialStatus.fromName(Docs.str(doc, "", "status"));
            return SerialStatus.canReceive(status) ? null
                    : "La serie " + serial + " ya está registrada (" + status.getLabel().toLowerCase()
                    + ", " + Docs.str(doc, "", "reference") + ").";
        }, callback);
    }

    @Override
    public void checkInMyWarehouse(Map<String, List<String>> serialsByProduct, RepositoryCallback<String> callback) {
        String me = context.warehouseId();
        check(serialsByProduct, (serial, doc) -> {
            if (doc == null) return null; // sin red y sin caché: lo validan las reglas al sincronizar
            if (!doc.exists()) return "La serie " + serial + " no está registrada en la empresa.";
            SerialStatus status = SerialStatus.fromName(Docs.str(doc, "", "status"));
            if (status != SerialStatus.EN_BODEGA) {
                return "La serie " + serial + " no está disponible (" + status.getLabel().toLowerCase() + ").";
            }
            return me.equals(Docs.str(doc, "", "warehouseId")) ? null
                    : "La serie " + serial + " está en otra bodega.";
        }, callback);
    }

    private interface Rule {
        /** doc == null: no se pudo leer (sin red ni caché). */
        String error(String serial, DocumentSnapshot doc);
    }

    private void check(Map<String, List<String>> serialsByProduct, Rule rule, RepositoryCallback<String> callback) {
        String companyId = context.companyId();
        List<Task<DocumentSnapshot>> reads = new ArrayList<>();
        List<String> serials = new ArrayList<>();
        if (companyId != null && serialsByProduct != null) {
            for (Map.Entry<String, List<String>> e : serialsByProduct.entrySet()) {
                for (String serial : e.getValue()) {
                    reads.add(firestore.collection(FirestoreCollections.SERIALS)
                            .document(Serials.docId(companyId, e.getKey(), serial)).get());
                    serials.add(serial);
                }
            }
        }
        if (reads.isEmpty()) {
            callback.onResult(null);
            return;
        }
        Tasks.whenAllComplete(reads).addOnCompleteListener(all -> {
            for (int i = 0; i < reads.size(); i++) {
                Task<DocumentSnapshot> t = reads.get(i);
                String error = rule.error(serials.get(i), t.isSuccessful() ? t.getResult() : null);
                if (error != null) {
                    callback.onResult(error);
                    return;
                }
            }
            callback.onResult(null);
        });
    }

    @Override
    public void addReceived(Writes writes, String productId, String productCode, String productName,
                            List<String> serials, String reference) {
        if (serials == null || productId == null || productId.isEmpty()) return;
        for (String serial : serials) {
            Map<String, Object> data = new HashMap<>();
            data.put("companyId", context.companyId());
            data.put("productId", productId);
            data.put("productCode", productCode);
            data.put("productName", productName);
            data.put("serialNumber", serial);
            data.put("status", SerialStatus.EN_BODEGA.name());
            data.put("warehouseId", context.warehouseId());
            data.put("reference", reference != null ? reference : "");
            data.put("receivedReference", reference != null ? reference : "");
            data.put("updatedByName", context.userName());
            data.put("updatedAt", FieldValue.serverTimestamp());
            writes.set(ref(productId, serial), data);
        }
    }

    @Override
    public void registerExisting(String productId, String productCode, String productName, List<String> serials,
                                 RepositoryCallback<OperationResult> callback) {
        if (!context.canConfigureProducts()) {
            callback.onResult(OperationResult.error("Solo un supervisor o jefe de bodega registra series existentes."));
            return;
        }
        if (serials == null || serials.isEmpty()) {
            callback.onResult(OperationResult.error("Ingresa al menos una serie."));
            return;
        }
        Map<String, List<String>> byProduct = new HashMap<>();
        byProduct.put(productId, serials);
        checkReceivable(byProduct, error -> {
            if (error != null) {
                callback.onResult(OperationResult.error(error));
                return;
            }
            WriteBatch batch = firestore.batch();
            addReceived(Writes.of(batch), productId, productCode, productName, serials, REGISTER_REFERENCE);
            guarded.commitLocally(batch, serials.size() + " series registradas en tu bodega.",
                    "No se pudieron registrar las series.", callback);
        });
    }

    /** Referencia de las series dadas de alta sobre stock que ya existía. */
    static final String REGISTER_REFERENCE = "Registro de stock existente";

    @Override
    public void move(Writes writes, String productId, List<String> serials, SerialStatus status,
                     String warehouseId, String reference) {
        if (serials == null || productId == null || productId.isEmpty()) return;
        for (String serial : serials) {
            Map<String, Object> data = new HashMap<>();
            data.put("status", status.name());
            data.put("warehouseId", warehouseId);
            data.put("reference", reference != null ? reference : "");
            data.put("updatedByName", context.userName());
            data.put("updatedAt", FieldValue.serverTimestamp());
            writes.update(ref(productId, serial), data);
        }
    }

    private com.google.firebase.firestore.DocumentReference ref(String productId, String serial) {
        return firestore.collection(FirestoreCollections.SERIALS)
                .document(Serials.docId(context.companyId(), productId, serial));
    }

    private static SerialNumber toSerial(DocumentSnapshot doc) {
        return new SerialNumber(
                doc.getId(),
                Docs.str(doc, "", "productId"),
                Docs.str(doc, "", "productCode"),
                Docs.str(doc, "Producto", "productName"),
                Docs.str(doc, doc.getId(), "serialNumber"),
                SerialStatus.fromName(Docs.str(doc, "", "status")),
                Docs.str(doc, "", "warehouseId"),
                Docs.str(doc, "", "reference"),
                Docs.str(doc, "", "updatedByName"),
                Docs.date(doc, "updatedAt"));
    }
}
