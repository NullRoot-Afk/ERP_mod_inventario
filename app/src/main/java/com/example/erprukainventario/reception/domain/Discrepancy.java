package com.example.erprukainventario.reception.domain;

import java.util.Date;

/** Discrepancia detectada al recibir (guía §3): faltante, sobrante o dañado, con foto opcional. */
public class Discrepancy {

    public enum Type {
        FALTANTE("Faltante"),
        SOBRANTE("Sobrante"),
        DANADO("Dañado");

        private final String label;

        Type(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private final String id;
    private final String receptionCode;
    private final String productCode;
    private final String productName;
    private final Type type;
    private final int quantity;
    private final String notes;
    private final String evidenceId;
    private final String createdByName;
    private final Date createdAt;

    public Discrepancy(String id, String receptionCode, String productCode, String productName, Type type,
                       int quantity, String notes, String evidenceId, String createdByName, Date createdAt) {
        this.id = id;
        this.receptionCode = receptionCode;
        this.productCode = productCode;
        this.productName = productName;
        this.type = type;
        this.quantity = quantity;
        this.notes = notes;
        this.evidenceId = evidenceId;
        this.createdByName = createdByName;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getReceptionCode() { return receptionCode; }
    public String getProductCode() { return productCode; }
    public String getProductName() { return productName; }
    public Type getType() { return type; }
    public int getQuantity() { return quantity; }
    public String getNotes() { return notes; }
    /** Id de la foto en EVIDENCE; vacío si no hay. */
    public String getEvidenceId() { return evidenceId; }
    public boolean hasPhoto() { return evidenceId != null && !evidenceId.isEmpty(); }
    public String getCreatedByName() { return createdByName; }
    public Date getCreatedAt() { return createdAt; }
}
