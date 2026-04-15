package com.paystream.paystream;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Service
public class RedisService {

    private final RedisTemplate<String, String> redisTemplate;

    public RedisService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void recordPaymentResult(PaymentProcessor processor, boolean success, long latencyMs){

        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm");
        String timeKey = now.format(formatter); // → "2026-04-13-14:32"

        // On every payment:
        String key= "processor:"+ processor.name()+ ":"+ timeKey;

        // only set TTL if key didn't exist before
        Long total = redisTemplate.opsForHash().increment(key, "totalCount", 1);
        if (total == 1) {
            redisTemplate.expire(key, 11, TimeUnit.MINUTES);
        }
        if (success) {
            redisTemplate.opsForHash().increment(key, "successCount", 1);
        }
        redisTemplate.opsForHash().increment(key, "totalLatency", latencyMs);

    }

    public ProcessorMetrics getMetrics(PaymentProcessor processor){

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm");

        long count= 0;
        long success= 0;
        long latency= 0;
        for (int i = 0; i < 10; i++) {
            LocalDateTime time = LocalDateTime.now().minusMinutes(i);
            String timeKey = time.format(formatter);
            String key = "processor:" + processor.name() + ":" + timeKey;
            // read from Redis and sum up

            Object rawCount = redisTemplate.opsForHash().get(key, "totalCount");
            // raw could be null if no payments in that minute
            // raw could be "47" if 47 payments happened
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
}