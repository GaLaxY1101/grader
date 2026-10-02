package ua.kpi.grader.submission.feedback;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts structured per-test results from a raw CI job log (or sandbox output).
 *
 * <p>The grader harness ({@code grader_test.h}, {@code conftest.py}) prints one JSON event per line,
 * prefixed with {@value #PREFIX}: {@code plan}, {@code start}, {@code result}, {@code end}, plus
 * {@code compile_failed} and {@code timeout} printed by the CI script. Tests that started without
 * a result are reported as CRASHED (or TIMEOUT), planned tests that never started as NOT_RUN.
 *
 * <p>Logs without events (custom CI templates, legacy test files) fall back to
 * {@code PASS name} / {@code FAIL name: expected X got Y} lines and pytest summary lines.
 *
 * <p>Student code runs in the same process as the harness and could print fake events. To limit
 * that, a result is only accepted for the test that is currently running (the harness's own result,
 * printed last, wins), events after the harness's {@code end} event are ignored, unknown test names
 * are rejected once a plan exists, and a run without an {@code end} event is reported as CRASHED.
 */
public final class TestReportParser {

    public static final String PREFIX = "##GRADER## ";

    static final int MAX_VALUE_CHARS = 1000;
    static final int MAX_NAME_CHARS = 255;
    static final int MAX_COMPILE_OUTPUT_CHARS = 20_000;
    static final int MAX_TESTS = 500;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;?]*[A-Za-z]");
    private static final Pattern GITLAB_SECTION =
            Pattern.compile("section_(?:start|end):\\d+:[A-Za-z0-9_.\\-]+(?:\\[[^\\]]*])?");

    private static final Pattern LEGACY_PASS = Pattern.compile("^PASS\\s+([^:\\s]+)\\s*$");
    private static final Pattern LEGACY_FAIL = Pattern.compile("^FAIL\\s+([^:\\s]+):?\\s*(.*)$");
    private static final Pattern EXPECTED_GOT = Pattern.compile("^expected\\s+(.*?)\\s+got\\s+(.*)$");
    private static final Pattern PYTEST_SUMMARY =
            Pattern.compile("^(PASSED|FAILED|ERROR)\\s+(\\S+::\\S+?)(?:\\s+-\\s+(.*))?$");

    /** Job-trailer lines that end a compile-output block. */
    private static final List<String> TRAILER_PREFIXES = List.of(
            "Cleaning up project directory", "Cleaning up file based variables", "ERROR: Job failed",
            "Job succeeded", "Uploading artifacts", "=== Job: ");
    private static final String GITLAB_TIMEOUT = "execution took longer than";

    private TestReportParser() {
    }

    /**
     * Parses a raw job log into a {@link TestReport}.
     *
     * @param rawLog job log, possibly truncated or containing ANSI codes and GitLab section markers
     * @return the parsed report; {@link TestReport#unavailable()} if nothing could be recognized
     */
    public static TestReport parse(String rawLog) {
        if (rawLog == null || rawLog.isBlank()) {
            return TestReport.unavailable();
        }
        List<String> lines = normalize(rawLog);
        EventState state = new EventState();
        boolean anyEvent = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith("$ ")) {
                // GitLab echoes script commands; an echoed `echo '##GRADER## ...'` is not an event.
                continue;
            }
            int idx = line.indexOf(PREFIX);
            if (idx < 0) {
                continue;
            }
            JsonNode event = readEvent(line.substring(idx + PREFIX.length()));
            if (event == null) {
                continue;
            }
            anyEvent = true;
            if ("compile_failed".equals(event.path("event").asText())) {
                state.compileFailed = true;
                state.compileOutput = event.hasNonNull("output")
                        ? event.get("output").asText()
                        : collectCompileOutput(lines, i + 1);
            } else {
                state.apply(event);
            }
        }
        if (!anyEvent) {
            return parseLegacy(lines);
        }
        if (!state.timedOut && rawLog.contains(GITLAB_TIMEOUT)) {
            state.timedOut = true;
        }
        return state.toReport();
    }

    /**
     * True if the log contains at least one harness event line.
     *
     * @param rawLog job log
     * @return whether {@link #parse} will use events rather than the legacy fallback
     */
    public static boolean hasEvents(String rawLog) {
        return rawLog != null && rawLog.contains(PREFIX);
    }

    // ── Normalization ─────────────────────────────────────────

    static List<String> normalize(String rawLog) {
        String text = ANSI.matcher(rawLog).replaceAll("");
        text = GITLAB_SECTION.matcher(text).replaceAll("");
        text = text.replace("\r\n", "\n").replace('\r', '\n');
        return List.of(text.split("\n", -1));
    }

    private static JsonNode readEvent(String json) {
        try {
            // Trailing text after the JSON object (e.g. pytest progress characters) is ignored.
            JsonNode node = MAPPER.readTree(json);
            return node != null && node.isObject() && node.path("event").isTextual() ? node : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String collectCompileOutput(List<String> lines, int from) {
        StringBuilder out = new StringBuilder();
        for (int i = from; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains(PREFIX) || TRAILER_PREFIXES.stream().anyMatch(line::startsWith)) {
                break;
            }
            out.append(line).append('\n');
            if (out.length() > MAX_COMPILE_OUTPUT_CHARS) {
                break;
            }
        }
        return out.toString();
    }

    // ── Event state machine ───────────────────────────────────

    private static final class EventState {
        private List<String> plan;
        private final Map<String, TestCaseResult> results = new LinkedHashMap<>();
        private final List<String> started = new ArrayList<>();
        private String current;
        private boolean compileFailed;
        private String compileOutput;
        private boolean timedOut;
        private boolean ended;

        void apply(JsonNode event) {
            String type = event.path("event").asText();
            if (ended && !"timeout".equals(type)) {
                // The harness has finished; anything later (atexit handlers, destructors) is not trusted.
                return;
            }
            switch (type) {
                case "plan" -> {
                    if (plan == null && started.isEmpty()) {
                        plan = new ArrayList<>();
                        for (JsonNode name : event.path("tests")) {
                            if (plan.size() >= MAX_TESTS) {
                                break;
                            }
                            String n = truncate(name.asText(), MAX_NAME_CHARS);
                            if (!plan.contains(n)) {
                                plan.add(n);
                            }
                        }
                    }
                }
                case "start" -> {
                    String name = truncate(event.path("name").asText(""), MAX_NAME_CHARS);
                    if (planComplete() || name.isEmpty() || started.contains(name) || !allowed(name)) {
                        return;
                    }
                    started.add(name);
                    current = name;
                }
                case "result" -> {
                    String name = truncate(event.path("name").asText(""), MAX_NAME_CHARS);
                    // Only the running test may report; a later genuine result overrides an earlier fake one.
                    if (!name.equals(current)) {
                        return;
                    }
                    results.put(name, toResult(name, event));
                }
                case "end" -> ended = true;
                case "timeout" -> timedOut = true;
                default -> {
                    // Unknown event types are ignored for forward compatibility.
                }
            }
        }

        private boolean allowed(String name) {
            return plan != null ? plan.contains(name) : started.size() < MAX_TESTS;
        }

        private boolean planComplete() {
            return plan != null && !plan.isEmpty() && results.keySet().containsAll(plan);
        }

        private static TestCaseResult toResult(String name, JsonNode event) {
            TestCaseStatus status = parseStatus(event.path("status").asText());
            Long duration = event.path("durationMs").canConvertToLong() && event.hasNonNull("durationMs")
                    ? event.get("durationMs").asLong()
                    : null;
            return new TestCaseResult(name, status,
                    text(event, "expected"), text(event, "actual"), text(event, "message"), duration);
        }

        private static TestCaseStatus parseStatus(String status) {
            return switch (status) {
                case "PASSED" -> TestCaseStatus.PASSED;
                case "FAILED" -> TestCaseStatus.FAILED;
                case "SKIPPED" -> TestCaseStatus.SKIPPED;
                default -> TestCaseStatus.ERROR;
            };
        }

        private static String text(JsonNode event, String field) {
            JsonNode node = event.get(field);
            if (node == null || node.isNull()) {
                return null;
            }
            String value = node.asText();
            return value.isEmpty() && !"expected".equals(field) && !"actual".equals(field)
                    ? null
                    : truncate(value, MAX_VALUE_CHARS);
        }

        TestReport toReport() {
            if (compileFailed) {
                return new TestReport(TestRunStatus.COMPILE_ERROR,
                        truncate(compileOutput == null ? "" : compileOutput.strip(), MAX_COMPILE_OUTPUT_CHARS),
                        0, plan == null ? 0 : plan.size(), notRun(plan), true, true);
            }
            List<String> order = new ArrayList<>(plan != null ? plan : started);
            String lastStarted = started.isEmpty() ? null : started.getLast();
            List<TestCaseResult> tests = new ArrayList<>();
            boolean crashed = false;
            boolean timeoutHit = false;
            for (String name : order) {
                TestCaseResult result = results.get(name);
                if (result != null) {
                    tests.add(result);
                } else if (started.contains(name)) {
                    if (timedOut && name.equals(lastStarted)) {
                        timeoutHit = true;
                        tests.add(simple(name, TestCaseStatus.TIMEOUT, null));
                    } else {
                        crashed = true;
                        tests.add(simple(name, TestCaseStatus.CRASHED, null));
                    }
                } else {
                    tests.add(simple(name, TestCaseStatus.NOT_RUN, null));
                }
            }
            int passed = (int) tests.stream().filter(t -> t.status() == TestCaseStatus.PASSED).count();
            boolean notRun = tests.stream().anyMatch(t -> t.status() == TestCaseStatus.NOT_RUN);
            TestRunStatus status;
            if (timeoutHit || timedOut && (notRun || !ended)) {
                status = TestRunStatus.TIMEOUT;
            } else if (crashed || notRun || !ended) {
                // A missing end event means the program exited before the harness finished.
                status = TestRunStatus.CRASHED;
            } else {
                status = TestRunStatus.COMPLETED;
            }
            return new TestReport(status, null, passed, tests.size(), List.copyOf(tests), true, true);
        }

        private static List<TestCaseResult> notRun(List<String> names) {
            if (names == null) {
                return List.of();
            }
            return names.stream().map(n -> simple(n, TestCaseStatus.NOT_RUN, null)).toList();
        }

        private static TestCaseResult simple(String name, TestCaseStatus status, String message) {
            return new TestCaseResult(name, status, null, null, message, null);
        }
    }

    // ── Legacy fallback ───────────────────────────────────────

    private static TestReport parseLegacy(List<String> lines) {
        Map<String, TestCaseResult> tests = new LinkedHashMap<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (tests.size() >= MAX_TESTS) {
                break;
            }
            Matcher pass = LEGACY_PASS.matcher(line);
            if (pass.matches()) {
                String name = truncate(pass.group(1), MAX_NAME_CHARS);
                tests.putIfAbsent(name, new TestCaseResult(name, TestCaseStatus.PASSED, null, null, null, null));
                continue;
            }
            Matcher fail = LEGACY_FAIL.matcher(line);
            if (fail.matches()) {
                String name = truncate(fail.group(1), MAX_NAME_CHARS);
                String detail = fail.group(2).strip();
                Matcher eg = EXPECTED_GOT.matcher(detail);
                TestCaseResult result = eg.matches()
                        ? new TestCaseResult(name, TestCaseStatus.FAILED,
                                truncate(eg.group(1), MAX_VALUE_CHARS), truncate(eg.group(2), MAX_VALUE_CHARS),
                                null, null)
                        : new TestCaseResult(name, TestCaseStatus.FAILED, null, null,
                                detail.isEmpty() ? null : truncate(detail, MAX_VALUE_CHARS), null);
                tests.putIfAbsent(name, result);
                continue;
            }
            Matcher py = PYTEST_SUMMARY.matcher(line);
            if (py.matches()) {
                String nodeId = py.group(2);
                String name = truncate(nodeId.substring(nodeId.lastIndexOf("::") + 2), MAX_NAME_CHARS);
                TestCaseStatus status = switch (py.group(1)) {
                    case "PASSED" -> TestCaseStatus.PASSED;
                    case "FAILED" -> TestCaseStatus.FAILED;
                    default -> TestCaseStatus.ERROR;
                };
                String message = py.group(3) == null ? null : truncate(py.group(3).strip(), MAX_VALUE_CHARS);
                tests.putIfAbsent(name, new TestCaseResult(name, status, null, null, message, null));
            }
        }
        if (tests.isEmpty()) {
            return TestReport.unavailable();
        }
        List<TestCaseResult> list = List.copyOf(tests.values());
        int passed = (int) list.stream().filter(t -> t.status() == TestCaseStatus.PASSED).count();
        return new TestReport(TestRunStatus.COMPLETED, null, passed, list.size(), list, true, false);
    }

    static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
