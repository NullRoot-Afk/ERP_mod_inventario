package com.example.erprukainventario.count.data;

import android.util.Log;

import com.example.erprukainventario.adjustment.data.AdjustmentWriter;
import com.example.erprukainventario.adjustment.domain.AdjustmentReason;
import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.core.data.DemoCatalog;
import com.example.erprukainventario.core.data.Docs;
import com.example.erprukainventario.core.data.FirestoreCollections;
import com.example.erprukainventario.core.data.ProductRepository;
import com.example.erprukainventario.core.data.WorkContext;
import com.example.erprukainventario.core.domain.OperationResult;
import com.example.erprukainventario.core.domain.Product;
import com.example.erprukainventario.core.scan.ScanConfig;
import com.example.erprukainventario.core.scan.ScanLine;
import com.example.erprukainventario.core.scan.ScanLines;
import com.example.erprukainventario.core.scan.ScanTask;
import com.example.erprukainventario.core.scan.ScanTaskSource;
import com.example.erprukainventario.count.domain.CountStatus;
import com.example.erprukainventario.count.domain.CountTask;
import com.example.erprukainventario.count.domain.CountType;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Modelo: COUNT_TASKS/{id} (companyId, warehouseId, name, type, status, lineCount, createdByName,
 *                           differencesCount, summary, closedByName, closedAt)
 *         COUNT_TASKS/{id}/ITEMS/{id} (productId, productCode, productName, barcode, location,
 *                           countedQuantity, systemQuantity, difference)
 *
 * El conteo es "a ciegas": el operario no ve el stock del sistema. Al cerrar se
 * compara con el stock VIGENTE (no el del momento de programarlo) y cada
 * diferencia queda como ajuste PENDIENTE de aprobación (guía §7 y §8).
 */
@Singleton
public class CountRepositoryImpl implements CountRepository, ScanTaskSource {

    private static final String TAG = "CountRepo";
    private static final int RANDOM_SAMPLE = 5;

    private final FirebaseFirestore firestore;
    private final WorkContext context;
    private final ProductRepository products;
    private final AdjustmentWriter adjustments;
    private final DemoCatalog catalog;

    @Inject
    public CountRepositoryImpl(FirebaseFirestore firestore, WorkContext context, ProductRepository products,
                               AdjustmentWriter adjustments, DemoCatalog catalog) {
        this.firestore = firestore;
        this.context = context;
        this.products = products;
        this.adjustments = adjustments;
        this.catalog = catalog;
    }

