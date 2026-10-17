package com.example.erprukainventario.reception.data;

import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.auth.data.TenantConfigLocalDataSource;
import com.example.erprukainventario.auth.domain.Company;
import com.example.erprukainventario.reception.domain.PurchaseOrder;
import com.example.erprukainventario.reception.domain.PurchaseOrderDetail;
import com.example.erprukainventario.reception.domain.PurchaseOrderItem;
import com.example.erprukainventario.reception.domain.PurchaseOrderStatus;

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
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Lee/escribe recepciones directamente en Cloud Firestore.
 * El caché offline lo maneja Firestore (persistencia en disco, activa por defecto),
 * por eso no hay Room ni hilos propios: los callbacks ya llegan en el hilo principal.
 *
 * Modelo: RECEPTIONS/{id} (companyId, code, supplierName, etaLabel, status, lineCount, notes)
 *         RECEPTIONS/{id}/ITEMS/{id} (productCode, productName, barcode, orderQuantity, receivedQuantity)
 */
@Singleton
public class ReceptionRepositoryImpl implements ReceptionRepository {

    private static final String RECEPTIONS = "RECEPTIONS";
    private static final String ITEMS = "ITEMS";

    // Datos de prueba (R3). Los documentos sembrados NO llevan "notes" a propósito:
    // simulan documentos antiguos anteriores a la versión 2 del modelo (R7).
    private static final String[][] SEED_ORDERS = {
            {"OC-1001", "Distribuidora Sur", "Llegada estimada hoy"},
            {"OC-1002", "Ferretería Industrial Ltda.", "Llegada estimada mañana"},
            {"OC-1003", "Importadora Andes", "Llegada estimada en 2 días"},
            {"OC-1004", "Logística del Pacífico", "Llegada estimada en 3 días"},
            {"OC-1005", "Aceros del Biobío", "Llegada estimada en 5 días"}
    };
    private static final String[][] SEED_ITEMS = {
            {"PROD-101", "Caja de Clavos de Acero 2.5\"", "7801234567890", "20"},
            {"PROD-102", "Martillo Carpintero 16 oz", "7801234567891", "12"},
            {"PROD-103", "Cinta Métrica 5 m", "7801234567892", "30"}
    };

    private final TenantConfigLocalDataSource tenantConfig;
    private final FirebaseFirestore firestore;

    @Inject
    public ReceptionRepositoryImpl(TenantConfigLocalDataSource tenantConfig,
                                   FirebaseFirestore firestore) {
        this.tenantConfig = tenantConfig;
        this.firestore = firestore;
    }

    // ------------------------------------------------------------------
    // READ
    // ------------------------------------------------------------------

