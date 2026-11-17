package com.ispbilling.notification;

import java.time.OffsetDateTime;

public record NotificationView(Long id, String channel, String recipient, String status, int attempts,
                               String lastError, OffsetDateTime nextAttemptAt, OffsetDateTime sentAt) {
}
