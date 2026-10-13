package com.example.erprukainventario.reception.data;

import com.example.erprukainventario.auth.domain.Company;
import com.example.erprukainventario.reception.domain.PurchaseOrder;
import com.example.erprukainventario.reception.domain.PurchaseOrderDetail;
import com.example.erprukainventario.reception.domain.PurchaseOrderItem;
import com.example.erprukainventario.reception.domain.PurchaseOrderStatus;
import com.example.erprukainventario.auth.data.TenantConfigLocalDataSource;
import com.example.erprukainventario.auth.data.RepositoryCallback;


import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Lee/escribe recepciones directamente en Cloud Firestore.
 * El caché offline lo maneja Firestore (persistencia en disco, activa por defecto),
 * por eso no hay Room ni hilos propios: los callbacks ya llegan en el hilo principal.
 */
@Singleton
public class ReceptionRepositoryImpl implements ReceptionRepository {

    private static final String RECEPTIONS = "RECEPTIONS";
    private static final String ITEMS = "ITEMS";

    private final TenantConfigLocalDataSource tenantConfig;
    private final FirebaseFirestore firestore;

    @Inject
    public ReceptionRepositoryImpl(TenantConfigLocalDataSource tenantConfig,
                                   FirebaseFirestore firestore) {
        this.tenantConfig = tenantConfig;
        this.firestore = firestore;
    }

    @Override
    public void getOrders(RepositoryCallback<List<PurchaseOrder>> callback) {
        Company company = tenantConfig.getConfiguredCompany();
        android.util.Log.d("ReceptionRepo", "getOrders llamado. Empresa: "
                + (company != null ? company.getId() : "NULL"));

        if (company == null) {
            callback.onResult(new ArrayList<>());
            return;
        }

        firestore.collection(RECEPTIONS)
                .whereEqualTo("companyId", company.getId())
                .get()
                .addOnCompleteListener(task -> {
                    List<PurchaseOrder> orders = new ArrayList<>();
                    if (task.isSuccessful() && task.getResult() != null) {
                        android.util.Log.d("ReceptionRepo", "Documentos: " + task.getResult().size());
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



        firestore.collection(RECEPTIONS)
                .whereEqualTo("companyId", company.getId())
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<PurchaseOrder> orders = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        orders.add(ReceptionMapper.toOrder(doc));
                    }
                    Collections.sort(orders, (a, b) -> a.getCode().compareToIgnoreCase(b.getCode()));
                    callback.onResult(orders);
                })
                .addOnFailureListener(e -> callback.onResult(new ArrayList<>()));
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
                    callback.onResult(ReceptionMapper.toDetail(orderDoc, items));
                })
                .addOnFailureListener(e -> callback.onResult(null));
    }

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
}
