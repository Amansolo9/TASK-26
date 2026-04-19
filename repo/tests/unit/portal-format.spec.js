import { describe, it, expect } from "vitest";
import {
    formatCents,
    maskPhone,
    truncate,
    formatLocalDateTime,
    bandForSla
} from "../../src/main/resources/static/js/portal-format.js";

describe("formatCents", () => {
    it("formats integer cents as USD currency", () => {
        expect(formatCents(12345)).toBe("$123.45");
    });

    it("handles zero", () => {
        expect(formatCents(0)).toBe("$0.00");
    });

    it("returns '-' for null / undefined / NaN", () => {
        expect(formatCents(null)).toBe("-");
        expect(formatCents(undefined)).toBe("-");
        expect(formatCents("not-a-number")).toBe("-");
    });

    it("formats negative amounts as parenthesized or signed depending on locale", () => {
        const out = formatCents(-500);
        expect(out).toMatch(/\$5\.00|\(\$5\.00\)|-\$5\.00/);
    });
});

describe("maskPhone", () => {
    it("keeps the last four digits and masks the rest", () => {
        expect(maskPhone("5551234567")).toBe("***-***-4567");
    });

    it("strips non-digit characters before masking", () => {
        expect(maskPhone("(555) 123-4567")).toBe("***-***-4567");
    });

    it("returns asterisks for very short input", () => {
        expect(maskPhone("12")).toBe("***");
    });

    it("returns empty string for null/undefined", () => {
        expect(maskPhone(null)).toBe("");
        expect(maskPhone(undefined)).toBe("");
    });
});

describe("truncate", () => {
    it("returns the input when shorter than limit", () => {
        expect(truncate("short", 100)).toBe("short");
    });

    it("truncates with ellipsis when longer than limit", () => {
        expect(truncate("abcdefghij", 5)).toBe("abcd\u2026");
    });

    it("returns original string when limit is undefined or negative", () => {
        expect(truncate("abc")).toBe("abc");
        expect(truncate("abc", -1)).toBe("abc");
    });

    it("handles edge case limit=0 or 1", () => {
        expect(truncate("abcdef", 0)).toBe("");
        expect(truncate("abcdef", 1)).toBe("a");
    });

    it("returns empty string for null/undefined input", () => {
        expect(truncate(null, 5)).toBe("");
        expect(truncate(undefined, 5)).toBe("");
    });
});

describe("formatLocalDateTime", () => {
    it("formats ISO dates as YYYY-MM-DD HH:mm", () => {
        const fmt = formatLocalDateTime("2026-04-19T14:30:00");
        expect(fmt).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/);
    });

    it("returns empty string for invalid input", () => {
        expect(formatLocalDateTime("not-a-date")).toBe("");
        expect(formatLocalDateTime(null)).toBe("");
        expect(formatLocalDateTime("")).toBe("");
    });
});

describe("bandForSla", () => {
    it("returns overdue for negative minutes", () => {
        expect(bandForSla(-5)).toBe("overdue");
    });

    it("returns critical for < 60", () => {
        expect(bandForSla(30)).toBe("critical");
        expect(bandForSla(59)).toBe("critical");
    });

    it("returns warning for < 240", () => {
        expect(bandForSla(60)).toBe("warning");
        expect(bandForSla(239)).toBe("warning");
    });

    it("returns ok for >= 240", () => {
        expect(bandForSla(240)).toBe("ok");
        expect(bandForSla(1000)).toBe("ok");
    });

    it("returns unknown for null/undefined/NaN", () => {
        expect(bandForSla(null)).toBe("unknown");
        expect(bandForSla(undefined)).toBe("unknown");
        expect(bandForSla("x")).toBe("unknown");
    });
});
