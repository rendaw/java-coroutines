# Coroutines

This is a fully functional framework/tool-agnostic coroutines implementation for Java.

Now supports Java 9!

``` java
Coroutine co = new Coroutine(() -> {
    System.out.println("Taking a break!\n");
    Coroutine.yield();
    System.out.println("Break time over.\n");
    System.out.format("%s\n", asyncMethod());
});

co.process();
System.out.format("1st break!\n");
co.process();
System.out.format("2nd break!\n");
co.process();
System.out.format("Coroutine done!\n");

public static int asyncMethod() throws SuspendExecution {
    System.out.println("Need another break.\n");
    Coroutine.yield();
    System.out.println("Okay, let's get started.\n");
    return 3;
}
```

```
Taking a break!
1st break!
Break time over.
Need another break.
2nd break!
Okay, let's get started.
3
Coroutine done!
```

## Maven

``` xml
<dependency>
    <groupId>com.zarbosoft</groupId>
    <artifactId>coroutines</artifactId>
    <version>0.0.5</version>
</dependency>
```

## Usage

A `Coroutine` runs a `SuspendableRunnable`, similar to how `Thread` runs a `Runnable`.  When the coroutine's runnable
suspends it can be started again from the point it suspended by another call to `process`.

Methods that throw `SuspendExcecution` are suspendable.  Suspendable methods can be called from other suspendable
methods.  Coroutines uses `SuspendExecution` exceptions to do the suspension, but don't worry about running
suspendable methods in `try` blocks - as long as you don't catch `SuspendExecution` explicitly there's no problem.

Running suspendable code takes a few additional steps.  Follow
[these instructions](https://github.com/rendaw/java-coroutines-core#running-your-code) to get going.

## Additional features

Aside from suspending and resuming, you can...

### Inject values and exceptions into coroutines when resuming

Make async callback-based apis synchronous, or use them as generators.

``` java
Coroutine c = Coroutine.getActiveCoroutine();
byte[] data = Coroutine.yieldThen(() -> {
    slowOperationWithCallback(result -> c.process(result));
});
display(data);
work2(data);
```

### Run blocking code in a coroutine

``` java
static ExecutorService executor = ...;

static void asyncCode() throws SuspendExecution {
    ...
    Cohelp.unblock(executor, () -> {
        double value = 0;
        for (long x = 0; x < Math.pow(10, 10); ++x) {
            value += 1;
        }
    });
    ...
}
```

### Run a coroutine in blocking code

``` java
public static void main(String[] args) {
    Cohelp.block(() -> {
        asyncDownloadValues();
        asyncRunProcess();
    });
}
```

### Turn a CompletableFuture into a suspending async call

``` java
public static void asyncCode() throws SuspendExecution {
    ...
    JsonNode response = Cohelp.unblock(rpc.call("get_history", "me", "shadowhawk4949"));
    ...
}
```

### Sleep

``` java
static ScheduledExecutorService executor = ...;

public static void asyncCode() throws SuspendExecution {
    System.out.println("sleeping");
    Cohelp.sleep(executor, 1, MINUTES);
    System.out.println("waking");
}
```

### Timers

``` java
static ScheduledExecutorService executor = ...;

public static void main(String[] args) {
    ...
    Cohelp.timer(executor, 1, HOURS, () -> {
        System.out.println(downloadWeather());
    });
    ...
}
```

### Asynchronous critical sections (think async synchronized blocks)

``` java
CriticalSection<Integer, Integer> critical = new CriticalSection<>() {
    private volatile int counter = 0;

    @Override
    protected int execute(int argument) throws SuspendExecution {
        ...
        return ++counter;
    }
};

public int accessService(int argument) {
    return critical.execute(argument);
}
```

### And more! (but not much more)

This is a wrapper around [coroutines-core](https://github.com/rendaw/java-coroutines-core) providing some utilities
to improve compatibility with other libraries and make it easier to use.  If you want a minimal coroutines
implementation, see that project.
