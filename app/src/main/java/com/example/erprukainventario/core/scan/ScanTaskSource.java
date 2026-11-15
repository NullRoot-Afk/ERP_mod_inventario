package com.example.erprukainventario.core.scan;

import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.core.domain.OperationResult;

import java.util.List;
import java.util.Map;

/**
 * Lo que cada módulo implementa para usar la pantalla de escaneo genérica.
 * Se registran en Hilt con @IntoMap + @StringKey(ScanModes.X).
 */
public interface ScanTaskSource {

    /** Entrega null si la tarea no existe o ya no se puede trabajar. */
    void load(String taskId, RepositoryCallback<ScanTask> callback);

    /**
     * Guarda el resultado. {@code extras} trae lo ingresado en {@link ScanConfig#finishPrompts},
     * con la etiqueta del campo como clave.
     */
    void save(String taskId, List<ScanLine> lines, Map<String, String> extras,
              RepositoryCallback<OperationResult> callback);
}
