package com.example.game;

import com.example.database.DatabaseClient;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.Set;

public class GameService {
    private static final Logger logger = LoggerFactory.getLogger(GameService.class);
    
    private final Vertx vertx;
    private final ConcurrentHashMap<Integer, GameModel> activeGames = new ConcurrentHashMap<>();
    private final AtomicInteger gameIdCounter = new AtomicInteger(1);
    
    // Système d'élimination robuste
    private final Map<Integer, Set<String>> eliminatedPlayers = new ConcurrentHashMap<>();
    
    public GameService(Vertx vertx) {
        this.vertx = vertx;
    }
    
    // Démarrer un jeu pour un lobby
    public void startGame(int lobbyId, Handler<AsyncResult<JsonObject>> resultHandler) {
        logger.info("🎮 Starting game for lobby: {}", lobbyId);
        
        // Vérifier si un jeu est déjà en cours pour ce lobby
        if (activeGames.containsKey(lobbyId)) {
            resultHandler.handle(Future.failedFuture("Game already in progress for lobby " + lobbyId));
            return;
        }
        
        // Récupérer les joueurs du lobby
        getPlayersInLobby(lobbyId, playersResult -> {
            if (playersResult.failed()) {
                logger.error("❌ Failed to get players for lobby {}: {}", lobbyId, playersResult.cause().getMessage());
                resultHandler.handle(Future.failedFuture("Failed to get players: " + playersResult.cause().getMessage()));
                return;
            }
            
            List<GameModel.PlayerInfo> players = playersResult.result();
            logger.info("👥 Found {} players in lobby {}: {}", players.size(), lobbyId, players);
            
            // Vérifier qu'il y a au moins 1 joueur
            if (players.size() < 1) {
                logger.error("❌ No players found in lobby {}", lobbyId);
                resultHandler.handle(Future.failedFuture("Need at least 1 player to start a game"));
                return;
            }
            
            // Vérifier que tous les joueurs sont prêts
            GameModel gameModel = new GameModel(lobbyId);
            if (!gameModel.areAllPlayersReady(players)) {
                logger.error("❌ Not all players are ready in lobby {}", lobbyId);
                resultHandler.handle(Future.failedFuture("Not all players are ready"));
                return;
            }
            
            // Créer le jeu
            int gameId = gameIdCounter.getAndIncrement();
            gameModel.setGameId(gameId);
            gameModel.startGame();
            
            // Sauvegarder le jeu
            activeGames.put(lobbyId, gameModel);
            
            logger.info("📤 About to send sequence to players in lobby {}", lobbyId);
            
            // Envoyer la séquence aux joueurs via MQTT
            sendSequenceToPlayers(lobbyId, gameModel);
            
            // Programmer le timer pour la fin du round
            scheduleRoundTimer(lobbyId, gameModel);
            
            logger.info("🎮 Game {} started for lobby {} with {} players", gameId, lobbyId, players.size());
            
            JsonObject result = new JsonObject()
                .put("success", true)
                .put("gameId", gameId)
                .put("lobbyId", lobbyId)
                .put("players", players.size())
                .put("sequence", gameModel.getCurrentSequence())
                .put("round", gameModel.getCurrentRound());
            
            resultHandler.handle(Future.succeededFuture(result));
        });
    }
    
    // Envoyer la séquence aux joueurs via MQTT
    private void sendSequenceToPlayers(int lobbyId, GameModel gameModel) {
        logger.info("📤 Sending sequence to lobby {}", lobbyId);
        
        // Récupérer les joueurs pour envoyer les séquences à leurs controllers
        getPlayersInLobby(lobbyId, res -> {
            if (res.succeeded()) {
                List<GameModel.PlayerInfo> players = res.result();
                logger.info("📤 Sending sequences to {} players", players.size());
                
                for (GameModel.PlayerInfo player : players) {
                    JsonObject sequenceMessage = new JsonObject()
                        .put("action", "game_sequence")
                        .put("gameId", gameModel.getGameId())
                        .put("lobbyId", lobbyId)
                        .put("playerId", player.getPlayerId())
                        .put("round", gameModel.getCurrentRound())
                        .put("sequence", gameModel.getCurrentSequence())
                        .put("duration", gameModel.getRoundDuration())
                        .put("timestamp", System.currentTimeMillis());
                    
                    // Publier sur le topic MQTT spécifique au controller
                    String topic = "group-05/controller/" + player.getControllerId() + "/game";
                    
                    logger.info("📡 Publishing to MQTT topic: {} for player {} (controllerId: {})", topic, player.getUsername(), player.getControllerId());
                    logger.info("📡 Sequence message: {}", sequenceMessage.encode());
                    
                    vertx.eventBus().publish("mqtt.publish", new JsonObject()
                        .put("topic", topic)
                        .put("message", sequenceMessage.encode()));
                }
                
                logger.info("✅ Sequences sent to {} controllers", players.size());
            } else {
                logger.error("❌ Failed to get players for sending sequences: {}", res.cause().getMessage());
            }
        });
    }
    
