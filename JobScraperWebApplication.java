package org.example.jobscraperweb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling  // Add this annotation
public class JobScraperWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobScraperWebApplication.class, args);
    }
}
