# Coroutines

This is fully functional framework/tool-agnostic coroutines implementation for Java.

Note that currently it only works on <= Java 1.8. AFAIK though this is due to the ASM dependency.

```
Coroutine co = new Coroutine(() -> {
    System.out.println("Taking a break!");
    Coroutine.yield();
    System.out.println("Break time over.");
    System.out.format("%s\n", asyncMethod1());
});

co.process();
System.out.format("1st break!");
co.process();
System.out.format("2nd break!");
co.process();
System.out.format("Coroutine done!");

public static int asyncMethod() throws SuspendExecution {
    System.out.println("Need another break.");
    Coroutine.yield();
    System.out.println("Okay, let's get started.");
    return 3;
}
```

Do not catch `SuspendExecution`. Suspendable methods and yield can only be called from suspendable methods. A suspended
coroutine can be resumed anywhere.

Aside from suspending and resuming, you can...

### Inject values and exceptions into coroutines when resuming

Make async callback-based apis synchronous, or use them as generators.

```
Coroutine c = Coroutine.getActiveCoroutine();
byte[] data = Coroutine.yieldThen(() -> {
    slowOperationWithCallback(result -> c.process(result));
});
display(data);
work2(data);
```

### Run blocking code in a coroutine

```
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

```
public static void main(String[] args) {
    Cohelp.block(() -> {
        asyncDownloadValues();
        asyncRunProcess();
    });
}
```

### Turn a CompletableFuture into a suspending async call

```
public static void asyncCode() throws SuspendExecution {
    ...
    JsonNode response = Cohelp.unblock(rpc.call("get_history", "me", "shadowhawk4949"));
    ...
}
```

### Sleep

```
static ScheduledExecutorService executor = ...;

public static void asyncCode() throws SuspendExecution {
    System.out.println("sleeping");
    Cohelp.sleep(executor, 1, MINUTES);
    System.out.println("waking");
}
```

### Timers

```
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

```
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
