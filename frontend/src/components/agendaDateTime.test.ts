import { describe, expect, it } from "vitest";
import {
  instantToCalendarValue,
  periodFieldsFromInstants,
  periodFieldsToInstants
} from "./agendaDateTime";

const timeZone = "America/Sao_Paulo";

describe("agenda date and time conversion", () => {
  it("converts local timed fields to UTC instants and back", () => {
    const instants = periodFieldsToInstants({
      allDay: false,
      startDate: "",
      endDate: "",
      startDateTime: "2026-09-10T09:30",
      endDateTime: "2026-09-10T11:00"
    }, timeZone);

    expect(instants).toEqual({
      startAt: "2026-09-10T12:30:00Z",
      endAt: "2026-09-10T14:00:00Z"
    });
    expect(periodFieldsFromInstants(instants.startAt, instants.endAt, false, timeZone))
      .toMatchObject({
        startDate: "2026-09-10",
        endDate: "2026-09-10",
        startDateTime: "2026-09-10T09:30",
        endDateTime: "2026-09-10T11:00"
      });
  });

  it("stores inclusive all-day form dates using an exclusive end", () => {
    const instants = periodFieldsToInstants({
      allDay: true,
      startDate: "2026-09-10",
      endDate: "2026-09-12",
      startDateTime: "",
      endDateTime: ""
    }, timeZone);

    expect(instants).toEqual({
      startAt: "2026-09-10T03:00:00Z",
      endAt: "2026-09-13T03:00:00Z"
    });
    expect(instantToCalendarValue(instants.startAt, true, timeZone)).toBe("2026-09-10");
    expect(instantToCalendarValue(instants.endAt, true, timeZone)).toBe("2026-09-13");
    expect(periodFieldsFromInstants(instants.startAt, instants.endAt, true, timeZone))
      .toMatchObject({ startDate: "2026-09-10", endDate: "2026-09-12" });
  });
});
