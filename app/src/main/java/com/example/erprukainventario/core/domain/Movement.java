package com.example.erprukainventario.core.domain;

import java.util.Date;

/** Un registro de la bitácora: quién, cuándo, qué acción y cuánto. */
public class Movement {

    private final MovementType type;
    private final int quantity; // con signo: + entra, - sale
    private final String reference;
    private final String userName;
    private final String userId;
    private final String warehouseId;
    private final String productName;
    private final String lot;
    private final Date createdAt;
    private final String serials;

    public Movement(MovementType type, int quantity, String reference, String userId, String userName,
                    String warehouseId, String productName, String lot, Date createdAt, String serials) {
        this.serials = serials != null ? serials : "";
        this.type = type;
        this.quantity = quantity;
        this.reference = reference;
        this.userId = userId;
        this.userName = userName;
        this.warehouseId = warehouseId;
        this.productName = productName;
        this.lot = lot != null ? lot : "";
        this.createdAt = createdAt;
    }

    public MovementType getType() { return type; }
    public int getQuantity() { return quantity; }
    public String getReference() { return reference; }
    public String getUserId() { return userId; }
    public String getUserName() { return userName; }
    public String getWarehouseId() { return warehouseId; }
    public String getProductName() { return productName; }
    /** Lote asociado (guía §10), vacío si no aplica. */
    public String getLot() { return lot; }
    public Date getCreatedAt() { return createdAt; }
    /** Números de serie de las unidades movidas ("SN1, SN2"), vacío si no aplica. */
    public String getSerials() { return serials; }
}
