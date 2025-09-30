package com.example.lobbyplayer;

import com.example.http.HttpController;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LobbyPlayerController implements HttpController {

    private static final Logger logger = LoggerFactory.getLogger(LobbyPlayerController.class);
    private final LobbyPlayerService lobbyPlayerService;

    public LobbyPlayerController(Vertx vertx) {
        this.lobbyPlayerService = new LobbyPlayerService(vertx);
    }

    @Override
    public void registerRoutes(Router router) {
        router.post("/api/lobby/:id/join").handler(this::handleJoinLobby);
        router.get("/api/lobby/:id/players").handler(this::handleGetLobbyPlayers);
        router.delete("/api/lobby/:id/players/:playerId").handler(this::handleRemovePlayerFromLobby);
        router.put("/api/lobby/:id/players/:playerId/status").handler(this::handleUpdatePlayerStatus);
        router.get("/api/lobby/:id/players/:playerId/status").handler(this::handleGetPlayerStatus);
        router.put("/api/lobby/:id/players/:playerId/score").handler(this::handleUpdatePlayerScore);
        router.get("/api/controller/:id/info").handler(this::handleGetControllerInfo);
    }

    private void handleJoinLobby(RoutingContext ctx) {
        int lobbyId = Integer.parseInt(ctx.pathParam("id"));
        JsonObject body = ctx.body().asJsonObject();
        
        String username = body.getString("username");
        String password = body.getString("password");
        Integer controllerId = body.getInteger("controllerId");
        String role = body.getString("role", "player");

        if (username == null || password == null || controllerId == null) {
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .setStatusCode(400)
                .end(new JsonObject()
                    .put("success", false)
                    .put("error", "Missing required fields: username, password, controllerId")
                    .encode());
            return;
        }

        lobbyPlayerService.joinLobby(lobbyId, username, password, controllerId, role, res -> {
            if (res.succeeded()) {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(200)
                    .end(new JsonObject()
                        .put("success", true)
                        .put("message", "Player joined lobby successfully")
                        .put("data", res.result())
                        .encode());
            } else {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(400)
                    .end(new JsonObject()
                        .put("success", false)
                        .put("error", res.cause().getMessage())
                        .encode());
            }
        });
    }

    private void handleGetLobbyPlayers(RoutingContext ctx) {
        int lobbyId = Integer.parseInt(ctx.pathParam("id"));

        lobbyPlayerService.getPlayersInLobby(lobbyId, res -> {
            if (res.succeeded()) {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(200)
                    .end(new JsonObject()
                        .put("success", true)
                        .put("data", res.result())
                        .encode());
            } else {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(400)
                    .end(new JsonObject()
                        .put("success", false)
                        .put("error", res.cause().getMessage())
                        .encode());
            }
        });
    }

    private void handleRemovePlayerFromLobby(RoutingContext ctx) {
        int lobbyId = Integer.parseInt(ctx.pathParam("id"));
        int playerId = Integer.parseInt(ctx.pathParam("playerId"));

        lobbyPlayerService.removePlayerFromLobby(lobbyId, playerId, res -> {
            if (res.succeeded()) {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(200)
                    .end(new JsonObject()
                        .put("success", true)
                        .put("message", "Player removed from lobby successfully")
                        .encode());
            } else {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(400)
                    .end(new JsonObject()
                        .put("success", false)
                        .put("error", res.cause().getMessage())
                        .encode());
            }
        });
    }

    private void handleUpdatePlayerStatus(RoutingContext ctx) {
        int lobbyId = Integer.parseInt(ctx.pathParam("id"));
        int playerId = Integer.parseInt(ctx.pathParam("playerId"));
        JsonObject body = ctx.body().asJsonObject();
        String status = body.getString("status");
        
        if (status == null || (!status.equals("waiting") && !status.equals("ready") && !status.equals("playing"))) {
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .setStatusCode(400)
                .end(new JsonObject()
                    .put("success", false)
                    .put("error", "Invalid status. Must be 'waiting', 'ready', or 'playing'")
                    .encode());
            return;
        }

        lobbyPlayerService.updatePlayerStatus(lobbyId, playerId, status, res -> {
            if (res.succeeded()) {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(200)
                    .end(new JsonObject()
                        .put("success", true)
                        .put("message", "Player status updated successfully")
                        .encode());
            } else {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(400)
                    .end(new JsonObject()
                        .put("success", false)
                        .put("error", res.cause().getMessage())
                        .encode());
            }
        });
    }

    private void handleGetControllerInfo(RoutingContext ctx) {
        int controllerId = Integer.parseInt(ctx.pathParam("id"));

        lobbyPlayerService.getControllerInfo(controllerId, res -> {
            if (res.succeeded()) {
                JsonObject controllerInfo = res.result();
                if (controllerInfo != null) {
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .setStatusCode(200)
                        .end(new JsonObject()
                            .put("success", true)
                            .put("data", controllerInfo)
                            .encode());
                } else {
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .setStatusCode(404)
                        .end(new JsonObject()
                            .put("success", false)
                            .put("error", "Controller not found or not associated with any player")
                            .encode());
                }
            } else {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(400)
                    .end(new JsonObject()
                        .put("success", false)
                        .put("error", res.cause().getMessage())
                        .encode());
            }
        });
    }

    private void handleGetPlayerStatus(RoutingContext ctx) {
        int lobbyId = Integer.parseInt(ctx.pathParam("id"));
        int playerId = Integer.parseInt(ctx.pathParam("playerId"));

        lobbyPlayerService.getPlayerStatus(lobbyId, playerId, res -> {
            if (res.succeeded()) {
                JsonObject playerData = res.result();
                if (playerData != null) {
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .setStatusCode(200)
                        .end(new JsonObject()
                            .put("success", true)
                            .put("data", playerData)
                            .encode());
                } else {
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .setStatusCode(404)
                        .end(new JsonObject()
                            .put("success", false)
                            .put("error", "Player not found in lobby")
                            .encode());
                }
            } else {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(400)
                    .end(new JsonObject()
                        .put("success", false)
                        .put("error", res.cause().getMessage())
                        .encode());
            }
        });
    }

    private void handleUpdatePlayerScore(RoutingContext ctx) {
        int lobbyId = Integer.parseInt(ctx.pathParam("id"));
        int playerId = Integer.parseInt(ctx.pathParam("playerId"));
        JsonObject body = ctx.body().asJsonObject();
        
        Integer score = body.getInteger("score");
        Integer round = body.getInteger("round");
        
        if (score == null || round == null) {
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .setStatusCode(400)
                .end(new JsonObject()
                    .put("success", false)
                    .put("error", "Missing required fields: score and round")
                    .encode());
            return;
        }

        lobbyPlayerService.updatePlayerScore(lobbyId, playerId, score, round, res -> {
            if (res.succeeded()) {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(200)
                    .end(new JsonObject()
                        .put("success", true)
                        .put("message", "Player score updated successfully")
                        .encode());
            } else {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(400)
                    .end(new JsonObject()
                        .put("success", false)
                        .put("error", res.cause().getMessage())
                        .encode());
            }
        });
    }
}
