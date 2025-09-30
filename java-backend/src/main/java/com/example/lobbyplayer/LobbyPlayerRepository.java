package com.example.lobbyplayer;

import com.example.database.DatabaseClient;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.Tuple;

public class LobbyPlayerRepository {

    private final JDBCPool jdbcPool;

    public LobbyPlayerRepository() {
        this.jdbcPool = DatabaseClient.getInstance();
    }

    public void addPlayerToLobby(int lobbyId, int playerId, int controllerId, String role, Handler<AsyncResult<Void>> resultHandler) {
        String query = "INSERT INTO lobbies_players (lobby_id, player_id, controller_id, role) VALUES (?, ?, ?, ?)";
        Tuple params = Tuple.of(lobbyId, playerId, controllerId, role);
        
        jdbcPool
            .preparedQuery(query)
            .execute(params)
            .map(rows -> (Void) null)
            .onComplete(resultHandler);
    }

    public void getPlayersInLobby(int lobbyId, Handler<AsyncResult<io.vertx.sqlclient.RowSet<io.vertx.sqlclient.Row>>> resultHandler) {
        String query = "SELECT lp.*, p.username, c.status as controller_status FROM lobbies_players lp " +
                      "JOIN players p ON lp.player_id = p.id " +
                      "LEFT JOIN controllers c ON lp.controller_id = c.id " +
                      "WHERE lp.lobby_id = ? ORDER BY lp.joined_at ASC";
        Tuple params = Tuple.of(lobbyId);
        
        jdbcPool
            .preparedQuery(query)
            .execute(params)
            .onComplete(resultHandler);
    }

    public void checkPlayerExists(String username, String password, Handler<AsyncResult<io.vertx.sqlclient.Row>> resultHandler) {
        String query = "SELECT * FROM players WHERE username = ? AND password = ?";
        Tuple params = Tuple.of(username, password);
        
        jdbcPool
            .preparedQuery(query)
            .execute(params)
            .map(rows -> rows.iterator().hasNext() ? rows.iterator().next() : null)
            .onComplete(resultHandler);
    }

    public void checkControllerAvailable(int controllerId, Handler<AsyncResult<io.vertx.sqlclient.Row>> resultHandler) {
        String query = "SELECT * FROM controllers WHERE id = ? AND status = 'online'";
        Tuple params = Tuple.of(controllerId);
        
        jdbcPool
            .preparedQuery(query)
            .execute(params)
            .map(rows -> rows.iterator().hasNext() ? rows.iterator().next() : null)
            .onComplete(resultHandler);
    }

    public void removePlayerFromLobby(int lobbyId, int playerId, Handler<AsyncResult<Void>> resultHandler) {
        // D'abord, récupérer le controller_id associé à ce joueur
        String getControllerQuery = "SELECT controller_id FROM lobbies_players WHERE lobby_id = ? AND player_id = ?";
        Tuple getControllerParams = Tuple.of(lobbyId, playerId);
        
        jdbcPool
            .preparedQuery(getControllerQuery)
            .execute(getControllerParams)
            .compose(controllerRows -> {
                // Supprimer le joueur du lobby
                String deletePlayerQuery = "DELETE FROM lobbies_players WHERE lobby_id = ? AND player_id = ?";
                Tuple deletePlayerParams = Tuple.of(lobbyId, playerId);
                
                return jdbcPool
                    .preparedQuery(deletePlayerQuery)
                    .execute(deletePlayerParams)
                    .compose(deleteRows -> {
                        // Si le joueur avait un controller, le supprimer
                        if (controllerRows.iterator().hasNext()) {
                            Integer controllerId = controllerRows.iterator().next().getInteger("controller_id");
                            if (controllerId != null) {
                                String deleteControllerQuery = "DELETE FROM controllers WHERE id = ?";
                                Tuple deleteControllerParams = Tuple.of(controllerId);
                                
                                return jdbcPool
                                    .preparedQuery(deleteControllerQuery)
                                    .execute(deleteControllerParams)
                                    .map(controllerDeleteRows -> {
                                        System.out.println("Deleted controller " + controllerId + " when removing player " + playerId + " from lobby " + lobbyId);
                                        return (Void) null;
                                    });
                            }
                        }
                        return io.vertx.core.Future.succeededFuture((Void) null);
                    });
            })
            .onComplete(resultHandler);
    }

