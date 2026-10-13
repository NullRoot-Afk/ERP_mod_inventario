package com.example.erprukainventario.auth.data;

import android.os.Handler;
import android.os.Looper;

import com.example.erprukainventario.auth.domain.AuthUser;
import com.example.erprukainventario.auth.domain.Company;
import com.example.erprukainventario.auth.domain.LoginResult;
import com.example.erprukainventario.auth.domain.UserRole;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AuthRepositoryImpl implements AuthRepository {

    // Ajusta estos nombres a los reales de tu Firestore (sensibles a mayúsculas).
    private static final String COMPANIES = "COMPANIES";
    private static final String USERS = "USERS";

    private final AuthLocalDataSource localDataSource;
    private final TenantConfigLocalDataSource tenantConfig;
    private final SessionManager sessionManager;
    private final FirebaseAuth firebaseAuth;
    private final FirebaseFirestore firestore;

    // Room no permite consultas en el hilo principal.
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Inject
    public AuthRepositoryImpl(
            AuthLocalDataSource localDataSource,
            TenantConfigLocalDataSource tenantConfig,
            SessionManager sessionManager,
            FirebaseAuth firebaseAuth,
            FirebaseFirestore firestore
    ) {
        this.localDataSource = localDataSource;
        this.tenantConfig = tenantConfig;
        this.sessionManager = sessionManager;
        this.firebaseAuth = firebaseAuth;
        this.firestore = firestore;
    }

    // ------------------------------------------------------------------
    // Configuración de empresa
    // ------------------------------------------------------------------

    @Override
    public void validateAndSaveCompany(String companyCode, RepositoryCallback<CompanyValidationResult> callback) {
        if (companyCode == null || companyCode.trim().isEmpty()) {
            callback.onResult(CompanyValidationResult.error("Debes ingresar un código de empresa."));
            return;
        }

        final String target = companyCode.trim();
        CollectionReference companies = firestore.collection(COMPANIES);

        // 1. Por ID de documento; 2. por campo "code". Sin descargar la colección completa.
        companies.document(target).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        saveCompanyAndReturn(doc, callback);
                        return;
                    }
                    companies.whereEqualTo("code", target).limit(1).get()
                            .addOnSuccessListener(snap -> {
                                if (!snap.isEmpty()) {
                                    saveCompanyAndReturn(snap.getDocuments().get(0), callback);
                                } else {
                                    callback.onResult(CompanyValidationResult.error(
                                            "No se encontró la empresa '" + target + "'."));
                                }
                            })
                            .addOnFailureListener(e -> callback.onResult(
                                    CompanyValidationResult.error(firestoreErrorMessage(e))));
                })
                .addOnFailureListener(e -> callback.onResult(
                        CompanyValidationResult.error(firestoreErrorMessage(e))));
    }

    private void saveCompanyAndReturn(DocumentSnapshot doc, RepositoryCallback<CompanyValidationResult> callback) {
        String companyId = doc.getId();
        String docName = doc.getString("name");
        String name = (docName != null && !docName.isEmpty()) ? docName : companyId;
        Company company = new Company(companyId, name);
        tenantConfig.saveCompany(company);
        callback.onResult(CompanyValidationResult.success(company));
    }

    // ------------------------------------------------------------------
    // Login
    // ------------------------------------------------------------------

    @Override
    public void login(String email, String password, RepositoryCallback<LoginResult> callback) {
        Company company = tenantConfig.getConfiguredCompany();
        if (company == null) {
            callback.onResult(LoginResult.error("El dispositivo no tiene una empresa configurada."));
            return;
        }
        if (email == null || email.trim().isEmpty() || password == null || password.isEmpty()) {
            callback.onResult(LoginResult.error("Ingresa tu correo y contraseña."));
            return;
        }

        final String cleanEmail = email.trim();

        firebaseAuth.signInWithEmailAndPassword(cleanEmail, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser == null) {
                        callback.onResult(LoginResult.error("Error al obtener usuario autenticado."));
                        return;
                    }
                    findUserDoc(firebaseUser.getUid(), cleanEmail, userDoc ->
                            onUserDocLoaded(userDoc, firebaseUser, cleanEmail, password, company, callback));
                })
                .addOnFailureListener(e -> {
                    // Solo se permite login offline si el fallo fue de red.
                    // Si fue contraseña incorrecta, no se debe aceptar una contraseña vieja del caché.
                    if (e instanceof FirebaseNetworkException) {
                        attemptOfflineLogin(cleanEmail, password, callback);
                    } else {
                        callback.onResult(LoginResult.error(mapAuthError(e)));
                    }
                });
    }

    /** Busca el documento del usuario por UID y, si no existe, por email. */
    private void findUserDoc(String uid, String email, RepositoryCallback<DocumentSnapshot> callback) {
        CollectionReference users = firestore.collection(USERS);
        users.document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        callback.onResult(doc);
                        return;
                    }
                    users.whereEqualTo("email", email).limit(1).get()
                            .addOnSuccessListener(snap ->
                                    callback.onResult(snap.isEmpty() ? null : snap.getDocuments().get(0)))
                            .addOnFailureListener(e -> callback.onResult(null));
                })
                .addOnFailureListener(e -> callback.onResult(null));
    }

    private void onUserDocLoaded(
            DocumentSnapshot doc,
            FirebaseUser firebaseUser,
            String email,
            String password,
            Company configuredCompany,
            RepositoryCallback<LoginResult> callback
    ) {
        if (doc == null) {
            firebaseAuth.signOut();
            callback.onResult(LoginResult.error(
                    "El usuario (" + email + ") no existe en la colección '" + USERS + "' de Firestore."));
            return;
        }

        String userCompanyId = doc.getString("companyId");
        if (userCompanyId == null
                || !userCompanyId.trim().equalsIgnoreCase(configuredCompany.getId().trim())) {
            firebaseAuth.signOut();
            callback.onResult(LoginResult.error(
                    "Acceso denegado: tu usuario pertenece a la empresa '"
                            + (userCompanyId != null ? userCompanyId : "sin empresa")
                            + "', pero este dispositivo está configurado con '"
                            + configuredCompany.getId() + "'."));
            return;
        }

        AuthUser authUser = new AuthUser(
                firebaseUser.getUid(),
                resolveFullName(doc, firebaseUser, email),
                resolveRole(doc),
                doc.getString("warehouseId"),
                doc.getString("warehouseName")
        );

        dbExecutor.execute(() -> {
            localDataSource.cacheCredentials(email, password, authUser);
            sessionManager.setCurrentUser(authUser);
            mainHandler.post(() -> callback.onResult(LoginResult.success(authUser, false)));
        });
    }

    private String resolveFullName(DocumentSnapshot doc, FirebaseUser firebaseUser, String email) {
        String fullName = doc.getString("fullName");
        if (fullName != null && !fullName.isEmpty()) return fullName;
        String name = doc.getString("name");
        if (name != null && !name.isEmpty()) return name;
        String display = firebaseUser.getDisplayName();
        return display != null ? display : email;
    }

    private UserRole resolveRole(DocumentSnapshot doc) {
        String roleStr = doc.getString("role");
        if (roleStr == null) return UserRole.OPERARIO;
        try {
            return UserRole.valueOf(roleStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return UserRole.OPERARIO;
        }
    }

    private void attemptOfflineLogin(String email, String password, RepositoryCallback<LoginResult> callback) {
        dbExecutor.execute(() -> {
            AuthUser cachedUser = localDataSource.validateOffline(email, password);
            mainHandler.post(() -> {
                if (cachedUser != null) {
                    sessionManager.setCurrentUser(cachedUser);
                    callback.onResult(LoginResult.success(cachedUser, true));
                } else {
                    callback.onResult(LoginResult.error(
                            "Sin conexión a internet y no hay sesión previa guardada en este dispositivo."));
                }
            });
        });
    }

    // ------------------------------------------------------------------
    // Recuperación de contraseña
    // ------------------------------------------------------------------

    @Override
    public void sendPasswordResetEmail(String email, RepositoryCallback<String> callback) {
        if (email == null || email.trim().isEmpty()) {
            callback.onResult("Por favor escribe tu correo electrónico primero.");
            return;
        }

        String cleanEmail = email.trim();
        firebaseAuth.sendPasswordResetEmail(cleanEmail)
                .addOnSuccessListener(v -> callback.onResult(null))
                .addOnFailureListener(e -> {
                    if (e instanceof FirebaseAuthInvalidUserException) {
                        callback.onResult("El correo " + cleanEmail + " no está registrado.");
                    } else {
                        callback.onResult(e.getLocalizedMessage() != null
                                ? e.getLocalizedMessage()
                                : "Error al enviar correo de recuperación.");
                    }
                });
    }

    // ------------------------------------------------------------------
    // Mensajes de error
    // ------------------------------------------------------------------

    private String mapAuthError(Exception e) {
        if (e instanceof FirebaseAuthInvalidUserException
                || e instanceof FirebaseAuthInvalidCredentialsException) {
            // Firebase moderno no distingue "correo inexistente" de "contraseña incorrecta".
            return "Correo o contraseña incorrectos.";
        }
        return e.getLocalizedMessage() != null
                ? e.getLocalizedMessage()
                : "Error de autenticación. Intenta nuevamente.";
    }

    private String firestoreErrorMessage(Exception e) {
        if (e instanceof FirebaseFirestoreException
                && ((FirebaseFirestoreException) e).getCode()
                == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            return "Permiso denegado en Firestore. Revisa las reglas de seguridad de la colección '"
                    + COMPANIES + "'.";
        }
        return "No se pudo validar la empresa. Verifica tu conexión e intenta de nuevo.";
    }
}
