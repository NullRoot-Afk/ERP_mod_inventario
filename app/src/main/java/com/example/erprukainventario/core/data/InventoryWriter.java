package com.example.erprukainventario.core.data;

import com.example.erprukainventario.core.domain.MovementType;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Agrega a un batch o transacción ({@link Writes}) los dos efectos de cualquier
 * operación de bodega: el cambio de stock del producto y el registro en la
 * bitácora MOVEMENTS. Al ir junto con el cambio de estado del documento, o se
 * aplica todo o nada (no queda stock movido sin su movimiento, ni al revés).
 */
@Singleton
public class InventoryWriter {

    private final FirebaseFirestore firestore;
    private final WorkContext context;

    @Inject
    public InventoryWriter(FirebaseFirestore firestore, WorkContext context) {
        this.firestore = firestore;
        this.context = context;
    }

    /**
     * Suma (o resta, si delta < 0) stock en una bodega. Se usa set+merge con
     * FieldValue.increment: es atómico en el servidor, no se pisa con cambios de
     * otros dispositivos (se suman) y no falla si el producto aún no tiene ese campo.
     */
    public void addStockChange(Writes w, String productId, String warehouseId, int delta) {
        if (productId == null || productId.isEmpty() || delta == 0) return;

        Map<String, Object> stock = new HashMap<>();
        stock.put(warehouseId, FieldValue.increment(delta));

        Map<String, Object> data = new HashMap<>();
        data.put("companyId", context.companyId());
        data.put("stock", stock);

        w.merge(firestore.collection(FirestoreCollections.PRODUCTS).document(productId), data);
    }

    public void addMovement(Writes w, MovementType type, String productId, String productCode,
                            String productName, int quantity, String reference, String warehouseId, String lot) {
        if (quantity == 0) return;
        w.set(firestore.collection(FirestoreCollections.MOVEMENTS).document(),
                movementData(type, productId, productCode, productName, quantity, reference, warehouseId, lot));
    }

    /** Atajo: mueve stock y deja el movimiento firmado, en la bodega del usuario. */
    public void apply(Writes w, MovementType type, String productId, String productCode,
                      String productName, int delta, String reference) {
        apply(w, type, productId, productCode, productName, delta, reference, context.warehouseId(), null);
    }

    public void apply(Writes w, MovementType type, String productId, String productCode,
                      String productName, int delta, String reference, String warehouseId, String lot) {
        apply(w, type, productId, productCode, productName, delta, reference, warehouseId, lot, null);
    }

    /** @param serials series de las unidades movidas ("SN1, SN2"), o null (guía §10). */
    public void apply(Writes w, MovementType type, String productId, String productCode,
                      String productName, int delta, String reference, String warehouseId, String lot,
                      String serials) {
        addStockChange(w, productId, warehouseId, delta);
        if (delta == 0) return;
        Map<String, Object> data = movementData(type, productId, productCode, productName, delta, reference,
                warehouseId, lot);
        if (serials != null && !serials.isEmpty()) data.put("serials", serials);
        w.set(firestore.collection(FirestoreCollections.MOVEMENTS).document(), data);
    }

    /**
     * Fija la ubicación del producto en la bodega del usuario (campo locations.{bodega}).
     * No toca el stock.
     */
    public void setLocation(Writes w, String productId, String location) {
        if (productId == null || productId.isEmpty() || location == null || location.isEmpty()) return;

        Map<String, Object> locations = new HashMap<>();
        locations.put(context.warehouseId(), location);

        Map<String, Object> data = new HashMap<>();
        data.put("companyId", context.companyId());
        data.put("locations", locations);

        w.merge(firestore.collection(FirestoreCollections.PRODUCTS).document(productId), data);
    }

    /**
     * Reubicación (guía §4): cambia la ubicación y deja la traza en MOVEMENTS.
     * La cantidad registrada es el stock trasladado; el stock total no cambia.
     */
    public void relocate(Writes w, String productId, String productCode, String productName,
                         int unitsMoved, String fromLocation, String toLocation) {
        setLocation(w, productId, toLocation);
        w.set(firestore.collection(FirestoreCollections.MOVEMENTS).document(),
                movementData(MovementType.REUBICACION, productId, productCode, productName, unitsMoved,
                        fromLocation + " → " + toLocation, context.warehouseId(), null));
    }

    private Map<String, Object> movementData(MovementType type, String productId, String productCode,
                                             String productName, int quantity, String reference,
                                             String warehouseId, String lot) {
        Map<String, Object> data = new HashMap<>();
        data.put("companyId", context.companyId());
        data.put("warehouseId", warehouseId);
        data.put("productId", productId != null ? productId : "");
        data.put("productCode", productCode);
        data.put("productName", productName);
        data.put("type", type.name());
        data.put("quantity", quantity);
        data.put("reference", reference);
        if (lot != null && !lot.isEmpty()) data.put("lot", lot);
        data.put("userId", context.userId());
        data.put("userName", context.userName());
        data.put("createdAt", FieldValue.serverTimestamp());
        return data;
    }
}
