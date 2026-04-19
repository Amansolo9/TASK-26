// Shared portal formatters used across Thymeleaf pages for client-side
// display tweaks (money, date/time, phone masking).
// Exposed both as an ES module (for Vitest) and as window.LexiBridgeFormat
// (for classic script usage in templates).

export function formatCents(cents) {
    if (cents === null || cents === undefined) return "-";
    const n = Number(cents);
    if (!Number.isFinite(n)) return "-";
    const dollars = n / 100;
    return dollars.toLocaleString("en-US", { style: "currency", currency: "USD" });
}

export function maskPhone(raw) {
    if (raw === null || raw === undefined) return "";
    const digits = String(raw).replace(/\D/g, "");
    if (digits.length < 4) return "***";
    return "***-***-" + digits.slice(-4);
}

export function truncate(text, limit) {
    if (text === null || text === undefined) return "";
    const s = String(text);
    if (limit === undefined || limit === null || limit < 0) return s;
    if (s.length <= limit) return s;
    if (limit <= 1) return s.slice(0, limit);
    return s.slice(0, limit - 1) + "\u2026";
}

export function formatLocalDateTime(isoString) {
    if (!isoString) return "";
    const d = new Date(isoString);
    if (Number.isNaN(d.getTime())) return "";
    const pad = (n) => String(n).padStart(2, "0");
    return d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate()) +
        " " + pad(d.getHours()) + ":" + pad(d.getMinutes());
}

export function bandForSla(minutesRemaining) {
    if (minutesRemaining === null || minutesRemaining === undefined) return "unknown";
    const m = Number(minutesRemaining);
    if (!Number.isFinite(m)) return "unknown";
    if (m < 0) return "overdue";
    if (m < 60) return "critical";
    if (m < 240) return "warning";
    return "ok";
}

if (typeof window !== "undefined") {
    window.LexiBridgeFormat = { formatCents, maskPhone, truncate, formatLocalDateTime, bandForSla };
}
