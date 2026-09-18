package io.jenkins.plugins.tdoms.git;

import hudson.FilePath;
import hudson.model.TaskListener;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GitDiffResolverTest {

    @TempDir
    Path workspace;

    @Test
    void resolvesDiffInsideWorkspaceCallable() throws Exception {
        Path source = workspace.resolve("src/program.rpgle");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "initial");

        String baseCommit;
        try (Git git = Git.init().setDirectory(workspace.toFile()).call()) {
            git.add().addFilepattern(".").call();
            baseCommit = git.commit()
                    .setMessage("initial")
                    .setAuthor("Test", "test@example.com")
                    .setCommitter("Test", "test@example.com")
                    .call()
                    .getName();

            Files.writeString(source, "changed");
            Files.writeString(workspace.resolve("README.md"), "root-level");
            git.add().addFilepattern(".").call();
            git.commit()
                    .setMessage("change")
                    .setAuthor("Test", "test@example.com")
                    .setCommitter("Test", "test@example.com")
                    .call();
        }

        List<String> changedFiles = GitDiffResolver.getChangedFiles(
                new FilePath(workspace.toFile()), baseCommit, TaskListener.NULL);

        assertEquals(List.of("src/program.rpgle"), changedFiles);
    }
}