package com.yeyito.littlechemistry.behavior;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Source inspection and migration for generated behavior classes. */
public final class DynamicBehaviorSource {
	private static final String CLASS_NAME = DynamicBehaviorCompiler.GENERATED_CLASS_NAME;
	private static final String MARKER_SOURCE = "public final class " + CLASS_NAME
			+ " implements com.yeyito.littlechemistry.behavior.DynamicBehavior { public "
			+ CLASS_NAME + "() {} }";
	private static final Pattern CLASS_DECLARATION = Pattern.compile(
			"\\bclass\\s+" + Pattern.quote(CLASS_NAME) + "\\b");
	private static final Pattern OVERRIDE_SUFFIX = Pattern.compile("@(?:java\\.lang\\.)?Override\\s*$");
	private static final Pattern PASS_RETURN = Pattern.compile(
			"return(?:net\\.minecraft\\.world\\.)?InteractionResult\\.PASS;");
	private static final Pattern IDENTIFIER_RETURN = Pattern.compile("return([A-Za-z_$][A-Za-z0-9_$]*);");
	private static final Pattern DYNAMIC_PARTICLE_SPAWN = Pattern.compile(
			"\\bDynamicParticles\\s*\\.\\s*spawn\\s*\\(");
	private static final Pattern STATIC_DYNAMIC_PARTICLE_IMPORT = Pattern.compile(
			"\\bimport\\s+static\\s+com\\.yeyito\\.littlechemistry\\.particle\\.DynamicParticles\\.spawn\\s*;");
	private static final Pattern DIRECT_DYNAMIC_PARTICLE_PACKET = Pattern.compile(
			"\\b(?:DynamicParticleOptions|DynamicParticleRegistry)\\b");
	private static final Pattern PARTICLE_ID_LITERAL = Pattern.compile("\"([a-z][a-z0-9_]{0,31})\"");
	private static final ConcurrentHashMap<String, Set<DynamicBehaviorCapability>> CAPABILITY_CACHE =
			new ConcurrentHashMap<>();

	private DynamicBehaviorSource() {
	}

	/** Creates or upgrades behavior source persisted by versions whose base interface supplied callback defaults. */
	public static String completeLegacySource(String source) {
		return migrateMonolithicSource(source == null || source.isBlank() ? MARKER_SOURCE : source);
	}

	/**
	 * Converts the former all-callback interface into explicit opt-in callback interfaces. Neutral methods inserted by
	 * the old generator are removed, while methods containing actual behavior are preserved and assigned a capability.
	 */
	public static String migrateMonolithicSource(String source) {
		if (source == null || source.isBlank()) return MARKER_SOURCE;
		String normalized = source.strip();
		SourceClass sourceClass = locateClass(normalized);
		if (!declaredCapabilities(normalized, sourceClass).isEmpty()) return normalized;

		String masked = maskNonCode(normalized);
		List<MethodRegion> neutralMethods = new ArrayList<>();
		EnumSet<DynamicBehaviorCapability> implemented = EnumSet.noneOf(DynamicBehaviorCapability.class);
		for (DynamicBehaviorCapability capability : DynamicBehaviorCapability.values()) {
			MethodRegion method = findTopLevelMethod(masked, sourceClass, capability.callbackName());
			if (method == null) continue;
			if (isNeutral(masked, method, capability)) neutralMethods.add(method);
			else implemented.add(capability);
		}

		String migrated = normalized;
		neutralMethods.sort((left, right) -> Integer.compare(right.start(), left.start()));
		for (MethodRegion method : neutralMethods) {
			migrated = migrated.substring(0, method.start()) + migrated.substring(method.end());
		}
		if (!implemented.isEmpty()) {
			SourceClass migratedClass = locateClass(migrated);
			StringBuilder interfaces = new StringBuilder();
			for (DynamicBehaviorCapability capability : implemented) {
				interfaces.append(", ").append(capability.qualifiedInterfaceName());
			}
			migrated = migrated.substring(0, migratedClass.openingBrace()) + interfaces
					+ " " + migrated.substring(migratedClass.openingBrace());
		}
		CAPABILITY_CACHE.remove(normalized);
		return migrated.strip();
	}

	/** Returns the callback capabilities explicitly selected in the generated class declaration. */
	public static Set<DynamicBehaviorCapability> capabilities(String source) {
		if (source == null || source.isBlank()) return Set.of();
		return CAPABILITY_CACHE.computeIfAbsent(source, DynamicBehaviorSource::inspectCapabilities);
	}

	public static boolean supports(String source, DynamicBehaviorCapability capability) {
		return capabilities(source).contains(capability);
	}

