package com.example.erprukainventario.reception.domain;

public class PurchaseOrderItem {

    private final String id;
    private final String productCode;
    private final String productName;
    private final String barcode;
    private final int orderedQuantity;
    private final int receivedQuantity;

    public PurchaseOrderItem(String id, String productCode, String productName,
                             String barcode, int orderedQuantity, int receivedQuantity) {
        this.id = id;
        this.productCode = productCode;
        this.productName = productName;
        this.barcode = barcode;
        this.orderedQuantity = orderedQuantity;
        this.receivedQuantity = receivedQuantity;
    }

    public String getId() { return id; }
    public String getProductCode() { return productCode; }
    public String getProductName() { return productName; }
    public String getBarcode() { return barcode; }
    public int getOrderedQuantity() { return orderedQuantity; }
    public int getReceivedQuantity() { return receivedQuantity; }
}
