package com.riotanalizer.dto;

/** One plain-language reason behind the queue advice, with a tone for styling. */
public record ReasonDto(
        String text,
        String tone // "good" | "bad" | "neutral"
) {}