    // Programmer le timer pour la fin du round
    private void scheduleRoundTimer(int lobbyId, GameModel gameModel) {
        long delay = gameModel.getRoundDuration() * 1000L; // Convertir en millisecondes
        
        vertx.setTimer(delay, timerId -> {
            GameModel currentGame = activeGames.get(lobbyId);
            if (currentGame != null && currentGame.getGameId() == gameModel.getGameId()) {
                handleRoundEnd(lobbyId, currentGame);
            }
        });
        
        logger.info("⏰ Scheduled round timer for lobby {} in {} seconds", lobbyId, gameModel.getRoundDuration());
    }
    
    // Gérer la fin d'un round
    private void handleRoundEnd(int lobbyId, GameModel gameModel) {
        logger.info("🏁 Round {} ended for lobby {}", gameModel.getCurrentRound(), lobbyId);
        
        // Envoyer un message de fin de round
        JsonObject roundEndMessage = new JsonObject()
            .put("action", "round_end")
            .put("gameId", gameModel.getGameId())
            .put("lobbyId", lobbyId)
            .put("round", gameModel.getCurrentRound())
            .put("timestamp", System.currentTimeMillis());
        
        String topic = "group-05/lobby/" + lobbyId + "/game";
        vertx.eventBus().publish("mqtt.publish", new JsonObject()
            .put("topic", topic)
            .put("message", roundEndMessage.encode()));
        
        // Vérifier si le jeu est terminé
        if (gameModel.isGameFinished()) {
            handleGameEnd(lobbyId, gameModel);
        } else {
            // Démarrer le prochain round
            startNextRound(lobbyId, gameModel);
        }
    }
    
    // Démarrer le prochain round
    private void startNextRound(int lobbyId, GameModel gameModel) {
        gameModel.startNewRound();
        
        logger.info("🔄 Starting round {} for lobby {} with sequence length: {}", 
            gameModel.getCurrentRound(), lobbyId, gameModel.getCurrentSequence().size());
        
        // Envoyer la nouvelle séquence
        sendSequenceToPlayers(lobbyId, gameModel);
        
        // Programmer le timer pour ce round
        scheduleRoundTimer(lobbyId, gameModel);
        
        logger.info("✅ Round {} started successfully for lobby {}", gameModel.getCurrentRound(), lobbyId);
    }
    
    // Gérer la fin du jeu
    private void handleGameEnd(int lobbyId, GameModel gameModel) {
        logger.info("🏆 Game {} ended for lobby {} after {} rounds", 
            gameModel.getGameId(), lobbyId, gameModel.getCurrentRound());
        
        // Envoyer un message de fin de jeu
        JsonObject gameEndMessage = new JsonObject()
            .put("action", "game_end")
            .put("gameId", gameModel.getGameId())
            .put("lobbyId", lobbyId)
            .put("finalRound", gameModel.getCurrentRound())
            .put("timestamp", System.currentTimeMillis());
        
        String topic = "group-05/lobby/" + lobbyId + "/game";
        vertx.eventBus().publish("mqtt.publish", new JsonObject()
            .put("topic", topic)
            .put("message", gameEndMessage.encode()));
        
        // Supprimer le jeu de la liste active
        activeGames.remove(lobbyId);
        
        logger.info("✅ Game {} cleanup completed for lobby {}", gameModel.getGameId(), lobbyId);
    }
    
    // Récupérer les joueurs d'un lobby
    private void getPlayersInLobby(int lobbyId, Handler<AsyncResult<List<GameModel.PlayerInfo>>> resultHandler) {
        logger.info("🔍 Fetching players for lobby {}", lobbyId);
        
        // Récupérer les vrais joueurs du lobby depuis la base de données
        vertx.executeBlocking(promise -> {
            try {
                // Appel à la base de données pour récupérer tous les joueurs du lobby
                String sql = "SELECT lp.player_id, p.username, lp.status, lp.controller_id " +
                           "FROM lobbies_players lp " +
                           "JOIN players p ON lp.player_id = p.id " +
                           "WHERE lp.lobby_id = ?";
                
                logger.info("🔍 Executing SQL: {} with lobbyId: {}", sql, lobbyId);
                
                DatabaseClient.getInstance().preparedQuery(sql)
                    .execute(Tuple.of(lobbyId))
                    .onSuccess(rows -> {
                        logger.info("📊 Found {} total rows in database for lobby {}", rows.size(), lobbyId);
                        
                        List<GameModel.PlayerInfo> players = new ArrayList<>();
                        for (Row row : rows) {
                            int playerId = row.getInteger("player_id");
                            String username = row.getString("username");
                            String status = row.getString("status");
                            Integer controllerId = row.getInteger("controller_id");
                            
                            logger.info("👤 Player: id={}, username={}, status={}, controllerId={}", 
                                playerId, username, status, controllerId);
                            
                            // Ne récupérer que les joueurs prêts avec un controller
                            if (controllerId != null && "ready".equals(status)) {
                                players.add(new GameModel.PlayerInfo(playerId, username, status, controllerId));
                                logger.info("✅ Added player {} to ready players list", username);
                            } else {
                                logger.info("❌ Skipped player {} (controllerId={}, status={})", username, controllerId, status);
                            }
                        }
                        
                        logger.info("🎯 Final ready players count: {}", players.size());
                        promise.complete(players);
                    })
                    .onFailure(err -> {
                        logger.error("❌ Error fetching players for lobby {}: {}", lobbyId, err.getMessage());
                        promise.fail(err);
                    });
            } catch (Exception e) {
                logger.error("❌ Error in getPlayersInLobby: {}", e.getMessage());
                promise.fail(e);
            }
        }, resultHandler);
    }
    
