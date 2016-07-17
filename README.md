**Answerer正在移植到[Hasta](https://github.com/sorra/hasta)，一个简捷的库，不再依赖代码生成。**
-

Answerer, 即凯尔特神话的神剑Fragarach，能自动出鞘攻击，因此别名Answerer(应答之剑)。

Answerer只是个工具，不会引入依赖，你可以随时抛开它。使用Answerer，你只需编写业务逻辑代码，并提供一点配置信息，Answerer会自动为你生成其他所需的代码，构成可运行的程序。生成的是平铺直叙的代码，可读性、可调试性都高于流行框架中基于反射的代码。

不同于IDE的一次性代码生成，Answerer能持续陪伴你的项目。当你修改了代码，Answerer也自动修改代码，与你保持一致。不怕改需求，不怕重构！

那么Answerer会降低码农的价值吗？不，码农节省了实现业务的时间，有更多时间研究技术了！

Answerer还在开发中，目前采用Gradle构建工具，能自动生成基于Spring Boot + Ebean ORM的RESTful service (用户可自行加入网页部分)。未来打算接入更多框架，任用户挑选使用。

###特色功能:
- 自动装配数据
- 编译时鸭子类型(compile-time duck typing)
- 编译时AOP：程序行为一目了然，支持自定义注解和属性注入，爽

只需调用autowire方法注入任意变量，它们会被自动装配到返回类型中(如果不合适，代码分析器会报告错误)。比Spring的注入还方便！静态检查更可靠！

使用autowire的类要在javadoc中加入`$UserFunction`字样，然后它的代码就可以这么写了:

```
// User字段为id, email, password, nickname, avatar, brief
// UserView字段为id, name, avatar, brief

String email="a@a.com", password="1234", nickname="Fraga", avatar="", brief="";

User user = Wirer.autowire(email, password, nickname, avatar, brief);
UserView uv = Wirer.autowire(user);

// "password=" 能给表达式"abcd"命名
user = Wirer.autowire(uv, email, "password=", "abcd");

// 集合也可以哦
List<User> users = Arrays.asList(user, user);
Collection<UserView> uvs = Wirer.autowire(users);
```
注意到两个类的字段有微妙不同，而且生成REST服务需要知道entity和DTO的关系，简单配置一下就解决了(Anwerer会扫描项目里的配置):

```
// Java写配置类，对重构友好(未来可能支持YAML等格式)
import sorra.answerer.api.Config;
class MyConfig extends Config {
  User user; UserView uv;
  {
    map(user.nickname, uv.name); // 解决字段名的差异
    map(user, uv); // 声明Entity和DTO是相关的
  }
}
```

需要提供RESTful CRUD的数据类要在javadoc中加入`$EnableRest`字样，运行update可自动生成对应的Controller。目前每次update会重新生成Controller，去掉$EnableRest字样可以停止重新生成(autowire不受影响)。

AOP功能介绍 [点这里](https://github.com/sorra/answerer/wiki/AOP)

###Quick start:
需要JDK 8。已有1个Demo项目，已包含entity和DTO类。

- 0. 构建: `./gradlew shadowJar`，jar生成在build/libs/answerer-0.2-all.jar ; Answerer目录下as-config.properties含有配置参数
- 1. 生成REST服务: 在answerer目录下运行 `java -jar build/libs/answerer-0.2-all.jar update`
- 2. 运行: 在example目录下运行 `./gradlew run`(请确保8080端口可用)，会构建并启动web服务。
- 3. 检验: 用curl请求新建1个数据`curl -l -H "Content-type: application/json" -X POST -d {} http://localhost:8080/user/new`，浏览器打开http://localhost:8080/user/all 可看到

也可生成新的项目，在answerer目录下运行`java -jar build/libs/answerer-0.2-all.jar create`，仿照example写好entity类(DTO可选)，并生成REST。

###用法解释
构建后生成了answerer的可执行jar，可以起个短点的名字(如answerer.jar)，以后就在你的项目下执行这个jar。用`java -jar`命令执行jar时，尾部可携带参数`create`或`update`。

在你的项目下创建一个as-config.properties，内容仿照answerer提供的样本。

命令中加上-D参数，如`java -jar -Dkey=value answerer.jar update`，可覆盖as-config.properties的配置。

###工作原理:
通过语义分析来理解用户代码，从而自动生成与之适配的代码。相关技术可参考我博客 http://segmentfault.com/blog/sorra

##即将到来
- 支持getter, setter, constructor
- 对TDD友好，业务逻辑直接可测试

##必然到来
- 自动注入一切数据和关系，无论它来自SQL、NoSQL甚至Micro service
- 懒加载和批量加载
- 类似微服务的分布式解决方案

##可能到来
- 配方(recipes)体系，第三方框架通过添加配方来接入
- SQL DSL
- 协程
- RAII
