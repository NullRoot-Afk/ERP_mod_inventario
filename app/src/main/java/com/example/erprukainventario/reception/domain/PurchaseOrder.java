package com.example.erprukainventario.reception.domain;

public class PurchaseOrder {

    private final String id;
    private final String code;
    private final String supplierName;
    private final int lineCount;
    private final PurchaseOrderStatus status;
    private final String etaLabel; // texto ya formateado, ej. "Llegada estimada hoy"

    public PurchaseOrder(String id, String code, String supplierName, int lineCount,
                         PurchaseOrderStatus status, String etaLabel) {
        this.id = id;
        this.code = code;
        this.supplierName = supplierName;
        this.lineCount = lineCount;
        this.status = status;
        this.etaLabel = etaLabel;
    }

    public String getId() { return id; }
    public String getCode() { return code; }
    public String getSupplierName() { return supplierName; }
    public int getLineCount() { return lineCount; }
    public PurchaseOrderStatus getStatus() { return status; }
    public String getEtaLabel() { return etaLabel; }
}