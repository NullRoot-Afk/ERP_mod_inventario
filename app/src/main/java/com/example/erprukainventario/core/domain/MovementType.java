package com.example.erprukainventario.core.domain;

/** Tipos de movimiento de stock registrados en la bitácora (trazabilidad, guía §10). */
public enum MovementType {
    RECEPCION("Recepción"),
    PICKING("Picking"),
    DESPACHO("Despacho"),
    AJUSTE("Ajuste"),
    TRANSFERENCIA_SALIDA("Transferencia (salida)"),
    TRANSFERENCIA_ENTRADA("Transferencia (entrada)"),
    CONTEO("Conteo"),
    /** Cambio de ubicación dentro de la bodega: no altera el stock (guía §4). */
    REUBICACION("Reubicación");

    private final String label;

    MovementType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
