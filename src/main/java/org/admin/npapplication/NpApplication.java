package org.admin.npapplication;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NpApplication {

    public static void main(String[] args) {
        SpringApplication.run(NpApplication.class, args);
    }

}
