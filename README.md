Answerer, 即凯尔特神话的神剑Fragarach，能自动出鞘攻击，因此又名Answerer(应答之剑)。

使用Answerer，你只需编写业务逻辑代码，并提供一点配置信息，Answerer会自动为你生成其他所需的代码，构成可运行的程序。生成的是平铺直叙的代码，可读性、可调试性都高于流行框架中基于反射的代码。

不同于IDE的一次性代码生成，Answerer能持续陪伴你的项目。当你修改了代码，Answerer也自动修改代码，与你保持一致。不怕改需求，不怕重构！

那么Answerer会降低码农的价值吗？不，码农节省了实现业务的时间，有更多时间研究技术了！

##Quick Start

Answerer还在开发中，尚未达到产品级别。

目前采用Gradle构建工具，能自动生成基于Spring Boot + Ebean ORM的RESTful service (用户可自行加入网页部分)。未来考虑接入更多框架，任用户挑选使用。

###如何运行:
需要JDK 8。已有1个Demo项目，已包含entity和DTO类。

- 1. 生成REST: 在answerer目录下运行 ./gradlew run -Pargs=update,example,com.example
- 2. 运行: 在example目录下运行 ./gradlew run (请确保8080端口可用)，会构建并启动web服务。
- 3. 检验: 用curl请求新建1个数据 curl -l -H "Content-type: application/json" -X POST -d {} http://localhost:8080/user/new ，浏览器打开http://localhost:8080/user/all 可看到

也可生成新的项目，在answerer目录下运行 ./gradlew run -Pargs=create,$projectName,$basePackage (带有$的项请填一个值)，仿照example写好entity类(DTO可选)，并生成REST。

###特色功能:
- 自动组装数据
- 静态检查的鸭子类型(duck type)

只需调用autowire方法注入任意变量，它们会被自动转换、组装到返回类型中(如果不合适，代码分析器会报告错误)。比Spring的注入还方便！静态检查更可靠！

使用autowire的类要在javadoc中加入$UserFunction字样，然后它的代码就可以这么写了:

```
// User字段为id, email, password, nickname, avatar, brief
// UserView字段为id, name, avatar, brief

String email="a@a.com", password="1234", nickname="Fraga", avatar="", brief="";

User user = Wirer.autowire(email, password, nickname, avatar, brief);
UserView uv = Wirer.autowire(user);
user = Wirer.autowire(uv, email, "password=", "abcd");// "password="能赋予"abcd"变量名
```
注意到两个类的字段有微妙不同，而且生成REST服务需要知道entity和DTO的关系，简单配置一下就解决了(Anwerer会扫描项目里的配置):

```
// Java写配置类，对重构友好(未来可能支持YAML等格式)
import sorra.answerer.api.Config;
public class MyConfig extends Config {
  User user; UserView uv;
  { // 解决字段名的差异
    map(user.nickname, uv.name);
    // 声明Entity和DTO是相关的
    map(user, uv);
  }
}
```

需要提供RESTful CRUD的数据类要在javadoc中加入$EnableRest字样。

###原理:
通过语法分析来理解用户代码，从而自动生成与之适配的代码。相关技术可参考我博客 http://segmentfault.com/blog/sorra

##远期目标

- 自动注入一切数据和关系，无论它来自SQL、NoSQL甚至Micro service
- 懒加载和批量加载
- 提供配方(recipe)平台，第三方框架通过添加配方来接入
- 类似微服务的分布式解决方案
- 类似async-await的协程
- IO对象的RAII

##近期目标
- 嵌入构建步骤
- 支持getter, setter
- 可配置的Unbox
- AOP
- 用户代码和生成代码分离，使code base更干净
- 对TDD友好，业务逻辑直接可测试