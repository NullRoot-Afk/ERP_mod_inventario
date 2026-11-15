package com.example.erprukainventario.count.domain;

public enum CountStatus {
    PENDIENTE("Programado"),
    CERRADO("Cerrado");

    private final String label;

    CountStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
