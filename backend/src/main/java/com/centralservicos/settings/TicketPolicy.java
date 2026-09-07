package com.centralservicos.settings;

import java.time.ZoneId;

public record TicketPolicy(int attachmentLimitMb, int reopenDays, ZoneId zoneId) {
}
