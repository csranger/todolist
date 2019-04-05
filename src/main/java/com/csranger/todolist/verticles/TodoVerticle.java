package com.csranger.todolist.verticles;

import com.csranger.todolist.Constants;
import com.csranger.todolist.entity.Todo;
import com.csranger.todolist.service.JdbcTodoService;
import com.csranger.todolist.service.RedisTodoService;
import com.csranger.todolist.service.TodoService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.redis.RedisOptions;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public class TodoVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(TodoVerticle.class);

    private static final String HOST = "0.0.0.0";
    private static final int PORT = 8082;

    private TodoService service;

    // 初始化存储结构
    private void initData() {
        final String serviceType = config().getString("service.type", "redis");
        LOGGER.info("Service Type: " + serviceType);
        switch (serviceType) {
            case "jdbc":
                service = new JdbcTodoService(vertx, config());
                break;
            case "redis":
            default:
                RedisOptions config = new RedisOptions()
                        .setHost(config().getString("redis.host", "127.0.0.1"))
                        .setPort(config().getInteger("redis.port", 6379));

                service = new RedisTodoService(vertx, config);
        }
        service.initData().setHandler(res -> {
            if (res.failed()) {
                LOGGER.error("Persistence service is not running!");
                res.cause().printStackTrace();
            }
        });
    }

    @Override
    public void start(Future<Void> future) throws Exception {
        Router router = Router.router(vertx);

        // CORS support:CORS是一个由浏览器共同遵循的一套策略，通过http的header来进行交互。当浏览器识别到发送的请求是跨域请求的时候，
        // 会把Origin的Header加入到http请求一起发送到服务器。服务器会解析Header并判断是否允许跨域请求，如果允许，
        // 响应头中会有Access-Control-Allow-Origin这个属性。如果服务器允许所有跨域请求，将该属性设置为*即可，如果响应头没有改属性，则浏览器会拦截该请求。
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
        // 给路由器绑定了一个全局的BodyHandler,它的作用是处理HTTP请求正文并获取其中的数据。比如，在实现添加待办事项逻辑的时候，
        // 我们需要读取请求正文中的JSON数据，这时候我们就可以用BodyHandler
        router.route().handler(BodyHandler.create());


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
                .listen(PORT, HOST, result -> {
                    if (result.succeeded()) future.complete();
                    else future.fail(result.cause());
                });


        // 初始化 RedisClient 并且测试连接
        initData();
    }


    // 对比1和2来理解consumer
    private <T> Handler<AsyncResult<T>> resultHandler(RoutingContext context, Consumer<T> consumer) {
        return res -> {
            if (res.succeeded()) {     // 异步操作成功，将异步操作结果传入，并执行 consumer 的唯一accept方法
                consumer.accept(res.result());
            } else {                   // 异步操作失败，通过 context 传入失败状态
                context.response().setStatusCode(503).end();
            }

        };
    }

    // 1.实现获取待办事项的逻辑
    private void handleGetTodo(RoutingContext context) {
        String todoId = context.request().getParam("todoId");
        if (todoId == null) {
            context.response().setStatusCode(400).end();  // 400 客户端请求的语法错误，服务器无法理解
            return;
        }
        service.getCertain(todoId).setHandler(ar -> {
            if (ar.succeeded()) {   // getCertain 操作完成成功
                Optional<Todo> res = ar.result();
                if (res.isPresent()) {          // 指定 id 的待办事项不为 null
                    final String encoded = Json.encodePrettily(res.get());
                    context.response().putHeader("content-type", "application/json")
                            .end(encoded);
                } else {                        // 指定 id 的待办事项为 null：未找到指定资源： 404
                    context.response().setStatusCode(404).end();
                }
            } else {                // getCertain 操作完成失败 503: service unavailable：无法进行数据库操作
                context.response().setStatusCode(503).end();
            }
        });
    }

    // 2.获取所有待办事项的逻辑
    private void handleGetAll(RoutingContext context) {
        service.getAll().setHandler(resultHandler(context, res -> {    // 此res 代表着异步操作的结果所以是 List<Todo> 类型
            if (res == null) {
                context.response().setStatusCode(503).end();   // 503：Service Unavailable 于超载或系统维护，服务器暂时的无法处理客户端的请求。
            } else {
                final String encoded = Json.encodePrettily(res);
                context.response()
                        .putHeader("content-type", "application/json")
                        .end(encoded);
            }
        }));
    }

    // 3.创建待办事项的逻辑
    private void handleCreateTodo(RoutingContext context) {
        try {
            final Todo todo = wrapObject(new Todo(context.getBodyAsString()), context);
            final String encoded = Json.encodePrettily(todo);
            service.insert(todo).setHandler(resultHandler(context, res -> {    // 此res 代表着异步操作的结果所以是 Boolean 类型
                if (res) {
                    context.response()
                            .setStatusCode(201)
                            .putHeader("content-type", "application/json")
                            .end(encoded);
                } else {
                    context.response().setStatusCode(503).end();   // service unavailable
                }
            }));
        } catch (DecodeException e) {
            context.response().setStatusCode(400).end();    // Bad Request	客户端请求的语法错误，服务器无法理解
        }
    }

    // 4.更新待办事项的逻辑
    private void handleUpdateTodo(RoutingContext context) {
        try {
            LOGGER.info("handleUpdateTodo");

            String todoId = context.request().getParam("todoId");
            final Todo newTodo = new Todo(context.getBodyAsString());
            // handle error
            if (todoId == null) {
                context.response().setStatusCode(400).end();    // url
                return;
            }

            service.update(todoId, newTodo).setHandler(resultHandler(context, res -> {  // 此res 代表着异步操作的结果所以是 Todo 类型
                if (res == null) {   // todoId 对应的待办事项在 redis 数据库中不存在
                    context.response().setStatusCode(404).end();  // 404 服务器无法根据客户端的请求找到资源
                } else {
                    final String encoded = Json.encodePrettily(res);
                    context.response()
                            .putHeader("content-type", "application/json")
                            .end(encoded);
                }

            }));
        } catch (DecodeException e) {
            context.response().setStatusCode(400).end(); //Bad Request	客户端请求的语法错误，服务器无法理解
        }
    }

    // 5.删除待办事项
    private void handleDeleteOne(RoutingContext context) {
        final String todoId = context.request().getParam("todoId");
        service.delete(todoId).setHandler(ar -> {
            if (ar.succeeded()) {
                Boolean res = ar.result();
                if (res) context.response().setStatusCode(204).end();    // No Content	无内容。服务器成功处理，但未返回内容。
                else
                    context.response().setStatusCode(503).end();        // Service Unavailable	由于超载或系统维护，服务器暂时的无法处理客户端的请求。
            } else {
                context.response().setStatusCode(503).end();        // Service Unavailable	由于超载或系统维护，服务器暂时的无法处理客户端的请求。
            }
        });
    }

    // 6.删除全部待办事项
    private void handleDeleteAll(RoutingContext context) {
        service.deleteAll().setHandler(resultHandler(context, res -> {      // 此res 代表着异步操作的结果所以是 Boolean 类型
            if (res) context.response().setStatusCode(204).end();
            else context.response().setStatusCode(503).end();
        }));
    }

    private Todo wrapObject(Todo todo, RoutingContext context) {
        int id = todo.getId();
        if (id > Todo.getIncId()) Todo.setIncIdWith(id);
        else if (id == 0) todo.setIncId();   // 对于没有ID（或者为默认ID）的待办事项，我们会给它分配一个ID。这里我们采用了自增ID的策略，通过AtomicInteger来实现。
        todo.setUrl(context.request().absoluteURI() + "/" + todo.getId());
        return todo;
    }
}
