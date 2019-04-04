package com.csranger.todolist.service;

import com.csranger.todolist.entity.Todo;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class JdbcTodoService implements TodoService {

    private final Vertx vertx;
    private final JsonObject config;
    private final JDBCClient client;


    // SQL
    private static final String SQL_CREATE = "CREATE TABLE IF NOT EXISTS `todo` (\n" +
            "  `id` int(11) NOT NULL AUTO_INCREMENT,\n" +
            "  `title` varchar(255) DEFAULT NULL,\n" +
            "  `completed` tinyint(1) DEFAULT NULL,\n" +
            "  `order` int(11) DEFAULT NULL,\n" +
            "  `url` varchar(255) DEFAULT NULL,\n" +
            "  PRIMARY KEY (`id`) )";
    private static final String SQL_INSERT = "INSERT INTO `todo` " +
            "(`id`, `title`, `completed`, `order`, `url`) VALUES (?, ?, ?, ?, ?)";
    private static final String SQL_QUERY = "SELECT * FROM todo WHERE id = ?";
    private static final String SQL_QUERY_ALL = "SELECT * FROM todo";
    private static final String SQL_UPDATE = "UPDATE `todo`\n" +
            "SET `id` = ?,\n" +
            "`title` = ?,\n" +
            "`completed` = ?,\n" +
            "`order` = ?,\n" +
            "`url` = ?\n" +
            "WHERE `id` = ?;";
    private static final String SQL_DELETE = "DELETE FROM `todo` WHERE `id` = ?";
    private static final String SQL_DELETE_ALL = "DELETE FROM `todo`";


    public JdbcTodoService(Vertx vertx, JsonObject config) {
        this.vertx = vertx;
        this.config = config;
        this.client = JDBCClient.createShared(vertx, config);
    }

    // 每一个数据库操作都需要获取数据库连接
    // 查询方法：query queryWithParams   增删改：update updateWithParams
    @Override
    public Future<Boolean> initData() {
        Future<Boolean> result = Future.future();
        client.getConnection(res -> {
            if (res.succeeded()) {              // getConnection 操作成功
                final SQLConnection connection = res.result();
                connection.execute(SQL_CREATE, create -> {
                    if (create.succeeded()) {
                        result.complete(true);      // 执行 SQL 语句成功
                    }
                    else {
                        result.fail(create.cause());                          // 执行 SQL 语句失败
                    }
                    connection.close();         // 最后一定要关闭数据库连接
                });

            } else {
                result.fail(res.cause());       // getConnection 操作失败
            }
        });
        return result;
    }

    // 由于每一个数据库操作都需要获取数据库连接，
    // 因此我们来包装一个返回Handler<AsyncResult<SQLConnection>>的方法，在此回调中可以直接使用数据库连接，可以减少一些代码量
    // 相当于 TodoVerticle 里的 resultHandler 方法
    // 这里的 Handler<SQLConnection> handler 左右就同于 Consumer<T> consumer，当异步操作成功，将异步操作结果传入，并执行 handler 的唯一 handle 方法
    private Handler<AsyncResult<SQLConnection>> connHandler(Future future, Handler<SQLConnection> handler) {
        return res -> {
            if (res.succeeded()) {          // 异步操作成功，将异步操作结果传入
                final SQLConnection connection = res.result();
                handler.handle(connection);
            } else
                future.fail(res.cause());   // 异步操作失败，通过 future 传入失败状态
        };
    }

    @Override
    public Future<Optional<Todo>> getCertain(String todoID) {
        Future<Optional<Todo>> result = Future.future();
        client.getConnection(connHandler(result, connection -> {
            connection.queryWithParams(SQL_QUERY, new JsonArray().add(todoID), res -> {
                if (res.succeeded()) {     // 查询过程成功
                    List<JsonObject> list = res.result().getRows();
                    if (list == null || list.isEmpty())
                        result.complete(Optional.empty());   // 如果查询时指定 todoId 数据库找不到待办事项
                    else
                        result.complete(Optional.of(new Todo(list.get(0))));   // List<JsonObject> -> JsonObject -> Todo -> Optional<Todo>
                } else {                    // 查询过程失败
                    result.fail(res.cause());
                }
                connection.close();
            });
        }));

        return result;
    }

    @Override
    public Future<List<Todo>> getAll() {
        Future<List<Todo>> result = Future.future();
        client.getConnection(connHandler(result, connection -> {
            connection.query(SQL_QUERY_ALL, res -> {
                if (res.succeeded()) {          // 查询过程成功
                    List<Todo> todos = res.result().getRows().stream().map(Todo::new).collect(Collectors.toList());
                    result.complete(todos);


                } else {                        // 查询过程失败
                    result.fail(res.cause());
                }
                connection.close();
            });
        }));
        return result;
    }

    @Override
    public Future<Boolean> insert(Todo todo) {
        Future<Boolean> result = Future.future();
        client.getConnection(connHandler(result, connection -> {
            connection.updateWithParams(SQL_INSERT, new JsonArray()      // 本质上是 List
                    .add(todo.getId())
                    .add(todo.getTitle())
                    .add(todo.isCompleted())
                    .add(todo.getOrder())
                    .add(todo.getUrl()), res -> {
                if (res.succeeded()) result.complete(true);
                else result.fail(res.cause());
                connection.close();
            });
        }));
        return result;
    }

    @Override
    public Future<Todo> update(String todoId, Todo newTodo) {
        Future<Todo> result = Future.future();
        client.getConnection(connHandler(result, connection -> {
            // 首先查找此 id 对应的待办事项是否存在
            this.getCertain(todoId).setHandler(res -> {
                if (res.failed()) result.fail(res.cause());     // 1。查找过程失败
                else {                                          // 2。查找过程成功
                    Optional<Todo> oldTodo = res.result();
                    if (!oldTodo.isPresent()) {    // 2。1查找结果：此 id 对应的待办事项不存在
                        result.complete(null);
                    }
                    // 2。2查找结果：此 id 对应的待办事项存在
                    Todo fnTodo = oldTodo.get().merge(newTodo);
                    // 接着对该 id 对应的待办事项进行 update
                    connection.updateWithParams(SQL_UPDATE, new JsonArray().add(todoId)
                            .add(fnTodo.getTitle())
                            .add(fnTodo.isCompleted())
                            .add(fnTodo.getOrder())
                            .add(fnTodo.getUrl())
                            .add(todoId), r -> {
                        if (r.succeeded()) result.complete(fnTodo);      // update 执行成功
                        else result.fail(res.cause());
                        connection.close();
                    });
                }
            });
        }));
        return result;
    }

    @Override
    public Future<Boolean> delete(String todoId) {
        Future<Boolean> result = Future.future();
        client.getConnection(connHandler(result, connection -> {
            connection.updateWithParams(SQL_DELETE, new JsonArray().add(todoId), res -> {
                if (res.succeeded()) result.complete(true);     // delete 执行成功，返回true
                else result.complete(false);                    // delete 执行失败，返回false
                connection.close();
            });
        }));
        return result;
    }

    @Override
    public Future<Boolean> deleteAll() {
        Future<Boolean> result = Future.future();
        client.getConnection(connHandler(result, connection -> {
            connection.update(SQL_DELETE_ALL, res -> {
                if (res.succeeded()) result.complete(true);     // delete 执行成功，返回true
                else result.complete(false);                    // delete 执行失败，返回false
                connection.close();
            });
        }));
        return result;
    }
}
