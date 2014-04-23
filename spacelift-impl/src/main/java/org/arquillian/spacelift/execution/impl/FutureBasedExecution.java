package org.arquillian.spacelift.execution.impl;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.arquillian.spacelift.execution.Execution;
import org.arquillian.spacelift.execution.ExecutionCondition;
import org.arquillian.spacelift.execution.ExecutionException;
import org.arquillian.spacelift.execution.ExecutionService;
import org.arquillian.spacelift.execution.TimeoutExecutionException;

class FutureBasedExecution<RESULT> implements Execution<RESULT> {

    public static final long DEFAULT_POLL_INTERVAL = 500;
    public static final TimeUnit DEFAULT_POLL_TIME_UNIT = TimeUnit.MILLISECONDS;

    private final Callable<RESULT> executionTask;
    private final Future<RESULT> executionFuture;
    private final ExecutionService service;

    private long pollInterval;
    private TimeUnit pollUnit;

    private boolean shouldBeFinished;

    public FutureBasedExecution(ExecutionService service, Callable<RESULT> task, Future<RESULT> future) {
        this.service = service;
        this.executionTask = task;
        this.executionFuture = future;
        this.pollInterval = DEFAULT_POLL_INTERVAL;
        this.pollUnit = DEFAULT_POLL_TIME_UNIT;
    }

    @Override
    public Execution<RESULT> markAsFinished() {
        this.shouldBeFinished = true;
        return this;
    }

    @Override
    public Execution<RESULT> registerShutdownHook() {
        ShutdownHooks.addHookFor(this);
        return this;
    }

    @Override
    public boolean isMarkedAsFinished() {
        return shouldBeFinished;
    }

    @Override
    public boolean isFinished() {
        return isMarkedAsFinished() || executionFuture.isDone();
    }

    @Override
    public boolean hasFailed() {
        return executionFuture.isCancelled();
    }

    @Override
    public Execution<RESULT> terminate() {
        executionFuture.cancel(true);
        return this;
    }

    @Override
    public RESULT await() throws ExecutionException {
        try {
            return executionFuture.get();
        } catch (InterruptedException e) {
            throw unwrapException(e, "Interrupted while executing a task");
        } catch (java.util.concurrent.ExecutionException e) {
            throw unwrapException(e, "Execution of a task failed");
        }
    }

    @Override
    public RESULT awaitAtMost(long timeout, TimeUnit unit) {
        try {
            return executionFuture.get(timeout, unit);
        } catch (InterruptedException e) {
            throw unwrapException(e, "Interrupted while executing a task");
        } catch (java.util.concurrent.ExecutionException e) {
            throw unwrapException(e, "Execution of a task failed");
        } catch (TimeoutException e) {
            throw unwrapExceptionAsTimeoutException(e, "Timed out after {0}{1} while executing a task", timeout, unit);
        }
    }

    @Override
    public Execution<RESULT> pollEvery(long step, TimeUnit unit) {
        this.pollInterval = step;
        this.pollUnit = unit;
        return this;
    }

    @Override
    public RESULT until(long timeout, TimeUnit unit, ExecutionCondition<RESULT> condition) throws ExecutionException,
        TimeoutExecutionException {

        CountDownWatch countdown = new CountDownWatch(timeout, unit);
        Execution<RESULT> currentExecution = new FutureBasedExecution<RESULT>(service, executionTask, executionFuture);

        // keep scheduling task until we have some time
        while (countdown.timeLeft() > 0) {

            Execution<RESULT> nextExecution = service.schedule(executionTask, pollInterval, pollUnit);

            try {
                RESULT result = currentExecution.awaitAtMost(countdown.timeLeft(), countdown.getTimeUnit());
                if (condition.satisfiedBy(result)) {
                    // terminate execution of next callable
                    // we want to ignore failures in termination
                    try {
                        nextExecution.terminate();
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    }
                    return result;
                }
            } catch (TimeoutExecutionException e) {
                continue;
            }

            // continue evaluating scheduled execution
            currentExecution = nextExecution;
        }

        throw new TimeoutExecutionException("Unable to trigger condition within {0} {1}.", timeout, unit.toString()
            .toLowerCase());

    }

    private static ExecutionException unwrapException(Throwable cause, String messageFormat, Object... parameters) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof ExecutionException) {
                return ((ExecutionException) current).prependMessage(messageFormat, parameters);
            }
            current = current.getCause();
        }

        return new ExecutionException(cause, messageFormat, parameters);
    }

    private static TimeoutExecutionException unwrapExceptionAsTimeoutException(Throwable cause, String messageFormat,
        Object... parameters) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof ExecutionException) {
                return new TimeoutExecutionException(current, messageFormat, parameters);
            }
            current = current.getCause();
        }

        return new TimeoutExecutionException(cause, messageFormat, parameters);
    }
}