package com.iknow.android.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Local implementations of the small utility surface used by the video editor. */
public final class VideoUtils {
    private VideoUtils() {}

    public static final class BaseUtils {
        private static Context application;
        public static void init(Context context) {
            application = context.getApplicationContext();
        }
        private static Context context() {
            if (application == null) throw new IllegalStateException("Application not initialized");
            return application;
        }
    }

    public static final class DeviceUtil {
        public static int getDeviceWidth() {
            return BaseUtils.context().getResources().getDisplayMetrics().widthPixels;
        }
    }

    public static final class UnitConverter {
        public static int dpToPx(int dp) {
            return Math.round(dp * BaseUtils.context().getResources().getDisplayMetrics().density);
        }
    }

    public static final class DateUtil {
        public static String convertSecondsToTime(long seconds) {
            seconds = Math.max(0, seconds);
            return seconds < 3600
                    ? String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
                    : String.format(Locale.US, "%02d:%02d:%02d", seconds / 3600,
                            seconds / 60 % 60, seconds % 60);
        }
    }

    public interface SingleCallback<T, R> {
        void onSingleCallback(T value, R detail);
    }

    public static final class BackgroundExecutor {
        // Thumbnail extraction is serialized to avoid concurrent decoder allocations.
        private static final ScheduledThreadPoolExecutor EXECUTOR = new ScheduledThreadPoolExecutor(1);
        private static final Map<String, List<Future<?>>> TASKS = new HashMap<>();

        public abstract static class Task implements Runnable {
            private final String id;
            private final long delay;
            public Task(String id, long delay, String serial) {
                this.id = id;
                this.delay = Math.max(0, delay);
            }
            public abstract void execute();
            @Override public final void run() { execute(); }
        }

        public static synchronized void execute(Task task) {
            Iterator<List<Future<?>>> groups = TASKS.values().iterator();
            while (groups.hasNext()) {
                List<Future<?>> futures = groups.next();
                Iterator<Future<?>> iterator = futures.iterator();
                while (iterator.hasNext()) if (iterator.next().isDone()) iterator.remove();
                if (futures.isEmpty()) groups.remove();
            }
            List<Future<?>> futures = TASKS.get(task.id);
            if (futures == null) {
                futures = new ArrayList<>();
                TASKS.put(task.id, futures);
            }
            futures.add(EXECUTOR.schedule(task, task.delay, TimeUnit.MILLISECONDS));
        }

        public static synchronized void cancelAll(String id, boolean interrupt) {
            List<Future<?>> futures = TASKS.remove(id);
            if (futures != null) for (Future<?> future : futures) future.cancel(interrupt);
            EXECUTOR.purge();
        }
    }

    public static final class UiThreadExecutor {
        private static final Handler HANDLER = new Handler(Looper.getMainLooper());
        public static void runTask(String id, Runnable task, long delay) {
            HANDLER.postAtTime(task, id.intern(), SystemClock.uptimeMillis() + Math.max(0, delay));
        }
        public static void cancelAll(String id) {
            HANDLER.removeCallbacksAndMessages(id.intern());
        }
    }
}
