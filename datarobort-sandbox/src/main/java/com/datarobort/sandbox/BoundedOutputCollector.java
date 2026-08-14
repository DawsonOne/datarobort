package com.datarobort.sandbox;

/**
 * Bounded sink for sandbox stdout/stderr.
 *
 * <p>Without a cap, malicious (or buggy) sandbox code could print
 * indefinitely and exhaust the host JVM heap — the container's 512MB memory
 * limit does not bound the rate at which output is piped out of it. Once the
 * cap is reached the collector truncates and appends a marker; the remaining
 * stream is drained and discarded.
 */
public final class BoundedOutputCollector {

    private final int maxChars;
    private final StringBuilder sb = new StringBuilder();
    private boolean truncated = false;

    public BoundedOutputCollector(int maxChars) {
        this.maxChars = maxChars;
    }

    public synchronized void append(String s) {
        if (truncated || s == null) {
            return;
        }
        int room = maxChars - sb.length();
        if (s.length() <= room) {
            sb.append(s);
        } else {
            if (room > 0) {
                sb.append(s, 0, room);
            }
            truncated = true;
            sb.append("\n[output truncated]");
        }
    }

    public synchronized boolean isTruncated() {
        return truncated;
    }

    public synchronized String content() {
        return sb.toString();
    }
}
