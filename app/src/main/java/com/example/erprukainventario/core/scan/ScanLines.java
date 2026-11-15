package com.example.erprukainventario.core.scan;

import com.example.erprukainventario.core.data.Docs;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lectura de la subcolección ITEMS a ScanLine, común a todos los módulos. */
public final class ScanLines {

    private ScanLines() {
    }

    public static ScanLine fromDoc(DocumentSnapshot doc, int expected, int scanned) {
        return new ScanLine(
                doc.getId(),
                Docs.str(doc, "", "productId"),
                Docs.str(doc, "PROD-" + doc.getId(), "productCode", "sku"),
                Docs.str(doc, "Producto", "productName", "name"),
                Docs.str(doc, "S/C", "barcode", "ean"),
                Docs.str(doc, "Sin ubicación", "location"),
                expected,
                scanned
        );
    }

    /** Marca con control de serie las líneas cuyos productos lo tienen (guía §10). */
    public static List<ScanLine> withSerialControl(List<ScanLine> lines, Set<String> serializedProductIds) {
        List<ScanLine> out = new ArrayList<>();
        for (ScanLine line : lines) {
            out.add(serializedProductIds.contains(line.getProductId())
                    ? line.withSerialControl(line.getAllowedSerials()) : line);
        }
        return out;
    }

    /** {productId: series capturadas} de las líneas con número de serie. */
    public static Map<String, List<String>> serialsByProduct(List<ScanLine> lines) {
        Map<String, List<String>> map = new HashMap<>();
        for (ScanLine line : lines) {
            if (!line.isSerialized() || line.getSerials().isEmpty()) continue;
            List<String> list = map.get(line.getProductId());
            if (list == null) map.put(line.getProductId(), list = new ArrayList<>());
            list.addAll(line.getSerials());
        }
        return map;
    }

    /** Error si alguna línea con serie no tiene una serie por unidad; null si están completas. */
    public static String missingSerials(List<ScanLine> lines) {
        for (ScanLine line : lines) {
            if (line.isSerialized() && line.getSerials().size() != line.getScannedQuantity()) {
                return "Faltan números de serie de " + line.getProductName() + ".";
            }
        }
        return null;
    }

    /**
     * Ruta de recolección (guía §5): ordenar por ubicación A-01-01, A-01-02, A-02-01...
     * recorre la bodega pasillo por pasillo sin volver atrás.
     */
    public static List<ScanLine> sortedByLocation(List<ScanLine> lines) {
        List<ScanLine> sorted = new ArrayList<>(lines);
        Collections.sort(sorted, (a, b) -> a.getLocation().compareToIgnoreCase(b.getLocation()));
        return sorted;
    }
}
