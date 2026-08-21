package com.example.erprukainventario.auth.data;

import com.example.erprukainventario.auth.domain.AuthUser;
import com.example.erprukainventario.auth.domain.Warehouse;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface AuthApi {

    @GET("v1/warehouses")
    Call<List<Warehouse>> getWarehouses();

    @POST("v1/auth/login")
    Call<LoginResponse> login(@Body LoginRequest request);

    class LoginRequest {
        public final String username;
        public final String password;
        public final String warehouseId;

        public LoginRequest(String username, String password, String warehouseId) {
            this.username = username;
            this.password = password;
            this.warehouseId = warehouseId;
        }
    }

    class LoginResponse {
        public AuthUser user;
        public String accessToken;
        public String refreshToken;
    }
}
