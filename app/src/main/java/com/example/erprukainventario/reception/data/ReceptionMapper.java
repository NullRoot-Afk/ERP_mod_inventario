package com.example.erprukainventario.reception.data;

import com.example.erprukainventario.reception.domain.PurchaseOrder;
import com.example.erprukainventario.reception.domain.PurchaseOrderDetail;
import com.example.erprukainventario.reception.domain.PurchaseOrderItem;
import com.example.erprukainventario.reception.domain.PurchaseOrderStatus;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.List;

/**
 * Único lugar que conoce los nombres de campo de Firestore.
 * Tolera alias (supplierName/supplier, etaLabel/eta, etc.).
 */
final class ReceptionMapper {

    private ReceptionMapper() {
    }

    static PurchaseOrder toOrder(DocumentSnapshot doc) {
        return new PurchaseOrder(
                doc.getId(),
                code(doc),
                supplier(doc),
                num(doc, "lineCount", "itemsCount"),
                status(doc),
                eta(doc)
        );
    }

    static PurchaseOrderDetail toDetail(DocumentSnapshot doc, List<PurchaseOrderItem> items) {
        return new PurchaseOrderDetail(
                doc.getId(), code(doc), supplier(doc), status(doc), eta(doc), items
        );
    }

    static PurchaseOrderItem toItem(DocumentSnapshot doc) {
        String id = doc.getId();
        return new PurchaseOrderItem(
                id,
                str(doc, "PROD-" + id, "productCode", "sku"),
                str(doc, "Producto", "productName", "name"),
                str(doc, "S/C", "barcode", "ean"),
                num(doc, "orderQuantity", "quantity"),
                num(doc, "receivedQuantity")
        );
    }

    // ---- helpers ----

    private static String code(DocumentSnapshot d) {
        return str(d, "OC-" + d.getId(), "code");
    }

    private static String supplier(DocumentSnapshot d) {
        return str(d, "Proveedor N/A", "supplierName", "supplier");
    }

    private static String eta(DocumentSnapshot d) {
        return str(d, "Pendiente", "etaLabel", "eta");
    }

    private static PurchaseOrderStatus status(DocumentSnapshot d) {
        String s = d.getString("status");
        if (s == null) return PurchaseOrderStatus.SIN_INICIAR;
        try {
            return PurchaseOrderStatus.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return PurchaseOrderStatus.SIN_INICIAR;
        }
    }

    /**
     * Primer campo no vacío entre los alias; si ninguno, el valor por defecto.
     */
    private static String str(DocumentSnapshot d, String fallback, String... fields) {
        for (String f : fields) {
            String v = d.getString(f);
            if (v != null && !v.isEmpty()) return v;
        }
        return fallback;
    }

    private static int num(DocumentSnapshot d, String... fields) {
        for (String f : fields) {
            Object v = d.get(f);
            if (v instanceof Number) {
                return ((Number) v).intValue();
            }
            if (v instanceof String) {
                try {
                    return (int) Double.parseDouble(((String) v).trim());
                } catch (NumberFormatException ignored) {
                    // texto no numérico: probar el siguiente alias
                }
            }
        }
        return 0; // <- esta línea es la que falta
    }
    }

