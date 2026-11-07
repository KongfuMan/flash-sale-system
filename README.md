# FlashSale

一个 Spring Boot REST API 示例，使用内存数据库，无需外部数据库或 Docker。
项目包含秒杀业务表的 JPA 实体和 Repository，以及读取所有订单的示例接口。
原问候示例的 Flyway 迁移已移除；已有数据库中的表和迁移历史不会自动删除。

## 环境

- JDK 17 或更高版本
- Maven 3.6.3 或更高版本

连接本机 Citus 数据库运行：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## 环境配置

`src/main/resources/application.properties` 保存所有环境共享的配置（包括默认端口 8081）。
`application-dev.properties`、`application-test.properties` 和 `application-prod.properties`
分别保存对应环境的覆盖配置。

通过 Spring profile 选择环境，例如：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
SPRING_PROFILES_ACTIVE=prod java -jar target/flashsale-0.0.1-SNAPSHOT.jar
```

未指定 profile 时使用 H2，且没有订单业务表。共享的 IntelliJ `FlashSale` 运行配置默认选择
`dev` profile。不要将密码或其他密钥提交到配置文件；
应通过环境变量或外部密钥管理系统提供。

开发环境默认连接本机 Docker Citus coordinator：`localhost:5442/postgres`，用户为
`postgres`。可通过 `CITUS_JDBC_URL`、`CITUS_USERNAME` 和 `CITUS_PASSWORD` 覆盖。
`dev` profile 默认禁用 Flyway，避免自动修改现有数据库；需要迁移时，请先确认目标数据库，
然后显式启用 `spring.flyway.enabled`。
在现有数据库中启用 Flyway 前，应先检查已有 schema 和 Flyway 历史记录。建议使用独立的开发数据库。

## 生产组件示例

- `entity` 和 `repository` 使用 Spring Data JPA 和 Hibernate 映射秒杀业务表。
- `@RestControllerAdvice` 演示请求验证错误处理。
- Spring Security 允许所有匿名请求并禁用 CSRF 保护。订单接口没有身份认证，仅供演示，不能用于生产数据。
- Actuator 提供健康检查和 Micrometer 指标。默认只公开健康检查；指标端点没有对外开放，应在配置认证和授权后再公开。
- 项目当前没有 Flyway 迁移；业务表目前只存在于外部数据库。真实部署需要配置持久数据库并管理对应数据库的迁移。
- `ExternalIntegrationClient` 演示外部 HTTPS 请求、超时和可选的进程内 TTL 缓存；默认禁用且不会在启动时发出请求。通过 `demo.integration.enabled`、`demo.integration.url`、`demo.integration.connect-timeout`、`demo.integration.request-timeout` 和 `demo.integration.cache-ttl` 配置。缓存不是分布式缓存。

## Grafana 监控

`dev` profile 在 `http://localhost:8081/actuator/prometheus` 公开 Micrometer 指标。
对于运行在 Docker Desktop 中的 Prometheus，可在其 `scrape_configs` 中添加：

```yaml
  - job_name: flashsale
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["host.docker.internal:8081"]
```

重新加载 Prometheus 配置后，在 Grafana 导入 `monitoring/grafana/flashsale-dashboard.json`，
选择对应的 Prometheus 数据源。dashboard 包含 HTTP 请求速率、错误率、平均延迟、JVM、CPU
连接池和 Tomcat HTTP 工作线程指标。HTTP 指标需要请求流量，速率图需要至少两次抓取。
订单列表延迟图展示平均值、近似 p95/p99 和采样的滚动最大值；这些值基于查询时间窗口，
与压测工具计算的整次运行结果可能不同。直方图会增加指标时间序列数量。
当前 Spring Security 允许匿名访问，指标端点仅在 dev 中默认公开；其他环境应保护管理端点。

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
curl 'http://localhost:8081/api/v1/orders?page=0&size=20'
curl http://localhost:8081/api/v1/orders/1
```

默认 H2 数据库没有业务表，因此订单接口需要连接已初始化业务表的数据库。
订单按 ID 升序分页，`page` 从 0 开始，`size` 默认 20、最大 100。响应包含 `orders`、
`page`、`size`、`hasNext`，每个订单的 `items` 包含对应订单项。接口先查询订单页，
再批量查询订单项；建议为 `order_items.order_id` 添加索引。已有数据库未自动修改。
按 ID 查询不存在的订单时返回 HTTP 404。

## 预留订单

`POST /api/v1/flash_sale_event/{eventId}/product/{eventProductId}` 接收 JSON：

```json
{"userId": 1, "idempotencyKey": "unique-request-key"}
```

事件 ID、活动商品 ID（`flash_sale_event_products.event_product_id`，不是 `product_id`）和用户 ID 必须为正整数，用户必须存在；幂等键不能为空白且最多 128 个字符。
成功创建返回 `201` 和 `{"orderId": 123}`；相同幂等键和相同事件、活动商品、用户重试返回
`200` 和原订单 ID。不同请求复用幂等键、事件未处于 `RUNNING`、活动商品不属于事件或库存不足返回
`409`；输入无效返回 `400`。预留一个单位，订单状态为 `PENDING_PAYMENT`，30 分钟后过期。

此接口使用 PostgreSQL SQL 和已有业务表及 `orders_idempotency_key_key` 唯一约束；默认 H2
环境不支持此接口。它不执行迁移，也不创建订单项、支付或自动过期释放库存的任务，且**不执行每用户购买限制**。
`userId` 由调用方提供，目前没有认证或授权，不能安全地用于生产。事件是否开放仅根据 `RUNNING`
状态判断，不检查开始和结束时间。

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
