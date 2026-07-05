package com.gomesdev.sortifyteams.config;

import java.time.LocalDateTime;

public class SuccessDetails {

	public static class SuccessResponse {
		private String message;
		private LocalDateTime timestamp = LocalDateTime.now();

		public SuccessResponse(String message) {
			this.message = message;
		}

		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			this.message = message;
		}

		public LocalDateTime getTimestamp() {
			return timestamp;
		}

	}
}
