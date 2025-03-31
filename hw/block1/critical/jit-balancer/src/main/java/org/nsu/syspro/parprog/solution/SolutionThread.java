package org.nsu.syspro.parprog.solution;

import org.nsu.syspro.parprog.UserThread;
import org.nsu.syspro.parprog.external.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SolutionThread extends UserThread {
    private static final long THRESHOLD_L1 = 1000;
    private static final long THRESHOLD_L2 = 10000;

    private static final ExecutorService compilerPool = Executors.newFixedThreadPool(2);
    private static final Cache globalCache = new Cache();
    private final Map<Long, Long> invocationCounts = new HashMap<>();
    private final Map<Long, CountDownLatch> compilationLatches = new HashMap<>();

    public SolutionThread(int compilationThreadBound, ExecutionEngine exec, CompilationEngine compiler, Runnable r) {
        super(compilationThreadBound, exec, compiler, r);
    }

    @Override
    public ExecutionResult executeMethod(MethodID id) {
        long methodID = id.id();
        Cache cache = globalCache;
        Object[] methodData = cache.get(methodID);
        invocationCounts.put(methodID, invocationCounts.getOrDefault(methodID, 0L) + 1);
        int level = (methodData != null) ? (int) methodData[1] : 0;
        CountDownLatch latch = compilationLatches.get(methodID);

        if (invocationCounts.get(methodID) == THRESHOLD_L1 && level == 0) {
            latch = new CountDownLatch(1);
            compilationLatches.put(methodID, latch);
            CountDownLatch finalLatch = latch;
            compilerPool.submit(() -> {
                CompiledMethod compiled = compiler.compile_l1(id);
                cache.put(methodID, compiled, 1);
                finalLatch.countDown();
            });
        }

        if (invocationCounts.get(methodID) == THRESHOLD_L2 && level != 2) {
            latch = new CountDownLatch(1);
            compilationLatches.put(methodID, latch);
            CountDownLatch finalLatch1 = latch;
            compilerPool.submit(() -> {
                CompiledMethod compiled = compiler.compile_l2(id);
                cache.put(methodID, compiled, 2);
                finalLatch1.countDown();
            });
        }

        if (latch != null) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException("Compilation was interrupted", e);
            }
        }

        return (methodData != null) ? exec.execute((CompiledMethod) methodData[0]) : exec.interpret(id);
    }

    private static class Cache {
        private final Map<Long, Object[]> methodMap = new HashMap<>();
        private final ReadWriteLock lock = new ReentrantReadWriteLock();

        public void put(long id, CompiledMethod compiledCode, int level) {
            lock.writeLock().lock();
            try {
                Object[] existing = methodMap.get(id);
                int currentLevel = (existing != null) ? (int) existing[1] : 0;
                if (level > currentLevel) {
                    methodMap.put(id, new Object[]{compiledCode, level});
                }
            } finally {
                lock.writeLock().unlock();
            }
        }

        public Object[] get(long id) {
            lock.readLock().lock();
            try {
                return methodMap.get(id);
            } finally {
                lock.readLock().unlock();
            }
        }
    }
}
