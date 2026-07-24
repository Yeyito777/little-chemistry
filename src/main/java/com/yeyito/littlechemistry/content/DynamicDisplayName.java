package com.yeyito.littlechemistry.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Canonicalizes generated player-facing names independently from their lowercase registry identifiers. */
public final class DynamicDisplayName {
	private DynamicDisplayName() {
	}

	public static String normalize(String raw) {
		String value = raw == null ? "" : raw.strip();
		if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
			value = value.substring(1, value.length() - 1).strip();
		}
		if (value.chars().anyMatch(Character::isISOControl)) {
			throw new IllegalArgumentException("Name must contain 1-64 printable characters.");
		}

		List<String> words = new ArrayList<>();
		StringBuilder word = new StringBuilder();
		int previous = -1;
		for (int offset = 0; offset < value.length();) {
			int codePoint = value.codePointAt(offset);
			offset += Character.charCount(codePoint);
			boolean apostrophe = codePoint == '\'' || codePoint == '\u2019';
			if (!Character.isLetterOrDigit(codePoint) && !apostrophe) {
				finishWord(words, word);
				previous = -1;
				continue;
			}
			if (previous >= 0 && Character.isLowerCase(previous) && Character.isUpperCase(codePoint)) {
				finishWord(words, word);
			}
			word.appendCodePoint(codePoint);
			previous = codePoint;
		}
		finishWord(words, word);
		String displayName = String.join(" ", words);
		if (displayName.isBlank() || displayName.length() > 64) {
			throw new IllegalArgumentException("Name must contain 1-64 printable characters.");
		}
		return displayName;
	}

	private static void finishWord(List<String> words, StringBuilder word) {
		if (word.isEmpty()) return;
		String rawWord = word.toString();
		word.setLength(0);
		int firstLetter = rawWord.codePoints().filter(Character::isLetter).findFirst().orElse(-1);
		if (firstLetter < 0) {
			words.add(rawWord);
			return;
		}
		boolean uniformlyCased = rawWord.codePoints().filter(Character::isLetter).allMatch(Character::isLowerCase)
				|| rawWord.codePoints().filter(Character::isLetter).allMatch(Character::isUpperCase);
		String cased = uniformlyCased ? rawWord.toLowerCase(Locale.ROOT) : rawWord;
		int firstOffset = 0;
		while (firstOffset < cased.length()) {
			int codePoint = cased.codePointAt(firstOffset);
			if (Character.isLetter(codePoint)) {
				StringBuilder canonical = new StringBuilder(cased.length());
				canonical.append(cased, 0, firstOffset);
				canonical.appendCodePoint(Character.toTitleCase(codePoint));
				canonical.append(cased, firstOffset + Character.charCount(codePoint), cased.length());
				words.add(canonical.toString());
				return;
			}
			firstOffset += Character.charCount(codePoint);
		}
	}
}
