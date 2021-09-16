/*
 * Copyright 2021 David Gray
 * 
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.graydavid.onemoretry;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A representation of a response indicative of either a success or a failure trying to do something.
 * 
 * This idea is useful for and targets 3 major usecases:<br>
 * 1. Transforming code that throws checked exceptions into code that throws unchecked exceptions -- checked exceptions
 * can be annoying when there's no specific way to handle them in calling code. At the same time, just wrapping them in
 * a RuntimeException isn't always the right thing to do (i.e. remember to set the interrupt flag for
 * InterruptedExceptions).<br>
 * 2. Creating exception-handling code -- this class helps remove some of the boilerplate for *general* code that deals
 * with *general* exceptions (e.g. exception-resilient code, like logging an exception and returning a default). Note:
 * this class is not meant as a general replacement for try-catch blocks: for code targeting specific exceptions (or
 * really whenever you can get away with it with just-as-good code, a normal try-catch block is much more
 * appropriate.<br>
 * 3. Passing around a value that indicates success or failure -- normally, code is structured around executing an
 * operation and throwing an exception for failures. Sometimes, however, it's convenient to pass back a single value
 * that can represent a success or failure itself (e.g. when integrating an optional, fault-tolerant call into a
 * framework of calls).
 * 
 * There are already examples of this kind of class being built: https://stackoverflow.com/q/27787772 . However, I
 * decided against using those for a couple of reasons: <br>
 * 1. They don't meet all of the targeted usecases above -- e.g. better-java-monads doesn't allow access to the thrown
 * exception. <br>
 * 2. They have questionable practices -- e.g. vavr and cyclops "sneakyThrow" checked exceptions (i.e. some of their
 * methods throw checked exceptions without declaring them in the method signature). Also, some libraries catch all
 * exceptions by default, including Errors, even though that's generally frowned against
 * (https://stackoverflow.com/a/11018879 ). Lastly, some libraries either ignore InterruptedExceptions or don't
 * implement the expected side effects for catching it (https://stackoverflow.com/a/3976377 ). In contrast, this class
 * below is intended to make it easy to do the standard thing correctly. <br>
 * 3. They come with a lot of baggage -- vavr and cyclops come with and build upon a lot of other classes and concepts.
 * I don't want to force that learning curve on people. I just want a simple, focused Try utility.<br>
 */
public class Try<T> {
    private final T success;
    private final Throwable failure;

    private Try(T success, Throwable failure) {
        this.success = success;
        this.failure = failure;
    }

    /**
     * Creates a Try object whose (nullable) result represents a success. This would be like a normal try block
     * succeeding without throwing an exception.
     */
    public static <T> Try<T> ofSuccess(T success) {
        return new Try<>(success, null);
    }

    /**
     * Creates a Try object whose (non-nullable) result represents a failure. This would be like a normal try block
     * throwing an exception. This method does *not* set the current Thread's interrupt status (by calling
     * {@link Thread#interrupt()}) if failure is an InterruptedException, instead swallowing the interrupt (which is
     * usually against standard practice... but maybe you're calling this method outside of a try-catch usecase).
     */
    public static <T> Try<T> ofFailureSwallowingInterrupt(Throwable failure) {
        if (failure == null) {
            throw new NullPointerException("failure must not be null");
        }
        return new Try<>(null, failure);
    }

