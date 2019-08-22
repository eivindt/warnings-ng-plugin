package io.jenkins.plugins.analysis.core.util;

import java.io.IOException;
import java.util.Collections;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import com.gargoylesoftware.htmlunit.html.HtmlPage;

import hudson.FilePath;
import hudson.model.FreeStyleProject;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.model.labels.LabelAtom;
import hudson.slaves.DumbSlave;
import hudson.slaves.RetentionStrategy;

import io.jenkins.plugins.analysis.core.model.AnalysisResult;
import io.jenkins.plugins.analysis.core.model.FileNameRenderer;
import io.jenkins.plugins.analysis.core.testutil.IntegrationTestWithJenkinsPerSuite;
import io.jenkins.plugins.analysis.warnings.Java;
import io.jenkins.plugins.analysis.warnings.recorder.pageobj.SourceCodeView;

import static io.jenkins.plugins.analysis.core.assertions.Assertions.*;
import static org.assertj.core.api.Assumptions.*;

/**
 * Integration tests for the class {@link AbsolutePathGenerator}.
 *
 * @author Ullrich Hafner
 */
public class AbsolutePathGeneratorITest extends IntegrationTestWithJenkinsPerSuite {
    private static final String SOURCE_CODE = "public class Test {}";

    /**
     * Verifies that the affected files will be copied even if the file name uses the wrong case (Windows only).
     */
    @Test
    @Issue("JENKINS-58824")
    public void shouldMapIssueToAffectedFileIfPathIsInWrongCase() throws IOException {
        assumeThat(isWindows()).as("Running on Windows").isTrue();

        Slave agent = getAgent();

        FreeStyleProject project = createFreeStyleProject();
        project.setAssignedNode(agent);

        FilePath folder = createFolder(agent, project);
        createFileInAgentWorkspace(agent, project, "Folder/Test.java", SOURCE_CODE);
        createFileInAgentWorkspace(agent, project, "warnings.txt", "[javac] " + getAbsolutePathInLowerCase(folder)
                + ":1: warning: Test Warning for Jenkins");

        Java javaJob = new Java();
        javaJob.setPattern("warnings.txt");
        enableWarnings(project, javaJob);

        AnalysisResult result = scheduleSuccessfulBuild(project);
        assertThat(result).hasTotalSize(1);

        assertThat(result).hasInfoMessages("-> 1 resolved, 0 unresolved, 0 already resolved");
        assertThat(result).doesNotHaveInfoMessages("-> 0 copied, 1 not in workspace, 0 not-found, 0 with I/O error");

        SourceCodeView view = new SourceCodeView(getSourceCodePage(result));
        assertThat(view.getSourceCode()).isEqualTo(SOURCE_CODE);
    }

    private Slave getAgent() {
        try {
            JenkinsRule jenkinsRule = getJenkins();
            Label l = new LabelAtom("agent");
            DumbSlave result;
            String labels = l == null ? null : l.getExpression();
            synchronized (jenkinsRule.jenkins) {
                int sz = jenkinsRule.jenkins.getNodes().size();
                DumbSlave result1;
                synchronized (jenkinsRule.jenkins) {
                    DumbSlave slave = new DumbSlave("slave" + sz, "dummy",
                            jenkinsRule.createTmpDir().getPath(), "1", Node.Mode.NORMAL, labels ==null?"": labels, jenkinsRule
                            .createComputerLauncher(null), RetentionStrategy.NOOP, Collections.EMPTY_LIST);
                    jenkinsRule.jenkins.addNode(slave);
                    result1 = slave;
                }
                result = result1;
            }
            DumbSlave s = result;
            jenkinsRule.waitOnline(s);
            return s;
        }
        catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private FilePath createFolder(final Slave agent, final FreeStyleProject project) {
        try {
            FilePath folder = getAgentWorkspace(agent, project).child("Folder");
            folder.mkdirs();
            return folder;
        }
        catch (IOException | InterruptedException exception) {
            throw new AssertionError(exception);
        }
    }

    private String getAbsolutePathInLowerCase(final FilePath folder) {
        return StringUtils.lowerCase(folder.getRemote() + "\\Test.java");
    }

    private HtmlPage getSourceCodePage(final AnalysisResult result) {
        return getWebPage(JavaScriptSupport.JS_DISABLED, result,
                new FileNameRenderer(result.getOwner()).getSourceCodeUrl(result.getIssues().get(0))
        );
    }
}
