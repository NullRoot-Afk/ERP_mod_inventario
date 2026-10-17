package com.example.erprukainventario.reception.domain;

import java.util.List;

public class PurchaseOrderDetail {

    private final String id;
    private final String code;
    private final String supplierName;
    private final PurchaseOrderStatus status;
    private final String etaLabel;
    private final List<PurchaseOrderItem> items;
    // Campo agregado en la versión 2 del modelo (R7). Los documentos antiguos no lo traen: ""
    private final String notes;

    public PurchaseOrderDetail(String id, String code, String supplierName,
                               PurchaseOrderStatus status, String etaLabel,
                               List<PurchaseOrderItem> items) {
        this(id, code, supplierName, status, etaLabel, items, "");
    }

    public PurchaseOrderDetail(String id, String code, String supplierName,
                               PurchaseOrderStatus status, String etaLabel,
                               List<PurchaseOrderItem> items, String notes) {
        this.id = id;
        this.code = code;
        this.supplierName = supplierName;
        this.status = status;
        this.etaLabel = etaLabel;
        this.items = items;
        this.notes = notes != null ? notes : "";
    }

    public String getId() { return id; }
    public String getCode() { return code; }
    public String getSupplierName() { return supplierName; }
    public PurchaseOrderStatus getStatus() { return status; }
    public String getEtaLabel() { return etaLabel; }
    public List<PurchaseOrderItem> getItems() { return items; }
    public String getNotes() { return notes; }
}