    /**
     * Similar to {@link #ofFailureSwallowingInterrupt(Throwable)}, except {@link Thread#interrupt()} is called if
     * failure is an InterruptedException, which is standard practice for catching an InterruptedException.
     */
    public static <T> Try<T> ofFailurePreservingInterrupt(Throwable failure) {
        if (failure instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        return ofFailureSwallowingInterrupt(failure);
    }

    /**
     * Creates a Try from either the success or failure, which is convenient for code accepting a BiConsumer, like
     * CompletableFuture. Same as {@link #ofFailureSwallowingInterrupt(Throwable)} if failure is non-null (and success
     * if null); otherwise, same as {@link #ofSuccess(Object)}.
     * 
     * @throws IllegalArgumentException if both success and failure are non-null.
     */
    public static <T> Try<T> ofSwallowingInterrupt(T success, Throwable failure) {
        return of(success, failure, Try::ofFailureSwallowingInterrupt);
    }

    private static <T> Try<T> of(T success, Throwable failure, Function<Throwable, Try<T>> failureFactory) {
        if (!(success == null || failure == null)) {
            String message = String.format("At least one of success or failure must be null: <%s, %s>", success,
                    failure);
            throw new IllegalArgumentException(message);
        }
        return failure == null ? ofSuccess(success) : failureFactory.apply(failure);
    }

    /**
     * Similar to {@link #ofSwallowingInterrupt(Object, Throwable), except
     * {@link #ofFailurePreservingInterrupt(Throwable)} is called for failures instead.
     */
    public static <T> Try<T> ofPreservingInterrupt(T success, Throwable failure) {
        return of(success, failure, Try::ofFailurePreservingInterrupt);
    }

    /**
     * Same as {@link #callCatchRuntime(RuntimeCallable)}, except that nothing is returned from the runnable, so a
     * Try<Void> is returned. {@link #getSuccess()} will always return an empty Optional, regardless of whether the
     * result was actually successful or not.
     */
    public static Try<Void> runCatchRuntime(Runnable runnable) {
        return callCatchRuntime(() -> {
            runnable.run();
            return null;
        });
    }

    /**
     * Calls callable and creates a Try from the result. Any RuntimeException thrown is caught and represented in the
     * returned Try; Exceptions, Errors, and Throwables are not caught and are propagated from this method. In that way,
     * this method follows the usual best practice of not catching Errors.
     * 
     * Note: Exceptions and Throwables are not caught and they are not declared in either this method's or
     * RuntimeCallable signature. Why mention this? Through some generics magic (https://stackoverflow.com/q/31316581 ),
     * the callable could still sneakily throw Exceptions and Throwables.
     */
    public static <T> Try<T> callCatchRuntime(RuntimeCallable<T> callable) {
        try {
            T success = callable.call();
            return Try.ofSuccess(success);
        } catch (RuntimeException e) {
            return Try.ofFailureSwallowingInterrupt(e);
        }
    }

    /** Similar to {@link Callable} except that it declares that it throws a RuntimeException, like {@link Runnable}. */
    @FunctionalInterface
    public interface RuntimeCallable<T> {
        T call();
    }

    /**
     * Same as {@link #callCatchException(Callable)}, except that nothing is returned from the runnable, so a Try<Void>
     * is returned. {@link #getSuccess()} will always return an empty Optional, regardless of whether the result was
     * actually successful or not.
     */
    public static Try<Void> runCatchException(ExceptionRunnable runnable) {
        return callCatchException(() -> {
            runnable.run();
            return null;
        });
    }

    /** Similar to {@link Runnable} except that it declares that it throws an Exception, like {@link Callable}. */
    @FunctionalInterface
    public interface ExceptionRunnable {
        void run() throws Exception;
    }

    /**
     * Similar to {@link #callCatchException(Callable)} except that, in addition to RuntimeExceptions, Exceptions are
     * also caught; Errors and Throwables are still not caught. In addition, for InterruptedException, the current
     * Thread's interrupt status will be set via {@link Thread#interrupt()} (as per standard practice.
     * 
     * Note: Throwables are not caught and they are not declared in either this method's or Callable's signature. Why
     * mention this? Through some generics magic (https://stackoverflow.com/q/31316581 ), the callable could still
     * sneakily throw Throwables.
     */
    public static <T> Try<T> callCatchException(Callable<T> callable) {
        try {
            T success = callable.call();
            return Try.ofSuccess(success);
        } catch (Exception e) {
            return Try.ofFailurePreservingInterrupt(e);
        }
    }

    /**
     * Same as {@link #callCatchThrowable(Callable)}, except that nothing is returned from the runnable, so a Try<Void>
     * is returned. {@link #getSuccess()} will always return an empty Optional, regardless of whether the result was
     * actually successful or not.
     */
    public static Try<Void> runCatchThrowable(ThrowableRunnable runnable) {
        return callCatchThrowable(() -> {
            runnable.run();
            return null;
        });
    }

    /** Similar to {@link Runnable} except that it declares that it throws a Throwable. */
    @FunctionalInterface
    public interface ThrowableRunnable {
        void run() throws Throwable;
    }

    /**
     * Similar to {@link #callCatchException(Callable)} except that, in addition to Exceptions and RuntimeExceptions,
     * Errors and Throwables are also caught.
     * 
     * BE CAREFUL when you call this method, since, as {@link #callCatchException(Callable)} says, Errors usually should
     * not be caught explicitly in standard code.
     */
    public static <T> Try<T> callCatchThrowable(ThrowableCallable<T> callable) {
        try {
            T success = callable.call();
            return Try.ofSuccess(success);
        } catch (Throwable e) {
            return Try.ofFailurePreservingInterrupt(e);
        }
    }

    /** Similar to {@link Callable} except that it declares that it throws a Throwable. */
    @FunctionalInterface
    public interface ThrowableCallable<T> {
        T call() throws Throwable;
    }

    /**
     * Runs the runnable as if it only produced unchecked exceptions. If a checked exception is thrown, it's caught,
     * wrapped in a {@link CheckedExceptionWrapper}, and then rethrown. This is the same as calling
     * {@link #runCatchThrowable(ThrowableRunnable)} followed by {@link #getOrThrowUnchecked(Function)}, which is
     * something you can do if you want more control over what's caught and/or how the checked exception is transformed.
     * 
     * @throws CheckedExceptionWrapper if this Try is a failure and the underlying exception is a checked exception. The
     *         checked exception is the {@link CheckedExceptionWrapper}'s cause.
     * @throws the underlying exception if this Try is a failure and the exception is unchecked.
     * 
     * @apiNote There was originally another variation of this method that accepted an additional "Function<? super
     *          Throwable, ? extends Throwable> checkedTransformer" parameter. However, that quickly multiplied for
     *          every similar unchecked method on this Try class. Instead, I'm forcing users to call the underlying
     *          methods if they want more control. I think this is a reasonable compromise. This method is just an
     *          extreme utility version.
     */
    public static void runUnchecked(ThrowableRunnable runnable) {
        Try.runCatchThrowable(runnable).getOrThrowUnchecked(CheckedExceptionWrapper::new);
    }

    /** An unchecked wrapper around a CheckedException */
    public static class CheckedExceptionWrapper extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private CheckedExceptionWrapper(Throwable cause) {
            super(cause);
        }
    }

