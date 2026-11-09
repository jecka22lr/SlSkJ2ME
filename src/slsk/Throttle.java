package slsk;

/** Shared speed limit for all uploads (or all downloads). 0 = unlimited. */
final class Throttle {
    volatile int kbps;
    private long t0;
    private long bytes;

    /** Call after moving n bytes; sleeps if we're ahead of the limit. */
    void took(int n) {
        int k = kbps;
        if (k <= 0) return;
        long wait;
        synchronized (this) {
            long now = System.currentTimeMillis();
            if (now - t0 > 3000 || bytes == 0) { t0 = now; bytes = 0; }
            bytes += n;
            wait = t0 + bytes * 1000L / (k * 1024L) - now;
        }
        if (wait > 0) {
            try { Thread.sleep(Math.min(wait, 2000)); } catch (InterruptedException e) {}
        }
    }
}
