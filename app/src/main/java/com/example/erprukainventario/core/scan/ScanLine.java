package com.example.erprukainventario.core.scan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Una línea de cualquier tarea que se trabaja escaneando (picking, despacho,
 * conteo, transferencia): qué producto, dónde está, cuánto se espera y cuánto
 * se lleva escaneado. Inmutable: cada cambio crea una línea nueva.
 *
 * Si el producto controla número de serie y la sesión lo pide, la cantidad
 * escaneada es la cantidad de series capturadas (guía §10).
 */
public class ScanLine {

    private final String id;
    private final String productId;
    private final String productCode;
    private final String productName;
    private final String barcode;
    private final String location;
    private final int expectedQuantity;
    private final int scannedQuantity;
    private final boolean serialized;
    private final List<String> serials;
    /** Series que pueden aparecer (lo enviado en una transferencia); vacía = cualquiera. */
    private final List<String> allowedSerials;

    public ScanLine(String id, String productId, String productCode, String productName, String barcode,
                    String location, int expectedQuantity, int scannedQuantity) {
        this(id, productId, productCode, productName, barcode, location, expectedQuantity, scannedQuantity,
                false, null, null);
    }

    private ScanLine(String id, String productId, String productCode, String productName, String barcode,
                     String location, int expectedQuantity, int scannedQuantity, boolean serialized,
                     List<String> serials, List<String> allowedSerials) {
        this.id = id;
        this.productId = productId;
        this.productCode = productCode;
        this.productName = productName;
        this.barcode = barcode;
        this.location = location;
        this.expectedQuantity = expectedQuantity;
        this.scannedQuantity = scannedQuantity;
        this.serialized = serialized;
        this.serials = serials != null ? Collections.unmodifiableList(new ArrayList<>(serials))
                : Collections.<String>emptyList();
        this.allowedSerials = allowedSerials != null ? Collections.unmodifiableList(new ArrayList<>(allowedSerials))
                : Collections.<String>emptyList();
    }

    public ScanLine withScanned(int quantity) {
        return new ScanLine(id, productId, productCode, productName, barcode, location,
                expectedQuantity, Math.max(0, quantity), serialized, serials, allowedSerials);
    }

    public ScanLine withLocation(String newLocation) {
        return new ScanLine(id, productId, productCode, productName, barcode, newLocation,
                expectedQuantity, scannedQuantity, serialized, serials, allowedSerials);
    }

    /** Marca la línea como de producto con número de serie. */
    public ScanLine withSerialControl(List<String> allowed) {
        return new ScanLine(id, productId, productCode, productName, barcode, location,
                expectedQuantity, scannedQuantity, true, serials, allowed);
    }

    /** Fija las series capturadas; la cantidad escaneada pasa a ser cuántas son. */
    public ScanLine withSerials(List<String> newSerials) {
        List<String> list = newSerials != null ? newSerials : Collections.<String>emptyList();
        return new ScanLine(id, productId, productCode, productName, barcode, location,
                expectedQuantity, list.size(), serialized, list, allowedSerials);
    }

    public boolean matches(String code) {
        if (code == null) return false;
        String c = code.trim();
        return c.equalsIgnoreCase(barcode) || c.equalsIgnoreCase(productCode);
    }

    public boolean isComplete() { return scannedQuantity >= expectedQuantity; }
    public boolean isStarted() { return scannedQuantity > 0; }
    public int difference() { return scannedQuantity - expectedQuantity; }

    public String getId() { return id; }
    public String getProductId() { return productId; }
    public String getProductCode() { return productCode; }
    public String getProductName() { return productName; }
    public String getBarcode() { return barcode; }
    public String getLocation() { return location; }
    public int getExpectedQuantity() { return expectedQuantity; }
    public int getScannedQuantity() { return scannedQuantity; }
    public boolean isSerialized() { return serialized; }
    public List<String> getSerials() { return serials; }
    public List<String> getAllowedSerials() { return allowedSerials; }
}
