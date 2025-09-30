package com.example.lobby;

import com.example.http.HttpController;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LobbyController implements HttpController {

    private static final Logger logger = LoggerFactory.getLogger(LobbyController.class);
    private final LobbyService lobbyService;

    public LobbyController(Vertx vertx) {
        this.lobbyService = new LobbyService();
    }

    @Override
    public void registerRoutes(Router router) {
        router.post("/api/lobby/create").handler(this::handleCreateLobby);
        router.get("/api/lobby/list").handler(this::handleGetAllLobbies);
        router.delete("/api/lobby/:id").handler(this::handleDeleteLobby);
        router.post("/api/lobby/:id/delete").handler(this::handleDeleteLobby);
    }

    private void handleCreateLobby(RoutingContext ctx) {
        lobbyService.createLobby(new JsonObject(), res -> {
            if (res.succeeded()) {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(201)
                    .end(new JsonObject()
                        .put("success", true)
                        .put("message", "Lobby created successfully with default values")
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

    private void handleDeleteLobby(RoutingContext ctx) {
        int lobbyId = Integer.parseInt(ctx.pathParam("id"));

        lobbyService.deleteLobby(lobbyId, res -> {
            if (res.succeeded()) {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(200)
                    .end(new JsonObject()
                        .put("success", true)
                        .put("message", "Lobby deleted successfully")
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

    private void handleGetAllLobbies(RoutingContext ctx) {
        lobbyService.getAllLobbies(res -> {
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
}
