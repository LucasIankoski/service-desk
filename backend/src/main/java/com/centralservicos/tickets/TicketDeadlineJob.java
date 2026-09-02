package com.centralservicos.tickets;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class TicketDeadlineJob {

    private final TicketService tickets;

    TicketDeadlineJob(TicketService tickets) {
        this.tickets = tickets;
    }

    @Scheduled(fixedDelayString = "${app.deadline-scan-delay:PT5M}")
    void scan() {
        tickets.processDeadlineNotifications();
    }
}
