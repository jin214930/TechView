package com.ward.techview;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithStructureTests {

    @Test
    void verifyModuleBoundaries() {
        ApplicationModules.of(TechViewApplication.class).verify();
    }
}
