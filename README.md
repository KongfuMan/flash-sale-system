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
`reservation-core` 保存共享预留业务，`flash-sale-api` 和 `order-consumer` 分别生成独立应用。
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
再批量查询订单项；建议为 `order_items.order_id` 添加索引。已有数据库未自动修改。
按 ID 查询不存在的订单时返回 HTTP 404。

## 预留订单

### 独立 Redis Cluster 库存原语（实验性）

`InventoryPrimitives` 独立于订单和 `ReservationService`，不会在启动时自动预加载。
`reserve(eventId, eventProductId, userId, idempotencyKey)` 是独立 Java API：ID 为正的 64 位整数，键非空白且最多 128 个字符。
同一活动内相同键和绑定重试返回 `REPLAY`，不同商品或用户返回 `CONFLICT`；即使活动已结束或库存为零也先检查重试。
首次成功返回 `RESERVED`，在同一 hash 中以一次 `HSET` 同时扣减库存并写入 `i:<sha256-hex-of-UTF8-key>` JSON
待处理记录。记录包含版本、绑定、原始幂等键、确定性 requestId（SHA-256，输入为活动 ID、冒号和原始键的 UTF-8）、
`PENDING` 状态和消息载荷；ID 存为字符串。原始键是敏感数据，必须限制 Redis 读取和日志访问。
记录无 TTL、不自动删除，空间会持续增长；未知或损坏记录失败关闭。不同活动允许使用相同键，但 PostgreSQL 的
`orders_idempotency_key_key` 是全局唯一约束，这两种语义不兼容；在设计并解决此冲突之前禁止接入 Kafka 或订单处理。

这些记录只是 Redis hash 字段，不是 Redis Stream；没有 relay 或 Kafka 发布，也没有连接到下述独立消费者。Redis 与数据库不构成
事务，持久化、复制、故障转移、淘汰、外部修改、容量和恢复均需运维设计，Redis 确认写入仍可能丢失或回滚。
当前方案不可用于生产或当作实际订单库存保护，也不保证消息最终送达。

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
字段 `status` 和 `p:<event_product_id>` 一次 Lua 写入，不设置 TTL；已有 key 一律拒绝覆盖。
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
先停止写入并人工协调两个存储，不能在流量运行时重置。此原语仍非生产方案，Kafka 尚未接入。

单元测试：`mvn test -Dtest=InventoryPrimitivesTest -Dsurefire.failIfNoSpecifiedTests=false`。可选集群脚本测试仅在显式指定集群时运行，
使用随机活动 ID，只删除自己成功创建的 key，不访问数据库：
`mvn test -Dtest=InventoryClusterTest -Dsurefire.failIfNoSpecifiedTests=false -Dinventory.test.cluster.nodes=host1:7001,host2:7002,host3:7003`。
使用隔离测试集群和受限凭据；测试可能因异常终止而留下随机 key。

### 独立 Kafka 订单消费者

根目录 `mvn package` 生成 `flash-sale-api/target/flash-sale-api-0.0.1-SNAPSHOT.jar` 和
独立的 `order-consumer/target/order-consumer-0.0.1-SNAPSHOT.jar`。
消费者不启动 HTTP 服务，也不扫描 API controller。它使用 `ReservationService` 创建订单，需要已初始化的
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
数据库生成订单 ID，并使用 `kafka:<message_id>` 作为全局幂等键；其他写入方不得占用 `kafka:` 命名空间。
订单事务提交后才确认 Kafka offset，重复投递通过现有数据库幂等约束处理。
新 topic 和字段不兼容旧的 `order-created-events-v1` 消息；切换前需创建新 topic 并安排旧消息的处理，
不可直接重投旧消息或假定旧 offset 会迁移。
所有错误（包括格式或业务错误）无限重试，永久失败
会阻塞相应分区及同一 consumer 线程上的其他分区，必须监控并人工处理；目前没有死信队列、结果通知或
自动库存补偿。须保证 Kafka 的消息保留时间足够，并在处理前验证目标 topic。

