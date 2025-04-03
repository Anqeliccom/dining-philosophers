package org.nsu.syspro.parprog.solution;

import org.nsu.syspro.parprog.UserThread;
import org.nsu.syspro.parprog.external.*;

import java.util.*;
import java.util.concurrent.*;

public class SolutionThread extends UserThread {
    private static final long THRESHOLD_L1 = 1000;
    private static final long THRESHOLD_L2 = 10000;

    private final ExecutorService compilerPool;
    private final Cache globalCache;
    private final Map<Long, Long> invocationCounts = new HashMap<>();
    private final Map<Long, Future<?>> compilationFutures = new HashMap<>();

    public SolutionThread(int compilationThreadBound, ExecutionEngine exec, CompilationEngine compiler,
                          Runnable r, ExecutorService compilerPool, Cache cache) {
        super(compilationThreadBound, exec, compiler, r);
        this.compilerPool = compilerPool;
        this.globalCache = cache;
    }

    @Override
    public ExecutionResult executeMethod(MethodID id) {
        long methodID = id.id();
        Cache.CachedMethod methodData = getFromCache(methodID);
        long count = updateAndGetInvocationCount(methodID);
        int level = (methodData != null) ? methodData.level : 0;

        if (count == THRESHOLD_L1 && level == 0) {
            startCompilation(id, 1);
        } else if (count == THRESHOLD_L2 && level != 2) {
            startCompilation(id, 2);
        }

        Future<?> future = compilationFutures.get(methodID);
        if (future != null) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Compilation failed", e);
            }
        }
        return (methodData != null) ? exec.execute(methodData.compiledMethod) : exec.interpret(id);
    }

    private Cache.CachedMethod getFromCache(long methodID) {
        return globalCache.get(methodID);
    }

    private long updateAndGetInvocationCount(long methodID) {
        long newCount = invocationCounts.getOrDefault(methodID, 0L) + 1;
        invocationCounts.put(methodID, newCount);
        return newCount;
    }

    private void startCompilation(MethodID id, int level) {
        long methodID = id.id();
        Future<?> future = compilerPool.submit(() -> {
            CompiledMethod compiled = (level == 1) ? compiler.compile_l1(id) : compiler.compile_l2(id);
            globalCache.put(methodID, compiled, level);
        });
        compilationFutures.put(methodID, future);
    }
}