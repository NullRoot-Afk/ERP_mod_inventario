package com.example.erprukainventario.core.domain;

/**
 * Dónde está una unidad con número de serie (guía §10). Los cambios válidos los
 * exige también firestore.rules: una serie despachada no se despacha de nuevo y
 * una serie en bodega no se vuelve a recibir.
 */
public enum SerialStatus {
    EN_BODEGA("En bodega"),
    EN_TRANSITO("En tránsito"),
    DESPACHADO("Despachado"),
    BAJA("Dado de baja");

    private final String label;

    SerialStatus(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }

    /** Se puede recibir (alta o reingreso): no existe, o salió de la empresa. */
    public static boolean canReceive(SerialStatus current) {
        return current == null || current == DESPACHADO || current == BAJA;
    }

    public static SerialStatus fromName(String name) {
        try {
            return name != null ? valueOf(name) : EN_BODEGA;
        } catch (IllegalArgumentException e) {
            return EN_BODEGA;
        }
    }
}
