package com.salary.management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableAutoConfiguration
public class SalaryManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(SalaryManagementApplication.class, args);
    }
}