package com.example.erprukainventario.reception.data;

import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.reception.domain.PurchaseOrder;
import com.example.erprukainventario.reception.domain.PurchaseOrderDetail;
import com.example.erprukainventario.reception.domain.PurchaseOrderItem;

import java.util.List;

public interface ReceptionRepository {
    // READ
    void getOrders(RepositoryCallback<List<PurchaseOrder>> callback);
    void getOrderDetail(String orderId, RepositoryCallback<PurchaseOrderDetail> callback);

    // UPDATE (avance de la recepción)
    void updateReceptionProgress(String orderId, List<PurchaseOrderItem> items, RepositoryCallback<Boolean> callback);

    // CREATE / UPDATE / DELETE de órdenes. createOrder entrega el id nuevo, o null si falló.
    void createOrder(String code, String supplier, String eta, String notes, RepositoryCallback<String> callback);
    void updateOrderInfo(String orderId, String code, String supplier, String eta, String notes, RepositoryCallback<Boolean> callback);
    void deleteOrder(String orderId, RepositoryCallback<Boolean> callback);

    // CREATE / UPDATE / DELETE de productos (ITEMS). itemId == null significa crear.
    void saveItem(String orderId, String itemId, String productCode, String productName,
                  String barcode, int orderedQuantity, RepositoryCallback<Boolean> callback);
    void deleteItem(String orderId, String itemId, RepositoryCallback<Boolean> callback);

    // Pobla la base desde la app: 5 órdenes con 3 productos cada una.
    void seedDemoData(RepositoryCallback<Boolean> callback);
}
