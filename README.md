# FlashSale

一个 Spring Boot REST API 示例，使用内存数据库，无需外部数据库或 Docker。
项目包含秒杀业务表的 JPA 实体和 Repository，以及读取所有订单的示例接口。
原问候示例的 Flyway 迁移已移除；已有数据库中的表和迁移历史不会自动删除。

## 环境

- JDK 17 或更高版本
- Maven 3.6.3 或更高版本

从仓库根目录构建所有模块，再连接本机 Citus 数据库运行 API：

```bash
mvn verify
SPRING_PROFILES_ACTIVE=dev java -jar flash-sale-api/target/flash-sale-api-0.0.1-SNAPSHOT.jar
```

根 `pom.xml` 是 Maven reactor 父工程：`message-contract` 保存消息契约，
`flash-sale-api` 和 `order-consumer` 分别生成独立应用；数据库订单预留由消费者负责。
根目录运行 `mvn verify` 会构建并测试所有模块。

## 环境配置

`flash-sale-api/src/main/resources/application.properties` 保存 API 所有环境共享的配置（包括默认端口 8081）。
`application-dev.properties`、`application-test.properties` 和 `application-prod.properties`
分别保存对应环境的覆盖配置。

通过 Spring profile 选择环境，例如：

```bash
SPRING_PROFILES_ACTIVE=dev java -jar flash-sale-api/target/flash-sale-api-0.0.1-SNAPSHOT.jar
SPRING_PROFILES_ACTIVE=prod java -jar flash-sale-api/target/flash-sale-api-0.0.1-SNAPSHOT.jar
```

独立消费者使用 `order-consumer/src/main/resources/` 下自己的 `application*.properties`；
修改 API 配置不会自动修改消费者配置，两者应分别检查数据库和环境设置。
Redis Lua 脚本和 Flyway 迁移仅由 `flash-sale-api` 维护；消费者不运行迁移，启动前必须由受控流程完成数据库迁移。

未指定 profile 时使用 H2，且没有订单业务表。共享的 IntelliJ `FlashSale` 运行配置默认选择
`dev` profile。不要将密码或其他密钥提交到配置文件；
应通过环境变量或外部密钥管理系统提供。

开发环境默认连接本机 Docker Citus coordinator：`localhost:5442/postgres`，用户为
`postgres`。可通过 `DB_JDBC_URL`、`DB_USERNAME` 和 `DB_PASSWORD` 覆盖。
当前 Citus schema 尚缺少 `password_hash` 和订单项 ID 序列；使用相关功能前需安全协调迁移。
`dev` profile 默认禁用 Flyway，避免自动修改现有数据库；需要迁移时，请先确认目标数据库，
然后显式启用 `spring.flyway.enabled`。
在现有数据库中启用 Flyway 前，应先检查已有 schema 和 Flyway 历史记录。建议使用独立的开发数据库。

## 生产组件示例

- `entity` 和 `repository` 使用 Spring Data JPA 和 Hibernate 映射秒杀业务表。
- `@RestControllerAdvice` 演示请求验证错误处理。
- Spring Security 允许所有匿名请求并禁用 CSRF 保护。订单接口没有身份认证，仅供演示，不能用于生产数据。
- Actuator 提供健康检查和 Micrometer 指标。默认只公开健康检查；指标端点没有对外开放，应在配置认证和授权后再公开。
- `flash-sale-api/src/main/resources/db/migration/V1__capture_business_schema.sql` 是当前业务表的 PostgreSQL DDL 基线；Flyway 默认禁用，应作为受控部署步骤执行，而不是在应用启动时自动修改数据库。
- `ExternalIntegrationClient` 演示外部 HTTPS 请求、超时和可选的进程内 TTL 缓存；默认禁用且不会在启动时发出请求。通过 `demo.integration.enabled`、`demo.integration.url`、`demo.integration.connect-timeout`、`demo.integration.request-timeout` 和 `demo.integration.cache-ttl` 配置。缓存不是分布式缓存。

## 数据库迁移

