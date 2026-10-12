package com.example.erprukainventario.reception.domain;

import java.util.List;

public class PurchaseOrderDetail {

    private final String id;
    private final String code;
    private final String supplierName;
    private final PurchaseOrderStatus status;
    private final String etaLabel;
    private final List<PurchaseOrderItem> items;

    public PurchaseOrderDetail(String id, String code, String supplierName,
                               PurchaseOrderStatus status, String etaLabel,
                               List<PurchaseOrderItem> items) {
        this.id = id;
        this.code = code;
        this.supplierName = supplierName;
        this.status = status;
        this.etaLabel = etaLabel;
        this.items = items;
    }

    public String getId() { return id; }
    public String getCode() { return code; }
    public String getSupplierName() { return supplierName; }
    public PurchaseOrderStatus getStatus() { return status; }
    public String getEtaLabel() { return etaLabel; }
    public List<PurchaseOrderItem> getItems() { return items; }
}
