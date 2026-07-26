package com.nexoskill.evaluation.users.application.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class SecureTemporaryPasswordGenerator {
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "!@#$%&*+-_?";
    private static final String ALL = UPPER + LOWER + DIGITS + SYMBOLS;
    private static final int LENGTH = 10;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        char[] password = new char[LENGTH];
        password[0] = randomCharacter(UPPER);
        password[1] = randomCharacter(LOWER);
        password[2] = randomCharacter(DIGITS);
        password[3] = randomCharacter(SYMBOLS);
        for (int index = 4; index < password.length; index++) {
            password[index] = randomCharacter(ALL);
        }
        for (int index = password.length - 1; index > 0; index--) {
            int swapIndex = random.nextInt(index + 1);
            char value = password[index];
            password[index] = password[swapIndex];
            password[swapIndex] = value;
        }
        return new String(password);
    }

    private char randomCharacter(String source) {
        return source.charAt(random.nextInt(source.length()));
    }
}
