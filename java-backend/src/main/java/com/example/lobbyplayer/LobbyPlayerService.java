package com.example.lobbyplayer;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class LobbyPlayerService {

    private final LobbyPlayerRepository lobbyPlayerRepository;
    private final com.example.controller.ControllerService controllerService;
    private final Vertx vertx;

    public LobbyPlayerService(Vertx vertx) {
        this.lobbyPlayerRepository = new LobbyPlayerRepository();
        this.controllerService = new com.example.controller.ControllerService(vertx);
        this.vertx = vertx;
    }

    public void joinLobby(int lobbyId, String username, String password, int controllerId, String role, Handler<AsyncResult<JsonObject>> resultHandler) {
        // Vérifier si le joueur existe
        lobbyPlayerRepository.checkPlayerExists(username, password, playerCheck -> {
            if (playerCheck.failed()) {
                resultHandler.handle(Future.failedFuture("Database error: " + playerCheck.cause().getMessage()));
                return;
            }

            if (playerCheck.result() == null) {
                resultHandler.handle(Future.failedFuture("Invalid username or password"));
                return;
            }

            int playerId = playerCheck.result().getInteger("id");

            // Vérifier si le contrôleur est disponible
            lobbyPlayerRepository.checkControllerAvailable(controllerId, controllerCheck -> {
                if (controllerCheck.failed()) {
                    resultHandler.handle(Future.failedFuture("Database error: " + controllerCheck.cause().getMessage()));
                    return;
                }

                if (controllerCheck.result() == null) {
                    resultHandler.handle(Future.failedFuture("Controller is not available (offline or busy)"));
                    return;
                }

                // Ajouter le joueur au lobby
                lobbyPlayerRepository.addPlayerToLobby(lobbyId, playerId, controllerId, role, addResult -> {
                    if (addResult.failed()) {
                        resultHandler.handle(Future.failedFuture("Failed to add player to lobby: " + addResult.cause().getMessage()));
                        return;
                    }

                    // Mettre à jour le statut du contrôleur de "online" à "busy"
                    controllerService.updateControllerStatus(controllerId, "busy", statusUpdateResult -> {
                        if (statusUpdateResult.failed()) {
                            // Log l'erreur mais ne pas échouer l'opération complète
                            System.err.println("Failed to update controller status: " + statusUpdateResult.cause().getMessage());
                        }

                        // Publier un message MQTT pour notifier le controller qu'il a été sélectionné
                        JsonObject selectionMessage = new JsonObject()
                            .put("controllerId", controllerId)
                            .put("playerId", playerId)
                            .put("lobbyId", lobbyId)
                            .put("action", "controller_selected");
                        
                        vertx.eventBus().publish("controller.selected", selectionMessage);
                        System.out.println("✅ Controller " + controllerId + " selected by player " + playerId + " in lobby " + lobbyId);

                        // Retourner les informations du joueur ajouté
                        JsonObject playerInfo = new JsonObject()
                            .put("id", playerId)
                            .put("username", username)
                            .put("role", role)
                            .put("controllerId", controllerId)
                            .put("lobbyId", lobbyId);

                        resultHandler.handle(Future.succeededFuture(playerInfo));
                        
                        // Programmer le timeout automatique pour ce joueur (10 secondes)
                        schedulePlayerTimeout(lobbyId, playerId, controllerId);
                    });
                });
            });
        });
    }
    
    // Map pour stocker les timers de timeout par joueur
    private final Map<String, Long> playerTimeouts = new ConcurrentHashMap<>();
    
    // Programmer le timeout automatique pour un joueur
    private void schedulePlayerTimeout(int lobbyId, int playerId, int controllerId) {
        String playerKey = lobbyId + "-" + playerId;
        
        // Annuler le timer précédent s'il existe
        Long existingTimer = playerTimeouts.get(playerKey);
        if (existingTimer != null) {
            vertx.cancelTimer(existingTimer);
        }
        
        System.out.println("⏰ Scheduling timeout for player " + playerId + " in lobby " + lobbyId + " (30 seconds)");
        
        long timerId = vertx.setTimer(30000, timer -> { // 30 secondes
            System.out.println("⏰ Timeout timer triggered for player " + playerId);
            
            // Vérifier si le joueur est toujours en attente
            lobbyPlayerRepository.getPlayerStatus(lobbyId, playerId, statusResult -> {
                if (statusResult.succeeded() && statusResult.result() != null) {
                    JsonObject playerData = statusResult.result();
                    String status = playerData.getString("status");
                    System.out.println("⏰ Player " + playerId + " current status: " + status);
                    
                    if ("waiting".equals(status)) {
                        // Le joueur n'est toujours pas prêt, le supprimer automatiquement
                        System.out.println("⏰ Timeout: Player " + playerId + " not ready after 30 seconds, removing from lobby");
                        
                        removePlayerFromLobby(lobbyId, playerId, removeResult -> {
                            if (removeResult.succeeded()) {
                                System.out.println("✅ Player " + playerId + " automatically removed due to timeout");
                                
                                // Publier un message MQTT pour notifier le frontend
                                JsonObject timeoutMessage = new JsonObject()
                                    .put("action", "player_timeout")
                                    .put("lobbyId", lobbyId)
                                    .put("playerId", playerId)
                                    .put("reason", "Not ready within 30 seconds");
                                
                                vertx.eventBus().publish("player.timeout", timeoutMessage);
                            } else {
                                System.err.println("❌ Failed to remove timed out player: " + removeResult.cause().getMessage());
                            }
                        });
                    } else {
                        System.out.println("✅ Player " + playerId + " is ready, no timeout needed");
                    }
                } else {
                    System.err.println("❌ Failed to get player status for timeout check: " + statusResult.cause().getMessage());
                }
            });
            
            // Supprimer le timer de la map
            playerTimeouts.remove(playerKey);
        });
        
        // Stocker le nouveau timer
        playerTimeouts.put(playerKey, timerId);
    }
    
    // Annuler le timeout d'un joueur (quand il devient ready)
    private void cancelPlayerTimeout(int lobbyId, int playerId) {
        String playerKey = lobbyId + "-" + playerId;
        Long timerId = playerTimeouts.get(playerKey);
        
        if (timerId != null) {
            vertx.cancelTimer(timerId);
            playerTimeouts.remove(playerKey);
            System.out.println("✅ Cancelled timeout for player " + playerId + " in lobby " + lobbyId);
        }
    }

    public void getPlayersInLobby(int lobbyId, Handler<AsyncResult<JsonArray>> resultHandler) {
        lobbyPlayerRepository.getPlayersInLobby(lobbyId, res -> {
            if (res.succeeded()) {
                JsonArray players = new JsonArray();
                res.result().forEach(row -> {
                    JsonObject player = new JsonObject()
                        .put("id", row.getInteger("player_id"))
                        .put("username", row.getString("username"))
                        .put("role", row.getString("role"))
                        .put("status", row.getString("status"))
                        .put("controllerId", row.getInteger("controller_id") != null ? row.getInteger("controller_id") : null)
                        .put("score", row.getInteger("score") != null ? row.getInteger("score") : 0)
                        .put("round", row.getInteger("round") != null ? row.getInteger("round") : 0)
                        .put("joinedAt", row.getLocalDateTime("joined_at").toString());
                    players.add(player);
                });
                resultHandler.handle(Future.succeededFuture(players));
            } else {
                resultHandler.handle(Future.failedFuture(res.cause()));
            }
        });
    }



    public void removePlayerFromLobby(int lobbyId, int playerId, Handler<AsyncResult<Void>> resultHandler) {
        // D'abord, récupérer les informations du joueur pour libérer le contrôleur
        lobbyPlayerRepository.getPlayerInfo(lobbyId, playerId, playerInfoResult -> {
            if (playerInfoResult.failed()) {
                resultHandler.handle(Future.failedFuture("Failed to get player info: " + playerInfoResult.cause().getMessage()));
                return;
            }

            JsonObject playerInfo = playerInfoResult.result();
            if (playerInfo == null) {
                resultHandler.handle(Future.failedFuture("Player not found in lobby"));
                return;
            }

            Integer controllerId = playerInfo.getInteger("controller_id");

            // Annuler le timeout du joueur s'il en avait un
            cancelPlayerTimeout(lobbyId, playerId);
            
            // Supprimer le joueur du lobby
            lobbyPlayerRepository.removePlayerFromLobby(lobbyId, playerId, removeResult -> {
                if (removeResult.failed()) {
                    resultHandler.handle(Future.failedFuture("Failed to remove player from lobby: " + removeResult.cause().getMessage()));
                    return;
                }

                // Si le joueur avait un contrôleur, le libérer
                if (controllerId != null) {
                    controllerService.updateControllerStatus(controllerId, "online", statusUpdateResult -> {
                        if (statusUpdateResult.failed()) {
                            // Log l'erreur mais ne pas échouer l'opération complète
                            System.err.println("Failed to update controller status: " + statusUpdateResult.cause().getMessage());
                        }

                        resultHandler.handle(Future.succeededFuture());
                    });
                } else {
                    resultHandler.handle(Future.succeededFuture());
                }
            });
        });
    }

    public void updatePlayerStatus(int lobbyId, int playerId, String status, Handler<AsyncResult<Void>> resultHandler) {
        lobbyPlayerRepository.updatePlayerStatus(lobbyId, playerId, status, res -> {
            if (res.succeeded()) {
                // Gérer le timeout selon le nouveau statut
                if ("ready".equals(status)) {
                    // Le joueur est prêt, annuler le timeout
                    cancelPlayerTimeout(lobbyId, playerId);
                    System.out.println("✅ Player " + playerId + " is ready, timeout cancelled");
                } else if ("waiting".equals(status)) {
                    // Le joueur redevient en attente, reprogrammer le timeout
                    schedulePlayerTimeout(lobbyId, playerId, 0); // controllerId n'est pas important pour le timeout
                    System.out.println("⏰ Player " + playerId + " is waiting again, timeout rescheduled");
                }
                
                // Publier un message MQTT pour notifier le frontend du changement de statut
                JsonObject statusUpdate = new JsonObject()
                    .put("action", "player_status_updated")
                    .put("lobbyId", lobbyId)
                    .put("playerId", playerId)
                    .put("status", status);
                
                vertx.eventBus().publish("player.status.updated", statusUpdate);
                
                resultHandler.handle(Future.succeededFuture());
            } else {
                resultHandler.handle(Future.failedFuture("Failed to update player status: " + res.cause().getMessage()));
            }
        });
    }

    public void getControllerInfo(int controllerId, Handler<AsyncResult<JsonObject>> resultHandler) {
        lobbyPlayerRepository.getControllerInfo(controllerId, res -> {
            if (res.succeeded()) {
                resultHandler.handle(Future.succeededFuture(res.result()));
            } else {
                resultHandler.handle(Future.failedFuture(res.cause()));
            }
        });
    }

    public void getPlayerStatus(int lobbyId, int playerId, Handler<AsyncResult<io.vertx.core.json.JsonObject>> resultHandler) {
        lobbyPlayerRepository.getPlayerStatus(lobbyId, playerId, res -> {
            if (res.succeeded()) {
                resultHandler.handle(Future.succeededFuture(res.result()));
            } else {
                resultHandler.handle(Future.failedFuture(res.cause()));
            }
        });
    }

    public void updatePlayerScore(int lobbyId, int playerId, int score, int round, Handler<AsyncResult<Void>> resultHandler) {
        lobbyPlayerRepository.updatePlayerScore(lobbyId, playerId, score, round, res -> {
            if (res.succeeded()) {
                // Publier un message MQTT pour notifier le frontend de la mise à jour du score
                JsonObject scoreUpdate = new JsonObject()
                    .put("action", "player_score_updated")
                    .put("lobbyId", lobbyId)
                    .put("playerId", playerId)
                    .put("score", score)
                    .put("currentRound", round);
                
                vertx.eventBus().publish("player.score.updated", scoreUpdate);
                
                resultHandler.handle(Future.succeededFuture());
            } else {
                resultHandler.handle(Future.failedFuture("Failed to update player score: " + res.cause().getMessage()));
            }
        });
    }
}
