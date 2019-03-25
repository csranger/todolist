package com.csranger.todolist.verticles;

import com.csranger.todolist.Constants;
import com.csranger.todolist.entity.Todo;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.redis.RedisClient;
import io.vertx.redis.RedisOptions;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class SingleApplicationVerticle extends AbstractVerticle {

    private static final String HTTP_HOST = "0.0.0.0";
    private static final String REDIS_HOST = "127.0.0.1";
    private static final int HTTP_PORT = 8082;
    private static final int REDIS_PORT = 6379;

    private RedisClient redis;

    private static final Logger LOGGER = LoggerFactory.getLogger(SingleApplicationVerticle.class);

    public void start(Future<Void> future) throws Exception {

        // 初始化 RedisClient 并且测试连接
        initData();

        Router router = Router.router(vertx);

        // CORS support:CORS是一个由浏览器共同遵循的一套策略，通过http的header来进行交互。当浏览器识别到发送的请求是跨域请求的时候，会把Origin的Header加入到http请求一起发送到服务器。服务器会解析Header并判断是否允许跨域请求，如果允许，响应头中会有Access-Control-Allow-Origin这个属性。如果服务器允许所有跨域请求，将该属性设置为*即可，如果响应头没有改属性，则浏览器会拦截该请求。
        Set<String> allowHeaders = new HashSet<>();
        allowHeaders.add("x-requested-with");
        allowHeaders.add("Access-Control-Allow-Origin");
        allowHeaders.add("origin");
        allowHeaders.add("Content-Type");
        allowHeaders.add("accept");
        Set<HttpMethod> allowMethods = new HashSet<>();
        allowMethods.add(HttpMethod.GET);
        allowMethods.add(HttpMethod.POST);
        allowMethods.add(HttpMethod.DELETE);
        allowMethods.add(HttpMethod.PATCH);
        router.route().handler(CorsHandler.create("*")   // route()方法（无参数）代表此路由匹配所有请求,这两个Set的作用是支持 CORS
                .allowedHeaders(allowHeaders)
                .allowedMethods(allowMethods));
        router.route().handler(BodyHandler.create());   // 给路由器绑定了一个全局的BodyHandler,它的作用是处理HTTP请求正文并获取其中的数据。比如，在实现添加待办事项逻辑的时候，我们需要读取请求正文中的JSON数据，这时候我们就可以用BodyHandler

        // routes:用对应的方法（如get,post,patch等等）将路由路径与路由器绑定，并且我们调用handler方法给每个路由绑定上对应的Handler
        // 接受的Handler类型为Handler<RoutingContext>。这里我们分别绑定了六个方法引用，这也是我们待办事项服务逻辑的核心。
        router.get(Constants.API_GET).handler(this::handleGetTodo);
        router.get(Constants.API_LIST_ALL).handler(this::handleGetAll);
        router.post(Constants.API_CREATE).handler(this::handleCreateTodo);
        router.patch(Constants.API_UPDATE).handler(this::handleUpdateTodo);
        router.delete(Constants.API_DELETE).handler(this::handleDeleteOne);
        router.delete(Constants.API_DELETE_ALL).handler(this::handleDeleteAll);


        // 创建一个HTTP服务端
        vertx.createHttpServer()
                .requestHandler(router::accept)
                .listen(HTTP_PORT, HTTP_HOST, result -> {
                    if (result.succeeded()) future.complete();
                    else future.fail(result.cause());
                });
    }

    private void initData() {
        RedisOptions config = new RedisOptions()
                .setHost(config().getString("redis.host", REDIS_HOST))
                .setPort(config().getInteger("redis.port", REDIS_PORT));

        this.redis = RedisClient.create(vertx, config);

        // Redis支持各种格式的数据，并且支持多种方式存储（如list、hash map等）。这里我们将我们的待办事项存储在 哈希表(map) 中。
        // 我们使用待办事项的id作为key，JSON格式的待办事项数据作为value。同时，我们的哈希表本身也要有个key，我们把它命名为 VERT_TODO，并且存储到Constants类中：
        // 可见所有的待办事项都存储在一个叫 VERT_TODO 的哈希表中，key是待办事项id，value是待办事项对象转化后的字符串
        redis.hset(Constants.REDIS_TODO_KEY, "24", Json.encodePrettily(
                new Todo(24, "Something to do...", false, 1, "todo/ex")), res -> {
            if (res.failed()) {
                LOGGER.error("Redis service is not running");
                res.cause().printStackTrace();
            }
        });
    }

    // 1.实现获取待办事项的逻辑
    private void handleGetTodo(RoutingContext context) {
        String todoId = context.request().getParam("todoId");
        if (todoId == null) {
            sendError(400, context.response());
        } else {  //路径参数获取成功
            // hget 代表通过key从对应的哈希表中获取对应的value:第一个参数key对应哈希表的key,第二个参数field代表待办事项的key,第三个参数代表获取操作后对应的回调
            redis.hget(Constants.REDIS_TODO_KEY, todoId, x -> {
                if (x.succeeded()) {  // 从 redis 中获取结果成功
                    String result = x.result();    // x.result()返回的字符串代表一个 Json.encodePrettily(new Todo(...))
                    if (result == null) {  // 获取结果为null表示没有此id对应的待办事项
                        sendError(404, context.response());
                    } else {
                        context.response()
                                .putHeader("content-type", "application/json")
                                .end(result);
                    }
                } else {
                    sendError(503, context.response());
                }
            });

        }
    }

    private void sendError(int statusCode, HttpServerResponse response) {
        response.setStatusCode(statusCode).end();  //只有调用end方法时，对应的HTTP Response才能被发送回客户端
    }

    // 2.获取所有待办事项的逻辑
    private void handleGetAll(RoutingContext context) {
        // hvals ： Get all the values in a hash ：JsonArray，是个 List<String> ：每个 String 代表一个待办事项对象
        redis.hvals(Constants.REDIS_TODO_KEY, res -> {
            if (res.succeeded()) {
                String encoded = Json.encodePrettily(res.result().stream()   // res.result()返回的是List<String> ：每个 String 代表一个待办事项对象； 经过encodePrettily函数返回的字符串代表一个 Json.encodePrettily(new List(todo1, todo2, ...))
                        .map(x -> new Todo((String) x))
                        .collect(Collectors.toList()));
                context.response()
                        .putHeader("content-type", "application/json")
                        .end(encoded);
            } else {
                sendError(503, context.response());
            }
        });
    }

    // 3.创建待办事项的逻辑
    private void handleCreateTodo(RoutingContext context) {
        try {
            final Todo todo = wrapObject(new Todo(context.getBodyAsString()), context);
            final String encoded = Json.encodePrettily(todo);
            redis.hset(Constants.REDIS_TODO_KEY, String.valueOf(todo.getId()), encoded, res -> {
                if (res.succeeded()) {
                    context.response()
                            .setStatusCode(201)
                            .putHeader("content-type", "application/json")
                            .end(encoded);
                } else {
                    sendError(503, context.response());
                }
            });
        } catch (DecodeException e) {
            sendError(400, context.response());
        }
    }

    private Todo wrapObject(Todo todo, RoutingContext context) {
        int id = todo.getId();
        if (id > Todo.getIncId()) Todo.setIncIdWith(id);
        else if (id == 0) todo.setIncId();   // 对于没有ID（或者为默认ID）的待办事项，我们会给它分配一个ID。这里我们采用了自增ID的策略，通过AtomicInteger来实现。
        todo.setUrl(context.request().absoluteURI() + "/" + todo.getId());
        return todo;
    }

    // 4.更新待办事项的逻辑
    private void handleUpdateTodo(RoutingContext context) {
        try {
            String todoId = context.request().getParam("todoId");
            final Todo newTodo = new Todo(context.getBodyAsString());
            // handle error
            if (todoId == null || newTodo == null) {
                sendError(400, context.response());
                return;
            }

            redis.hget(Constants.REDIS_TODO_KEY, todoId, x -> { // (3)
                if (x.succeeded()) {
                    String result = x.result();
                    if (result == null)
                        sendError(404, context.response()); // (4)
                    else {
                        Todo oldTodo = new Todo(result);
                        String response = Json.encodePrettily(oldTodo.merge(newTodo)); // (5)
                        redis.hset(Constants.REDIS_TODO_KEY, todoId, response, res -> { // (6)
                            if (res.succeeded()) {
                                context.response()
                                        .putHeader("content-type", "application/json")
                                        .end(response); // (7)
                            }
                        });
                    }
                } else
                    sendError(503, context.response());
            });
        } catch (DecodeException e) {
            sendError(400, context.response());
        }

    }

    // 5.删除待办事项
    private void handleDeleteOne(RoutingContext context) {
        String todoID = context.request().getParam("todoId");
        redis.hdel(Constants.REDIS_TODO_KEY, todoID, res -> {
            if (res.succeeded())
                context.response().setStatusCode(204).end();
            else
                sendError(503, context.response());
        });
    }

    // 6.删除全部待办事项
    private void handleDeleteAll(RoutingContext context) {
        redis.del(Constants.REDIS_TODO_KEY, res -> {
            if (res.succeeded())
                context.response().setStatusCode(204).end();
            else
                sendError(503, context.response());
        });
    }


}
