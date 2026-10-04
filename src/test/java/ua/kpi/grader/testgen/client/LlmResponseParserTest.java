package ua.kpi.grader.testgen.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmResponseParserTest {

    @Test
    void extractCode_returnsBodyOfFencedBlockWithLanguageTag() {
        String content = "Here are the tests:\n```python\nfrom solution import *\n\ndef test_a():\n    assert add(1, 2) == 3\n```\nDone.";

        assertThat(LlmResponseParser.extractCode(content))
                .isEqualTo("from solution import *\n\ndef test_a():\n    assert add(1, 2) == 3\n");
    }

    @Test
    void extractCode_returnsBodyOfFencedBlockWithoutLanguageTag() {
        String content = "```\nimport pytest\nfrom solution import *\n```";

        assertThat(LlmResponseParser.extractCode(content)).isEqualTo("import pytest\nfrom solution import *\n");
    }

    @Test
    void extractCode_handlesCppTagAndCrLf() {
        String content = "```c++\r\n#include \"solution.cpp\"\r\nint main() { return 0; }\r\n```";

        assertThat(LlmResponseParser.extractCode(content)).startsWith("#include \"solution.cpp\"");
    }

    @Test
    void extractCode_returnsWholeContentWhenUnfenced() {
        String content = "  from solution import *\n\ndef test_a():\n    assert True\n  ";

        assertThat(LlmResponseParser.extractCode(content))
                .isEqualTo("from solution import *\n\ndef test_a():\n    assert True");
    }

    @Test
    void extractCode_returnsFirstOfMultipleBlocks() {
        String content = "```python\nfirst = 1\n```\ntext\n```python\nsecond = 2\n```";

        assertThat(LlmResponseParser.extractCode(content)).isEqualTo("first = 1\n");
    }

    @Test
    void extractCode_returnsEmptyStringForNull() {
        assertThat(LlmResponseParser.extractCode(null)).isEmpty();
    }
}
