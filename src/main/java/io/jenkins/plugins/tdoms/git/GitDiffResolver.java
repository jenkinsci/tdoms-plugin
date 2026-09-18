package io.jenkins.plugins.tdoms.git;

import hudson.FilePath;
import hudson.model.TaskListener;
import io.jenkins.plugins.tdoms.util.TdOmsLogLevel;
import jenkins.agents.ControllerToAgentFileCallable;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class GitDiffResolver {

    public static List<String> getChangedFiles(FilePath workspace, String compareBranch, TaskListener listener) throws IOException, InterruptedException {
        return getChangedFiles(workspace, compareBranch, listener, null, null, TdOmsLogLevel.DEFAULT);
    }

    public static List<String> getChangedFiles(FilePath workspace, String compareBranch, TaskListener listener,
                                                String username, String password, TdOmsLogLevel level) throws IOException, InterruptedException {
        PrintStream logger = listener.getLogger();
        Resolution resolution;
        try {
            resolution = workspace.act(new ResolveGitDiff(compareBranch, username, password));
        } catch (IOException e) {
            level.println(logger, TdOmsLogLevel.ERROR, "Error resolving Git diff: " + e.getMessage());
            throw e;
        }
        for (LogEntry entry : resolution.logs) {
            level.println(logger, entry.level, entry.message);
        }
        return resolution.changedFiles;
    }

    private static Resolution resolve(File workspace, String compareBranch, String username, String password)
            throws IOException {
        List<String> changedFiles = new ArrayList<>();
        List<LogEntry> logs = new ArrayList<>();

        File gitDir = new File(workspace, ".git");
        if (!gitDir.exists()) {
            logs.add(new LogEntry(TdOmsLogLevel.WARNING,
                    "Warning: .git directory not found in workspace at " + workspace));
            return new Resolution(changedFiles, logs);
        }

        try (Git git = Git.open(workspace)) {
            Repository repository = git.getRepository();

            ObjectId headId = repository.resolve("HEAD");
            ObjectId compareId = repository.resolve(compareBranch);

            if (headId == null) {
                logs.add(new LogEntry(TdOmsLogLevel.WARNING, "Warning: Cannot resolve HEAD in repository."));
                return new Resolution(changedFiles, logs);
            }

            if (compareId == null) {
                // Fallback attempt to resolve origin/master or compareBranch without origin prefix
                String fallbackBranch = compareBranch.startsWith("origin/") ? compareBranch.substring(7) : "origin/" + compareBranch;
                compareId = repository.resolve(fallbackBranch);
            }

            if (compareId == null) {
                // Common cause: multibranch pipelines only fetch the refspec for the branch being
                // built, so remote-tracking refs like 'origin/master' are never present locally.
                // Try an on-demand fetch of the missing branch before giving up.
                logs.add(new LogEntry(TdOmsLogLevel.DEBUG,
                        "Compare ref '" + compareBranch + "' not found locally. Attempting to fetch it from remote..."));
                CredentialsProvider credentialsProvider = username == null
                        ? null
                        : new UsernamePasswordCredentialsProvider(username, password == null ? "" : password);
                compareId = fetchAndResolve(git, repository, compareBranch, credentialsProvider, logs);
            }

            if (compareId == null) {
                logs.add(new LogEntry(TdOmsLogLevel.WARNING,
                        "Warning: Cannot resolve compare branch '" + compareBranch + "'. Returning empty diff."));
                logs.add(new LogEntry(TdOmsLogLevel.DEBUG,
                        "Hint: ensure the checkout fetches this branch, e.g. add a refspec such as "
                        + "'+refs/heads/*:refs/remotes/origin/*' to the SCM configuration, run "
                        + "'git fetch origin " + branchNameOf(compareBranch) + "' before this step, or set "
                        + "'gitCredentialsId' on tdOmsChangedFiles so the on-demand fetch can authenticate."));
                return new Resolution(changedFiles, logs);
            }

            AbstractTreeIterator oldTreeParser = prepareTreeParser(repository, compareId);
            AbstractTreeIterator newTreeParser = prepareTreeParser(repository, headId);

            List<DiffEntry> diffs = git.diff()
                    .setOldTree(oldTreeParser)
                    .setNewTree(newTreeParser)
                    .call();

            for (DiffEntry entry : diffs) {
                if (entry.getChangeType() != DiffEntry.ChangeType.DELETE) {
                    String path = entry.getNewPath();
                    if (isEligibleSourceFile(path)) {
                        changedFiles.add(path);
                    }
                }
            }
        } catch (Exception e) {
            throw new IOException("Failed to resolve git diff against " + compareBranch, e);
        }

        return new Resolution(changedFiles, logs);
    }

    public static boolean isEligibleSourceFile(String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }
        String normalized = path.replace('\\', '/');

        // Exclude root level files (must be inside a folder structure)
        if (!normalized.contains("/")) {
            return false;
        }

        // Exclude hidden files or paths with segments starting with dot
        String[] segments = normalized.split("/");
        for (String segment : segments) {
            if (segment.startsWith(".")) {
                return false;
            }
        }

        return true;
    }

    /**
     * Attempts to fetch the missing compare branch from its remote and re-resolve it.
     * Best-effort only: relies on any credentials/credential helper already usable by the
     * agent's git installation (e.g. cached from the initial checkout). Failures are logged
     * and treated as "still unresolved" rather than aborting the step.
     */
    private static ObjectId fetchAndResolve(Git git, Repository repository, String compareBranch,
                                             CredentialsProvider credentialsProvider, List<LogEntry> logs) {
        String remoteName = compareBranch.contains("/") ? compareBranch.substring(0, compareBranch.indexOf('/')) : "origin";
        String branchName = branchNameOf(compareBranch);
        try {
            FetchCommand fetch = git.fetch()
                    .setRemote(remoteName)
                    .setRefSpecs(new RefSpec("+refs/heads/" + branchName + ":refs/remotes/" + remoteName + "/" + branchName));
            if (credentialsProvider != null) {
                fetch.setCredentialsProvider(credentialsProvider);
            }
            fetch.call();
            return repository.resolve(remoteName + "/" + branchName);
        } catch (Exception e) {
            logs.add(new LogEntry(TdOmsLogLevel.WARNING,
                "Warning: On-demand fetch of '" + compareBranch + "' failed: " + e.getMessage()));
            return null;
        }
    }

    private static String branchNameOf(String compareBranch) {
        return compareBranch.contains("/") ? compareBranch.substring(compareBranch.indexOf('/') + 1) : compareBranch;
    }

    private static AbstractTreeIterator prepareTreeParser(Repository repository, ObjectId objectId) throws IOException {
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(objectId);
            RevTree tree = walk.parseTree(commit.getTree().getId());

            CanonicalTreeParser treeParser = new CanonicalTreeParser();
            try (ObjectReader reader = repository.newObjectReader()) {
                treeParser.reset(reader, tree.getId());
            }
            walk.dispose();
            return treeParser;
        }
    }

    private static final class ResolveGitDiff implements ControllerToAgentFileCallable<Resolution> {
        @Serial
        private static final long serialVersionUID = 1L;

        private final String compareBranch;
        private final String username;
        private final String password;

        private ResolveGitDiff(String compareBranch, String username, String password) {
            this.compareBranch = compareBranch;
            this.username = username;
            this.password = password;
        }

        @Override
        public Resolution invoke(File workspace, hudson.remoting.VirtualChannel channel) throws IOException {
            return resolve(workspace, compareBranch, username, password);
        }
    }

    private static final class Resolution implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final List<String> changedFiles;
        private final List<LogEntry> logs;

        private Resolution(List<String> changedFiles, List<LogEntry> logs) {
            this.changedFiles = changedFiles;
            this.logs = logs;
        }
    }

    private static final class LogEntry implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final TdOmsLogLevel level;
        private final String message;

        private LogEntry(TdOmsLogLevel level, String message) {
            this.level = level;
            this.message = message;
        }
    }
}
