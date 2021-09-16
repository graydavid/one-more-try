package io.github.graydavid.onemoretry;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import io.github.graydavid.onemoretry.Try.CheckedExceptionWrapper;

public class TryTest {
    // Suppress justify: Mockito can't create generic mocks in a typesafe way, but the mock is used that way
    @SuppressWarnings("unchecked")
    private final Function<Throwable, Integer> recovery = mock(Function.class);
    // Suppress justify: Mockito can't create generic mocks in a typesafe way, but the mock is used that way
    @SuppressWarnings("unchecked")
    private final Consumer<Throwable> handler = mock(Consumer.class);
    // Suppress justify: Mockito can't create generic mocks in a typesafe way, but the mock is used that way
    @SuppressWarnings("unchecked")
    private final BiFunction<Integer, Throwable, String> converter = mock(BiFunction.class);
    // Suppress justify: Mockito can't create generic mocks in a typesafe way, but the mock is used that way
    @SuppressWarnings("unchecked")
    private final BiConsumer<Integer, Throwable> consumer = mock(BiConsumer.class);

    @Test
    public void ofSuccessAllowsNonNullSuccesses() {
        Try<Integer> result = Try.ofSuccess(5);

        assertThat(result.getSuccess().get(), is(5));
        assertThat(result.getFailure(), is(Optional.empty()));
        assertThat(result.getNullableSuccess(), is(5));
        assertNull(result.getNullableFailure());
        assertTrue(result.isSuccess());
        assertFalse(result.isFailure());
    }

    @Test
    public void ofSuccessAllowsNullSuccesses() {
        Try<Integer> result = Try.ofSuccess(null);

        assertThat(result.getSuccess(), is(Optional.empty()));
        assertThat(result.getFailure(), is(Optional.empty()));
        assertNull(result.getNullableSuccess());
        assertNull(result.getNullableFailure());
        assertTrue(result.isSuccess());
        assertFalse(result.isFailure());
    }

    @Test
    public void ofFailureSwallowingInterruptAllowsNonNullThrowables() {
        Throwable throwable = new Throwable();

        Try<Integer> result = Try.ofFailureSwallowingInterrupt(throwable);

        assertThat(result.getSuccess(), is(Optional.empty()));
        assertThat(result.getFailure().get(), is(throwable));
        assertNull(result.getNullableSuccess());
        assertThat(result.getNullableFailure(), is(throwable));
        assertFalse(result.isSuccess());
        assertTrue(result.isFailure());
    }

    @Test
    public void ofFailureSwallowingInterruptThrowsExceptionGivenNullThrowables() {
        assertThrows(NullPointerException.class, () -> Try.ofFailureSwallowingInterrupt(null));
    }

    @Test
    public void ofFailureSwallowingInterruptDoesntSetInterruptedFlagForInterruptedExceptions() {
        InterruptedException interruptedException = new InterruptedException();

        Try.ofFailureSwallowingInterrupt(interruptedException);

        assertFalse(Thread.interrupted(), "Expected Thread interrupted flag not to be set");
    }

    @Test
    public void ofFailurePreservingInterruptSetsInterruptedFlagForInterruptedExceptions() {
        InterruptedException interruptedException = new InterruptedException();

        Try.ofFailurePreservingInterrupt(interruptedException);

        assertTrue(Thread.interrupted(), "Expected Thread interrupted flag to be set");
    }

    @Test
    public void ofSwallowingInterruptCreatesSuccessForNonNullSuccessAndNullFailure() {
        Try<Integer> result = Try.ofSwallowingInterrupt(5, null);

        assertThat(result.getSuccess(), is(Optional.of(5)));
        assertThat(result.getFailure(), is(Optional.empty()));
        assertTrue(result.isSuccess());
    }

    @Test
    public void ofSwallowingInterruptCreatesSuccessForNullSuccessAndFailure() {
        Try<Integer> result = Try.ofSwallowingInterrupt(null, null);

        assertThat(result.getSuccess(), is(Optional.empty()));
        assertThat(result.getFailure(), is(Optional.empty()));
        assertTrue(result.isSuccess());
    }

    @Test
    public void ofSwallowingInterruptCreatesFailureForNullSuccessAndNonNullFailure() {
        Throwable throwable = new Throwable();

        Try<Integer> result = Try.ofSwallowingInterrupt(null, throwable);

        assertThat(result.getSuccess(), is(Optional.empty()));
        assertThat(result.getFailure(), is(Optional.of(throwable)));
        assertTrue(result.isFailure());
    }

    @Test
    public void ofSwallowingInterruptThrowsExceptionForNonNullSuccessAndFailure() {
        assertThrows(IllegalArgumentException.class, () -> Try.ofSwallowingInterrupt(5, new Throwable()));
    }

    @Test
    public void ofSwallowingInterruptDoesntSetInterruptedFlagForInterruptedExceptions() {
        InterruptedException interruptedException = new InterruptedException();

        Try.ofSwallowingInterrupt(null, interruptedException);

        assertFalse(Thread.interrupted(), "Expected Thread interrupted flag not to be set");
    }

