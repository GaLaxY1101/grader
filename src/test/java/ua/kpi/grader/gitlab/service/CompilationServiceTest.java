package ua.kpi.grader.gitlab.service;

import org.junit.jupiter.api.Test;
import ua.kpi.grader.course.entity.Language;
import ua.kpi.grader.testgen.sandbox.CompileCheck;
import ua.kpi.grader.testgen.sandbox.SandboxRunner;
import ua.kpi.grader.testgen.sandbox.SandboxUnavailableException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Sandbox behaviour itself is covered by SandboxRunnerTest (tag "docker"). */
class CompilationServiceTest {

    private final SandboxRunner sandboxRunner = mock(SandboxRunner.class);
    private final CompilationService service = new CompilationService(sandboxRunner);

    @Test
    void compileSolutionWithTests_compiles_returnsSuccess() {
        when(sandboxRunner.compileOnly(Language.CPP, "solution", "tests"))
                .thenReturn(new CompileCheck(true, null));

        CompilationResult result = service.compileSolutionWithTests("solution", "tests", Language.CPP);

        assertThat(result.success()).isTrue();
        assertThat(result.output()).isNull();
    }

    @Test
    void compileSolution_compilerError_returnsOutput() {
        when(sandboxRunner.compileOnly(Language.PYTHON, "def f(", null))
                .thenReturn(new CompileCheck(false, "SyntaxError: '(' was never closed"));

        CompilationResult result = service.compileSolution("def f(", Language.PYTHON);

        assertThat(result.success()).isFalse();
        assertThat(result.output()).contains("SyntaxError");
    }

    @Test
    void compileSolution_sandboxUnavailable_returnsInternalError() {
        when(sandboxRunner.compileOnly(Language.CPP, "int x;", null))
                .thenThrow(new SandboxUnavailableException("docker not found", new IOException("docker")));

        CompilationResult result = service.compileSolution("int x;", Language.CPP);

        assertThat(result.success()).isFalse();
        assertThat(result.output()).contains("docker not found");
    }
}
