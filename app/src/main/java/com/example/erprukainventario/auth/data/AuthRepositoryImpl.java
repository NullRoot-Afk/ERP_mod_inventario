package com.example.erprukainventario.auth.data;

import android.os.Handler;
import android.os.Looper;

import com.example.erprukainventario.auth.domain.AuthUser;
import com.example.erprukainventario.auth.domain.Company;
import com.example.erprukainventario.auth.domain.LoginResult;
import com.example.erprukainventario.auth.domain.UserRole;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AuthRepositoryImpl implements AuthRepository {

    private final AuthApi authApi;
    private final AuthLocalDataSource localDataSource;
    private final TenantConfigLocalDataSource tenantConfig;
    private final SessionManager sessionManager;
    private final FirebaseAuth firebaseAuth;
    private final FirebaseFirestore firestore;

    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Inject
    public AuthRepositoryImpl(
            AuthApi authApi,
            AuthLocalDataSource localDataSource,
            TenantConfigLocalDataSource tenantConfig,
            SessionManager sessionManager,
            FirebaseAuth firebaseAuth,
            FirebaseFirestore firestore
    ) {
        this.authApi = authApi;
        this.localDataSource = localDataSource;
        this.tenantConfig = tenantConfig;
        this.sessionManager = sessionManager;
        this.firebaseAuth = firebaseAuth;
        this.firestore = firestore;
    }

    @Override
    public void validateAndSaveCompany(String companyCode, RepositoryCallback<CompanyValidationResult> callback) {
        if (companyCode == null || companyCode.trim().isEmpty()) {
            callback.onResult(CompanyValidationResult.error("Debes ingresar un código de empresa."));
            return;
        }

        String targetCode = companyCode.trim();
        String[] possibleCollections = new String[]{"COMPANIES", "Companies", "companies", "COMPANY", "Company", "company", "EMPRESAS", "Empresas", "empresas"};

        checkNextCollection(possibleCollections, 0, targetCode, callback);
    }

    private void checkNextCollection(
            String[] collections,
            int index,
            String targetCode,
            RepositoryCallback<CompanyValidationResult> callback
    ) {
        if (index >= collections.length) {
            callback.onResult(CompanyValidationResult.error(
                    "No se encontró la empresa '" + targetCode + "' en Firestore. Verifica que exista la colección 'companies' con el documento '" + targetCode + "'."
            ));
            return;
        }

        String collectionName = collections[index];

        // 1. Probar consultar el documento directo por ID
        firestore.collection(collectionName).document(targetCode).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        saveCompanyAndReturn(targetCode, doc, callback);
                    } else {
                        // Probar en mayúsculas
                        firestore.collection(collectionName).document(targetCode.toUpperCase()).get()
                                .addOnSuccessListener(docUpper -> {
                                    if (docUpper.exists()) {
                                        saveCompanyAndReturn(targetCode.toUpperCase(), docUpper, callback);
                                    } else {
                                        searchInCollectionDocs(collectionName, targetCode, collections, index, callback);
                                    }
                                })
                                .addOnFailureListener(e -> searchInCollectionDocs(collectionName, targetCode, collections, index, callback));
                    }
                })
                .addOnFailureListener(e -> searchInCollectionDocs(collectionName, targetCode, collections, index, callback));
    }

    private void searchInCollectionDocs(
            String collectionName,
            String targetCode,
            String[] collections,
            int index,
            RepositoryCallback<CompanyValidationResult> callback
    ) {
        firestore.collection(collectionName).get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            String docId = doc.getId();
                            String fieldCode = doc.getString("code");
                            String fieldId = doc.getString("id");
                            String fieldName = doc.getString("name");

                            boolean isMatch = docId.equalsIgnoreCase(targetCode)
                                    || (fieldCode != null && fieldCode.equalsIgnoreCase(targetCode))
                                    || (fieldId != null && fieldId.equalsIgnoreCase(targetCode))
                                    || (fieldName != null && fieldName.equalsIgnoreCase(targetCode));

                            if (isMatch) {
                                saveCompanyAndReturn(docId, doc, callback);
                                return;
                            }
                        }
                    }
                    checkNextCollection(collections, index + 1, targetCode, callback);
                })
                .addOnFailureListener(e -> {
                    String msg = e.getLocalizedMessage();
                    if (msg != null && msg.contains("PERMISSION_DENIED")) {
                        callback.onResult(CompanyValidationResult.error(
                                "Permiso denegado en Firestore (PERMISSION_DENIED). Ve a Firebase Console -> Firestore -> Reglas y permite la lectura: allow read, write: if true;"
                        ));
                    } else {
                        checkNextCollection(collections, index + 1, targetCode, callback);
                    }
                });
    }

    private void saveCompanyAndReturn(String companyId, DocumentSnapshot doc, RepositoryCallback<CompanyValidationResult> callback) {
        String docName = doc.getString("name");
        String name = (docName != null && !docName.isEmpty()) ? docName : companyId;
        Company company = new Company(companyId, name);
        tenantConfig.saveCompany(company);
        callback.onResult(CompanyValidationResult.success(company));
    }

    @Override
    public void login(String email, String password, RepositoryCallback<LoginResult> callback) {
        Company company = tenantConfig.getConfiguredCompany();
        if (company == null) {
            callback.onResult(LoginResult.error("El dispositivo no tiene una empresa configurada."));
            return;
        }

        // 1. Autenticar con Firebase Auth
        firebaseAuth.signInWithEmailAndPassword(email.trim(), password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser == null) {
                        callback.onResult(LoginResult.error("Error al obtener usuario autenticado."));
                        return;
                    }

                    String uid = firebaseUser.getUid();
                    String[] userCollections = new String[]{"users", "USERS", "Users", "usuarios", "USUARIOS"};
                    fetchUserFromCollections(userCollections, 0, uid, firebaseUser, email, password, company, callback);
                })
                .addOnFailureListener(e -> attemptOfflineLogin(email, password, e, callback));
    }

    private void fetchUserFromCollections(
            String[] collections,
            int index,
            String uid,
            FirebaseUser firebaseUser,
            String email,
            String password,
            Company company,
            RepositoryCallback<LoginResult> callback
    ) {
        if (index >= collections.length) {
            firebaseAuth.signOut();
            callback.onResult(LoginResult.error(
                    "El usuario (" + email + ") no existe en la base de datos (colección 'users' o 'USERS'). Registra su documento en Firestore."
            ));
            return;
        }

        String collectionName = collections[index];
        firestore.collection(collectionName).document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        processUserDataAndVerifyCompany(doc, firebaseUser, email, password, company, callback);
                    } else {
                        firestore.collection(collectionName)
                                .whereEqualTo("email", email.trim())
                                .get()
                                .addOnSuccessListener(querySnapshot -> {
                                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                                        DocumentSnapshot userDoc = querySnapshot.getDocuments().get(0);
                                        processUserDataAndVerifyCompany(userDoc, firebaseUser, email, password, company, callback);
                                    } else {
                                        fetchUserFromCollections(collections, index + 1, uid, firebaseUser, email, password, company, callback);
                                    }
                                })
                                .addOnFailureListener(e -> fetchUserFromCollections(collections, index + 1, uid, firebaseUser, email, password, company, callback));
                    }
                })
                .addOnFailureListener(e -> fetchUserFromCollections(collections, index + 1, uid, firebaseUser, email, password, company, callback));
    }

    private void processUserDataAndVerifyCompany(
            DocumentSnapshot doc,
            FirebaseUser firebaseUser,
            String email,
            String password,
            Company configuredCompany,
            RepositoryCallback<LoginResult> callback
    ) {
        String userCompanyId = doc.getString("companyId");

        if (userCompanyId == null || !userCompanyId.trim().equalsIgnoreCase(configuredCompany.getId().trim())) {
            firebaseAuth.signOut();
            callback.onResult(LoginResult.error(
                    "Acceso denegado: En la base de datos tu usuario pertenece a la empresa '"
                            + (userCompanyId != null ? userCompanyId : "sin empresa")
                            + "', pero este dispositivo fue configurado con la empresa '" + configuredCompany.getId() + "'."
            ));
            return;
        }

        String docFullName = doc.getString("fullName");
        String docName = doc.getString("name");
        String resolvedName = (docFullName != null && !docFullName.isEmpty()) ? docFullName : docName;
        String fullName = (resolvedName != null && !resolvedName.isEmpty())
                ? resolvedName
                : (firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : email);

        UserRole role;
        String roleStr = doc.getString("role");
        if (roleStr != null) {
            try {
                role = UserRole.valueOf(roleStr.toUpperCase().trim());
            } catch (IllegalArgumentException e) {
                role = UserRole.OPERARIO;
            }
        } else {
            role = UserRole.OPERARIO;
        }

        String warehouseId = doc.getString("warehouseId");
        String warehouseName = doc.getString("warehouseName");

        AuthUser authUser = new AuthUser(firebaseUser.getUid(), fullName, role, warehouseId, warehouseName);
        dbExecutor.execute(() -> {
            localDataSource.cacheCredentials(email, password, authUser);
            sessionManager.setCurrentUser(authUser);
            mainHandler.post(() -> callback.onResult(LoginResult.success(authUser, false)));
        });
    }

    private void attemptOfflineLogin(
            String email,
            String password,
            Exception firebaseException,
            RepositoryCallback<LoginResult> callback
    ) {
        dbExecutor.execute(() -> {
            AuthUser cachedUser = localDataSource.validateOffline(email, password);
            mainHandler.post(() -> {
                if (cachedUser != null) {
                    sessionManager.setCurrentUser(cachedUser);
                    callback.onResult(LoginResult.success(cachedUser, true));
                } else {
                    String errorMsg = mapFirebaseError(firebaseException);
                    callback.onResult(LoginResult.error(errorMsg));
                }
            });
        });
    }

    @Override
    public void sendPasswordResetEmail(String email, RepositoryCallback<String> callback) {
        if (email == null || email.trim().isEmpty()) {
            callback.onResult("Por favor escribe tu correo electrónico primero.");
            return;
        }

        firebaseAuth.sendPasswordResetEmail(email.trim())
                .addOnSuccessListener(aVoid -> callback.onResult(null))
                .addOnFailureListener(e -> {
                    if (e instanceof FirebaseAuthInvalidUserException) {
                        callback.onResult("El correo " + email + " no está registrado en Firebase Auth.");
                    } else {
                        callback.onResult(e.getLocalizedMessage() != null ? e.getLocalizedMessage() : "Error al enviar correo de recuperación.");
                    }
                });
    }

    private String mapFirebaseError(Exception e) {
        if (e instanceof FirebaseAuthInvalidUserException) {
            return "No existe una cuenta registrada con este correo electrónico.";
        } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
            return "Contraseña incorrecta. Verifica tus datos.";
        } else if (e instanceof IOException || (e != null && e.getMessage() != null && e.getMessage().contains("network"))) {
            return "Sin conexión a internet y no hay sesión previa guardada en este dispositivo.";
        }
        return e != null ? e.getLocalizedMessage() : "Error de autenticación. Intenta nuevamente.";
    }
}
