package com.jacohend.rxvertrest; /**
 * Created by jacob on 1/22/16.
 */

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.http.HttpServerResponse;
import io.vertx.rxjava.ext.asyncsql.AsyncSQLClient;
import io.vertx.rxjava.ext.asyncsql.PostgreSQLClient;
import io.vertx.rxjava.ext.sql.SQLConnection;
import io.vertx.rxjava.ext.web.Router;
import io.vertx.rxjava.ext.web.RoutingContext;

public class rxVertRest extends AbstractVerticle {

    private AsyncSQLClient async_postgresql;
    final String SQL_CONN = "cursor";
    final String REST_CONTENT = "application/json";

    //Load config dynamically from environment- better than static file, especially for docker
    final JsonObject config = new JsonObject().put("host", System.getProperty("DB_HOST", "localhost"))
                                                    .put("port", Integer.parseInt(System.getProperty("DB_PORT", "5432")))
                                                    .put("maxPoolSize", Integer.parseInt(System.getProperty("DB_POOL", "10")))
                                                    .put("username", System.getProperty("DB_USER", "test"))
                                                    .put("password", System.getProperty("DB_PASS", "test"))
                                                    .put("database", System.getProperty("DB_NAME", "test"))
                                                    .put("server_port", Integer.parseInt(System.getProperty("SERVER_PORT", "8080")));

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        async_postgresql = PostgreSQLClient.createShared(vertx, config);

        Router router = Router.router(vertx);

        router.route("/*").handler(this::createDB).failureHandler(this::failDB); //connection pool from jdbc example
        router.get("/healthcheck").handler(this::healthCheck);
        router.get("/healthcheckdb").handler(this::healthCheckDB);
        router.post("/user").handler(this::postUser);
        router.get("/users").handler(this::getUsers);

        vertx.createHttpServer().requestHandler(router::accept).listenObservable(config.getInteger("server_port"))
                .subscribe(
                        success -> {
                            System.out.println("server up");
                            startFuture.complete();
                        },
                        failure -> {
                            System.out.println("can't bring server up");
                            startFuture.fail(failure);
                        }
                );
    }

    public void createDB(RoutingContext routingContext){
        async_postgresql.getConnection(sql -> {
            if(sql.failed()){
                routingContext.fail(sql.cause());
            }else{
                SQLConnection cursor = sql.result();
                routingContext.put(SQL_CONN, cursor);
                routingContext.addHeadersEndHandler(done -> {cursor.close();});
                routingContext.next();
            }
        });
    }

    public SQLConnection getDB(RoutingContext routingContext){
        return routingContext.get(SQL_CONN);
    }

    public void failDB(RoutingContext routingContext){
        SQLConnection cursor = routingContext.get(SQL_CONN);
        if (cursor != null){
            cursor.close();
        }
    }

    public void healthCheck(RoutingContext routingContext){
        HttpServerResponse response = routingContext.response();
        response.setStatusCode(200)
                .putHeader("content-type", REST_CONTENT)
                .end(new JsonObject().put("status", "ok").encode());
    }

    public void healthCheckDB(RoutingContext routingContext){
        HttpServerResponse response = routingContext.response();
        SQLConnection sql = this.getDB(routingContext);
        sql.queryObservable("SELECT 1 AS dbtest").subscribe(resultSet -> {
            response.setStatusCode(200)
                    .putHeader("content-type", REST_CONTENT)
                    .end(resultSet.toJson().getJsonArray("rows", new JsonArray()).encode());
        }, err -> {
            response.setStatusCode(500).end(new JsonObject().put("err", err.getCause().toString()).encode());
        }, sql::close);
    }

    public void postUser(RoutingContext routingContext){
        HttpServerResponse response = routingContext.response();
        SQLConnection sql = this.getDB(routingContext);
        JsonObject user = routingContext.getBodyAsJson();
        //TODO: Even though this is just an example, I still need to salt/hash the password!
        sql.updateWithParamsObservable("INSERT INTO \"user\" (email, password) VALUES (?, ?)",
                new JsonArray().add(user.getValue("email")).add(user.getValue("password")))
                .subscribe(updateResult -> {
                    response.setStatusCode(200).end();
                }, err -> {
                    response.setStatusCode(500).end(new JsonObject().put("err", err.getCause().toString()).encode());
                }, sql::close);
    }

    public void getUsers(RoutingContext routingContext){
        HttpServerResponse response = routingContext.response();
        SQLConnection sql = this.getDB(routingContext);
        sql.queryObservable("SELECT id, email FROM \"user\"").subscribe(resultSet -> {
            response.setStatusCode(200)
                    .putHeader("content-type", REST_CONTENT)
                    .end(resultSet.toJson().getJsonArray("rows", new JsonArray()).encode());
        }, err -> {
            response.setStatusCode(500).end(new JsonObject().put("err", err.getCause().toString()).encode());
        }, sql::close);
    }

}