`V1__capture_business_schema.sql` 根据现有 coordinator 的业务表生成，包括表、约束和订单 ID
序列；不包含业务数据、测试表或 Citus 扩展配置。当前这些表并未配置 Citus 分布。
新的 V1 基线复用了旧问候示例曾使用的版本号，仅适用于全新数据库。新数据库可通过受控部署步骤执行
Flyway `migrate`；迁移成功后，应使用 `validate` 并在相同的 PostgreSQL/Citus 版本上测试。

本机全新 PostgreSQL 容器可执行以下命令（仅适用于该开发容器）。先从仓库根目录安装 API 依赖的
本地模块；此步骤不执行数据库迁移：

```bash
mvn -pl flash-sale-api -am -DskipTests install
mvn -f flash-sale-api/pom.xml org.flywaydb:flyway-maven-plugin:11.7.2:migrate -Dflyway.locations=filesystem:"$PWD"/flash-sale-api/src/main/resources/db/migration -Dflyway.url=jdbc:postgresql://127.0.0.1:55432/flashsale -Dflyway.user=flashsale -Dflyway.password=flashsale-local-dev
mvn -f flash-sale-api/pom.xml org.flywaydb:flyway-maven-plugin:11.7.2:validate -Dflyway.locations=filesystem:"$PWD"/flash-sale-api/src/main/resources/db/migration -Dflyway.url=jdbc:postgresql://127.0.0.1:55432/flashsale -Dflyway.user=flashsale -Dflyway.password=flashsale-local-dev
```

现有开发数据库已有业务表，**不能直接执行 V1**，否则建表会失败。旧版 V1/V2 和 V3/V4 历史
与新的 V1 不兼容，必须先检查 schema、备份及 `flyway_schema_history`，制定专门的协调策略。
不要盲目 baseline 或 repair，也不要在现有数据库执行 clean。
`dev` profile 当前不启用 Hibernate schema 校验，因为 Citus 尚未完成 schema 更新。

`flash-sale-api/src/main/resources/db/dev/R__seed.sql` 是根据本地 Citus 记录生成的开发环境 Flyway repeatable migration，生产部署默认
不会加载。仅在专用开发数据库、应用写入停止时，通过同时指定 schema 和 dev 两个 Flyway location 执行；脚本包含
显式订单 ID 并调整序列。脚本可重复执行，但 `ON CONFLICT DO NOTHING` 不会修复已有不一致数据。
源数据没有产品库存字段，因此 `total_inventory` 使用 0 占位；原有日期、过期订单和保留库存数量
按快照保留，可能不适合直接测试新订单。种子不包含密码哈希。
V1 包含 `unit_price`、`password_hash` 和订单项 ID 序列，保留其他产品列；此基线仅供全新数据库使用。
已经应用开发种子的数据库后续运行 Flyway 时也应包含 `db/dev` location，否则校验可能因缺失已应用迁移而失败。

```bash
FLYWAY_URL=jdbc:postgresql://localhost:5442/postgres FLYWAY_USER=postgres FLYWAY_PASSWORD=postgres \
  mvn -f flash-sale-api/pom.xml org.flywaydb:flyway-maven-plugin:11.7.2:migrate \
  -Dflyway.locations=filesystem:"$PWD"/flash-sale-api/src/main/resources/db/migration,filesystem:"$PWD"/flash-sale-api/src/main/resources/db/dev
```

### GitLab CI/CD

`.gitlab-ci.yml` 先执行 Maven `verify`，在受保护的默认分支上提供手动生产迁移任务；
Flyway 成功后才发布 API 和消费者 JAR。此流程不启动或部署应用，发布的制品供后续部署流程使用。
在 GitLab 中保护默认分支和 `production` 环境，配置所需审批，并设置受保护、受限环境范围的
CI/CD 变量 `FLYWAY_URL`、`FLYWAY_USER` 和 `FLYWAY_PASSWORD`。密码应设为 masked/hidden，
数据库迁移账户应与应用运行账户分离；runner 必须能够安全访问目标数据库。

首次使用前检查目标 schema 和 Flyway 历史，尤其是旧版 V1/V2 和 V3/V4；不要自动 baseline、repair
或 clean。生产迁移应先在与生产相同的数据库版本和代表性数据上验证，并确保迁移与仍在运行的旧版
应用兼容。当前应用的匿名安全配置不适合生产部署。

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

