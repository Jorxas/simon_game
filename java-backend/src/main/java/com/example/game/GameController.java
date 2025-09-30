package com.example.game;

import com.example.http.HttpController;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GameController implements HttpController {

    private static final Logger logger = LoggerFactory.getLogger(GameController.class);
    private final GameService gameService;

    public GameController(Vertx vertx) {
        this.gameService = new GameService(vertx);
    }

    @Override
    public void registerRoutes(Router router) {
        router.post("/api/lobby/:id/game/start").handler(this::handleStartGame);
        router.post("/api/lobby/:id/game/stop").handler(this::handleStopGame);
        router.get("/api/lobby/:id/game/status").handler(this::handleGetGameStatus);
        router.post("/game/sequence-response").handler(this::handleSequenceResponse);
        router.get("/api/highscores").handler(this::handleGetHighscores);
        router.post("/api/players/score").handler(this::handleSaveScore);
    }

    private void handleStartGame(RoutingContext ctx) {
        int lobbyId = Integer.parseInt(ctx.pathParam("id"));
        
        logger.info("🎮 Starting game for lobby: {}", lobbyId);
        
        gameService.startGame(lobbyId, res -> {
            if (res.succeeded()) {
                JsonObject result = res.result();
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(200)
                    .end(result.encode());
                
                logger.info("✅ Game started successfully for lobby {}: {}", lobbyId, result.encode());
            } else {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(400)
                    .end(new JsonObject()
                        .put("success", false)
                        .put("error", res.cause().getMessage())
                        .encode());
                
                logger.error("❌ Failed to start game for lobby {}: {}", lobbyId, res.cause().getMessage());
            }
        });
    }

    private void handleStopGame(RoutingContext ctx) {
        int lobbyId = Integer.parseInt(ctx.pathParam("id"));
        
        logger.info("🛑 Stopping game for lobby: {}", lobbyId);
        
        gameService.stopGame(lobbyId, res -> {
            if (res.succeeded()) {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(200)
                    .end(new JsonObject()
                        .put("success", true)
                        .put("message", "Game stopped successfully")
                        .encode());
                
                logger.info("✅ Game stopped successfully for lobby {}", lobbyId);
            } else {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(400)
                    .end(new JsonObject()
                        .put("success", false)
                        .put("error", res.cause().getMessage())
                        .encode());
                
                logger.error("❌ Failed to stop game for lobby {}: {}", lobbyId, res.cause().getMessage());
            }
        });
    }

    private void handleGetGameStatus(RoutingContext ctx) {
        int lobbyId = Integer.parseInt(ctx.pathParam("id"));
        
        logger.info("📊 Getting game status for lobby: {}", lobbyId);
        
        gameService.getGameStatus(lobbyId, res -> {
            if (res.succeeded()) {
                JsonObject status = res.result();
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(200)
                    .end(new JsonObject()
                        .put("success", true)
                        .put("data", status)
                        .encode());
                
                logger.info("✅ Game status retrieved for lobby {}: {}", lobbyId, status.encode());
            } else {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(404)
                    .end(new JsonObject()
                        .put("success", false)
                        .put("error", res.cause().getMessage())
                        .encode());
                
                logger.info("ℹ️ No active game found for lobby {}: {}", lobbyId, res.cause().getMessage());
            }
        });
    }

    // Traiter la réponse d'un joueur à une séquence
    public void handleSequenceResponse(RoutingContext context) {
        JsonObject request = context.getBodyAsJson();
        int gameId = request.getInteger("gameId");
        String controllerId = request.getString("controllerId");
        JsonArray playerSequence = request.getJsonArray("sequence");
        long responseTime = request.getLong("responseTime", System.currentTimeMillis());
        
        logger.info("🎮 Réponse reçue du joueur {} pour le jeu {}: {}", controllerId, gameId, playerSequence);
        
        // Vérifier si le joueur n'est pas déjà éliminé
        if (gameService.isPlayerEliminated(gameId, controllerId)) {
            logger.info("💀 Joueur {} déjà éliminé, réponse ignorée", controllerId);
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("success", false).put("message", "Player already eliminated").encode());
            return;
        }
        
        // Récupérer la séquence correcte
        GameModel game = gameService.getGame(gameId);
        if (game == null) {
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject().put("success", false).put("message", "Game not found").encode());
            return;
        }
        
        List<String> correctSequence = game.getCurrentSequence().stream()
            .map(String::valueOf)
            .collect(Collectors.toList());
        List<String> playerSeqList = new ArrayList<>();
        for (int i = 0; i < playerSequence.size(); i++) {
            playerSeqList.add(playerSequence.getString(i));
        }
        
        // Comparer les séquences
        boolean isCorrect = playerSeqList.equals(correctSequence);
        
        if (isCorrect) {
            // ✅ Bonne réponse - calculer le score
            int score = calculateScore(responseTime, correctSequence.size());
            logger.info("✅ Bonne réponse du joueur {} - Score: {}", controllerId, score);
            
            // TODO: Mettre à jour le score du joueur
            
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                    .put("success", true)
                    .put("correct", true)
                    .put("score", score)
                    .encode());
        } else {
            // ❌ Mauvaise séquence - éliminer le joueur
            logger.info("❌ Mauvaise séquence du joueur {} - Élimination", controllerId);
            gameService.eliminatePlayer(gameId, controllerId, "Mauvaise séquence");
            
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                    .put("success", true)
                    .put("correct", false)
                    .put("eliminated", true)
                    .put("reason", "Mauvaise séquence")
                    .encode());
        }
    }
    
    // Calculer le score basé sur le temps de réponse et la longueur de la séquence
    private int calculateScore(long responseTime, int sequenceLength) {
        // Score de base pour la longueur de la séquence
        int baseScore = sequenceLength * 10;
        
        // Bonus pour la rapidité (plus rapide = plus de points)
        long timeBonus = Math.max(0, 5000 - responseTime) / 100; // Bonus max 50 points
        
        return baseScore + (int) timeBonus;
    }

    // Récupérer les 10 meilleurs scores
    public void handleGetHighscores(RoutingContext context) {
        logger.info("🏆 Getting top 10 highscores");
        
        // Simuler des highscores pour le moment
        // TODO: Récupérer depuis la base de données
        List<JsonObject> highscores = new ArrayList<>();
        
        // Ajouter quelques scores d'exemple
        highscores.add(new JsonObject()
            .put("playerName", "Player1")
            .put("score", 1500)
            .put("round", 15)
            .put("timestamp", System.currentTimeMillis() - 86400000)); // 1 jour ago
        
        highscores.add(new JsonObject()
            .put("playerName", "Player2")
            .put("score", 1200)
            .put("round", 12)
            .put("timestamp", System.currentTimeMillis() - 172800000)); // 2 jours ago
        
        highscores.add(new JsonObject()
            .put("playerName", "Player3")
            .put("score", 1000)
            .put("round", 10)
            .put("timestamp", System.currentTimeMillis() - 259200000)); // 3 jours ago
        
        // Trier par score décroissant
        highscores.sort((a, b) -> Integer.compare(b.getInteger("score"), a.getInteger("score")));
        
        // Prendre les 10 premiers
        List<JsonObject> top10 = highscores.stream().limit(10).collect(Collectors.toList());
        
        JsonObject response = new JsonObject()
            .put("success", true)
            .put("data", new JsonArray(top10));
        
        context.response()
            .putHeader("Content-Type", "application/json")
            .end(response.encode());
    }
    
    // Sauvegarder un nouveau score
    public void handleSaveScore(RoutingContext context) {
        JsonObject request = context.getBodyAsJson();
        int playerId = request.getInteger("playerId");
        int lobbyId = request.getInteger("lobbyId");
        int finalScore = request.getInteger("finalScore");
        int finalRound = request.getInteger("finalRound");
        
        logger.info("📊 Saving score for player {}: {} points (round {})", playerId, finalScore, finalRound);
        
        // TODO: Sauvegarder en base de données
        
        JsonObject response = new JsonObject()
            .put("success", true)
            .put("message", "Score saved successfully");
        
        context.response()
            .putHeader("Content-Type", "application/json")
            .end(response.encode());
    }
}
