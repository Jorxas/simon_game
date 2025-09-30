package com.example.mqtt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AbstractVerticle;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;

public class MqttVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(MqttVerticle.class);

    @Override
    public void start() {

        MqttClientOptions options = new MqttClientOptions()
                .setAutoKeepAlive(true)
                .setCleanSession(true)
                .setUsername("group-05")
                .setPassword("X8y!dF5zLp2A");

        MqttClient mqttClient = MqttClient.create(vertx, options);

        // Connecter au broker MQTT de l'école THM
        String mqttHost = "iti-mqtt.mni.thm.de";
        int mqttPort = 1883; // Port MQTT standard
        
        mqttClient.connect(mqttPort, mqttHost, ar -> {
            if (ar.succeeded()) {
                logger.info("Connected to MQTT broker successfully!");

                MqttController mqttController = new MqttController(mqttClient, vertx);
                mqttController.registerEventBusConsumers();
                mqttController.registerMqttConsumers();

            } else {
                logger.error("Failed to connect to MQTT: {}", ar.cause().getMessage());
            }
        });

    }
}
