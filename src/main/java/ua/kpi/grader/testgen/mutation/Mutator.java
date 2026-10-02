package ua.kpi.grader.testgen.mutation;

import org.springframework.stereotype.Component;
import ua.kpi.grader.course.entity.Language;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates first-order mutants of a solution with simple token-level operators.
 *
 * <p>Comments, string/char literals and {@code #include} lines are skipped (best effort).
 * Mutants are not guaranteed to compile; callers filter them with a syntax check.
 */
@Component
public class Mutator {

    private static final Pattern RELATIONAL = Pattern.compile("(?<![<>=!+\\-*/%&|^])(<=|>=|==|!=|<|>)(?![<>=])");
    private static final Pattern ARITHMETIC = Pattern.compile("(?<![+\\-*/%=<>!&|^])([+\\-*/])(?![+\\-*/>])");
    private static final Pattern INTEGER = Pattern.compile("(?<![\\w.])\\d+(?![\\w.])");
    private static final Pattern C_LOGICAL = Pattern.compile("&&|\\|\\|");
    private static final Pattern PY_LOGICAL = Pattern.compile("\\b(and|or)\\b");
    private static final Pattern PY_BOOL = Pattern.compile("\\b(True|False)\\b");
    private static final Pattern C_BOOL = Pattern.compile("\\b(true|false)\\b");
    private static final Pattern C_RETURN = Pattern.compile("\\breturn\\s+([^;\\n]+?)\\s*;");
    private static final Pattern PY_RETURN = Pattern.compile("\\breturn\\s+([^\\n#]+?)\\s*(?=#|\\n|$)");

    /**
     * Generates up to {@code maxCount} distinct mutants in a seeded random order.
     *
     * @param source   reference solution
     * @param language language of the solution
     * @param seed     seed for the order of mutation sites
     * @param maxCount maximum number of mutants to return
     * @return mutants with ids {@code M1..Mn}; each differs from {@code source} in exactly one place
     */
    public List<Mutant> generate(String source, Language language, long seed, int maxCount) {
        String normalized = source.replace("\r\n", "\n");
        boolean[] code = codeMask(normalized, language);
        List<Edit> edits = new ArrayList<>();
        collectEdits(normalized, language, code, edits);
        edits.sort(Comparator.comparingInt(Edit::start).thenComparing(Edit::replacement));
        Collections.shuffle(edits, new Random(seed));

        List<Mutant> mutants = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Edit edit : edits) {
            if (mutants.size() >= maxCount) {
                break;
            }
            String mutated = normalized.substring(0, edit.start()) + edit.replacement()
                    + normalized.substring(edit.end());
            if (mutated.equals(normalized) || !seen.add(mutated)) {
                continue;
            }
            int lineNo = lineOf(normalized, edit.start());
            mutants.add(new Mutant("M" + (mutants.size() + 1), edit.operator(), lineNo, mutated,
                    diff(normalized, mutated, lineNo, language.getSolutionFileName())));
        }
        return mutants;
    }

    private void collectEdits(String src, Language language, boolean[] code, List<Edit> edits) {
        boolean python = language == Language.PYTHON;

        Matcher m = RELATIONAL.matcher(src);
        while (m.find()) {
            String replacement = switch (m.group(1)) {
                case "<" -> "<=";
                case "<=" -> "<";
                case ">" -> ">=";
                case ">=" -> ">";
                case "==" -> "!=";
                default -> "==";
            };
            addIfCode(edits, code, m.start(1), m.end(1), replacement, MutationOperator.RELATIONAL);
        }

        m = ARITHMETIC.matcher(src);
        while (m.find()) {
            int start = m.start(1);
            if (isExponentSign(src, start)) {
                continue;
            }
            String replacement = switch (m.group(1)) {
                case "+" -> "-";
                case "-" -> "+";
                case "*" -> "/";
                default -> "*";
            };
            addIfCode(edits, code, start, m.end(1), replacement, MutationOperator.ARITHMETIC);
        }

        m = INTEGER.matcher(src);
        while (m.find()) {
            long value;
            try {
                value = Long.parseLong(m.group());
            } catch (NumberFormatException e) {
                continue;
            }
            addIfCode(edits, code, m.start(), m.end(), Long.toString(value + 1), MutationOperator.CONSTANT);
            addIfCode(edits, code, m.start(), m.end(), Long.toString(value - 1), MutationOperator.CONSTANT);
        }

        m = (python ? PY_LOGICAL : C_LOGICAL).matcher(src);
        while (m.find()) {
            String replacement = switch (m.group()) {
                case "&&" -> "||";
                case "||" -> "&&";
                case "and" -> "or";
                default -> "and";
            };
            addIfCode(edits, code, m.start(), m.end(), replacement, MutationOperator.BOOLEAN);
        }

        m = (python ? PY_BOOL : C_BOOL).matcher(src);
        while (m.find()) {
            String replacement = switch (m.group()) {
                case "True" -> "False";
                case "False" -> "True";
                case "true" -> "false";
                default -> "true";
            };
            addIfCode(edits, code, m.start(), m.end(), replacement, MutationOperator.BOOLEAN);
        }

        m = (python ? PY_RETURN : C_RETURN).matcher(src);
        while (m.find()) {
            String expr = m.group(1).strip();
            if (expr.equals("0") || !balanced(expr) || (python && expr.endsWith("\\"))) {
                continue;
            }
            addIfCode(edits, code, m.start(1), m.start(1) + m.group(1).stripTrailing().length(), "0",
                    MutationOperator.RETURN_VALUE);
        }
    }

    private static void addIfCode(List<Edit> edits, boolean[] code, int start, int end,
                                  String replacement, MutationOperator operator) {
        for (int i = start; i < end; i++) {
            if (!code[i]) {
                return;
            }
        }
        edits.add(new Edit(start, end, replacement, operator));
    }

    /** {@code 1e-5}: a sign directly after an exponent marker that follows a digit. */
    private static boolean isExponentSign(String src, int index) {
        return index >= 2
                && (src.charAt(index - 1) == 'e' || src.charAt(index - 1) == 'E')
                && Character.isDigit(src.charAt(index - 2));
    }

    private static boolean balanced(String expr) {
        int depth = 0;
        for (char c : expr.toCharArray()) {
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
                if (depth < 0) {
                    return false;
                }
            }
        }
        return depth == 0;
    }

    /**
     * Marks which characters are code (true) as opposed to comments, string/char literals
     * or {@code #include}/{@code #pragma} lines (false).
     */
    static boolean[] codeMask(String src, Language language) {
        boolean[] code = new boolean[src.length()];
        Arrays.fill(code, true);
        boolean python = language == Language.PYTHON;
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            int end;
            if (python && c == '#') {
                end = lineEnd(src, i);
            } else if (!python && c == '#' && isLineStart(src, i)
                    && (src.startsWith("#include", i) || src.startsWith("#pragma", i))) {
                end = lineEnd(src, i);
            } else if (!python && src.startsWith("//", i)) {
                end = lineEnd(src, i);
            } else if (!python && src.startsWith("/*", i)) {
                int close = src.indexOf("*/", i + 2);
                end = close < 0 ? n : close + 2;
            } else if (python && (src.startsWith("\"\"\"", i) || src.startsWith("'''", i))) {
                String quote = src.substring(i, i + 3);
                int close = src.indexOf(quote, i + 3);
                end = close < 0 ? n : close + 3;
            } else if (c == '"' || c == '\'') {
                end = stringEnd(src, i, c);
            } else {
                i++;
                continue;
            }
            for (int k = i; k < end; k++) {
                code[k] = false;
            }
            i = end;
        }
        return code;
    }

    private static boolean isLineStart(String src, int index) {
        for (int k = index - 1; k >= 0; k--) {
            char c = src.charAt(k);
            if (c == '\n') {
                return true;
            }
            if (c != ' ' && c != '\t') {
                return false;
            }
        }
        return true;
    }

    private static int lineEnd(String src, int from) {
        int nl = src.indexOf('\n', from);
        return nl < 0 ? src.length() : nl;
    }

    private static int stringEnd(String src, int from, char quote) {
        int k = from + 1;
        while (k < src.length()) {
            char c = src.charAt(k);
            if (c == '\\') {
                k += 2;
                continue;
            }
            if (c == quote || c == '\n') {
                return k + 1;
            }
            k++;
        }
        return src.length();
    }

    private static int lineOf(String src, int index) {
        int line = 1;
        for (int k = 0; k < index; k++) {
            if (src.charAt(k) == '\n') {
                line++;
            }
        }
        return line;
    }

    /** Unified diff of a single changed line with one line of context on each side. */
    static String diff(String original, String mutated, int lineNo, String fileName) {
        String[] before = original.split("\n", -1);
        String[] after = mutated.split("\n", -1);
        int idx = lineNo - 1;
        int from = Math.max(0, idx - 1);
        int to = Math.min(before.length - 1, idx + 1);

        StringBuilder sb = new StringBuilder();
        sb.append("--- ").append(fileName).append(" (correct)\n");
        sb.append("+++ ").append(fileName).append(" (buggy)\n");
        int count = to - from + 1;
        sb.append("@@ -").append(from + 1).append(',').append(count)
                .append(" +").append(from + 1).append(',').append(count).append(" @@\n");
        for (int k = from; k <= to; k++) {
            if (k == idx) {
                sb.append('-').append(before[k]).append('\n');
                sb.append('+').append(after[k]).append('\n');
            } else {
                sb.append(' ').append(before[k]).append('\n');
            }
        }
        return sb.toString();
    }

    private record Edit(int start, int end, String replacement, MutationOperator operator) {}
}
