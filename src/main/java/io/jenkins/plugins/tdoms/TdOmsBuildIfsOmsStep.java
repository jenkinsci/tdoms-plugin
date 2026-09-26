package io.jenkins.plugins.tdoms;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.ibm.as400.access.IFSFile;
import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import io.jenkins.plugins.tdoms.util.TdOmsLogLevel;
import org.jenkinsci.plugins.ibmisteps.configuration.IBMiGlobalConfiguration;
import org.jenkinsci.plugins.ibmisteps.configuration.IBMiServerConfiguration;
import org.jenkinsci.plugins.ibmisteps.model.CallResult;
import org.jenkinsci.plugins.ibmisteps.model.IBMi;
import org.jenkinsci.plugins.ibmisteps.model.IBMiContext;
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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.PrintStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Uploads a single source file to IFS and triggers {@code BLDIFSOMS} for it.
 * Intended to be
 * called once per entry returned by {@code tdOmsChangedFiles}.
 */
public class TdOmsBuildIfsOmsStep extends Step {

    public static final String DEFAULT_ACTION = "*PUSH";
    public static final String DEFAULT_APPLICATION = "*CALC";
    public static final String DEFAULT_TASK = "*CALC";
    public static final String DEFAULT_ROUTE_CODE = "*REG";
    public static final String DEFAULT_CONNECT_STREAM_FILE = "*REG";
    public static final String DEFAULT_COPY_TO_SOURCE_FILE = "*REG";
    public static final String DEFAULT_CONNECT_OBJECT = "*YES";
    public static final int DEFAULT_CCSID = 1208;
    public static final String DEFAULT_ADD_TO_BUILD_QUEUE = "*NO";
    public static final String DEFAULT_RELEASE_BUILD_QUEUE = "*NO";

    private String server;
    private String targetPath;
    private String relativePath;
    private String branch;
    private String application = DEFAULT_APPLICATION;
    private String task = DEFAULT_TASK;
    private String routeCode = DEFAULT_ROUTE_CODE;
    private String connectStreamFile = DEFAULT_CONNECT_STREAM_FILE;
    private String streamFileLabels;
    private String copyToSourceFile = DEFAULT_COPY_TO_SOURCE_FILE;
    private String connectObject = DEFAULT_CONNECT_OBJECT;
    private String objectLabels;
    private int ccsid = DEFAULT_CCSID;
    private String addToBuildQueue = DEFAULT_ADD_TO_BUILD_QUEUE;
    private String releaseBuildQueue = DEFAULT_RELEASE_BUILD_QUEUE;
    private String logLevel = String.valueOf(TdOmsLogLevel.DEFAULT.getCode());

    @DataBoundConstructor
    public TdOmsBuildIfsOmsStep() {
    }

    public String getServer() {
        return server;
    }

    @DataBoundSetter
    public void setServer(String server) {
        this.server = server;
    }

    public String getTargetPath() {
        return targetPath;
    }