	/**
	 * Returns and validates local particle IDs used by generated calls to {@code DynamicParticles.spawn}.
	 * Requiring a literal ID makes draft validation deterministic when particle definitions are replaced.
	 */
	public static List<String> referencedCustomParticleIds(String source) {
		if (source == null || source.isBlank()) return List.of();
		String masked = maskNonCode(source);
		if (STATIC_DYNAMIC_PARTICLE_IMPORT.matcher(masked).find()) {
			throw new IllegalArgumentException(
					"Call custom particles through DynamicParticles.spawn instead of a static import");
		}
		if (DIRECT_DYNAMIC_PARTICLE_PACKET.matcher(masked).find()) {
			throw new IllegalArgumentException(
					"Generated behavior must emit custom particles through the budgeted DynamicParticles.spawn API");
		}
		List<String> ids = new ArrayList<>();
		Matcher calls = DYNAMIC_PARTICLE_SPAWN.matcher(masked);
		while (calls.find()) {
			int openingParenthesis = masked.indexOf('(', calls.start());
			int closingParenthesis = matchingDelimiter(masked, openingParenthesis, '(', ')');
			if (closingParenthesis < 0) {
				throw new IllegalArgumentException("DynamicParticles.spawn call is incomplete");
			}
			List<ArgumentRegion> arguments = argumentRegions(masked, openingParenthesis, closingParenthesis);
			if (arguments.size() < 3) {
				throw new IllegalArgumentException("DynamicParticles.spawn requires a particle ID argument");
			}
			ArgumentRegion particleId = arguments.get(2);
			Matcher literal = PARTICLE_ID_LITERAL.matcher(
					source.substring(particleId.start(), particleId.end()).strip());
			if (!literal.matches()) {
				throw new IllegalArgumentException(
						"DynamicParticles.spawn particle ID must be a literal local ID declared by set_custom_particles");
			}
			ids.add(literal.group(1));
		}
		return List.copyOf(ids);
	}

	private static Set<DynamicBehaviorCapability> inspectCapabilities(String source) {
		EnumSet<DynamicBehaviorCapability> capabilities = declaredCapabilities(source, locateClass(source));
		return capabilities.isEmpty()
				? Set.of()
				: Collections.unmodifiableSet(EnumSet.copyOf(capabilities));
	}

	private static EnumSet<DynamicBehaviorCapability> declaredCapabilities(String source, SourceClass sourceClass) {
		String masked = maskNonCode(source);
		String header = masked.substring(sourceClass.declarationStart(), sourceClass.openingBrace());
		EnumSet<DynamicBehaviorCapability> capabilities = EnumSet.noneOf(DynamicBehaviorCapability.class);
		for (DynamicBehaviorCapability capability : DynamicBehaviorCapability.values()) {
			if (Pattern.compile("\\b" + Pattern.quote(capability.interfaceName()) + "\\b")
					.matcher(header).find()) {
				capabilities.add(capability);
			}
		}
		return capabilities;
	}

	private static SourceClass locateClass(String source) {
		String masked = maskNonCode(source);
		Matcher declaration = CLASS_DECLARATION.matcher(masked);
		if (!declaration.find()) throw new IllegalArgumentException("Behavior does not declare " + CLASS_NAME);
		int openingBrace = masked.indexOf('{', declaration.end());
		if (openingBrace < 0) throw new IllegalArgumentException("Behavior class has no body");
		int closingBrace = matchingDelimiter(masked, openingBrace, '{', '}');
		if (closingBrace < 0) throw new IllegalArgumentException("Behavior class body is incomplete");
		return new SourceClass(declaration.start(), openingBrace, closingBrace);
	}

	private static MethodRegion findTopLevelMethod(String masked, SourceClass sourceClass, String methodName) {
		Pattern implementation = Pattern.compile("\\bpublic\\b[^;{}=]*\\b"
				+ Pattern.quote(methodName) + "\\s*\\(", Pattern.DOTALL);
		Matcher matcher = implementation.matcher(masked);
		matcher.region(sourceClass.openingBrace() + 1, sourceClass.closingBrace());
		while (matcher.find()) {
			if (braceDepthAt(masked, sourceClass.openingBrace() + 1, matcher.start()) != 0) continue;
			int methodNameStart = masked.indexOf(methodName, matcher.start());
			int openingParenthesis = masked.indexOf('(', methodNameStart + methodName.length());
			int closingParenthesis = matchingDelimiter(masked, openingParenthesis, '(', ')');
			if (closingParenthesis < 0) continue;
			int openingBrace = skipWhitespace(masked, closingParenthesis + 1);
			if (openingBrace >= masked.length() || masked.charAt(openingBrace) != '{') continue;
			int closingBrace = matchingDelimiter(masked, openingBrace, '{', '}');
			if (closingBrace < 0 || closingBrace > sourceClass.closingBrace()) continue;
			int start = includeOverrideAnnotation(masked, sourceClass.openingBrace() + 1, matcher.start());
			return new MethodRegion(start, closingBrace + 1, openingParenthesis, closingParenthesis,
					openingBrace, closingBrace);
		}
		return null;
	}

