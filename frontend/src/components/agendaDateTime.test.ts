import type { AgendaItem, AgendaShift } from "../api/types";
import { describe, expect, it } from "vitest";
import {
  agendaCalendarBlocks,
  shiftInstants,
  formatAgendaPeriod,
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

describe("daily shifts", () => {
  it.each([
    ["MORNING", "2026-09-30T09:00:00Z", "2026-10-02T15:00:00Z"],
    ["AFTERNOON", "2026-09-30T15:00:00Z", "2026-10-02T21:00:00Z"],
    ["NIGHT", "2026-09-30T21:00:00Z", "2026-10-03T03:00:00Z"]
  ] as const)("round trips %s with inclusive final dates", (shift, startAt, endAt) => {
    const period = shiftInstants("2026-09-30", "2026-10-02", shift, timeZone);
    expect(period).toEqual({ startAt, endAt });
    expect(periodFieldsFromInstants(startAt, endAt, false, timeZone, shift))
      .toMatchObject({ startDate: "2026-09-30", endDate: "2026-10-02", shift });
    expect(periodFieldsToInstants({ allDay: false, shift, startDate: "2026-09-30", endDate: "2026-10-02", startDateTime: "", endDateTime: "" }, timeZone)).toEqual(period);
  });

  function item(shift: AgendaShift): AgendaItem {
    return { id: "task", kind: "INTERNAL_DEMAND", title: "Task", allDay: false, shift,
      ...shiftInstants("2026-09-30", "2026-10-04", shift, timeZone),
      version: 0, createdAt: "", updatedAt: "" };
  }

  it.each(["MORNING", "AFTERNOON", "NIGHT"] as const)("expands %s only within the visible range including weekends", (shift) => {
    const blocks = agendaCalendarBlocks(item(shift), timeZone, { start: "2026-10-01T03:00:00Z", end: "2026-10-05T03:00:00Z" });
    expect(blocks).toHaveLength(4);
    expect(new Set(blocks.map((block) => block.id)).size).toBe(4);
    expect(blocks.every((block) => block.extendedProps.itemId === "task")).toBe(true);
    blocks.forEach((block) => expect(Date.parse(block.end) - Date.parse(block.start)).toBe(6 * 3600000));
    expect(Date.parse(blocks[1].start) - Date.parse(blocks[0].end)).toBe(18 * 3600000);
  });

  it("does not display nighttime end as an additional selected day", () => {
    const period = shiftInstants("2026-09-30", "2026-09-30", "NIGHT", timeZone);
    expect(formatAgendaPeriod(period.startAt, period.endAt, false, timeZone, "NIGHT")).toBe("30/09/2026 · Noite");
    expect(agendaCalendarBlocks(item("NIGHT"), timeZone, { start: "2026-10-01T03:00:00Z", end: "2026-10-01T21:00:00Z" })).toEqual([]);
  });

  it("rejects reversed dates and does not infer shifts for legacy items", () => {
    expect(() => shiftInstants("2026-10-02", "2026-10-01", "NIGHT", timeZone)).toThrow();
    const legacy = { ...item("MORNING"), shift: null };
    expect(agendaCalendarBlocks(legacy, timeZone)).toHaveLength(1);
    expect(periodFieldsFromInstants(legacy.startAt, legacy.endAt, false, timeZone).shift).toBeUndefined();
  });
});
