package com.pickagent.assistant.web;

/** Stable HTTP error representation. */
public record ApiError(String code, String message) {
}
