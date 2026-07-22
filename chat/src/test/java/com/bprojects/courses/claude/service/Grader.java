package com.bprojects.courses.claude.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

class Grader {

    private final static int GRADE_COMPILED_FORMAT_CORRECT = 5;
    private final static int GRADE_COMPILED_FORMAT_INCORRECT = 3;
    private final static int GRADE_MIN = 1;
    private final static int GRADE_MAX = 10;

    private static final String MODEL_GRADER_PROMPT = """
            You are an expert code reviewer. Evaluate this AI-generated solution.

            Original Task:
            <task>
            %s
            </task>
            
            Solution to evaluate:
            <solution>
            %s
            </solution>

            Criteria you should use to evaluate the solution:
            <criteria>
            %s
            </criteria>
            
            Provide your evaluation as a structured JSON object with:
            - "strengths": An array of 1-3 key strengths
            - "weaknesses": An array of 1-3 key areas for improvement
            - "reasoning": A concise explanation of your assessment
            - "score": A number between 1-10""";

    private Grader() {}

    /**
     * Combines a format-based code validation score with a model-graded score for the given task/response pair.
     */
    static int validate(String type, String task, String response, String criteria, Function<String, String> modelCaller) {
        if (response == null || response.isBlank()) {
            return GRADE_MIN;
        }
        int formatScore = validateFormat(type, response);
        int modelScore = validateWithModel(task, response, criteria, modelCaller);
        return Math.round((formatScore + modelScore) / 2f);
    }

    private static int validateFormat(String type, String response) {
        return switch (type) {
            case "json" -> validateJson(response);
            case "regex" -> validateRegex(response);
            case "java" -> validateJava(response);
            default -> GRADE_MIN;
        };
    }

    private static int validateWithModel(String task, String response, String criteria, Function<String, String> modelCaller) {
        try {
            String prompt = MODEL_GRADER_PROMPT.formatted(task, response, criteria);
            String evaluation = modelCaller.apply(prompt);
            JsonNode node = new ObjectMapper().readTree(extractJsonBlock(evaluation));
            int score = node.path("score").asInt(GRADE_MIN);
            System.out.printf("[EVALUATION RESULT]%nTask:%n%s%nSolution:%n%s%nEvaluation result:%n%s", task,response, node);
            return Math.max(GRADE_MIN, Math.min(GRADE_MAX, score));
        } catch (IOException | RuntimeException e) {
            return GRADE_MIN;
        }
    }

    private static int validateJson(String response) {
        try {
            new ObjectMapper().readTree(extractJsonBlock(response));
            return GRADE_COMPILED_FORMAT_CORRECT;
        } catch (IOException e) {
            return response.contains("{") || response.contains("[") ? 3 : 1;
        }
    }

    private static String extractJsonBlock(String response) {
        int start = Math.min(indexOfOrMax(response, '{'), indexOfOrMax(response, '['));
        int end = Math.max(response.lastIndexOf('}'), response.lastIndexOf(']'));
        if (start == Integer.MAX_VALUE || end < start) {
            return response.trim();
        }
        return response.substring(start, end + 1);
    }

    private static int indexOfOrMax(String s, char c) {
        int idx = s.indexOf(c);
        return idx < 0 ? Integer.MAX_VALUE : idx;
    }

    private static int validateRegex(String response) {
        String trimmed = response.trim();
        if (compiles(trimmed)) {
            return GRADE_COMPILED_FORMAT_CORRECT;
        }
        if (compiles(trimmed.lines().findFirst().orElse(""))) {
            return GRADE_COMPILED_FORMAT_INCORRECT;
        }
        return GRADE_MIN;
    }

    private static boolean compiles(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return false;
        }
        try {
            Pattern.compile(pattern);
            return true;
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    private static int validateJava(String response) {
        boolean looksLikeMethod = response.contains("(") && response.contains(")")
                && response.contains("{") && response.contains("}");
        boolean hasModifier = response.contains("public") || response.contains("private")
                || response.contains("static");
        if (looksLikeMethod && hasModifier) {
            return GRADE_COMPILED_FORMAT_CORRECT;
        }
        if (looksLikeMethod || hasModifier) {
            return GRADE_COMPILED_FORMAT_INCORRECT;
        }
        return GRADE_MIN;
    }
}
