package com.example.erprukainventario.dispatch.data;

import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.core.data.Docs;
import com.example.erprukainventario.core.data.FirestoreCollections;
import com.example.erprukainventario.core.data.InventoryWriter;
import com.example.erprukainventario.core.data.WorkContext;
import com.example.erprukainventario.core.domain.MovementType;
import com.example.erprukainventario.core.domain.OperationResult;
import com.example.erprukainventario.core.scan.ScanConfig;
import com.example.erprukainventario.core.scan.ScanLine;
import com.example.erprukainventario.core.scan.ScanLines;
import com.example.erprukainventario.core.scan.ScanTask;
import com.example.erprukainventario.core.scan.ScanTaskSource;
import com.example.erprukainventario.picking.data.PickingMapper;
import com.example.erprukainventario.picking.data.PickingRepositoryImpl;
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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Despacho = control de salida (guía §6). Trabaja sobre PICKING_ORDERS:
 * se verifica escaneando cada producto contra lo preparado, se piden los datos
 * del transporte y se genera el número de guía. Recién aquí sale el stock.
 */
@Singleton
public class DispatchRepositoryImpl implements DispatchRepository, ScanTaskSource {

    public static final String PROMPT_CARRIER = "Transportista";
    public static final String PROMPT_PLATE = "Patente del vehículo";

    private final FirebaseFirestore firestore;
    private final WorkContext context;
    private final InventoryWriter inventory;

    @Inject
    public DispatchRepositoryImpl(FirebaseFirestore firestore, WorkContext context, InventoryWriter inventory) {
        this.firestore = firestore;
        this.context = context;
        this.inventory = inventory;
    }

    @Override
    public void getOrders(RepositoryCallback<List<PickingOrder>> callback) {
        PickingRepositoryImpl.getOrdersOfWarehouse(firestore, context, callback);
    }

    // ------------------------------------------------------------------
    // ScanTaskSource (modo DISPATCH)
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
                    if (!order.getStatus().isReadyToDispatch()) {
                        callback.onResult(null);
                        return;
                    }
                    // Se verifica lo PREPARADO, no lo pedido: lo no recolectado es backorder.
                    // La verificación parte de cero cada vez (es un control nuevo).
                    List<ScanLine> lines = new ArrayList<>();
                    for (DocumentSnapshot item : itemsTask.getResult().getDocuments()) {
                        int picked = Docs.num(item, "pickedQuantity");
                        if (picked > 0) lines.add(ScanLines.fromDoc(item, picked, 0));
                    }
                    ScanConfig config = new ScanConfig.Builder("Despacho · control de salida")
                            .quantityLabel("Cantidad verificada")
                            .finishLabel("Confirmar despacho")
                            .requireComplete()
                            .finishPrompts(PROMPT_CARRIER, PROMPT_PLATE)
                            .build();
                    callback.onResult(new ScanTask(order.getId(), order.getCode(),
                            order.getCustomerName(), ScanLines.sortedByLocation(lines), config));
                })
                .addOnFailureListener(e -> callback.onResult(null));
    }

    @Override
    public void save(String taskId, List<ScanLine> lines, Map<String, String> extras,
                     RepositoryCallback<OperationResult> callback) {
        DocumentReference orderRef = firestore.collection(FirestoreCollections.PICKING_ORDERS).document(taskId);
        orderRef.get().addOnSuccessListener(doc -> {
            if (!doc.exists()) {
                callback.onResult(OperationResult.error("El pedido ya no existe."));
                return;
            }
            PickingOrder order = PickingMapper.toOrder(doc);
            // Evita despachar dos veces si otro dispositivo se adelantó.
            if (!order.getStatus().isReadyToDispatch()) {
                callback.onResult(OperationResult.error("El pedido ya fue despachado por otro usuario."));
                return;
            }

            String guide = newGuideNumber();
            String carrier = value(extras, PROMPT_CARRIER);
            String plate = value(extras, PROMPT_PLATE).toUpperCase(Locale.ROOT);

            WriteBatch batch = firestore.batch();
            int units = 0;
            for (ScanLine line : lines) {
                units += line.getScannedQuantity();
                batch.update(orderRef.collection(FirestoreCollections.ITEMS).document(line.getId()),
                        "verifiedQuantity", line.getScannedQuantity());
                inventory.apply(batch, MovementType.DESPACHO, line.getProductId(), line.getProductCode(),
                        line.getProductName(), -line.getScannedQuantity(), guide + " / " + order.getCode());
            }

            Map<String, Object> data = new HashMap<>();
            data.put("status", PickingStatus.DESPACHADO.name());
            data.put("guideNumber", guide);
            data.put("carrier", carrier);
            data.put("plate", plate);
            data.put("dispatchedAt", FieldValue.serverTimestamp());
            data.put("dispatchedById", context.userId());
            data.put("dispatchedByName", context.userName());
            data.put("backorder", order.getStatus() == PickingStatus.PARCIAL);
            batch.update(orderRef, data);

            final int totalUnits = units;
            batch.commit()
                    .addOnSuccessListener(v -> callback.onResult(OperationResult.ok(
                            "Guía de despacho " + guide + " generada.\n\n"
                                    + "Pedido: " + order.getCode() + " · " + order.getCustomerName() + "\n"
                                    + "Transportista: " + carrier + "\n"
                                    + "Patente: " + plate + "\n"
                                    + "Total: " + totalUnits + " un. en " + lines.size() + " líneas"
                                    + (order.getStatus() == PickingStatus.PARCIAL
                                    ? "\n\nDespacho parcial: lo no recolectado queda como backorder." : ""))))
                    .addOnFailureListener(e -> callback.onResult(
                            OperationResult.error("No se pudo registrar el despacho.")));
        }).addOnFailureListener(e -> callback.onResult(OperationResult.error("Sin conexión con el servidor.")));
    }

    /** GD-AAMMDD-HHMMSS: único por segundo y legible para el transportista. */
    private static String newGuideNumber() {
        return "GD-" + new SimpleDateFormat("yyMMdd-HHmmss", Locale.ROOT).format(new Date());
    }

    private static String value(Map<String, String> extras, String key) {
        String v = extras != null ? extras.get(key) : null;
        return v != null ? v.trim() : "";
    }
}
