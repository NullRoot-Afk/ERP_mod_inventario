package com.example.erprukainventario.picking.domain;

/**
 * Ciclo de un pedido de salida: se recolecta (picking) y luego se despacha.
 * PARCIAL = se recolectó solo una parte; lo faltante queda como backorder.
 */
public enum PickingStatus {
    PENDIENTE("Pendiente"),
    EN_PROCESO("En proceso"),
    PARCIAL("Parcial · backorder"),
    PREPARADO("Preparado"),
    DESPACHADO("Despachado");

    private final String label;

    PickingStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isPickable() {
        return this == PENDIENTE || this == EN_PROCESO || this == PARCIAL;
    }

    public boolean isReadyToDispatch() {
        return this == PREPARADO || this == PARCIAL;
    }
}
