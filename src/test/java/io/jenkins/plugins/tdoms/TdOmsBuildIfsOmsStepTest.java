package io.jenkins.plugins.tdoms;

import hudson.util.FormValidation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TdOmsBuildIfsOmsStepTest {

    @Test
    void defaultsMatchBldIfsOmsDefaults() {
        TdOmsBuildIfsOmsStep step = new TdOmsBuildIfsOmsStep();

        assertEquals(TdOmsBuildIfsOmsStep.DEFAULT_ACTION, step.getAction());
        assertEquals(TdOmsBuildIfsOmsStep.DEFAULT_APPLICATION, step.getApplication());
        assertEquals(TdOmsBuildIfsOmsStep.DEFAULT_TASK, step.getTask());
        assertEquals(TdOmsBuildIfsOmsStep.DEFAULT_ROUTE_CODE, step.getRouteCode());
        assertEquals(TdOmsBuildIfsOmsStep.DEFAULT_CONNECT_STREAM_FILE, step.getConnectStreamFile());
        assertEquals(TdOmsBuildIfsOmsStep.DEFAULT_COPY_TO_SOURCE_FILE, step.getCopyToSourceFile());
        assertEquals(TdOmsBuildIfsOmsStep.DEFAULT_CCSID, step.getCcsid());
        assertEquals(TdOmsBuildIfsOmsStep.DEFAULT_ADD_TO_BUILD_QUEUE, step.getAddToBuildQueue());
        assertEquals(TdOmsBuildIfsOmsStep.DEFAULT_RELEASE_BUILD_QUEUE, step.getReleaseBuildQueue());
    }

    @Test
    void descriptorValidatesRequiredPaths() {
        TdOmsBuildIfsOmsStep.DescriptorImpl descriptor = new TdOmsBuildIfsOmsStep.DescriptorImpl();

        FormValidation missingTarget = descriptor.doCheckTargetPath(" ");
        FormValidation validTarget = descriptor.doCheckTargetPath("/QOpenSys/OMSIFS");
        FormValidation missingRelative = descriptor.doCheckRelativePath(null);
        FormValidation validRelative = descriptor.doCheckRelativePath("src/main.rpgle");

        assertNotNull(missingTarget);
        assertEquals(FormValidation.Kind.ERROR, missingTarget.kind);
        assertEquals(FormValidation.Kind.OK, validTarget.kind);
        assertEquals(FormValidation.Kind.ERROR, missingRelative.kind);
        assertEquals(FormValidation.Kind.OK, validRelative.kind);
    }

    @Test
    void normalizesSafeRelativePaths() {
        assertEquals("src/main.rpgle", TdOmsBuildIfsOmsStep.normalizeRelativePath(" src\\main.rpgle "));
        assertEquals("src/main.rpgle", TdOmsBuildIfsOmsStep.normalizeRelativePath("src/./main.rpgle"));
    }

    @Test
    void rejectsWorkspaceEscapingPaths() {
        assertThrows(IllegalArgumentException.class,
                () -> TdOmsBuildIfsOmsStep.normalizeRelativePath("../outside.txt"));
        assertThrows(IllegalArgumentException.class,
                () -> TdOmsBuildIfsOmsStep.normalizeRelativePath("src/../../outside.txt"));
        assertThrows(IllegalArgumentException.class,
                () -> TdOmsBuildIfsOmsStep.normalizeRelativePath("C:/outside.txt"));
        assertThrows(IllegalArgumentException.class,
                () -> TdOmsBuildIfsOmsStep.normalizeRelativePath("/outside.txt"));
    }
}
