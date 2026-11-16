package com.example.erprukainventario.core.data;

import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Catálogo de prueba (productos + bodegas) que necesitan los datos demo de
 * Picking, Conteo, Transferencias y Ajustes. Solo se escribe si la empresa
 * todavía no tiene productos/bodegas: no pisa datos reales ni stock ya movido.
 *
 * Los códigos de barras 7801234567890..92 son los mismos de Recepción, para
 * poder probar el flujo completo con los mismos productos.
 */
@Singleton
public class DemoCatalog {

    /** {código, nombre, EAN, ubicación, categoría, stockMínimo, stockMiBodega, stockSucursal} */
    public static final String[][] PRODUCTS = {
            {"PROD-101", "Caja de Clavos de Acero 2.5\"", "7801234567890", "A-01-01", "Fijaciones", "10", "60", "25"},
            {"PROD-102", "Martillo Carpintero 16 oz", "7801234567891", "A-01-02", "Herramientas", "5", "24", "8"},
            {"PROD-103", "Cinta Métrica 5 m", "7801234567892", "A-02-01", "Herramientas", "8", "40", "12"},
            {"PROD-104", "Destornillador Philips #2", "7801234567893", "A-02-03", "Herramientas", "6", "4", "10"},
            {"PROD-105", "Guantes de Seguridad Talla L", "7801234567894", "B-01-01", "Seguridad", "20", "120", "50"},
            {"PROD-106", "Casco de Seguridad Blanco", "7801234567895", "B-01-02", "Seguridad", "5", "18", "6"},
            {"PROD-107", "Taladro Percutor 750W", "7801234567896", "C-01-01", "Eléctricos", "2", "7", "3"},
            {"PROD-108", "Brocas para Concreto (Set 5)", "7801234567897", "C-02-02", "Eléctricos", "4", "15", "9"}
    };

    private final FirebaseFirestore firestore;
    private final WorkContext context;

    @Inject
    public DemoCatalog(FirebaseFirestore firestore, WorkContext context) {
        this.firestore = firestore;
        this.context = context;
    }

    /** Id de documento determinista: el mismo producto nunca se duplica. */
    public String productId(String code) {
        return context.companyId() + "_" + code;
    }

    public String branchWarehouseId() {
        return context.companyId() + "_TALCAHUANO";
    }

    public String branchWarehouseName() {
        return "Sucursal Talcahuano";
    }

    public String secondBranchWarehouseId() {
        return context.companyId() + "_CHILLAN";
    }

    public String secondBranchWarehouseName() {
        return "Sucursal Chillán";
    }

    /** Fila del catálogo por código, o null. */
    public static String[] row(String code) {
        for (String[] p : PRODUCTS) {
            if (p[0].equals(code)) return p;
        }
        return null;
    }

    /** Crea productos y bodegas solo si faltan. Entrega false si no hay empresa o falló. */
    public void ensureCatalog(RepositoryCallback<Boolean> callback) {
        String companyId = context.companyId();
        if (companyId == null) {
            callback.onResult(false);
            return;
        }

        Task<QuerySnapshot> products = firestore.collection(FirestoreCollections.PRODUCTS)
                .whereEqualTo("companyId", companyId).limit(1).get();
        Task<QuerySnapshot> warehouses = firestore.collection(FirestoreCollections.WAREHOUSES)
                .whereEqualTo("companyId", companyId).limit(1).get();

        Tasks.whenAllComplete(products, warehouses).addOnCompleteListener(all -> {
            if (!products.isSuccessful() || !warehouses.isSuccessful()) {
                callback.onResult(false);
                return;
            }
            WriteBatch batch = firestore.batch();
            boolean write = false;

            if (warehouses.getResult().isEmpty()) {
                // Coordenadas para el mapa de bodegas (Concepción, Talcahuano, Chillán).
                putWarehouse(batch, context.warehouseId(), context.warehouseName(), -36.8201, -73.0444);
                putWarehouse(batch, branchWarehouseId(), branchWarehouseName(), -36.7246, -73.1172);
                putWarehouse(batch, secondBranchWarehouseId(), secondBranchWarehouseName(), -36.6066, -72.1034);
                write = true;
            }
            if (products.getResult().isEmpty()) {
                for (String[] p : PRODUCTS) {
                    Map<String, Object> stock = new HashMap<>();
                    stock.put(context.warehouseId(), Integer.parseInt(p[6]));
                    stock.put(branchWarehouseId(), Integer.parseInt(p[7]));

                    Map<String, Object> data = new HashMap<>();
                    data.put("companyId", companyId);
                    data.put("code", p[0]);
                    data.put("name", p[1]);
                    data.put("barcode", p[2]);
                    data.put("location", p[3]);
                    data.put("category", p[4]);
                    data.put("minStock", Integer.parseInt(p[5]));
                    data.put("stock", stock);
                    batch.set(firestore.collection(FirestoreCollections.PRODUCTS).document(productId(p[0])), data);
                }
                write = true;
            }

            if (!write) {
                callback.onResult(true);
                return;
            }
            batch.commit()
                    .addOnSuccessListener(v -> callback.onResult(true))
                    .addOnFailureListener(e -> callback.onResult(false));
        });
    }

    private void putWarehouse(WriteBatch batch, String id, String name, double lat, double lng) {
        Map<String, Object> data = new HashMap<>();
        data.put("companyId", context.companyId());
        data.put("name", name);
        data.put("lat", lat);
        data.put("lng", lng);
        batch.set(firestore.collection(FirestoreCollections.WAREHOUSES).document(id), data);
    }

    /** Mapa de una línea (ITEMS) a partir de una fila del catálogo; el llamador agrega cantidades. */
    public Map<String, Object> lineFromCatalog(String code) {
        String[] p = row(code);
        Map<String, Object> data = new HashMap<>();
        if (p == null) return data;
        data.put("productId", productId(p[0]));
        data.put("productCode", p[0]);
        data.put("productName", p[1]);
        data.put("barcode", p[2]);
        data.put("location", p[3]);
        return data;
    }
}
