package com.example.controller;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ControllerService {

    private final ControllerRepository controllerRepository;
    private final EventBus eventBus;

    public ControllerService(Vertx vertx) {
        this.controllerRepository = new ControllerRepository();
        this.eventBus = vertx.eventBus();
    }

    public void createController(Handler<AsyncResult<JsonObject>> resultHandler) {
        controllerRepository.createController(res -> {
            if (res.succeeded()) {
                JsonObject controller = new JsonObject()
                    .put("id", res.result())
                    .put("status", "online");
                
                // Publier l'événement MQTT
                eventBus.publish("controller.created", controller);
                
                resultHandler.handle(Future.succeededFuture(controller));
            } else {
                resultHandler.handle(Future.failedFuture(res.cause()));
            }
        });
    }

    public void updateControllerStatus(int controllerId, String status, Handler<AsyncResult<Void>> resultHandler) {
        controllerRepository.updateControllerStatus(controllerId, status, resultHandler);
    }



    public void getAllControllers(Handler<AsyncResult<JsonArray>> resultHandler) {
        controllerRepository.getAllControllers(res -> {
            if (res.succeeded()) {
                JsonArray controllers = new JsonArray();
                res.result().forEach(row -> {
                    JsonObject controller = new JsonObject()
                        .put("id", row.getInteger("id"))
                        .put("status", row.getString("status"));
                    controllers.add(controller);
                });
                resultHandler.handle(Future.succeededFuture(controllers));
            } else {
                resultHandler.handle(Future.failedFuture(res.cause()));
            }
        });
    }

    public void deleteController(int controllerId, Handler<AsyncResult<Void>> resultHandler) {
        System.out.println("Attempting to delete controller with ID: " + controllerId);
        controllerRepository.deleteController(controllerId, res -> {
            if (res.succeeded()) {
                System.out.println("Successfully deleted controller with ID: " + controllerId);
                
                // Publier l'événement MQTT
                eventBus.publish("controller.deleted", controllerId);
                
                resultHandler.handle(Future.succeededFuture());
            } else {
                System.err.println("Failed to delete controller with ID " + controllerId + ": " + res.cause().getMessage());
                resultHandler.handle(Future.failedFuture(res.cause()));
            }
        });
    }
}
