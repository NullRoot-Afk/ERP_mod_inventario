package com.example.erprukainventario.count.presentation;

import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.core.data.WorkContext;
import com.example.erprukainventario.core.ui.TaskListViewModel;
import com.example.erprukainventario.core.ui.TaskSummary;
import com.example.erprukainventario.count.data.CountRepository;
import com.example.erprukainventario.count.domain.CountStatus;
import com.example.erprukainventario.count.domain.CountTask;
import com.example.erprukainventario.count.domain.CountType;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class CountListViewModel extends TaskListViewModel<CountTask> {

    static final int TAB_PENDING = 0;

    private final CountRepository repository;
    private final WorkContext context;

    @Inject
    public CountListViewModel(CountRepository repository, WorkContext context) {
        this.repository = repository;
        this.context = context;
        load();
    }

    public boolean canCreateCounts() {
        return context.canCreateCounts();
    }

    @Override
    protected void fetch(RepositoryCallback<List<CountTask>> callback) {
        repository.getCounts(callback);
    }

    @Override
    protected boolean belongsToTab(CountTask c, int tab) {
        return (tab == TAB_PENDING) == (c.getStatus() != CountStatus.CERRADO);
    }

    @Override
    protected TaskSummary toSummary(CountTask c) {
        if (c.getStatus() == CountStatus.CERRADO) {
            boolean ok = c.getDifferencesCount() == 0;
            return new TaskSummary(c.getId(), c.getName(), c.getLineCount() + " productos contados",
                    "Cerrado por " + c.getClosedByName(),
                    ok ? "Sin diferencias" : c.getDifferencesCount() + " diferencias",
                    ok ? TaskSummary.Badge.SUCCESS : TaskSummary.Badge.WARNING);
        }
        return new TaskSummary(c.getId(), c.getName(), c.getLineCount() + " ubicaciones",
                "Programado por " + c.getCreatedByName(), c.getStatus().getLabel(), TaskSummary.Badge.NEUTRAL);
    }

    @Override
    protected String idOf(CountTask item) {
        return item.getId();
    }

    public void createCount(CountType type, String filter) {
        repository.createCount(type, filter, result -> {
            showMessage(result.getMessage());
            if (result.isSuccess()) load();
        });
    }

    public void seedDemoData() {
        repository.seedDemoData(reloadAfter("Conteos de prueba programados.", "No se pudieron cargar los datos."));
    }
}
