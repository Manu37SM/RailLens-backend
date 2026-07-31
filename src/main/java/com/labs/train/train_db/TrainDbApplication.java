package com.labs.train.train_db;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

// EnableScheduling powers RefreshTokenCleanupTask's daily @Scheduled purge
// of revoked/expired refresh_tokens rows - nothing else in the app uses
// @Scheduled yet, so this wasn't needed before.
@SpringBootApplication
@EnableCaching
@EnableScheduling
public class TrainDbApplication {

	public static void main(String[] args) {
		SpringApplication.run(TrainDbApplication.class, args);
	}

}