    /**
     * Creates a runnable that can run the runnable as it it only produced unchecked exceptions. In other words,
     * invoking the runnable has the same behavior as {@link Try#runUnchecked(ThrowableRunnable)}.
     * 
     * @apiNote see apiNote on {@link #runUnchecked(ThrowableRunnable)}.
     */
    public static Runnable uncheckedRunnable(ThrowableRunnable runnable) {
        return () -> Try.runCatchThrowable(runnable).getOrThrowUnchecked(CheckedExceptionWrapper::new);
    }

    /**
     * Calls the callable as if it only produced unchecked exceptions. If a checked exception is thrown, it's caught,
     * wrapped in a {@link CheckedExceptionWrapper}, and then rethrown. This is the same as calling
     * {@link #callCatchThrowable(ThrowableCallable)} followed by {@link #getOrThrowUnchecked(Function)}, which is
     * something you can do if you want more control over what's caught and/or how the checked exception is transformed.
     * 
     * @throws CheckedExceptionWrapper if this Try is a failure and the underlying exception is a checked exception. The
     *         checked exception is the {@link CheckedExceptionWrapper}'s cause.
     * @throws the underlying exception if this Try is a failure and the exception is unchecked.
     * 
     * @apiNote see apiNote on {@link #runUnchecked(ThrowableRunnable)}.
     */
    public static <T> T callUnchecked(ThrowableCallable<T> callable) {
        return Try.callCatchThrowable(callable).getOrThrowUnchecked(CheckedExceptionWrapper::new);
    }

    /**
     * Creates a supplier that can call the callable as it it only produced unchecked exceptions. In other words,
     * invoking the supplier has the same behavior as {@link Try#callUnchecked(ThrowableCallable)}.
     */
    public static <T> Supplier<T> uncheckedSupplier(ThrowableCallable<T> callable) {
        return () -> callUnchecked(callable);
    }

    /**
     * Gets the successful part of this Try, if present. If the try was not successful, then an empty Optional is
     * returned. Note: an empty Optional can also be produced by a successful try with a null result, so that should not
     * be used to check whether this Try was successful.
     */
    public Optional<T> getSuccess() {
        return Optional.ofNullable(success);
    }

    /** Gets the failed part of this Try, if present. An empty Optional is returned if and only if the Try failed. */
    public Optional<Throwable> getFailure() {
        return Optional.ofNullable(failure);
    }

    /**
     * Returns the possibly null value for the successful part of this Try. The value could be null either if the try
     * was a failure or the if it was a success and the value associated with that success is null. This method is the
     * same as {@link #getSuccess()}; it's just that the response is the raw value rather than an Optional wrapping the
     * raw value.
     */
    public T getNullableSuccess() {
        return success;
    }

    /**
     * Returns the possibly null value for the failed part of this Try. The value is null if and only if the Try was a
     * failure. This method is the same as {@link #getFailure()}; it's just that the failure is the raw value rather
     * than an Optional wrapping the raw value.
     */
    public Throwable getNullableFailure() {
        return failure;
    }

