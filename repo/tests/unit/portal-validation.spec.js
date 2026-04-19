import { describe, it, expect } from "vitest";
import {
    isNonEmpty,
    isValidEmail,
    isValidDurationMinutes,
    isValidCidr,
    validateLeaveForm
} from "../../src/main/resources/static/js/portal-validation.js";

describe("isNonEmpty", () => {
    it("accepts non-empty trimmed strings", () => {
        expect(isNonEmpty("abc")).toBe(true);
        expect(isNonEmpty("  a  ")).toBe(true);
    });

    it("rejects empty, whitespace, null, non-string", () => {
        expect(isNonEmpty("")).toBe(false);
        expect(isNonEmpty("   ")).toBe(false);
        expect(isNonEmpty(null)).toBe(false);
        expect(isNonEmpty(undefined)).toBe(false);
        expect(isNonEmpty(123)).toBe(false);
    });
});

describe("isValidEmail", () => {
    it("accepts typical email addresses", () => {
        expect(isValidEmail("user@example.com")).toBe(true);
        expect(isValidEmail("first.last+tag@example.co.uk")).toBe(true);
    });

    it("rejects missing @, missing domain, spaces", () => {
        expect(isValidEmail("noatsign")).toBe(false);
        expect(isValidEmail("no-domain@")).toBe(false);
        expect(isValidEmail("@no-local")).toBe(false);
        expect(isValidEmail("spa ce@example.com")).toBe(false);
    });

    it("rejects empty / null / non-string", () => {
        expect(isValidEmail("")).toBe(false);
        expect(isValidEmail(null)).toBe(false);
        expect(isValidEmail(42)).toBe(false);
    });

    it("rejects excessively long local parts", () => {
        const longLocal = "a".repeat(250) + "@example.com";
        expect(isValidEmail(longLocal)).toBe(false);
    });
});

describe("isValidDurationMinutes", () => {
    it("accepts positive multiples of 15", () => {
        expect(isValidDurationMinutes(15)).toBe(true);
        expect(isValidDurationMinutes(30)).toBe(true);
        expect(isValidDurationMinutes(480)).toBe(true);
        expect(isValidDurationMinutes("30")).toBe(true); // coerces
    });

    it("rejects non-multiples of 15", () => {
        expect(isValidDurationMinutes(10)).toBe(false);
        expect(isValidDurationMinutes(16)).toBe(false);
    });

    it("rejects zero, negatives, and non-integers", () => {
        expect(isValidDurationMinutes(0)).toBe(false);
        expect(isValidDurationMinutes(-15)).toBe(false);
        expect(isValidDurationMinutes(15.5)).toBe(false);
        expect(isValidDurationMinutes(null)).toBe(false);
        expect(isValidDurationMinutes("abc")).toBe(false);
    });
});

describe("isValidCidr", () => {
    it("accepts valid IPv4 CIDR blocks", () => {
        expect(isValidCidr("0.0.0.0/0")).toBe(true);
        expect(isValidCidr("10.0.0.0/8")).toBe(true);
        expect(isValidCidr("192.168.1.1/32")).toBe(true);
    });

    it("rejects out-of-range octets", () => {
        expect(isValidCidr("256.0.0.0/8")).toBe(false);
        expect(isValidCidr("10.0.0.0/33")).toBe(false);
    });

    it("rejects malformed strings", () => {
        expect(isValidCidr("10.0.0.0")).toBe(false);
        expect(isValidCidr("10.0/8")).toBe(false);
        expect(isValidCidr("")).toBe(false);
        expect(isValidCidr(null)).toBe(false);
    });
});

describe("validateLeaveForm", () => {
    const baseValid = {
        leaveType: "ANNUAL_LEAVE",
        startDate: "2026-05-01",
        endDate: "2026-05-01",
        durationMinutes: 480
    };

    it("reports valid for a complete submission", () => {
        const result = validateLeaveForm(baseValid);
        expect(result.valid).toBe(true);
        expect(result.errors).toHaveLength(0);
    });

    it("reports an error per missing required field", () => {
        const result = validateLeaveForm({
            leaveType: "",
            startDate: "",
            endDate: "",
            durationMinutes: null
        });
        expect(result.valid).toBe(false);
        const fields = result.errors.map((e) => e.field);
        expect(fields).toEqual(expect.arrayContaining(["leaveType", "startDate", "endDate", "durationMinutes"]));
    });

    it("flags end date earlier than start date", () => {
        const result = validateLeaveForm({
            ...baseValid,
            startDate: "2026-05-10",
            endDate: "2026-05-01"
        });
        expect(result.valid).toBe(false);
        expect(result.errors.some((e) => e.field === "endDate")).toBe(true);
    });

    it("flags invalid duration regardless of dates", () => {
        const result = validateLeaveForm({ ...baseValid, durationMinutes: 13 });
        expect(result.valid).toBe(false);
        expect(result.errors.some((e) => e.field === "durationMinutes")).toBe(true);
    });
});
