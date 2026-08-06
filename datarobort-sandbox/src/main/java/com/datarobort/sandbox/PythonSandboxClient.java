package com.datarobort.sandbox;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Docker-based Python execution sandbox, driven by the docker CLI.
 *
 * <p>Why the CLI instead of a Java Docker client: the CLI speaks to the
 * daemon over the exact channel the host already configured (Windows named
 * pipe / Linux unix socket / TCP), so there is zero transport setup and no
 * native dependency. The interface of this class stays stable, so the
 * transport can be swapped later without touching callers.
 *
 * <p>Security defaults: --network none, 512MB memory, 1 CPU, hard timeout
 * with forced kill; the container is created with --rm so nothing is left
 * behind.
 */
@Slf4j
public class PythonSandboxClient implements AutoCloseable {

    private static final String MEMORY_LIMIT = "512m";
    private static final String CPU_LIMIT = "1";

    /** Result of one sandbox execution. */
    public record SandboxResult(boolean timeout, long exitCode, String stdout, String stderr,
                                long elapsedMs, String containerId) {
    }

    /**
     * Runs a Python snippet in a fresh, isolated container.
     *
     * @param image   sandbox image, e.g. python:3.12-slim
     * @param code    python source, piped to {@code python} over stdin
     * @param timeout hard limit; the container is killed when exceeded
     */
    public SandboxResult runPython(String image, String code, Duration timeout) {
        ensureImage(image);
        String name = "datarobort-sandbox-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long start = System.nanoTime();

        ProcessBuilder pb = new ProcessBuilder(
                "docker", "run", "--rm",
                "--name", name,
                "--network", "none",
                "--memory", MEMORY_LIMIT,
                "--cpus", CPU_LIMIT,
                "-i", image,
                "python", "-");
        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new IllegalStateException("failed to start docker CLI, is docker on PATH?", e);
        }

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        Thread outGobbler = gobble(process.getInputStream(), stdout);
        Thread errGobbler = gobble(process.getErrorStream(), stderr);

        try {
            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(code.getBytes(StandardCharsets.UTF_8));
                stdin.flush();
            }
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                killByName(name);
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                joinQuietly(outGobbler);
                joinQuietly(errGobbler);
                return new SandboxResult(true, -1, stdout.toString(), stderr.toString(),
                        elapsedMs(start), name);
            }
            joinQuietly(outGobbler);
            joinQuietly(errGobbler);
            return new SandboxResult(false, process.exitValue(), stdout.toString(), stderr.toString(),
                    elapsedMs(start), name);
        } catch (IOException e) {
            throw new IllegalStateException("failed to feed code to sandbox container", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            killByName(name);
            throw new IllegalStateException("interrupted while waiting for sandbox container", e);
        }
    }

    private void ensureImage(String image) {
        try {
            Process inspect = new ProcessBuilder("docker", "image", "inspect", image)
                    .redirectErrorStream(true).start();
            if (inspect.waitFor(30, TimeUnit.SECONDS) && inspect.exitValue() == 0) {
                return;
            }
            log.info("sandbox image {} not found locally, pulling...", image);
            Process pull = new ProcessBuilder("docker", "pull", image).inheritIO().start();
            if (!pull.waitFor(5, TimeUnit.MINUTES)) {
                pull.destroyForcibly();
                throw new IllegalStateException("timeout while pulling sandbox image " + image);
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to inspect/pull sandbox image " + image, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while preparing sandbox image", e);
        }
    }

    private void killByName(String name) {
        try {
            Process kill = new ProcessBuilder("docker", "kill", name).redirectErrorStream(true).start();
            kill.waitFor(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("failed to kill sandbox container {}: {}", name, e.getMessage());
        }
    }

    private Thread gobble(InputStream in, StringBuilder sink) {
        Thread t = new Thread(() -> {
            try {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) != -1) {
                    synchronized (sink) {
                        sink.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                    }
                }
            } catch (IOException ignored) {
                // stream closed
            }
        });
        t.setDaemon(true);
        t.start();
        return t;
    }

    private void joinQuietly(Thread t) {
        try {
            t.join(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private long elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    @Override
    public void close() {
        // nothing to release: every container runs with --rm
    }
}
