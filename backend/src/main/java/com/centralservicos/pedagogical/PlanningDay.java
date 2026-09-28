package com.centralservicos.pedagogical;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDate;
@Embeddable
class PlanningDay {
    LocalDate activityDate;
    boolean noClass;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) String reason;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) String proposal;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) String objectives;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) String development;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) String resources;
    protected PlanningDay() {}
    PlanningDay(LocalDate date, PedagogicalViews.DayInput input) {
        activityDate = date; noClass = input.noClass(); reason = input.reason();
        proposal = input.proposal(); objectives = input.objectives();
        development = input.development(); resources = input.resources();
    }
}
