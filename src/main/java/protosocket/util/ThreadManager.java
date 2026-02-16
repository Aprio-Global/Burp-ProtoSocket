package protosocket.util;

import burp.api.montoya.MontoyaApi;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages background threads for async protobuf decoding operations.
 * Ensures clean shutdown when extension unloads.
 */
public class ThreadManager {
    private final ExecutorService executor;
    private final MontoyaApi api;

    public ThreadManager(MontoyaApi api) {
        this.api = api;
        this.executor = Executors.newFixedThreadPool(4, new DaemonThreadFactory());
        api.logging().logToOutput("ThreadManager initialized with 4 worker threads");
    }

    /**
     * Submit a task to run in a background thread.
     */
    public void submit(Runnable task) {
        executor.submit(() -> {
            try {
                task.run();
            } catch (Exception e) {
                api.logging().logToError("Background task failed: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Gracefully shutdown the thread pool.
     * Called when the extension is unloaded.
     */
    public void shutdown() {
        api.logging().logToOutput("Shutting down thread pool...");
        executor.shutdown();

        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                api.logging().logToOutput("Thread pool did not terminate gracefully, forcing shutdown");
                executor.shutdownNow();

                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    api.logging().logToError("Thread pool did not terminate after forced shutdown");
                }
            } else {
                api.logging().logToOutput("Thread pool shut down successfully");
            }
        } catch (InterruptedException e) {
            api.logging().logToError("Thread pool shutdown interrupted");
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Custom ThreadFactory that creates daemon threads.
     * Daemon threads don't prevent JVM shutdown.
     */
    private static class DaemonThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "ProtoSocket-Worker-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
