package com.minitelemetry.sdk.trace.sampling.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 按 {@link SamplingStrategy#getDailyErrorLimit() dailyErrorLimit} 控制错误翻牌额度的限流器。
 * <p>线程安全。预算桶按 spanName 隔离;wildcard 命中共用 {@code "*"} 桶。
 * 本地时区自然日 0 点滚动归零。
 *
 * <p><b>预算范围:每 JVM 实例独立</b>。桶存在本进程内 {@code ConcurrentMap},不跨实例共享。
 * 同服务 N 实例部署 -> 全服务全天最多翻牌 {@code limit * N}。
 *
 * <p>策略协议:{@code keepErrors=false} 一律拒绝;{@code keepErrors=true, limit>=1} 受控保留;
 * 其余组合视为协议异常一律拒绝。{@code limit} 字段不含 sentinel 值。
 *
 * <p>limit 在线热更:同一桶 limit 变化时,“当天已扣计数继承 + 收紧立即生效”。
 */
public class ErrorLimiter {

    private static final Logger log = LoggerFactory.getLogger(ErrorLimiter.class);

    public static final String WILDCARD = "*";

    private final SamplingStrategyStorage storage;
    private final ConcurrentMap<String, BudgetState> budgets = new ConcurrentHashMap<>();

    public ErrorLimiter(SamplingStrategyStorage storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    /** @return {@code true} 预算扣减成功可翻牌;{@code false} 无策略 / 预算耗尽 */
    public boolean tryConsume(String spanName) {
        SamplingStrategy strategy = storage.get(spanName);
        String key = spanName;
        if (strategy == null) {
            strategy = storage.get(WILDCARD);
            key = WILDCARD;
        }
        if (strategy == null) {
            return false;
        }
        // 显式关闭错误保留,不论 limit 是几一律拒绝;修复“limit=0 = 无限”的语义反转
        if (!strategy.isKeepErrors()) {
            return false;
        }
        int limit = strategy.getDailyErrorLimit();
        if (limit < 1) {
            return false;
        }
        BudgetState state = budgets.compute(key, (String k, BudgetState old) -> {
            if (old == null || old.limit == limit) {
                return old != null ? old : new BudgetState(limit);
            }
            BudgetState fresh = new BudgetState(limit);
            fresh.count.set(old.count.get());
            fresh.epochDay.set(old.epochDay.get());
            // 相同认帐日才继承 limit 收紧耗尽标志,防止重复刷屏;
            // limit 扩大后当天可再次耗尽故依然 WARN
            fresh.exhausted.set(old.exhausted.get() && limit <= old.limit);
            return fresh;
        });
        return consume(state, key);
    }

    /**
     * 把一次 {@link #tryConsume(String)} 已扣的预算还回去,等价于 {@code count.decrementAndGet()} 加下界保护。
     * <p>用于 {@code Span.setStatus(ERROR)}:tryConsume 成功后 CAS 抢翻牌权失败时归还,
     * 保证“trace 链度严格扣 1 次”。
     */
    public void refund(String spanName) {
        String key = storage.get(spanName) != null ? spanName : WILDCARD;
        if (storage.get(key) == null) {
            return;
        }
        BudgetState state = budgets.get(key);
        if (state != null) {
            state.count.updateAndGet(n -> Math.max(0, n - 1));
        }
    }

    private static boolean consume(BudgetState state, String label) {
        long today = LocalDate.now(ZoneId.systemDefault()).toEpochDay();
        long cur = state.epochDay.get();
        // CAS 保证多线程下跨日只重置一次
        if (cur != today && state.epochDay.compareAndSet(cur, today)) {
            state.count.set(0);
            state.exhausted.set(false);
        }
        long n = state.count.incrementAndGet();
        if (n > state.limit) {
            state.count.decrementAndGet();
            // 每桶每日首次耗尽一次并且首次耗尽后状态,避免错误风暴下日志刷屏
            if (state.exhausted.compareAndSet(false, true)) {
                log.warn("keep-errors budget exhausted, span={}, limit={}", label, state.limit);
            }
            return false;
        }
        return true;
    }

    private static final class BudgetState {
        final int limit;
        final AtomicLong count = new AtomicLong(0);
        final AtomicLong epochDay = new AtomicLong(-1);
        final AtomicBoolean exhausted = new AtomicBoolean(false);

        BudgetState(int limit) { this.limit = limit; }
    }
}