启动 `dev` profile 并连接已初始化的 PostgreSQL 数据库后，可以请求订单列表：

```bash
curl 'http://localhost:8081/api/v1/orders?page=0&size=20'
curl http://localhost:8081/api/v1/orders/1
```

默认 H2 数据库没有业务表，因此订单接口需要连接已初始化业务表的数据库。
订单按 ID 升序分页，`page` 从 0 开始，`size` 默认 20、最大 100。响应包含 `orders`、
`page`、`size`、`hasNext`，每个订单的 `items` 包含对应订单项。接口先查询订单页，
再批量查询订单项；V1 通过 `order_items.order_id` 上的唯一约束提供所需索引。已有数据库未自动修改。
按 ID 查询不存在的订单时返回 HTTP 404。

## 预留订单

### 独立 Redis Cluster 库存原语（实验性）

`InventoryPrimitives` 用于 API 的实验性 Redis 准入，独立于消费者的数据库 `OrderService`，不会在启动时自动预加载。
`reserve(eventId, eventProductId, userId, idempotencyKey, payload)` 是独立 Java API：ID 为正的 64 位整数，键非空白且最多 128 个字符。
同一活动内相同键和绑定的版本 3 标记重试返回 `REPLAY`，版本 1/2 标记返回 `LEGACY_REPLAY`，不同商品或用户返回 `CONFLICT`；即使活动已结束或库存为零也先检查重试。
首次成功返回 `RESERVED`，在同一 hash 中以一次 `HSET` 同时扣减库存并写入 `outbox:<requestId>` 信封及 `idem:<idempotencyKey>` JSON
待处理记录。版本 3 记录包含活动、商品和用户 ID、原始幂等键、确定性 requestId（SHA-256，输入为活动 ID、冒号和原始键的 UTF-8）
及 `PENDING` 状态；ID 存为字符串，不重复存储 `binding` 和 `message` 对象。版本 1 和 2 记录仍按各自格式验证，但无法确认历史投递，匹配重试需要人工对账。
原始键是敏感数据，必须限制 Redis 读取和日志访问。
版本 3 的结构由 Java `InventoryMarker` record 定义并序列化；Lua 在写入前校验记录与请求一致，重试时校验已存记录。
版本 1 兼容格式仍由 Lua 校验。Java 与 Lua 的字段约定通过 Redis 集群契约测试验证，并非编译时强制保证。
幂等记录无 TTL、不自动删除，空间会持续增长；outbox 在 Kafka ACK 后条件删除。未知或损坏的幂等记录失败关闭。脚本不读取旧 `i:` 或
`idem:<sha256>` 标记；切换前必须停止所有写入并等待在途操作结束，安全迁移和核对旧标记后才能恢复。
旧版写入方无法读取版本 3 记录；升级前必须停止所有旧版写入方并等待在途操作结束，不能混合运行或直接回滚。
不同活动允许使用相同键；Kafka 消息 UUID 由活动 ID 和幂等键确定，消费者使用不带前缀的 UUID 字符串作为全局数据库
幂等键。其他数据库写入方必须避免使用相同键；此设计仍需配合跨系统对账验证。

这些记录只是 Redis hash 字段，不是 Redis Stream。API 写入 outbox，由启用的 API relay 后台 worker 发布；当前方案不可用于生产或当作实际订单库存保护，也不保证消息最终送达。

预加载前必须停止所有涉及该活动的数据库和 Redis 库存写入，等待在途操作完成并核对数据，整个读取和
预加载期间保持静止；数据库读取与 Redis 写入并非原子操作。使用具有数据库只读权限及受限 Redis 权限的
操作员凭据，检查目标环境后手动运行（不启动 HTTP、迁移或 schema 更新）：

演示接口为 `POST /api/v1/operator/events/{eventId}/inventory/prefill`：成功返回 201，已有 key
返回 409，活动不存在、无商品或超过 1000 个商品时返回 400。**当前接口允许匿名访问，仅供演示；**
生产环境必须先配置运营身份认证、授权、网络隔离和审计。调用接口时也必须遵循上述停止写入要求。

