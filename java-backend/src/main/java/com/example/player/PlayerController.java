package com.example.player;


import com.example.http.HttpController;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerController implements HttpController {

    private static final Logger logger = LoggerFactory.getLogger(PlayerController.class);
    private final PlayerService playerService;
    private final EventBus eventBus;

    public PlayerController(Vertx vertx) {
        this.eventBus = vertx.eventBus();
        this.playerService = new PlayerService();
    }

    @Override
    public void registerRoutes(Router router) {
        // Routes publiques
        router.post("/api/register").handler(this::handleRegister);
        router.post("/api/login").handler(this::handleLogin);
    }

    private void handleRegister(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) {
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .setStatusCode(400)
                .end(new JsonObject().put("error", "Invalid JSON").encode());
            return;
        }

        // Validate required fields
        String username = body.getString("username");
        String password = body.getString("password");
        
        if (username == null || username.trim().isEmpty()) {
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .setStatusCode(400)
                .end(new JsonObject().put("error", "Username is required").encode());
            return;
        }
        
        if (password == null || password.trim().isEmpty()) {
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .setStatusCode(400)
                .end(new JsonObject().put("error", "Password is required").encode());
            return;
        }
        
        // Validate password length
        if (password.length() < 6) {
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .setStatusCode(400)
                .end(new JsonObject().put("error", "Password must be at least 6 characters long").encode());
            return;
        }

        logger.info("Registration attempt for user: {}", username);
        
        playerService.register(body, res -> {
            if (res.succeeded()) {
                logger.info("Registration successful for user: {}", username);
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(201)
                    .end(new JsonObject()
                        .put("success", true)
                        .put("message", "Player registered successfully")
                        .put("username", username)
                        .encode());
            } else {
                logger.error("Registration failed for user {}: {}", username, res.cause().getMessage());
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(400)
                    .end(new JsonObject()
                        .put("success", false)
                        .put("error", "Registration failed: " + res.cause().getMessage())
                        .encode());
            }
        });
    }

    private void handleLogin(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) {
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .setStatusCode(400)
                .end(new JsonObject().put("error", "Invalid JSON").encode());
            return;
        }

        playerService.login(body, res -> {
            if (res.succeeded()) {
                JsonObject userData = res.result();
                if (userData != null) {
                    JsonObject response = new JsonObject()
                        .put("user", userData);
                    
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .setStatusCode(200)
                        .end(response.encode());
                } else {
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .setStatusCode(401)
                        .end(new JsonObject().put("error", "Invalid credentials").encode());
                }
            } else {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(401)
                    .end(new JsonObject().put("error", "Login failed: " + res.cause().getMessage()).encode());
            }
        });
    }
}
