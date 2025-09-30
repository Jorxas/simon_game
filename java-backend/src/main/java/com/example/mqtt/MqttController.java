package com.example.mqtt;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.mqtt.MqttClient;
import com.example.controller.ControllerService;

public class MqttController {

    private static final Logger logger = LoggerFactory.getLogger(MqttController.class);
    private final MqttService mqttService;
    private final MqttClient mqttClient;
    private final EventBus eventBus;
    private final ControllerService controllerService;

    public MqttController(MqttClient mqttClient, Vertx vertx) {
        this.mqttClient = mqttClient;
        this.mqttService = new MqttService(mqttClient);
        this.eventBus = vertx.eventBus();
        this.controllerService = new ControllerService(vertx);
    }

    /* EVENTS empfangen und verarbeiten */
    public void registerEventBusConsumers() {
        this.eventBus.consumer("game.start", msg -> {
            logger.info("Message received via EventBus: 'game.start'");
            mqttService.publishGameStart();
        });

        this.eventBus.consumer("game.stop", msg -> {
            logger.info("Message received via EventBus: 'game.stop'");
            mqttService.publishGameStop();
        });

        this.eventBus.consumer("object.created", msg -> {
            logger.info("Message received via EventBus: 'object.created'");
            mqttService.publishObjectCreated(msg.body().toString());
        });
        this.eventBus.consumer("mqtt.demo.message", msg -> {
            logger.info("Message received via EventBus: 'mqtt.message'");
            mqttService.publishDemoMessage(msg.body().toString());
        });

        this.eventBus.consumer("room.created", msg -> {
            logger.info("Message received via EventBus: 'room.created'");
            mqttService.publishRoomCreated((JsonObject) msg.body());
        });

        this.eventBus.consumer("room.deleted", msg -> {
            logger.info("Message received via EventBus: 'room.deleted'");
            mqttService.publishRoomDeleted((Integer) msg.body());
        });

        this.eventBus.consumer("chat.message", msg -> {
            logger.info("Message received via EventBus: 'chat.message'");
            JsonObject chatData = (JsonObject) msg.body();
            mqttService.publishChatMessage(chatData.getString("username"), chatData.getString("message"));
        });

        this.eventBus.consumer("controller.created", msg -> {
            logger.info("Message received via EventBus: 'controller.created'");
            JsonObject controllerData = (JsonObject) msg.body();
            mqttService.publishControllerCreated(controllerData);
        });

        this.eventBus.consumer("controller.deleted", msg -> {
            logger.info("Message received via EventBus: 'controller.deleted'");
            Integer controllerId = (Integer) msg.body();
            mqttService.publishControllerDeleted(controllerId);
        });

        this.eventBus.consumer("player.status.updated", msg -> {
            logger.info("Message received via EventBus: 'player.status.updated'");
            JsonObject statusData = (JsonObject) msg.body();
            mqttService.publishPlayerStatusUpdated(statusData);
        });

        this.eventBus.consumer("player.timeout", msg -> {
            logger.info("Message received via EventBus: 'player.timeout'");
            JsonObject timeoutData = (JsonObject) msg.body();
            mqttService.publishPlayerTimeout(timeoutData);
        });

        this.eventBus.consumer("controller.selected", msg -> {
            logger.info("Message received via EventBus: 'controller.selected'");
            JsonObject selectionData = (JsonObject) msg.body();
            mqttService.publishControllerSelected(selectionData);
        });

        this.eventBus.consumer("mqtt.publish", msg -> {
            logger.info("Message received via EventBus: 'mqtt.publish'");
            JsonObject publishData = (JsonObject) msg.body();
            String topic = publishData.getString("topic");
            String message = publishData.getString("message");
            mqttService.publishToTopic(topic, message);
        });
    }

    public void registerMqttConsumers() {

        mqttClient.publishHandler(message -> {

            Buffer payload = message.payload();
            String topic = message.topicName();

            logger.info("📨 MQTT message received! Topic: {}, Payload: {}", topic, payload);

            switch (topic) {
                case "demo/hello_world" ->
                    this.eventBus.publish("mqtt.demo.message", payload);
                case "group-05/controllers/create" ->
                    handleControllerCreate(payload);
                case "group-05/controllers/delete" ->
                    handleControllerDelete(payload);
                default ->
                    logger.info("Handler for topic: {} not implemented", topic);
            }

        });

        logger.info("🔔 Subscribing to MQTT topics...");
        mqttClient.subscribe(Map.of(
                "demo/hello_world", 0,
                "test/output", 0,
                "group-05/controllers/create", 0,
                "group-05/controllers/delete", 0
        ));
        logger.info("✅ Successfully subscribed to MQTT topics");

    }

    private void handleControllerCreate(Buffer payload) {
        try {
            JsonObject data = new JsonObject(payload.toString());
            logger.info("Creating controller via MQTT: {}", data);
            
            controllerService.createController(res -> {
                if (res.succeeded()) {
                    JsonObject controller = res.result();
                    logger.info("Controller created successfully via MQTT: {}", controller);
                    
                    // Publier la confirmation de création
                    mqttService.publishControllerCreated(controller);
                } else {
                    logger.error("Failed to create controller via MQTT: {}", res.cause().getMessage());
                }
            });
        } catch (Exception e) {
            logger.error("Error handling controller create MQTT message: {}", e.getMessage());
        }
    }

    private void handleControllerDelete(Buffer payload) {
        try {
            JsonObject data = new JsonObject(payload.toString());
            int controllerId = data.getInteger("controllerId");
            logger.info("Deleting controller via MQTT: {}", controllerId);
            
            controllerService.deleteController(controllerId, res -> {
                if (res.succeeded()) {
                    logger.info("Controller deleted successfully via MQTT: {}", controllerId);
                    
                    // Publier la confirmation de suppression
                    mqttService.publishControllerDeleted(controllerId);
                } else {
                    logger.error("Failed to delete controller via MQTT: {}", res.cause().getMessage());
                }
            });
        } catch (Exception e) {
            logger.error("Error handling controller delete MQTT message: {}", e.getMessage());
        }
    }
}
