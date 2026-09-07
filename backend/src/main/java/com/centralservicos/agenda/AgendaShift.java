package com.centralservicos.agenda;

import java.time.LocalTime;

public enum AgendaShift {
    MORNING(6, 12), AFTERNOON(12, 18), NIGHT(18, 0);

    private final LocalTime start;
    private final LocalTime end;

    AgendaShift(int start, int end) {
        this.start = LocalTime.of(start, 0);
        this.end = LocalTime.of(end, 0);
    }

    LocalTime start() { return start; }
    LocalTime end() { return end; }
}
