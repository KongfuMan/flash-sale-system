# Spring Boot Web Demo

一个最小的 Spring Boot REST API 示例，不依赖数据库或 Docker。

## 环境

- JDK 17 或更高版本
- Maven 3.6.3 或更高版本

在本目录运行：

```bash
mvn spring-boot:run
```

## Bean Discovery vs Bean Definition

**Bean Discovery**指 Spring 找到需要注册的组件的一种方式。例如，
组件扫描会查找扫描范围内带有 `@Component` 及其派生注解的类。
本项目的 `@SpringBootApplication` 默认从 `com.example.demo` 包开始扫描，
因此可以发现带有 `@RestController` 的 `GreetingController`。

**Bean Definition（定义）**是 Spring 容器中描述如何创建和管理一个 Bean 的配置，
例如 Bean 的类型、名称、作用域，以及创建方式。组件扫描发现一个类后，Spring
会为它注册 Bean Definition；`@Bean` 方法也可以提供 Bean Definition。
Spring 随后根据这些定义创建实例并注入依赖。

两者不是互斥的替代方案：**扫描是发现和注册 Bean Definition 的一种途径**。
除扫描和 `@Bean` 之外，Spring 还可以通过导入配置、自动配置等方式注册定义。

```text
扫描 @Component 类 ─┐
                   ├─> 注册 Bean Definition ─> 创建和管理 Bean
处理 @Bean 方法 ────┘
```

## @Component vs @Bean

| | `@Component` | `@Bean` |
| --- | --- | --- |
| 标注位置 | 类上 | 配置类的方法上 |
| 注册方式 | 通常通过组件扫描发现类 | Spring 处理配置类中的工厂方法 |
| 创建方式 | Spring 根据类的定义构造实例 | 方法负责创建并返回实例 |
| 常见用途 | 自己编写、适合交给 Spring 构造的类 | 第三方类，或需要自定义创建过程的对象 |

例如，本项目中的 `@RestController` 是一种组件注解，因此下面的 Controller
可以通过扫描注册：

```java
@RestController
public class GreetingController {
    // ...
}
```

如果要将一个无法自行添加注解的类交给 Spring 管理，可以使用 `@Bean`：

```java
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
```

上面的 `AppConfig` 本身也必须被 Spring 发现或导入；`@Bean` 方法才会被处理。
这两个示例都是 Spring 管理的 Bean，可以通过构造函数注入到其他组件中。
默认情况下，Bean 是容器内的单例，但可通过作用域配置改变。

另开终端请求接口：

```bash
curl http://localhost:8081/api/greeting
curl 'http://localhost:8081/api/greeting?name=Charles'
```

响应示例：

```json
{"message":"Hello, Charles!"}
```

如果端口 8081 已被占用，可指定其他端口：

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8082
```

运行测试：

```bash
mvn test
```

## 阅读顺序

1. `pom.xml`：Spring Boot Web 和测试依赖。
2. `DemoApplication.java`：`@SpringBootApplication` 和应用入口。
3. `GreetingController.java`：`@RestController`、GET 路由、查询参数和 JSON 响应。
4. `GreetingControllerTest.java`：使用 `MockMvc` 验证 HTTP 路由和响应。

`@RestController` 的返回值会由 Spring MVC 序列化为 JSON；内嵌的
Tomcat 负责接收 HTTP 请求。服务默认监听 8081 端口。

## Spring Bean 注解速览

```text
                    SPRING BEAN ANNOTATIONS
                              |
          +-------------------+-------------------+
          |                   |                   |
     Bean Discovery      Bean Definition    Dependency Injection
          |                   |                   |
     @Component             @Bean              @Autowired
          |                   |                @Qualifier
          |               @Configuration       @Primary
          |                   |                @Value
          |                   |                @Lazy
          |                   |
          |               @Import
          |               @Conditional
          |
          +-- @Service
          |
          +-- @Repository
          |
          +-- @Controller
          |      |
          |      +-- @RestController
          |
          +-- @Configuration
          |
          +-- Custom stereotype annotations


                    BEAN MANAGEMENT
                          |
          +---------------+---------------+
          |               |               |
       Scope          Lifecycle       Conditions
          |               |               |
        @Scope       @PostConstruct    @Conditional
        @Lazy        @PreDestroy       @Profile
                     @DependsOn        @ConditionalOnBean
                                       @ConditionalOnMissingBean
                                       @ConditionalOnClass
                                       @ConditionalOnProperty
```
