package com.example.erprukainventario.core.scan;

public class ScanSessionUiState {

    public enum FilterTab { ALL, DONE, PENDING }

    public final ScanTask task;
    public final boolean isLoading;
    public final boolean isSaving;
    public final FilterTab tab;

    public ScanSessionUiState() {
        this(null, true, false, FilterTab.PENDING);
    }

    private ScanSessionUiState(ScanTask task, boolean isLoading, boolean isSaving, FilterTab tab) {
        this.task = task;
        this.isLoading = isLoading;
        this.isSaving = isSaving;
        this.tab = tab;
    }

    public ScanSessionUiState withLoading() { return new ScanSessionUiState(task, true, false, tab); }
    public ScanSessionUiState withTask(ScanTask t) { return new ScanSessionUiState(t, false, false, tab); }
    public ScanSessionUiState withSaving(boolean saving) { return new ScanSessionUiState(task, false, saving, tab); }
    public ScanSessionUiState withTab(FilterTab t) { return new ScanSessionUiState(task, isLoading, isSaving, t); }
}
