
package com.example.erprukainventario.auth.data;

import com.example.erprukainventario.auth.domain.AuthUser;
import com.example.erprukainventario.auth.domain.Company;

//Retrofit Biblioteca cliente HTTP para facilitar el consumo de APIs
//Maneja la interaccion entre la app y el servidor
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

//Las llamadas se estructuran en una interfaz que es implementada automaticamente en tiempo de ejecucion
public interface AuthApi {
    //implementacion parecida a las APIs REST de Flask con python para el backend
    //Retrofit convierte automaticamente la respuesta de la API
    @GET("v1/companies/validate")
    Call<Company> validateCompany(@Query("code") String companyCode); //la API espera el parametro "code",aqui se envia mediante url
    @POST("v1/auth/login")
    Call<LoginResponse> login(@Body LoginRequest request);

    class LoginRequest {
        public final String username;
        public final String password;
        public final String companyId;

        public LoginRequest(String username, String password, String companyId) {
            this.username = username;
            this.password = password;
            this.companyId = companyId;
        }
    }

    class LoginResponse {
        public AuthUser user;
        public String accessToken;
        public String refreshToken;
    }
}
