// Live-preview binding for the content editor page.
// Shared by the portal HTML (loaded as a classic script tag exposing
// window.LexiBridgeContentPreview) and by Vitest specs (which import named exports).

export function bindLivePreview(doc, map) {
    if (!doc || !Array.isArray(map)) {
        throw new Error("bindLivePreview requires (document, map[])");
    }

    const cleanupFns = [];
    map.forEach(function (pair) {
        const inputId = pair[0];
        const outputId = pair[1];
        const input = doc.getElementById(inputId);
        const output = doc.getElementById(outputId);
        if (!input || !output) {
            return;
        }
        const handler = function () {
            output.textContent = input.value && input.value.length > 0 ? input.value : "-";
        };
        handler();
        input.addEventListener("input", handler);
        cleanupFns.push(function () {
            input.removeEventListener("input", handler);
        });
    });

    return function unbind() {
        cleanupFns.forEach(function (fn) { fn(); });
    };
}

if (typeof window !== "undefined") {
    window.LexiBridgeContentPreview = { bindLivePreview };
}
