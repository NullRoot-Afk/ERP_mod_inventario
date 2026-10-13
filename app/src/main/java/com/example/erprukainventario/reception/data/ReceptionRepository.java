package com.example.erprukainventario.reception.data;

import com.example.erprukainventario.reception.domain.PurchaseOrder;
import com.example.erprukainventario.reception.domain.PurchaseOrderDetail;
import com.example.erprukainventario.reception.domain.PurchaseOrderItem;
import com.example.erprukainventario.auth.data.RepositoryCallback;


import java.util.List;

public interface ReceptionRepository {
    void getOrders(RepositoryCallback<List<PurchaseOrder>> callback);
    void getOrderDetail(String orderId, RepositoryCallback<PurchaseOrderDetail> callback);
    void updateReceptionProgress(String orderId, List<PurchaseOrderItem> items, RepositoryCallback<Boolean> callback);
}
