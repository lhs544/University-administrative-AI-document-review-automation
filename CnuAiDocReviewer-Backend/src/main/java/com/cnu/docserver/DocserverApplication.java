package com.cnu.docserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class DocserverApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocserverApplication.class, args);
    }

}