此消费者不包含生产者，也没有与 Redis 库存原语接通。特别是 Redis 活动内幂等范围与数据库全局约束
不一致，且 Redis 预扣和 Kafka 发送之间没有可靠交接；解决这些问题并设计恢复、补偿和对账之前，
不能把它们拼接成生产秒杀流程。

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

### Redis Reservation Admission

**Current implementation warning:** The Redis admission and provisioning workflow described
below is not implemented in the current source tree. `ReservationService` currently writes
directly to PostgreSQL, regardless of `inventory.cache.enabled`; Redis failures do not
produce the fail-closed behavior described below. Do not use the instructions below to
provision or reconcile Redis, or rely on them for inventory safety. There is no bootstrap
command for that design or Kafka handoff. The existing PostgreSQL conditional update and idempotency
constraint remain in place. The separate experimental primitives above have a manual prefill
command, but do not implement the admission design below.

Reservations require Redis first. It is disabled by default, and disabled, unavailable,
missing, expired or invalid state returns 503, not a database fallback. Configure an
existing Redis with `INVENTORY_CACHE_ENABLED=true`, `REDIS_HOST`, `REDIS_PORT` and, if
needed, `REDIS_USERNAME` and `REDIS_PASSWORD` via environment or secret management.
No endpoint is provisioned here; Redis health checks are disabled. Timeouts are 200 ms.
Redis zero rejects with 409 before PostgreSQL; stale low stock can reject valid orders.
PostgreSQL's conditional update remains authoritative. There is no Kafka handoff yet.

Lua stores stock and global SHA-256 idempotency markers in the single persistent hash
`flashsale:{reservation}:state`, in one Redis Cluster slot (a potential hot slot).
Pending and completed markers have no TTL and grow without bound. A pending/unknown
request returns 503; a completed matching request replays its original order ID, and a
different binding returns 409. A failed or ambiguous Redis call or database commit can
leave a pending marker and reduced stock. Finalization happens only after confirmed DB
commit. Only confirmed business rollbacks release admission; existing DB replays restore
the extra Redis decrement atomically while recording the original order ID.

Redis and PostgreSQL are not an atomic transaction. Configure durable Redis persistence,
appropriate replication/failover and no eviction, monitor capacity, and prevent other
clients from modifying this reserved key. Redis data loss, rollback after failover, partial
manual edits or restoring an old snapshot cannot be detected reliably and can violate
idempotency or availability. Stop admissions and reconcile after any such incident; do
not automatically repopulate or reset state while traffic runs. PostgreSQL still prevents
overselling, but cannot make a lost Redis marker safe. Stock changes outside this path,
including administrative changes, require the same controlled reconciliation.

**Operator provisioning and reconciliation:** pause all reservation writers across all
instances, wait for every in-flight request and resolve unknown database commits using
authoritative PostgreSQL records and transaction status. Back up and inspect both stores.
Only once quiescence and database outcomes are proven, rebuild or correct the hash from
the database: `schema` must be `1`, and each `s:<eventId>:<eventProductId>` field is
`allocated_quantity - sold_quantity - reserved_quantity`, a nonnegative decimal integer
at most 999999999999999. Provision missing state manually, without TTL, using a controlled
Redis client. Preserve completed markers, or reconstruct `i:<sha256-hex-of-UTF8-key>` as
`<eventId>:<eventProductId>:<userId>:D:<orderId>` from verified orders. Resolve pending
markers individually against PostgreSQL; clear a stale marker only after proving no
commit can occur and accounting for its inventory. Never clear completed markers without
an explicit idempotency retention policy and verified database reconciliation. Verify the
entire hash, durability and no expiry before resuming writers. If outcomes cannot be
proven, keep affected operations blocked and escalate. `userId` is caller supplied and
unauthenticated, so idempotency replay and ordering are not safe for production use.

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
3. `reservation-core/src/main/java/`：共享预留业务及数据库访问。
4. `message-contract/src/main/java/`：消息契约。
5. `order-consumer/src/main/java/`：独立消费者入口和消息处理。

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
