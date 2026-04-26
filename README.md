# mini-telemetry

一个按 OpenTelemetry 核心思路缩小后的最小实现，包含：

- `MiniOpenTelemetry` 顶层入口，用法对齐 `openTelemetry.getTracer(...)`
- `Context` / `Scope` / `ThreadLocalContextStorage`
- `Tracer` / `SpanBuilder` / `Span`
- `SpanExporter` 与一个控制台 `LoggingSpanExporter`
- 显式异步上下文传播：`Context.current().wrap(...)`

当前 Java 包结构：

- `com.minitelemetry.api`
- `com.minitelemetry.context`
- `com.minitelemetry.trace`
- `com.minitelemetry.exporter`
- `com.minitelemetry.demo`
- `com.minitelemetry.testing`

编译：

```bash
javac -d out $(find src/main/java src/test/java -name '*.java')
```

运行 demo：

```bash
java -cp out com.minitelemetry.demo.MiniTelemetryDemo
```

当前 demo 会依次演示：

- HTTP 请求里的同步嵌套 span
- 并行异步任务的上下文传播
- `setParent(...)` 显式覆盖当前上下文
- 异常链路里的 `recordException()`
- fire-and-forget 后台任务的手动上下文捕获

Spring 风格的最小接入示例：

```java
@Configuration
public class MiniTelemetryConfig {

    @Bean
    public MiniOpenTelemetry miniOpenTelemetry() {
        return MiniOpenTelemetry.builder()
                .setSpanExporter(new LoggingSpanExporter())
                .build();
    }
}
```

```java
@Service
public class OrderService {
    private final Tracer tracer;

    public OrderService(MiniOpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer("order");
    }

    public void createOrder(String orderId) {
        Span span = tracer.spanBuilder("OrderService.createOrder").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            span.setAttribute("order.id", orderId);
            span.setStatus(StatusCode.OK, "success");
        } catch (Exception e) {
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }
}
```

运行自检：

```bash
java -cp out com.minitelemetry.testing.MiniTelemetrySelfTest
```

前端 Trace Viewer：

```bash
open ui/index.html
```

或者：

```bash
cd ui
python3 -m http.server 8080
```

然后访问 `http://localhost:8080`。

前端支持两种输入格式：

- `[{ traceId, spanId, parentSpanId, ... }]`
- `[{ spanContext: { traceId, spanId }, parentSpanId, ... }]`

页面内置了 demo 样例数据，也可以直接粘贴 JSON 或导入 JSON 文件。
