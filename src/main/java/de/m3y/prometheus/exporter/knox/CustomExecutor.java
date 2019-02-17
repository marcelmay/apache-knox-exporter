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

    /**
     * Timed future task.
     * <p>
     * See http://jcip.net/listings/TimingThreadPool.java
     *
     * @param <T> The result type returned by {@code get}
     */
    static class TimedFutureTask<T> extends FutureTask<T> {
        private long startTimeNs;
        private long durationNs;

        public TimedFutureTask(Callable<T> callable) {
            super(callable);
        }

        /**
         * @return duration in nanoseconds.
         */
        public long getDurationNs() {
            return durationNs;
        }

        /**
         * Initializes the start time.
         */
        public void startTimer() {
            this.startTimeNs = System.nanoTime();
        }

        /**
         * Initializes the end time.
         */
        public void stopTimer() {
            durationNs = System.nanoTime() - startTimeNs;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            stopTimer();
            return super.cancel(mayInterruptIfRunning);
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
        final String name = r.toString();
        t.setName(name);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Before execution of timed {}", name);
        }
        if (r instanceof TimedFutureTask) {
            ((TimedFutureTask) r).startTimer();
        }
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        try {
            if (r instanceof TimedFutureTask) {
                ((TimedFutureTask) r).stopTimer();
            } else {
                LOGGER.warn("Runnable not of expected type {} but of type {} for {}",
                        TimedFutureTask.class, r.getClass(), r);
            }
        } finally {
            super.afterExecute(r, t);
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("After execution of timed {}", r);
        }
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        if (callable instanceof CancellableCallable) {
            return ((CancellableCallable<T>) callable).newTask();
        }
        return new TimedFutureTask<>(callable);
    }
}
