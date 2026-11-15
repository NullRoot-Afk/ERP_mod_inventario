package com.example.erprukainventario.adjustment.domain;

public enum AdjustmentStatus {
    PENDIENTE("Pendiente"),
    APROBADO("Aprobado"),
    RECHAZADO("Rechazado");

    private final String label;

    AdjustmentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
