package com.example.erprukainventario.reception.data;

import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.reception.domain.PurchaseOrder;

import java.util.List;

public interface ReceptionRepository {
    void getOrders(RepositoryCallback<List<PurchaseOrder>> callback);
}
