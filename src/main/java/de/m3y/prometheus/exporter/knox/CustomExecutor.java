package de.m3y.prometheus.exporter.knox;

import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Customized thread pool executor supporting timing tasks and cancelling blocking tasks.
 * <p>
 * See http://jcip.net/listings/TimingThreadPool.java and http://jcip.net/listings/SocketUsingTask.java
 */
class CustomExecutor extends ThreadPoolExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomExecutor.class);
    private final ThreadLocal<Long> startTime = new ThreadLocal<>();

    /**
     * Timed future task.
     * <p>
     * See http://jcip.net/listings/TimingThreadPool.java
     *
     * @param <T> The result type returned by {@code get}
     */
    static class TimedFutureTask<T> extends FutureTask<T> {
        private long duration;

        public TimedFutureTask(Callable<T> callable) {
            super(callable);
        }

        /**
         * @return duration in nanoseconds.
         */
        public long getDuration() {
            return duration;
        }

        public void setDuration(long duration) {
            this.duration = duration;
        }
    }

    /**
     * See http://jcip.net/listings/SocketUsingTask.java
     *
     * @param <T> the result type of method {@code call}
     */
    interface CancellableCallable<T> extends Callable<T> {
        /**
         * Cancel ongoing operation, eg by closing a socket.
         */
        void cancel();

        /**
         * @return
         */
        RunnableFuture<T> newTask();
    }

    public CustomExecutor() {
        super(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        super.beforeExecute(t, r);
        t.setName(r.toString());
        startTime.set(System.nanoTime());
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        try {
            long endTime = System.nanoTime();
            long taskTime = endTime - startTime.get();
            if (r instanceof TimedFutureTask) {
                ((TimedFutureTask) r).setDuration(taskTime);
            } else {
                LOGGER.warn("Runnable not of expected type {} but of type {}",
                        TimedFutureTask.class, r.getClass());
            }
        } finally {
            super.afterExecute(r, t);
        }
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        if (callable instanceof CancellableCallable) {
            return ((CancellableCallable) callable).newTask();
        }
        return new TimedFutureTask<>(callable);
    }
}