```bash
mvn -pl flash-sale-api -am install -DskipTests
SPRING_PROFILES_ACTIVE=dev mvn -f flash-sale-api/pom.xml compile exec:java -Dexec.mainClass=com.webapps.inventorycli.InventoryPrefillCommand -Dexec.args="prefill --event-id 1"
```

dev 的 `REDIS_CLUSTER_NODES` 可覆盖默认的三个集群种子节点；连接使用 Redis Cluster 拓扑发现。
读取 `flash_sale_events` 和 `flash_sale_event_products`，每个活动最多 1000 个商品，库存为
`allocated_quantity - sold_quantity - reserved_quantity`。新 hash 为 `flashsale:inventory:v1:{event:<id>}`，
字段 `status` 和 `prod:<event_product_id>` 一次 Lua 写入，不设置 TTL；已有 key 一律拒绝覆盖。
已有 Redis hash 的旧 `p:` 库存字段不会被新脚本读取；切换前须停止所有写入、迁移并核对库存字段，不能混合运行新旧写入方。
状态是预加载快照，后续数据库状态变化不会同步。只有快照状态为 `RUNNING` 才能扣减；缺 key、缺商品、
非法库存和库存不足均返回明确结果，不会隐式初始化。它不检查活动时间、用户限制或订单，也不提供重建流程。
原有 `--event-id 1` 命令仍是预加载的兼容形式。

活动开始前预加载的 `UPCOMING` 快照不会自动切换。确认目标数据库和 Redis 环境，停止该活动所有数据库及
Redis 写入并等待在途操作完成，将数据库活动状态置为 `RUNNING` 后，在保持写入静止期间显式执行：

```bash
SPRING_PROFILES_ACTIVE=dev mvn -f flash-sale-api/pom.xml compile exec:java -Dexec.mainClass=com.webapps.inventorycli.InventoryPrefillCommand -Dexec.args="activate --event-id 1"
```

激活先读取数据库并要求状态为 `RUNNING`，再通过 Lua 仅将已存在 hash 的 `UPCOMING` 或 `RUNNING` 状态
设为 `RUNNING`，不会创建 key 或重置库存；其他状态、缺失 key、错误类型均失败。数据库读取与 Redis 更新
不具备原子性，必须保持写入静止并核对结果后才能恢复；数据库状态后续变化不会自动同步。清理或重建也必须
先停止写入并人工协调两个存储，不能在流量运行时重置。此原语仍非生产方案。

单元测试：`mvn test -Dtest=InventoryPrimitivesTest -Dsurefire.failIfNoSpecifiedTests=false`。可选集群脚本测试仅在显式指定集群时运行，
使用随机活动 ID，只删除自己成功创建的 key，不访问数据库：
`mvn test -Dtest=InventoryClusterTest -Dsurefire.failIfNoSpecifiedTests=false -Dinventory.test.cluster.nodes=host1:7001,host2:7002,host3:7003`。
使用隔离测试集群和受限凭据；测试可能因异常终止而留下随机 key。
CI 必须对隔离 Redis Cluster 运行以下记录契约测试；`inventory.test.cluster.required=true` 防止未配置集群时静默跳过。
测试检查 Java 序列化的版本 3 记录、Lua 的写入和重试行为以及旧版标记的处理；单独的 record 单元测试不需要 Redis：

```bash
mvn -pl flash-sale-api -am test -Dtest=InventoryMarkerTest,InventoryMarkerPrefixTest -Dsurefire.failIfNoSpecifiedTests=false -Dinventory.test.cluster.required=true -Dinventory.test.cluster.nodes=host1:7001,host2:7002,host3:7003
```

### 独立 Kafka 订单消费者

根目录 `mvn package` 生成 `flash-sale-api/target/flash-sale-api-0.0.1-SNAPSHOT.jar` 和
独立的 `order-consumer/target/order-consumer-0.0.1-SNAPSHOT.jar`。
消费者不启动 HTTP 服务，也不扫描 API controller。它使用 `OrderService` 创建订单，需要已初始化的
PostgreSQL/Citus 业务数据库。示例（先创建 Kafka topic 并检查数据库配置）：

```bash
SPRING_PROFILES_ACTIVE=dev SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
  java -jar order-consumer/target/order-consumer-0.0.1-SNAPSHOT.jar
```

