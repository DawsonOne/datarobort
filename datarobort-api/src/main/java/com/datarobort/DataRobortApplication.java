package com.datarobort;

import com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceAutoConfigure;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * DataRobort application entry.
 *
 * <p>DruidDataSourceAutoConfigure is excluded on purpose (P5): its wrapper
 * picks up every Filter bean in the context — including our SQL wall filter —
 * and attaches it to the platform datasource, which would block the schema
 * initializer (CREATE TABLE) at startup. The wall filter must guard only the
 * business datasource pools, which DataSourcePoolManager wires explicitly.
 */
@SpringBootApplication(scanBasePackages = "com.datarobort",
        exclude = DruidDataSourceAutoConfigure.class)
public class DataRobortApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataRobortApplication.class, args);
    }
}
