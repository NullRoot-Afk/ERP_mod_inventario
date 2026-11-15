package com.example.erprukainventario.picking.data;

import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.picking.domain.PickingOrder;

import java.util.List;

public interface PickingRepository {

    /** Pedidos de la bodega del usuario (todos los estados). */
    void getOrders(RepositoryCallback<List<PickingOrder>> callback);

    /**
     * Toma el pedido: queda asignado al usuario. Con markInProgress pasa a EN_PROCESO
     * (no se usa para pedidos PARCIAL, que deben seguir visibles en Despacho).
     */
    void assignToMe(String orderId, boolean markInProgress, RepositoryCallback<Boolean> callback);

    void seedDemoData(RepositoryCallback<Boolean> callback);
}
