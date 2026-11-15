package com.example.erprukainventario.transfer.data;

import android.util.Log;

import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.auth.domain.Warehouse;
import com.example.erprukainventario.core.data.DemoCatalog;
import com.example.erprukainventario.core.data.Docs;
import com.example.erprukainventario.core.data.FirestoreCollections;
import com.example.erprukainventario.core.data.GuardedCommit;
import com.example.erprukainventario.core.data.InventoryWriter;
import com.example.erprukainventario.core.data.LotRepository;
import com.example.erprukainventario.core.data.ProductRepository;
import com.example.erprukainventario.core.data.SerialRepository;
import com.example.erprukainventario.core.data.WorkContext;
import com.example.erprukainventario.core.domain.MovementType;
import com.example.erprukainventario.core.domain.OperationResult;
import com.example.erprukainventario.core.domain.SerialStatus;
import com.example.erprukainventario.core.domain.Serials;
import com.example.erprukainventario.core.scan.ScanConfig;
import com.example.erprukainventario.core.scan.ScanLine;
import com.example.erprukainventario.core.scan.ScanLines;
import com.example.erprukainventario.core.scan.ScanTask;
import com.example.erprukainventario.core.scan.ScanTaskSource;
import com.example.erprukainventario.transfer.domain.Transfer;
import com.example.erprukainventario.transfer.domain.TransferStatus;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
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
 * Modelo: TRANSFERS/{id} (companyId, code, originWarehouseId, originWarehouseName,
 *                         destinationWarehouseId, destinationWarehouseName, status, lineCount,
 *                         notes, createdByName, carrier, sentAt, receivedAt, hasDiscrepancy)
 *         TRANSFERS/{id}/ITEMS/{id} (productId, productCode, productName, barcode, location,
 *                         quantity, sentQuantity, receivedQuantity, lots, serials, receivedSerials)
 *
 * Flujo: POR_ENVIAR (origen escanea y despacha: sale stock del origen)
 *        -> EN_TRANSITO (destino escanea al recibir: entra stock al destino) -> RECIBIDA.
 */
@Singleton
public class TransferRepositoryImpl implements TransferRepository {

    private static final String TAG = "TransferRepo";
    public static final String PROMPT_CARRIER = "Transportista";

    private final FirebaseFirestore firestore;
    private final WorkContext context;
    private final InventoryWriter inventory;
    private final DemoCatalog catalog;
    private final LotRepository lots;
    private final GuardedCommit guarded;
    private final ProductRepository products;
    private final SerialRepository serials;

    @Inject
    public TransferRepositoryImpl(FirebaseFirestore firestore, WorkContext context, InventoryWriter inventory,
                                  DemoCatalog catalog, LotRepository lots, GuardedCommit guarded,
                                  ProductRepository products, SerialRepository serials) {
        this.products = products;
        this.serials = serials;
        this.firestore = firestore;
        this.context = context;
        this.inventory = inventory;
        this.catalog = catalog;
        this.lots = lots;
        this.guarded = guarded;
    }

