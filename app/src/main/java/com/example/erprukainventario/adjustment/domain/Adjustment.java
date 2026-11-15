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

    public Adjustment(String id, String productId, String productCode, String productName, int quantity,
                      AdjustmentReason reason, String notes, String reference, AdjustmentStatus status,
                      String createdById, String createdByName, Date createdAt,
                      String reviewedByName, String reviewNote) {
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

    /** "+5" / "-3" para mostrar. */
    public String signedQuantity() {
        return (quantity > 0 ? "+" : "") + quantity;
    }
}
