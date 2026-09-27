package io.jenkins.plugins.tdoms;

import hudson.Extension;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class TdOmsReleaseBuildQStep extends TdOmsCommandStep {
    public static final String DEFAULT_ADD_TO_BUILD_QUEUE = "*NO";
    public static final String DEFAULT_RELEASE_BUILD_QUEUE = "*BATCH";

    private String addToBuildQueue = DEFAULT_ADD_TO_BUILD_QUEUE;
    private String releaseBuildQueue = DEFAULT_RELEASE_BUILD_QUEUE;

    @DataBoundConstructor
    public TdOmsReleaseBuildQStep() {
    }

    public String getAddToBuildQueue() {
        return addToBuildQueue;
    }

    @DataBoundSetter
    public void setAddToBuildQueue(String addToBuildQueue) {
        this.addToBuildQueue = addToBuildQueue;
    }

    public String getReleaseBuildQueue() {
        return releaseBuildQueue;
    }

    @DataBoundSetter
    public void setReleaseBuildQueue(String releaseBuildQueue) {
        this.releaseBuildQueue = releaseBuildQueue;
    }

    @Override
    protected String actionCode() {
        return "*RLSBQ";
    }

    @Override
    protected String extraKeywords() {
        return " ADDTOBQ(" + valueOrDefault(addToBuildQueue, DEFAULT_ADD_TO_BUILD_QUEUE)
                + ") RLSBQ(" + valueOrDefault(releaseBuildQueue, DEFAULT_RELEASE_BUILD_QUEUE) + ")";
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }

    @Extension
    public static class DescriptorImpl extends TdOmsCommandStep.DescriptorImpl {
        @Override
        public String getFunctionName() {
            return "omsReleaseBuildQ";
        }

        @Override
        public String getDisplayName() {
            return "Release TD/OMS build queue";
        }
    }
}