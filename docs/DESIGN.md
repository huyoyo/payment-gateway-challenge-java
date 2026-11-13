# 支付网关多模块设计（OpenAPI-first + Jersey 2.x）

| 项目 | 内容 |
| --- | --- |
| 版本 | v6.0（Operation 路由 + public builders + connector-api 错误 advice） |
| 构建 | Maven 3.9 + Maven Wrapper，Spring Boot 3.1.5，Java 17 |
| 模块 | `gateway-web` / `gateway-app` / `gateway-connector` / `connector-api` / `connector-bank` |
| 契约 | `gateway-app/src/main/resources/openapi/payment-gateway.yaml`、`connector-bank/src/main/resources/openapi/bank-simulator.yaml` |
| 需求来源 | [Checkout.com Engineering Assessment](https://github.com/cko-recruitment/) |

---

## 1. 目标与范围

### 1.1 功能

1. 商户通过 `POST /payments` 提交支付；`bankId` 位于请求 body。
2. 网关校验后按 `bankId` 路由到对应 connector，调用收单银行模拟器。
3. 返回 `Authorized`、`Declined` 或 `Rejected`。
4. 商户通过 `GET /payment/{id}` 查询已创建支付。
5. 新增银行渠道只需增加 connector 子模块，app/operation/controller 不变。
6. 使用 `requestKey` 对重试请求幂等：同一 key + 同一 payload 不重复调用银行。

### 1.2 非功能目标

| 目标 | 落点 |
| --- | --- |
| 契约一致性 | 公共 API 由 OpenAPI YAML 定义，`openapi-generator-maven-plugin` 生成接口与模型，Controller 实现生成接口 |
| 请求校验 | OpenAPI schema → Bean Validation 注解；`MethodArgumentNotValidException` 统一映射为错误响应 |
| 银行契约 | bank-simulator 也用 OpenAPI YAML 定义，生成 Jackson POJO |
| 可维护性 | Maven 模块边界、`IPaymentProcessor` SPI、Operation 路由、public builder 与银行 builder/client 分离 |
| 可扩展性 | 新银行 = 新 connector 子模块实现 SPI；Spring 自动发现 Bean |
| 可用性 | Jersey client 超时、错误分类、健康探针、关联 ID |
| 幂等性 | `requestKey` 原子占位、completed replay、failed replay、payload 指纹 |
| 安全 | 全卡号/CVV 不落库、不写日志；只返回后四位 |

### 1.3 不做

- 3DS/SCA、退款、捕获、对账文件、Webhook。
- 真实数据库、分布式事务、消息队列。
- 商户认证/授权、限流网关。
- 超过 3 个币种的扩展（当前仅 GBP/USD/EUR）。
- Luhn、生产级持久化幂等表/分布式锁与对账（当前为单实例内存实现，见 5.1）。

---

## 2. Maven 模块与依赖

### 2.1 结构

```text
payment-gateway (pom)
├── gateway-app (jar)
│   ├── src/main/resources/openapi/payment-gateway.yaml
│   └── target/generated-sources/openapi (generated PaymentsApi + models)
├── gateway-web (jar + Boot repackage)
└── gateway-connector (pom)
    ├── connector-api (jar: IPaymentProcessor, PaymentCommand/Outcome, errors)
    └── connector-bank (jar: builders, Jersey client, IPaymentProcessor impl)
        ├── src/main/resources/openapi/bank-simulator.yaml
        └── target/generated-sources/openapi (generated bank POJOs)
```

依赖：

```text
gateway-web ──▶ gateway-app ──▶ connector-api
     └──▶ connector-bank (runtime) ──▶ connector-api

gateway-connector (pom) ──▶ connector-api / connector-bank
```

| 模块 | 打包 | 关键依赖 | 职责 |
| --- | --- | --- | --- |
| `gateway-web` | jar + Boot repackage | app、connector-bank (runtime)、starter-web、actuator、springdoc | 启动类、RequestId、OpenAPI YAML 静态服务、Swagger UI |
| `gateway-app` | jar | connector-api、starter-web、starter-validation、OpenAPI Generator | Controller（实现生成接口）、Operation（perform + bankId 路由）、request/response builder、注册表、校验、公共模型（生成）、Repository |
| `gateway-connector` | pom | — | connector 聚合父模块 |
| `connector-api` | jar | spring-webmvc（provided） | `IPaymentProcessor`、`PaymentCommand`、`PaymentOutcome`、connector 异常、共享 `GlobalExceptionHandler`/错误模型 |
| `connector-bank` | jar | connector-api、Jersey 2.35、Jackson、Validation | OpenAPI 生成 POJO、request/response builder、Jersey client、SPI 实现 |

### 2.2 依赖规则

1. `gateway-app` 不依赖任何具体 connector，只依赖 `connector-api`。
2. `connector-bank` 不依赖 app/web；实现 `IPaymentProcessor`。
3. 只有 `connector-bank` 知道银行字段名、`MM/YYYY` 和 `POST /payments`。
4. `gateway-web` 是 composition root，负责把实际启用的 connector 放入运行时。
5. OpenAPI 生成的源码不提交；由 Maven `generate-sources` 生成。

### 2.3 新银行渠道

1. 在 `gateway-connector/` 新增 `connector-acme` 子模块；
2. 依赖 `connector-api`；
3. 实现 `IPaymentProcessor`，提供该银行的 OpenAPI YAML、生成 POJO、builder/client；
4. 在 `gateway-connector/pom.xml` 的 `<modules>` 注册；
5. 在 `gateway-web` 增加该 connector 的 runtime 依赖。

app 的 Controller/Operation/注册表无需修改。

---

## 3. OpenAPI-first 公共 API

### 3.1 契约文件

`gateway-app/src/main/resources/openapi/payment-gateway.yaml`：

- `POST /payments` → `CreatePaymentRequest` → `PaymentResponse` / `ErrorResponse`
- `GET /payment/{id}` → `PaymentResponse` / `ErrorResponse`
- 字段使用 **camelCase**，请求结构参考 Checkout.com Payments API：
  - `bankId`、`requestKey`
  - `source`（discriminator：`CREDIT_CARD` / `WALLET`）
  - `amount`（`value` / `currency`）
  - `softDescriptor`、`merchantAccount`、`paymentContext`
  - `paymentContext.processing`（discriminator：`CardProcessing` / `WalletProcessing`）
  - 响应：`id`、`amount`、`bankId`、`status`、`source`、`processedOn`、`processing`
- `bankId` 是 body 必填字段，不再使用 `X-Bank-Id` header。

### 3.2 Swagger/OpenAPI Generator 插件

`gateway-app/pom.xml` 使用 `openapi-generator-maven-plugin:7.0.1`：

```xml
<generatorName>spring</generatorName>
<configOptions>
  <useSpringBoot3>true</useSpringBoot3>
  <interfaceOnly>true</interfaceOnly>
  <skipDefaultInterface>true</skipDefaultInterface>
  <useBeanValidation>true</useBeanValidation>
  <openApiNullable>false</openApiNullable>
</configOptions>
```

生成：

- `PaymentsApi`（Controller 实现它，路径/方法/参数来自 YAML）；
- `CreatePaymentRequest`、`PaymentResponse`、`ErrorResponse`、`FieldViolation`；
- `Source` 多态接口 + `CardSource` / `WalletSource`；
- `Processing` 多态接口 + `CardProcessing` / `WalletProcessing`；
- `Amount`、`BillingAddress`、`Phone`、`AccountHolder`、`MerchantAccount`、`PaymentContext`；
- 带 `@NotNull`、`@Pattern`、`@Min`、`@Max`、`@Size` 的 Bean Validation 注解。

Discriminator 说明：

- `Source` 的 `propertyName=type`，mapping 为 `CREDIT_CARD -> CardSource`、
  `WALLET -> WalletSource`；
- `Processing` 的 `propertyName=schema`，mapping 为
  `CardProcessing -> CardProcessing`、`WalletProcessing -> WalletProcessing`；
- `type` / `schema` 使用 `string + pattern` 而不是 enum。OpenAPI Generator 7.0.1 在
  discriminator + enum 时会生成接口 `String getType()` 与子类 `TypeEnum getType()` 的冲突；
  pattern 保留同样的取值约束，同时保证生成代码可编译。

### 3.3 请求/响应校验

1. 生成接口的参数上有 `@Valid @RequestBody`；Controller 也显式标注 `@Valid`。
2. OpenAPI schema 约束由 Bean Validation 执行（`spring-boot-starter-validation`）。
3. 校验失败 → `MethodArgumentNotValidException` → `connector-api` 中的共享
   `GlobalExceptionHandler` 统一生成 `ApiErrorResponse`（JSON 与 `ErrorResponse` 契约一致），
   `code=PAYMENT_REJECTED`、`status=Rejected`、错误 `id`、字段错误列表。
4. `PaymentValidator` 继续负责 OpenAPI 不易表达的业务规则：卡 source 的到期月份 + 年份组合
   不能早于当前月（字段路径为 `source.expiryYear`）。
5. Wallet source 可以完成 OpenAPI 解析/校验；当前 `bank-simulator` connector 只支持卡支付，
   因此 wallet 在 builder 阶段以 `source.type` 业务拒绝，不调用银行。
6. 响应模型由生成接口约束，Controller 必须返回生成的 `PaymentResponse`；错误统一生成
   `ErrorResponse`。

### 3.4 Swagger UI

- `/openapi.yaml`：`OpenApiSpecController` 从 classpath 返回源 YAML；
- `springdoc.swagger-ui.url=/openapi.yaml`，Swagger UI 直接读取源 YAML；
- `springdoc.api-docs.enabled=true` 仅用于运维/调试，不作为契约来源；
- Swagger UI：`/swagger-ui/index.html`。

---

## 4. bank-simulator OpenAPI 与 Jersey 2.x

### 4.1 银行契约

`connector-bank/src/main/resources/openapi/bank-simulator.yaml` 定义：

```text
BankPaymentRequest:  card_number, expiry_date (MM/YYYY), currency, amount, cvv
BankPaymentResponse: authorized, authorization_code
```

`connector-bank/pom.xml` 使用 `openapi-generator-maven-plugin:7.0.1`，`spring` generator 的
`generateApis=false` 只生成模型（POJO）。POJO 带 Jackson `@JsonProperty`，因此 Java 属性是
`cardNumber` 等，JSON 字段仍是银行要求的 `card_number` 等。

request builder 将 `PaymentCommand` 转成 `BankPaymentRequest`；response builder 将
`BankPaymentResponse` 转成 `PaymentOutcome`。

### 4.2 Jersey 2.x client

- 依赖 `org.glassfish.jersey.core:jersey-client:2.35`、`jersey-hk2:2.35`、
  `jackson-jaxrs-json-provider`。
- Spring Boot 3 默认管理 Jersey 3.x；本项目在根 POM 锁定 Jersey **2.35** 全套版本，避免
  javax/jakarta 混用。
- `BankSimulatorConfiguration` 以 `@Bean(destroyMethod="close")` 暴露 Spring 管理的
  `javax.ws.rs.client.Client`：
  - `ClientConfig` 设置 connect/read timeout；
  - 注册 `JacksonJsonProvider`（纯 Jackson，不依赖 JAXB）。
- `BankSimulatorClient` 构造器注入 `Client`，Spring IoC 生效；它只负责：
  - POST `base-url + /payments`；
  - 2xx → 反序列化 `BankPaymentResponse`；
  - 4xx → `BankIntegrationException`；
  - 5xx/连接失败/超时 → `BankUnavailableException`；
  - JSON 不可解析 → `BankIntegrationException`。
- 入站 HTTP 继续使用 Spring MVC（Jakarta）；Jersey 2.x 仅用于出站银行调用，两者命名空间互不
  影响。

### 4.3 故障分类

| 上游情况 | connector 异常 | 对外 HTTP |
| --- | --- | --- |
| 连接失败、读超时 | `BankUnavailableException` | 503 |
| 5xx | `BankUnavailableException` | 503 |
| 4xx | `BankIntegrationException` | 502 |
| 2xx 但空/非法 body | `BankIntegrationException` | 502 |

不自动重试银行 POST（非幂等），避免重复扣款。

---

## 5. 银行路由与 Operation 抽象

`PaymentOperation` 负责 `perform` 用例和 bankId 路由：

```text
校验 → 按 BankId enum 路由 → registry.get(bankId) → processor.performPayments(command) → 持久化
```

核心代码：

```java
IPaymentProcessor paymentProcessor = paymentsConnectorRegistry.get(resolvedBankId);
PaymentOutcome outcome = paymentProcessor.performPayments(command);
```

- Controller 只做 HTTP 适配和 public builder 调用，不包含路由/银行方法；
- Operation 不直接依赖任何具体 connector，只依赖 `IPaymentProcessor` 抽象和注册表；
- 新增银行只增加 connector 子模块。

`PaymentsConnectorRegistry`：

1. 构造器注入 `List<IPaymentProcessor>`；
2. 将 connector 的字符串 id 映射到 OpenAPI 生成的 `BankId` enum；
3. 空/未知/重复 connector id 启动失败；
4. 已声明但未注册的 id → `UnsupportedBankException` → 400 `UNSUPPORTED_BANK`；
5. 错误信息只列支持项，不回显原始输入。

`bankId` 来自 body，必须匹配 OpenAPI enum 值（当前为 `bank-simulator`）；enum 同时用于
路由、持久化与返回。新增银行时需同时更新 OpenAPI enum 和 connector。

`PaymentController` 使用 `@Autowired` 注入 `PaymentOperation`；`PaymentOperation` 使用
`@Autowired` 注入 validator、registry、repository、request builder、response builder。
错误 advice 在 `connector-api`，app 层不暴露 error handler。

### 5.0 响应 Builder 分层

- `PaymentResponseBuilder`：公共字段映射与原地 masked 处理，不做深拷贝，不直接暴露完整卡号；
- `CreatePaymentResponseBuilder`：POST 响应，`source.number` 为空；
- `GetPaymentResponseBuilder`：GET 响应，直接复用公共 builder 并在原响应上设置
  `source.number = **** **** **** 1234` 与 `source.cardNumberLastFour`；
- POST replay 会移除 GET 临时添加的 masked number，保持 POST 不返回卡号字段。

所有 Bean 使用显式名称，例如 `gateway.app.connector.payments-connector-registry`、
`gateway.app.api.builder.payment-response-builder`，便于生命周期和依赖图排查。

### 5.1 requestKey 幂等

`requestKey` 是请求必填 UUID，重试必须复用同一个 key 和同一份语义 payload。

状态机：

| 状态 | 重试（key + payload 相同） | 行为 |
| --- | --- | --- |
| `IN_PROGRESS` | 原请求仍在执行 | 409 `REQUEST_IN_PROGRESS`，不调用银行 |
| `COMPLETED` | 已完成 | 201 + 原 `PaymentResponse`（同一 payment id），不调用银行 |
| `FAILED` | 已终止失败 | 重放原 4xx/5xx，不调用银行 |
| 任意状态 + 不同 fingerprint | payload 被修改 | 409 `IDEMPOTENCY_KEY_REUSED`，不调用银行 |

执行顺序：

```text
validate
  -> build PaymentCommand
  -> SHA-256(ObjectMapper(request))  // 语义 fingerprint
  -> idempotency.start(requestKey, fingerprint)  // putIfAbsent, 原子占位
  -> bank.performPayments
  -> repository.save
  -> idempotency.complete(requestKey, payment)
  -> on RuntimeException: idempotency.fail(requestKey, exception)
```

关键点：

- `start` 用 `ConcurrentHashMap.putIfAbsent` 原子占位，保证并发重复请求只有一个进入银行调用；
- fingerprint 覆盖 source/amount/paymentContext 等完整请求（同 key 不同 payload 会被拒绝）；
- 银行超时/5xx 或网关 500 等终止失败会以 `FAILED` 保存异常；同 key 重试直接重放异常，
  不再调用银行，避免“第一次已扣款、第二次重复扣款”；
- completed 重试返回同一 `PaymentResponse`，不重新生成 payment id；
- 失败重放的错误 response 会生成新的 error `id`，但 HTTP status/code 保持原终止结果。

边界与限制：

- 当前实现是单实例内存 Map，重启丢失、无 TTL；生产必须使用数据库/Redis 的唯一
  `requestKey` 约束、in-progress lease/TTL 和定时清理；
- 若进程在银行调用后、写入 `COMPLETED/FAILED` 前崩溃，记录会停留在 `IN_PROGRESS`，
  重试返回 409，需要按 `requestKey` 对账；生产用持久化状态 + 对账/Outbox 解决；
- 银行超时当前映射为 503；同一机制适用于网关 500 及其他终止性 RuntimeException。

---

## 6. 公共 API 契约

### 6.1 创建支付

`POST /payments`

```json
{
  "bankId": "bank-simulator",
  "requestKey": "8f14e45f-ceea-467a-9d0b-1c0a7c3a9e21",
  "source": {
    "type": "CREDIT_CARD",
    "brand": "VISA",
    "number": "4543474002249996",
    "expiryMonth": 6,
    "expiryYear": 2030,
    "name": "Bruce Wayne",
    "cvv": "956",
    "billingAddress": {
      "addressLine1": "123 High St.",
      "city": "London",
      "zip": "SW1A 1AA",
      "country": "GB"
    }
  },
  "amount": { "value": 6540, "currency": "USD" },
  "paymentContext": {
    "captureMode": "SINGLE",
    "transactionInitiatorType": "CUSTOMER",
    "processing": {
      "schema": "CardProcessing",
      "routingNetwork": "CARTES_BANCAIRES"
    }
  }
}
```

成功 201：

```json
{
  "id": "8f14e45f-ceea-467a-9d0b-1c0a7c3a9e21",
  "amount": { "value": 6540, "currency": "USD" },
  "bankId": "bank-simulator",
  "status": "Authorized",
  "source": {
    "type": "CREDIT_CARD",
    "brand": "VISA",
    "cardNumberLastFour": "9996",
    "expiryMonth": 6,
    "expiryYear": 2030
  },
  "processedOn": "2026-11-13T08:00:00Z",
  "processing": { "authCode": "auth-123" }
}
```

`Rejected` / `Authorized` / `Declined` 统一定义在 `PaymentStatus`，成功与错误响应共用。
POST 响应故意不返回 `source.number`，只返回品牌、后四位和有效期；GET 响应通过
`source.number = **** **** **** 9996` 返回掩码卡号。

响应字段是 `processing.authCode`，不是 `authorizationCode`；银行的 `authorization_code` 映射到该字段。
只有银行返回非空授权码（通常是 Authorized）时才生成 `processing`，Declined 不生成。

响应头：`Location: /payment/{id}`、`X-Request-Id`。

### 6.2 查询

`GET /payment/{id}` 只按 payment id 查询；返回 200 `PaymentResponse` 或 404 `ErrorResponse`。
requestKey 仅用于 POST 幂等，不作为查询键。GET 使用 `GetPaymentResponseBuilder`，在内存解密
卡号后补充 `source.number`。

### 6.3 错误响应

```json
{
  "id": "8f14e45f-ceea-467a-9d0b-1c0a7c3a9e21",
  "status": "Rejected",
  "timestamp": "2026-11-13T08:00:00Z",
  "code": "PAYMENT_REJECTED",
  "message": "Payment request validation failed",
  "errors": [
    {
      "field": "source.number",
      "message": "must match \"^[0-9]{14,19}$\""
    }
  ]
}
```

- 所有非 2xx 错误统一有 `id`（error id，UUID）与 `status=Rejected`；
- 错误字段名与公开 camelCase 契约一致，嵌套字段使用 `source.number` 等路径；
- 不返回全卡号/CVV、堆栈或内部类名。

### 6.4 错误码

| HTTP | code | 场景 |
| --- | --- | --- |
| 401 | `UNAUTHORIZED` | 缺少或错误的 Basic token |
| 400 | `PAYMENT_REJECTED` | OpenAPI Bean Validation 或业务校验失败 |
| 400 | `UNSUPPORTED_BANK` | 有效 enum 值没有对应 connector |
| 400 | `INVALID_REQUEST` | body `bankId` 不在 enum 中、JSON 不可解析、路径类型错误、浮点金额 |
| 404 | `PAYMENT_NOT_FOUND` | 支付不存在 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 同一 requestKey 携带不同 payload |
| 409 | `REQUEST_IN_PROGRESS` | 同一 requestKey 的首次请求仍在执行 |
| 405 | `METHOD_NOT_ALLOWED` | 方法不支持 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | 非 JSON |
| 502 | `ACQUIRING_BANK_ERROR` | 银行 4xx/非法响应 |
| 503 | `ACQUIRING_BANK_UNAVAILABLE` | 银行超时/连接失败/5xx |
| 500 | `INTERNAL_ERROR` | 未预期异常 |

---

## 6.5 卡数据加密

`InMemoryPaymentRepository.save(payment, cardNumber, cvv)`：

- 使用 AES/GCM/NoPadding；
- 每条记录的 AES key 由 `HMAC-SHA256(masterKey, paymentId)` 派生；
- 每次加密随机 12 字节 IV，存储 `Base64(IV || ciphertext)`；
- cardNumber 和 cvv 分别加密；
- 公开 `PaymentResponse` 不包含完整卡号/CVV。

`GET /payment/{id}`：

- 只解密 cardNumber；
- 取后四位；
- 设置 `source.number = "**** **** **** " + lastFour` 和 `source.cardNumberLastFour`；
- CVV 保持加密，永不解密返回。

加密主密钥在应用启动时安全随机生成并仅保存在内存中，不放入配置。由于支付数据仓库也是内存实现，
密文只在当前进程生命周期内可解密；重启后新密钥无法解密旧密文。该方案仅适用于挑战项目的内存存储，
生产持久化存储应使用 KMS/HSM 管理持久密钥。

## 6.6 API Token 校验

支付 API 通过轻量 filter 校验 `Authorization: Basic <base64 token>`。预期 token 从
`GATEWAY_BASIC_TOKEN` 读取；本地默认 `bWVyY2hhbnQ6c2VjcmV0`（`merchant:secret`），生产必须覆盖。
请求校验无状态，缺少或不匹配的 token 返回 401 `UNAUTHORIZED`；Actuator health 和 API 文档端点保持公开。

---

## 7. 校验与状态

OpenAPI + Bean Validation：

- `bankId` 必填，且必须是 OpenAPI enum 声明的值；
- `source` 必填，`type` 为 `CREDIT_CARD` / `WALLET`；
- card source：`number` 14–19 数字、`expiryMonth` 1–12、`expiryYear` 1000–9999、
  `cvv` 3–4 数字；
- wallet source：`token` 非空；
- `amount.value` 正整数；
- `amount.currency` 仅允许 `GBP`、`USD`、`EUR` 三个大写币种代码；
- `paymentContext.processing.schema`：`CardProcessing` / `WalletProcessing`（可选
  processing 多态）。

`PaymentValidator` 只保留 OpenAPI 无法表达的规则：

- 到期月份 + 年份组合不得早于当前月（当前月有效）；
- 字段必填/格式/范围/币种格式全部由生成的 OpenAPI 模型 Bean Validation 负责，已从
  `PaymentValidator` 移除，避免重复校验；
- 使用 `Clock` 注入固定时间测试。

状态：

- 持久化/响应：生成模型 `PaymentResponse.StatusEnum` 的 `AUTHORIZED` / `DECLINED`；
- 错误响应：`status="Rejected"`（所有非 2xx；不持久化）。

---

## 8. 存储与可用性

### 8.1 存储

`InMemoryPaymentRepository` 使用 `ConcurrentHashMap<UUID, PaymentResponse>`，直接持久化 OpenAPI
生成的 `PaymentResponse`，避免 API 契约、存储模型和手写 POJO 三份漂移。通过 `PaymentRepository`
端口可替换 PostgreSQL。内存存储不跨实例、重启丢失，生产需外部存储。

### 8.2 一致性风险

银行授权成功、保存前崩溃会丢失记录；生产需 PENDING + 幂等键 + 对账/Outbox。

### 8.3 可用性

- Jersey connect timeout 2s、read timeout 3s，可配置。
- 不自动重试非幂等 POST。
- 4xx/5xx/网络/解析错误分类映射。
- Actuator liveness/readiness；`gateway-web` 唯一可执行模块。
- 生产建议熔断、连接池、限流、多副本 + 外部存储。

---

## 9. 可维护性与 ADR

### 9.1 变更矩阵

| 变更 | 修改位置 | 不需要修改 |
| --- | --- | --- |
| 新增银行 | 新增 connector 子模块 + runtime 依赖 | app/controller/operation/router |
| 调整公共契约 | `payment-gateway.yaml` + 重新生成 | connector |
| 调整银行报文 | `bank-simulator.yaml` + 重新生成 + builder | app/web |
| 调整业务校验 | app validator / YAML | connector |
| 切换数据库 | app 新增 Repository 实现 | controller/connector |

### 9.2 ADR

| # | 决策 | 备选 | 理由 |
| --- | --- | --- | --- |
| 1 | OpenAPI YAML + Generator 生成接口/模型 | 手写 DTO | 契约唯一来源，编译期同步，Bean Validation 自动生成 |
| 2 | Controller 实现 `PaymentsApi` | 手写 Controller 映射 | 路径/参数/响应由 YAML 保证 |
| 3 | 公共字段 camelCase | snake_case | 按本次要求；银行报文仍 snake_case |
| 4 | bankId 在 body | header/path | 按本次要求；路由元数据随请求载荷 |
| 5 | SPI 在 connector-api | app/父 POM | app 不依赖实现；父 POM 不能编译代码 |
| 6 | Operation 直接 `perform` 并路由 | 额外 Router 层 | 按本次要求 operation 负责 perform/bankId 路由，减少一层间接 |
| 7 | Jersey 2.35 client | RestTemplate | 按本次要求；Spring Bean 管理 Client |
| 8 | JacksonJsonProvider | JacksonFeature(JAXB) | Java 17 无 JAXB；避免额外 JAXB 依赖 |
| 9 | 根 POM 锁定 Jersey 2.35 | 使用 Boot 默认 Jersey 3 | 防止 javax/jakarta 混用 |
| 10 | 最多 3 个币种白名单 | 任意 3 位字母 | 按本次要求限制为 GBP/USD/EUR |
| 11 | 不自动重试 POST | 重试 | 非幂等，避免重复扣款 |
| 12 | 内存仓库 + 端口 | 数据库 | 挑战允许，替换成本低 |
| 17 | `requestKey` 原子占位 + 状态机 | 客户端直接重试 | 防止并发/超时重试重复调用银行 |
| 18 | 内存幂等存储 | DB/Redis 唯一键 + lease | 单实例挑战实现，生产需持久化/TTL/对账 |
| 19 | 卡号/CVV 按 payment id 派生 AES-GCM 密钥加密存储 | 明文内存 | 降低内存/快照泄露风险；GET 仅解密卡号生成掩码 |
| 13 | 生成 `PaymentResponse` 作为存储/响应模型 | 手写领域实体 | 按本次要求移除 domain POJO；契约与存储不漂移 |
| 14 | `source` / `processing` 使用 OpenAPI discriminator | 扁平字段 + 手写 if/else | 贴近 Checkout.com API；生成多态接口 |
| 15 | discriminator 值用 `string + pattern` | enum | 规避 Generator 7.0.1 接口/子类返回类型冲突 |
| 16 | wallet 只建模，当前 connector 拒绝 | 伪造成卡支付 | bank-simulator 只支持卡；后续新增 wallet connector |

---

## 10. 安全与观测

- 全卡号/CVV 只存在于请求/银行调用期间；不持久化、不返回、不写日志。
- 生成的银行 POJO 含全卡号字段，禁止直接日志打印；client 只记录状态/授权结果。
- 对外只返回后四位。
- 生产 HTTPS/TLS、银行 mTLS/签名、密钥管理、认证/限流/WAF。
- `X-Request-Id` 合法字符校验 + MDC 日志。
- 错误日志不打印可能含卡数据的上游响应体/堆栈。
- 建议指标：支付结果计数、银行延迟直方图、错误率、校验失败字段。

---

## 11. 测试策略

| 模块 | 测试 | 覆盖 |
| --- | --- | --- |
| gateway-app | `PaymentValidatorTest` | 仅跨字段到期校验；字段规则由 OpenAPI/Bean Validation 覆盖 |
| gateway-app | `CreatePaymentRequestBuilderTest` / `CreatePaymentResponseBuilderTest` / `GetPaymentResponseBuilderTest` / `PaymentResponseBuilderTest` | POST/GET 响应分离、公共映射 |
| gateway-app | `PaymentsConnectorRegistryTest` | 注册表、大小写、重复/空 ID、未知 bank |
| gateway-app | `PaymentOperationTest` | perform、bankId 路由、幂等重放、授权/拒绝/校验/未知 bank/银行不可用/查询 |
| gateway-app | `PaymentIdempotencyServiceTest` | in-progress、completed replay、failed replay、fingerprint 冲突 |
| gateway-app | `PaymentCryptoServiceTest` / `InMemoryPaymentRepositoryTest` | AES-GCM 加解密、按 ID 绑定、GET 解密后掩码 |
| connector-bank | `BankSimulatorRequestBuilderTest` | 生成 POJO 映射、`MM/YYYY`、snake_case 序列化 |
| connector-bank | `BankSimulatorResponseBuilderTest` | 授权/拒绝/null |
| connector-bank | `BankSimulatorClientTest` | Jersey HTTP、200/400/503、连接失败、空/非法 body |
| connector-bank | `BankPaymentsConnectorTest` | SPI 编排、`getBankId` |
| gateway-web | `PaymentGatewayIntegrationTest` | 生成 API、discriminator、嵌套字段校验、wallet 解析、body bankId、Jersey 全链路、OpenAPI YAML |

数量：app 33、connector-bank 15、web 30，共 **78**。测试不依赖 Docker。

---

## 12. 运行

```bash
docker compose up -d
./mvnw clean verify
./mvnw -pl gateway-web -am spring-boot:run
```

或：

```bash
java -jar gateway-web/target/gateway-web-0.0.1-SNAPSHOT.jar
```

- Swagger UI：`http://localhost:8090/swagger-ui/index.html`
- YAML：`http://localhost:8090/openapi.yaml`
- Health：`http://localhost:8090/actuator/health`

---

## 13. 假设与限制

1. `bankId` 必填，来自 body，必须匹配 OpenAPI enum 声明的值。
2. 金额为最小货币单位正整数，使用 `int`。
3. 到期年四位；当前月有效。
4. 币种白名单仅允许 GBP、USD、EUR 三个大写代码。
5. 单商户、无认证。
6. 银行模拟器从 `connector.bank.simulator.base-url` 可达。
7. `Rejected` 不持久化、无 payment id，但错误响应带独立 error `id`；`Declined` 持久化。
8. 内存存储仅单实例演示。
9. Jersey 2.35 仅用于出站；入站 Spring MVC/Jakarta。
10. 当前为单实例内存幂等；生产需 PENDING/持久化幂等表/lease/TTL/对账。

---

## 14. 目录结构

```text
.
├── pom.xml
├── gateway-app/
│   ├── pom.xml
│   └── src/main/
│       ├── java/.../app/
│       │   ├── api/ (PaymentController implements generated PaymentsApi; request/response builders)
│       │   ├── connector/ (PaymentsConnectorRegistry)
│       │   ├── domain/ (PaymentOperation, PaymentValidator)
│       │   └── repository/
│       └── resources/openapi/payment-gateway.yaml
├── gateway-connector/
│   ├── pom.xml (pom aggregator)
│   ├── connector-api/
│   │   └── src/main/java/.../connector/api/ (IPaymentProcessor, command/outcome, errors)
│   └── connector-bank/
│       ├── pom.xml
│       ├── src/main/java/.../connector/bank/ (builders, Jersey client, connector)
│       └── src/main/resources/openapi/bank-simulator.yaml
├── gateway-web/
│   └── src/main/java/.../web/ (RequestIdFilter, OpenApiSpecController)
├── docs/DESIGN.md
├── docker-compose.yml
└── imposters/bank_simulator.ejs
```
