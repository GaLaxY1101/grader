// Grader test harness for C and C++ assignments (C++17, standard library only).
//
// Provided by the grading system and committed next to the test file on every attempt;
// changes made by students are overwritten.
//
// Usage in test.cpp:
//
//     #include "grader_test.h"
//     #include "solution.cpp"
//
//     TEST_CASE(test_add) {
//         EXPECT_EQ(5, add(2, 3));
//     }
//
// Compile with -DGRADER_MAIN so the harness provides main(). Every test emits machine-readable
// events ("##GRADER## {json}") plus human-readable PASS/FAIL lines on stdout.
#ifndef GRADER_TEST_H
#define GRADER_TEST_H

#include <charconv>
#include <chrono>
#include <cmath>
#include <cstddef>
#include <cstdio>
#include <exception>
#include <iostream>
#include <iterator>
#include <optional>
#include <sstream>
#include <string>
#include <string_view>
#include <tuple>
#include <type_traits>
#include <typeinfo>
#include <utility>
#include <vector>

namespace grader {

constexpr std::size_t kMaxValueChars = 1000;

namespace detail {

template <class T, class = void>
struct is_iterable : std::false_type {};
template <class T>
struct is_iterable<T, std::void_t<decltype(std::begin(std::declval<const T&>())),
                                  decltype(std::end(std::declval<const T&>()))>> : std::true_type {};

template <class T, class = void>
struct is_streamable : std::false_type {};
template <class T>
struct is_streamable<T, std::void_t<decltype(std::declval<std::ostream&>() << std::declval<const T&>())>>
    : std::true_type {};

template <class T>
struct is_pair : std::false_type {};
template <class A, class B>
struct is_pair<std::pair<A, B>> : std::true_type {};

template <class T>
struct is_tuple : std::false_type {};
template <class... Ts>
struct is_tuple<std::tuple<Ts...>> : std::true_type {};

template <class T>
struct is_optional : std::false_type {};
template <class T>
struct is_optional<std::optional<T>> : std::true_type {};

inline std::string escape_char(char c, char quote) {
    switch (c) {
        case '\n': return "\\n";
        case '\t': return "\\t";
        case '\r': return "\\r";
        case '\0': return "\\0";
        case '\\': return "\\\\";
        default: break;
    }
    if (c == quote) {
        return std::string("\\") + c;
    }
    unsigned char u = static_cast<unsigned char>(c);
    if (u < 0x20 || u == 0x7f) {
        char buf[8];
        std::snprintf(buf, sizeof(buf), "\\x%02x", u);
        return buf;
    }
    return std::string(1, c);
}

inline std::string quote(std::string_view s) {
    std::string out = "\"";
    for (char c : s) {
        out += escape_char(c, '"');
        if (out.size() > kMaxValueChars) {
            break;
        }
    }
    return out + "\"";
}

inline std::string format_double(double v) {
    if (std::isnan(v)) return "nan";
    if (std::isinf(v)) return v > 0 ? "inf" : "-inf";
    char buf[64];
    auto res = std::to_chars(buf, buf + sizeof(buf), v);
    std::string s(buf, res.ptr);
    if (s.find_first_of(".eE") == std::string::npos) {
        s += ".0";
    }
    return s;
}

inline std::string truncate(std::string s) {
    if (s.size() > kMaxValueChars) {
        s.resize(kMaxValueChars);
        s += "...";
    }
    return s;
}

}  // namespace detail

/**
 * Renders a value as a source-like literal: strings and chars quoted, bools as true/false,
 * containers as [a, b], pairs/tuples as {a, b}, anything streamable via operator<<,
 * otherwise "<value>". The result is truncated to kMaxValueChars characters.
 */
template <class T>
std::string to_string(const T& v) {
    using D = std::decay_t<T>;
    std::string out;
    if constexpr (std::is_array_v<T> && std::is_same_v<std::remove_cv_t<std::remove_extent_t<T>>, char>) {
        out = detail::quote(std::string_view(v));
    } else if constexpr (std::is_array_v<T>) {
        out = "[";
        bool first = true;
        for (const auto& item : v) {
            if (!first) out += ", ";
            first = false;
            out += grader::to_string(item);
            if (out.size() > kMaxValueChars) break;
        }
        out += "]";
    } else if constexpr (std::is_same_v<D, bool>) {
        out = v ? "true" : "false";
    } else if constexpr (std::is_same_v<D, char>) {
        out = "'" + detail::escape_char(v, '\'') + "'";
    } else if constexpr (std::is_same_v<D, std::string> || std::is_same_v<D, std::string_view>) {
        out = detail::quote(v);
    } else if constexpr (std::is_same_v<D, const char*> || std::is_same_v<D, char*>) {
        out = v == nullptr ? "nullptr" : detail::quote(v);
    } else if constexpr (std::is_same_v<D, std::nullptr_t>) {
        out = "nullptr";
    } else if constexpr (std::is_floating_point_v<D>) {
        out = detail::format_double(static_cast<double>(v));
    } else if constexpr (std::is_integral_v<D>) {
        out = std::to_string(v);
    } else if constexpr (std::is_enum_v<D>) {
        out = std::to_string(static_cast<std::underlying_type_t<D>>(v));
    } else if constexpr (detail::is_pair<D>::value) {
        out = "{" + grader::to_string(v.first) + ", " + grader::to_string(v.second) + "}";
    } else if constexpr (detail::is_tuple<D>::value) {
        out = "{";
        std::apply([&out](const auto&... items) {
            bool first = true;
            ((out += (first ? "" : ", ") + grader::to_string(items), first = false), ...);
        }, v);
        out += "}";
    } else if constexpr (detail::is_optional<D>::value) {
        out = v.has_value() ? grader::to_string(*v) : "nullopt";
    } else if constexpr (detail::is_iterable<D>::value) {
        out = "[";
        bool first = true;
        for (const auto& item : v) {
            if (!first) out += ", ";
            first = false;
            out += grader::to_string(item);
            if (out.size() > kMaxValueChars) break;
        }
        out += "]";
    } else if constexpr (detail::is_streamable<D>::value) {
        std::ostringstream ss;
        ss << v;
        out = ss.str();
    } else {
        out = "<value>";
    }
    return detail::truncate(std::move(out));
}

/** Thrown by a failed expectation; ends the current test with status FAILED. */
struct Failure {
    std::string expected;
    std::string actual;
    std::string message;
};

namespace detail {

struct TestCase {
    const char* name;
    void (*fn)();
};

inline std::vector<TestCase>& registry() {
    static std::vector<TestCase> tests;
    return tests;
}

struct Registrar {
    Registrar(const char* name, void (*fn)()) { registry().push_back({name, fn}); }
};

inline std::string json_string(std::string_view s) {
    std::string out = "\"";
    for (char c : s) {
        switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default:
                if (static_cast<unsigned char>(c) < 0x20) {
                    char buf[8];
                    std::snprintf(buf, sizeof(buf), "\\u%04x", static_cast<unsigned char>(c));
                    out += buf;
                } else {
                    out += c;
                }
        }
    }
    return out + "\"";
}

inline void emit(const std::string& json) {
    // Leading newline: student output without a trailing newline must not swallow the marker.
    std::cout << std::flush;
    std::cerr << std::flush;
    std::cout << "\n##GRADER## " << json << std::endl;
}

inline std::string error_message(const std::exception& e) {
    return std::string("exception: ") + e.what();
}

}  // namespace detail

