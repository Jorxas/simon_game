package com.example.profil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

public class ProfilService {

    private final ProfilRepository repo;

    public ProfilService() {
        this.repo = new ProfilRepository();
    }

    public void updateProfile(JsonObject data, Handler<AsyncResult<JsonObject>> resultHandler) {
        String currentUsername = data.getString("currentUsername");
        String currentPassword = data.getString("currentPassword");
        String newUsername = data.getString("newUsername");
        String newPassword = data.getString("newPassword");
        String newProfileUrl = data.getString("newProfileUrl");

        // Validation des données
        if (currentUsername == null || currentUsername.trim().isEmpty()) {
            resultHandler.handle(Future.failedFuture("Current username is required"));
            return;
        }

        if (currentPassword == null || currentPassword.trim().isEmpty()) {
            resultHandler.handle(Future.failedFuture("Current password is required"));
            return;
        }

        // Le username ne peut pas être modifié, on utilise le currentUsername
        if (newUsername == null || newUsername.trim().isEmpty()) {
            newUsername = currentUsername;
        }

        // Validation du nouveau mot de passe (optionnel)
        if (newPassword != null && !newPassword.trim().isEmpty()) {
            if (newPassword.length() < 6) {
                resultHandler.handle(Future.failedFuture("New password must be at least 6 characters"));
                return;
            }
        }

        // Validation du profileUrl (doit être une des 10 photos prédéfinies)
        if (newProfileUrl != null && !newProfileUrl.trim().isEmpty()) {
            if (!isValidProfilePicture(newProfileUrl)) {
                resultHandler.handle(Future.failedFuture("Invalid profile picture URL. Please select from predefined options."));
                return;
            }
        }

        repo.updateProfile(data).onComplete(resultHandler);
    }

    public void getUserProfile(JsonObject data, Handler<AsyncResult<JsonObject>> resultHandler) {
        String username = data.getString("username");

        if (username == null || username.trim().isEmpty()) {
            resultHandler.handle(Future.failedFuture("Username is required"));
            return;
        }

        repo.getUserProfile(data).onComplete(resultHandler);
    }

    // Méthode pour valider si l'URL de la photo de profil est une des 10 prédéfinies
    private boolean isValidProfilePicture(String profileUrl) {
        if (profileUrl == null || profileUrl.trim().isEmpty()) {
            return false;
        }
        
        // Vérifier si l'URL correspond à une des 10 photos prédéfinies
        for (int i = 1; i <= 10; i++) {
            if (profileUrl.equals("/profil/avatar" + i + ".svg")) {
                return true;
            }
        }
        
        return false;
    }
}
