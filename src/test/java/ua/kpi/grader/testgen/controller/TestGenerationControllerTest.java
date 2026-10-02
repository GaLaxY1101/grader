package ua.kpi.grader.testgen.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ua.kpi.grader.common.exception.GlobalExceptionHandler;
import ua.kpi.grader.common.exception.ResourceNotFoundException;
import ua.kpi.grader.testgen.service.TestGenerationService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TestGenerationControllerTest {

    @Mock
    private TestGenerationService testGenerationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestGenerationController(testGenerationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void start_returns202WithJobId() throws Exception {
        when(testGenerationService.startJob(any())).thenReturn(17L);

        mockMvc.perform(post("/api/test-generation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"taskDescription": "Add two numbers.", "functionSignature": "def add(a, b):",
                                 "language": "PYTHON", "referenceSolution": "def add(a, b):\\n    return a + b\\n"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(17));
    }

    @Test
    void start_returns400_whenReferenceSolutionMissing() throws Exception {
        mockMvc.perform(post("/api/test-generation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"taskDescription": "Add two numbers.", "language": "PYTHON"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("referenceSolution")));
        verifyNoInteractions(testGenerationService);
    }

    @Test
    void start_returns400_whenOverrideOutOfRange() throws Exception {
        mockMvc.perform(post("/api/test-generation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"taskDescription": "x", "language": "CPP", "referenceSolution": "int f();",
                                 "config": {"maxIterations": 9}}
                                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(testGenerationService);
    }

    @Test
    void getJob_returns404_forUnknownJob() throws Exception {
        when(testGenerationService.getJob(5L)).thenThrow(new ResourceNotFoundException("Test generation job not found with id: 5"));

        mockMvc.perform(get("/api/test-generation/5"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
