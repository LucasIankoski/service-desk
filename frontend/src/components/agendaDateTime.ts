import { Temporal } from "temporal-polyfill";

export type AgendaPeriodFields = {
  allDay: boolean;
  startDate: string;
  endDate: string;
  startDateTime: string;
  endDateTime: string;
};

export function instantToCalendarValue(value: string, allDay: boolean, timeZone: string) {
  if (!allDay) return value;
  return Temporal.Instant.from(value).toZonedDateTimeISO(timeZone).toPlainDate().toString();
}

export function periodFieldsFromInstants(startAt: string, endAt: string, allDay: boolean,
                                         timeZone: string): AgendaPeriodFields {
  const start = Temporal.Instant.from(startAt).toZonedDateTimeISO(timeZone);
  const end = Temporal.Instant.from(endAt).toZonedDateTimeISO(timeZone);
  return {
    allDay,
    startDate: start.toPlainDate().toString(),
    endDate: allDay ? end.toPlainDate().subtract({ days: 1 }).toString() : end.toPlainDate().toString(),
    startDateTime: start.toPlainDateTime().toString({ smallestUnit: "minute" }),
    endDateTime: end.toPlainDateTime().toString({ smallestUnit: "minute" })
  };
}

export function periodFieldsFromSelection(start: string, end: string, allDay: boolean,
                                           timeZone: string): AgendaPeriodFields {
  if (allDay) {
    return {
      allDay: true,
      startDate: start.slice(0, 10),
      endDate: Temporal.PlainDate.from(end.slice(0, 10)).subtract({ days: 1 }).toString(),
      startDateTime: "",
      endDateTime: ""
    };
  }
  return periodFieldsFromInstants(Temporal.Instant.from(start).toString(),
    Temporal.Instant.from(end).toString(), false, timeZone);
}

export function defaultAgendaPeriod(timeZone: string): AgendaPeriodFields {
  const today = Temporal.Now.zonedDateTimeISO(timeZone).toPlainDate();
  return {
    allDay: true,
    startDate: today.toString(),
    endDate: today.toString(),
    startDateTime: "",
    endDateTime: ""
  };
}

export function periodFieldsToInstants(fields: AgendaPeriodFields, timeZone: string) {
  if (fields.allDay) {
    const startAt = Temporal.PlainDate.from(fields.startDate).toZonedDateTime(timeZone).toInstant();
    const endAt = Temporal.PlainDate.from(fields.endDate).add({ days: 1 })
      .toZonedDateTime(timeZone).toInstant();
    return { startAt: startAt.toString(), endAt: endAt.toString() };
  }
  const startAt = Temporal.PlainDateTime.from(fields.startDateTime).toZonedDateTime(timeZone).toInstant();
  const endAt = Temporal.PlainDateTime.from(fields.endDateTime).toZonedDateTime(timeZone).toInstant();
  return { startAt: startAt.toString(), endAt: endAt.toString() };
}

export function formatAgendaPeriod(startAt: string, endAt: string, allDay: boolean, timeZone: string) {
  const start = Temporal.Instant.from(startAt).toZonedDateTimeISO(timeZone);
  const end = Temporal.Instant.from(endAt).toZonedDateTimeISO(timeZone);
  const dateFormatter = new Intl.DateTimeFormat("pt-BR", { dateStyle: "long", timeZone });
  if (allDay) {
    const inclusiveEnd = end.subtract({ days: 1 });
    const startDate = new Date(start.epochMilliseconds);
    const endDate = new Date(inclusiveEnd.epochMilliseconds);
    return start.toPlainDate().equals(inclusiveEnd.toPlainDate())
      ? dateFormatter.format(startDate)
      : `${dateFormatter.format(startDate)} a ${dateFormatter.format(endDate)}`;
  }
  const formatter = new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short",
    timeZone
  });
  return `${formatter.format(new Date(start.epochMilliseconds))} a ${formatter.format(new Date(end.epochMilliseconds))}`;
}
