# One-More-Try

One-More-Try is a simple java library containing a single class (called Try) representing either a success or a failure trying to do something.

This idea is useful for and targets 3 major usecases:

1. Transforming code that throws checked exceptions into code that throws unchecked exceptions -- checked exceptions can be annoying when there's no specific way to handle them in calling code. At the same time, just wrapping them in a RuntimeException isn't always the right thing to do (i.e. remember to set the interrupt flag for
InterruptedExceptions).
2. Creating exception-handling code -- this class helps remove some of the boilerplate for *general* code that deals with *general* exceptions (e.g. exception-resilient code, like logging an exception and returning a default). Note: this class is not meant as a general replacement for try-catch blocks: for code targeting specific exceptions (or really whenever you can get away with it with just-as-good code, a normal try-catch block is much more appropriate.
3. Passing around a value that indicates success or failure -- normally, code is structured around executing an operation and throwing an exception for failures. Sometimes, however, it's convenient to pass back a single value that can represent a success or failure itself (e.g. when integrating an optional, fault-tolerant call into a framework of calls).

There are already a couple of [examples](https://stackoverflow.com/q/27787772) of this kind of class being built . However, I decided against using those for a couple of reasons: 
1. They don't meet all of the targeted usecases above -- e.g. better-java-monads doesn't allow access to the thrown exception. 
2. They have questionable practices -- e.g. vavr and cyclops "sneakyThrow" checked exceptions (i.e. some of their methods throw checked exceptions without declaring them in the method signature). Also, some libraries catch all exceptions by default, including Errors, even though that's generally [frowned against](https://stackoverflow.com/a/11018879). Lastly, some libraries either ignore InterruptedExceptions or don't implement [the expected side effects](https://stackoverflow.com/a/3976377) for catching it . In contrast, Try is intended to make it easy to do the standard thing correctly: it declares checked exceptions when relevant and handles setting the current Thread's interrupt status based on InterruptedExceptions.
3. They come with a lot of baggage -- vavr and cyclops come with and build upon a lot of other classes and concepts. I don't want to force that learning curve on users. I just want a simple, focused Try utility.

## Adding this project to your build

This project follows [semantic versioning](https://semver.org/). It's currently available via the Maven snapshot repository only as version 0.0.1-SNAPSHOT. The "0.0.1" part is because it's still in development, waiting for feedback or sufficient usage until its first official release. The "SNAPSHOT" part is because no one has requested a stable, non-SNAPSHOT development version. If you need a non-SNAPSHOT development version, feel free to reach out, and I can build this project into the Maven central repository.

## Usage

This project requires JDK version 11 or higher.

Below are examples/starting points for using Try in each of the 3 targeted use cases:

### Tranforming checked into unchecked


```java
CountDownLatch latch = new CountDownLatch(1);
Try.runUnchecked(() -> latch.await(5, TimeUnit.SECONDS));
```

### Creating exception-handling code


```java
Output implemementServiceCall(Input input){
    return Try.callCatchThrowable(() -> implementation.call());
            .observeFailure(throwable -> logger.error(throwable, "Error executing service"));
            .getOrThrowUnchecked();
}
```

### Passing around a value


```java
Try<Integer> tryToPrimeDependency(Dependency optionalDependency){
    return Try.callCatchRuntime(() -> optionalDependency.prime());
}
```

## Contributions

Contributions are welcome! See the [graydavid-parent](https://github.com/graydavid/graydavid-parent) project for details.