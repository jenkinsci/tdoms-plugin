package io.jenkins.plugins.tdoms;

import hudson.Extension;
import org.kohsuke.stapler.DataBoundConstructor;

public class TdOmsReleaseBuildQStep extends TdOmsCommandStep {
    @DataBoundConstructor
    public TdOmsReleaseBuildQStep() {
    }

    @Override
    protected String actionCode() {
        return "*RLSBQ";
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