// Client-side validation utilities for portal forms. Kept in a shared module
// so both the portal templates and Vitest specs exercise identical logic.

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function isNonEmpty(value) {
    return typeof value === "string" && value.trim().length > 0;
}

export function isValidEmail(value) {
    if (typeof value !== "string") return false;
    const trimmed = value.trim();
    if (trimmed.length === 0 || trimmed.length > 254) return false;
    return EMAIL_RE.test(trimmed);
}

export function isValidDurationMinutes(value) {
    const n = Number(value);
    return Number.isInteger(n) && n > 0 && n % 15 === 0;
}

export function isValidCidr(value) {
    if (typeof value !== "string") return false;
    const m = value.match(/^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})\/(\d{1,2})$/);
    if (!m) return false;
    for (let i = 1; i <= 4; i++) {
        const o = Number(m[i]);
        if (!Number.isInteger(o) || o < 0 || o > 255) return false;
    }
    const prefix = Number(m[5]);
    return Number.isInteger(prefix) && prefix >= 0 && prefix <= 32;
}

export function validateLeaveForm(form) {
    const errors = [];
    if (!isNonEmpty(form.leaveType)) errors.push({ field: "leaveType", message: "leave type required" });
    if (!isNonEmpty(form.startDate)) errors.push({ field: "startDate", message: "start date required" });
    if (!isNonEmpty(form.endDate)) errors.push({ field: "endDate", message: "end date required" });
    if (!isValidDurationMinutes(form.durationMinutes)) {
        errors.push({ field: "durationMinutes", message: "duration must be positive multiple of 15" });
    }
    if (form.startDate && form.endDate && form.startDate > form.endDate) {
        errors.push({ field: "endDate", message: "end date must be >= start date" });
    }
    return { valid: errors.length === 0, errors };
}

if (typeof window !== "undefined") {
    window.LexiBridgeValidation = {
        isNonEmpty,
        isValidEmail,
        isValidDurationMinutes,
        isValidCidr,
        validateLeaveForm
    };
}
