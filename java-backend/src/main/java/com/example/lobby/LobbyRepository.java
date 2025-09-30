package com.example.lobby;

import com.example.database.DatabaseClient;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.Tuple;

public class LobbyRepository {

    private final JDBCPool jdbcPool;

    public LobbyRepository() {
        this.jdbcPool = DatabaseClient.getInstance();
    }

    public void createLobby(Handler<AsyncResult<Integer>> resultHandler) {
        String query = "INSERT INTO lobbies () VALUES ()";
        
        jdbcPool
            .preparedQuery(query)
            .execute()
            .compose(rows -> {
                // Récupérer l'ID du lobby qui vient d'être créé
                return jdbcPool
                    .preparedQuery("SELECT LAST_INSERT_ID() as id")
                    .execute()
                    .map(resultRows -> resultRows.iterator().next().getInteger("id"));
            })
            .onComplete(resultHandler);
    }

    public void deleteLobby(int lobbyId, Handler<AsyncResult<Void>> resultHandler) {
        // D'abord, récupérer tous les controllers associés à ce lobby
        String getControllersQuery = "SELECT DISTINCT controller_id FROM lobbies_players WHERE lobby_id = ? AND controller_id IS NOT NULL";
        
        jdbcPool
            .preparedQuery(getControllersQuery)
            .execute(Tuple.of(lobbyId))
            .compose(controllerRows -> {
                // Supprimer tous les controllers associés
                if (controllerRows.size() > 0) {
                    String deleteControllersQuery = "DELETE FROM controllers WHERE id IN (";
                    StringBuilder placeholders = new StringBuilder();
                    for (int i = 0; i < controllerRows.size(); i++) {
                        if (i > 0) placeholders.append(",");
                        placeholders.append("?");
                    }
                    deleteControllersQuery += placeholders.toString() + ")";
                    
                    // Créer les paramètres pour la suppression des controllers
                    Object[] controllerIds = new Object[controllerRows.size()];
                    int i = 0;
                    for (io.vertx.sqlclient.Row row : controllerRows) {
                        controllerIds[i++] = row.getInteger("controller_id");
                    }
                    Tuple controllerParams = Tuple.from(controllerIds);
                    
                    return jdbcPool
                        .preparedQuery(deleteControllersQuery)
                        .execute(controllerParams)
                        .map(rows -> {
                            System.out.println("Deleted " + rows.rowCount() + " controllers from lobby " + lobbyId);
                            return controllerRows;
                        });
                } else {
                    return Future.succeededFuture(controllerRows);
                }
            })
            .compose(controllerRows -> {
                // Supprimer toutes les entrées de lobbies_players pour ce lobby
                String deletePlayersQuery = "DELETE FROM lobbies_players WHERE lobby_id = ?";
                return jdbcPool
                    .preparedQuery(deletePlayersQuery)
                    .execute(Tuple.of(lobbyId))
                    .map(rows -> {
                        System.out.println("Deleted " + rows.rowCount() + " player entries from lobby " + lobbyId);
                        return (Void) null;
                    });
            })
            .compose(v -> {
                // Enfin, supprimer le lobby lui-même
                String deleteLobbyQuery = "DELETE FROM lobbies WHERE id = ?";
                return jdbcPool
                    .preparedQuery(deleteLobbyQuery)
                    .execute(Tuple.of(lobbyId))
                    .map(rows -> {
                        System.out.println("Deleted lobby " + lobbyId + " from database");
                        return (Void) null;
                    });
            })
            .onComplete(resultHandler);
    }

    public void getAllLobbies(Handler<AsyncResult<io.vertx.core.json.JsonArray>> resultHandler) {
        String query = "SELECT * FROM lobbies ORDER BY id ASC";
        
        jdbcPool
            .preparedQuery(query)
            .execute()
            .map(rows -> {
                io.vertx.core.json.JsonArray lobbies = new io.vertx.core.json.JsonArray();
                rows.forEach(row -> {
                    io.vertx.core.json.JsonObject lobby = new io.vertx.core.json.JsonObject()
                        .put("id", row.getInteger("id"))
                        .put("status", row.getString("status"))
                        .put("current_players", row.getInteger("current_players"))
                        .put("created_at", row.getLocalDateTime("created_at").toString());
                    lobbies.add(lobby);
                });
                return lobbies;
            })
            .onComplete(resultHandler);
    }
}
