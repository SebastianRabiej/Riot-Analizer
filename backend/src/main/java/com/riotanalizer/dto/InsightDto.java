package com.riotanalizer.dto;

/**
 * A single plain-language finding for the Insights view. {@code sentiment} is
 * one of "good" | "bad" | "neutral" (drives colour). {@code linkKind} and
 * {@code linkValue} tell the UI where a click should drill to:
 * <ul>
 *   <li>{@code vs-player}  / value = "gameName#tagLine"</li>
 *   <li>{@code with-player}/ value = "gameName#tagLine"</li>
 *   <li>{@code vs-champion}/ value = champion name</li>
 *   <li>{@code trends}     / value = a tab hint ("roles" | "time")</li>
 * </ul>
 * Both link fields may be null for a non-navigable insight.
 */
public record InsightDto(
        String kind,
        String icon,
        String title,
        String detail,
        String sentiment,
        String linkKind,
        String linkValue
) {}
