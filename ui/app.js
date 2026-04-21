(function () {
    var SAMPLE_SPANS = [
        {
            tracerName: "demo-tracer",
            name: "HTTP GET /users/123/profile",
            kind: "SERVER",
            traceId: "596db8727cf3475a9825642fbf1421ae",
            spanId: "3174cb6bb568491a",
            parentSpanId: null,
            startEpochMillis: 1713803424220,
            endEpochMillis: 1713803424661,
            statusCode: "OK",
            statusMessage: "success",
            threadName: "main",
            attributes: {
                "http.method": "GET",
                "http.route": "/users/123/profile",
                "request.id": "req-1001"
            }
        },
        {
            tracerName: "demo-tracer",
            name: "authCheck",
            kind: "INTERNAL",
            traceId: "596db8727cf3475a9825642fbf1421ae",
            spanId: "6abe3aeebd444e53",
            parentSpanId: "3174cb6bb568491a",
            startEpochMillis: 1713803424221,
            endEpochMillis: 1713803424256,
            statusCode: "OK",
            statusMessage: "auth ok",
            threadName: "main",
            attributes: {
                "auth.type": "bearer",
                "tenant": "acme"
            }
        },
        {
            tracerName: "demo-tracer",
            name: "userBiz",
            kind: "INTERNAL",
            traceId: "596db8727cf3475a9825642fbf1421ae",
            spanId: "0608ad7beb1c4a28",
            parentSpanId: "3174cb6bb568491a",
            startEpochMillis: 1713803424263,
            endEpochMillis: 1713803424661,
            statusCode: "OK",
            statusMessage: "biz done",
            threadName: "main",
            attributes: {
                "user.id": "123456"
            }
        },
        {
            tracerName: "demo-tracer",
            name: "GET redis:user-profile",
            kind: "CLIENT",
            traceId: "596db8727cf3475a9825642fbf1421ae",
            spanId: "4478bdb9be004f2d",
            parentSpanId: "0608ad7beb1c4a28",
            startEpochMillis: 1713803424263,
            endEpochMillis: 1713803424288,
            statusCode: "OK",
            statusMessage: "cache miss",
            threadName: "main",
            attributes: {
                "cache.system": "redis",
                "cache.hit": false
            }
        },
        {
            tracerName: "demo-tracer",
            name: "childMethod",
            kind: "INTERNAL",
            traceId: "596db8727cf3475a9825642fbf1421ae",
            spanId: "19aedf2b44694ea2",
            parentSpanId: "0608ad7beb1c4a28",
            startEpochMillis: 1713803424289,
            endEpochMillis: 1713803424344,
            statusCode: "OK",
            statusMessage: "child ok",
            threadName: "main",
            attributes: {
                "user.type": "vip"
            }
        },
        {
            tracerName: "demo-tracer",
            name: "SELECT user",
            kind: "CLIENT",
            traceId: "596db8727cf3475a9825642fbf1421ae",
            spanId: "0785441d9854434e",
            parentSpanId: "0608ad7beb1c4a28",
            startEpochMillis: 1713803424344,
            endEpochMillis: 1713803424429,
            statusCode: "OK",
            statusMessage: "db ok",
            threadName: "main",
            attributes: {
                "db.system": "mysql",
                "db.statement": "select * from user where id = 123456"
            }
        },
        {
            tracerName: "demo-tracer",
            name: "auditLogTask",
            kind: "INTERNAL",
            traceId: "596db8727cf3475a9825642fbf1421ae",
            spanId: "84d13472392e4d02",
            parentSpanId: "0608ad7beb1c4a28",
            startEpochMillis: 1713803424431,
            endEpochMillis: 1713803424496,
            statusCode: "OK",
            statusMessage: "audit ok",
            threadName: "pool-1-thread-2",
            attributes: {
                "async": true,
                "task.type": "audit"
            }
        },
        {
            tracerName: "demo-tracer",
            name: "recommendationTask",
            kind: "INTERNAL",
            traceId: "596db8727cf3475a9825642fbf1421ae",
            spanId: "646ab049f3324b0f",
            parentSpanId: "0608ad7beb1c4a28",
            startEpochMillis: 1713803424431,
            endEpochMillis: 1713803424556,
            statusCode: "OK",
            statusMessage: "recommendation ok",
            threadName: "pool-1-thread-1",
            attributes: {
                "async": true,
                "task.type": "recommendation"
            }
        },
        {
            tracerName: "demo-tracer",
            name: "HTTP POST /checkout",
            kind: "SERVER",
            traceId: "108b4602364545868ee010a0b05b8b03",
            spanId: "74d0e3e5de0a456b",
            parentSpanId: null,
            startEpochMillis: 1713803424707,
            endEpochMillis: 1713803424797,
            statusCode: "ERROR",
            statusMessage: "IllegalStateException: gateway timeout",
            threadName: "main",
            attributes: {
                "http.method": "POST",
                "http.route": "/checkout",
                "exception.type": "java.lang.IllegalStateException",
                "exception.message": "gateway timeout",
                "error.handled": true
            }
        },
        {
            tracerName: "demo-tracer",
            name: "reserveInventory",
            kind: "INTERNAL",
            traceId: "108b4602364545868ee010a0b05b8b03",
            spanId: "bc8f71004be145b2",
            parentSpanId: "74d0e3e5de0a456b",
            startEpochMillis: 1713803424707,
            endEpochMillis: 1713803424747,
            statusCode: "OK",
            statusMessage: "inventory reserved",
            threadName: "main",
            attributes: {
                "sku.count": 2
            }
        },
        {
            tracerName: "demo-tracer",
            name: "POST payment-gateway",
            kind: "CLIENT",
            traceId: "108b4602364545868ee010a0b05b8b03",
            spanId: "82900da4d08f4665",
            parentSpanId: "74d0e3e5de0a456b",
            startEpochMillis: 1713803424747,
            endEpochMillis: 1713803424797,
            statusCode: "ERROR",
            statusMessage: "IllegalStateException: gateway timeout",
            threadName: "main",
            attributes: {
                "payment.provider": "mockpay",
                "payment.amount": 299,
                "exception.type": "java.lang.IllegalStateException",
                "exception.message": "gateway timeout"
            }
        }
    ];

    var state = {
        rawSpans: [],
        traces: [],
        selectedTraceId: null,
        selectedSpanId: null,
        filters: {
            status: "ALL",
            search: ""
        }
    };

    var elements = {
        summaryGrid: document.getElementById("summary-grid"),
        traceList: document.getElementById("trace-list"),
        traceCount: document.getElementById("trace-count"),
        activeTraceTitle: document.getElementById("active-trace-title"),
        activeTraceMeta: document.getElementById("active-trace-meta"),
        spanTree: document.getElementById("span-tree"),
        timeline: document.getElementById("timeline"),
        inspector: document.getElementById("inspector"),
        statusFilter: document.getElementById("status-filter"),
        searchInput: document.getElementById("search-input"),
        jsonInput: document.getElementById("json-input"),
        renderJsonBtn: document.getElementById("render-json-btn"),
        loadSampleBtn: document.getElementById("load-sample-btn"),
        fileInput: document.getElementById("file-input")
    };

    bindEvents();
    loadSample();

    function bindEvents() {
        elements.loadSampleBtn.addEventListener("click", loadSample);
        elements.renderJsonBtn.addEventListener("click", renderJsonInput);
        elements.statusFilter.addEventListener("change", function (event) {
            state.filters.status = event.target.value;
            refresh();
        });
        elements.searchInput.addEventListener("input", function (event) {
            state.filters.search = String(event.target.value || "").trim().toLowerCase();
            refresh();
        });
        elements.fileInput.addEventListener("change", function (event) {
            var file = event.target.files && event.target.files[0];
            if (!file) {
                return;
            }
            var reader = new FileReader();
            reader.onload = function () {
                elements.jsonInput.value = String(reader.result || "");
                renderJsonInput();
            };
            reader.readAsText(file, "utf-8");
        });
    }

    function loadSample() {
        elements.jsonInput.value = JSON.stringify(SAMPLE_SPANS, null, 2);
        setSpans(SAMPLE_SPANS);
    }

    function renderJsonInput() {
        try {
            var parsed = JSON.parse(elements.jsonInput.value);
            setSpans(parsed);
        } catch (error) {
            window.alert("JSON 解析失败: " + error.message);
        }
    }

    function setSpans(rawInput) {
        var list = Array.isArray(rawInput) ? rawInput : rawInput && rawInput.spans;
        if (!Array.isArray(list)) {
            throw new Error("输入必须是 span 数组，或者 { spans: [...] }");
        }

        state.rawSpans = list.map(normalizeSpan).filter(Boolean);
        state.traces = buildTraces(state.rawSpans);
        state.selectedTraceId = state.traces[0] ? state.traces[0].traceId : null;
        state.selectedSpanId = null;
        refresh();
    }

    function normalizeSpan(rawSpan) {
        if (!rawSpan) {
            return null;
        }

        var traceId = rawSpan.traceId || (rawSpan.spanContext && rawSpan.spanContext.traceId);
        var spanId = rawSpan.spanId || (rawSpan.spanContext && rawSpan.spanContext.spanId);
        if (!traceId || !spanId) {
            return null;
        }

        var attributes = rawSpan.attributes || {};
        return {
            tracerName: rawSpan.tracerName || rawSpan.tracer || "unknown-tracer",
            name: rawSpan.name || "unnamed-span",
            kind: rawSpan.kind || "INTERNAL",
            traceId: traceId,
            spanId: spanId,
            parentSpanId: rawSpan.parentSpanId || null,
            startEpochMillis: Number(rawSpan.startEpochMillis || rawSpan.start || 0),
            endEpochMillis: Number(rawSpan.endEpochMillis || rawSpan.end || rawSpan.startEpochMillis || 0),
            statusCode: rawSpan.statusCode || "UNSET",
            statusMessage: rawSpan.statusMessage || "",
            threadName: rawSpan.threadName || "unknown-thread",
            attributes: attributes
        };
    }

    function buildTraces(spans) {
        var grouped = {};
        spans.forEach(function (span) {
            if (!grouped[span.traceId]) {
                grouped[span.traceId] = [];
            }
            grouped[span.traceId].push(span);
        });

        return Object.keys(grouped).map(function (traceId) {
            var traceSpans = grouped[traceId].slice().sort(byStartThenDuration);
            var traceStart = Math.min.apply(null, traceSpans.map(function (span) {
                return span.startEpochMillis;
            }));
            var traceEnd = Math.max.apply(null, traceSpans.map(function (span) {
                return span.endEpochMillis;
            }));
            var spanMap = {};
            var childrenMap = {};
            traceSpans.forEach(function (span) {
                spanMap[span.spanId] = span;
                childrenMap[span.spanId] = [];
            });
            traceSpans.forEach(function (span) {
                if (span.parentSpanId && childrenMap[span.parentSpanId]) {
                    childrenMap[span.parentSpanId].push(span);
                }
            });
            Object.keys(childrenMap).forEach(function (spanId) {
                childrenMap[spanId].sort(byStartThenDuration);
            });

            var roots = traceSpans.filter(function (span) {
                return !span.parentSpanId || !spanMap[span.parentSpanId];
            });

            var orderedSpans = [];
            roots.forEach(function (root) {
                walk(root, 0);
            });

            function walk(span, depth) {
                orderedSpans.push({
                    span: span,
                    depth: depth
                });
                (childrenMap[span.spanId] || []).forEach(function (child) {
                    walk(child, depth + 1);
                });
            }

            var errorCount = traceSpans.filter(function (span) {
                return span.statusCode === "ERROR";
            }).length;
            var okCount = traceSpans.filter(function (span) {
                return span.statusCode === "OK";
            }).length;

            return {
                traceId: traceId,
                spans: traceSpans,
                roots: roots,
                orderedSpans: orderedSpans,
                spanMap: spanMap,
                childrenMap: childrenMap,
                start: traceStart,
                end: traceEnd,
                durationMs: traceEnd - traceStart,
                rootName: roots[0] ? roots[0].name : "unknown-root",
                rootStatus: deriveTraceStatus(traceSpans),
                errorCount: errorCount,
                okCount: okCount
            };
        }).sort(function (a, b) {
            return b.start - a.start;
        });
    }

    function deriveTraceStatus(spans) {
        if (spans.some(function (span) { return span.statusCode === "ERROR"; })) {
            return "ERROR";
        }
        if (spans.some(function (span) { return span.statusCode === "OK"; })) {
            return "OK";
        }
        return "UNSET";
    }

    function refresh() {
        var visibleTraces = filterTraces(state.traces, state.filters);
        if (!visibleTraces.some(function (trace) { return trace.traceId === state.selectedTraceId; })) {
            state.selectedTraceId = visibleTraces[0] ? visibleTraces[0].traceId : null;
            state.selectedSpanId = null;
        }

        renderSummary(visibleTraces);
        renderTraceList(visibleTraces);
        renderActiveTrace(findSelectedTrace(visibleTraces));
    }

    function filterTraces(traces, filters) {
        return traces.map(function (trace) {
            var filteredSpans = trace.orderedSpans.filter(function (item) {
                return matchesStatus(item.span, filters.status) && matchesSearch(item.span, filters.search, trace.traceId);
            });

            return {
                traceId: trace.traceId,
                spans: trace.spans,
                orderedSpans: filteredSpans,
                spanMap: trace.spanMap,
                start: trace.start,
                end: trace.end,
                durationMs: trace.durationMs,
                rootName: trace.rootName,
                rootStatus: trace.rootStatus,
                errorCount: trace.errorCount,
                okCount: trace.okCount
            };
        }).filter(function (trace) {
            return trace.orderedSpans.length > 0;
        });
    }

    function matchesStatus(span, status) {
        return status === "ALL" || span.statusCode === status;
    }

    function matchesSearch(span, search, traceId) {
        if (!search) {
            return true;
        }
        var haystacks = [
            span.name,
            span.kind,
            span.statusCode,
            span.statusMessage,
            span.threadName,
            traceId,
            JSON.stringify(span.attributes)
        ];
        return haystacks.join(" ").toLowerCase().indexOf(search) >= 0;
    }

    function renderSummary(traces) {
        if (!traces.length) {
            elements.summaryGrid.innerHTML = "";
            return;
        }

        var traceCount = traces.length;
        var spans = traces.reduce(function (acc, trace) { return acc.concat(trace.spans); }, []);
        var errorSpans = spans.filter(function (span) { return span.statusCode === "ERROR"; }).length;
        var avgDuration = Math.round(traces.reduce(function (sum, trace) { return sum + trace.durationMs; }, 0) / traceCount);
        var maxDuration = Math.max.apply(null, traces.map(function (trace) { return trace.durationMs; }));
        var uniqueThreads = uniq(spans.map(function (span) { return span.threadName; })).length;

        elements.summaryGrid.innerHTML = [
            summaryCard("Trace 数", traceCount),
            summaryCard("Span 数", spans.length),
            summaryCard("错误 Span", errorSpans),
            summaryCard("平均耗时", avgDuration + "ms"),
            summaryCard("最长 Trace", maxDuration + "ms / " + uniqueThreads + " 线程")
        ].join("");
    }

    function summaryCard(label, value) {
        return [
            '<article class="summary-card">',
            '<span class="summary-label">', escapeHtml(label), '</span>',
            '<span class="summary-value">', escapeHtml(String(value)), '</span>',
            '</article>'
        ].join("");
    }

    function renderTraceList(traces) {
        elements.traceCount.textContent = traces.length + " traces";
        if (!traces.length) {
            elements.traceList.innerHTML = '<div class="empty-state">没有符合筛选条件的 trace</div>';
            return;
        }

        elements.traceList.innerHTML = traces.map(function (trace) {
            var active = trace.traceId === state.selectedTraceId ? " active" : "";
            return [
                '<article class="trace-card', active, '" data-trace-id="', trace.traceId, '">',
                '<div class="trace-card-top">',
                '<div class="trace-name">', escapeHtml(trace.rootName), '</div>',
                statusChip(trace.rootStatus),
                '</div>',
                '<div class="trace-meta">',
                '<span class="chip mono">', escapeHtml(shortId(trace.traceId, 16)), '</span>',
                '<span class="chip">', escapeHtml(trace.spans.length + " spans"), '</span>',
                '<span class="chip">', escapeHtml(trace.durationMs + "ms"), '</span>',
                '<span class="chip error">', escapeHtml("ERR " + trace.errorCount), '</span>',
                '</div>',
                '</article>'
            ].join("");
        }).join("");

        Array.prototype.forEach.call(elements.traceList.querySelectorAll(".trace-card"), function (node) {
            node.addEventListener("click", function () {
                state.selectedTraceId = node.getAttribute("data-trace-id");
                state.selectedSpanId = null;
                refresh();
            });
        });
    }

    function renderActiveTrace(trace) {
        if (!trace) {
            elements.activeTraceTitle.textContent = "未找到 Trace";
            elements.activeTraceMeta.textContent = "调整筛选条件或重新导入数据";
            elements.spanTree.innerHTML = '<div class="empty-state">没有可展示的 span</div>';
            elements.timeline.innerHTML = '<div class="empty-state">没有可展示的 timeline</div>';
            elements.inspector.className = "inspector empty-state";
            elements.inspector.textContent = "选择一个 span 查看详情";
            return;
        }

        elements.activeTraceTitle.textContent = trace.rootName;
        elements.activeTraceMeta.textContent = [
            shortId(trace.traceId, 18),
            trace.spans.length + " spans",
            trace.durationMs + "ms",
            "状态 " + trace.rootStatus
        ].join("  |  ");

        var selectedSpan = pickSelectedSpan(trace);
        renderSpanTree(trace, selectedSpan);
        renderTimeline(trace, selectedSpan);
        renderInspector(selectedSpan || (trace.orderedSpans[0] && trace.orderedSpans[0].span));
    }

    function pickSelectedSpan(trace) {
        if (state.selectedSpanId && trace.spanMap[state.selectedSpanId]) {
            return trace.spanMap[state.selectedSpanId];
        }
        return null;
    }

    function renderSpanTree(trace, selectedSpan) {
        elements.spanTree.innerHTML = trace.orderedSpans.map(function (item) {
            var span = item.span;
            var active = selectedSpan && selectedSpan.spanId === span.spanId ? " active" : "";
            var prefix = new Array(item.depth + 1).join("│  ") + (item.depth ? "└─" : "");
            return [
                '<article class="span-row', active, '" data-span-id="', span.spanId, '">',
                '<div class="span-row-main">',
                '<div class="span-title">',
                item.depth ? '<span class="tree-branch">' + escapeHtml(prefix) + '</span>' : "",
                statusDot(span.statusCode),
                '<span class="span-name">', escapeHtml(span.name), '</span>',
                '</div>',
                '<span class="chip">', escapeHtml(durationOf(span) + "ms"), '</span>',
                '</div>',
                '<div class="span-subline">',
                '<span class="chip">', escapeHtml(span.kind), '</span>',
                '<span class="chip mono">', escapeHtml(shortId(span.spanId, 12)), '</span>',
                '<span class="chip">', escapeHtml(span.threadName), '</span>',
                statusChip(span.statusCode),
                '</div>',
                '</article>'
            ].join("");
        }).join("");

        bindSpanSelection(elements.spanTree);
    }

    function renderTimeline(trace, selectedSpan) {
        var total = Math.max(trace.durationMs, 1);
        elements.timeline.innerHTML = trace.orderedSpans.map(function (item) {
            var span = item.span;
            var active = selectedSpan && selectedSpan.spanId === span.spanId ? " active" : "";
            var offset = ((span.startEpochMillis - trace.start) / total) * 100;
            var width = Math.max((durationOf(span) / total) * 100, 2);
            return [
                '<article class="timeline-row', active, '" data-span-id="', span.spanId, '">',
                '<div class="span-row-main timeline-row-main">',
                '<div class="span-title">',
                statusDot(span.statusCode),
                '<span class="span-name">', escapeHtml(span.name), '</span>',
                '</div>',
                '<span class="chip">', escapeHtml(durationOf(span) + "ms"), '</span>',
                '</div>',
                '<div class="timeline-subline">',
                '<span class="chip mono">+', escapeHtml(String(span.startEpochMillis - trace.start)), 'ms</span>',
                '<span class="chip">', escapeHtml(span.kind), '</span>',
                '<span class="chip">', escapeHtml(span.threadName), '</span>',
                '</div>',
                '<div class="timeline-bar-wrap">',
                '<div class="timeline-bar" style="left:', offset.toFixed(2), '%;width:', width.toFixed(2), '%;"></div>',
                '</div>',
                '</article>'
            ].join("");
        }).join("");

        bindSpanSelection(elements.timeline);
    }

    function bindSpanSelection(container) {
        Array.prototype.forEach.call(container.querySelectorAll("[data-span-id]"), function (node) {
            node.addEventListener("click", function () {
                state.selectedSpanId = node.getAttribute("data-span-id");
                refresh();
            });
        });
    }

    function renderInspector(span) {
        if (!span) {
            elements.inspector.className = "inspector empty-state";
            elements.inspector.textContent = "选择一个 span 查看详情";
            return;
        }

        elements.inspector.className = "inspector";
        elements.inspector.innerHTML = [
            '<section class="inspector-card">',
            '<div class="key-value">',
            kv("名称", span.name),
            kv("状态", span.statusCode + (span.statusMessage ? " | " + span.statusMessage : "")),
            kv("Kind", span.kind),
            kv("Tracer", span.tracerName),
            kv("TraceId", span.traceId),
            kv("SpanId", span.spanId),
            kv("ParentSpanId", span.parentSpanId || "ROOT"),
            kv("线程", span.threadName),
            kv("开始时间", formatTime(span.startEpochMillis)),
            kv("结束时间", formatTime(span.endEpochMillis)),
            kv("耗时", durationOf(span) + "ms"),
            '</div>',
            '</section>',
            '<section class="inspector-card">',
            '<h3>Attributes</h3>',
            renderAttributes(span.attributes),
            '</section>'
        ].join("");
    }

    function renderAttributes(attributes) {
        var entries = Object.keys(attributes || {});
        if (!entries.length) {
            return '<div class="empty-state">这个 span 没有 attributes</div>';
        }
        return [
            '<div class="attribute-list">',
            entries.sort().map(function (key) {
                return [
                    '<div class="attribute-item">',
                    '<span class="kv-key">', escapeHtml(key), '</span>',
                    '<span class="kv-value">', escapeHtml(stringifyValue(attributes[key])), '</span>',
                    '</div>'
                ].join("");
            }).join(""),
            '</div>'
        ].join("");
    }

    function kv(key, value) {
        return [
            '<div class="kv-row">',
            '<span class="kv-key">', escapeHtml(key), '</span>',
            '<span class="kv-value">', escapeHtml(String(value)), '</span>',
            '</div>'
        ].join("");
    }

    function findSelectedTrace(traces) {
        for (var i = 0; i < traces.length; i += 1) {
            if (traces[i].traceId === state.selectedTraceId) {
                return traces[i];
            }
        }
        return null;
    }

    function shortId(id, max) {
        if (!id) {
            return "";
        }
        return id.length <= max ? id : id.slice(0, max) + "…";
    }

    function durationOf(span) {
        return Math.max(0, Number(span.endEpochMillis) - Number(span.startEpochMillis));
    }

    function formatTime(epochMillis) {
        var date = new Date(epochMillis);
        return date.toLocaleString("zh-CN", {
            hour12: false
        }) + "." + pad(date.getMilliseconds(), 3);
    }

    function pad(value, width) {
        var output = String(value);
        while (output.length < width) {
            output = "0" + output;
        }
        return output;
    }

    function byStartThenDuration(a, b) {
        if (a.startEpochMillis !== b.startEpochMillis) {
            return a.startEpochMillis - b.startEpochMillis;
        }
        return durationOf(b) - durationOf(a);
    }

    function statusChip(status) {
        var cls = status.toLowerCase();
        return '<span class="chip ' + cls + '">' + escapeHtml(status) + '</span>';
    }

    function statusDot(status) {
        return '<span class="status-dot ' + escapeHtml(status.toLowerCase()) + '"></span>';
    }

    function uniq(values) {
        var seen = {};
        return values.filter(function (value) {
            if (seen[value]) {
                return false;
            }
            seen[value] = true;
            return true;
        });
    }

    function stringifyValue(value) {
        if (typeof value === "string") {
            return value;
        }
        return JSON.stringify(value);
    }

    function escapeHtml(value) {
        return String(value)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }
}());
