package com.example.erprukainventario.core.ui;

import java.util.Collections;
import java.util.List;

public class TaskListUiState {

    public final List<TaskSummary> items;
    public final boolean isLoading;
    public final int selectedTab;

    public TaskListUiState() {
        this(Collections.<TaskSummary>emptyList(), true, 0);
    }

    public TaskListUiState(List<TaskSummary> items, boolean isLoading, int selectedTab) {
        this.items = items;
        this.isLoading = isLoading;
        this.selectedTab = selectedTab;
    }
}
