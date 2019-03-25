package com.csranger.todolist.entity;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 待办事项对象：数据实体对象 - Todo 实体:Todo 实体对象由序号id、标题title、次序order、地址url以及代表待办事项是否完成的一个标识complete组成
 * 注解 @DataObject，这是用于生成JSON转换类的注解：被 @DataObject 注解的实体类需要满足以下条件：拥有一个拷贝构造函数以及一个接受一个 JsonObject 对象的构造函数。
 */

@DataObject(generateConverter = true)
public class Todo {

    public static final AtomicInteger acc = new AtomicInteger(0);    // counter

    private int id;
    private String title;
    private Boolean completed;
    private Integer order;
    private String url;

    // 5种构造器
    public Todo() {
    }

    public Todo(Todo other) {
        this.id = other.id;
        this.title = other.title;
        this.completed = other.completed;
        this.order = other.order;
        this.url = other.url;
    }

    public Todo(int id, String title, Boolean completed, Integer order, String url) {
        this.id = id;
        this.title = title;
        this.completed = completed;
        this.order = order;
        this.url = url;
    }


    public Todo(JsonObject obj) {
        TodoConverter.fromJson(obj, this);
    }

    public Todo(String jsonStr) {
        TodoConverter.fromJson(new JsonObject(jsonStr), this);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        TodoConverter.toJson(this, json);
        return json;
    }

    // counter
    public void setIncId() {
        this.id = acc.incrementAndGet();
    }

    public static int getIncId() {
        return acc.get();
    }

    public static void setIncIdWith(int n) {
        acc.set(n);
    }

    // completed
    public Boolean isCompleted() {
        return getOrElse(completed, false);
    }

    public void setCompleted(Boolean completed) {
        this.completed = completed;
    }

    private <T> T getOrElse(T value, T defaultValue) {
        return value == null ? defaultValue : value;
    }

    // merge 用于更新待办事项
    public Todo merge(Todo todo) {
        return new Todo(id,
                getOrElse(todo.title, title),
                getOrElse(todo.completed, completed),
                getOrElse(order, order),
                url);
    }

    // id, title, order, url 的 setter getter
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getOrder() {
        return order;
    }

    public void setOrder(Integer order) {
        this.order = order;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Todo todo = (Todo) o;
        return id == todo.id &&
                Objects.equals(title, todo.title) &&
                Objects.equals(completed, todo.completed) &&
                Objects.equals(order, todo.order) &&
                Objects.equals(url, todo.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, completed, order, url);
    }

    @Override
    public String toString() {
        return "Todo{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", completed=" + completed +
                ", order=" + order +
                ", url='" + url + '\'' +
                '}';
    }
}
