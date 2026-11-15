package com.example.erprukainventario.core.data;

import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.auth.domain.Warehouse;
import com.example.erprukainventario.core.domain.Movement;
import com.example.erprukainventario.core.domain.OperationResult;
import com.example.erprukainventario.core.domain.Product;

import java.util.List;

public interface ProductRepository {

    /** Busca por código de barras y, si no hay, por SKU. Entrega null si no existe. */
    void findByCode(String code, RepositoryCallback<Product> callback);

    void getProducts(RepositoryCallback<List<Product>> callback);

    /** Historial del producto, del más reciente al más antiguo. */
    void getMovements(String productId, int limit, RepositoryCallback<List<Movement>> callback);

    /** Movimientos de la bodega del usuario desde una fecha (productividad, guía §12). */
    void getWarehouseMovements(java.util.Date since, RepositoryCallback<List<Movement>> callback);

    /** Bodegas de la empresa configurada. */
    void getWarehouses(RepositoryCallback<List<Warehouse>> callback);

    /** Solo supervisor/jefe: activa o quita el control por número de serie (guía §10). */
    void setSerialized(String productId, boolean serialized, RepositoryCallback<OperationResult> callback);

    /** Ids de los productos que controlan número de serie (funciona offline con el caché). */
    void getSerializedProductIds(RepositoryCallback<java.util.Set<String>> callback);
}
