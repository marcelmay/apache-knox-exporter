package de.m3y.prometheus.exporter.knox;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomExecutorTest {
    private static final Logger LOG = LoggerFactory.getLogger(CustomExecutorTest.class);

    @Test
    public void testDuration() throws InterruptedException {
        CustomExecutor executor = new CustomExecutor();

        Collection<? extends Callable<Object>> actions = Collections.singletonList(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                LOG.info("Sleeping ...");
                Thread.sleep(100);
                LOG.info("... awake again!");
                return null;
            }
        });
        final List<Future<Object>> futures = executor.invokeAll(actions);
        assertThat(futures.size()).isEqualTo(1);
        final Future<Object> future = futures.get(0);
        assertThat(future).isInstanceOf(CustomExecutor.TimedFutureTask.class);
        CustomExecutor.TimedFutureTask timedFutureTask = (CustomExecutor.TimedFutureTask) future;
        assertThat(timedFutureTask.getDurationNs() / 1000.0 / 1000.0).isGreaterThan(100).isLessThan(110 /*20% tolerance*/);

        executor.shutdownNow();
    }

    class BlockingTestCallable implements CustomExecutor.CancellableCallable<Object> {
        final ServerSocket serverSocket;

        BlockingTestCallable() throws IOException {
            serverSocket = new ServerSocket(9999, 1, InetAddress.getLocalHost());
        }

        @Override
        public void cancel() {
            LOG.info("Cancel ...");
            try {
                serverSocket.close();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public RunnableFuture newTask() {
            return new CustomExecutor.TimedFutureTask(this) {
                @Override
                public boolean cancel(boolean mayInterruptIfRunning) {
                    try {
                        BlockingTestCallable.this.cancel();
                    } finally {
                        return super.cancel(mayInterruptIfRunning);
                    }
                }
            };
        }

        @Override
        public Object call() throws Exception {
            LOG.info("Blocking ...");
            return serverSocket.accept(); // Block!
        }
    }

    @Test
    public void testCancelling() throws InterruptedException, IOException {
        CustomExecutor executor = new CustomExecutor();

        Collection<? extends Callable<Object>> actions = Collections.singletonList(new BlockingTestCallable());

        final List<Future<Object>> futures = executor.invokeAll(actions, 100, TimeUnit.MILLISECONDS);
        assertThat(futures.size()).isEqualTo(1);
        final Future<Object> future = futures.get(0);
        assertThat(future).isInstanceOf(CustomExecutor.TimedFutureTask.class);
        CustomExecutor.TimedFutureTask timedFutureTask = (CustomExecutor.TimedFutureTask) future;
        assertThat(timedFutureTask.isCancelled()).isTrue();
        assertThat(timedFutureTask.getDurationNs() / 1000.0 / 1000.0).isGreaterThan(100).isLessThan(120 /* 20% tolerance */);

        executor.shutdownNow();
    }
}
