package com.example.erprukainventario.home.presentation;

public class HomeUiState {

    public final String greetingName;
    public final String roleLabel;
    public final String warehouseName;
    public final String initials;
    public final int pendingTasksCount;

    public HomeUiState(String greetingName, String roleLabel, String warehouseName,
                       String initials, int pendingTasksCount) {
        this.greetingName = greetingName;
        this.roleLabel = roleLabel;
        this.warehouseName = warehouseName;
        this.initials = initials;
        this.pendingTasksCount = pendingTasksCount;
    }
}
