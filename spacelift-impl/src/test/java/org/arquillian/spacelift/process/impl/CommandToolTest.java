package org.arquillian.spacelift.process.impl;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;

import java.io.UnsupportedEncodingException;

import org.apache.commons.lang3.SystemUtils;
import org.arquillian.spacelift.execution.Execution;
import org.arquillian.spacelift.execution.ExecutionException;
import org.arquillian.spacelift.execution.Tasks;
import org.arquillian.spacelift.process.CommandBuilder;
import org.arquillian.spacelift.process.ProcessResult;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class CommandToolTest {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void runJavaCommandHelp() {

        // run only on linux
        Assume.assumeThat(SystemUtils.IS_OS_LINUX, is(true));

        Tasks.prepare(CommandTool.class).programName("java").parameters("-help")
            .execute().await();
    }

    @Test
    public void runJavaCommandInvalid() {

        // run only on linux
        Assume.assumeThat(SystemUtils.IS_OS_LINUX, is(true));

        exception.expect(ExecutionException.class);

        Tasks.prepare(CommandTool.class).programName("java").parameters("-foo", "-bar")
            .execute().await();
    }

    @Test
    public void runJavaCommandInvalidExpectFailure() throws UnsupportedEncodingException {

        // run only on linux
        Assume.assumeThat(SystemUtils.IS_OS_LINUX, is(true));

        Tasks.prepare(CommandTool.class).programName("java").parameters("-foo", "-bar")
            .shouldExitWith(1)
            .execute();
    }

    @Test
    public void splitToParameters() throws Exception{
        CommandBuilder cb = Tasks.prepare(CommandTool.class).programName("java").splitToParameters("-foo -bar").commandBuilder;
        Assert.assertThat(cb.build().getNumberOfParameters(), is(2));
    }

    @Test
    public void spawningProcess() throws Exception {

        // run only on linux
        Assume.assumeThat(SystemUtils.IS_OS_LINUX, is(true));

        Execution<ProcessResult> yes = Tasks.prepare(CommandTool.class).programName("yes").parameters("spacelift")
            .shouldExitWith(143)
            .execute();

        // wait until process has started
        Thread.sleep(500);

        Assert.assertThat(yes.isFinished(), is(false));

        yes.terminate();
        Assert.assertThat(yes.isFinished(), is(true));

        ProcessResult result = yes.await();
        if (result != null) {
            Assert.assertThat(result.output().size(), is(not(0)));
        }
    }

    @Test
    public void workingDir() throws Exception {
        Tasks.prepare(CommandTool.class).programName("yes").workingDir(null);
    }
}