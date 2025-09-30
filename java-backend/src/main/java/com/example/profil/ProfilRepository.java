package com.example.profil;

import com.example.database.DatabaseClient;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

public class ProfilRepository {

    private final JDBCPool jdbcPool;

    public ProfilRepository() {
        this.jdbcPool = DatabaseClient.getInstance();
    }

    public Future<JsonObject> updateProfile(JsonObject data) {
        String currentUsername = data.getString("currentUsername");
        String currentPassword = data.getString("currentPassword");
        String newUsername = data.getString("newUsername");
        String newPassword = data.getString("newPassword");
        String newProfileUrl = data.getString("newProfileUrl");

        // D'abord vérifier que l'utilisateur existe avec le mot de passe actuel
        String checkQuery = "SELECT id FROM players WHERE username = ? AND password = ?";
        Tuple checkParams = Tuple.of(currentUsername, currentPassword);

        return jdbcPool
                .preparedQuery(checkQuery)
                .execute(checkParams)
                .compose(rows -> {
                    if (rows == null || !rows.iterator().hasNext()) {
                        return Future.failedFuture("Invalid current username or password");
                    }

                    Row row = rows.iterator().next();
                    Integer userId = row.getInteger("id");

                    // Construire la requête de mise à jour selon les champs fournis
                    String updateQuery;
                    Tuple updateParams;

                    if (newPassword != null && !newPassword.trim().isEmpty()) {
                        // Mettre à jour password et profileUrl (username reste inchangé)
                        updateQuery = "UPDATE players SET password = ?, profileUrl = ? WHERE id = ?";
                        updateParams = Tuple.of(newPassword, newProfileUrl, userId);
                    } else {
                        // Mettre à jour profileUrl seulement (username et password restent inchangés)
                        updateQuery = "UPDATE players SET profileUrl = ? WHERE id = ?";
                        updateParams = Tuple.of(newProfileUrl, userId);
                    }

                    return jdbcPool
                            .preparedQuery(updateQuery)
                            .execute(updateParams)
                            .map(updateRows -> {
                                return new JsonObject()
                                        .put("id", userId)
                                        .put("username", currentUsername) // Garder le username actuel
                                        .put("profileUrl", newProfileUrl)
                                        .put("message", "Profile updated successfully");
                            });
                });
    }

    public Future<JsonObject> getUserProfile(JsonObject data) {
        String username = data.getString("username");

        String query = "SELECT id, username, profileUrl FROM players WHERE username = ?";
        Tuple params = Tuple.of(username);

        return jdbcPool
                .preparedQuery(query)
                .execute(params)
                .map(rows -> {
                    if (rows == null || !rows.iterator().hasNext()) {
                        return null;
                    } else {
                        Row row = rows.iterator().next();
                        return new JsonObject()
                                .put("id", row.getInteger("id"))
                                .put("username", row.getString("username"))
                                .put("profileUrl", row.getString("profileUrl"));
                    }
                });
    }
}
