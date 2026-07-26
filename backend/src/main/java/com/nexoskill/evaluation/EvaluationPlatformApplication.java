package com.nexoskill.evaluation;

import com.nexoskill.evaluation.shared.infrastructure.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
@EnableScheduling
public class EvaluationPlatformApplication {

	public static void main(String[] args) {
		SpringApplication.run(EvaluationPlatformApplication.class, args);
	}
}
