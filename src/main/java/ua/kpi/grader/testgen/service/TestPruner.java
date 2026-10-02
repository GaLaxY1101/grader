package ua.kpi.grader.testgen.service;

import ua.kpi.grader.course.entity.Language;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes named test cases from a test file. Used after the repair loop to drop tests that
 * still fail on the reference solution: since the reference is correct, such tests are wrong.
 */
final class TestPruner {

    private TestPruner() {
    }

    /**
     * @param names test names as reported by the sandbox (pytest parameter ids are ignored)
     * @return the file without those tests, or null if a test could not be located
     */
    static String remove(Language language, String tests, Set<String> names) {
        String result = tests.replace("\r\n", "\n");
        for (String name : names) {
            String function = name.replaceAll("\\[.*]$", "");
            if (function.equals("main")) {
                // Synthetic failure for a crashing/silent test program; there is no test to remove.
                return null;
            }
            result = language == Language.PYTHON ? removePython(result, function) : removeCpp(result, function);
            if (result == null) {
                return null;
            }
        }
        return result;
    }

    /** Removes a top-level {@code def name(...)} block including its decorators. */
    private static String removePython(String src, String name) {
        List<String> lines = new ArrayList<>(Arrays.asList(src.split("\n", -1)));
        Pattern def = Pattern.compile("^(async\\s+)?def\\s+" + Pattern.quote(name) + "\\s*\\(");
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (def.matcher(lines.get(i)).find()) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return null;
        }
        int end = start + 1;
        while (end < lines.size() && (lines.get(end).isBlank() || Character.isWhitespace(lines.get(end).charAt(0)))) {
            end++;
        }
        while (start > 0 && lines.get(start - 1).startsWith("@")) {
            start--;
        }
        lines.subList(start, end).clear();
        return String.join("\n", lines);
    }

    /** Removes {@code TEST_CASE(name) { ... }}, or {@code void name() { ... }} and every {@code name();} call. */
    private static String removeCpp(String src, String name) {
        Matcher m = Pattern.compile("(?m)^[ \\t]*TEST_CASE\\s*\\(\\s*" + Pattern.quote(name)
                + "\\s*\\)\\s*\\{").matcher(src);
        if (!m.find()) {
            m = Pattern.compile("(?m)^[ \\t]*(?:static\\s+)?(?:inline\\s+)?(?:void|bool|int)\\s+"
                    + Pattern.quote(name) + "\\s*\\(\\s*\\)\\s*\\{").matcher(src);
            if (!m.find()) {
                return null;
            }
        }
        int close = matchingBrace(src, m.end() - 1);
        if (close < 0) {
            return null;
        }
        int end = close + 1;
        if (end < src.length() && src.charAt(end) == '\n') {
            end++;
        }
        String withoutFunction = src.substring(0, m.start()) + src.substring(end);
        return withoutFunction.replaceAll("(?m)^[ \\t]*" + Pattern.quote(name) + "\\s*\\(\\s*\\)\\s*;[ \\t]*\\n?", "");
    }

    /** Index of the brace closing the one at {@code open}, skipping string/char literals and comments. */
    private static int matchingBrace(String src, int open) {
        int depth = 0;
        for (int i = open; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipLiteral(src, i, c);
            } else if (src.startsWith("//", i)) {
                int nl = src.indexOf('\n', i);
                i = nl < 0 ? src.length() : nl;
            } else if (src.startsWith("/*", i)) {
                int endComment = src.indexOf("*/", i + 2);
                i = endComment < 0 ? src.length() : endComment + 1;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int skipLiteral(String src, int from, char quote) {
        for (int i = from + 1; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == quote) {
                return i;
            }
        }
        return src.length();
    }
}
