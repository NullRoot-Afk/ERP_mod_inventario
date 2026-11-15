package com.example.erprukainventario.core.domain;

import java.util.Date;

/** Un registro de la bitácora: quién, cuándo, qué acción y cuánto. */
public class Movement {

    private final MovementType type;
    private final int quantity; // con signo: + entra, - sale
    private final String reference;
    private final String userName;
    private final String warehouseId;
    private final Date createdAt;

    public Movement(MovementType type, int quantity, String reference, String userName,
                    String warehouseId, Date createdAt) {
        this.type = type;
        this.quantity = quantity;
        this.reference = reference;
        this.userName = userName;
        this.warehouseId = warehouseId;
        this.createdAt = createdAt;
    }

    public MovementType getType() { return type; }
    public int getQuantity() { return quantity; }
    public String getReference() { return reference; }
    public String getUserName() { return userName; }
    public String getWarehouseId() { return warehouseId; }
    public Date getCreatedAt() { return createdAt; }
}
