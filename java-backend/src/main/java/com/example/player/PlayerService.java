package com.example.player;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

public class PlayerService {

    private final PlayerRepository repo;

    public PlayerService() {
        this.repo = new PlayerRepository();
    }

    public void register(JsonObject data, Handler<AsyncResult<Void>> resultHandler) {
        String username = data.getString("username");
        String password = data.getString("password");

        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            resultHandler.handle(Future.failedFuture("Username and password are required"));
            return;
        }

        repo.register(data).onComplete(resultHandler);
    }

    public void login(JsonObject data, Handler<AsyncResult<JsonObject>> resultHandler) {
        String username = data.getString("username");
        String password = data.getString("password");

        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            resultHandler.handle(Future.failedFuture("Username and password are required"));
            return;
        }

        repo.login(data).onComplete(resultHandler);
    }
}