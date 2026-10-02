"""Grader test harness for Python assignments (pytest plugin).

Provided by the grading system and committed next to the test file on every attempt;
changes made by students are overwritten.

Tests stay plain pytest functions. Write comparisons as ``assert actual == expected``
so the report shows the expected and the actual value.

Every test emits machine-readable events (``##GRADER## {json}``) on stdout; pytest's
normal output is kept unchanged.
"""
import json

import pytest

_PREFIX = "##GRADER## "
_MAX_CHARS = 1000

_config = None
_names = {}
_comparisons = {}
_results = {}
_current = None


def _truncate(text):
    text = str(text)
    return text if len(text) <= _MAX_CHARS else text[:_MAX_CHARS] + "..."


def _repr(value):
    try:
        return _truncate(repr(value))
    except Exception as e:  # noqa: BLE001 - a broken __repr__ in student code must not break reporting
        return "<unrepresentable %s: %s>" % (type(value).__name__, e)


def _emit(payload):
    line = _PREFIX + json.dumps(payload, ensure_ascii=False)
    reporter = _config.pluginmanager.get_plugin("terminalreporter") if _config is not None else None
    if reporter is None:
        print(line, flush=True)
        return
    reporter.write_line(line)
    flush = getattr(reporter, "flush", None)
    if flush is not None:
        flush()


def _name(nodeid):
    return _names.get(nodeid, nodeid.rsplit("::", 1)[-1])


def pytest_configure(config):
    global _config
    _config = config


def _clean_traceback(text):
    """Drops pytest/importlib frames from a collection error, keeping the student-relevant lines."""
    kept = []
    skipping = False
    for line in text.splitlines():
        if line and not line[0].isspace() and not line.startswith("E "):
            skipping = "site-packages" in line or "<frozen" in line or "/importlib/" in line
        if skipping:
            continue
        kept.append(line[1:].removeprefix("   ") if line.startswith("E ") else line)
    return "\n".join(kept).strip() or text


def pytest_collectreport(report):
    if report.failed:
        text = report.longreprtext or ""
        _emit({"event": "compile_failed", "output": _truncate(_clean_traceback(text))})


def pytest_collection_finish(session):
    for item in session.items:
        _names[item.nodeid] = item.name
    _emit({"event": "plan", "tests": [item.name for item in session.items]})


def pytest_runtest_logstart(nodeid, location):
    global _current
    _current = nodeid
    _comparisons.pop(nodeid, None)
    _emit({"event": "start", "name": _name(nodeid)})


def pytest_assertrepr_compare(config, op, left, right):
    # Convention: ``assert actual == expected`` -> left is the actual value.
    if op == "==" and _current is not None and _current not in _comparisons:
        _comparisons[_current] = (_repr(left), _repr(right))
    return None


@pytest.hookimpl(hookwrapper=True)
def pytest_runtest_makereport(item, call):
    outcome = yield
    report = outcome.get_result()
    if report.when == "call" or (report.when == "setup" and not report.passed):
        _results[item.nodeid] = _describe(item.nodeid, report, call)


def _describe(nodeid, report, call):
    result = {"event": "result", "name": _name(nodeid), "durationMs": int(round(report.duration * 1000))}
    if report.passed:
        result["status"] = "PASSED"
    elif report.skipped:
        result["status"] = "SKIPPED"
    elif call.excinfo is not None and call.excinfo.errisinstance(AssertionError) and report.when == "call":
        result["status"] = "FAILED"
        comparison = _comparisons.get(nodeid)
        if comparison is not None:
            result["actual"], result["expected"] = comparison
        message = str(call.excinfo.value).strip()
        result["message"] = _truncate(message.splitlines()[0] if message else "assertion failed")
    else:
        result["status"] = "ERROR"
        if call.excinfo is not None:
            exc = call.excinfo.value
            text = str(exc).strip()
            result["message"] = _truncate(type(exc).__name__ + (": " + text if text else ""))
        else:
            result["message"] = "error"
    return result


def pytest_runtest_logreport(report):
    result = _results.pop(report.nodeid, None)
    if result is not None:
        _emit(result)


def pytest_sessionfinish(session, exitstatus):
    _emit({"event": "end"})
