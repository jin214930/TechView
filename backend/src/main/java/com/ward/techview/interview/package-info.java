@ApplicationModule(
        allowedDependencies = {"common", "account::api", "question::api", "processing::api"}
)

package com.ward.techview.interview;

import org.springframework.modulith.ApplicationModule;
