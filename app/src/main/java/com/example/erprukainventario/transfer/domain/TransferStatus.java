package com.example.erprukainventario.transfer.domain;

public enum TransferStatus {
    POR_ENVIAR("Por enviar"),
    EN_TRANSITO("En tránsito"),
    RECIBIDA("Recibida");

    private final String label;

    TransferStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
