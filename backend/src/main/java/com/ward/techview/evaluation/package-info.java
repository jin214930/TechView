@ApplicationModule(
        allowedDependencies = {"common", "interview :: api", "processing :: api"}
)

package com.ward.techview.evaluation;

import org.springframework.modulith.ApplicationModule;