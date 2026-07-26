package com.nexoskill.evaluation.shared.infrastructure.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

	private final Security security = new Security();
	private final Seed seed = new Seed();

	public Security getSecurity() {
		return security;
	}

	public Seed getSeed() {
		return seed;
	}

	public static class Security {
		private String cookieName = "EVSESSION";
		private String studentCookieName = "EVSTUDENT";
		private Duration sessionDuration = Duration.ofHours(8);
		private boolean cookieSecure;
		private List<String> allowedOrigins = new ArrayList<>();
		private int maxFailedAttempts = 5;
		private Duration lockDuration = Duration.ofMinutes(15);
		private Duration temporaryPasswordDuration = Duration.ofDays(7);
		private int passwordHistorySize = 5;

		public String getCookieName() {
			return cookieName;
		}

		public String getStudentCookieName() {
			return studentCookieName;
		}

		public void setStudentCookieName(String studentCookieName) {
			this.studentCookieName = studentCookieName;
		}

		public void setCookieName(String cookieName) {
			this.cookieName = cookieName;
		}

		public Duration getSessionDuration() {
			return sessionDuration;
		}

		public void setSessionDuration(Duration sessionDuration) {
			this.sessionDuration = sessionDuration;
		}

		public boolean isCookieSecure() {
			return cookieSecure;
		}

		public void setCookieSecure(boolean cookieSecure) {
			this.cookieSecure = cookieSecure;
		}

		public List<String> getAllowedOrigins() {
			return allowedOrigins;
		}

		public void setAllowedOrigins(List<String> allowedOrigins) {
			this.allowedOrigins = allowedOrigins;
		}

		public int getMaxFailedAttempts() {
			return maxFailedAttempts;
		}

		public void setMaxFailedAttempts(int maxFailedAttempts) {
			this.maxFailedAttempts = maxFailedAttempts;
		}

		public Duration getLockDuration() {
			return lockDuration;
		}

		public void setLockDuration(Duration lockDuration) {
			this.lockDuration = lockDuration;
		}

		public Duration getTemporaryPasswordDuration() {
			return temporaryPasswordDuration;
		}

		public void setTemporaryPasswordDuration(Duration temporaryPasswordDuration) {
			this.temporaryPasswordDuration = temporaryPasswordDuration;
		}

		public int getPasswordHistorySize() {
			return passwordHistorySize;
		}

		public void setPasswordHistorySize(int passwordHistorySize) {
			this.passwordHistorySize = passwordHistorySize;
		}
	}

	public static class Seed {
		private boolean enabled;
		private String adminEmail;
		private String adminPassword;
		private String userEmail;
		private String userPassword;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String getAdminEmail() {
			return adminEmail;
		}

		public void setAdminEmail(String adminEmail) {
			this.adminEmail = adminEmail;
		}

		public String getAdminPassword() {
			return adminPassword;
		}

		public void setAdminPassword(String adminPassword) {
			this.adminPassword = adminPassword;
		}

		public String getUserEmail() {
			return userEmail;
		}

		public void setUserEmail(String userEmail) {
			this.userEmail = userEmail;
		}

		public String getUserPassword() {
			return userPassword;
		}

		public void setUserPassword(String userPassword) {
			this.userPassword = userPassword;
		}
	}
}