默认 topic 为 `order-requests-v1`，group 为 `reservation-consumer`；可用
`RESERVATION_KAFKA_TOPIC` 和 `RESERVATION_KAFKA_GROUP_ID` 覆盖。topic 自动创建默认关闭。
多个消费者实例使用相同 group 分担分区；并行度受分区数限制。

消息 value 为 UTF-8 JSON，例如：

```json
{"message_id":"8c4eb4f3-860d-4da0-962d-15490e49dc7c","message_type":"ORDER_REQUESTED",
 "schema_version":2,"occurred_at":"2026-11-05T20:36:46.115907+00:00",
 "data":{"flash_sale_event_id":1,"user_id":3,"items":[{"event_product_id":2,"quantity":1}]}}
```

生产者和消费者可共享 `com.webapps.flashsale.contract` 中的消息信封和 payload 类型。
消费者按 `message_type` 和 `schema_version` 显式分发，目前仅接受 `ORDER_REQUESTED` 版本 2；
三个 data ID 都是正的 64 位整数。未知字段、类型或版本会被拒绝。`ORDER_CREATED` 是独立的事实事件，
此消费者不会处理或发布它；模型不会自动保证类型与 payload 匹配。
`OrderRequested` 使用 `items` 列表，每项包含 `event_product_id` 和 `quantity`；该模型本身不执行业务验证。
消费者目前仅接受一个商品且数量为 1。多商品的原子预留、库存、支付和幂等处理尚未实现；
多个商品或其他数量会被拒绝并持续重试，可能阻塞分区。版本 1 消息也会被拒绝；部署前须处理旧 topic 中的消息。
数据库生成订单 ID，并使用不带 `kafka:` 前缀的 `message_id` UUID 字符串作为全局幂等键；其他写入方必须避免键冲突。
订单事务提交后才确认 Kafka offset，重复投递通过现有数据库幂等约束处理。
新 topic 和字段不兼容旧的 `order-created-events-v1` 消息；切换前需创建新 topic 并安排旧消息的处理，
不可直接重投旧消息或假定旧 offset 会迁移。
所有错误（包括格式或业务错误）无限重试，永久失败
会阻塞相应分区及同一 consumer 线程上的其他分区，必须监控并人工处理；目前没有死信队列、结果通知或
自动库存补偿。须保证 Kafka 的消息保留时间足够，并在处理前验证目标 topic。

`POST /api/v1/flash_sale_event/{eventId}/product/{eventProductId}` 使用 `UserId` 和
`Idempotency-Key` 请求头，调用 Redis 库存预留。首次预扣及版本 3 标记的匹配重试返回 202
`{"status":"PENDING"}`，不代表订单已创建或消息已发布。版本 1/2 标记的匹配重试返回 503，必须人工对账，不能推断历史投递结果。Redis 超时的结果可能不明确，不能盲目回补库存。
此流程仅供实验，不要在生产或无法恢复库存的数据上使用。

### Redis Outbox 与 API Relay

API 写入 outbox，不直接发送 Kafka；relay 作为 `flash-sale-api` 内的后台 worker 运行，不再单独构建或部署。
relay 默认关闭；如未启用，新的 outbox 消息会一直保留在 Redis 中，不会自动发布。
成功准入（`RESERVED` 或 `REPLAY`）后，请求线程只向容量有限的内存队列投递一个尽力而为的提示，不等待 Kafka；
队列满、提示丢失或 relay 失败时，周期性全量扫描仍会补发，正确性不依赖提示。

每次新的成功预扣在同一活动 hash 内，以单个 `HSET` 同时写入递减后的 `prod:<eventProductId>`、
持久的 `idem:<idempotencyKey>` 标记和 `outbox:<requestId>`。outbox 的 value 是完整的 Kafka 消息信封，
而不是仅有标记或指针；`requestId` 由活动 ID 和原始幂等键确定。匹配的版本 3 重试返回已接受，
不再次扣库存或创建新消息；接受只表示准入，不表示 Kafka 已投递或订单已创建。绑定不匹配返回冲突。
标记保留以支持幂等，即使已确认的 outbox 字段被删除；不要把原始键或信封暴露给不受信任的客户端。

