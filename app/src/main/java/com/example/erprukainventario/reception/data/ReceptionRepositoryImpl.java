package com.example.erprukainventario.reception.data;

import android.os.Handler;
import android.os.Looper;

import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.auth.data.SessionManager;
import com.example.erprukainventario.auth.data.TenantConfigLocalDataSource;
import com.example.erprukainventario.auth.domain.AuthUser;
import com.example.erprukainventario.auth.domain.Company;
import com.example.erprukainventario.reception.domain.PurchaseOrder;
import com.example.erprukainventario.reception.domain.PurchaseOrderDetail;
import com.example.erprukainventario.reception.domain.PurchaseOrderItem;
import com.example.erprukainventario.reception.domain.PurchaseOrderStatus;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ReceptionRepositoryImpl implements ReceptionRepository {

    private final ReceptionLocalDataSource localDataSource;
    private final SessionManager sessionManager;
    private final TenantConfigLocalDataSource tenantConfig;
    private final FirebaseFirestore firestore;

    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Inject
    public ReceptionRepositoryImpl(
            ReceptionLocalDataSource localDataSource,
            SessionManager sessionManager,
            TenantConfigLocalDataSource tenantConfig,
            FirebaseFirestore firestore
    ) {
        this.localDataSource = localDataSource;
        this.sessionManager = sessionManager;
        this.tenantConfig = tenantConfig;
        this.firestore = firestore;
    }

    @Override
    public void getOrders(RepositoryCallback<List<PurchaseOrder>> callback) {
        AuthUser user = sessionManager.getCurrentUser();
        Company company = tenantConfig.getConfiguredCompany();

        if (company == null) {
            callback.onResult(new ArrayList<>());
            return;
        }

        String companyId = company.getId();
        String warehouseId = (user != null && user.getWarehouseId() != null) ? user.getWarehouseId() : "WH-01";

        // Consultar colección "receptions" en Cloud Firestore
        firestore.collection("receptions")
                .whereEqualTo("companyId", companyId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<PurchaseOrder> orders = new ArrayList<>();
                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            String id = doc.getId();
                            String rawCode = doc.getString("code");
                            String code = (rawCode != null && !rawCode.isEmpty()) ? rawCode : "OC-" + id;

                            String docSupplierName = doc.getString("supplierName");
                            String docSupplier = doc.getString("supplier");
                            String supplierName = docSupplierName != null ? docSupplierName : (docSupplier != null ? docSupplier : "Proveedor N/A");

                            Long rawLines = doc.getLong("lineCount");
                            Long linesLong = rawLines != null ? rawLines : doc.getLong("itemsCount");
                            int lineCount = linesLong != null ? linesLong.intValue() : 0;

                            PurchaseOrderStatus status;
                            String statusStr = doc.getString("status");
                            if (statusStr != null) {
                                try {
                                    status = PurchaseOrderStatus.valueOf(statusStr.toUpperCase().trim());
                                } catch (IllegalArgumentException e) {
                                    status = PurchaseOrderStatus.SIN_INICIAR;
                                }
                            } else {
                                status = PurchaseOrderStatus.SIN_INICIAR;
                            }

                            String docEtaLabel = doc.getString("etaLabel");
                            String docEta = doc.getString("eta");
                            String etaLabel = docEtaLabel != null ? docEtaLabel : (docEta != null ? docEta : "Pendiente");

                            orders.add(new PurchaseOrder(id, code, supplierName, lineCount, status, etaLabel));
                        }

                        // Guardar en la base de datos local Room
                        dbExecutor.execute(() -> {
                            localDataSource.replaceOrdersForWarehouse(warehouseId, orders);
                            mainHandler.post(() -> callback.onResult(orders));
                        });
                    } else {
                        fallbackToCache(warehouseId, callback);
                    }
                })
                .addOnFailureListener(e -> fallbackToCache(warehouseId, callback));
    }

    private void fallbackToCache(String warehouseId, RepositoryCallback<List<PurchaseOrder>> callback) {
        dbExecutor.execute(() -> {
            List<PurchaseOrder> cached = localDataSource.getCachedOrders(warehouseId);
            mainHandler.post(() -> callback.onResult(cached != null ? cached : new ArrayList<>()));
        });
    }

    @Override
    public void getOrderDetail(String orderId, RepositoryCallback<PurchaseOrderDetail> callback) {
        if (orderId == null || orderId.isEmpty()) {
            callback.onResult(null);
            return;
        }

        firestore.collection("receptions").document(orderId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String id = documentSnapshot.getId();
                        String rawCode = documentSnapshot.getString("code");
                        String code = (rawCode != null && !rawCode.isEmpty()) ? rawCode : "OC-" + id;

                        String docSupplierName = documentSnapshot.getString("supplierName");
                        String docSupplier = documentSnapshot.getString("supplier");
                        String supplierName = docSupplierName != null ? docSupplierName : (docSupplier != null ? docSupplier : "Proveedor N/A");

                        PurchaseOrderStatus status;
                        String statusStr = documentSnapshot.getString("status");
                        if (statusStr != null) {
                            try {
                                status = PurchaseOrderStatus.valueOf(statusStr.toUpperCase().trim());
                            } catch (IllegalArgumentException e) {
                                status = PurchaseOrderStatus.SIN_INICIAR;
                            }
                        } else {
                            status = PurchaseOrderStatus.SIN_INICIAR;
                        }

                        String docEtaLabel = documentSnapshot.getString("etaLabel");
                        String docEta = documentSnapshot.getString("eta");
                        String etaLabel = docEtaLabel != null ? docEtaLabel : (docEta != null ? docEta : "Pendiente");

                        final PurchaseOrderStatus finalStatus = status;

                        // Cargar subcolección "items"
                        firestore.collection("receptions").document(orderId).collection("items").get()
                                .addOnSuccessListener(itemsSnapshot -> {
                                    List<PurchaseOrderItem> items = new ArrayList<>();
                                    if (itemsSnapshot != null && !itemsSnapshot.isEmpty()) {
                                        for (DocumentSnapshot itemDoc : itemsSnapshot.getDocuments()) {
                                            String itemId = itemDoc.getId();
                                            String pCode = itemDoc.getString("productCode");
                                            if (pCode == null) pCode = itemDoc.getString("sku");
                                            if (pCode == null) pCode = "PROD-" + itemId;

                                            String pName = itemDoc.getString("productName");
                                            if (pName == null) pName = itemDoc.getString("name");
                                            if (pName == null) pName = "Producto";

                                            String barcode = itemDoc.getString("barcode");
                                            if (barcode == null) barcode = itemDoc.getString("ean");
                                            if (barcode == null) barcode = "S/C";

                                            Long ordQty = itemDoc.getLong("orderedQuantity");
                                            if (ordQty == null) ordQty = itemDoc.getLong("quantity");
                                            int orderedQty = ordQty != null ? ordQty.intValue() : 0;

                                            Long recQty = itemDoc.getLong("receivedQuantity");
                                            int receivedQty = recQty != null ? recQty.intValue() : 0;

                                            items.add(new PurchaseOrderItem(itemId, pCode, pName, barcode, orderedQty, receivedQty));
                                        }
                                    }
                                    callback.onResult(new PurchaseOrderDetail(id, code, supplierName, finalStatus, etaLabel, items));
                                })
                                .addOnFailureListener(e -> callback.onResult(new PurchaseOrderDetail(id, code, supplierName, finalStatus, etaLabel, new ArrayList<>())));
                    } else {
                        callback.onResult(null);
                    }
                })
                .addOnFailureListener(e -> callback.onResult(null));
    }

    @Override
    public void updateReceptionProgress(String orderId, List<PurchaseOrderItem> items, RepositoryCallback<Boolean> callback) {
        if (orderId == null || orderId.isEmpty() || items == null) {
            callback.onResult(false);
            return;
        }

        // 1. Calcular estado global
        int totalOrdered = 0;
        int totalReceived = 0;
        for (PurchaseOrderItem item : items) {
            totalOrdered += item.getOrderedQuantity();
            totalReceived += item.getReceivedQuantity();
        }

        PurchaseOrderStatus calculatedStatus;
        if (totalReceived <= 0) {
            calculatedStatus = PurchaseOrderStatus.SIN_INICIAR;
        } else if (totalReceived >= totalOrdered) {
            calculatedStatus = PurchaseOrderStatus.COMPLETA;
        } else {
            calculatedStatus = PurchaseOrderStatus.PARCIAL;
        }

        // 2. Actualizar cada item en la subcolección de Firestore
        for (PurchaseOrderItem item : items) {
            firestore.collection("receptions")
                    .document(orderId)
                    .collection("items")
                    .document(item.getId())
                    .update("receivedQuantity", item.getReceivedQuantity());
        }

        // 3. Actualizar el estado en el documento principal de la recepción
        firestore.collection("receptions")
                .document(orderId)
                .update("status", calculatedStatus.name())
                .addOnSuccessListener(aVoid -> callback.onResult(true))
                .addOnFailureListener(e -> callback.onResult(false));
    }
}
