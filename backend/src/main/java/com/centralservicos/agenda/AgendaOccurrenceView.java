package com.centralservicos.agenda;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AgendaOccurrenceView(UUID id, LocalDate date, String body, UUID createdById,
                                  String createdByName, long version, Instant createdAt, Instant updatedAt) {}
