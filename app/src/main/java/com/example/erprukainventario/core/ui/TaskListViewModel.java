package com.example.erprukainventario.core.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.erprukainventario.auth.data.RepositoryCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * Base de los ViewModels de lista: guarda la lista completa (T = objeto de dominio)
 * y aplica pestaña + búsqueda en memoria, igual que ReceptionListViewModel.
 */
public abstract class TaskListViewModel<T> extends ViewModel {

    protected final MutableLiveData<TaskListUiState> uiState = new MutableLiveData<>(new TaskListUiState());
    protected final MutableLiveData<Event<String>> messages = new MutableLiveData<>();

    private List<T> master = new ArrayList<>();
    private String query = "";
    private int tab = 0;

    public LiveData<TaskListUiState> getUiState() { return uiState; }
    public LiveData<Event<String>> getMessages() { return messages; }

    /** Descarga los objetos de dominio del repositorio. */
    protected abstract void fetch(RepositoryCallback<List<T>> callback);

    protected abstract boolean belongsToTab(T item, int tab);

    protected abstract TaskSummary toSummary(T item);

    protected abstract String idOf(T item);

    public void load() {
        uiState.setValue(new TaskListUiState(current().items, true, tab));
        fetch(items -> {
            master = items != null ? items : new ArrayList<T>();
            apply();
        });
    }

    public void onSearch(String text) {
        query = text != null ? text.trim().toLowerCase(java.util.Locale.ROOT) : "";
        apply();
    }

    public void onTab(int tab) {
        this.tab = tab;
        apply();
    }

    public T findById(String id) {
        for (T item : master) {
            if (idOf(item).equals(id)) return item;
        }
        return null;
    }

    protected void showMessage(String message) {
        messages.setValue(new Event<>(message));
    }

    /** Muestra el mensaje y recarga si la operación salió bien. */
    protected RepositoryCallback<Boolean> reloadAfter(String okMessage, String errorMessage) {
        return ok -> {
            showMessage(ok ? okMessage : errorMessage);
            if (ok) load();
        };
    }

    private void apply() {
        List<TaskSummary> filtered = new ArrayList<>();
        for (T item : master) {
            if (!belongsToTab(item, tab)) continue;
            TaskSummary summary = toSummary(item);
            if (summary.matches(query)) filtered.add(summary);
        }
        uiState.setValue(new TaskListUiState(filtered, false, tab));
    }

    private TaskListUiState current() {
        TaskListUiState s = uiState.getValue();
        return s != null ? s : new TaskListUiState();
    }
}
