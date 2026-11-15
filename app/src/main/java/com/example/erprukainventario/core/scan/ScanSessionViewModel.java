package com.example.erprukainventario.core.scan;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.erprukainventario.core.domain.OperationResult;
import com.example.erprukainventario.core.ui.Event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * Lógica común de todas las sesiones de escaneo. Qué se carga y qué se
 * guarda lo decide el ScanTaskSource del módulo (elegido por "mode").
 */
@HiltViewModel
public class ScanSessionViewModel extends ViewModel {

    public enum FinishCheck { OK, BLOCKED, NEEDS_CONFIRMATION }

    private final Map<String, ScanTaskSource> sources;
    private final MutableLiveData<ScanSessionUiState> uiState = new MutableLiveData<>(new ScanSessionUiState());
    private final MutableLiveData<Event<String>> messages = new MutableLiveData<>();
    private final MutableLiveData<Event<OperationResult>> finishResult = new MutableLiveData<>();

    private ScanTaskSource source;
    private String taskId;
    private boolean dirty = false;

    @Inject
    public ScanSessionViewModel(Map<String, ScanTaskSource> sources) {
        this.sources = sources;
    }

    public LiveData<ScanSessionUiState> getUiState() { return uiState; }
    public LiveData<Event<String>> getMessages() { return messages; }
    public LiveData<Event<OperationResult>> getFinishResult() { return finishResult; }

    /** Solo carga la primera vez (sobrevive a rotaciones). */
    public void init(String mode, String taskId) {
        if (this.source != null) return;
        this.source = sources.get(mode);
        this.taskId = taskId;
        if (source == null || taskId == null) {
            finishResult.setValue(new Event<>(OperationResult.error("Tarea no disponible.")));
            return;
        }
        uiState.setValue(state().withLoading());
        source.load(taskId, task -> {
            if (task == null) {
                finishResult.setValue(new Event<>(OperationResult.error(
                        "La tarea no existe o ya fue cerrada por otro usuario.")));
                return;
            }
            uiState.setValue(state().withTask(task));
        });
    }

    public ScanTask getTask() {
        return state().task;
    }

    public boolean hasUnsavedChanges() {
        return dirty;
    }

    public ScanLine findLine(String code) {
        ScanTask task = getTask();
        if (task == null) return null;
        for (ScanLine line : task.getLines()) {
            if (line.matches(code)) return line;
        }
        return null;
    }

    /**
     * Fija la cantidad escaneada de una línea.
     * @return mensaje de error si viola las reglas de la sesión; null si se aplicó.
     */
    public String setQuantity(String lineId, int newQuantity) {
        ScanTask task = getTask();
        if (task == null) return "Tarea no cargada.";
        if (newQuantity < 0) return "La cantidad no puede ser negativa.";

        List<ScanLine> updated = new ArrayList<>();
        for (ScanLine line : task.getLines()) {
            if (line.getId().equals(lineId)) {
                if (line.isSerialized()) return "Este producto se registra por número de serie.";
                if (!task.getConfig().allowExceedExpected && newQuantity > line.getExpectedQuantity()) {
                    return "No puedes superar lo esperado (" + line.getExpectedQuantity() + " un.).";
                }
                updated.add(line.withScanned(newQuantity));
            } else {
                updated.add(line);
            }
        }
        dirty = true;
        uiState.setValue(state().withTask(task.withLines(updated)));
        return null;
    }

    /**
     * Línea con número de serie: la cantidad es la cantidad de series capturadas.
     * @return mensaje de error si viola las reglas de la sesión; null si se aplicó.
     */
    public String setSerials(String lineId, List<String> serials) {
        ScanTask task = getTask();
        if (task == null) return "Tarea no cargada.";
        List<ScanLine> updated = new ArrayList<>();
        for (ScanLine line : task.getLines()) {
            if (line.getId().equals(lineId)) {
                if (!task.getConfig().allowExceedExpected && serials.size() > line.getExpectedQuantity()) {
                    return "No puedes superar lo esperado (" + line.getExpectedQuantity() + " un.).";
                }
                updated.add(line.withSerials(serials));
            } else {
                updated.add(line);
            }
        }
        dirty = true;
        uiState.setValue(state().withTask(task.withLines(updated)));
        return null;
    }

    public void setTab(ScanSessionUiState.FilterTab tab) {
        uiState.setValue(state().withTab(tab));
    }

    public List<ScanLine> getVisibleLines() {
        ScanSessionUiState s = state();
        List<ScanLine> result = new ArrayList<>();
        if (s.task == null) return result;
        for (ScanLine line : s.task.getLines()) {
            boolean done = s.task.getConfig().showExpected ? line.isComplete() : line.isStarted();
            if (s.tab == ScanSessionUiState.FilterTab.ALL
                    || (s.tab == ScanSessionUiState.FilterTab.DONE && done)
                    || (s.tab == ScanSessionUiState.FilterTab.PENDING && !done)) {
                result.add(line);
            }
        }
        return result;
    }

    /** [hechas, total] de líneas, para la barra de avance. */
    public int[] getProgress() {
        ScanTask task = getTask();
        if (task == null) return new int[]{0, 0};
        int done = 0;
        for (ScanLine line : task.getLines()) {
            if (task.getConfig().showExpected ? line.isComplete() : line.isStarted()) done++;
        }
        return new int[]{done, task.getLines().size()};
    }

    public FinishCheck checkFinish() {
        ScanTask task = getTask();
        if (task == null) return FinishCheck.BLOCKED;
        boolean incomplete = false;
        for (ScanLine line : task.getLines()) {
            if (task.getConfig().showExpected && !line.isComplete()) {
                incomplete = true;
                break;
            }
        }
        if (!incomplete) return FinishCheck.OK;
        if (task.getConfig().requireComplete) return FinishCheck.BLOCKED;
        return task.getConfig().incompleteWarning != null ? FinishCheck.NEEDS_CONFIRMATION : FinishCheck.OK;
    }

    public void finish(Map<String, String> extras) {
        ScanTask task = getTask();
        if (task == null || source == null || state().isSaving) return;
        uiState.setValue(state().withSaving(true));
        source.save(taskId, task.getLines(), extras, result -> {
            uiState.setValue(state().withSaving(false));
            if (result != null && result.isSuccess()) {
                dirty = false;
                finishResult.setValue(new Event<>(result));
            } else {
                messages.setValue(new Event<>(result != null ? result.getMessage()
                        : "No se pudo guardar. Intenta nuevamente."));
            }
        });
    }

    private ScanSessionUiState state() {
        ScanSessionUiState s = uiState.getValue();
        return s != null ? s : new ScanSessionUiState();
    }
}
