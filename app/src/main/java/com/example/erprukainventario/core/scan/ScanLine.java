package com.example.erprukainventario.core.scan;

/**
 * Una línea de cualquier tarea que se trabaja escaneando (picking, despacho,
 * conteo, transferencia): qué producto, dónde está, cuánto se espera y cuánto
 * se lleva escaneado. Inmutable: cada cambio crea una línea nueva.
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

    public ScanLine(String id, String productId, String productCode, String productName, String barcode,
                    String location, int expectedQuantity, int scannedQuantity) {
        this.id = id;
        this.productId = productId;
        this.productCode = productCode;
        this.productName = productName;
        this.barcode = barcode;
        this.location = location;
        this.expectedQuantity = expectedQuantity;
        this.scannedQuantity = scannedQuantity;
    }

    public ScanLine withScanned(int quantity) {
        return new ScanLine(id, productId, productCode, productName, barcode, location,
                expectedQuantity, Math.max(0, quantity));
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
}