    /** Returns whether or not this Try was successful. */
    public boolean isSuccess() {
        return failure == null;
    }

    /** Returns whether or not this Try was a failure. */
    public boolean isFailure() {
        return !isSuccess();
    }

    /**
     * If this Try was successful, this method returns the successful part; otherwise, this method throws the underlying
     * exception as an unchecked exception. If the underlying exception is a checked exception, it's wrapped in a
     * {@link CheckedExceptionWrapper} before being thrown. If you want more flexibility in the type of wrapper
     * exception thrown, see {@link #getOrThrowUnchecked(Function)}.
     * 
     * Note: this method makes *no* changes to the Thread's interrupt status, even if the checked exception is an
     * InterruptedException. It assumes that the desired interrupt status was already determined on creation of this
     * Try. Plus, since this method only throws unchecked exceptions, it cannot be throwing an InterruptedException,
     * leaving the interrupt status as the sole indicator of whether the Thread was interrupted.
     * 
     * @throws CheckedExceptionWrapper if this Try is a failure and the underlying exception is a checked exception. The
     *         checked exception is the {@link CheckedExceptionWrapper}'s cause.
     * @throws any other non-checked exception if this Try is a failure.
     */
    public T getOrThrowUnchecked() {
        return getOrThrowUnchecked(CheckedExceptionWrapper::new);
    }

    /**
     * Same as {@link #getOrThrowUnchecked()}, except instead of always Throwing a CheckedExceptionWrapper for
     * underlying checked exceptions, it calls checkedTransformer for those exceptions to make them into unchecked
     * exceptions and throws that.
     * 
     * @param checkedTransformer called to transform a checked exception. Should return either a RuntimeException or an
     *        Error.
     * 
     * @throws same as {@link #getOrThrowUnchecked()}. Plus, if checkedTransformer throws an exception, that exception
     *         is propagated as is. Plus, if checkedTransformer returns a checked exception itself, then an
     *         IllegalArgumentException is thrown with the checked exception as the cause.
     * 
     * @apiNote It would be nice if java had a single class to represent unchecked exceptions, so that
     *          checkedTransformer's response could be more targeted towards that instead of the generic Throwable. I
     *          thought about making multiple getOrThrowUncheckedViaRuntime and getOrThrowUncheckedViaError, which would
     *          be targeted towards RuntimeExceptions and Errors, but I think there would always be a need for something
     *          that handles both. Plus, having multiple methods and forcing users to choose would branch their code as
     *          well. I think targeting Throwable and wrapping that in a (RuntimeException) IllegalArgumentException is
     *          a good compromise, since it technically still does what this method is about... just not exactly as the
     *          user may intend.
     */
    public T getOrThrowUnchecked(Function<? super Throwable, ? extends Throwable> checkedTransformer) {
        if (isSuccess()) {
            return success;
        }

        throwIfUnchecked(failure);
        Throwable transformed = checkedTransformer.apply(failure);
        throwIfUnchecked(transformed);
        throw new IllegalArgumentException("The checkedTransformer should only return unchecked exceptions",
                transformed);
    }

    private static void throwIfUnchecked(Throwable throwable) {
        if (throwable instanceof RuntimeException) {
            throw (RuntimeException) throwable;
        }
        if (throwable instanceof Error) {
            throw (Error) throwable;
        }
    }

    /**
     * If this Try was successful, this method returns the successful part; otherwise, this method throws the underlying
     * exception as is... unless it's a Throwable, in which case it's first wrapped in a {@link CheckedExceptionWrapper}
     * before being thrown. If you want more flexibility in the type of wrapper exception thrown, see
     * {@link #getOrThrowException(Function)}.
     * 
     * Note: If the exception is an InterruptedException, the Thread's interrupt status is cleared (so that
     * {@link Thread#isInterrupted()} will return false). This is standard best practice, as throwing an
     * InterruptedException should be the sole indicator of whether the Thread was interrupted.
     * 
     * @throws CheckedExceptionWrapper if this Try is a failure and the underlying exception is a checked exception. The
     *         checked exception is the {@link CheckedExceptionWrapper}'s cause.
     * @throws any other non-checked exception if this Try is a failure.
     */
    public T getOrThrowException() throws Exception {
        return getOrThrowException(CheckedExceptionWrapper::new);
    }