    // Arrêter un jeu
    public void stopGame(int lobbyId, Handler<AsyncResult<Void>> resultHandler) {
        GameModel game = activeGames.remove(lobbyId);
        if (game != null) {
            logger.info("🛑 Game {} stopped for lobby {}", game.getGameId(), lobbyId);
            
            // Envoyer un message d'arrêt
            JsonObject stopMessage = new JsonObject()
                .put("action", "game_stopped")
                .put("gameId", game.getGameId())
                .put("lobbyId", lobbyId)
                .put("timestamp", System.currentTimeMillis());
            
            String topic = "group-05/lobby/" + lobbyId + "/game";
            vertx.eventBus().publish("mqtt.publish", new JsonObject()
                .put("topic", topic)
                .put("message", stopMessage.encode()));
            
            resultHandler.handle(Future.succeededFuture());
        } else {
            resultHandler.handle(Future.failedFuture("No active game found for lobby " + lobbyId));
        }
    }
    
    // Obtenir le statut d'un jeu
    public void getGameStatus(int lobbyId, Handler<AsyncResult<JsonObject>> resultHandler) {
        GameModel game = activeGames.get(lobbyId);
        if (game != null) {
            JsonObject status = new JsonObject()
                .put("gameId", game.getGameId())
                .put("lobbyId", game.getLobbyId())
                .put("state", game.getState().name())
                .put("currentRound", game.getCurrentRound())
                .put("maxRounds", game.getMaxRounds())
                .put("roundDuration", game.getRoundDuration());
            
            resultHandler.handle(Future.succeededFuture(status));
        } else {
            resultHandler.handle(Future.failedFuture("No active game found for lobby " + lobbyId));
        }
    }
    
    // Éliminer un joueur immédiatement
    public void eliminatePlayer(int gameId, String controllerId, String reason) {
        logger.info("💀 Élimination directe du joueur {} pour raison: {}", controllerId, reason);
        
        // Étape A: Mémoire locale
        eliminatedPlayers.computeIfAbsent(gameId, k -> ConcurrentHashMap.newKeySet()).add(controllerId);
        
        // Étape B: Base de données (si nécessaire)
        // TODO: Ajouter la mise à jour en base de données
        
        // Étape C: Notification MQTT
        publishPlayerEliminated(gameId, controllerId, reason);
    }
    
    // Publier l'élimination via MQTT
    private void publishPlayerEliminated(int gameId, String controllerId, String reason) {
        JsonObject eliminationMessage = new JsonObject()
            .put("action", "player_eliminated")
            .put("gameId", gameId)
            .put("eliminatedPlayer", controllerId)
            .put("reason", reason)
            .put("message", "💀 Joueur éliminé : " + reason)
            .put("timestamp", System.currentTimeMillis());
        
        // Publier sur le topic du lobby
        String lobbyTopic = "group-05/lobby/" + gameId + "/game";
        vertx.eventBus().publish("mqtt.publish", new JsonObject()
            .put("topic", lobbyTopic)
            .put("message", eliminationMessage.encode()));
        
        // Publier sur le topic spécifique du contrôleur
        String controllerTopic = "group-05/controller/" + controllerId + "/game";
        vertx.eventBus().publish("mqtt.publish", new JsonObject()
            .put("topic", controllerTopic)
            .put("message", eliminationMessage.encode()));
        
        logger.info("📡 Élimination publiée sur topics: {} et {}", lobbyTopic, controllerTopic);
    }
    
    // Vérifier si un joueur est éliminé
    public boolean isPlayerEliminated(int gameId, String controllerId) {
        Set<String> eliminated = eliminatedPlayers.get(gameId);
        return eliminated != null && eliminated.contains(controllerId);
    }
    
    // Récupérer un jeu par son ID
    public GameModel getGame(int gameId) {
        return activeGames.get(gameId);
    }
}