    @Test
    public void ofPreservingInterruptSetsInterruptedFlagForInterruptedExceptions() {
        InterruptedException interruptedException = new InterruptedException();

        Try.ofPreservingInterrupt(null, interruptedException);

        assertTrue(Thread.interrupted(), "Expected Thread interrupted flag to be set");
    }

    @Test
    public void callCatchRuntimeReturnsValueOnSuccess() {
        assertTryProductionReturnsValueOnSuccess(integer -> Try.callCatchRuntime(() -> integer));
    }

    private static void assertTryProductionReturnsValueOnSuccess(Function<Integer, Try<Integer>> tryProducer) {
        Try<Integer> result = tryProducer.apply(5);

        assertThat(result.getSuccess().get(), is(5));
        assertThat(result.getFailure(), is(Optional.empty()));
    }

    @Test
    public void callCatchRuntimeCatchesRuntimeExceptions() {
        assertTryProductionCatchesRuntimeExceptions(e -> Try.callCatchRuntime(() -> {
            throw e;
        }));
    }

    private static void assertTryProductionCatchesRuntimeExceptions(Function<RuntimeException, Try<?>> tryProducer) {
        RuntimeException runtimeException = new RuntimeException();

        Try<?> result = tryProducer.apply(runtimeException);

        assertThat(result.getSuccess(), is(Optional.empty()));
        assertThat(result.getFailure().get(), sameInstance(runtimeException));
    }

    @Test
    public void callCatchRuntimeDoesntCatchSneakyExceptions() {
        assertTryProductionDoesntCatchExceptions(e -> Try.callCatchRuntime(() -> {
            sneakyThrow(e);
            return null;
        }));
    }

    // DO NOT FOLLOW THIS PATTERN. The casting below is known to be false, and the suppression is unjustified. This
    // falsity is needed to simulate what some libraries do to bypass checked exceptions being declared in signatures.
    // We're suppressing the warning, because this is only used in tests to help simulate that behavior.
    @SuppressWarnings("unchecked")
    private static <T, U extends Throwable> T sneakyThrow(Throwable throwable) throws U {
        throw (U) throwable;
    }

    private static void assertTryProductionDoesntCatchExceptions(Function<Exception, Try<?>> tryProducer) {
        Exception exception = new Exception();

        Exception thrown = assertThrows(Exception.class, () -> tryProducer.apply(exception));

        assertThat(thrown, sameInstance(exception));
    }

    @Test
    public void callCatchRuntimeDoesntCatchErrors() {
        assertTryProductionDoesntCatchErrors(e -> Try.callCatchRuntime(() -> {
            throw e;
        }));
    }

    private static void assertTryProductionDoesntCatchErrors(Function<Error, Try<?>> tryProducer) {
        Error error = new Error();

        Error thrown = assertThrows(Error.class, () -> tryProducer.apply(error));

        assertThat(thrown, sameInstance(error));
    }

    @Test
    public void callCatchRuntimeDoesntCatchSneakyThrowables() {
        assertTryProductionDoesntCatchThrowables(e -> Try.callCatchRuntime(() -> {
            sneakyThrow(e);
            return null;
        }));
    }

    private static void assertTryProductionDoesntCatchThrowables(Function<Throwable, Try<?>> tryProducer) {
        Throwable throwable = new Throwable();

        Throwable thrown = assertThrows(Throwable.class, () -> tryProducer.apply(throwable));

        assertThat(thrown, sameInstance(throwable));
    }

    @Test
    public void runCatchRuntimeJustRunsRunnableOnSuccess() {
        Runnable runnable = mock(Runnable.class);

        Try.runCatchRuntime(runnable::run);

        verify(runnable).run();
    }

    @Test
    public void runCatchRuntimeCatchesRuntimeExceptions() {
        assertTryProductionCatchesRuntimeExceptions(e -> Try.runCatchRuntime(() -> {
            throw e;
        }));
    }

    @Test
    public void runCatchRuntimeDoesntCatchSneakyExceptions() {
        assertTryProductionDoesntCatchExceptions(e -> Try.runCatchRuntime(() -> {
            sneakyThrow(e);
        }));
    }

    @Test
    public void callCatchExceptionReturnsValueOnSuccess() {
        assertTryProductionReturnsValueOnSuccess(integer -> Try.callCatchException(() -> integer));
    }

    @Test
    public void callCatchExceptionCatchesRuntimeExceptions() {
        assertTryProductionCatchesRuntimeExceptions(e -> Try.callCatchException(() -> {
            throw e;
        }));
    }

    @Test
    public void callCatchExceptionCatchesExceptions() {
        assertTryProductionCatchesExceptions(e -> Try.callCatchException(() -> {
            throw e;
        }));
    }

    private static void assertTryProductionCatchesExceptions(Function<Exception, Try<?>> tryProducer) {
        Exception exception = new Exception();

        Try<?> result = tryProducer.apply(exception);

        assertThat(result.getSuccess(), is(Optional.empty()));
        assertThat(result.getFailure().get(), sameInstance(exception));
    }