relay worker 首先处理 admission 提示，只读取对应 `outbox:<requestId>` 的已存值并复用同一发布路径；
若在 `relay.poll-ms` 内没有提示，则扫描 Redis Cluster 当前可用 master 上的活动 hash。两条路径都校验 outbox 信封并发布
到消费者使用的 Kafka topic，以消息 UUID 为 Kafka key。收到 broker 确认后，以原子比较删除方式删除仍与已发送 value 完全相同的
outbox 字段；发送失败、超时或确认结果不明时保留以便重试。

这只能在 Redis 已确认数据不会丢失或回滚的前提下提供至少一次交付，不是 Redis 与 Kafka 的分布式事务，
也不是恰好一次。broker 确认后、删除前崩溃，超时但实际发送成功，多个启用 relay 的 API 实例并发或扫描重复均可能产生重复；
消费者必须用稳定的消息 ID 和数据库唯一约束幂等处理。Kafka 的确认和持久化配置、消费者事务、消息保留时间
以及 Redis 持久化、复制和故障转移策略都需要独立验证；Redis ACK 本身不保证故障后数据仍存在。Redis 丢失
或回滚、淘汰、过期、容量耗尽或外部修改会破坏库存、幂等或交付，必须停止准入并人工核对，不能承诺自动恢复。

集群扫描不是快照：SCAN 可重复或遗漏，故障转移、节点不可达和 resharding 可使一次遍历错过字段；需要持续
扫描所有当前 master、刷新拓扑并在拓扑稳定后重复扫描，不能以单次空扫描证明队列已清空。扫描成本、大活动
hash、积压、损坏信封、Kafka 不可用及永久消费失败都需监控和人工处理；relay 没有指标或 metrics 端点，仅记录警告日志，也没有队列深度指标、租约、重试上限、死信队列、
告警、清理或对账工具。限制 Redis 权限、禁止驱逐和不受控写入，规划内存、备份与恢复，持续观察积压和投递延迟。

迁移时先停止所有 API 实例以及新旧库存写入方，等待在途操作结束并核对 Redis、Kafka 和数据库。历史
`idem:` 标记没有可靠的完整 outbox 信封，不会自动重放，也不能据标记推断是否曾发布或创建订单；逐条人工
对账并处理不确定结果，禁止盲目补发或回补库存。保留已验证的幂等状态，协调旧字段格式和库存迁移，完成核验后
才启用新写入方和 relay；不要混合运行旧的直接发布写入方或直接回滚。现有 prefill 对已存在的 key 一律拒绝，
不能用它覆盖已有 hash 或作为迁移、重建方案；任何人工重建都需在静止期间备份、核对并明确处理未完成请求。

relay worker 使用 API 的 Redis Cluster 连接配置和 Kafka producer 配置，topic 应与消费者一致。启用前必须配置 Redis Cluster
和 Kafka，并确认 broker 的副本数、`min.insync.replicas` 和持久化策略。启用 relay 时会检查 producer 使用
`acks=all`、幂等发送和字符串序列化器，否则启动失败。

| 环境变量 | 默认值 | 用途 |
| --- | --- | --- |
| `RELAY_ENABLED` | `false` | 显式启用后台 relay worker |
| `RELAY_POLL_MS` | `1000` | 无 admission 提示时的最大扫描等待时间，毫秒 |
| `RELAY_TIMEOUT_MS` | `5000` | 等待 Kafka ACK 的超时时间，毫秒 |
| `RELAY_TRIGGER_QUEUE_CAPACITY` | `10000` | admission 提示队列容量；满时丢弃并依赖扫描 |
| `RELAY_TOPIC` | `order-requests-v1` | Kafka 发布 topic |
| `REDIS_CLUSTER_NODES` | 参见 dev 配置 | Redis Cluster 种子节点 |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Spring 默认值 | Kafka bootstrap servers |

例如，在已配置集群与 topic 的开发环境中启动 API：

```bash
SPRING_PROFILES_ACTIVE=dev RELAY_ENABLED=true REDIS_CLUSTER_NODES=localhost:7001,localhost:7002,localhost:7003 \
  SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092 java -jar flash-sale-api/target/flash-sale-api-0.0.1-SNAPSHOT.jar
```