	private static boolean isNeutral(String masked, MethodRegion method, DynamicBehaviorCapability capability) {
		String body = masked.substring(method.openingBrace() + 1, method.closingBrace()).replaceAll("\\s+", "");
		return switch (capability) {
			case USE_AIR, USE_ON_BLOCK, INTERACT_LIVING_ENTITY, USE_PLACED_BLOCK ->
					PASS_RETURN.matcher(body).matches();
			case FINISH_USING -> returnsResultParameter(masked, method, body);
			default -> body.isEmpty();
		};
	}

	private static boolean returnsResultParameter(String masked, MethodRegion method, String body) {
		Matcher returned = IDENTIFIER_RETURN.matcher(body);
		if (!returned.matches()) return false;
		String parameters = masked.substring(method.openingParenthesis() + 1, method.closingParenthesis());
		String[] split = parameters.split(",");
		if (split.length < 4) return false;
		Matcher name = Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*)\\s*$").matcher(split[3]);
		return name.find() && returned.group(1).equals(name.group(1));
	}

	private static int includeOverrideAnnotation(String masked, int classBodyStart, int methodStart) {
		Matcher override = OVERRIDE_SUFFIX.matcher(masked.substring(classBodyStart, methodStart));
		return override.find() ? classBodyStart + override.start() : methodStart;
	}

	private static int skipWhitespace(String source, int start) {
		int index = start;
		while (index < source.length() && Character.isWhitespace(source.charAt(index))) index++;
		return index;
	}

	private static int braceDepthAt(String source, int start, int end) {
		int depth = 0;
		for (int index = start; index < end; index++) {
			if (source.charAt(index) == '{') depth++;
			else if (source.charAt(index) == '}') depth--;
		}
		return depth;
	}

	private static int matchingDelimiter(String source, int opening, char open, char close) {
		if (opening < 0 || opening >= source.length() || source.charAt(opening) != open) return -1;
		int depth = 0;
		for (int index = opening; index < source.length(); index++) {
			char value = source.charAt(index);
			if (value == open) depth++;
			else if (value == close && --depth == 0) return index;
		}
		return -1;
	}

	private static List<ArgumentRegion> argumentRegions(String masked, int openingParenthesis,
			int closingParenthesis) {
		List<ArgumentRegion> arguments = new ArrayList<>();
		int start = openingParenthesis + 1;
		int depth = 0;
		for (int index = start; index < closingParenthesis; index++) {
			char value = masked.charAt(index);
			if (value == '(' || value == '[' || value == '{') depth++;
			else if (value == ')' || value == ']' || value == '}') depth--;
			else if (value == ',' && depth == 0) {
				arguments.add(new ArgumentRegion(start, index));
				start = index + 1;
			}
		}
		arguments.add(new ArgumentRegion(start, closingParenthesis));
		return arguments;
	}

	private static String maskNonCode(String source) {
		StringBuilder masked = new StringBuilder(source);
		boolean lineComment = false;
		boolean blockComment = false;
		boolean string = false;
		boolean character = false;
		boolean textBlock = false;
		boolean escaped = false;
		for (int index = 0; index < source.length(); index++) {
			char value = source.charAt(index);
			char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
			if (lineComment) {
				if (value == '\n') lineComment = false;
				else masked.setCharAt(index, ' ');
				continue;
			}
			if (blockComment) {
				masked.setCharAt(index, value == '\n' ? '\n' : ' ');
				if (value == '*' && next == '/') {
					masked.setCharAt(++index, ' ');
					blockComment = false;
				}
				continue;
			}
			if (textBlock) {
				masked.setCharAt(index, value == '\n' ? '\n' : ' ');
				if (value == '"' && next == '"' && index + 2 < source.length() && source.charAt(index + 2) == '"') {
					masked.setCharAt(++index, ' ');
					masked.setCharAt(++index, ' ');
					textBlock = false;
				}
				continue;
			}
			if (string || character) {
				masked.setCharAt(index, value == '\n' ? '\n' : ' ');
				if (escaped) escaped = false;
				else if (value == '\\') escaped = true;
				else if (string && value == '"') string = false;
				else if (character && value == '\'') character = false;
				continue;
			}
			if (value == '/' && next == '/') {
				masked.setCharAt(index, ' ');
				masked.setCharAt(++index, ' ');
				lineComment = true;
			} else if (value == '/' && next == '*') {
				masked.setCharAt(index, ' ');
				masked.setCharAt(++index, ' ');
				blockComment = true;
			} else if (value == '"' && next == '"' && index + 2 < source.length() && source.charAt(index + 2) == '"') {
				masked.setCharAt(index, ' ');
				masked.setCharAt(++index, ' ');
				masked.setCharAt(++index, ' ');
				textBlock = true;
			} else if (value == '"') {
				masked.setCharAt(index, ' ');
				string = true;
			} else if (value == '\'') {
				masked.setCharAt(index, ' ');
				character = true;
			}
		}
		return masked.toString();
	}

	private record SourceClass(int declarationStart, int openingBrace, int closingBrace) {
	}

	private record MethodRegion(int start, int end, int openingParenthesis, int closingParenthesis,
			int openingBrace, int closingBrace) {
	}

	private record ArgumentRegion(int start, int end) {
	}
}
