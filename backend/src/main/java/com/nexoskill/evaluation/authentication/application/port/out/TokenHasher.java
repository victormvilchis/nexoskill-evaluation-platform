package com.nexoskill.evaluation.authentication.application.port.out;

public interface TokenHasher {

    String hash(String rawToken);
}
