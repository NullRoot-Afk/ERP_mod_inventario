package com.example.erprukainventario.count.data;

import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.core.domain.OperationResult;
import com.example.erprukainventario.count.domain.CountTask;
import com.example.erprukainventario.count.domain.CountType;

import java.util.List;

public interface CountRepository {

    void getCounts(RepositoryCallback<List<CountTask>> callback);

    /**
     * Programa un conteo a partir del catálogo.
     * @param filter prefijo de ubicación (ZONA) o categoría (CATEGORIA); se ignora en los demás tipos.
     */
    void createCount(CountType type, String filter, RepositoryCallback<OperationResult> callback);

    void seedDemoData(RepositoryCallback<Boolean> callback);
}
