package com.example.erprukainventario.reception.domain;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Una lectura confirmada durante la recepción: cuántas unidades de qué línea,
 * con qué lote/vencimiento y en qué ubicación se guardan. Al finalizar, cada
 * entrada genera su movimiento de stock (y su lote, si lo trae).
 */
public class ReceiptEntry {

    private final String itemId;
    private final String productCode;
    private final String productName;
    private final int quantity;
    private final String lot;
    private final Date expiryDate;
    private final String location;
    /** Una serie por unidad si el producto controla número de serie; vacía si no (guía §10). */
    private final List<String> serials;

    public ReceiptEntry(String itemId, String productCode, String productName, int quantity,
                        String lot, Date expiryDate, String location, List<String> serials) {
        this.serials = serials != null ? new ArrayList<>(serials) : new ArrayList<>();
        this.itemId = itemId;
        this.productCode = productCode;
        this.productName = productName;
        this.quantity = quantity;
        this.lot = lot != null ? lot.trim() : "";
        this.expiryDate = expiryDate;
        this.location = location;
    }

    public String getItemId() { return itemId; }
    public String getProductCode() { return productCode; }
    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }
    public String getLot() { return lot; }
    /** null si no se informó. */
    public Date getExpiryDate() { return expiryDate; }
    /** Ubicación canónica asignada, o null si no se informó. */
    public String getLocation() { return location; }
    public List<String> getSerials() { return serials; }
}
