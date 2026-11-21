package org.bbnors.lab.bench;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Small, dependency-free micro-benchmark harness (JMH is not available offline in this build).
 *
 * <p>Method: calibrate a batch size so one batch takes ≈ {@code targetBatchMs}; run
 * {@code warmup} untimed batches (JIT); then {@code measured} timed batches. Report the median
 * and p10/p90 of per-op time across batches, plus allocated bytes per op from
 * {@code com.sun.management.ThreadMXBean#getCurrentThreadAllocatedBytes}. Every result is folded
 * into a sink to defeat dead-code elimination.
 *
 * <p>Limitations (documented in docs/PERFORMANCE.md): no fork isolation, no CPU pinning; numbers
 * are comparable within one run on one machine, not across machines.
 */
public final class Harness {

    /** One measurement. */
    public record Result(String group, String name, double nsMedian, double nsP10, double nsP90, double bytesPerOp, long opsPerBatch, String note) {
        public Map<String, Object> asMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("group", group);
            m.put("name", name);
            m.put("ns_per_op_median", nsMedian);
            m.put("ns_per_op_p10", nsP10);
            m.put("ns_per_op_p90", nsP90);
            m.put("bytes_per_op", bytesPerOp);
            m.put("ops_per_batch", opsPerBatch);
            m.put("note", note);
            return m;
        }
    }

    private final int warmup;
    private final int measured;
    private final double targetBatchMs;
    private final com.sun.management.ThreadMXBean threads;
    private volatile long sink;

    public Harness(int warmup, int measured, double targetBatchMs) {
        this.warmup = warmup;
        this.measured = measured;
        this.targetBatchMs = targetBatchMs;
        this.threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        threads.setThreadAllocatedMemoryEnabled(true);
    }

    public Result run(String group, String name, String note, IntFunction<Object> op) {
        long ops = 1;
        while (true) {
            long t = batch(op, ops);
            if (t >= targetBatchMs * 1e6 * 0.5 || ops > (1L << 30)) {
                ops = Math.max(1, (long) (ops * targetBatchMs * 1e6 / Math.max(1, t)));
                break;
            }
            ops *= 4;
        }
        for (int i = 0; i < warmup; i++) {
            batch(op, ops);
        }
        double[] perOp = new double[measured];
        long allocTotal = 0;
        long tid = Thread.currentThread().threadId();
        for (int i = 0; i < measured; i++) {
            long a0 = threads.getThreadAllocatedBytes(tid);
            long t = batch(op, ops);
            long a1 = threads.getThreadAllocatedBytes(tid);
            allocTotal += a1 - a0;
            perOp[i] = (double) t / ops;
        }
        Arrays.sort(perOp);
        return new Result(group, name, pct(perOp, 0.5), pct(perOp, 0.1), pct(perOp, 0.9),
                (double) allocTotal / (ops * (double) measured), ops, note);
    }

    private long batch(IntFunction<Object> op, long ops) {
        long acc = 0;
        long t0 = System.nanoTime();
        for (long i = 0; i < ops; i++) {
            Object r = op.apply((int) i);
            acc += r == null ? 1 : r.hashCode();
        }
        long t = System.nanoTime() - t0;
        sink += acc;
        return t;
    }

    private static double pct(double[] sorted, double p) {
        double idx = p * (sorted.length - 1);
        int lo = (int) Math.floor(idx);
        int hi = (int) Math.ceil(idx);
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (idx - lo);
    }

    public static String markdown(List<Result> results) {
        StringBuilder sb = new StringBuilder("| Group | Benchmark | median | p10 | p90 | alloc/op | Note |\n|---|---|---:|---:|---:|---:|---|\n");
        for (Result r : results) {
            sb.append(String.format(Locale.ROOT, "| %s | `%s` | %s | %s | %s | %s | %s |%n", r.group(), r.name(),
                    time(r.nsMedian()), time(r.nsP10()), time(r.nsP90()), bytes(r.bytesPerOp()), r.note()));
        }
        return sb.toString();
    }

    static String time(double ns) {
        if (ns >= 1e6) return String.format(Locale.ROOT, "%.2f ms", ns / 1e6);
        if (ns >= 1e3) return String.format(Locale.ROOT, "%.2f µs", ns / 1e3);
        return String.format(Locale.ROOT, "%.1f ns", ns);
    }

    static String bytes(double b) {
        if (b >= 1 << 20) return String.format(Locale.ROOT, "%.2f MB", b / (1 << 20));
        if (b >= 1024) return String.format(Locale.ROOT, "%.1f KB", b / 1024);
        return String.format(Locale.ROOT, "%.0f B", b);
    }

    public static Map<String, Object> environment() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("java", System.getProperty("java.vm.name") + " " + System.getProperty("java.version"));
        m.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        m.put("cpus", Runtime.getRuntime().availableProcessors());
        m.put("max_heap_mb", Runtime.getRuntime().maxMemory() >> 20);
        m.put("gc", ManagementFactory.getGarbageCollectorMXBeans().stream().map(java.lang.management.GarbageCollectorMXBean::getName).toList());
        return m;
    }

    public static List<Result> list() {
        return new ArrayList<>();
    }
}
