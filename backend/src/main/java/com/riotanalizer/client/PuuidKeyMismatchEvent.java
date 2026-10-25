package com.riotanalizer.client;

/**
 * Published by {@link RiotApiClient} whenever Riot answers 400 "Exception
 * decrypting": a stored PUUID/summonerId was issued for another API key's
 * application, so the configured key has most likely changed application.
 */
public record PuuidKeyMismatchEvent(String riotMessage) {
}
