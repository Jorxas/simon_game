package com.example.controller;

import com.example.database.DatabaseClient;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.Tuple;

public class ControllerRepository {

    private final JDBCPool jdbcPool;

    public ControllerRepository() {
        this.jdbcPool = DatabaseClient.getInstance();
    }

    public void createController(Handler<AsyncResult<Integer>> resultHandler) {
        String query = "INSERT INTO controllers (status) VALUES ('online')";
        
        jdbcPool
            .preparedQuery(query)
            .execute()
            .compose(rows -> {
                // Récupérer l'ID du contrôleur qui vient d'être créé
                return jdbcPool
                    .preparedQuery("SELECT LAST_INSERT_ID() as id")
                    .execute()
                    .map(resultRows -> resultRows.iterator().next().getInteger("id"));
            })
            .onComplete(resultHandler);
    }

    public void updateControllerStatus(int controllerId, String status, Handler<AsyncResult<Void>> resultHandler) {
        String query = "UPDATE controllers SET status = ? WHERE id = ?";
        Tuple params = Tuple.of(status, controllerId);
        
        jdbcPool
            .preparedQuery(query)
            .execute(params)
            .map(rows -> (Void) null)
            .onComplete(resultHandler);
    }



    public void getAllControllers(Handler<AsyncResult<io.vertx.sqlclient.RowSet<io.vertx.sqlclient.Row>>> resultHandler) {
        String query = "SELECT * FROM controllers ORDER BY id ASC";
        
        jdbcPool
            .preparedQuery(query)
            .execute()
            .onComplete(resultHandler);
    }

    public void deleteController(int controllerId, Handler<AsyncResult<Void>> resultHandler) {
        String query = "DELETE FROM controllers WHERE id = ?";
        Tuple params = Tuple.of(controllerId);
        
        System.out.println("Executing DELETE query: " + query + " with params: " + controllerId);
        
        jdbcPool
            .preparedQuery(query)
            .execute(params)
            .map(rows -> {
                System.out.println("DELETE query executed, rows affected: " + rows.rowCount());
                return (Void) null;
            })
            .onComplete(resultHandler);
    }
}
