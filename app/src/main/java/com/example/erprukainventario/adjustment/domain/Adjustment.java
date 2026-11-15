package com.example.erprukainventario.adjustment.domain;

import java.util.Date;

/** Solicitud de ajuste de stock. Solo mueve stock cuando un supervisor la aprueba. */
public class Adjustment {

    private final String id;
    private final String productId;
    private final String productCode;
    private final String productName;
    private final int quantity; // con signo
    private final AdjustmentReason reason;
    private final String notes;
    private final String reference;
    private final AdjustmentStatus status;
    private final String createdById;
    private final String createdByName;
    private final Date createdAt;
    private final String reviewedByName;
    private final String reviewNote;
    /** Lote afectado ("" = sin indicar: al aprobar una rebaja se descuenta por FEFO). */
    private final String lotId;
    private final String lotNumber;
    /** Series de las unidades ajustadas (producto con número de serie, guía §10). */
    private final java.util.List<String> serials;

    public Adjustment(String id, String productId, String productCode, String productName, int quantity,
                      AdjustmentReason reason, String notes, String reference, AdjustmentStatus status,
                      String createdById, String createdByName, Date createdAt,
                      String reviewedByName, String reviewNote, String lotId, String lotNumber,
                      java.util.List<String> serials) {
        this.serials = serials != null ? serials : java.util.Collections.<String>emptyList();
        this.id = id;
        this.productId = productId;
        this.productCode = productCode;
        this.productName = productName;
        this.quantity = quantity;
        this.reason = reason;
        this.notes = notes;
        this.reference = reference;
        this.status = status;
        this.createdById = createdById;
        this.createdByName = createdByName;
        this.createdAt = createdAt;
        this.reviewedByName = reviewedByName;
        this.reviewNote = reviewNote;
        this.lotId = lotId;
        this.lotNumber = lotNumber;
    }

    public String getId() { return id; }
    public String getProductId() { return productId; }
    public String getProductCode() { return productCode; }
    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }
    public AdjustmentReason getReason() { return reason; }
    public String getNotes() { return notes; }
    public String getReference() { return reference; }
    public AdjustmentStatus getStatus() { return status; }
    public String getCreatedById() { return createdById; }
    public String getCreatedByName() { return createdByName; }
    public Date getCreatedAt() { return createdAt; }
    public String getReviewedByName() { return reviewedByName; }
    public String getReviewNote() { return reviewNote; }
    public String getLotId() { return lotId; }
    public String getLotNumber() { return lotNumber; }
    public boolean hasLot() { return lotId != null && !lotId.isEmpty(); }
    public java.util.List<String> getSerials() { return serials; }

    /** "+5" / "-3" para mostrar. */
    public String signedQuantity() {
        return (quantity > 0 ? "+" : "") + quantity;
    }
}
