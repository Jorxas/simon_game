package com.example.player;

import com.example.database.DatabaseClient;
import com.example.util.PasswordUtil;

import io.vertx.core.Future;

import io.vertx.core.json.JsonObject;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerRepository {

    private static final Logger logger = LoggerFactory.getLogger(PlayerRepository.class);
    private final JDBCPool jdbcPool;

    public PlayerRepository() {
        this.jdbcPool = DatabaseClient.getInstance();
    }

    public Future<Void> register(JsonObject data) {
        String username = data.getString("username");
        String plainPassword = data.getString("password");

        // Validate password
        if (!PasswordUtil.isPasswordValid(plainPassword)) {
            return Future.failedFuture("Password must be at least 6 characters long");
        }

        // Hash the password
        String hashedPassword = PasswordUtil.hashPassword(plainPassword);
        logger.info("Password hashed for user: {}", username);

        String query = "INSERT INTO players (username, password, profileUrl) VALUES (?, ?, ?)";
        Tuple params = Tuple.of(username, hashedPassword, data.getString("profileUrl", null));

        return jdbcPool
                .preparedQuery(query)
                .execute(params)
                .mapEmpty();
    }

    public Future<JsonObject> login(JsonObject data) {
        String username = data.getString("username");
        String plainPassword = data.getString("password");

        // First, get the user with the hashed password
        String query = "SELECT id, username, password, role FROM players WHERE username = ?";
        Tuple params = Tuple.of(username);

        return jdbcPool
                .preparedQuery(query)
                .execute(params)
                .map(rows -> {
                    if (rows == null || !rows.iterator().hasNext()) {
                        logger.warn("Login failed: User not found - {}", username);
                        return null;
                    } else {
                        Row row = rows.iterator().next();
                        String storedHash = row.getString("password");
                        
                        // Verify the password
                        if (PasswordUtil.verifyPassword(plainPassword, storedHash)) {
                            logger.info("Login successful for user: {}", username);
                            return new JsonObject()
                                    .put("id", row.getInteger("id"))
                                    .put("username", row.getString("username"))
                                    .put("role", row.getString("role"));
                        } else {
                            logger.warn("Login failed: Invalid password for user - {}", username);
                            return null;
                        }
                    }
                });
    }
}