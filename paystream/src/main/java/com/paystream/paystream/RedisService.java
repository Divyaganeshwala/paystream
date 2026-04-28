package com.paystream.paystream;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Service
public class RedisService {

    private final RedisTemplate<String, String> redisTemplate;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm");

    public RedisService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void recordPaymentResult(PaymentProcessor processor, boolean success, long latencyMs) {
        String key = "processor:" + processor.name() + ":" + LocalDateTime.now().format(formatter);
        Long total = redisTemplate.opsForHash().increment(key, "totalCount", 1);
        if (total == 1) {
            redisTemplate.expire(key, 11, TimeUnit.MINUTES);
        }
        if (success) {
            redisTemplate.opsForHash().increment(key, "successCount", 1);
        }
        redisTemplate.opsForHash().increment(key, "totalLatency", latencyMs);
    }

    public ProcessorMetrics getMetrics(PaymentProcessor processor) {
        long count = 0, success = 0, latency = 0;
        for (int i = 0; i < 10; i++) {
            String key = "processor:" + processor.name() + ":" +
                    LocalDateTime.now().minusMinutes(i).format(formatter);
            Object rawCount = redisTemplate.opsForHash().get(key, "totalCount");
            Object rawSuccess = redisTemplate.opsForHash().get(key, "successCount");
            Object rawLatency = redisTemplate.opsForHash().get(key, "totalLatency");
            count += rawCount == null ? 0L : Long.parseLong(rawCount.toString());
            success += rawSuccess == null ? 0L : Long.parseLong(rawSuccess.toString());
            latency += rawLatency == null ? 0L : Long.parseLong(rawLatency.toString());
        }
        double successRate = count == 0 ? 100.0 : (success * 100.0) / count;
        double avgLatency = count == 0 ? 0.0 : (double) latency / count;
        return new ProcessorMetrics(successRate, avgLatency);
    }

    public long getLastMinuteCount(PaymentProcessor processor) {
        String key = "processor:" + processor.name() + ":" +
                LocalDateTime.now().format(formatter);
        Object raw = redisTemplate.opsForHash().get(key, "totalCount");
        return raw == null ? 0L : Long.parseLong(raw.toString());
    }

    public void flushAll() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }
}