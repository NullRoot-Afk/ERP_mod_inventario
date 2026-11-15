package com.example.erprukainventario.core.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.erprukainventario.R;
import com.example.erprukainventario.databinding.ActivityTaskListBinding;

/**
 * Pantalla de lista compartida: header, buscador, 2-3 pestañas, lista y una
 * acción principal opcional. Cada módulo solo define textos y qué pasa al tocar.
 * La subclase concreta lleva @AndroidEntryPoint.
 */
public abstract class BaseTaskListActivity extends AppCompatActivity {

    protected ActivityTaskListBinding binding;
    private TaskSummaryAdapter adapter;
    private boolean firstStart = true;

    protected abstract TaskListViewModel<?> viewModel();
    protected abstract String screenTitle();
    protected abstract String searchHint();
    protected abstract String[] tabLabels();
    protected abstract String emptyText();
    protected abstract void onItemClick(TaskSummary item);

    /** null = sin botón inferior. */
    protected String primaryActionLabel() { return null; }
    protected void onPrimaryAction() { }

    /** Texto del diálogo para cargar datos de prueba con toque largo en el título; null = deshabilitado. */
    protected String seedDescription() { return null; }
    protected void onSeedConfirmed() { }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTaskListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.tvTitle.setText(screenTitle());
        binding.tilSearch.setHint(searchHint());
        binding.tvEmpty.setText(emptyText());

        adapter = new TaskSummaryAdapter(this::onItemClick);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);

        setupTabs();
        setupListeners();
        observe();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Recarga al volver de una sesión de escaneo o formulario.
        if (!firstStart) viewModel().load();
        firstStart = false;
    }

    private void setupTabs() {
        String[] labels = tabLabels();
        TextView[] tabs = {binding.tab0, binding.tab1, binding.tab2};
        for (int i = 0; i < tabs.length; i++) {
            if (i < labels.length) {
                final int index = i;
                tabs[i].setText(labels[i]);
                tabs[i].setVisibility(View.VISIBLE);
                tabs[i].setOnClickListener(v -> viewModel().onTab(index));
            } else {
                tabs[i].setVisibility(View.GONE);
            }
        }
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel().onSearch(s.toString());
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        String action = primaryActionLabel();
        if (action != null) {
            binding.tvPrimaryAction.setText(action);
            binding.btnPrimaryAction.setVisibility(View.VISIBLE);
            binding.btnPrimaryAction.setOnClickListener(v -> onPrimaryAction());
        }

        // Toque largo en el título: datos de prueba (solo desarrollo), igual que en Recepción.
        binding.tvTitle.setOnLongClickListener(v -> {
            String description = seedDescription();
            if (description == null) return false;
            new AlertDialog.Builder(this)
                    .setTitle("Cargar datos de prueba")
                    .setMessage(description)
                    .setPositiveButton("Cargar", (d, w) -> onSeedConfirmed())
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return true;
        });
    }

    private void observe() {
        viewModel().getUiState().observe(this, state -> {
            binding.progressBar.setVisibility(state.isLoading ? View.VISIBLE : View.GONE);
            binding.tvEmpty.setVisibility(!state.isLoading && state.items.isEmpty() ? View.VISIBLE : View.GONE);
            adapter.submit(state.items);

            TextView[] tabs = {binding.tab0, binding.tab1, binding.tab2};
            for (int i = 0; i < tabs.length; i++) {
                boolean selected = i == state.selectedTab;
                tabs[i].setBackgroundResource(selected ? R.drawable.bg_tab_selected : R.drawable.bg_tab_unselected);
                tabs[i].setTextColor(getColor(selected ? R.color.on_accent : R.color.text_primary));
            }
        });

        viewModel().getMessages().observe(this, event -> {
            String msg = event.consume();
            if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        });
    }
}
