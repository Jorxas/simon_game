package com.example.lobby;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

public class LobbyService {

    private final LobbyRepository lobbyRepository;

    public LobbyService() {
        this.lobbyRepository = new LobbyRepository();
    }

    public void createLobby(JsonObject data, Handler<AsyncResult<JsonObject>> resultHandler) {
        lobbyRepository.createLobby(res -> {
            if (res.succeeded()) {
                resultHandler.handle(Future.succeededFuture(new JsonObject()
                    .put("id", res.result())));
            } else {
                resultHandler.handle(Future.failedFuture(res.cause()));
            }
        });
    }

    public void deleteLobby(int lobbyId, Handler<AsyncResult<Void>> resultHandler) {
        System.out.println("Attempting to delete all lobbies from database");
        lobbyRepository.deleteLobby(lobbyId, res -> {
            if (res.succeeded()) {
                System.out.println("Successfully deleted all lobbies from database");
                resultHandler.handle(Future.succeededFuture());
            } else {
                System.err.println("Failed to delete lobbies: " + res.cause().getMessage());
                resultHandler.handle(Future.failedFuture(res.cause()));
            }
        });
    }

    public void getAllLobbies(Handler<AsyncResult<io.vertx.core.json.JsonArray>> resultHandler) {
        lobbyRepository.getAllLobbies(res -> {
            if (res.succeeded()) {
                resultHandler.handle(Future.succeededFuture(res.result()));
            } else {
                resultHandler.handle(Future.failedFuture("Failed to get lobbies: " + res.cause().getMessage()));
            }
        });
    }
}
