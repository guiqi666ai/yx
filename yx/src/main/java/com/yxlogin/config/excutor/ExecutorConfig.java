package com.yxlogin.config.excutor;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class ExecutorConfig {
    @Bean("userExecutor")
    public ExecutorService userExecutor(){
        return new ThreadPoolExecutor(
                21,
                21,
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(500),
                new ThreadFactory() {
                    private final AtomicInteger idx = new AtomicInteger(0);
                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "user-" + idx.getAndIncrement());
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
