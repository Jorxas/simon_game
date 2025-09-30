package com.example.mqtt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.json.JsonObject;
import io.vertx.mqtt.MqttClient;

public class MqttService {

    private final MqttClient mqttClient;

    private static final Logger logger = LoggerFactory.getLogger(MqttService.class);

    public MqttService(MqttClient mqttClient) {
        this.mqttClient = mqttClient;
    }

    public void publishGameStart() {
        JsonObject data = new JsonObject().put("action", "start");
        mqttClient.publish("test/game/start", data.toBuffer(), MqttQoS.AT_MOST_ONCE, false, false);
        logger.info("📡 MQTT published game start: {}", data);
    }

    public void publishGameStop() {
        JsonObject data = new JsonObject().put("action", "stop");
        mqttClient.publish("test/game/stop", data.toBuffer(), MqttQoS.AT_MOST_ONCE, false, false);
        logger.info("📡 MQTT published game stop: {}", data);
    }

    public void publishObjectCreated(String name) {
        JsonObject data = new JsonObject().put("name", name);
        mqttClient.publish("test/game/object/created", data.toBuffer(), MqttQoS.AT_MOST_ONCE, false, false);
        logger.info("📡 MQTT published object created: {}", data);
    }

    public void publishDemoMessage(String message) {
        JsonObject data = new JsonObject().put("message", message);
        mqttClient.publish("demo/message", data.toBuffer(), MqttQoS.AT_MOST_ONCE, false, false);
        logger.info("📡 MQTT published demo message: {}", data);
    }

    public void publishRoomCreated(JsonObject room) {
        JsonObject data = new JsonObject()
                .put("action", "room_created")
                .put("room", room);
        mqttClient.publish("test/rooms/created", data.toBuffer(), MqttQoS.AT_MOST_ONCE, false, false);
        logger.info("📡 MQTT published room created: {}", data);
    }

    public void publishRoomDeleted(int roomId) {
        JsonObject data = new JsonObject()
                .put("action", "room_deleted")
                .put("roomId", roomId);
        mqttClient.publish("test/rooms/deleted", data.toBuffer(), MqttQoS.AT_MOST_ONCE, false, false);
        logger.info("📡 MQTT published room deleted: {}", data);
    }

    public void publishChatMessage(String username, String message) {
        JsonObject data = new JsonObject()
                .put("username", username)
                .put("message", message)
                .put("timestamp", System.currentTimeMillis());
        mqttClient.publish("test/chat", data.toBuffer(), MqttQoS.AT_MOST_ONCE, false, false);
        logger.info("📡 MQTT published chat message: {}", data);
    }

    public void publishControllerCreated(JsonObject controller) {
        JsonObject data = new JsonObject()
                .put("action", "controller_created")
                .put("controller", controller);
        mqttClient.publish("group-05/controllers/created", data.toBuffer(), MqttQoS.AT_MOST_ONCE, false, false);
        logger.info("📡 MQTT published controller created: {}", data);
    }

    public void publishControllerDeleted(int controllerId) {
        JsonObject data = new JsonObject()
                .put("action", "controller_deleted")
                .put("controllerId", controllerId);
        mqttClient.publish("group-05/controllers/deleted", data.toBuffer(), MqttQoS.AT_MOST_ONCE, false, false);
        logger.info("📡 MQTT published controller deleted: {}", data);
    }

    public void publishControllerSelected(JsonObject selectionData) {
        JsonObject data = new JsonObject()
                .put("action", "controller_selected")
                .put("controllerId", selectionData.getInteger("controllerId"))
                .put("playerId", selectionData.getInteger("playerId"))
                .put("lobbyId", selectionData.getInteger("lobbyId"));
        mqttClient.publish("group-05/controllers/selected", data.toBuffer(), MqttQoS.AT_MOST_ONCE, false, false);
        logger.info("📡 MQTT published controller selected: {}", data);
    }

    public void publishPlayerStatusUpdated(JsonObject statusData) {
        JsonObject data = new JsonObject()
                .put("action", "player_status_updated")
                .put("lobbyId", statusData.getInteger("lobbyId"))
                .put("playerId", statusData.getInteger("playerId"))
                .put("status", statusData.getString("status"));
        mqttClient.publish("group-05/players/status", data.toBuffer(), MqttQoS.AT_MOST_ONCE, false, false);
        logger.info("📡 MQTT published player status updated: {}", data);
    }

    public void publishPlayerTimeout(JsonObject timeoutData) {
        JsonObject data = new JsonObject()
                .put("action", "player_timeout")
                .put("lobbyId", timeoutData.getInteger("lobbyId"))
                .put("playerId", timeoutData.getInteger("playerId"))
                .put("reason", timeoutData.getString("reason"));
        mqttClient.publish("group-05/players/timeout", data.toBuffer(), MqttQoS.AT_MOST_ONCE, false, false);
        logger.info("📡 MQTT published player timeout: {}", data);
    }

    public void publishToTopic(String topic, String message) {
        mqttClient.publish(topic, io.vertx.core.buffer.Buffer.buffer(message), MqttQoS.AT_MOST_ONCE, false, false);
        logger.info("📡 MQTT published to topic {}: {}", topic, message);
    }
}
