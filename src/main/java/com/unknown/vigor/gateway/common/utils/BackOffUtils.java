package com.unknown.vigor.gateway.common.utils;

import java.util.Random;

public class BackOffUtils {

    private static final int BASE_DELAY_TIME = 2;

    private static final int MAX_DELAY_TIME = 120;

    private static final float FACTOR = 1.6f;

    private static final float JITTER = 0.2f;

    private static final Random RANDOM = new Random();


    public static int retryTime(int retries) {

        if (retries <= 0) {
            return BASE_DELAY_TIME;
        }

        float backoff = BASE_DELAY_TIME;
        float maxDelay = MAX_DELAY_TIME;

        while (backoff < maxDelay && retries > 0) {
            backoff = backoff * FACTOR;
            retries--;
        }

        if (backoff > maxDelay) {
            backoff = maxDelay;
        }

        backoff *= 1 + JITTER * (RANDOM.nextFloat() * 2 - 1);
        

        if (backoff < 0) {
            return 0;
        }

        return (int) backoff;

    }

}
