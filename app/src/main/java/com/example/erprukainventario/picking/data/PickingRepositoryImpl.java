package com.example.erprukainventario.picking.data;

import android.util.Log;

import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.core.data.DemoCatalog;
import com.example.erprukainventario.core.data.Docs;
import com.example.erprukainventario.core.data.FirestoreCollections;
import com.example.erprukainventario.core.data.GuardedCommit;
import com.example.erprukainventario.core.data.LotRepository;
import com.example.erprukainventario.core.data.ProductRepository;
import com.example.erprukainventario.core.data.WorkContext;
import com.example.erprukainventario.core.domain.OperationResult;
import com.example.erprukainventario.core.domain.Lot;
import com.example.erprukainventario.core.domain.Product;
import com.example.erprukainventario.core.scan.ScanConfig;
import com.example.erprukainventario.core.scan.ScanLine;
import com.example.erprukainventario.core.scan.ScanLines;
import com.example.erprukainventario.core.scan.ScanTask;
import com.example.erprukainventario.core.scan.ScanTaskSource;
import com.example.erprukainventario.picking.domain.PickingOrder;
import com.example.erprukainventario.picking.domain.PickingStatus;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Modelo: PICKING_ORDERS/{id} (companyId, warehouseId, code, customerName, dueLabel, status,
 *                              lineCount, assignedToId, assignedToName, pickedAt,
 *                              guideNumber, carrier, plate, dispatchedAt)
 *         PICKING_ORDERS/{id}/ITEMS/{id} (productId, productCode, productName, barcode, location,
 *                              requestedQuantity, pickedQuantity, verifiedQuantity)
 *
 * El stock no se descuenta en el picking (el producto sigue en la bodega),
 * sino al despachar: ver DispatchRepositoryImpl.
 */
@Singleton
public class PickingRepositoryImpl implements PickingRepository, ScanTaskSource {

    private static final String TAG = "PickingRepo";

    private final FirebaseFirestore firestore;
    private final WorkContext context;
    private final DemoCatalog catalog;
    private final ProductRepository products;
    private final LotRepository lots;
    private final GuardedCommit guarded;

    @Inject
    public PickingRepositoryImpl(FirebaseFirestore firestore, WorkContext context, DemoCatalog catalog,
                                 ProductRepository products, LotRepository lots, GuardedCommit guarded) {
        this.firestore = firestore;
        this.context = context;
        this.catalog = catalog;
        this.products = products;
        this.lots = lots;
        this.guarded = guarded;
    }

    @Override
    public void getOrders(RepositoryCallback<List<PickingOrder>> callback) {
        getOrdersOfWarehouse(firestore, context, callback);
    }

