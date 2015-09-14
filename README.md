(建设中)

Answerer, 即凯尔特神话的神剑Fragarach，能自动出鞘攻击，因此又名Answerer(应答之剑)。

使用Answerer框架，你只需编写和维护业务逻辑代码，并提供一点配置信息，框架会自动为你生成其他所需的代码，构成可运行的程序。

不同于IDE的一次性代码生成，Answerer能持续陪伴你的项目。当你修改了代码，Answerer也自动修改代码，与你保持一致。不怕改需求，不怕重构！

Answerer生成的是平铺直叙的代码，可读性、可调试性都高于流行框架中基于反射的代码。

目前Answerer采用Gradle构建工具，能自动生成基于Spring Boot + Ebean ORM的RESTful service (用户可自行加入网页部分)。未来考虑接入更多框架，用户可以挑选使用。

<!--###如何运行:
已有1个Demo项目，在example目录下运行./gradlew run (请确保8080端口可用)，会构建并启动web服务。-->

###特色功能:
- 自动组装数据
- 静态检查的鸭子类型(duck type)

只需调用autowire方法注入任意变量，它们会被自动转换、组装到返回类型中(如果不合适，代码分析器会报告错误)。比Spring的注入还方便！静态检查更可靠！

代码示例:

```
// User字段为id, email, password, nickname, avatar, brief
// UserView字段为id, name, avatar, brief

// 用Java写的配置信息，对重构友好(未来可能支持YAML等格式)
public class MyConfig extends Config {
  User user; UserView uv;
  { // 用映射来解决字段名的差异
    map(user.nickname, uv.name);
  }
}

String email="a@a.com", password="1234", nickname="Fraga", avatar="", brief="";

User user = Wirer.autowire(email, password, nickname, avatar, brief);
UserView uv = Wirer.autowire(user);
user = Wirer.autowire(uv, email, password);
```

###原理:
通过语法分析来理解用户代码，从而自动生成与之适配的代码。相关技术可参考我博客 http://segmentfault.com/blog/sorra

##未来蓝图

- 自动注入一切数据和关系，无论它来自SQL、NoSQL甚至Micro service
- 懒加载和批量加载
- 提供配方(recipe)平台，第三方框架通过添加配方来接入
- 分布式解决方案(例如微服务)

##近期目标
- 可配置的REST
- AOP
- 用户代码和生成代码分离，使code base更干净
- 对TDD友好，业务逻辑直接可测试