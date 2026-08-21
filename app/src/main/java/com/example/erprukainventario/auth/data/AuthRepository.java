package com.example.erprukainventario.auth.data;


import com.example.erprukainventario.auth.domain.LoginResult;
import com.example.erprukainventario.auth.domain.Warehouse;

import java.util.List;

public interface AuthRepository {
    void getWarehouses(RepositoryCallback<List<Warehouse>> callback);
    void login(String username, String password, String warehouseId, RepositoryCallback<LoginResult> callback);
}