    @Override
    public void getTransfers(RepositoryCallback<List<Transfer>> callback) {
        String companyId = context.companyId();
        if (companyId == null) {
            callback.onResult(new ArrayList<>());
            return;
        }
        String me = context.warehouseId();
        firestore.collection(FirestoreCollections.TRANSFERS)
                .whereEqualTo("companyId", companyId)
                .get()
                .addOnCompleteListener(task -> {
                    List<Transfer> list = new ArrayList<>();
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                            Transfer t = toTransfer(doc);
                            if (me.equals(t.getOriginId()) || me.equals(t.getDestinationId())) list.add(t);
                        }
                        Collections.sort(list, (a, b) -> b.getCode().compareToIgnoreCase(a.getCode()));
                    } else {
                        Log.e(TAG, "Consulta falló", task.getException());
                    }
                    callback.onResult(list);
                });
    }

    @Override
    public void create(Warehouse destination, String notes, List<ScanLine> lines,
                       RepositoryCallback<OperationResult> callback) {
        if (destination == null || lines == null || lines.isEmpty() || context.companyId() == null) {
            callback.onResult(OperationResult.error("Elige un destino y agrega al menos un producto."));
            return;
        }
        if (destination.getId().equals(context.warehouseId())) {
            callback.onResult(OperationResult.error("El destino debe ser distinto de tu bodega."));
            return;
        }
        String code = newCode();
        DocumentReference ref = firestore.collection(FirestoreCollections.TRANSFERS).document();
        WriteBatch batch = firestore.batch();
        batch.set(ref, header(code, context.warehouseId(), context.warehouseName(),
                destination.getId(), destination.getName(), notes, lines.size()));
        for (ScanLine line : lines) {
            Map<String, Object> item = new HashMap<>();
            item.put("productId", line.getProductId());
            item.put("productCode", line.getProductCode());
            item.put("productName", line.getProductName());
            item.put("barcode", line.getBarcode());
            item.put("location", line.getLocation());
            item.put("quantity", line.getScannedQuantity());
            item.put("sentQuantity", 0);
            item.put("receivedQuantity", 0);
            batch.set(ref.collection(FirestoreCollections.ITEMS).document(), item);
        }
        guarded.commitLocally(batch, "Transferencia " + code + " creada. Queda en \"Por enviar\".",
                "No se pudo crear la transferencia.", callback);
    }

    private Map<String, Object> header(String code, String originId, String originName, String destId,
                                       String destName, String notes, int lineCount) {
        Map<String, Object> data = new HashMap<>();
        data.put("companyId", context.companyId());
        data.put("code", code);
        data.put("originWarehouseId", originId);
        data.put("originWarehouseName", originName);
        data.put("destinationWarehouseId", destId);
        data.put("destinationWarehouseName", destName);
        data.put("status", TransferStatus.POR_ENVIAR.name());
        data.put("lineCount", lineCount);
        data.put("notes", notes != null ? notes : "");
        data.put("createdByName", context.userName());
        data.put("createdAt", FieldValue.serverTimestamp());
        return data;
    }

    // ------------------------------------------------------------------
    // Sesiones de escaneo: envío (origen) y recepción (destino)
    // ------------------------------------------------------------------

    public ScanTaskSource sendSource() {
        return new ScanTaskSource() {
            @Override
            public void load(String taskId, RepositoryCallback<ScanTask> callback) {
                loadTransfer(taskId, TransferStatus.POR_ENVIAR, true, callback);
            }

            @Override
            public void save(String taskId, List<ScanLine> lines, Map<String, String> extras,
                             RepositoryCallback<OperationResult> callback) {
                saveSend(taskId, lines, extras, callback);
            }
        };
    }

    public ScanTaskSource receiveSource() {
        return new ScanTaskSource() {
            @Override
            public void load(String taskId, RepositoryCallback<ScanTask> callback) {
                loadTransfer(taskId, TransferStatus.EN_TRANSITO, false, callback);
            }

            @Override
            public void save(String taskId, List<ScanLine> lines, Map<String, String> extras,
                             RepositoryCallback<OperationResult> callback) {
                saveReceive(taskId, lines, callback);
            }
        };
    }

    private void loadTransfer(String taskId, TransferStatus requiredStatus, boolean sending,
                              RepositoryCallback<ScanTask> callback) {
        DocumentReference ref = firestore.collection(FirestoreCollections.TRANSFERS).document(taskId);
        Task<DocumentSnapshot> headerTask = ref.get();
        Task<QuerySnapshot> itemsTask = ref.collection(FirestoreCollections.ITEMS).get();

        Tasks.whenAllSuccess(headerTask, itemsTask)
                .addOnSuccessListener(r -> {
                    DocumentSnapshot doc = headerTask.getResult();
                    if (!doc.exists()) {
                        callback.onResult(null);
                        return;
                    }
                    Transfer t = toTransfer(doc);
                    String myRole = sending ? t.getOriginId() : t.getDestinationId();
                    if (t.getStatus() != requiredStatus || !context.warehouseId().equals(myRole)) {
                        callback.onResult(null);
                        return;
                    }
                    List<ScanLine> lines = new ArrayList<>();
                    for (DocumentSnapshot item : itemsTask.getResult().getDocuments()) {
                        int expected = sending ? Docs.num(item, "quantity") : Docs.num(item, "sentQuantity");
                        if (expected <= 0) continue;
                        ScanLine line = ScanLines.fromDoc(item, expected, 0);
                        // Al recibir, las series de la línea son exactamente las que se enviaron.
                        List<String> sentSerials = sending ? null : Docs.strList(item, "serials");
                        lines.add(sentSerials != null && !sentSerials.isEmpty()
                                ? line.withSerialControl(sentSerials) : line);
                    }
                    ScanConfig config = sending
                            ? new ScanConfig.Builder("Transferencia · envío")
                            .quantityLabel("Cantidad enviada")
                            .finishLabel("Enviar transferencia")
                            .incompleteWarning("Se enviará menos de lo solicitado. La diferencia no saldrá de tu stock.")
                            .finishPrompts(PROMPT_CARRIER)
                            .build()
                            : new ScanConfig.Builder("Transferencia · recepción")
                            .quantityLabel("Cantidad recibida")
                            .finishLabel("Confirmar recepción")
                            .allowExceed()
                            .incompleteWarning("Lo recibido no calza con lo enviado. Se registrará la discrepancia.")
                            .build();
                    String subtitle = sending
                            ? "Hacia " + t.getDestinationName()
                            : "Desde " + t.getOriginName() + (t.getCarrier().isEmpty() ? "" : " · " + t.getCarrier());
                    if (!sending) {
                        callback.onResult(new ScanTask(t.getId(), t.getCode(), subtitle, lines, config));
                        return;
                    }
                    // Al enviar: los productos con número de serie piden la serie de cada unidad.
                    products.getSerializedProductIds(serialized -> callback.onResult(new ScanTask(
                            t.getId(), t.getCode(), subtitle,
                            ScanLines.withSerialControl(ScanLines.sortedByLocation(lines), serialized), config)));
                })
                .addOnFailureListener(e -> callback.onResult(null));
    }

    private void saveSend(String taskId, List<ScanLine> lines, Map<String, String> extras,
                          RepositoryCallback<OperationResult> callback) {
        int sent = 0;
        for (ScanLine line : lines) sent += line.getScannedQuantity();
        if (sent == 0) {
            callback.onResult(OperationResult.error("Escanea al menos un producto antes de enviar."));
            return;
        }
        DocumentReference ref = firestore.collection(FirestoreCollections.TRANSFERS).document(taskId);
        String carrier = extras != null && extras.get(PROMPT_CARRIER) != null ? extras.get(PROMPT_CARRIER) : "";

        Map<String, Integer> quantities = new HashMap<>();
        for (ScanLine line : lines) {
            if (line.getScannedQuantity() <= 0 || line.getProductId().isEmpty()) continue;
            Integer prev = quantities.get(line.getProductId());
            quantities.put(line.getProductId(), (prev != null ? prev : 0) + line.getScannedQuantity());
        }

        String missing = ScanLines.missingSerials(lines);
        if (missing != null) {
            callback.onResult(OperationResult.error(missing));
            return;
        }
        serials.checkInMyWarehouse(ScanLines.serialsByProduct(lines), serialError -> {
            if (serialError != null) callback.onResult(OperationResult.error(serialError));
            else commitSend(ref, lines, quantities, carrier, callback);
        });
    }

    private void commitSend(DocumentReference ref, List<ScanLine> lines, Map<String, Integer> quantities,
                            String carrier, RepositoryCallback<OperationResult> callback) {
        // FEFO: lo que sale hacia la otra bodega es lo que vence antes (guía §10).
        lots.planFefo(quantities, plan -> guarded.run(ref,
                doc -> doc.exists() && toTransfer(doc).getStatus() == TransferStatus.POR_ENVIAR
                        ? null : "La transferencia ya fue enviada por otro usuario.",
                (doc, w) -> {
                    Transfer t = toTransfer(doc);
                    int units = 0;
                    java.util.Set<String> consumed = new java.util.HashSet<>();
                    for (ScanLine line : lines) {
                        units += line.getScannedQuantity();
                        String lotLabel = consumed.add(line.getProductId())
                                ? lots.applyTakes(w, plan.get(line.getProductId())) : null;
                        Map<String, Object> item = new HashMap<>();
                        item.put("sentQuantity", line.getScannedQuantity());
                        if (lotLabel != null) item.put("lots", lotLabel);
                        if (line.isSerialized()) item.put("serials", line.getSerials());
                        w.update(ref.collection(FirestoreCollections.ITEMS).document(line.getId()), item);
                        // Las series viajan "en tránsito" hacia el destino hasta que éste las reciba.
                        serials.move(w, line.getProductId(), line.getSerials(), SerialStatus.EN_TRANSITO,
                                t.getDestinationId(), t.getCode());
                        inventory.apply(w, MovementType.TRANSFERENCIA_SALIDA, line.getProductId(), line.getProductCode(),
                                line.getProductName(), -line.getScannedQuantity(),
                                t.getCode() + " → " + t.getDestinationName(), t.getOriginId(), lotLabel,
                                Serials.join(line.getSerials()));
                    }
                    Map<String, Object> data = new HashMap<>();
                    data.put("status", TransferStatus.EN_TRANSITO.name());
                    data.put("carrier", carrier);
                    data.put("sentAt", FieldValue.serverTimestamp());
                    data.put("sentByName", context.userName());
                    w.update(ref, data);
                    return t.getCode() + " en tránsito hacia " + t.getDestinationName() + ".\n"
                            + units + " un. descontadas de tu bodega.";
                },
                "No se pudo enviar.", callback));
    }

    private void saveReceive(String taskId, List<ScanLine> lines, RepositoryCallback<OperationResult> callback) {
        String missing = ScanLines.missingSerials(lines);
        if (missing != null) {
            callback.onResult(OperationResult.error(missing));
            return;
        }
        DocumentReference ref = firestore.collection(FirestoreCollections.TRANSFERS).document(taskId);
        guarded.run(ref,
                doc -> doc.exists() && toTransfer(doc).getStatus() == TransferStatus.EN_TRANSITO
                        ? null : "La transferencia ya fue recibida por otro usuario.",
                (doc, w) -> {
                    Transfer t = toTransfer(doc);
                    StringBuilder discrepancies = new StringBuilder();
                    int units = 0;
                    for (ScanLine line : lines) {
                        units += line.getScannedQuantity();
                        Map<String, Object> item = new HashMap<>();
                        item.put("receivedQuantity", line.getScannedQuantity());
                        if (line.isSerialized()) item.put("receivedSerials", line.getSerials());
                        w.update(ref.collection(FirestoreCollections.ITEMS).document(line.getId()), item);
                        serials.move(w, line.getProductId(), line.getSerials(), SerialStatus.EN_BODEGA,
                                t.getDestinationId(), t.getCode());
                        inventory.apply(w, MovementType.TRANSFERENCIA_ENTRADA, line.getProductId(), line.getProductCode(),
                                line.getProductName(), line.getScannedQuantity(),
                                t.getCode() + " ← " + t.getOriginName(), t.getDestinationId(), null,
                                Serials.join(line.getSerials()));
                        if (line.difference() != 0) {
                            discrepancies.append("\n• ").append(line.getProductName()).append(": enviado ")
                                    .append(line.getExpectedQuantity()).append(", recibido ").append(line.getScannedQuantity());
                        }
                        // Las series enviadas que no llegaron quedan EN_TRANSITO (se pueden rastrear).
                        List<String> notArrived = new ArrayList<>(line.getAllowedSerials());
                        notArrived.removeAll(line.getSerials());
                        if (!notArrived.isEmpty()) {
                            discrepancies.append("\n• ").append(line.getProductName()).append(": series no recibidas ")
                                    .append(Serials.join(notArrived));
                        }
                    }
                    Map<String, Object> data = new HashMap<>();
                    data.put("status", TransferStatus.RECIBIDA.name());
                    data.put("receivedAt", FieldValue.serverTimestamp());
                    data.put("receivedByName", context.userName());
                    data.put("hasDiscrepancy", discrepancies.length() > 0);
                    data.put("discrepancies", discrepancies.toString().trim());
                    w.update(ref, data);
                    return t.getCode() + " recibida: " + units + " un. ingresadas a tu bodega."
                            + (discrepancies.length() > 0 ? "\n\nDiscrepancias registradas:" + discrepancies : "");
                },
                "No se pudo registrar la recepción.", callback);
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
            WriteBatch batch = firestore.batch();
            String me = context.warehouseId();
            String myName = context.warehouseName();

            // Dos por enviar desde mi bodega.
            seedTransfer(batch, "TR-DEMO-01", me, myName, catalog.branchWarehouseId(), catalog.branchWarehouseName(),
                    TransferStatus.POR_ENVIAR, new String[]{"PROD-105", "PROD-106"}, new int[]{20, 4});
            seedTransfer(batch, "TR-DEMO-02", me, myName, catalog.secondBranchWarehouseId(),
                    catalog.secondBranchWarehouseName(), TransferStatus.POR_ENVIAR,
                    new String[]{"PROD-102", "PROD-103", "PROD-108"}, new int[]{3, 5, 2});
            // Una en tránsito hacia mi bodega (ya salió del stock de la sucursal).
            seedTransfer(batch, "TR-DEMO-03", catalog.branchWarehouseId(), catalog.branchWarehouseName(), me, myName,
                    TransferStatus.EN_TRANSITO, new String[]{"PROD-104", "PROD-107"}, new int[]{6, 1});

            batch.commit()
                    .addOnSuccessListener(v -> callback.onResult(true))
                    .addOnFailureListener(e -> callback.onResult(false));
        });
    }

    private void seedTransfer(WriteBatch batch, String code, String originId, String originName, String destId,
                              String destName, TransferStatus status, String[] products, int[] qty) {
        DocumentReference ref = firestore.collection(FirestoreCollections.TRANSFERS).document();
        Map<String, Object> data = header(code, originId, originName, destId, destName, "Datos de prueba", products.length);
        data.put("status", status.name());
        if (status == TransferStatus.EN_TRANSITO) data.put("carrier", "Transportes Biobío");
        batch.set(ref, data);
        for (int i = 0; i < products.length; i++) {
            Map<String, Object> item = catalog.lineFromCatalog(products[i]);
            item.put("quantity", qty[i]);
            item.put("sentQuantity", status == TransferStatus.EN_TRANSITO ? qty[i] : 0);
            item.put("receivedQuantity", 0);
            batch.set(ref.collection(FirestoreCollections.ITEMS).document(), item);
        }
    }

    private static String newCode() {
        return "TR-" + new SimpleDateFormat("yyMMdd-HHmmss", Locale.ROOT).format(new Date());
    }

    private static Transfer toTransfer(DocumentSnapshot doc) {
        Object discrepancy = doc.get("hasDiscrepancy");
        return new Transfer(
                doc.getId(),
                Docs.str(doc, "TR-" + doc.getId(), "code"),
                Docs.str(doc, "", "originWarehouseId"),
                Docs.str(doc, "Origen", "originWarehouseName"),
                Docs.str(doc, "", "destinationWarehouseId"),
                Docs.str(doc, "Destino", "destinationWarehouseName"),
                Docs.status(doc, TransferStatus.class, TransferStatus.POR_ENVIAR),
                Docs.num(doc, "lineCount"),
                Docs.str(doc, "", "notes"),
                Docs.str(doc, "", "createdByName"),
                Docs.str(doc, "", "carrier"),
                discrepancy instanceof Boolean && (Boolean) discrepancy
        );
    }
}
