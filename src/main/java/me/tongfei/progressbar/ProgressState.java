package me.tongfei.progressbar;

import java.time.Instant;
import java.util.function.Supplier;

/**
 * Encapsulates the internal states of a progress bar.
 * @author Tongfei Chen
 * @since 0.5.0
 */
class ProgressState {

    String task;
    private long current = 0;
    boolean indefinite = false;
    long max = 0;
    Instant startTime = null;
    String extraMessage = "";

    private Supplier<Long> supplier;


    ProgressState(String task, long initialMax) {
        this.task = task;
        this.max = initialMax;
        if (initialMax < 0) indefinite = true;
    }

    public void bindCurrentTo(Supplier<Long> supplier) {
        this.supplier = supplier;
    }

    synchronized void setAsDefinite() {
        indefinite = false;
    }

    synchronized void setAsIndefinite() {
        indefinite = true;
    }

    synchronized void maxHint(long n) {
        max = n;
    }

    synchronized void stepBy(long n) {
        current += n;
        if (current > max) max = current;
    }

    synchronized void stepTo(long n) {
        current = n;
        if (current > max) max = current;
    }

    synchronized void setExtraMessage(String msg) {
        extraMessage = msg;
    }

    String getTask() {
        return task;
    }

    synchronized String getExtraMessage() {
        return extraMessage;
    }

    public synchronized long getCurrent() {
        if (supplier == null) {
            return current;
        } else {
            return supplier.get();
        }
    }

    public synchronized boolean isFinished() {
        return getCurrent() >= max;
    }

    synchronized long getMax() {
        return max;
    }

}
