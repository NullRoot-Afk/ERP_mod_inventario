package com.example.erprukainventario.core.domain;

/** Unidades que una salida toma de un lote (plan FEFO). */
public class LotTake {

    private final String lotId;
    private final String lotNumber;
    private final int quantity;
    /** true si la salida deja el lote en cero. */
    private final boolean depletes;

    public LotTake(String lotId, String lotNumber, int quantity, boolean depletes) {
        this.lotId = lotId;
        this.lotNumber = lotNumber;
        this.quantity = quantity;
        this.depletes = depletes;
    }

    public String getLotId() { return lotId; }
    public String getLotNumber() { return lotNumber; }
    public int getQuantity() { return quantity; }
    public boolean depletes() { return depletes; }
}
