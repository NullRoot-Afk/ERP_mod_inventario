package com.example.erprukainventario.core.scan;

import com.example.erprukainventario.core.data.Docs;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
