package com.example.erprukainventario.core.data;

import android.util.Log;

import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.auth.domain.Warehouse;
import com.example.erprukainventario.core.domain.Movement;
import com.example.erprukainventario.core.domain.MovementType;
import com.example.erprukainventario.core.domain.Product;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Modelo: PRODUCTS/{id} (companyId, code, name, barcode, location, category, minStock,
 *                         stock: {warehouseId: cantidad})
 *         MOVEMENTS/{id} (companyId, warehouseId, productId, type, quantity, reference,
 *                         userId, userName, createdAt)
 *         WAREHOUSES/{id} (companyId, name)
 *
 * Las consultas usan solo filtros de igualdad y se ordenan en el cliente:
 * así no se necesitan índices compuestos en Firestore.
 */
@Singleton
public class ProductRepositoryImpl implements ProductRepository {

    private static final String TAG = "ProductRepo";

    private final FirebaseFirestore firestore;
    private final WorkContext context;

    @Inject
    public ProductRepositoryImpl(FirebaseFirestore firestore, WorkContext context) {
        this.firestore = firestore;
        this.context = context;
    }

    @Override
    public void findByCode(String code, RepositoryCallback<Product> callback) {
        String companyId = context.companyId();
        if (companyId == null || code == null || code.trim().isEmpty()) {
            callback.onResult(null);
            return;
        }
        final String target = code.trim();

        firestore.collection(FirestoreCollections.PRODUCTS)
                .whereEqualTo("companyId", companyId)
                .whereEqualTo("barcode", target)
                .limit(1)
                .get()
                .addOnSuccessListener(byBarcode -> {
                    if (!byBarcode.isEmpty()) {
                        callback.onResult(toProduct(byBarcode.getDocuments().get(0)));
                        return;
                    }
                    firestore.collection(FirestoreCollections.PRODUCTS)
                            .whereEqualTo("companyId", companyId)
                            .whereEqualTo("code", target.toUpperCase(java.util.Locale.ROOT))
                            .limit(1)
                            .get()
                            .addOnSuccessListener(byCode -> callback.onResult(
                                    byCode.isEmpty() ? null : toProduct(byCode.getDocuments().get(0))))
                            .addOnFailureListener(e -> callback.onResult(null));
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "findByCode falló", e);
                    callback.onResult(null);
                });
    }

    @Override
    public void getProducts(RepositoryCallback<List<Product>> callback) {
        String companyId = context.companyId();
        if (companyId == null) {
            callback.onResult(new ArrayList<>());
            return;
        }
        firestore.collection(FirestoreCollections.PRODUCTS)
                .whereEqualTo("companyId", companyId)
                .get()
                .addOnCompleteListener(task -> {
                    List<Product> products = new ArrayList<>();
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                            products.add(toProduct(doc));
                        }
                        Collections.sort(products, (a, b) -> a.getLocation().compareToIgnoreCase(b.getLocation()));
                    } else {
                        Log.e(TAG, "getProducts falló", task.getException());
                    }
                    callback.onResult(products);
                });
    }

    @Override
    public void getMovements(String productId, int limit, RepositoryCallback<List<Movement>> callback) {
        String companyId = context.companyId();
        if (companyId == null || productId == null) {
            callback.onResult(new ArrayList<>());
            return;
        }
        firestore.collection(FirestoreCollections.MOVEMENTS)
                .whereEqualTo("companyId", companyId)
                .whereEqualTo("productId", productId)
                .get()
                .addOnCompleteListener(task -> {
                    List<Movement> movements = new ArrayList<>();
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                            movements.add(toMovement(doc));
                        }
                        Collections.sort(movements, (a, b) -> {
                            Date da = a.getCreatedAt();
                            Date db = b.getCreatedAt();
                            if (da == null && db == null) return 0;
                            if (da == null) return -1; // pendiente de servidor = recién creado
                            if (db == null) return 1;
                            return db.compareTo(da);
                        });
                    } else {
                        Log.e(TAG, "getMovements falló", task.getException());
                    }
                    callback.onResult(movements.size() > limit ? new ArrayList<>(movements.subList(0, limit)) : movements);
                });
    }

    @Override
    public void getWarehouses(RepositoryCallback<List<Warehouse>> callback) {
        String companyId = context.companyId();
        if (companyId == null) {
            callback.onResult(new ArrayList<>());
            return;
        }
        firestore.collection(FirestoreCollections.WAREHOUSES)
                .whereEqualTo("companyId", companyId)
                .get()
                .addOnCompleteListener(task -> {
                    List<Warehouse> warehouses = new ArrayList<>();
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                            warehouses.add(new Warehouse(doc.getId(), Docs.str(doc, doc.getId(), "name")));
                        }
                        Collections.sort(warehouses, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                    }
                    callback.onResult(warehouses);
                });
    }

    // ---- mapeo ----

    static Product toProduct(DocumentSnapshot doc) {
        return new Product(
                doc.getId(),
                Docs.str(doc, doc.getId(), "code", "sku"),
                Docs.str(doc, "Producto", "name", "productName"),
                Docs.str(doc, "S/C", "barcode", "ean"),
                Docs.str(doc, "Sin ubicación", "location"),
                Docs.str(doc, "", "category"),
                Docs.num(doc, "minStock"),
                Docs.intMap(doc, "stock")
        );
    }

    private static Movement toMovement(DocumentSnapshot doc) {
        MovementType type;
        try {
            type = MovementType.valueOf(Docs.str(doc, "AJUSTE", "type"));
        } catch (IllegalArgumentException e) {
            type = MovementType.AJUSTE;
        }
        return new Movement(
                type,
                Docs.num(doc, "quantity"),
                Docs.str(doc, "", "reference"),
                Docs.str(doc, "", "userName"),
                Docs.str(doc, "", "warehouseId"),
                Docs.date(doc, "createdAt")
        );
    }
}
