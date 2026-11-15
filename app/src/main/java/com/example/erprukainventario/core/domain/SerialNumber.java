package com.example.erprukainventario.core.domain;

import java.util.Date;

/** Una unidad identificada por su número de serie (guía §10). */
public class SerialNumber {

    private final String id;
    private final String productId;
    private final String productCode;
    private final String productName;
    private final String serial;
    private final SerialStatus status;
    private final String warehouseId;
    /** Última operación: OC, guía de despacho, transferencia o ajuste. */
    private final String reference;
    private final String updatedByName;
    private final Date updatedAt;

    public SerialNumber(String id, String productId, String productCode, String productName, String serial,
                        SerialStatus status, String warehouseId, String reference, String updatedByName,
                        Date updatedAt) {
        this.id = id;
        this.productId = productId;
        this.productCode = productCode;
        this.productName = productName;
        this.serial = serial;
        this.status = status;
        this.warehouseId = warehouseId;
        this.reference = reference;
        this.updatedByName = updatedByName;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getProductId() { return productId; }
    public String getProductCode() { return productCode; }
    public String getProductName() { return productName; }
    public String getSerial() { return serial; }
    public SerialStatus getStatus() { return status; }
    public String getWarehouseId() { return warehouseId; }
    public String getReference() { return reference; }
    public String getUpdatedByName() { return updatedByName; }
    /** null = pendiente de sincronizar. */
    public Date getUpdatedAt() { return updatedAt; }
}
