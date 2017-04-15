**Answerer's core is migrated to [Hasta](https://github.com/sorra/hasta)，a simple library getting rid of code generation**
-

[中文(Chinese)](https://github.com/sorra/answerer/blob/master/README.md)

Answerer is the Celtic mythical sword Fragarach which can attack automatically.

Answerer is just a tool without runtime dependencies and won't disturb your project。With Answerer, you can focus on business logics. And with a few configurations, Answerer generates code to give you a runnable application。The generated codes are straight-forward, readable and debuggable.

Different from the disposable code generated by IDE, Answerer can keep up with your project continuously. When you modify code, Answerer keeps consistent with your modifications. Not afraid of changing requirements or refactoring!

Since it is so, does Answerer devalue developers? No, developers save time in implementing functionality and have more time to make technologies better!

Answerer is under development but its core is ready for use. Currently its core can wire data and its web extension can generate RESTful services.

###Features:
- Wire data objects automatically
- Compile-time duck typing
- Compile-time AOP：straight-forward and supports custom annotations and property injection

Call the `autowire` method to inject any variable, and it will be wired into returned types automatically (errors will be reported if you injected improper variables). This is more reliable and convenient than Spring.

Your class using `autowire` needs to add `$UserFunction` to Javadoc，and you can write code like this:

```
// User has id, email, password, nickname, avatar, brief
// UserView has id, name, avatar, brief

String email="a@a.com", password="1234", nickname="Fraga", avatar="", brief="";

User user = Wirer.autowire(email, password, nickname, avatar, brief);
UserView uv = Wirer.autowire(user);

// "password=" names the value "abcd"
user = Wirer.autowire(uv, email, "password=", "abcd");

// Collections are also OK
List<User> users = Arrays.asList(user, user);
Collection<UserView> uvs = Wirer.autowire(users);
```


Note that there is a small difference of property name between two classes. What's more, to generate RESTful services it should know the relationship of entity and DTO. So you need to write a few simple configurations in your source folder like this:

```
// Written in Java which is refactorable (and YAML is welcome in the future)
import sorra.answerer.api.Config;
class MyConfig extends Config {
  User user; UserView uv;
  {
    map(user.nickname, uv.name); // Relates the property names
    map(user, uv); // Relates the entitiy User and the DTO UserView
  }
}
```

If you want to generate RESTful CRUD from a data class, you need to add `$EnableRest` to Javadoc and run an update command to generate web controllers.

Currently controllers are overwritten at every time you run update, but you can remove `$EnableRest` to avoid updating controllers (while wiring still works).

AOP功能介绍 [点这里](https://github.com/sorra/answerer/wiki/AOP)

###Quick start:
Requires JDK 8. There's a demo contain entity and DTO.

- 0. Build: `./gradlew shadowJar`，the jar is created at build/libs/answerer-0.2-all.jar ; the file `as-config.properties`  contains configuration parameters.
- 1. Generate REST: run `java -jar build/libs/answerer-0.2-all.jar update` in Answerer folder.
- 2. Run: run `./gradlew run` in example folder. This builds and starts the example web service.
- 3. Verify: create data by `curl -l -H "Content-type: application/json" -X POST -d {} http://localhost:8080/user/new`, and open http://localhost:8080/user/all in browser to see the data.

To generate a new project, run `java -jar build/libs/answerer-0.2-all.jar create` in Answerer folder，then write entity or DTO similar to example, and run update.

###Explanation
The built jar accepts command like `create` or `update`.

Create a file `as-config.properties` in your existing project folder, similar to example.

You can add -D parameter like `java -jar -Dkey=value answerer.jar update` to override the configurations in `as-config.properties`.

###How it works:
Understand user code by syntactical and semantic analysis so that can generate code to cooperate with user code。My Chinese blog explains the technology in detail: https://www.qingjingjie.com/blogs/2

##Coming features
- support constructors with parameters
- TDD friendly design

##Features in roadmap
- Inject any data or relationship automatically，no matter it is from SQL, NoSQL or even Micro-services.
- Lazy loading and Batch loading
- Distributed system solution similar to Micro-services

##Features to be discussed
- A recipes system supporting various third-party frameworks
- SQL DSL
- Corountine
- RAII