    @DataBoundSetter
    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }

    public String getRelativePath() {
        return relativePath;
    }

    @DataBoundSetter
    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

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

    public String getRouteCode() {
        return routeCode;
    }

    @DataBoundSetter
    public void setRouteCode(String routeCode) {
        this.routeCode = routeCode;
    }

    public String getConnectStreamFile() {
        return connectStreamFile;
    }

    @DataBoundSetter
    public void setConnectStreamFile(String connectStreamFile) {
        this.connectStreamFile = connectStreamFile;
    }

    public String getStreamFileLabels() {
        return streamFileLabels;
    }

    @DataBoundSetter
    public void setStreamFileLabels(String streamFileLabels) {
        this.streamFileLabels = streamFileLabels;
    }

    public String getCopyToSourceFile() {
        return copyToSourceFile;
    }

    @DataBoundSetter
    public void setCopyToSourceFile(String copyToSourceFile) {
        this.copyToSourceFile = copyToSourceFile;
    }

    public String getConnectObject() {
        return connectObject;
    }

    @DataBoundSetter
    public void setConnectObject(String connectObject) {
        this.connectObject = connectObject;
    }

    public String getObjectLabels() {
        return objectLabels;
    }

    @DataBoundSetter
    public void setObjectLabels(String objectLabels) {
        this.objectLabels = objectLabels;
    }

    public int getCcsid() {
        return ccsid;
    }

    @DataBoundSetter
    public void setCcsid(int ccsid) {
        this.ccsid = ccsid;
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

    static String normalizeRelativePath(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("omsPush requires a 'relativePath' parameter.");
        }

        String normalized = value.trim().replace('\\', '/');
        Path path = Paths.get(normalized).normalize();
        if (path.isAbsolute() || normalized.startsWith("/") || normalized.matches("^[A-Za-z]:/.*")
                || path.startsWith("..")) {
            throw new IllegalArgumentException("omsPush requires a workspace-relative 'relativePath'.");
        }
        return path.toString().replace('\\', '/');
    }

    static String buildCommand(TdOmsBuildIfsOmsStep step, String relativePath) {
        String streamFileLabels = formatLabels("LBLSTMF", step.getStreamFileLabels());
        String objectLabels = formatLabels("LBLOBJ", step.getObjectLabels());

        return String.format(
                "BLDIFSOMS ACTC(*PUSH)%s ROTC(%s) STMF('%s') DIR('%s') "
                        + "CONSTMF(%s)%s CPYTOSRCF(%s) CONOBJ(%s)%s CCSID(%d) ADDTOBQ(%s) RLSBQ(%s)",
                TdOmsCommandScope.format(step.getBranch(), step.getApplication(), step.getTask()),
                valueOrDefault(step.getRouteCode(), DEFAULT_ROUTE_CODE),
                escapeClValue(relativePath.replace('\\', '/')),
                escapeClValue(step.getTargetPath()),
                valueOrDefault(step.getConnectStreamFile(), DEFAULT_CONNECT_STREAM_FILE),
                streamFileLabels,
                valueOrDefault(step.getCopyToSourceFile(), DEFAULT_COPY_TO_SOURCE_FILE),
                valueOrDefault(step.getConnectObject(), DEFAULT_CONNECT_OBJECT),
                objectLabels,
                step.getCcsid(),
                valueOrDefault(step.getAddToBuildQueue(), DEFAULT_ADD_TO_BUILD_QUEUE),
                valueOrDefault(step.getReleaseBuildQueue(), DEFAULT_RELEASE_BUILD_QUEUE));
    }

    private static String formatLabels(String keyword, String labels) {
        if (labels == null || labels.trim().isEmpty()) {
            return "";
        }

        String[] values = labels.split(",", -1);
        if (values.length > 5) {
            throw new IllegalArgumentException(keyword + " accepts at most five comma-separated labels.");
        }

        StringBuilder formatted = new StringBuilder(" ").append(keyword).append("(");
        for (String value : values) {
            String label = value.trim();
            if (label.isEmpty()) {
                throw new IllegalArgumentException(keyword + " labels must not be blank.");
            }
            formatted.append("('").append(escapeClValue(label)).append("')");
        }
        return formatted.append(")").toString();
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }

    private static String escapeClValue(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private static class Execution extends SynchronousNonBlockingStepExecution<Void> {
        private static final long serialVersionUID = 1L;

        private final transient TdOmsBuildIfsOmsStep step;

        Execution(StepContext context, TdOmsBuildIfsOmsStep step) {
            super(context);
            this.step = step;
        }

        @Override
        protected Void run() throws Exception {
            StepContext ctx = getContext();
            TaskListener listener = ctx.get(TaskListener.class);
            FilePath workspace = ctx.get(FilePath.class);
            Run<?, ?> run = ctx.get(Run.class);
            PrintStream logger = listener.getLogger();
            TdOmsLogLevel level = TdOmsLogLevel.parse(step.getLogLevel());

            String relativePath = TdOmsBuildIfsOmsStep.normalizeRelativePath(step.getRelativePath());
            if (step.getTargetPath() == null || step.getTargetPath().trim().isEmpty()) {
                throw new IllegalArgumentException("omsPush requires a 'targetPath' parameter.");
            }
            String bldCmd = TdOmsBuildIfsOmsStep.buildCommand(step, relativePath);

            IBMiContext ibmiContext = ctx.get(IBMiContext.class);
            boolean standalone = false;

            if (ibmiContext == null) {
                if (step.getServer() == null || step.getServer().trim().isEmpty()) {
                    throw new IllegalArgumentException(
                            "omsPush requires either an active 'onIBMi' block or 'server' parameter.");
                }

                IBMiServerConfiguration serverConfig = IBMiGlobalConfiguration.get().getServer(step.getServer());
                if (serverConfig == null) {
                    throw new IllegalArgumentException("IBM i Server configuration '" + step.getServer()
                            + "' not found in Jenkins configuration.");
                }

                StandardUsernamePasswordCredentials credentials = serverConfig.getCredentialsId() != null
                        ? CredentialsProvider.findCredentialById(serverConfig.getCredentialsId(),
                                StandardUsernamePasswordCredentials.class, run)
                        : null;

                ibmiContext = new IBMiContext(
                        serverConfig.getHost(),
                        credentials,
                        serverConfig.getCcsidInt(),
                        serverConfig.isSecure(),
                        null,
                        level == TdOmsLogLevel.TRACE || level == TdOmsLogLevel.DEBUG);
                standalone = true;
            }

            try {
                IBMi ibmi = ibmiContext.getIBMi(listener);

                FilePath localFilePath = workspace.child(relativePath);

                String remoteFilePath = step.getTargetPath() + "/" + relativePath.replace('\\', '/');
                IFSFile remoteIFSFile = new IFSFile(ibmi.getIbmiConnection(), remoteFilePath);

                if (remoteIFSFile.getParentFile() != null) {
                    remoteIFSFile.getParentFile().mkdirs();
                }

                ibmi.upload(localFilePath, remoteIFSFile, 1208);

                CallResult result = ibmi.executeCommand(bldCmd);
                level.println(logger, TdOmsLogLevel.INFO, "Running: " + bldCmd);

                if (result.isSuccessful()) {
                    level.println(logger, TdOmsLogLevel.INFO, "OK: " + relativePath);
                } else {
                    level.println(logger, TdOmsLogLevel.ERROR,
                            "FAILED: " + relativePath + " -> " + result.getPrettyMessages());
                    throw new AbortException(
                            "BLDIFSOMS failed for " + relativePath + ": " + result.getPrettyMessages());
                }

            } finally {
                if (standalone && ibmiContext != null) {
                    ibmiContext.close();
                }
            }

            return null;
        }

    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "omsPush";
        }

        @Override
        public String getDisplayName() {
            return "Run BLDIFSOMS for a source file (TD/OMS)";
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
        public FormValidation doCheckTargetPath(@QueryParameter String value) {
            checkReadPermission();
            if (value == null || value.trim().isEmpty()) {
                return FormValidation.error("Target IFS path is required");
            }
            return FormValidation.ok();
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
        public FormValidation doCheckRelativePath(@QueryParameter String value) {
            checkReadPermission();
            if (value == null || value.trim().isEmpty()) {
                return FormValidation.error("Relative path is required");
            }
            return FormValidation.ok();
        }

        @RequirePOST
        public ListBoxModel doFillServerItems(@AncestorInPath Item item) {
            checkReadPermission();
            ListBoxModel servers = new ListBoxModel();
            servers.add("-- Inherit from enclosing onIBMi block --", "");
            if (item != null && item.hasPermission(Item.EXTENDED_READ)) {
                for (IBMiServerConfiguration server : IBMiGlobalConfiguration.get().getServers()) {
                    servers.add(String.format("%s (%s)", server.getName(), server.getHost()), server.getName());
                }
            }
            return servers;
        }

        private static void checkReadPermission() {
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            if (jenkins != null) {
                jenkins.checkPermission(Jenkins.READ);
            }
        }
    }
}
