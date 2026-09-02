package com.centralservicos.tickets;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "ticket_counter")
class TicketCounter {

    @Id
    private Integer counterYear;
    private long nextValue;
    @Version
    private Long rowVersion;

    protected TicketCounter() {
    }

    TicketCounter(int year) {
        this.counterYear = year;
        this.nextValue = 1L;
    }

    long next() {
        return nextValue++;
    }
}
