package com.csranger.todolist.service;

import com.csranger.todolist.Constants;
import com.csranger.todolist.entity.Todo;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.redis.RedisClient;
import io.vertx.redis.RedisOptions;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 封装 RedisClient service 实现Redis版本的服务
 */
public class RedisTodoService implements TodoService {

    public static final Logger LOGGER = LoggerFactory.getLogger(RedisTodoService.class);

    private final Vertx vertx;
    private final RedisOptions config;
    private final RedisClient redis;

    public RedisTodoService(Vertx vertx, RedisOptions config) {
        this.vertx = vertx;
        this.config = config;
        this.redis = RedisClient.create(vertx, config);
    }

    @Override
    public Future<Boolean> initData() {
        // initData插入的待办事项的url没有"localhost/",因为没有wrapObject
        return this.insert(new Todo(Math.abs(new java.util.Random().nextInt()),
                "Something to do...", false, 1, "todo/ex"));
    }


    @Override
    public Future<Optional<Todo>> getCertain(String todoID) {
        Future<Optional<Todo>> result = Future.future();
        redis.hget(Constants.REDIS_TODO_KEY, todoID, ar -> {
            if (ar.succeeded()) {
                result.complete(Optional.ofNullable(ar.result() == null ? null : new Todo(ar.result())));
            } else {
                result.fail(ar.cause());
            }
        });
        return result;
    }

    @Override
    public Future<List<Todo>> getAll() {
        Future<List<Todo>> result = Future.future();
        redis.hvals(Constants.REDIS_TODO_KEY, ar -> {
            if (ar.succeeded())        // ar.result 返回的是 jsonArray：List<Object>
                result.complete(ar.result()
                        .stream()
                        .map(x -> new Todo((String) x))
                        .collect(Collectors.toList()));    // 转变成 List<Todo>
            else result.fail(ar.cause());
        });
        return result;
    }

    @Override
    public Future<Boolean> insert(Todo todo) {
        Future<Boolean> result = Future.future();
        final String encoded = Json.encodePrettily(todo);
        redis.hset(Constants.REDIS_TODO_KEY, String.valueOf(todo.getId()), encoded, ar -> {
            if (ar.succeeded()) result.complete(true);
            else result.fail(ar.cause());
        });
        return result;
    }

    // 好好理解此 update 实现
    // 顺序组合 Future:compose(mapper)：当前 Future 完成时，执行相关代码，并返回 Future。当返回的 Future 完成时，组合完成。
    @Override
    public Future<Todo> update(String todoId, Todo newTodo) {
        LOGGER.info("update");
        // 更新待办事项的逻辑，我们会发现它其实是由两个独立的操作组成 - get 和 insert（对于Redis来说）
        // get 查找指定 id 的待办事项，如果存在则使用 insert 方法插入新待办事项；如果id对应的待办事项不存在则返回404
        return this.getCertain(todoId).compose(old -> { // getCertain(todoId)返回的是 Future<Optional<Todo>> 而 old 代表着返回结果中的 Optional<Todo>
            if (old.isPresent()) {                     // 1. 查找待办事项存在
                Todo fnTodo = old.get().merge(newTodo);
                return this.insert(fnTodo)                // 1.1 insert(fnTodo)返回结果 Future<Boolean>
                        .map(r -> r ? fnTodo : null);     // 1.2 如果插入的结果成功将 Future<Boolean> 转化成 Future<Todo>
            } else {                                  // 2. 查找待办事项不存在
                return Future.succeededFuture();
            }
        });
    }

    @Override
    public Future<Boolean> delete(String todoId) {
        Future<Boolean> result = Future.future();
        redis.hdel(Constants.REDIS_TODO_KEY, todoId, ar -> {
            if (ar.succeeded()) result.complete(true);
            else result.complete(false);
        });
        return result;
    }

    @Override
    public Future<Boolean> deleteAll() {
        Future<Boolean> result = Future.future();
        redis.del(Constants.REDIS_TODO_KEY, ar -> {
            if (ar.succeeded()) result.complete(true);
            else result.complete(false);
        });
        return result;
    }
}
