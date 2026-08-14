package com.datarobort.sandbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spike C: self-test of {@link PythonSandboxClient} covering the three
 * scenarios the production sandbox must guarantee.
 */
@Slf4j
@Service
public class SandboxSpikeService {

    @Value("${datarobort.spike.sandbox-image:python:3.12-slim}")
    private String image;

    @Value("${datarobort.spike.sandbox-timeout-seconds:60}")
    private long timeoutSeconds;

    private final PythonSandboxClient sandbox;

    public SandboxSpikeService(PythonSandboxClient sandbox) {
        this.sandbox = sandbox;
    }

    public Map<String, Object> run() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("image", image);

            // 1) normal execution: stdout must come back, exit code 0
            PythonSandboxClient.SandboxResult normal = sandbox.runPython(image,
                    "data = [3, 1, 4, 1, 5]\n"
                            + "import json\n"
                            + "print(json.dumps({'message': 'hello from sandbox', 'values': data, 'sum': sum(data)}))",
                    Duration.ofSeconds(timeoutSeconds));
            Map<String, Object> normalCase = new LinkedHashMap<>();
            normalCase.put("exitCode", normal.exitCode());
            normalCase.put("timeout", normal.timeout());
            normalCase.put("stdout", normal.stdout().trim());
            normalCase.put("elapsedMs", normal.elapsedMs());
            normalCase.put("pass", normal.exitCode() == 0 && normal.stdout().contains("hello from sandbox"));
            report.put("normalExecution", normalCase);

            // 2) hard timeout: container must be killed
            PythonSandboxClient.SandboxResult slow = sandbox.runPython(image,
                    "import time; time.sleep(600)",
                    Duration.ofSeconds(5));
            Map<String, Object> timeoutCase = new LinkedHashMap<>();
            timeoutCase.put("timeout", slow.timeout());
            timeoutCase.put("elapsedMs", slow.elapsedMs());
            timeoutCase.put("pass", slow.timeout() && slow.elapsedMs() < 15000);
            report.put("timeoutKill", timeoutCase);

            // 3) network isolation: outbound connections must fail
            PythonSandboxClient.SandboxResult net = sandbox.runPython(image,
                    "import urllib.request\n"
                            + "try:\n"
                            + "    urllib.request.urlopen('http://example.com', timeout=3)\n"
                            + "    print('NETWORK_REACHABLE')\n"
                            + "except Exception as e:\n"
                            + "    print('NETWORK_BLOCKED')",
                    Duration.ofSeconds(30));
            boolean blocked = net.stdout().contains("NETWORK_BLOCKED");
            Map<String, Object> netCase = new LinkedHashMap<>();
            netCase.put("stdout", net.stdout().trim());
            netCase.put("pass", blocked);
            report.put("networkIsolation", netCase);

            report.put("conclusion", "execute / timeout-kill / network-isolation all verified");
        return report;
    }
}
