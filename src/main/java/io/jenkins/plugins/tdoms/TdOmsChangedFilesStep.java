package io.jenkins.plugins.tdoms;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import io.jenkins.plugins.tdoms.git.GitDiffResolver;
import io.jenkins.plugins.tdoms.model.ChangedFile;
import io.jenkins.plugins.tdoms.util.TdOmsLogLevel;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves the set of source files changed relative to a compare branch, returning them as a
 * list so that pipeline scripts can decide what to do with each one (e.g. call
 * {@code bldIfsOms} per entry).
 */
public class TdOmsChangedFilesStep extends Step {

    private String compareBranch = "origin/master";
    private String gitCredentialsId;
    private String logLevel = String.valueOf(TdOmsLogLevel.DEFAULT.getCode());

    @DataBoundConstructor
    public TdOmsChangedFilesStep() {
    }

    public String getCompareBranch() {
        return compareBranch;
    }

    @DataBoundSetter
    public void setCompareBranch(String compareBranch) {
        this.compareBranch = compareBranch;
    }

    public String getGitCredentialsId() {
        return gitCredentialsId;
    }

    @DataBoundSetter
    public void setGitCredentialsId(String gitCredentialsId) {
        this.gitCredentialsId = gitCredentialsId;
    }

    public String getLogLevel() {
        return logLevel;
    }

    @DataBoundSetter
    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(context, this);
    }

    private static class Execution extends SynchronousNonBlockingStepExecution<List<Map<String, String>>> {
        private static final long serialVersionUID = 1L;

        private final transient TdOmsChangedFilesStep step;

        Execution(StepContext context, TdOmsChangedFilesStep step) {
            super(context);
            this.step = step;
        }

        @Override
        protected List<Map<String, String>> run() throws Exception {
            StepContext ctx = getContext();
            TaskListener listener = ctx.get(TaskListener.class);
            FilePath workspace = ctx.get(FilePath.class);
            Run<?, ?> run = ctx.get(Run.class);
            PrintStream logger = listener.getLogger();
            TdOmsLogLevel level = TdOmsLogLevel.parse(step.getLogLevel());

            org.eclipse.jgit.transport.CredentialsProvider gitCredentialsProvider = null;
            if (step.getGitCredentialsId() != null && !step.getGitCredentialsId().trim().isEmpty()) {
                StandardUsernamePasswordCredentials gitCredentials = CredentialsProvider.findCredentialById(
                        step.getGitCredentialsId(), StandardUsernamePasswordCredentials.class, run);
                if (gitCredentials != null) {
                    gitCredentialsProvider = new UsernamePasswordCredentialsProvider(
                            gitCredentials.getUsername(), gitCredentials.getPassword().getPlainText());
                } else {
                    level.println(logger, TdOmsLogLevel.WARNING,
                            "Warning: gitCredentialsId '" + step.getGitCredentialsId() + "' did not resolve to any credentials.");
                }
            }

                List<String> changedFiles = GitDiffResolver.getChangedFiles(
                    workspace, step.getCompareBranch(), listener, gitCredentialsProvider, level);

                level.println(logger, TdOmsLogLevel.INFO,
                    "TD/OMS: Found " + changedFiles.size() + " changed file(s) against " + step.getCompareBranch());

            List<Map<String, String>> result = new ArrayList<>();
            for (String relativePath : changedFiles) {
                result.add(new ChangedFile(relativePath).toMap());
            }
            return result;
        }
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "omsChangedFiles";
        }

        @Override
        public String getDisplayName() {
            return "Get list of changed sources (TD/OMS)";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            Set<Class<?>> context = new HashSet<>();
            context.add(TaskListener.class);
            context.add(FilePath.class);
            context.add(Run.class);
            return Collections.unmodifiableSet(context);
        }

        @RequirePOST
        public ListBoxModel doFillLogLevelItems() {
            checkReadPermission();
            ListBoxModel levels = new ListBoxModel();
            for (TdOmsLogLevel level : TdOmsLogLevel.values()) {
                levels.add(level.getCode() + " - " + level.name(), String.valueOf(level.getCode()));
            }
            return levels;
        }

        @RequirePOST
        public ListBoxModel doFillGitCredentialsIdItems(@AncestorInPath Item item, @QueryParameter String gitCredentialsId) {
            checkReadPermission();
            StandardListBoxModel result = new StandardListBoxModel();
            if (item == null || !item.hasPermission(Item.EXTENDED_READ)) {
                return result.includeCurrentValue(gitCredentialsId);
            }
            return result
                    .includeEmptyValue()
                    .includeAs(ACL.SYSTEM2, item, StandardUsernamePasswordCredentials.class, URIRequirementBuilder.fromUri("").build())
                    .includeCurrentValue(gitCredentialsId);
        }

        private static void checkReadPermission() {
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            if (jenkins != null) {
                jenkins.checkPermission(Jenkins.READ);
            }
        }
    }
}
