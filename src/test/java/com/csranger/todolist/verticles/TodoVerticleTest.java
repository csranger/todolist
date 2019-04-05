package com.csranger.todolist.verticles;

import com.csranger.todolist.entity.Todo;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class TodoVerticleTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TodoVerticleTest.class);

    private static final int PORT = 8082;
    private Vertx vertx;

    private final Todo todoEx = new Todo(164, "Test case...", false,
            22, "http://localhost:8082/todos/164");
    private final Todo todoUp = new Todo(164, "Test case...Update!", false,
            26, "http://localhost:8082/todos/164");


    @Before
    public void setUp(TestContext context) {
        vertx = Vertx.vertx();
        final DeploymentOptions options = new DeploymentOptions()
                .setConfig(new JsonObject().put("http.port", PORT));
        TodoVerticle verticle = new TodoVerticle();

        vertx.deployVerticle(verticle, options, context.asyncAssertSuccess());
    }

    @After
    public void tearDown(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    // 测试获取待办事项的逻辑
    @Test(timeout = 3000L)
    public void testGetTodo(TestContext context) throws Exception {
        HttpClient httpClient = vertx.createHttpClient();
        Async async = context.async();
        httpClient.getNow(PORT, "localhost", "/todos/164", response ->
                response.bodyHandler(body -> {
                    LOGGER.info("GET请求localhost：8082/todos/164 返回的结果如下\n" + body.toString());
                    context.assertEquals(new Todo(body.toString()), todoEx);
                    httpClient.close();
                    async.complete();
                })
        );
    }

    // 测试创建待办事项的逻辑
    @Test(timeout = 3000L)
    public void testCreateTodo(TestContext context) throws Exception {
        HttpClient httpClient = vertx.createHttpClient();
        Async async = context.async();
        Todo todo = new Todo(164, "Test case...", false, 22, "/164");
        httpClient.post(PORT, "localhost", "/todos", response -> {
            context.assertEquals(201, response.statusCode());
            httpClient.close();
            async.complete();

        }).putHeader("content-type", "application/json").end(Json.encodePrettily(todo));
    }

    // 测试更新待办事项的逻辑和删除待办事项
    @Test(timeout = 3000L)
    public void testUpdateAndDeleteTodo(TestContext context) throws Exception {
        HttpClient httpClient = vertx.createHttpClient();
        Async async = context.async();
        Todo todo = new Todo(164, "Test case...Update!", false,
                26, "/164abcd");
        LOGGER.info("准备测试更新待办事项");
        httpClient.request(HttpMethod.PATCH, PORT, "localhost", "/todos/164", response -> response.bodyHandler(body -> {
            context.assertEquals(new Todo(body.toString()), todoUp);
            httpClient.request(HttpMethod.DELETE, PORT, "localhost", "/todos/164", res -> {
                context.assertEquals(204, res.statusCode());
                async.complete();
            }).end();

        })).putHeader("content-type", "application/json").end(Json.encodePrettily(todo));
    }

}