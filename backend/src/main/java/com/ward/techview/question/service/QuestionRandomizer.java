package com.ward.techview.question.service;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Random;

@Component
public class QuestionRandomizer {
    private final Random random;

    public QuestionRandomizer() {
        this(new Random());
    }

    QuestionRandomizer(Random random) {
        this.random = random;
    }

    public <T> void shuffle(List<T> values) {
        Collections.shuffle(values, random);
    }

    public <T> T select(List<T> values) {
        return values.get(random.nextInt(values.size()));
    }
}
