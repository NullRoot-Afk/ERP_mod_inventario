package com.example.erprukainventario.home.presentation;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.erprukainventario.auth.data.SessionManager;
import com.example.erprukainventario.auth.domain.AuthUser;
import com.example.erprukainventario.auth.domain.UserRole;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class HomeViewModel extends ViewModel {

    private final MutableLiveData<HomeUiState> uiState = new MutableLiveData<>();

    @Inject
    public HomeViewModel(SessionManager sessionManager) {
        AuthUser user = sessionManager.getCurrentUser();

        String fullName = user != null ? user.getFullName() : "";
        String firstName = toFirstName(fullName);
        String roleLabel = user != null ? roleToLabel(user.getRole()) : "";
        String warehouseName = user != null ? user.getWarehouseName() : "";
        String initials = toInitials(fullName);

        // TODO: reemplazar por el conteo real de tareas asignadas cuando
        // exista el feature de picking/recepción/etc. con su propio backend.
        int pendingTasksCount = 3;

        uiState.setValue(new HomeUiState(firstName, roleLabel, warehouseName, initials, pendingTasksCount));
    }

    public LiveData<HomeUiState> getUiState() {
        return uiState;
    }

    private String toFirstName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) return "";
        return fullName.trim().split("\\s+")[0];
    }

    private String roleToLabel(UserRole role) {
        switch (role) {
            case OPERARIO: return "Operario";
            case SUPERVISOR: return "Supervisor";
            case JEFE_BODEGA: return "Jefe de bodega";
            default: return "";
        }
    }

    private String toInitials(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) return "?";
        String[] parts = fullName.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(2, parts.length); i++) {
            sb.append(Character.toUpperCase(parts[i].charAt(0)));
        }
        return sb.toString();
    }
}
