# MiniTelemetry

MiniTelemetry 是一个面向 Java 17 的轻量级分布式追踪 SDK，提供 Span 生命周期、上下文传播、动态采样、指标聚合、Spring Boot 自动配置以及基于 Byte Buddy 的 Java Agent。

项目目前处于早期开发阶段，API 与上报协议仍可能调整，不建议直接用于关键生产环境。

## 功能

- Trace、Span、Context 与 Scope
- W3C `traceparent` 上下文传播
- 固定比例、远端策略和错误翻牌采样
- Span 异步批量导出
- Span 维度的成功数、失败数和耗时指标聚合
- MDC `traceId`、`spanId` 注入
- Spring Boot 自动配置
- Servlet、Feign、Hutool HTTP、Spring Messaging 埋点
- `@Traced` 方法埋点
- Java Agent 字节码织入

## 项目结构

```text
minitelemetry/
├── minitelemetry-sdk/        # Trace SDK、自动配置与组件埋点
├── minitelemetry-javaagent/  # Java Agent 与 @Traced 字节码织入
└── pom.xml                    # Maven 聚合工程
```

## 环境要求

- JDK 17
- Maven 3.9+

## 构建

```bash
git clone https://github.com/SarekCui/minitelemetry.git
cd minitelemetry
mvn clean package
```

构建产物：

```text
minitelemetry-sdk/target/minitelemetry-sdk-1.0-SNAPSHOT.jar
minitelemetry-javaagent/target/minitelemetry-javaagent-1.0-SNAPSHOT.jar
```

安装到本地 Maven 仓库：

```bash
mvn clean install
```

## 引入 SDK

项目尚未发布到 Maven Central。执行 `mvn install` 后，可在本地项目中添加：

```xml
<dependency>
    <groupId>com.minitelemetry.sdk</groupId>
    <artifactId>minitelemetry-sdk</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

## Spring Boot 配置

```yaml
spring:
  application:
    name: order-service

trace:
  report:
    base-url: http://localhost:8080
```

`trace.report.base-url` 是上报服务的 HTTP 基址，不包含具体接口路径。SDK 当前使用以下接口：

```text
POST /trace/report/spans
POST /trace/report/metrics
GET  /trace/sampling/strategies?serviceName={serviceName}
```

本仓库只包含 SDK 与 Java Agent，不包含接收这些请求的服务端实现。

## 使用 `@Traced`

```java
import com.minitelemetry.sdk.annotation.Traced;
import com.minitelemetry.sdk.trace.SpanKind;

public class OrderService {

    @Traced(value = "order.create", kind = SpanKind.INTERNAL)
    public void createOrder() {
        // business logic
    }
}
```

`value` 留空时，Span 名称默认为“声明类全名.方法名”。外部触发的服务入口可以显式声明 `localRoot`：

```java
@Traced(value = "order.consume", localRoot = true)
public void consumeOrder() {
    // message handling
}
```

## 手动创建 Span

```java
import com.minitelemetry.sdk.context.Scope;
import com.minitelemetry.sdk.trace.Span;
import com.minitelemetry.sdk.trace.StatusCode;
import com.minitelemetry.sdk.trace.Tracer;

Span span = Tracer.spanBuilder("order.create").startSpan();
try (Scope ignored = span.makeCurrent()) {
    span.setAttribute("order.id", "10001");
    span.setStatus(StatusCode.OK);
} catch (RuntimeException e) {
    span.recordException(e);
    throw e;
} finally {
    span.end();
}
```

简单场景也可以使用：

```java
Tracer.withSpan("order.create", () -> {
    // business logic
});
```

## 日志关联

SDK 会将当前 Span 的标识写入 SLF4J MDC。以 Logback 为例：

```xml
<pattern>%d %-5level [%X{traceId}:%X{spanId}] %logger - %msg%n</pattern>
```

## Java Agent

使用构建后的 Agent JAR 启动应用：

```bash
java \
  -javaagent:/path/to/minitelemetry-javaagent-1.0-SNAPSHOT.jar \
  -jar application.jar
```

只扫描指定业务包并开启调试日志：

```bash
java \
  -javaagent:/path/to/minitelemetry-javaagent-1.0-SNAPSHOT.jar=include=com.example.,debug=true \
  -jar application.jar
```

Agent 参数：

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `enabled` | `true` | 是否启用 Agent |
| `debug` | `false` | 是否输出 Agent 安装日志 |
| `include` | 空 | 仅扫描指定类名前缀；空表示扫描所有非排除类 |
| `exclude` | JDK、Byte Buddy、SDK、Agent 包 | 排除的类名前缀 |

也可以通过系统属性配置，例如：

```bash
-Dminitelemetry.javaagent.include=com.example.
-Dminitelemetry.javaagent.debug=true
```

Java Agent 当前只织入带 `@Traced` 的方法，并依赖 SDK 完成运行时采样、处理和导出。Spring 应用如果已经启用 `TracedAspect`，不要再使用 Agent 织入相同方法，否则会产生重复 Span。

## 上下文传播

异步任务需要显式捕获当前 Context：

```java
executor.execute(Tracer.wrap(() -> {
    // current trace context is restored here
}));
```

向自定义载体写入或读取 W3C Trace Context：

```java
Tracer.inject(headers);
Context parent = Tracer.extract(headers);
```

## 验证

```bash
mvn clean test
```

当前 Java Agent 包含配置解析测试；SDK 的完整单元测试和端到端测试仍在补充中。

## 当前限制

- 未包含 Trace/Metric 接收服务端
- 未发布到 Maven Central
- Java Agent 当前仅支持 `@Traced` 方法织入
- Spring AOP 与 Java Agent 不应同时织入同一方法
- API、配置键和上报协议在 `1.0.0` 发布前可能发生变化

## 参与开发

提交代码前请至少执行：

```bash
mvn clean test
```

提交信息建议使用 Conventional Commits，例如：

```text
feat: add servlet instrumentation
fix: restore parent context after async task
test: cover batch span processor flush
```
