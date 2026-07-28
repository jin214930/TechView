package com.ward.techview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic(sharedModules = "common")
@SpringBootApplication
public class TechViewApplication {

    public static void main(String[] args) {
        SpringApplication.run(TechViewApplication.class, args);
    }

}
