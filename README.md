# FlashSale

一个 Spring Boot REST API 示例，使用内存数据库，无需外部数据库或 Docker。
项目包含秒杀业务表的 JPA 实体和 Repository，以及读取所有订单的示例接口。
历史 Flyway 迁移仍保留 `greeting_template` 表，以兼容已有迁移记录。

## 环境

- JDK 17 或更高版本
- Maven 3.6.3 或更高版本

在本目录运行：

```bash
mvn spring-boot:run
```

## 环境配置

`src/main/resources/application.properties` 保存所有环境共享的配置（包括默认端口 8081）。
`application-dev.properties`、`application-test.properties` 和 `application-prod.properties`
分别保存对应环境的覆盖配置；它们目前没有额外设置。

通过 Spring profile 选择环境，例如：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
SPRING_PROFILES_ACTIVE=prod java -jar target/flashsale-0.0.1-SNAPSHOT.jar
```

未指定 profile 时只使用共享配置。在 IntelliJ 的运行配置中可通过环境变量
`SPRING_PROFILES_ACTIVE=dev` 选择开发环境。不要将密码或其他密钥提交到配置文件；
应通过环境变量或外部密钥管理系统提供。

开发环境默认连接本机 Docker Citus coordinator：`localhost:5442/postgres`，用户为
`postgres`。可通过 `CITUS_JDBC_URL`、`CITUS_USERNAME` 和 `CITUS_PASSWORD` 覆盖。
`dev` profile 默认禁用 Flyway，避免自动修改现有数据库；需要迁移时，请先确认目标数据库，
然后显式启用 `spring.flyway.enabled`。
在现有数据库中启用 Flyway 前，应先检查已有 schema 和 Flyway 历史记录。建议使用独立的开发数据库。

## 生产组件示例

- `entity` 和 `repository` 使用 Spring Data JPA 和 Hibernate 映射秒杀业务表。
- `@RestControllerAdvice` 演示请求验证错误处理。
- Spring Security 允许匿名 GET/HEAD 访问 `/actuator/health` 和 `/api/v1/orders`，其他请求被拒绝。订单接口返回所有订单，未分页且没有身份认证，仅供演示，不能用于生产数据。
- Actuator 提供健康检查和 Micrometer 指标。默认只公开健康检查；指标端点没有对外开放，应在配置认证和授权后再公开。
- 默认环境中 Flyway 对临时 H2 内存数据库执行历史迁移；业务表目前只存在于外部数据库。真实部署需要配置持久数据库并验证对应数据库的迁移。
- `ExternalIntegrationClient` 演示外部 HTTPS 请求、超时和可选的进程内 TTL 缓存；默认禁用且不会在启动时发出请求。通过 `demo.integration.enabled`、`demo.integration.url`、`demo.integration.connect-timeout`、`demo.integration.request-timeout` 和 `demo.integration.cache-ttl` 配置。缓存不是分布式缓存。

## Bean Discovery vs Bean Definition

**Bean Discovery**指 Spring 找到需要注册的组件的一种方式。例如，
组件扫描会查找扫描范围内带有 `@Component` 及其派生注解的类。
本项目的 `@SpringBootApplication` 默认从 `com.webapps.flashsale` 包开始扫描，
因此可以发现该包及其子包中的 Spring 组件。

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

例如，`@RestController` 是一种组件注解，因此下面的示例 Controller
可以通过扫描注册：

```java
@RestController
public class ExampleController {
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
curl http://localhost:8081/actuator/health
```

启动 `dev` profile 并连接现有 Citus 数据库后，可以请求订单列表：

```bash
curl http://localhost:8081/api/v1/orders
```

默认 H2 数据库没有业务表，因此订单接口需要连接已初始化业务表的数据库。

响应示例：

```json
{"status":"UP"}
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
2. `MainApplication.java`：`@SpringBootApplication` 和应用入口。
3. `entity/`：Hibernate 映射的业务表实体。
4. `repository/`：Spring Data JPA 数据访问接口。
5. `BusinessEntityMappingTest.java`：验证实体映射。

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
