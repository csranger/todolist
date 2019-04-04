package com.csranger.todolist.service;

import com.csranger.todolist.entity.Todo;
import io.vertx.core.Future;

import java.util.List;
import java.util.Optional;

public interface TodoService {


    /**
     * 1.待办事项业务逻辑与控制器混杂在一起，让这个类非常的庞大，并且这也不利于我们服务的扩展。根据面向对象解耦的思想，我们需要将控制器部分与业务逻辑部分分离。
     * 2.我们的服务需要是异步的，因此这些服务的方法要么需要接受一个Handler参数作为回调，要么需要返回一个Future对象。
     * 但是想象一下很多个Handler混杂在一起嵌套的情况，你会陷入 回调地狱，这是非常糟糕的。因此，这里我们用Future实现异步服务即待办事项服务。
     */

    Future<Boolean> initData(); // 初始化数据（或数据库）

    Future<Optional<Todo>> getCertain(String todoId);

    Future<List<Todo>> getAll();

    Future<Boolean> insert(Todo todo);

    Future<Todo> update(String todoId, Todo newTodo);   // 返回 newTodo

    Future<Boolean> delete(String todoId);

    Future<Boolean> deleteAll();
}
