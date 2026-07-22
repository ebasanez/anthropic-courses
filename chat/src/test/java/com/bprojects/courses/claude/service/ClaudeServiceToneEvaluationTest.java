package com.bprojects.courses.claude.service;

import com.bprojects.courses.claude.vo.Tone;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class ClaudeServiceToneEvaluationTest {

    private record EvalTask(String id, String type, String task, String criteria) {}

    private static List<EvalTask> tasks;

    @Autowired
    private ClaudeService claudeService;

    @BeforeAll
    static void loadDataset() throws IOException {
        var mapper = new ObjectMapper();
        var resource = new ClassPathResource("aws-prompt-evaluation-dataset-lite.json");
        tasks = mapper.readValue(resource.getInputStream(),
                mapper.getTypeFactory().constructCollectionType(List.class, EvalTask.class));
    }

    private static final int MIN_PASSING_SCORE = 7;

    @ParameterizedTest(name = "tone={0}")
    @EnumSource(Tone.class)
    @DisplayName("Evaluate AWS task dataset against each Tone's system prompt")
    void evaluateToneAgainstDataset(Tone tone) {
        List<String> lowScores = new ArrayList<>();
        int totalScore = 0;
        for (EvalTask task : tasks) {
            String response = claudeService.generateResponse(task.task(), 1024, null, null, null, null, tone);
            int score = Grader.validate(task.type(), task.task(),response, task.criteria(),
                    prompt -> claudeService.generateResponse(prompt, 1024, 0.0, null, null, null, null));
            totalScore += score;
            System.out.printf("[%s][%s][%s] score=%d/10%n", tone, task.id(), task.type(), score);
            if (score < MIN_PASSING_SCORE) {
                lowScores.add(task.id() + ": score=" + score);
            }
        }
        double averageScore = (double) totalScore / tasks.size();
        assertThat(lowScores)
                .withFailMessage("Tone %s averaged %.1f/10, %d/%d tasks scored below %d:%n%s",
                        tone, averageScore, lowScores.size(), tasks.size(), MIN_PASSING_SCORE,
                        String.join("\n", lowScores))
                .isEmpty();
    }

}
