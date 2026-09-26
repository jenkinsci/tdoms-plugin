package io.jenkins.plugins.tdoms;

import hudson.util.FormValidation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TdOmsBuildIfsOmsStepTest {

    @Test
    void defaultsMatchBldIfsOmsDefaults() {
        TdOmsBuildIfsOmsStep step = new TdOmsBuildIfsOmsStep();

        assertEquals(TdOmsBuildIfsOmsStep.DEFAULT_APPLICATION, step.getApplication());
        assertEquals(TdOmsBuildIfsOmsStep.DEFAULT_TASK, step.getTask());
        assertEquals(TdOmsBuildIfsOmsStep.DEFAULT_ROUTE_CODE, step.getRouteCode());
        assertEquals(TdOmsBuildIfsOmsStep.DEFAULT_CONNECT_STREAM_FILE, step.getConnectStreamFile());
        assertEquals(TdOmsBuildIfsOmsStep.DEFAULT_COPY_TO_SOURCE_FILE, step.getCopyToSourceFile());
        assertEquals(TdOmsBuildIfsOmsStep.DEFAULT_CONNECT_OBJECT, step.getConnectObject());
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

    @Test
    void buildsCommandWithNewBldIfsOmsKeywords() {
        TdOmsBuildIfsOmsStep step = new TdOmsBuildIfsOmsStep();
        step.setTargetPath("/home/upload");
        step.setBranch("feature/OMS-1");
        step.setStreamFileLabels("Label 1, Label 2");
        step.setConnectObject("*VIRTUAL");
        step.setObjectLabels("Object's label");

        String command = TdOmsBuildIfsOmsStep.buildCommand(step, "source/example.rpgle");

        assertTrue(command.contains("LBLSTMF(('Label 1')('Label 2'))"));
        assertTrue(command.contains("CONOBJ(*VIRTUAL)"));
        assertTrue(command.contains("LBLOBJ(('Object''s label'))"));
    }

    @Test
    void omitsOptionalLabelKeywordsWhenNotConfigured() {
        TdOmsBuildIfsOmsStep step = new TdOmsBuildIfsOmsStep();
        step.setTargetPath("/home/upload");
        step.setBranch("main");

        String command = TdOmsBuildIfsOmsStep.buildCommand(step, "source/example.rpgle");

        assertTrue(command.contains("CONOBJ(*YES)"));
        assertFalse(command.contains("LBLSTMF("));
        assertFalse(command.contains("LBLOBJ("));
    }

    @Test
    void rejectsInvalidLabelLists() {
        TdOmsBuildIfsOmsStep step = new TdOmsBuildIfsOmsStep();
        step.setTargetPath("/home/upload");
        step.setBranch("main");

        step.setStreamFileLabels("one,,three");
        assertThrows(IllegalArgumentException.class,
                () -> TdOmsBuildIfsOmsStep.buildCommand(step, "source/example.rpgle"));

        step.setStreamFileLabels("one,two,three,four,five,six");
        assertThrows(IllegalArgumentException.class,
                () -> TdOmsBuildIfsOmsStep.buildCommand(step, "source/example.rpgle"));
    }

        @Test
        void commandOnlyStepsUseTheirFixedActionsAndScope() {
        TdOmsDeployStep deploy = new TdOmsDeployStep();
        deploy.setBranch("feature's");
        TdOmsReleaseBuildQStep release = new TdOmsReleaseBuildQStep();
        release.setBranch("main");

        assertEquals("BLDIFSOMS ACTC(*DEPLOY) BRANCH('feature''s') APPC(*CALC) TASK(*CALC)",
            TdOmsCommandStep.buildCommand(deploy));
        assertEquals("BLDIFSOMS ACTC(*RLSBQ) BRANCH('main') APPC(*CALC) TASK(*CALC)",
            TdOmsCommandStep.buildCommand(release));
        assertTrue(new TdOmsDeployStep.DescriptorImpl().getRequiredContext()
            .contains(org.jenkinsci.plugins.ibmisteps.model.IBMiContext.class));
        }

        @Test
        void branchIsRequiredUnlessBothApplicationAndTaskAreExplicit() {
        TdOmsDeployStep deploy = new TdOmsDeployStep();
        assertThrows(IllegalArgumentException.class, () -> TdOmsCommandStep.buildCommand(deploy));

        deploy.setApplication("APP");
        assertThrows(IllegalArgumentException.class, () -> TdOmsCommandStep.buildCommand(deploy));

        deploy.setTask("TASK");
        assertEquals("BLDIFSOMS ACTC(*DEPLOY) APPC(APP) TASK(TASK)", TdOmsCommandStep.buildCommand(deploy));

        deploy.setBranch(" ");
        assertEquals("BLDIFSOMS ACTC(*DEPLOY) APPC(APP) TASK(TASK)", TdOmsCommandStep.buildCommand(deploy));
        deploy.setTask("*calc");
        assertThrows(IllegalArgumentException.class, () -> TdOmsCommandStep.buildCommand(deploy));

        TdOmsBuildIfsOmsStep push = new TdOmsBuildIfsOmsStep();
        push.setTargetPath("/home/upload");
        assertThrows(IllegalArgumentException.class,
            () -> TdOmsBuildIfsOmsStep.buildCommand(push, "source/example.rpgle"));
        push.setApplication("APP");
        push.setTask("TASK");
        assertTrue(TdOmsBuildIfsOmsStep.buildCommand(push, "source/example.rpgle")
            .startsWith("BLDIFSOMS ACTC(*PUSH) APPC(APP) TASK(TASK) ROTC("));
        }
}
