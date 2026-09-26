package io.jenkins.plugins.tdoms;

import hudson.AbortException;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.ibmisteps.model.CallResult;
import org.jenkinsci.plugins.ibmisteps.model.IBMi;
import org.jenkinsci.plugins.ibmisteps.model.IBMiContext;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.Set;

abstract class TdOmsCommandStep extends Step {
    private String branch;
    private String application = TdOmsCommandScope.DEFAULT_APPLICATION;
    private String task = TdOmsCommandScope.DEFAULT_TASK;

    public String getBranch() {
        return branch;
    }

    @DataBoundSetter
    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getApplication() {
        return application;
    }

    @DataBoundSetter
    public void setApplication(String application) {
        this.application = application;
    }

    public String getTask() {
        return task;
    }

    @DataBoundSetter
    public void setTask(String task) {
        this.task = task;
    }

    protected abstract String actionCode();

    static String buildCommand(TdOmsCommandStep step) {
        return "BLDIFSOMS ACTC(" + step.actionCode() + ")"
                + TdOmsCommandScope.format(step.getBranch(), step.getApplication(), step.getTask());
    }

    @Override
    public StepExecution start(StepContext context) {
        return new Execution(context, this);
    }

    private static class Execution extends SynchronousNonBlockingStepExecution<Void> {
        private static final long serialVersionUID = 1L;

        private final transient TdOmsCommandStep step;

        Execution(StepContext context, TdOmsCommandStep step) {
            super(context);
            this.step = step;
        }

        @Override
        protected Void run() throws Exception {
            String command = buildCommand(step);
            TaskListener listener = getContext().get(TaskListener.class);
            IBMiContext ibmiContext = getContext().get(IBMiContext.class);
            IBMi ibmi = ibmiContext.getIBMi(listener);
            listener.getLogger().println("Running: " + command);
            CallResult result = ibmi.executeCommand(command);
            if (!result.isSuccessful()) {
                throw new AbortException("BLDIFSOMS failed: " + result.getPrettyMessages());
            }
            return null;
        }
    }

    abstract static class DescriptorImpl extends StepDescriptor {
        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(TaskListener.class, IBMiContext.class);
        }
    }
}