package com.navgrow.util;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class OrderNumberGenerator {
    private final AtomicInteger counter = new AtomicInteger(1);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public String generate() {
        return "NGO-" + LocalDateTime.now().format(FMT) + "-" +
               String.format("%04d", counter.getAndIncrement());
    }
}