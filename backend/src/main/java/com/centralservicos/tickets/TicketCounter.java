package com.centralservicos.tickets;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Sequência anual de chamados de uma unidade. A unidade faz parte da chave e é sempre informada explicitamente. */
@Entity
@Table(name = "unit_ticket_counter")
@IdClass(TicketCounter.Key.class)
class TicketCounter {

    @Id
    @Convert(disableConversion = true)
    private UUID unitId;
    @Id
    private Integer counterYear;
    private long nextValue;
    @Version
    private Long rowVersion;

    protected TicketCounter() {
    }

    TicketCounter(UUID unitId, int year) {
        this.unitId = unitId;
        this.counterYear = year;
        this.nextValue = 1L;
    }

    long next() {
        return nextValue++;
    }

    static class Key implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private UUID unitId;
        private Integer counterYear;

        protected Key() {
        }

        Key(UUID unitId, Integer counterYear) {
            this.unitId = unitId;
            this.counterYear = counterYear;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(unitId, key.unitId)
                    && Objects.equals(counterYear, key.counterYear);
        }

        @Override
        public int hashCode() {
            return Objects.hash(unitId, counterYear);
        }
    }
}
