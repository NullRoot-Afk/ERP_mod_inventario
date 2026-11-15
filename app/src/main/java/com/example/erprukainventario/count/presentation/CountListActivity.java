package com.example.erprukainventario.count.presentation;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;

import com.example.erprukainventario.R;
import com.example.erprukainventario.core.scan.ScanModes;
import com.example.erprukainventario.core.scan.ScanSessionActivity;
import com.example.erprukainventario.core.ui.BaseTaskListActivity;
import com.example.erprukainventario.core.ui.Dialogs;
import com.example.erprukainventario.core.ui.TaskListViewModel;
import com.example.erprukainventario.core.ui.TaskSummary;
import com.example.erprukainventario.count.domain.CountStatus;
import com.example.erprukainventario.count.domain.CountTask;
import com.example.erprukainventario.count.domain.CountType;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class CountListActivity extends BaseTaskListActivity {

    private CountListViewModel vm;

    @Override
    protected TaskListViewModel<?> viewModel() {
        if (vm == null) vm = new ViewModelProvider(this).get(CountListViewModel.class);
        return vm;
    }

    @Override protected String screenTitle() { return getString(R.string.count_title); }
    @Override protected String searchHint() { return getString(R.string.count_search_hint); }
    @Override protected String emptyText() { return getString(R.string.count_empty); }

    @Override
    protected String[] tabLabels() {
        return new String[]{getString(R.string.tab_pending), getString(R.string.count_tab_closed)};
    }

    /** Solo supervisor/jefe programa conteos (guía §1, permisos por rol). */
    @Override
    protected String primaryActionLabel() {
        return ((CountListViewModel) viewModel()).canCreateCounts() ? getString(R.string.count_new) : null;
    }

    @Override
    protected void onPrimaryAction() {
        String[] options = {"Por zona / ubicación", "Por categoría", "Aleatorio (5 productos)", "Conteo general"};
        new AlertDialog.Builder(this)
                .setTitle(R.string.count_new)
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0:
                            Dialogs.textInput(this, "Zona a contar", "Prefijo de ubicación, ej. A o B-01",
                                    "Programar", zone -> vm.createCount(CountType.ZONA, zone), null);
                            break;
                        case 1:
                            Dialogs.textInput(this, "Categoría a contar", "Ej. Herramientas, Seguridad",
                                    "Programar", cat -> vm.createCount(CountType.CATEGORIA, cat), null);
                            break;
                        case 2:
                            vm.createCount(CountType.ALEATORIO, "");
                            break;
                        default:
                            vm.createCount(CountType.GENERAL, "");
                    }
                })
                .show();
    }

    @Override
    protected void onItemClick(TaskSummary item) {
        CountTask count = vm.findById(item.id);
        if (count == null) return;
        if (count.getStatus() == CountStatus.CERRADO) {
            new AlertDialog.Builder(this)
                    .setTitle(count.getName())
                    .setMessage(count.getDifferencesCount() == 0
                            ? "Cerrado sin diferencias por " + count.getClosedByName() + "."
                            : "Diferencias (enviadas a Ajustes para aprobación):\n\n" + count.getSummary())
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        startActivity(ScanSessionActivity.intent(this, ScanModes.COUNT, count.getId()));
    }

    @Override
    protected String seedDescription() {
        return "Se programarán 3 conteos de prueba (zona A, categoría Seguridad y general) "
                + "y el catálogo de productos si no existe.";
    }

    @Override
    protected void onSeedConfirmed() {
        vm.seedDemoData();
    }
}