    public void getPlayerControllerId(int lobbyId, int playerId, Handler<AsyncResult<Integer>> resultHandler) {
        String query = "SELECT controller_id FROM lobbies_players WHERE lobby_id = ? AND player_id = ?";
        Tuple params = Tuple.of(lobbyId, playerId);
        
        jdbcPool
            .preparedQuery(query)
            .execute(params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return rows.iterator().next().getInteger("controller_id");
                }
                return null;
            })
            .onComplete(resultHandler);
    }

    public void getPlayerInfo(int lobbyId, int playerId, Handler<AsyncResult<io.vertx.core.json.JsonObject>> resultHandler) {
        String query = "SELECT lp.*, p.username FROM lobbies_players lp " +
                      "JOIN players p ON lp.player_id = p.id " +
                      "WHERE lp.lobby_id = ? AND lp.player_id = ?";
        Tuple params = Tuple.of(lobbyId, playerId);
        
        jdbcPool
            .preparedQuery(query)
            .execute(params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    io.vertx.sqlclient.Row row = rows.iterator().next();
                    return new io.vertx.core.json.JsonObject()
                        .put("player_id", row.getInteger("player_id"))
                        .put("controller_id", row.getInteger("controller_id") != null ? row.getInteger("controller_id") : null)
                        .put("role", row.getString("role"))
                        .put("status", row.getString("status"))
                        .put("username", row.getString("username"));
                }
                return null;
            })
            .onComplete(resultHandler);
    }

    public void updatePlayerStatus(int lobbyId, int playerId, String status, Handler<AsyncResult<Void>> resultHandler) {
        String query = "UPDATE lobbies_players SET status = ? WHERE lobby_id = ? AND player_id = ?";
        Tuple params = Tuple.of(status, lobbyId, playerId);
        
        jdbcPool
            .preparedQuery(query)
            .execute(params)
            .map(rows -> (Void) null)
            .onComplete(resultHandler);
    }
    
    public void getPlayerStatus(int lobbyId, int playerId, Handler<AsyncResult<io.vertx.core.json.JsonObject>> resultHandler) {
        String query = "SELECT status, score, round FROM lobbies_players WHERE lobby_id = ? AND player_id = ?";
        Tuple params = Tuple.of(lobbyId, playerId);
        
        jdbcPool
            .preparedQuery(query)
            .execute(params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    io.vertx.sqlclient.Row row = rows.iterator().next();
                    return new io.vertx.core.json.JsonObject()
                        .put("status", row.getString("status"))
                        .put("score", row.getInteger("score") != null ? row.getInteger("score") : 0)
                        .put("currentRound", row.getInteger("round") != null ? row.getInteger("round") : 1);
                }
                return null;
            })
            .onComplete(resultHandler);
    }

    public void getControllerInfo(int controllerId, Handler<AsyncResult<io.vertx.core.json.JsonObject>> resultHandler) {
        String query = "SELECT lp.lobby_id, lp.player_id, lp.status, p.username FROM lobbies_players lp " +
                      "JOIN players p ON lp.player_id = p.id " +
                      "WHERE lp.controller_id = ?";
        Tuple params = Tuple.of(controllerId);
        
        jdbcPool
            .preparedQuery(query)
            .execute(params)
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    io.vertx.sqlclient.Row row = rows.iterator().next();
                    return new io.vertx.core.json.JsonObject()
                        .put("lobbyId", row.getInteger("lobby_id"))
                        .put("playerId", row.getInteger("player_id"))
                        .put("status", row.getString("status"))
                        .put("username", row.getString("username"));
                }
                return null;
            })
            .onComplete(resultHandler);
    }

    public void updatePlayerScore(int lobbyId, int playerId, int score, int round, Handler<AsyncResult<Void>> resultHandler) {
        String query = "UPDATE lobbies_players SET score = ?, round = ? WHERE lobby_id = ? AND player_id = ?";
        Tuple params = Tuple.of(score, round, lobbyId, playerId);
        
        jdbcPool
            .preparedQuery(query)
            .execute(params)
            .map(rows -> (Void) null)
            .onComplete(resultHandler);
    }
}
