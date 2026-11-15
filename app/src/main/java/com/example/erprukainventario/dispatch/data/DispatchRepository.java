package com.example.erprukainventario.dispatch.data;

import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.picking.domain.PickingOrder;

import java.util.List;

public interface DispatchRepository {

    /** Pedidos de la bodega (se filtran en la lista: listos / despachados). */
    void getOrders(RepositoryCallback<List<PickingOrder>> callback);
}