    /** Compartido con Despacho: misma colección, mismo filtro. */
    public static void getOrdersOfWarehouse(FirebaseFirestore firestore, WorkContext context,
                                     RepositoryCallback<List<PickingOrder>> callback) {
        String companyId = context.companyId();
        if (companyId == null) {
            callback.onResult(new ArrayList<>());
            return;
        }
        firestore.collection(FirestoreCollections.PICKING_ORDERS)
                .whereEqualTo("companyId", companyId)
                .whereEqualTo("warehouseId", context.warehouseId())
                .get()
                .addOnCompleteListener(task -> {
                    List<PickingOrder> orders = new ArrayList<>();
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                            orders.add(PickingMapper.toOrder(doc));
                        }
                        Collections.sort(orders, (a, b) -> a.getCode().compareToIgnoreCase(b.getCode()));
                    } else {
                        Log.e(TAG, "Consulta falló", task.getException());
                    }
                    callback.onResult(orders);
                });
    }

    @Override
    public void assignToMe(String orderId, String expectedAssigneeId, boolean markInProgress,
                           RepositoryCallback<OperationResult> callback) {
        DocumentReference ref = firestore.collection(FirestoreCollections.PICKING_ORDERS).document(orderId);
        String expected = expectedAssigneeId != null ? expectedAssigneeId : "";
        guarded.run(ref, doc -> {
            if (!doc.exists()) return "El pedido ya no existe.";
            PickingOrder current = PickingMapper.toOrder(doc);
            if (!current.getStatus().isPickable()) return "El pedido ya está " + current.getStatus().getLabel().toLowerCase(Locale.ROOT) + ".";
            String now = current.getAssignedToId();
            // Alguien lo tomó después de que el usuario vio la lista.
            if (!now.equals(expected) && !current.isAssignedTo(context.userId())) {
                return current.getAssignedToName() + " acaba de tomar este pedido.";
            }
            return null;
        }, (doc, w) -> {
            Map<String, Object> data = new HashMap<>();
            data.put("assignedToId", context.userId());
            data.put("assignedToName", context.userName());
            if (markInProgress && PickingMapper.toOrder(doc).getStatus() == PickingStatus.PENDIENTE) {
                data.put("status", PickingStatus.EN_PROCESO.name());
            }
            w.update(ref, data);
            return "Pedido asignado a ti.";
        }, "No se pudo tomar el pedido.", callback);
    }

    // ------------------------------------------------------------------
    // ScanTaskSource (modo PICKING)
    // ------------------------------------------------------------------

    @Override
    public void load(String taskId, RepositoryCallback<ScanTask> callback) {
        DocumentReference ref = firestore.collection(FirestoreCollections.PICKING_ORDERS).document(taskId);
        Task<DocumentSnapshot> orderTask = ref.get();
        Task<QuerySnapshot> itemsTask = ref.collection(FirestoreCollections.ITEMS).get();

        Tasks.whenAllSuccess(orderTask, itemsTask)
                .addOnSuccessListener(r -> {
                    DocumentSnapshot doc = orderTask.getResult();
                    if (!doc.exists()) {
                        callback.onResult(null);
                        return;
                    }
                    PickingOrder order = PickingMapper.toOrder(doc);
                    if (!order.getStatus().isPickable()) {
                        callback.onResult(null);
                        return;
                    }
                    List<ScanLine> lines = new ArrayList<>();
                    for (DocumentSnapshot item : itemsTask.getResult().getDocuments()) {
                        lines.add(ScanLines.fromDoc(item,
                                Docs.num(item, "requestedQuantity", "quantity"),
                                Docs.num(item, "pickedQuantity")));
                    }
                    ScanConfig config = new ScanConfig.Builder("Picking · ruta por ubicación")
                            .quantityLabel("Cantidad recolectada")
                            .finishLabel("Finalizar picking")
                            .incompleteWarning("Hay productos sin recolectar por completo. El pedido quedará "
                                    + "como PARCIAL y lo faltante como backorder.")
                            .build();
                    // La ruta usa la ubicación vigente del producto (pudo reubicarse después
                    // de crear el pedido); si no se encuentra, la guardada en la línea.
                    // Además se sugiere el lote que vence primero (FEFO, guía §10).
                    products.getProducts(all -> lots.getActiveLots(activeLots -> {
                        Map<String, String> locationById = new HashMap<>();
                        for (Product p : all) locationById.put(p.getId(), p.getLocation());
                        Map<String, Lot> firstLot = new HashMap<>();
                        for (Lot lot : activeLots) { // ya vienen ordenados por vencimiento
                            if (!firstLot.containsKey(lot.getProductId())) firstLot.put(lot.getProductId(), lot);
                        }
                        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM", Locale.getDefault());
                        List<ScanLine> routed = new ArrayList<>();
                        for (ScanLine line : lines) {
                            String current = locationById.get(line.getProductId());
                            String location = current != null ? current : line.getLocation();
                            Lot lot = firstLot.get(line.getProductId());
                            if (lot != null) {
                                location += " · Lote " + lot.getLotNumber()
                                        + (lot.getExpiryDate() != null ? " (vence " + fmt.format(lot.getExpiryDate()) + ")" : "");
                            }
                            routed.add(line.withLocation(location));
                        }
                        callback.onResult(new ScanTask(order.getId(), order.getCode(),
                                order.getCustomerName(), ScanLines.sortedByLocation(routed), config));
                    }));
                })
                .addOnFailureListener(e -> callback.onResult(null));
    }

    @Override
    public void save(String taskId, List<ScanLine> lines, Map<String, String> extras,
                     RepositoryCallback<OperationResult> callback) {
        int requestedSum = 0;
        int pickedSum = 0;
        for (ScanLine line : lines) {
            requestedSum += line.getExpectedQuantity();
            pickedSum += line.getScannedQuantity();
        }
        final int requested = requestedSum;
        final int picked = pickedSum;
        final PickingStatus status = picked <= 0 ? PickingStatus.EN_PROCESO
                : picked >= requested ? PickingStatus.PREPARADO : PickingStatus.PARCIAL;

        DocumentReference orderRef = firestore.collection(FirestoreCollections.PICKING_ORDERS).document(taskId);
        guarded.run(orderRef, doc -> {
            if (!doc.exists()) return "El pedido ya no existe.";
            PickingOrder current = PickingMapper.toOrder(doc);
            if (!current.getStatus().isPickable() && current.getStatus() != PickingStatus.PREPARADO) {
                return "El pedido ya fue " + current.getStatus().getLabel().toLowerCase(Locale.ROOT) + " por otro usuario.";
            }
            if (current.getStatus() == PickingStatus.PREPARADO && !current.isAssignedTo(context.userId())) {
                return current.getAssignedToName() + " ya terminó de preparar este pedido.";
            }
            if (!current.getAssignedToId().isEmpty() && !current.isAssignedTo(context.userId())) {
                return current.getAssignedToName() + " tomó este pedido mientras lo preparabas. "
                        + "Vuelve a la lista para ver su avance.";
            }
            return null;
        }, (doc, w) -> {
            for (ScanLine line : lines) {
                w.update(orderRef.collection(FirestoreCollections.ITEMS).document(line.getId()),
                        "pickedQuantity", line.getScannedQuantity());
            }
            Map<String, Object> order = new HashMap<>();
            order.put("status", status.name());
            order.put("assignedToId", context.userId());
            order.put("assignedToName", context.userName());
            order.put("pickedAt", FieldValue.serverTimestamp());
            w.update(orderRef, order);

            if (status == PickingStatus.PREPARADO) {
                return "Pedido preparado (" + picked + " un.). Ya está disponible en Despacho.";
            } else if (status == PickingStatus.PARCIAL) {
                return "Pedido parcial: " + picked + " un. recolectadas, " + (requested - picked)
                        + " un. quedan como backorder. Puedes despacharlo parcial o completarlo después.";
            }
            return "Avance guardado. El pedido sigue asignado a ti.";
        }, "No se pudo guardar el picking.", callback);
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
            // {código, cliente, plazo, estado, asignarme}
            String[][] orders = {
                    {"PK-2001", "Ferretería Don Pedro", "Salida hoy 15:00", "PENDIENTE", "no"},
                    {"PK-2002", "Constructora Biobío", "Salida hoy 17:30", "PENDIENTE", "si"},
                    {"PK-2003", "Transferencia a Sucursal Chillán", "Salida mañana", "PENDIENTE", "no"},
                    {"PK-2004", "Comercial Lota", "Salida hoy 12:00", "PREPARADO", "si"}
            };
            String[][] lines = {
                    {"PROD-105", "10"}, {"PROD-101", "6"}, {"PROD-103", "4"}, {"PROD-107", "1"}
            };

            WriteBatch batch = firestore.batch();
            for (int i = 0; i < orders.length; i++) {
                DocumentReference ref = firestore.collection(FirestoreCollections.PICKING_ORDERS).document();
                boolean prepared = PickingStatus.PREPARADO.name().equals(orders[i][3]);
                boolean mine = "si".equals(orders[i][4]);
                int count = 2 + (i % 3); // 2 a 4 líneas

                Map<String, Object> order = new HashMap<>();
                order.put("companyId", context.companyId());
                order.put("warehouseId", context.warehouseId());
                order.put("code", orders[i][0]);
                order.put("customerName", orders[i][1]);
                order.put("dueLabel", orders[i][2]);
                order.put("status", orders[i][3]);
                order.put("lineCount", count);
                order.put("assignedToId", mine ? context.userId() : "");
                order.put("assignedToName", mine ? context.userName() : "");
                batch.set(ref, order);

                for (int j = 0; j < count; j++) {
                    String[] l = lines[(i + j) % lines.length];
                    int qty = Integer.parseInt(l[1]) + i;
                    Map<String, Object> item = catalog.lineFromCatalog(l[0]);
                    item.put("requestedQuantity", qty);
                    item.put("pickedQuantity", prepared ? qty : 0);
                    batch.set(ref.collection(FirestoreCollections.ITEMS).document(), item);
                }
            }
            batch.commit()
                    .addOnSuccessListener(v -> callback.onResult(true))
                    .addOnFailureListener(e -> callback.onResult(false));
        });
    }
}
