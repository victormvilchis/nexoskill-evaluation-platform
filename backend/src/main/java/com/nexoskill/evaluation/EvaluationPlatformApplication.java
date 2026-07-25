package com.nexoskill.evaluation;

import com.nexoskill.evaluation.shared.infrastructure.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class EvaluationPlatformApplication {

	public static void main(String[] args) {
		SpringApplication.run(EvaluationPlatformApplication.class, args);
	}
}
