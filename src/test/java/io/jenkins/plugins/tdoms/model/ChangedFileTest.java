package io.jenkins.plugins.tdoms.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChangedFileTest {

    @Test
    void identifiesRootAndNestedFiles() {
        assertEquals("true", new ChangedFile("README.md").toMap().get("isRootFile"));
        assertEquals("false", new ChangedFile("src/program.rpgle").toMap().get("isRootFile"));
        assertEquals("false", new ChangedFile("src\\program.rpgle").toMap().get("isRootFile"));
    }

    @Test
    void identifiesHiddenPathsByComponent() {
        assertEquals("true", new ChangedFile(".env").toMap().get("isHidden"));
        assertEquals("true", new ChangedFile("config/.secrets").toMap().get("isHidden"));
        assertEquals("true", new ChangedFile(".github/workflows/build.yml").toMap().get("isHidden"));
        assertEquals("true", new ChangedFile("src/.github/workflows/build.yml").toMap().get("isHidden"));
        assertEquals("false", new ChangedFile("src/program.rpgle").toMap().get("isHidden"));
    }
}