    /**
     * Same as {@link #getOrThrowException()}, except instead of always Throwing a CheckedExceptionWrapper for
     * underlying Throwables, it calls checkedTransformer for those exceptions to make them into unchecked exceptions
     * and throws that.
     * 
     * @param throwableTransformer called to transform a checked exception. Should return either an Exception or an
     *        Error.
     * 
     * @throws same as {@link #getOrThrowException()}. Plus, if throwableTransformer throws an exception, that exception
     *         is propagated as is. Plus, if throwableTransformer returns a Throwable itself, then an
     *         IllegalArgumentException is thrown with the Throwable as the cause.
     * 
     * @apiNote See the apiNote under {@link #getOrThrowUnchecked()} for a similar note as to what applies here: why we
     *          don't limit the response type from throwableTransformer so that it would be impossible for it to return
     *          a Throwable.
     */
    public T getOrThrowException(Function<? super Throwable, ? extends Throwable> throwableTransformer)
            throws Exception {
        if (isSuccess()) {
            return success;
        }

        clearInterruptStatusForInterruptedException(failure);
        throwIfNonThrowableChecked(failure);
        Throwable transformed = throwableTransformer.apply(failure);
        throwIfNonThrowableChecked(transformed);
        throw new IllegalArgumentException("The throwableTransformer should not return Throwables", transformed);
    }

    private static void clearInterruptStatusForInterruptedException(Throwable throwable) {
        if (throwable instanceof InterruptedException) {
            Thread.interrupted();
        }
    }

    private static void throwIfNonThrowableChecked(Throwable throwable) throws Exception {
        if (throwable instanceof Exception) {
            throw (Exception) throwable;
        }
        if (throwable instanceof Error) {
            throw (Error) throwable;
        }
    }

    /**
     * If this Try was successful, this method returns the successful part; otherwise, this method throws the underlying
     * exception as is.
     * 
     * Note: as with {@link #getOrThrowException()} and for the same reasons, if the exception is an
     * InterruptedException, the Thread's interrupt status is cleared (so that {@link Thread#isInterrupted()} will
     * return false).
     * 
     * BE CAREFUL when you call this method, since it will encourage upstream clients to catch all Throwables, including
     * errors. As {@link #callCatchException(Callable)} says, Errors should usually not be caught explicitly in standard
     * code.
     * 
     * @throws the underlying exception if this Try is a failure and the exception is unchecked.
     */
    public T getOrThrowThrowable() throws Throwable {
        if (isSuccess()) {
            return success;
        }

        clearInterruptStatusForInterruptedException(failure);
        throw failure;
    }

    /**
     * If this Try is successful, the original successful result is returned; otherwise, the recovery function is
     * executed against the underlying failure to produce the returned result. If the recovery function itself throws an
     * exception, that exception is propagated from this function.
     */
    public T getOrRecover(Function<? super Throwable, ? extends T> recovery) {
        if (isSuccess()) {
            return success;
        }
        return recovery.apply(failure);
    }

    /**
     * If this Try is successful, the original successful result is returned, wrapped in an Optional; otherwise, the
     * handler function is executed against the underlying failure, and an empty Optional is returned. If the handler
     * function itself throws an exception, that exception is propagated from this function. Remember that successful
     * Trys can have null results, so the empty-ness of the returned Optional does not indicate success/failure.
     */
    public Optional<T> getOrHandle(Consumer<? super Throwable> handler) {
        return observeFailure(handler).getSuccess();
    }

    /**
     * A convenient, chainable method to observe the failure part of this object. If this Try is a failure, the observer
     * function is executed against the underlying failure. Whether or not Try is a success or failure, this method then
     * always returns the current object. If the observer function itself throws an exception, that exception is
     * propagated from this function.
     */
    public Try<T> observeFailure(Consumer<? super Throwable> observer) {
        if (failure != null) {
            observer.accept(failure);
        }
        return this;
    }

    /**
     * Converts the (possibly null... but never both non-null at the same time) success and failure values for this try
     * through the provided converter to produce a new type of result. If the converter function itself throws an
     * exception, that exception is propagated from this function.
     */
    public <U> U convert(BiFunction<? super T, ? super Throwable, U> converter) {
        return converter.apply(success, failure);
    }

    /**
     * Same as {@link #convert(BiFunction)}, but merely consumes the success and failure and doesn't return a result.
     */
    public void consume(BiConsumer<? super T, ? super Throwable> consumer) {
        consumer.accept(success, failure);
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof Try) {
            Try<?> other = (Try<?>) object;
            return Objects.equals(success, other.success) && Objects.equals(failure, other.failure);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, failure);
    }

    @Override
    public String toString() {
        return String.format("Try[success=%s,failure=%s]", success, failure);
    }
}
