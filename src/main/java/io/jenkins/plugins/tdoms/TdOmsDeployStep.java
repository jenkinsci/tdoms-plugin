package io.jenkins.plugins.tdoms;

import hudson.Extension;
import org.kohsuke.stapler.DataBoundConstructor;

public class TdOmsDeployStep extends TdOmsCommandStep {
    @DataBoundConstructor
    public TdOmsDeployStep() {
    }

    @Override
    protected String actionCode() {
        return "*DEPLOY";
    }

    @Extension
    public static class DescriptorImpl extends TdOmsCommandStep.DescriptorImpl {
        @Override
        public String getFunctionName() {
            return "omsDeploy";
        }

        @Override
        public String getDisplayName() {
            return "Deploy TD/OMS build queue";
        }
    }
}