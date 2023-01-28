package kwyyeung.autoboost.helper;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.*;

public class PUTExecutor {
    private static final PUTExecutor singleton = new PUTExecutor();
    private final ExecutorService executor = Executors.newCachedThreadPool();;

    public static PUTExecutor getSingleton() {
        return singleton;
    }

    public Object callMethodWithTimeout(Callable<Object> task) throws InvocationTargetException {
        Future<Object> future = executor.submit(task);
        try {
            Object result = future.get(5, TimeUnit.SECONDS);
            return result;
        } catch (Exception ex) {
            // handle the timeout
            throw new InvocationTargetException(ex, "Method timeout");
        } finally {
            future.cancel(true); // may or may not desire this
        }
    }


    public void shutdown() {
        executor.shutdownNow();
        if(!executor.isShutdown())
            executor.shutdownNow();
    }
}
