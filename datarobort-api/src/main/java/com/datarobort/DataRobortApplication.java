package com.datarobort;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * DataRobort application entry.
 */
@SpringBootApplication(scanBasePackages = "com.datarobort")
public class DataRobortApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataRobortApplication.class, args);
    }
}