    @Test
    public void callCatchExceptionDoesntCatchErrors() {
        assertTryProductionDoesntCatchErrors(e -> Try.callCatchException(() -> {
            throw e;
        }));
    }

    @Test
    public void callCatchExceptionDoesntCatchSneakyThrowables() {
        assertTryProductionDoesntCatchThrowables(e -> Try.callCatchException(() -> {
            sneakyThrow(e);
            return null;
        }));
    }

    @Test
    public void callCatchExceptionSetsInterruptedFlagForInterruptedExceptions() {
        assertTryProducerSetsInterruptedFlagForInterruptedExceptions(e -> Try.callCatchException(() -> {
            throw e;
        }));
    }

    private static void assertTryProducerSetsInterruptedFlagForInterruptedExceptions(
            Function<InterruptedException, Try<?>> tryProducer) {
        InterruptedException interruptedException = new InterruptedException();

        Try<?> result = tryProducer.apply(interruptedException);

        assertTrue(Thread.interrupted(), "Expected Thread interrupted flag to be set");
        assertThat(result.getSuccess(), is(Optional.empty()));
        assertThat(result.getFailure().get(), sameInstance(interruptedException));
    }

    @Test
    public void callCatchExceptionDoesntSetInterruptedFlagForNonInterruptedExceptions() {
        assertTryProducerDoesntSetInterruptedFlagForNonInterruptedExceptions(e -> Try.callCatchException(() -> {
            throw e;
        }));
    }

    private static void assertTryProducerDoesntSetInterruptedFlagForNonInterruptedExceptions(
            Function<Exception, Try<?>> tryProducer) {
        Exception exception = new Exception();

        Try<Void> result = Try.callCatchException(() -> {
            throw exception;
        });

        assertFalse(Thread.interrupted(), "Expected Thread interrupted flag not to be set");
        assertThat(result.getSuccess(), is(Optional.empty()));
        assertThat(result.getFailure().get(), sameInstance(exception));
    }

    @Test
    public void runCatchExceptionJustRunsRunnableOnSuccess() {
        Runnable runnable = mock(Runnable.class);

        Try.runCatchException(runnable::run);

        verify(runnable).run();
    }

    @Test
    public void runCatchExceptionCatchesExceptions() {
        assertTryProductionCatchesExceptions(e -> Try.runCatchException(() -> {
            throw e;
        }));
    }

    @Test
    public void runCatchExceptionDoesntCatchSneakyThrowables() {
        assertTryProductionDoesntCatchThrowables(e -> Try.runCatchException(() -> {
            sneakyThrow(e);
        }));
    }

    @Test
    public void callCatchThrowableReturnsValueOnSuccess() {
        assertTryProductionReturnsValueOnSuccess(integer -> Try.callCatchThrowable(() -> integer));
    }

    @Test
    public void callCatchThrowableCatchesRuntimeExceptions() {
        assertTryProductionCatchesRuntimeExceptions(e -> Try.callCatchThrowable(() -> {
            throw e;
        }));
    }

    @Test
    public void callCatchThrowableCatchesExceptions() {
        assertTryProductionCatchesExceptions(e -> Try.callCatchThrowable(() -> {
            throw e;
        }));
    }

    @Test
    public void callCatchThrowableCatchesErrors() {
        Error error = new Error();

        Try<?> result = Try.callCatchThrowable(() -> {
            throw error;
        });

        assertThat(result.getSuccess(), is(Optional.empty()));
        assertThat(result.getFailure().get(), sameInstance(error));
    }

    @Test
    public void callCatchThrowableCatchesThrowables() {
        assertTryProductionCatchesThrowable(e -> Try.callCatchThrowable(() -> {
            throw e;
        }));
    }

    private static void assertTryProductionCatchesThrowable(Function<Throwable, Try<?>> tryProducer) {
        Throwable throwable = new Throwable();

        Try<?> result = tryProducer.apply(throwable);

        assertThat(result.getSuccess(), is(Optional.empty()));
        assertThat(result.getFailure().get(), sameInstance(throwable));
    }

    @Test
    public void callCatchThrowableSetsInterruptedFlagForInterruptedExceptions() {
        assertTryProducerSetsInterruptedFlagForInterruptedExceptions(e -> Try.callCatchThrowable(() -> {
            throw e;
        }));
    }

    @Test
    public void callCatchThrowableDoesntSetInterruptedFlagForNonInterruptedExceptions() {
        assertTryProducerDoesntSetInterruptedFlagForNonInterruptedExceptions(e -> Try.callCatchThrowable(() -> {
            throw e;
        }));
    }

    @Test
    public void runCatchThrowableCatchesThrowables() {
        assertTryProductionCatchesThrowable(e -> Try.runCatchThrowable(() -> {
            throw e;
        }));
    }

    @Test
    public void runUncheckedJustRunsRunnableOnSuccess() {
        Runnable runnable = mock(Runnable.class);

        Try.runUnchecked(runnable::run);

        verify(runnable).run();
    }

    @Test
    public void runUncheckedThrowsRuntimeExceptionsAsIs() {
        assertRunnerThrowsRuntimeExceptionsAsIs(e -> {
            Try.runUnchecked(() -> {
                throw e;
            });
            return null;
        });
    }

