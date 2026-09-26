package io.jenkins.plugins.tdoms;

final class TdOmsCommandScope {
    static final String DEFAULT_APPLICATION = "*CALC";
    static final String DEFAULT_TASK = "*CALC";

    private TdOmsCommandScope() {
    }
 
    static String format(String branch, String application, String task) {
        String appValue = valueOrDefault(application, DEFAULT_APPLICATION);
        String taskValue = valueOrDefault(task, DEFAULT_TASK);
        String branchValue = branch == null ? "" : branch.trim();
        if (branchValue.isEmpty() && ("*CALC".equalsIgnoreCase(appValue) || "*CALC".equalsIgnoreCase(taskValue))) {
            throw new IllegalArgumentException("branch is required unless application and task are both explicit.");
        }
        String branchKeyword = branchValue.isEmpty() ? "" : " BRANCH('" + branchValue.replace("'", "''") + "')";
        return branchKeyword + " APPC(" + appValue + ") TASK(" + taskValue + ")";
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }
}