多个 API 实例启用时各自运行任务，没有跨实例租约或协调，会重复扫描和可能重复发布；应规划实例配置并保证消费者幂等。
凭据应通过安全的环境或密钥管理系统提供。relay 本身没有专用指标或 metrics 端点，不能依赖 API 的通用 Actuator 指标监控 relay 积压或投递。

```bash
curl -X POST http://localhost:8081/api/v1/flash_sale_event/1/product/2 \
  -H 'UserId: 3' -H 'Idempotency-Key: unique-request-key'
```

事件 ID、活动商品 ID（`event_product_id`，不是 `product_id`）和用户 ID 必须为正整数；幂等键不能为空白且最多
128 个字符。冲突、无库存或活动未运行返回 409，商品缺失返回 404，输入无效返回 400，库存不可用返回 503。
接口不校验用户是否存在，也不执行每用户购买限制；请求头没有身份认证或授权。Redis 超时的结果可能不明确，
不能假定预扣未发生。独立 Kafka 消费者的数据库预订逻辑不受此 API 更改影响。

## Simulated Payment

`POST /api/v1/orders/{orderId}/payments` simulates a successful payment; it does not contact a payment provider or transfer money. For example:

```json
{"paymentId": 123, "transactionId": "unique-transaction", "paymentMethod": "demo"}
```

`paymentId` is a caller-supplied positive 64-bit integer; `transactionId` is required, nonblank and at most 128 characters. `paymentMethod` is optional and at most 30 characters. A successful request returns `201` with payment ID, order ID, transaction ID, amount and `SUCCESS` status. Unknown orders return `404`; expired or non-pending orders, missing reserved inventory, and duplicate payment or transaction IDs return `409`. Repeating a successful request is rejected, not replayed. Invalid input returns `400`.

The amount is the **current** `flash_sale_event_products.flash_price` when inventory is updated, not a price captured at reservation: it can change between reservation and payment. The transaction locks the order, checks expiry using database time, moves one reserved unit to sold, records the payment and marks the order paid atomically. It requires the existing PostgreSQL-compatible business schema and uniqueness constraints; no schema or migration is created.

**This endpoint is public and has no authentication or authorization. Anyone who can reach it can mark an eligible order paid without paying. Do not expose it publicly or use it with production data.**

响应示例：

```json
{"status":"UP"}
```

如果端口 8081 已被占用，可指定其他端口：

```bash
java -jar flash-sale-api/target/flash-sale-api-0.0.1-SNAPSHOT.jar --server.port=8082
```

运行测试：

```bash
mvn test
```

`ReservationPostgresTest` 默认跳过。要运行它，必须指定一个全新、空的临时 PostgreSQL 数据库；
该测试会执行迁移、写入数据并创建触发器，不能用于现有开发或生产数据库：

```bash
mvn test -Dtest=ReservationPostgresTest -Dsurefire.failIfNoSpecifiedTests=false \
  -Dreservation.test.url=jdbc:postgresql://localhost:PORT/postgres \
  -Dreservation.test.user=postgres -Dreservation.test.password=disposable
```

每次运行需要新的空数据库。测试会在迁移前检查 `public` schema 是否为空。

`RedisAdmissionIntegrationTest` is skipped unless `reservation.redis.disposable.port` is
set to the port of a new, empty disposable Redis on localhost. It writes reservation state
and must never point at an existing Redis instance. For example, start a disposable
container with a loopback-only dynamically assigned port, inspect its port with
`docker port`, and run `mvn test -Dtest=RedisAdmissionIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false
-Dreservation.redis.disposable.port=PORT`; stop the container afterward. The legacy
PostgreSQL integration test also needs an explicitly provisioned Redis admission state
before it can exercise the reservation endpoint.

## 阅读顺序

1. 根 `pom.xml`：reactor 模块；各模块的 `pom.xml` 定义各自依赖。
2. `flash-sale-api/src/main/java/`：API 应用入口、controller 和配置。
3. `message-contract/src/main/java/`：消息契约。
4. `order-consumer/src/main/java/`：独立消费者、消息处理和数据库预留服务。
5. `flash-sale-api/src/main/java/com/webapps/flashsale/relay/`：Redis outbox 扫描和 Kafka 发布任务。

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