    @Override
    public void getCounts(RepositoryCallback<List<CountTask>> callback) {
        String companyId = context.companyId();
        if (companyId == null) {
            callback.onResult(new ArrayList<>());
            return;
        }
        firestore.collection(FirestoreCollections.COUNT_TASKS)
                .whereEqualTo("companyId", companyId)
                .whereEqualTo("warehouseId", context.warehouseId())
                .get()
                .addOnCompleteListener(task -> {
                    List<CountTask> list = new ArrayList<>();
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                            list.add(toCount(doc));
                        }
                        Collections.sort(list, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                    } else {
                        Log.e(TAG, "Consulta falló", task.getException());
                    }
                    callback.onResult(list);
                });
    }

    @Override
    public void createCount(CountType type, String filter, RepositoryCallback<OperationResult> callback) {
        if (!context.canCreateCounts()) {
            callback.onResult(OperationResult.error("Solo un supervisor puede programar conteos."));
            return;
        }
        doCreateCount(type, filter, callback);
    }

    /** Sin control de rol: lo usan createCount (ya validado) y los datos de prueba. */
    private void doCreateCount(CountType type, String filter, RepositoryCallback<OperationResult> callback) {
        products.getProducts(all -> {
            List<Product> selected = select(all, type, filter);
            if (selected.isEmpty()) {
                callback.onResult(OperationResult.error("No hay productos que calcen con \"" + filter + "\"."));
                return;
            }

            String name = type.getLabel() + (type == CountType.ZONA || type == CountType.CATEGORIA
                    ? " " + filter.toUpperCase(Locale.ROOT) : "");
            DocumentReference ref = firestore.collection(FirestoreCollections.COUNT_TASKS).document();
            WriteBatch batch = firestore.batch();

            Map<String, Object> count = new HashMap<>();
            count.put("companyId", context.companyId());
            count.put("warehouseId", context.warehouseId());
            count.put("name", name);
            count.put("type", type.name());
            count.put("status", CountStatus.PENDIENTE.name());
            count.put("lineCount", selected.size());
            count.put("createdByName", context.userName());
            count.put("createdAt", FieldValue.serverTimestamp());
            batch.set(ref, count);

            for (Product p : selected) {
                Map<String, Object> item = new HashMap<>();
                item.put("productId", p.getId());
                item.put("productCode", p.getCode());
                item.put("productName", p.getName());
                item.put("barcode", p.getBarcode());
                item.put("location", p.getLocation());
                item.put("countedQuantity", 0);
                batch.set(ref.collection(FirestoreCollections.ITEMS).document(), item);
            }
            batch.commit()
                    .addOnSuccessListener(v -> callback.onResult(OperationResult.ok(
                            name + " programado con " + selected.size() + " productos.")))
                    .addOnFailureListener(e -> callback.onResult(OperationResult.error("No se pudo crear el conteo.")));
        });
    }

    private static List<Product> select(List<Product> all, CountType type, String filter) {
        String f = filter != null ? filter.trim().toLowerCase(Locale.ROOT) : "";
        List<Product> result = new ArrayList<>();
        switch (type) {
            case ZONA:
                for (Product p : all) if (p.getLocation().toLowerCase(Locale.ROOT).startsWith(f)) result.add(p);
                break;
            case CATEGORIA:
                for (Product p : all) if (p.getCategory().toLowerCase(Locale.ROOT).equals(f)) result.add(p);
                break;
            case ALEATORIO:
                List<Product> shuffled = new ArrayList<>(all);
                Collections.shuffle(shuffled);
                result.addAll(shuffled.subList(0, Math.min(RANDOM_SAMPLE, shuffled.size())));
                break;
            case GENERAL:
            default:
                result.addAll(all);
        }
        return result;
    }

    // ------------------------------------------------------------------
    // ScanTaskSource (modo COUNT)
    // ------------------------------------------------------------------

    @Override
    public void load(String taskId, RepositoryCallback<ScanTask> callback) {
        DocumentReference ref = firestore.collection(FirestoreCollections.COUNT_TASKS).document(taskId);
        Task<DocumentSnapshot> countTask = ref.get();
        Task<QuerySnapshot> itemsTask = ref.collection(FirestoreCollections.ITEMS).get();

        Tasks.whenAllSuccess(countTask, itemsTask)
                .addOnSuccessListener(r -> {
                    DocumentSnapshot doc = countTask.getResult();
                    if (!doc.exists()) {
                        callback.onResult(null);
                        return;
                    }
                    CountTask count = toCount(doc);
                    if (count.getStatus() == CountStatus.CERRADO) {
                        callback.onResult(null);
                        return;
                    }
                    List<ScanLine> lines = new ArrayList<>();
                    for (DocumentSnapshot item : itemsTask.getResult().getDocuments()) {
                        // expected = 0: en conteo a ciegas no se muestra ni se usa.
                        lines.add(ScanLines.fromDoc(item, 0, Docs.num(item, "countedQuantity")));
                    }
                    ScanConfig config = new ScanConfig.Builder("Conteo físico · a ciegas")
                            .quantityLabel("Cantidad contada")
                            .finishLabel("Cerrar conteo")
                            .blind()
                            .build();
                    callback.onResult(new ScanTask(count.getId(), count.getName(),
                            count.getLineCount() + " productos · bodega " + context.warehouseName(),
                            ScanLines.sortedByLocation(lines), config));
                })
                .addOnFailureListener(e -> callback.onResult(null));
    }

    @Override
    public void save(String taskId, List<ScanLine> lines, Map<String, String> extras,
                     RepositoryCallback<OperationResult> callback) {
        DocumentReference ref = firestore.collection(FirestoreCollections.COUNT_TASKS).document(taskId);
        ref.get().addOnSuccessListener(doc -> {
            if (!doc.exists() || toCount(doc).getStatus() == CountStatus.CERRADO) {
                callback.onResult(OperationResult.error("El conteo ya fue cerrado por otro usuario."));
                return;
            }
            String countName = toCount(doc).getName();
            // Stock vigente al momento de cerrar.
            products.getProducts(all -> {
                Map<String, Product> byId = new HashMap<>();
                for (Product p : all) byId.put(p.getId(), p);

                WriteBatch batch = firestore.batch();
                StringBuilder summary = new StringBuilder();
                int differences = 0;
                for (ScanLine line : lines) {
                    Product product = byId.get(line.getProductId());
                    int system = product != null ? product.stockIn(context.warehouseId()) : 0;
                    int counted = line.getScannedQuantity();
                    int diff = counted - system;

                    Map<String, Object> item = new HashMap<>();
                    item.put("countedQuantity", counted);
                    item.put("systemQuantity", system);
                    item.put("difference", diff);
                    batch.update(ref.collection(FirestoreCollections.ITEMS).document(line.getId()), item);

                    if (diff != 0) {
                        differences++;
                        adjustments.addPending(batch, line.getProductId(), line.getProductCode(),
                                line.getProductName(), diff, AdjustmentReason.DIFERENCIA_CONTEO,
                                "Sistema " + system + " · contado " + counted, countName);
                        if (summary.length() > 0) summary.append('\n');
                        summary.append(line.getProductCode()).append(" ").append(line.getProductName())
                                .append(": sistema ").append(system).append(", contado ").append(counted)
                                .append(" (").append(diff > 0 ? "+" : "").append(diff).append(")");
                    }
                }

                Map<String, Object> data = new HashMap<>();
                data.put("status", CountStatus.CERRADO.name());
                data.put("differencesCount", differences);
                data.put("summary", summary.toString());
                data.put("closedByName", context.userName());
                data.put("closedAt", FieldValue.serverTimestamp());
                batch.update(ref, data);

                final int totalDiff = differences;
                batch.commit()
                        .addOnSuccessListener(v -> callback.onResult(OperationResult.ok(totalDiff == 0
                                ? "Conteo cerrado sin diferencias. ¡El stock calza!"
                                : "Conteo cerrado con " + totalDiff + " diferencia(s). Se enviaron como ajustes "
                                + "pendientes de aprobación:\n\n" + summary)))
                        .addOnFailureListener(e -> callback.onResult(OperationResult.error("No se pudo cerrar el conteo.")));
            });
        }).addOnFailureListener(e -> callback.onResult(OperationResult.error("Sin conexión con el servidor.")));
    }

    // ------------------------------------------------------------------
    // Datos de prueba
    // ------------------------------------------------------------------

    @Override
    public void seedDemoData(RepositoryCallback<Boolean> callback) {
        catalog.ensureCatalog(ok -> {
            if (!ok) {
                callback.onResult(false);
                return;
            }
            doCreateCount(CountType.ZONA, "A", r1 ->
                    doCreateCount(CountType.CATEGORIA, "Seguridad", r2 ->
                            doCreateCount(CountType.GENERAL, "", r3 ->
                                    callback.onResult(r1.isSuccess() && r2.isSuccess() && r3.isSuccess()))));
        });
    }

    private static CountTask toCount(DocumentSnapshot doc) {
        CountType type;
        try {
            type = CountType.valueOf(Docs.str(doc, "GENERAL", "type"));
        } catch (IllegalArgumentException e) {
            type = CountType.GENERAL;
        }
        return new CountTask(
                doc.getId(),
                Docs.str(doc, type.getLabel(), "name"),
                type,
                Docs.status(doc, CountStatus.class, CountStatus.PENDIENTE),
                Docs.num(doc, "lineCount"),
                Docs.str(doc, "", "createdByName"),
                Docs.num(doc, "differencesCount"),
                Docs.str(doc, "", "summary"),
                Docs.str(doc, "", "closedByName"),
                Docs.date(doc, "closedAt")
        );
    }
}
