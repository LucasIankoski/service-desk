import { Temporal } from "temporal-polyfill";
import type { AgendaItem, AgendaShift } from "../api/types";

export const shiftLabels: Record<AgendaShift, string> = { MORNING: "Manhã", AFTERNOON: "Tarde", NIGHT: "Noite" };
const shiftHours = { MORNING: [6, 12], AFTERNOON: [12, 18], NIGHT: [18, 24] } as const;

export type AgendaPeriodFields = {
  allDay: boolean;
  shift?: AgendaShift | null;
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
                                         timeZone: string, shift?: AgendaShift | null): AgendaPeriodFields {
  const start = Temporal.Instant.from(startAt).toZonedDateTimeISO(timeZone);
  const end = Temporal.Instant.from(endAt).toZonedDateTimeISO(timeZone);
  return {
    allDay,
    shift,
    startDate: start.toPlainDate().toString(),
    endDate: allDay || shift === "NIGHT" ? end.toPlainDate().subtract({ days: 1 }).toString() : end.toPlainDate().toString(),
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
  if (!fields.allDay && fields.shift) {
    return shiftInstants(fields.startDate, fields.endDate, fields.shift, timeZone);
  }
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

export function formatAgendaPeriod(startAt: string, endAt: string, allDay: boolean, timeZone: string, shift?: AgendaShift | null) {
  const start = Temporal.Instant.from(startAt).toZonedDateTimeISO(timeZone);
  const end = Temporal.Instant.from(endAt).toZonedDateTimeISO(timeZone);
  const dateFormatter = new Intl.DateTimeFormat("pt-BR", { dateStyle: "long", timeZone });
  if (shift && !allDay) {
    const fields = periodFieldsFromInstants(startAt, endAt, false, timeZone, shift);
    const date = (value: string) => value.split("-").reverse().join("/");
    return `${date(fields.startDate)}${fields.endDate === fields.startDate ? "" : ` a ${date(fields.endDate)}`} · ${shiftLabels[shift]}`;
  }
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


export function shiftInstants(startDate: string, endDate: string, shift: AgendaShift, timeZone: string) {
  const first = Temporal.PlainDate.from(startDate);
  const last = Temporal.PlainDate.from(endDate);
  if (Temporal.PlainDate.compare(last, first) < 0) throw new RangeError("Data final anterior à inicial.");
  const [startHour, endHour] = shiftHours[shift];
  const end = endHour === 24 ? last.add({ days: 1 }) : last;
  return {
    startAt: first.toZonedDateTime({ timeZone, plainTime: { hour: startHour } }).toInstant().toString(),
    endAt: end.toZonedDateTime({ timeZone, plainTime: { hour: endHour % 24 } }).toInstant().toString()
  };
}

export function agendaCalendarBlocks(item: AgendaItem, timeZone: string, range?: { start: string; end: string }) {
  if (!item.shift || item.allDay) return [{
    id: item.id, start: instantToCalendarValue(item.startAt, item.allDay, timeZone),
    end: instantToCalendarValue(item.endAt, item.allDay, timeZone), allDay: item.allDay,
    extendedProps: { itemId: item.id }
  }];
  if (!range) return [];
  const fields = periodFieldsFromInstants(item.startAt, item.endAt, false, timeZone, item.shift);
  const rangeStart = Temporal.Instant.from(range.start);
  const rangeEnd = Temporal.Instant.from(range.end);
  let date = Temporal.PlainDate.from(fields.startDate);
  const visibleStart = rangeStart.toZonedDateTimeISO(timeZone).toPlainDate();
  if (Temporal.PlainDate.compare(date, visibleStart) < 0) date = visibleStart;
  let last = Temporal.PlainDate.from(fields.endDate);
  const visibleEnd = rangeEnd.toZonedDateTimeISO(timeZone).toPlainDate();
  if (Temporal.PlainDate.compare(last, visibleEnd) > 0) last = visibleEnd;
  const blocks = [];
  for (; Temporal.PlainDate.compare(date, last) <= 0; date = date.add({ days: 1 })) {
    const period = shiftInstants(date.toString(), date.toString(), item.shift, timeZone);
    if (Temporal.Instant.compare(period.startAt, rangeEnd) >= 0 || Temporal.Instant.compare(period.endAt, rangeStart) <= 0) continue;
    blocks.push({
      id: `${item.id}:${date}`, start: period.startAt, end: period.endAt, allDay: false,
      extendedProps: { itemId: item.id }
    });
  }
  return blocks;
}
