package org.nsu.syspro.parprog.solution;

import org.nsu.syspro.parprog.external.CompiledMethod;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Cache {
    public static class CachedMethod  {
        public final CompiledMethod compiledMethod;
        public final int level;

        public CachedMethod (CompiledMethod compiledMethod, int level) {
            this.compiledMethod = compiledMethod;
            this.level = level;
        }
    }

    private final Map<Long, CachedMethod> methodMap = new HashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public void put(long id, CompiledMethod compiledCode, int level) {
        lock.writeLock().lock();
        try {
            CachedMethod existing = methodMap.get(id);
            int currentLevel = (existing != null) ? existing.level : 0;
            if (level > currentLevel) {
                methodMap.put(id, new CachedMethod(compiledCode, level));
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public CachedMethod get(long id) {
        lock.readLock().lock();
        try {
            return methodMap.get(id);
        } finally {
            lock.readLock().unlock();
        }
    }
}