    private static void assertRunnerThrowsRuntimeExceptionsAsIs(Function<RuntimeException, ?> runner) {
        RuntimeException runtimeException = new RuntimeException();

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> runner.apply(runtimeException));

        assertThat(thrown, sameInstance(runtimeException));
    }

    @Test
    public void runUncheckedThrowsCheckedExceptionsInWrapper() {
        assertRunnerThrowsCheckedExceptionsInWrapper(e -> {
            Try.runUnchecked(() -> {
                throw e;
            });
            return null;
        });
    }

    private static void assertRunnerThrowsCheckedExceptionsInWrapper(Function<Throwable, ?> runner) {
        Throwable throwable = new Throwable();

        CheckedExceptionWrapper thrown = assertThrows(CheckedExceptionWrapper.class, () -> runner.apply(throwable));

        assertThat(thrown.getCause(), sameInstance(throwable));
    }

    @Test
    public void runUncheckedPropagatesErrors() {
        assertRunnerPropagatesErrors(e -> {
            Try.runUnchecked(() -> {
                throw e;
            });
            return null;
        });
    }

    private static void assertRunnerPropagatesErrors(Function<Error, ?> runner) {
        Error error = new Error();

        Error thrown = assertThrows(Error.class, () -> runner.apply(error));

        assertThat(thrown, sameInstance(error));
    }

    @Test
    public void uncheckedRunnableDoesntRunRunnableDirectly() {
        Try.uncheckedRunnable(() -> {
            fail("This should never be run");
        });
    }

    @Test
    public void uncheckedRunnableResponseJustRunsRunnableOnSuccess() {
        Runnable runnable = mock(Runnable.class);

        Try.uncheckedRunnable(runnable::run).run();

        verify(runnable).run();
    }

    @Test
    public void uncheckedRunnableResponseThrowsRuntimeExceptionsAsIs() {
        assertRunnerThrowsRuntimeExceptionsAsIs(e -> {
            Try.uncheckedRunnable(() -> {
                throw e;
            }).run();
            return null;
        });
    }

    @Test
    public void uncheckedRunnableResponseThrowsCheckedExceptionsInWrapper() {
        assertRunnerThrowsCheckedExceptionsInWrapper(e -> {
            Try.uncheckedRunnable(() -> {
                throw e;
            }).run();
            return null;
        });
    }

    @Test
    public void uncheckedRunnableResponsePropagatesErrors() {
        assertRunnerPropagatesErrors(e -> {
            Try.uncheckedRunnable(() -> {
                throw e;
            }).run();
            return null;
        });
    }

    @Test
    public void callUncheckedReturnsValueOnSuccess() {
        Integer result = Try.callUnchecked(() -> 5);

        assertThat(result, is(5));
    }

    @Test
    public void callUncheckedThrowsRuntimeExceptionsAsIs() {
        assertRunnerThrowsRuntimeExceptionsAsIs(e -> Try.callUnchecked(() -> {
            throw e;
        }));
    }

    @Test
    public void callUncheckedThrowsCheckedExceptionsInWrapper() {
        assertRunnerThrowsCheckedExceptionsInWrapper(e -> Try.callUnchecked(() -> {
            throw e;
        }));
    }

    @Test
    public void callUncheckedPropagatesErrors() {
        assertRunnerPropagatesErrors(e -> Try.callUnchecked(() -> {
            throw e;
        }));
    }

    @Test
    public void uncheckedSupplierDoesntCallCallableDirectly() {
        Try.uncheckedSupplier(() -> {
            fail("This should never be run");
            return null;
        });
    }

    @Test
    public void uncheckedSupplierResponseReturnsValueOnSuccess() {
        Supplier<Integer> result = Try.uncheckedSupplier(() -> 5);

        assertThat(result.get(), is(5));
    }

    @Test
    public void uncheckedSupplierResponseThrowsRuntimeExceptionsAsIs() {
        assertRunnerThrowsRuntimeExceptionsAsIs(e -> Try.uncheckedSupplier(() -> {
            throw e;
        }).get());
    }

    @Test
    public void uncheckedSupplierResponseThrowsCheckedExceptionsInWrapper() {
        assertRunnerThrowsCheckedExceptionsInWrapper(e -> Try.uncheckedSupplier(() -> {
            throw e;
        }).get());
    }

    @Test
    public void uncheckedSupplierResponsePropagatesErrors() {
        assertRunnerPropagatesErrors(e -> Try.uncheckedSupplier(() -> {
            throw e;
        }).get());
    }

    @Test
    public void getOrThrowUncheckedReturnsNonNullSuccesses() throws Throwable {
        assertTryGetterReturnsNonNullSuccesses(tr -> tr.getOrThrowUnchecked());
    }

    private static void assertTryGetterReturnsNonNullSuccesses(ThrowingFunction<Try<Integer>, Integer> tryGetter)
            throws Throwable {
        Try<Integer> result = Try.ofSuccess(5);

        assertThat(tryGetter.apply(result), is(5));
    }

    @FunctionalInterface
    private interface ThrowingFunction<T, R> {
        R apply(T t) throws Throwable;
    }

    @Test
    public void getOrThrowUncheckedReturnsNullSuccesses() throws Throwable {
        assertTryGetterReturnsNullSuccesses(tr -> tr.getOrThrowUnchecked());
    }

    private static void assertTryGetterReturnsNullSuccesses(ThrowingFunction<Try<Integer>, Integer> tryGetter)
            throws Throwable {
        Try<Integer> result = Try.ofSuccess(null);

        assertNull(tryGetter.apply(result));
    }

    @Test
    public void getOrThrowUncheckedThrowsRuntimeExceptionsAsIs() {
        assertTryGetterThrowsThrowableAsIs(tr -> tr.getOrThrowUnchecked(), new RuntimeException());
    }

    private static void assertTryGetterThrowsThrowableAsIs(ThrowingFunction<Try<Integer>, Integer> tryGetter,
            Throwable throwable) {
        Try<Integer> result = Try.ofFailureSwallowingInterrupt(throwable);

        Throwable thrown = assertThrows(Throwable.class, () -> tryGetter.apply(result));

        assertThat(thrown, sameInstance(throwable));
    }

    @Test
    public void getOrThrowUncheckedThrowsErrorsAsIs() {
        assertTryGetterThrowsThrowableAsIs(tr -> tr.getOrThrowUnchecked(), new Error());
    }

    @Test
    public void getOrThrowUncheckedWrapsCheckedExceptionsInWrapper() {
        assertTryGetterWrapsThrowable(tr -> tr.getOrThrowUnchecked(), new Throwable());
    }

    private static void assertTryGetterWrapsThrowable(ThrowingFunction<Try<Integer>, Integer> tryGetter,
            Throwable throwable) {
        Try<Integer> result = Try.ofFailureSwallowingInterrupt(throwable);

        CheckedExceptionWrapper thrown = assertThrows(CheckedExceptionWrapper.class, () -> tryGetter.apply(result));

        assertThat(thrown.getCause(), sameInstance(throwable));
    }

    @Test
    public void getOrThrowUncheckedDoesntResetInterruptFlagForInterruptedException() throws Throwable {
        InterruptedException interruptedException = new InterruptedException();
        Thread.currentThread().interrupt();
        Try<Integer> result = Try.ofFailureSwallowingInterrupt(interruptedException);

        CheckedExceptionWrapper thrown = assertThrows(CheckedExceptionWrapper.class,
                () -> result.getOrThrowUnchecked());

        assertThat(thrown.getCause(), sameInstance(interruptedException));
        assertTrue(Thread.interrupted(), "Expected Thread interrupted flag to be set");
    }

    @Test
    public void getOrThrowUncheckedWithCheckedTransformerWrapsCheckedExceptionsInRuntimeExceptionWrapper() {
        Throwable checked = new Throwable();
        RuntimeException transformed = new RuntimeException();
        Function<Throwable, Throwable> checkedTransformer = mockTransformer();
        when(checkedTransformer.apply(checked)).thenReturn(transformed);
        Try<Integer> result = Try.ofFailureSwallowingInterrupt(checked);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> result.getOrThrowUnchecked(checkedTransformer));

        assertThat(thrown, sameInstance(transformed));
    }

    // Suppress justification: returned mocks only ever used in compatible way for declared type.
    @SuppressWarnings("unchecked")
    private static Function<Throwable, Throwable> mockTransformer() {
        return mock(Function.class);
    }

    @Test
    public void getOrThrowUncheckedWithCheckedTransformerWrapsCheckedExceptionsInErrorWrapper() {
        Throwable checked = new Throwable();
        Error transformed = new Error();
        Function<Throwable, Throwable> checkedTransformer = mockTransformer();
        when(checkedTransformer.apply(checked)).thenReturn(transformed);
        Try<Integer> result = Try.ofFailureSwallowingInterrupt(checked);

        Error thrown = assertThrows(Error.class, () -> result.getOrThrowUnchecked(checkedTransformer));

        assertThat(thrown, sameInstance(transformed));
    }

    @Test
    public void getOrThrowUncheckedWithCheckedTransformerThrowsIllegalArgumentExceptionIfTransformedExceptionIsChecked() {
        Throwable checked = new Throwable();
        Exception transformed = new Exception();
        Function<Throwable, Throwable> checkedTransformer = mockTransformer();
        when(checkedTransformer.apply(checked)).thenReturn(transformed);
        Try<Integer> result = Try.ofFailureSwallowingInterrupt(checked);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> result.getOrThrowUnchecked(checkedTransformer));

        assertThat(thrown.getCause(), sameInstance(transformed));
    }

    @Test
    public void getOrThrowUncheckedWithCheckedTransformerPropagatesTransformerExceptions() {
        Throwable checked = new Throwable();
        RuntimeException transformerException = new RuntimeException();
        Function<Throwable, Throwable> checkedTransformer = mockTransformer();
        when(checkedTransformer.apply(checked)).thenThrow(transformerException);
        Try<Integer> result = Try.ofFailureSwallowingInterrupt(checked);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> result.getOrThrowUnchecked(checkedTransformer));

        assertThat(thrown, sameInstance(transformerException));
    }

    @Test
    public void getOrThrowExceptionReturnsNonNullSuccesses() throws Throwable {
        assertTryGetterReturnsNonNullSuccesses(tr -> tr.getOrThrowException());
    }

    @Test
    public void getOrThrowExceptionReturnsNullSuccesses() throws Throwable {
        assertTryGetterReturnsNullSuccesses(tr -> tr.getOrThrowException());
    }

    @Test
    public void getOrThrowExceptionThrowsExceptionsAsIs() {
        assertTryGetterThrowsThrowableAsIs(tr -> tr.getOrThrowException(), new Exception());
    }

    @Test
    public void getOrThrowExceptionThrowsErrorsAsIs() {
        assertTryGetterThrowsThrowableAsIs(tr -> tr.getOrThrowException(), new Error());
    }

    @Test
    public void getOrThrowUncheckedWrapsThrowablesInWrapper() {
        assertTryGetterWrapsThrowable(tr -> tr.getOrThrowException(), new Throwable());
    }

    @Test
    public void getOrThrowExceptionResetsInterruptFlagForInterruptedException() {
        assertTryGetterResetsInterruptFlagForInterruptedException(tr -> tr.getOrThrowException());
    }

    private static void assertTryGetterResetsInterruptFlagForInterruptedException(
            ThrowingFunction<Try<Integer>, Integer> tryGetter) {
        InterruptedException interruptedException = new InterruptedException();
        Thread.currentThread().interrupt();
        Try<Integer> result = Try.ofFailureSwallowingInterrupt(interruptedException);

        InterruptedException thrown = assertThrows(InterruptedException.class, () -> tryGetter.apply(result));

        assertThat(thrown, sameInstance(interruptedException));
        assertFalse(Thread.interrupted(), "Expected Thread interrupted flag not to be set");
    }

    @Test
    public void getOrThrowExceptionDoesntResetInterruptFlagForNonInterruptedException() {
        assertTryGetterDoesntResetInterruptFlagForNonInterruptedException(tr -> tr.getOrThrowException());
    }

    private static void assertTryGetterDoesntResetInterruptFlagForNonInterruptedException(
            ThrowingFunction<Try<Integer>, Integer> tryGetter) {
        Exception exception = new Exception();
        Thread.currentThread().interrupt();
        Try<Integer> result = Try.ofFailureSwallowingInterrupt(exception);

        Exception thrown = assertThrows(Exception.class, () -> tryGetter.apply(result));

        assertThat(thrown, sameInstance(exception));
        assertTrue(Thread.interrupted(), "Expected Thread interrupted flag to be set");
    }

    @Test
    public void getOrThrowExceptionWithThrowableTransformerWrapsThrowablesInRuntimeExceptionWrapper() {
        Throwable checked = new Throwable();
        RuntimeException transformed = new RuntimeException();
        Function<Throwable, Throwable> throwableTransformer = mockTransformer();
        when(throwableTransformer.apply(checked)).thenReturn(transformed);
        Try<Integer> result = Try.ofFailureSwallowingInterrupt(checked);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> result.getOrThrowException(throwableTransformer));

        assertThat(thrown, sameInstance(transformed));
    }

    @Test
    public void getOrThrowExceptionWithThrowableTransformerWrapsThrowablesInErrorWrapper() {
        Throwable checked = new Throwable();
        Error transformed = new Error();
        Function<Throwable, Throwable> throwableTransformer = mockTransformer();
        when(throwableTransformer.apply(checked)).thenReturn(transformed);
        Try<Integer> result = Try.ofFailureSwallowingInterrupt(checked);

        Error thrown = assertThrows(Error.class, () -> result.getOrThrowException(throwableTransformer));

        assertThat(thrown, sameInstance(transformed));
    }

    @Test
    public void getOrThrowExceptionWithThrowableTransformerWrapsThrowablesInExceptionWrapper() {
        Throwable checked = new Throwable();
        Exception transformed = new Exception();
        Function<Throwable, Throwable> throwableTransformer = mockTransformer();
        when(throwableTransformer.apply(checked)).thenReturn(transformed);
        Try<Integer> result = Try.ofFailureSwallowingInterrupt(checked);

        Exception thrown = assertThrows(Exception.class, () -> result.getOrThrowException(throwableTransformer));

        assertThat(thrown, sameInstance(transformed));
    }

    @Test
    public void getOrThrowExceptionWithThrowableTransformerThrowsIllegalArgumentExceptionIfTransformedExceptionIsThrowable() {
        Throwable checked = new Throwable();
        Throwable transformed = new Throwable();
        Function<Throwable, Throwable> checkedTransformer = mockTransformer();
        when(checkedTransformer.apply(checked)).thenReturn(transformed);
        Try<Integer> result = Try.ofFailureSwallowingInterrupt(checked);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> result.getOrThrowException(checkedTransformer));

        assertThat(thrown.getCause(), sameInstance(transformed));
    }

    @Test
    public void getOrThrowExceptionWithThrowableTransformerPropagatesTransformerExceptions() {
        Throwable checked = new Throwable();
        RuntimeException transformerException = new RuntimeException();
        Function<Throwable, Throwable> checkedTransformer = mockTransformer();
        when(checkedTransformer.apply(checked)).thenThrow(transformerException);
        Try<Integer> result = Try.ofFailureSwallowingInterrupt(checked);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> result.getOrThrowException(checkedTransformer));

        assertThat(thrown, sameInstance(transformerException));
    }

    @Test
    public void getOrThrowThrowableReturnsNonNullSuccesses() throws Throwable {
        assertTryGetterReturnsNonNullSuccesses(tr -> tr.getOrThrowThrowable());
    }

    @Test
    public void getOrThrowThrowableReturnsNullSuccesses() throws Throwable {
        assertTryGetterReturnsNullSuccesses(tr -> tr.getOrThrowThrowable());
    }

    @Test
    public void getOrThrowThrowableThrowsThrowablesAsIs() {
        assertTryGetterThrowsThrowableAsIs(tr -> tr.getOrThrowThrowable(), new Throwable());
    }

    @Test
    public void getOrThrowThrowableResetsInterruptFlagForInterruptedException() {
        assertTryGetterResetsInterruptFlagForInterruptedException(tr -> tr.getOrThrowThrowable());
    }

    @Test
    public void getOrThrowThrowableDoesntResetInterruptFlagForNonInterruptedException() {
        assertTryGetterDoesntResetInterruptFlagForNonInterruptedException(tr -> tr.getOrThrowThrowable());
    }

    @Test
    public void getOrRecoverReturnsOriginalResultIfNonNullSuccessful() {
        Try<Integer> result = Try.ofSuccess(5);

        Integer recovered = result.getOrRecover(recovery);

        assertThat(recovered, is(5));
        verifyNoInteractions(recovery);
    }

    @Test
    public void getOrRecoverReturnsOriginalResultIfNullSuccessful() {
        Try<Integer> result = Try.ofSuccess(null);

        Integer recovered = result.getOrRecover(recovery);

        assertNull(recovered);
        verifyNoInteractions(recovery);
    }

    @Test
    public void getOrRecoverExecutesRecoveryFunctionOnFailures() {
        Throwable throwable = new Throwable();
        Try<Integer> result = Try.ofFailureSwallowingInterrupt(throwable);
        when(recovery.apply(throwable)).thenReturn(10);

        Integer recovered = result.getOrRecover(recovery);

        assertThat(recovered, is(10));
    }

    @Test
    public void getOrRecoverPropagatesRecoveryFailuresAsIs() {
        Throwable original = new Throwable();
        Try<Integer> result = Try.ofFailureSwallowingInterrupt(original);
        RuntimeException recoveryFailure = new RuntimeException();
        when(recovery.apply(original)).thenThrow(recoveryFailure);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> result.getOrRecover(recovery));

        assertThat(thrown, sameInstance(recoveryFailure));
    }

    @Test
    public void getOrHandleReturnsOriginalResultIfNonNullSuccessful() {
        Try<Integer> result = Try.ofSuccess(5);

        Optional<Integer> handled = result.getOrHandle(handler);

        assertThat(handled, is(Optional.of(5)));
        verifyNoInteractions(handler);
    }

    @Test
    public void getOrHandleReturnsOriginalResultIfNullSuccessful() {
        Try<Integer> result = Try.ofSuccess(null);

        Optional<Integer> handled = result.getOrHandle(handler);

        assertThat(handled, is(Optional.empty()));
        verifyNoInteractions(handler);
    }

    @Test
    public void getOrHandleExecutesHandlerFunctionOnFailures() {
        Throwable throwable = new Throwable();
        Try<Integer> result = Try.ofFailureSwallowingInterrupt(throwable);

        Optional<Integer> handled = result.getOrHandle(handler);

        assertThat(handled, is(Optional.empty()));
        verify(handler).accept(throwable);
    }

    @Test
    public void getOrHandlePropagatesHandlingFailuresAsIs() {
        Throwable original = new Throwable();
        Try<Integer> result = Try.ofFailureSwallowingInterrupt(original);
        RuntimeException handlingFailure = new RuntimeException();
        doThrow(handlingFailure).when(handler).accept(original);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> result.getOrHandle(handler));

        assertThat(thrown, sameInstance(handlingFailure));
    }

    @Test
    public void observeFailureReturnsSameTryIfSuccessful() {
        Try<Integer> result = Try.ofSuccess(5);

        Try<Integer> actual = result.observeFailure(handler);

        assertThat(actual, sameInstance(result));
        verifyNoInteractions(handler);
    }

    @Test
    public void observeFailureReturnsSameTryIfFailed() {
        Throwable throwable = new Throwable();
        Try<Integer> result = Try.ofFailureSwallowingInterrupt(throwable);

        Try<Integer> actual = result.observeFailure(handler);

        assertThat(actual, sameInstance(result));
        verify(handler).accept(throwable);
    }

    @Test
    public void convertPassesNullSuccessToMapper() {
        Try<Integer> result = Try.ofSuccess(null);
        when(converter.apply(null, null)).thenReturn("converted");

        String converted = result.convert(converter);

        assertThat(converted, is("converted"));
    }

    @Test
    public void convertPassesNonNullSuccessToMapper() {
        Try<Integer> result = Try.ofSuccess(5);
        when(converter.apply(5, null)).thenReturn("converted");

        String converted = result.convert(converter);

        assertThat(converted, is("converted"));
    }

    @Test
    public void convertPassesFailureToMapper() {
        Throwable throwable = new Throwable();
        Try<Integer> result = Try.ofFailureSwallowingInterrupt(throwable);
        when(converter.apply(null, throwable)).thenReturn("converted");

        String converted = result.convert(converter);

        assertThat(converted, is("converted"));
    }

    @Test
    public void convertPropagatesConverterFunctionAsIs() {
        Try<Integer> result = Try.ofSuccess(5);
        RuntimeException convertingFailure = new RuntimeException();
        when(converter.apply(5, null)).thenThrow(convertingFailure);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> result.convert(converter));

        assertThat(thrown, sameInstance(convertingFailure));
    }

    @Test
    public void consumePassesNullSuccessToMapper() {
        Try<Integer> result = Try.ofSuccess(null);

        result.consume(consumer);

        verify(consumer).accept(null, null);
    }

    @Test
    public void consumePassesNonNullSuccessToMapper() {
        Try<Integer> result = Try.ofSuccess(5);

        result.consume(consumer);

        verify(consumer).accept(5, null);
    }

    @Test
    public void consumePassesFailureToMapper() {
        Throwable throwable = new Throwable();
        Try<Integer> result = Try.ofFailureSwallowingInterrupt(throwable);
        when(converter.apply(null, throwable)).thenReturn("converted");

        result.consume(consumer);

        verify(consumer).accept(null, throwable);
    }

    @Test
    public void consumePropagatesConverterFunctionAsIs() {
        Try<Integer> result = Try.ofSuccess(5);
        RuntimeException consumingFailure = new RuntimeException();
        doThrow(consumingFailure).when(consumer).accept(5, null);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> result.consume(consumer));

        assertThat(thrown, sameInstance(consumingFailure));
    }

    @Test
    public void hashCodeObeysContract() {
        Try<Integer> success1 = Try.ofSuccess(18);
        Try<Integer> success2 = Try.ofSuccess(18);
        Try<Integer> successNull1 = Try.ofSuccess(null);
        Try<Integer> successNull2 = Try.ofSuccess(null);
        IllegalArgumentException exception = new IllegalArgumentException();
        Try<Integer> failure1 = Try.ofFailureSwallowingInterrupt(exception);
        Try<Integer> failure2 = Try.ofFailureSwallowingInterrupt(exception);

        // Same object produces same hash code on multiple calls
        assertThat(success1.hashCode(), is(success1.hashCode()));
        assertThat(successNull1.hashCode(), is(successNull2.hashCode()));
        assertThat(failure1.hashCode(), is(failure1.hashCode()));
        // Equal objects have same hash code
        assertThat(success1.hashCode(), is(success2.hashCode()));
        assertThat(successNull1.hashCode(), is(successNull2.hashCode()));
        assertThat(failure1.hashCode(), is(failure2.hashCode()));
    }

    @Test
    public void equalsObeysContract() {
        Try<Integer> success1 = Try.ofSuccess(18);
        Try<Integer> success2 = Try.ofSuccess(18);
        Try<Integer> successNull1 = Try.ofSuccess(null);
        Try<Integer> successNull2 = Try.ofSuccess(null);
        IllegalArgumentException exception = new IllegalArgumentException();
        Try<Integer> failure1 = Try.ofFailureSwallowingInterrupt(exception);
        Try<Integer> failure2 = Try.ofFailureSwallowingInterrupt(exception);

        // Reflexive
        assertThat(success1, is(success1));
        assertThat(successNull1, is(successNull1));
        assertThat(failure1, is(failure1));
        // Symmetric
        assertThat(success1, not(18));
        assertThat(18, not(success1));
        assertThat(success1, equalTo(success2));
        assertThat(success2, equalTo(success1));
        assertThat(successNull1, equalTo(successNull2));
        assertThat(successNull2, equalTo(successNull1));
        assertThat(failure1, not(18));
        assertThat(18, not(failure1));
        assertThat(failure1, equalTo(failure2));
        assertThat(failure2, equalTo(failure1));
        assertThat(success1, not(equalTo(successNull1)));
        assertThat(successNull1, not(equalTo(success1)));
        assertThat(success1, not(equalTo(failure1)));
        assertThat(failure1, not(equalTo(success1)));
        assertThat(successNull1, not(equalTo(failure1)));
        assertThat(failure1, not(equalTo(successNull1)));
    }

    @Test
    public void toStringIncludesBothSuccessAndFailure() {
        IllegalArgumentException exception = new IllegalArgumentException();
        Try<Integer> failure = Try.ofFailureSwallowingInterrupt(exception);

        assertThat(Try.ofSuccess(18).toString(), containsString("18"));
        assertThat(failure.toString(), containsString(exception.toString()));
    }
}
