package com.example.controller;

import com.example.http.HttpController;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ControllerController implements HttpController {

    private static final Logger logger = LoggerFactory.getLogger(ControllerController.class);
    private final ControllerService controllerService;

    public ControllerController(Vertx vertx) {
        this.controllerService = new ControllerService(vertx);
    }

    @Override
    public void registerRoutes(Router router) {
        router.post("/api/controller/create").handler(this::handleCreateController);
        router.get("/api/controller/list").handler(this::handleGetAllControllers);
        router.put("/api/controller/:id/status").handler(this::handleUpdateControllerStatus);
        router.delete("/api/controller/:id").handler(this::handleDeleteController);
        router.post("/api/controller/:id/delete").handler(this::handleDeleteController);
    }

    private void handleCreateController(RoutingContext ctx) {
        controllerService.createController(res -> {
            if (res.succeeded()) {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(201)
                    .end(new JsonObject()
                        .put("success", true)
                        .put("message", "Controller created successfully")
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

    private void handleGetAllControllers(RoutingContext ctx) {
        controllerService.getAllControllers(res -> {
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

    private void handleUpdateControllerStatus(RoutingContext ctx) {
        int controllerId = Integer.parseInt(ctx.pathParam("id"));
        JsonObject body = ctx.body().asJsonObject();
        String status = body.getString("status");
        
        if (status == null || (!status.equals("online") && !status.equals("busy"))) {
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .setStatusCode(400)
                .end(new JsonObject()
                    .put("success", false)
                    .put("error", "Invalid status. Must be 'online' or 'busy'")
                    .encode());
            return;
        }

        controllerService.updateControllerStatus(controllerId, status, res -> {
            if (res.succeeded()) {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(200)
                    .end(new JsonObject()
                        .put("success", true)
                        .put("message", "Controller status updated successfully")
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

    private void handleDeleteController(RoutingContext ctx) {
        int controllerId = Integer.parseInt(ctx.pathParam("id"));

        controllerService.deleteController(controllerId, res -> {
            if (res.succeeded()) {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(200)
                    .end(new JsonObject()
                        .put("success", true)
                        .put("message", "Controller deleted successfully")
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
