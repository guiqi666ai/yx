package com.yxlogin.service.impl;

import com.yxlogin.service.IdSegmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class IdSegmentServiceImplTest {
    @Autowired
    private IdSegmentService idGenerator;

    //@Test
    public void testConcurrent() throws Exception {
        int threads = 5;
        int perThread = 20;
        int total = threads * perThread;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        // 用 ConcurrentHashMap 记录所有 ID，putIfAbsent 返回非 null 说明重复
        ConcurrentHashMap<Long, Boolean> ids = new ConcurrentHashMap<>(total);

        long start = System.currentTimeMillis();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    for (int j = 0; j < perThread; j++) {
                        long id = idGenerator.nextId("user");
                        if (ids.putIfAbsent(id, Boolean.TRUE) != null) {
                            throw new IllegalStateException("重复 ID: " + id);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        pool.shutdown();
        long cost = System.currentTimeMillis() - start;

        System.out.println("生成总数: " + ids.size());
        System.out.println("预期总数: " + total);
        System.out.println("耗时: " + cost + "ms");
        System.out.println("QPS: " + (total * 1000L / cost));

        if (ids.size() != total) {
            throw new IllegalStateException("ID 数量不对，可能重复");
        }
        System.out.println("并发测试通过，无重复");
    }
}