    @Override
    public void getOrders(RepositoryCallback<List<PurchaseOrder>> callback) {
        Company company = tenantConfig.getConfiguredCompany();
        if (company == null) {
            callback.onResult(new ArrayList<>());
            return;
        }

        // Una sola consulta, filtrada por empresa (no se descarga la colección completa).
        firestore.collection(RECEPTIONS)
                .whereEqualTo("companyId", company.getId())
                .get()
                .addOnCompleteListener(task -> {
                    List<PurchaseOrder> orders = new ArrayList<>();
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                            try {
                                orders.add(ReceptionMapper.toOrder(doc));
                            } catch (Exception ex) {
                                android.util.Log.e("ReceptionRepo", "Doc inválido: " + doc.getId(), ex);
                            }
                        }
                        Collections.sort(orders, (a, b) -> a.getCode().compareToIgnoreCase(b.getCode()));
                    } else {
                        android.util.Log.e("ReceptionRepo", "Consulta falló", task.getException());
                    }
                    callback.onResult(orders);
                });
    }

    @Override
    public void getOrderDetail(String orderId, RepositoryCallback<PurchaseOrderDetail> callback) {
        if (orderId == null || orderId.isEmpty()) {
            callback.onResult(null);
            return;
        }

        DocumentReference ref = firestore.collection(RECEPTIONS).document(orderId);
        Task<DocumentSnapshot> orderTask = ref.get();
        Task<QuerySnapshot> itemsTask = ref.collection(ITEMS).get();

        Tasks.whenAllSuccess(orderTask, itemsTask)
                .addOnSuccessListener(results -> {
                    DocumentSnapshot orderDoc = orderTask.getResult();
                    if (!orderDoc.exists()) {
                        callback.onResult(null);
                        return;
                    }
                    List<PurchaseOrderItem> items = new ArrayList<>();
                    for (DocumentSnapshot itemDoc : itemsTask.getResult().getDocuments()) {
                        items.add(ReceptionMapper.toItem(itemDoc));
                    }
                    Collections.sort(items, (a, b) -> a.getProductName().compareToIgnoreCase(b.getProductName()));
                    callback.onResult(ReceptionMapper.toDetail(orderDoc, items));
                })
                .addOnFailureListener(e -> callback.onResult(null));
    }

    // ------------------------------------------------------------------
    // UPDATE (avance)
    // ------------------------------------------------------------------

    @Override
    public void updateReceptionProgress(String orderId, List<PurchaseOrderItem> items,
                                        RepositoryCallback<Boolean> callback) {
        if (orderId == null || orderId.isEmpty() || items == null) {
            callback.onResult(false);
            return;
        }

        int totalOrdered = 0;
        int totalReceived = 0;
        for (PurchaseOrderItem item : items) {
            totalOrdered += item.getOrderedQuantity();
            totalReceived += item.getReceivedQuantity();
        }

        PurchaseOrderStatus status;
        if (totalReceived <= 0) status = PurchaseOrderStatus.SIN_INICIAR;
        else if (totalReceived >= totalOrdered) status = PurchaseOrderStatus.COMPLETA;
        else status = PurchaseOrderStatus.PARCIAL;

        // Un solo batch: o se guardan todos los ítems y el estado, o ninguno.
        DocumentReference orderRef = firestore.collection(RECEPTIONS).document(orderId);
        WriteBatch batch = firestore.batch();
        for (PurchaseOrderItem item : items) {
            batch.update(orderRef.collection(ITEMS).document(item.getId()),
                    "receivedQuantity", item.getReceivedQuantity());
        }
        batch.update(orderRef, "status", status.name());

        batch.commit()
                .addOnSuccessListener(v -> callback.onResult(true))
                .addOnFailureListener(e -> callback.onResult(false));
    }

    // ------------------------------------------------------------------
    // CREATE / UPDATE / DELETE de órdenes
    // ------------------------------------------------------------------

    @Override
    public void createOrder(String code, String supplier, String eta, String notes,
                            RepositoryCallback<String> callback) {
        Company company = tenantConfig.getConfiguredCompany();
        if (company == null) {
            callback.onResult(null);
            return;
        }

        Map<String, Object> data = orderInfoMap(code, supplier, eta, notes);
        data.put("companyId", company.getId());   // llave foránea hacia COMPANIES
        data.put("status", PurchaseOrderStatus.SIN_INICIAR.name());
        data.put("lineCount", 0);

        DocumentReference ref = firestore.collection(RECEPTIONS).document();
        ref.set(data)
                .addOnSuccessListener(v -> callback.onResult(ref.getId()))
                .addOnFailureListener(e -> callback.onResult(null));
    }

    @Override
    public void updateOrderInfo(String orderId, String code, String supplier, String eta,
                                String notes, RepositoryCallback<Boolean> callback) {
        if (orderId == null || orderId.isEmpty()) {
            callback.onResult(false);
            return;
        }
        // update() solo toca los campos indicados: no pisa status, lineCount ni companyId.
        firestore.collection(RECEPTIONS).document(orderId)
                .update(orderInfoMap(code, supplier, eta, notes))
                .addOnSuccessListener(v -> callback.onResult(true))
                .addOnFailureListener(e -> callback.onResult(false));
    }

    /**
     * Firestore NO borra las subcolecciones al borrar el documento padre, así que se
     * eliminan primero los ITEMS y la orden en un mismo batch (sin registros huérfanos).
     * Un batch admite hasta 500 operaciones: suficiente para órdenes de este módulo.
     */
    @Override
    public void deleteOrder(String orderId, RepositoryCallback<Boolean> callback) {
        if (orderId == null || orderId.isEmpty()) {
            callback.onResult(false);
            return;
        }
        DocumentReference orderRef = firestore.collection(RECEPTIONS).document(orderId);
        orderRef.collection(ITEMS).get()
                .addOnSuccessListener(snapshot -> {
                    WriteBatch batch = firestore.batch();
                    for (DocumentSnapshot item : snapshot.getDocuments()) {
                        batch.delete(item.getReference());
                    }
                    batch.delete(orderRef);
                    batch.commit()
                            .addOnSuccessListener(v -> callback.onResult(true))
                            .addOnFailureListener(e -> callback.onResult(false));
                })
                .addOnFailureListener(e -> callback.onResult(false));
    }

    // ------------------------------------------------------------------
    // CREATE / UPDATE / DELETE de productos
    // ------------------------------------------------------------------

    @Override
    public void saveItem(String orderId, String itemId, String productCode, String productName,
                         String barcode, int orderedQuantity, RepositoryCallback<Boolean> callback) {
        if (orderId == null || orderId.isEmpty() || productName == null || productName.isEmpty()
                || orderedQuantity <= 0) {
            callback.onResult(false);
            return;
        }

        DocumentReference orderRef = firestore.collection(RECEPTIONS).document(orderId);
        orderRef.get()
                .addOnSuccessListener(orderDoc -> {
                    // Integridad: no se puede insertar un hijo cuyo padre no existe.
                    if (!orderDoc.exists()) {
                        callback.onResult(false);
                        return;
                    }

                    Map<String, Object> data = new HashMap<>();
                    data.put("productCode", productCode);
                    data.put("productName", productName);
                    data.put("barcode", barcode);
                    data.put("orderQuantity", orderedQuantity);

                    WriteBatch batch = firestore.batch();
                    if (itemId == null) {
                        data.put("receivedQuantity", 0);
                        batch.set(orderRef.collection(ITEMS).document(), data);
                        batch.update(orderRef, "lineCount", FieldValue.increment(1));
                    } else {
                        batch.update(orderRef.collection(ITEMS).document(itemId), data);
                    }
                    batch.commit()
                            .addOnSuccessListener(v -> callback.onResult(true))
                            .addOnFailureListener(e -> callback.onResult(false));
                })
                .addOnFailureListener(e -> callback.onResult(false));
    }

    @Override
    public void deleteItem(String orderId, String itemId, RepositoryCallback<Boolean> callback) {
        if (orderId == null || orderId.isEmpty() || itemId == null || itemId.isEmpty()) {
            callback.onResult(false);
            return;
        }
        DocumentReference orderRef = firestore.collection(RECEPTIONS).document(orderId);
        WriteBatch batch = firestore.batch();
        batch.delete(orderRef.collection(ITEMS).document(itemId));
        batch.update(orderRef, "lineCount", FieldValue.increment(-1));
        batch.commit()
                .addOnSuccessListener(v -> callback.onResult(true))
                .addOnFailureListener(e -> callback.onResult(false));
    }

    // ------------------------------------------------------------------
    // Datos de prueba
    // ------------------------------------------------------------------

    @Override
    public void seedDemoData(RepositoryCallback<Boolean> callback) {
        Company company = tenantConfig.getConfiguredCompany();
        if (company == null) {
            callback.onResult(false);
            return;
        }

        WriteBatch batch = firestore.batch();
        for (int i = 0; i < SEED_ORDERS.length; i++) {
            DocumentReference orderRef = firestore.collection(RECEPTIONS).document();
            boolean partial = (i == 1);

            Map<String, Object> order = new HashMap<>();
            order.put("companyId", company.getId());
            order.put("code", SEED_ORDERS[i][0]);
            order.put("supplierName", SEED_ORDERS[i][1]);
            order.put("etaLabel", SEED_ORDERS[i][2]);
            order.put("status", (partial ? PurchaseOrderStatus.PARCIAL : PurchaseOrderStatus.SIN_INICIAR).name());
            order.put("lineCount", SEED_ITEMS.length);
            batch.set(orderRef, order);

            for (int j = 0; j < SEED_ITEMS.length; j++) {
                Map<String, Object> item = new HashMap<>();
                item.put("productCode", SEED_ITEMS[j][0]);
                item.put("productName", SEED_ITEMS[j][1]);
                item.put("barcode", SEED_ITEMS[j][2]);
                item.put("orderQuantity", Integer.parseInt(SEED_ITEMS[j][3]) + i * 5);
                item.put("receivedQuantity", (partial && j == 0) ? 5 : 0);
                batch.set(orderRef.collection(ITEMS).document(), item);
            }
        }
        batch.commit()
                .addOnSuccessListener(v -> callback.onResult(true))
                .addOnFailureListener(e -> callback.onResult(false));
    }

    // ---- helpers ----

    private Map<String, Object> orderInfoMap(String code, String supplier, String eta, String notes) {
        Map<String, Object> data = new HashMap<>();
        data.put("code", code);
        data.put("supplierName", supplier);
        data.put("etaLabel", eta);
        data.put("notes", notes != null ? notes : "");
        return data;
    }
}
