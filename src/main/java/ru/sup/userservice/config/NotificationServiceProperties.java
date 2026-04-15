package ru.sup.userservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "notification-service")
public class NotificationServiceProperties {
    private String host = "localhost";
    private int port = 9090;
    private long deadlineMs = 3000;
}
