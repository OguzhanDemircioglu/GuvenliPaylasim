package com.server.modules.verification;

import com.server.modules.notification.SmsGateway;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Doğrulama mesajları kuyruktan, istek thread'inden ayrı gönderilir (Security.md S4): cevap süresi mesajın gidip
 * gitmediğine, dolayısıyla numaranın kayıtlı olup olmadığına bağlı olmaz. Kuyruk sınırlıdır; dolarsa mesaj
 * gönderilmez ve hata loglanır.
 */
@Slf4j
@Component
public class VerificationMessageSender {

    private static final int QUEUE_CAPACITY = 500;

    private final SmsGateway gateway;
    private final ThreadPoolExecutor executor;

    public VerificationMessageSender(SmsGateway gateway) {
        this.gateway = gateway;
        this.executor = new ThreadPoolExecutor(1, 2, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                Thread.ofVirtual().name("verification-sender-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
    }

    public void enqueue(String phone, String message) {
        try {
            executor.execute(() -> send(phone, message));
        } catch (RejectedExecutionException e) {
            log.error("Verification message queue is full; message to {} dropped", Masks.phone(phone));
        }
    }

    private void send(String phone, String message) {
        try {
            gateway.send(phone, message);
        } catch (RuntimeException e) {
            log.error("Verification message to {} could not be sent: {}", Masks.phone(phone), e.getMessage());
        }
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }
}
