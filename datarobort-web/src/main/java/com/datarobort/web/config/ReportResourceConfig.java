package com.datarobort.web.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * Serves generated HTML report files from the reports directory.
 * URL pattern: /reports/xxx.html → file ./reports/xxx.html
 */
@Configuration
public class ReportResourceConfig implements WebFluxConfigurer {

    @Value("${datarobort.report-dir:reports}")
    private String reportDirName;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = "file:" + reportDirName + "/";
        registry.addResourceHandler("/reports/**")
                .addResourceLocations(location);
    }
}