/** Runs all registered tests, emitting plan/start/result/end events. Returns the number of non-passing tests. */
inline int run_all() {
    const auto& tests = detail::registry();
    std::string plan = "{\"event\":\"plan\",\"tests\":[";
    for (std::size_t i = 0; i < tests.size(); ++i) {
        if (i > 0) plan += ",";
        plan += detail::json_string(tests[i].name);
    }
    detail::emit(plan + "]}");

    int notPassed = 0;
    for (const auto& test : tests) {
        detail::emit("{\"event\":\"start\",\"name\":" + detail::json_string(test.name) + "}");
        auto started = std::chrono::steady_clock::now();
        std::string status = "PASSED";
        std::optional<Failure> failure;
        std::string message;
        try {
            test.fn();
        } catch (const Failure& f) {
            status = "FAILED";
            failure = f;
        } catch (const std::exception& e) {
            status = "ERROR";
            message = detail::error_message(e);
        } catch (...) {
            status = "ERROR";
            message = "unknown exception";
        }
        long long ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now() - started).count();

        std::string json = "{\"event\":\"result\",\"name\":" + detail::json_string(test.name)
                + ",\"status\":\"" + status + "\"";
        if (failure) {
            json += ",\"expected\":" + detail::json_string(failure->expected)
                    + ",\"actual\":" + detail::json_string(failure->actual)
                    + ",\"message\":" + detail::json_string(failure->message);
        } else if (!message.empty()) {
            json += ",\"message\":" + detail::json_string(message);
        }
        json += ",\"durationMs\":" + std::to_string(ms) + "}";
        detail::emit(json);

        if (status == "PASSED") {
            std::cout << "PASS " << test.name << std::endl;
        } else {
            ++notPassed;
            if (failure) {
                std::cout << "FAIL " << test.name << ": expected " << failure->expected
                          << " got " << failure->actual << std::endl;
            } else {
                std::cout << "FAIL " << test.name << ": " << message << std::endl;
            }
        }
    }
    detail::emit("{\"event\":\"end\"}");
    return notPassed > 100 ? 100 : notPassed;
}

}  // namespace grader

