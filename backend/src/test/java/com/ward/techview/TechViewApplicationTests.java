package com.ward.techview;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.modulith.core.ApplicationModules;

@SpringBootTest
class TechViewApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void verifyModuleBoundaries() {
        ApplicationModules.of(TechViewApplication.class).verify();
    }
}
