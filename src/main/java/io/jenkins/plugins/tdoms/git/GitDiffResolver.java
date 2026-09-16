package io.jenkins.plugins.tdoms.git;

import hudson.FilePath;
import hudson.model.TaskListener;
import io.jenkins.plugins.tdoms.util.TdOmsLogLevel;
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
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

public class GitDiffResolver {

    public static List<String> getChangedFiles(FilePath workspace, String compareBranch, TaskListener listener) throws IOException, InterruptedException {
        return getChangedFiles(workspace, compareBranch, listener, null);
    }

    public static List<String> getChangedFiles(FilePath workspace, String compareBranch, TaskListener listener,
                                                CredentialsProvider credentialsProvider) throws IOException, InterruptedException {
        return getChangedFiles(workspace, compareBranch, listener, credentialsProvider, TdOmsLogLevel.DEFAULT);
    }

    public static List<String> getChangedFiles(FilePath workspace, String compareBranch, TaskListener listener,
                                                CredentialsProvider credentialsProvider, TdOmsLogLevel level) throws IOException, InterruptedException {
        PrintStream logger = listener.getLogger();
        List<String> changedFiles = new ArrayList<>();

        File gitDir = new File(workspace.getRemote(), ".git");
        if (!gitDir.exists()) {
                level.println(logger, TdOmsLogLevel.WARNING,
                    "Warning: .git directory not found in workspace at " + workspace.getRemote());
            return changedFiles;
        }

        try (Git git = Git.open(new File(workspace.getRemote()))) {
            Repository repository = git.getRepository();

            ObjectId headId = repository.resolve("HEAD");
            ObjectId compareId = repository.resolve(compareBranch);

            if (headId == null) {
                level.println(logger, TdOmsLogLevel.WARNING, "Warning: Cannot resolve HEAD in repository.");
                return changedFiles;
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
                level.println(logger, TdOmsLogLevel.DEBUG,
                    "Compare ref '" + compareBranch + "' not found locally. Attempting to fetch it from remote...");
                compareId = fetchAndResolve(git, repository, compareBranch, credentialsProvider, logger, level);
            }

            if (compareId == null) {
                level.println(logger, TdOmsLogLevel.WARNING,
                    "Warning: Cannot resolve compare branch '" + compareBranch + "'. Returning empty diff.");
                level.println(logger, TdOmsLogLevel.DEBUG,
                    "Hint: ensure the checkout fetches this branch, e.g. add a refspec such as "
                        + "'+refs/heads/*:refs/remotes/origin/*' to the SCM configuration, run "
                        + "'git fetch origin " + branchNameOf(compareBranch) + "' before this step, or set "
                        + "'gitCredentialsId' on tdOmsChangedFiles so the on-demand fetch can authenticate.");
                return changedFiles;
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
            level.println(logger, TdOmsLogLevel.ERROR, "Error resolving Git diff: " + e.getMessage());
            throw new IOException("Failed to resolve git diff against " + compareBranch, e);
        }

        return changedFiles;
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
                                             CredentialsProvider credentialsProvider, PrintStream logger,
                                             TdOmsLogLevel level) {
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
                level.println(logger, TdOmsLogLevel.WARNING,
                    "Warning: On-demand fetch of '" + compareBranch + "' failed: " + e.getMessage());
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
}