#define GRADER_CONCAT_INNER(a, b) a##b
#define GRADER_CONCAT(a, b) GRADER_CONCAT_INNER(a, b)

/** Declares a test; the name becomes the reported test name. */
#define TEST_CASE(name)                                                                     \
    static void grader_test_##name();                                                        \
    static ::grader::detail::Registrar grader_registrar_##name(#name, &grader_test_##name); \
    static void grader_test_##name()

/** Fails the test unless expected == actual. Both values are reported. */
#define EXPECT_EQ(expected, actual)                                                          \
    do {                                                                                     \
        const auto& grader_e_ = (expected);                                                  \
        const auto& grader_a_ = (actual);                                                    \
        if (!(grader_e_ == grader_a_)) {                                                     \
            throw ::grader::Failure{::grader::to_string(grader_e_), ::grader::to_string(grader_a_), \
                                    "EXPECT_EQ(" #expected ", " #actual ")"};                \
        }                                                                                    \
    } while (0)

/** Fails the test unless cond is true. */
#define EXPECT_TRUE(cond)                                                                    \
    do {                                                                                     \
        if (!(cond)) {                                                                       \
            throw ::grader::Failure{"true", "false", "EXPECT_TRUE(" #cond ")"};              \
        }                                                                                    \
    } while (0)

/** Fails the test unless cond is false. */
#define EXPECT_FALSE(cond)                                                                   \
    do {                                                                                     \
        if (cond) {                                                                          \
            throw ::grader::Failure{"false", "true", "EXPECT_FALSE(" #cond ")"};             \
        }                                                                                    \
    } while (0)

/** Fails the test unless |expected - actual| <= eps. */
#define EXPECT_NEAR(expected, actual, eps)                                                   \
    do {                                                                                     \
        const double grader_e_ = static_cast<double>(expected);                              \
        const double grader_a_ = static_cast<double>(actual);                                \
        const double grader_eps_ = static_cast<double>(eps);                                 \
        if (!(std::fabs(grader_e_ - grader_a_) <= grader_eps_)) {                            \
            throw ::grader::Failure{::grader::to_string(grader_e_), ::grader::to_string(grader_a_), \
                                    "EXPECT_NEAR(" #expected ", " #actual ", " #eps ")"};    \
        }                                                                                    \
    } while (0)

#ifdef GRADER_MAIN
int main() {
    return ::grader::run_all();
}
#endif

#endif  // GRADER_TEST_H
