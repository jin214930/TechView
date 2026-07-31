@ApplicationModule(
        allowedDependencies = {"common", "account::api", "question::api"}
)

package com.ward.techview.interview;

import org.springframework.modulith.ApplicationModule;
