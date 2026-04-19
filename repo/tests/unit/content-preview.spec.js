// Frontend unit tests for the portal's content-preview module.
// Runs under Vitest with the jsdom environment.

import { describe, it, expect, beforeEach } from "vitest";
import { bindLivePreview } from "../../src/main/resources/static/js/content-preview.js";

function render(html) {
    document.body.innerHTML = html;
}

describe("bindLivePreview", () => {
    beforeEach(() => {
        document.body.innerHTML = "";
    });

    it("mirrors input value into the preview node on first bind", () => {
        render(`
            <input id="term" value="hello" />
            <span id="previewTerm"></span>
        `);
        bindLivePreview(document, [["term", "previewTerm"]]);
        expect(document.getElementById("previewTerm").textContent).toBe("hello");
    });

    it("falls back to '-' when input is empty", () => {
        render(`
            <input id="term" value="" />
            <span id="previewTerm">stale</span>
        `);
        bindLivePreview(document, [["term", "previewTerm"]]);
        expect(document.getElementById("previewTerm").textContent).toBe("-");
    });

    it("updates preview on subsequent input events", () => {
        render(`
            <input id="term" value="" />
            <span id="previewTerm"></span>
        `);
        bindLivePreview(document, [["term", "previewTerm"]]);

        const input = document.getElementById("term");
        input.value = "lexicon";
        input.dispatchEvent(new Event("input"));

        expect(document.getElementById("previewTerm").textContent).toBe("lexicon");
    });

    it("silently skips pairs whose nodes are missing", () => {
        render(`<input id="term" value="ok" /><span id="previewTerm"></span>`);
        expect(() => bindLivePreview(document, [
            ["term", "previewTerm"],
            ["ghost", "previewGhost"]
        ])).not.toThrow();
        expect(document.getElementById("previewTerm").textContent).toBe("ok");
    });

    it("unbind removes the input listener", () => {
        render(`
            <input id="term" value="init" />
            <span id="previewTerm"></span>
        `);
        const unbind = bindLivePreview(document, [["term", "previewTerm"]]);

        unbind();

        const input = document.getElementById("term");
        input.value = "changed";
        input.dispatchEvent(new Event("input"));
        expect(document.getElementById("previewTerm").textContent).toBe("init");
    });

    it("rejects invalid arguments", () => {
        expect(() => bindLivePreview(null, [])).toThrow();
        expect(() => bindLivePreview(document, null)).toThrow();
    });
});
