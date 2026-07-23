package com.yeyito.littlechemistry.content;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Shared, client-independent word wrapping for generated-content tooltip prose. */
final class DynamicTooltipText {
	static final int DEFAULT_LINE_LENGTH = 40;

	private DynamicTooltipText() {
	}

	static void appendWrapped(Consumer<Component> builder, String text, ChatFormatting formatting) {
		appendWrapped(builder, text, formatting, Integer.MAX_VALUE);
	}

	static void appendWrapped(Consumer<Component> builder, String text, ChatFormatting formatting, int maximumLines) {
		if (text == null || text.isBlank() || maximumLines <= 0) return;
		List<String> lines = wrap(text, DEFAULT_LINE_LENGTH);
		int count = Math.min(lines.size(), maximumLines);
		for (int index = 0; index < count; index++) {
			String line = lines.get(index);
			if (index == count - 1 && lines.size() > maximumLines) line = ellipsize(line, DEFAULT_LINE_LENGTH);
			builder.accept(Component.literal(line).withStyle(formatting));
		}
	}

	static List<String> wrap(String text, int maximumLineLength) {
		if (text == null) throw new IllegalArgumentException("Tooltip text must not be null");
		if (maximumLineLength < 1) throw new IllegalArgumentException("Tooltip line length must be positive");
		if (text.isBlank()) return List.of();

		List<String> lines = new ArrayList<>();
		String[] paragraphs = text.split("\\R", -1);
		for (int paragraphIndex = 0; paragraphIndex < paragraphs.length; paragraphIndex++) {
			String paragraph = paragraphs[paragraphIndex].trim();
			if (paragraph.isEmpty()) {
				if (!lines.isEmpty() && paragraphIndex < paragraphs.length - 1) lines.add("");
				continue;
			}

			StringBuilder current = new StringBuilder();
			for (String originalWord : paragraph.split("\\s+")) {
				String word = originalWord;
				while (codePoints(word) > maximumLineLength) {
					if (!current.isEmpty()) {
						lines.add(current.toString());
						current.setLength(0);
					}
					int boundary = word.offsetByCodePoints(0, maximumLineLength);
					lines.add(word.substring(0, boundary));
					word = word.substring(boundary);
				}
				if (word.isEmpty()) continue;
				int proposedLength = codePoints(current) + (current.isEmpty() ? 0 : 1) + codePoints(word);
				if (!current.isEmpty() && proposedLength > maximumLineLength) {
					lines.add(current.toString());
					current.setLength(0);
				}
				if (!current.isEmpty()) current.append(' ');
				current.append(word);
			}
			if (!current.isEmpty()) lines.add(current.toString());
		}
		return List.copyOf(lines);
	}

	private static String ellipsize(String line, int maximumLineLength) {
		if (codePoints(line) < maximumLineLength) return line + "…";
		int retained = Math.max(0, maximumLineLength - 1);
		return line.substring(0, line.offsetByCodePoints(0, retained)) + "…";
	}

	private static int codePoints(CharSequence value) {
		return Character.codePointCount(value, 0, value.length());
	}
}
