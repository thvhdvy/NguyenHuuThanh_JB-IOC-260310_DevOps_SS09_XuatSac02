package com.quickbite.user_service.exception;

import java.time.Instant;

/** Cấu trúc lỗi chung của mọi service: {timestamp, status, error, message, path}. */
public record ApiError(Instant timestamp, int status, String error, String message, String path) {
}
