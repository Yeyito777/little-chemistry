package com.yeyito.littlechemistry.ai.generation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts the common {@code CubeListBuilder}/{@code PartPose} form used by native entity models into a compact,
 * model-facing description of cuboids, pivots, rotations, and exact face UV rectangles.
 *
 * <p>This is deliberately a source/reference translator, not a renderer and not a raster viewer. It pairs the textual
 * texture pixels with the geometry information needed to understand them, while leaving generated content free to copy,
 * simplify, or replace the native profile.</p>
 */
final class NativeEntityModelReference {
	private static final Pattern METHOD = Pattern.compile(
			"(?:public|protected|private)\\s+static\\s+[^;{}]+?\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(([^)]*)\\)\\s*\\{");
	private static final Pattern NUMBER_DECLARATION = Pattern.compile(
			"(?:private\\s+|protected\\s+|public\\s+)?(?:static\\s+)?(?:final\\s+)?(?:int|float|double)\\s+"
					+ "([A-Za-z_$][A-Za-z0-9_$]*)\\s*=\\s*([^;]+);");
	private static final Pattern ASSIGNED_PART = Pattern.compile(
			"PartDefinition\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*=\\s*$");
	private static final Pattern PARENT_PART = Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*)\\s*$");

	private NativeEntityModelReference() {
	}

	static JsonObject translate(String modelClass, String factoryMethod, String source, JsonObject texture) {
		if (modelClass == null || !modelClass.matches("[A-Za-z_$][A-Za-z0-9_$.]*")) {
			throw new IllegalArgumentException("Native entity model class name is invalid");
		}
		if (factoryMethod == null || !factoryMethod.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
			throw new IllegalArgumentException("Native entity model factory method is invalid");
		}
		Map<String, MethodBody> methods = methods(source);
		MethodBody factory = methods.get(factoryMethod);
		if (factory == null) throw new IllegalArgumentException(
				"Native entity model source has no static factory method named " + factoryMethod);
		Map<String, Double> numbers = numericConstants(source);
		int[] textureSize = textureSize(factory.body(), numbers);
		if (textureSize == null) {
			textureSize = new int[] {requiredInt(texture, "width"), requiredInt(texture, "height")};
		}

		LinkedHashSet<String> reachable = new LinkedHashSet<>();
		collectReachable(factoryMethod, methods, reachable);
		List<Part> parts = new ArrayList<>();
		Map<String, Part> variables = new HashMap<>();
		Part root = new Part("root", "root", null, Pose.ZERO, new ArrayList<>());
		variables.put("root", root);
		parts.add(root);
		List<String> diagnostics = new ArrayList<>();
		for (String methodName : reachable) {
			parseParts(methods.get(methodName).body(), variables, parts, numbers, diagnostics);
		}

		List<ResolvedCube> cubes = resolve(parts);
		Bounds complete = Bounds.of(cubes);
		JsonObject model = new JsonObject();
		model.addProperty("modelClass", modelClass);
		model.addProperty("factoryMethod", factoryMethod);
		model.addProperty("textureWidth", textureSize[0]);
		model.addProperty("textureHeight", textureSize[1]);
		model.addProperty("sourceCoordinates", "Minecraft ModelPart pixels: +X right, +Y down, +Z toward the model's south; "
				+ "PartPose offsets/rotations are inherited by children");
		model.addProperty("generatedCoordinates", "Suggested bounds are normalized into Little Chemistry 0..16 model space: "
				+ "+X right, +Y up, +Z south. They are a starting profile, not a requirement to preserve native geometry.");
		model.addProperty("uvContract", "pixelUv is the exact native cuboid-net rectangle; generatedUv is that rectangle "
				+ "normalized independently to 0..16 by the complete texture width/height for DynamicBlockUv");
		JsonArray encodedParts = new JsonArray();
		for (Part part : parts) {
			if (part == root || part.cubes().isEmpty()) continue;
			JsonObject encodedPart = new JsonObject();
			encodedPart.addProperty("path", part.path());
			encodedPart.addProperty("parent", part.parent() == null ? null : part.parent().path());
			encodedPart.add("pivot", vector(part.pose().x(), part.pose().y(), part.pose().z()));
			encodedPart.add("rotationRadians", vector(part.pose().xRot(), part.pose().yRot(), part.pose().zRot()));
			JsonArray encodedCubes = new JsonArray();
			for (Cube cube : part.cubes()) {
				ResolvedCube resolved = cubes.stream().filter(candidate -> candidate.cube() == cube).findFirst().orElseThrow();
				JsonObject encodedCube = new JsonObject();
				encodedCube.add("sourceLocalBounds", bounds(cube.x(), cube.y(), cube.z(),
						cube.x() + cube.width(), cube.y() + cube.height(), cube.z() + cube.depth()));
				encodedCube.add("sourceTransformedBounds", resolved.bounds().json());
				encodedCube.add("suggestedGeneratedBounds", complete.normalized(resolved.bounds()));
				encodedCube.addProperty("mirrored", cube.mirrored());
				encodedCube.add("textureOrigin", vector(cube.u(), cube.v()));
				encodedCube.add("faces", faces(cube, textureSize[0], textureSize[1]));
				encodedCubes.add(encodedCube);
			}
			encodedPart.add("cubes", encodedCubes);
			encodedParts.add(encodedPart);
		}
		model.add("parts", encodedParts);
		if (!diagnostics.isEmpty()) {
			JsonArray values = new JsonArray();
			diagnostics.stream().distinct().limit(16).forEach(values::add);
			model.add("translationDiagnostics", values);
		}
		return model;
	}

	private static void collectReachable(String name, Map<String, MethodBody> methods, Set<String> result) {
		if (!result.add(name)) return;
		String body = methods.get(name).body();
		for (String candidate : methods.keySet()) {
			if (!candidate.equals(name) && Pattern.compile("\\b" + Pattern.quote(candidate) + "\\s*\\(")
					.matcher(body).find()) collectReachable(candidate, methods, result);
		}
	}

	private static void parseParts(String body, Map<String, Part> variables, List<Part> parts,
			Map<String, Double> numbers, List<String> diagnostics) {
		int cursor = 0;
		while ((cursor = body.indexOf(".addOrReplaceChild", cursor)) >= 0) {
			int open = body.indexOf('(', cursor);
			int close = matching(body, open, '(', ')');
			if (open < 0 || close < 0) break;
			int statement = Math.max(body.lastIndexOf(';', cursor), body.lastIndexOf('{', cursor)) + 1;
			String prefix = body.substring(statement, cursor).strip();
			Matcher parentMatcher = PARENT_PART.matcher(prefix);
			String parentVariable = parentMatcher.find() ? parentMatcher.group(1) : "root";
			String declaration = parentMatcher.find(0) ? prefix.substring(0, parentMatcher.start()).strip() : prefix;
			Matcher assignedMatcher = ASSIGNED_PART.matcher(declaration);
			String assigned = assignedMatcher.find() ? assignedMatcher.group(1) : null;
			List<String> arguments = splitTopLevel(body.substring(open + 1, close));
			if (arguments.size() < 3) {
				diagnostics.add("Skipped an addOrReplaceChild call with fewer than three arguments");
				cursor = close + 1;
				continue;
			}
			String name = stringLiteral(arguments.get(0));
			Part parent = variables.getOrDefault(parentVariable, variables.get("root"));
			Pose pose;
			try {
				pose = pose(arguments.get(2), numbers);
			} catch (RuntimeException invalid) {
				pose = Pose.ZERO;
				diagnostics.add("Part " + name + " used an unsupported PartPose expression; its pivot/rotation defaulted to zero");
			}
			Part part = new Part(name, parent.path() + "/" + name, parent, pose, new ArrayList<>());
			try {
				part.cubes().addAll(cubes(arguments.get(1), numbers));
			} catch (RuntimeException invalid) {
				diagnostics.add("Part " + name + " used an unsupported CubeListBuilder expression: " + invalid.getMessage());
			}
			parts.add(part);
			if (assigned != null) variables.put(assigned, part);
			cursor = close + 1;
		}
	}

	private static List<Cube> cubes(String builder, Map<String, Double> numbers) {
		List<Cube> result = new ArrayList<>();
		double u = 0;
		double v = 0;
		boolean mirrored = false;
		int cursor = 0;
		while (cursor < builder.length()) {
			int tex = builder.indexOf(".texOffs", cursor);
			int mirror = builder.indexOf(".mirror", cursor);
			int box = builder.indexOf(".addBox", cursor);
			int next = minPositive(tex, mirror, box);
			if (next < 0) break;
			int open = builder.indexOf('(', next);
			int close = matching(builder, open, '(', ')');
			if (open < 0 || close < 0) break;
			List<String> arguments = splitTopLevel(builder.substring(open + 1, close));
			if (next == tex) {
				u = evaluate(arguments.get(0), numbers);
				v = evaluate(arguments.get(1), numbers);
			} else if (next == mirror) {
				mirrored = arguments.isEmpty() || evaluate(arguments.get(0), numbers) != 0;
			} else {
				if (arguments.size() < 6) throw new IllegalArgumentException("addBox has fewer than six numeric arguments");
				result.add(new Cube(
						evaluate(arguments.get(0), numbers), evaluate(arguments.get(1), numbers),
						evaluate(arguments.get(2), numbers), evaluate(arguments.get(3), numbers),
						evaluate(arguments.get(4), numbers), evaluate(arguments.get(5), numbers),
						u, v, mirrored));
			}
			cursor = close + 1;
		}
		return result;
	}

	private static Pose pose(String expression, Map<String, Double> numbers) {
		if (expression.strip().equals("PartPose.ZERO")) return Pose.ZERO;
		for (String method : List.of("offsetAndRotation", "offset", "rotation")) {
			int call = expression.indexOf("PartPose." + method);
			if (call < 0) continue;
			int open = expression.indexOf('(', call);
			int close = matching(expression, open, '(', ')');
			List<String> arguments = splitTopLevel(expression.substring(open + 1, close));
			double[] values = arguments.stream().mapToDouble(value -> evaluate(value, numbers)).toArray();
			return switch (method) {
				case "offsetAndRotation" -> new Pose(values[0], values[1], values[2], values[3], values[4], values[5]);
				case "offset" -> new Pose(values[0], values[1], values[2], 0, 0, 0);
				case "rotation" -> new Pose(0, 0, 0, values[0], values[1], values[2]);
				default -> throw new AssertionError(method);
			};
		}
		throw new IllegalArgumentException("Unsupported PartPose expression");
	}

	private static JsonObject faces(Cube cube, int textureWidth, int textureHeight) {
		double x = cube.u();
		double z = cube.depth();
		double width = cube.width();
		double height = cube.height();
		double y = cube.v();
		double u0 = x;
		double u1 = x + z;
		double u2 = x + z + width;
		double u3 = x + z + width + width;
		double u4 = x + z + width + z;
		double u5 = x + z + width + z + width;
		double v0 = y;
		double v1 = y + z;
		double v2 = y + z + height;
		Map<String, double[]> rectangles = new LinkedHashMap<>();
		rectangles.put("down", new double[] {u1, v0, u2, v1});
		rectangles.put("up", new double[] {u2, v1, u3, v0});
		rectangles.put("west", new double[] {u0, v1, u1, v2});
		rectangles.put("north", new double[] {u1, v1, u2, v2});
		rectangles.put("east", new double[] {u2, v1, u4, v2});
		rectangles.put("south", new double[] {u4, v1, u5, v2});
		JsonObject result = new JsonObject();
		for (var entry : rectangles.entrySet()) {
			JsonObject face = new JsonObject();
			face.add("pixelUv", vector(entry.getValue()));
			face.add("generatedUv", vector(
					entry.getValue()[0] * 16.0 / textureWidth, entry.getValue()[1] * 16.0 / textureHeight,
					entry.getValue()[2] * 16.0 / textureWidth, entry.getValue()[3] * 16.0 / textureHeight));
			result.add(entry.getKey(), face);
		}
		return result;
	}

	private static List<ResolvedCube> resolve(List<Part> parts) {
		List<ResolvedCube> result = new ArrayList<>();
		Map<Part, Matrix> transforms = new HashMap<>();
		for (Part part : parts) {
			Matrix parent = part.parent() == null ? Matrix.IDENTITY : transforms.getOrDefault(part.parent(), Matrix.IDENTITY);
			Matrix transform = parent.multiply(Matrix.pose(part.pose()));
			transforms.put(part, transform);
			for (Cube cube : part.cubes()) result.add(new ResolvedCube(cube, transformedBounds(cube, transform)));
		}
		return result;
	}

	private static Bounds transformedBounds(Cube cube, Matrix matrix) {
		Bounds result = new Bounds();
		for (double x : new double[] {cube.x(), cube.x() + cube.width()})
			for (double y : new double[] {cube.y(), cube.y() + cube.height()})
				for (double z : new double[] {cube.z(), cube.z() + cube.depth()}) result.include(matrix.transform(x, y, z));
		return result;
	}

	private static Map<String, MethodBody> methods(String source) {
		Map<String, MethodBody> result = new LinkedHashMap<>();
		Matcher matcher = METHOD.matcher(source);
		while (matcher.find()) {
			int open = source.indexOf('{', matcher.start());
			int close = matching(source, open, '{', '}');
			if (close > open) result.put(matcher.group(1), new MethodBody(source.substring(open + 1, close)));
		}
		return result;
	}

	private static Map<String, Double> numericConstants(String source) {
		Map<String, Double> result = new HashMap<>();
		result.put("Math.PI", Math.PI);
		result.put("Mth.PI", Math.PI);
		for (int pass = 0; pass < 8; pass++) {
			boolean changed = false;
			Matcher matcher = NUMBER_DECLARATION.matcher(source);
			while (matcher.find()) {
				if (result.containsKey(matcher.group(1))) continue;
				try {
					result.put(matcher.group(1), evaluate(matcher.group(2), result));
					changed = true;
				} catch (RuntimeException ignored) {
				}
			}
			if (!changed) break;
		}
		return result;
	}

	private static int[] textureSize(String body, Map<String, Double> numbers) {
		int cursor = body.indexOf("LayerDefinition.create");
		if (cursor < 0) return null;
		int open = body.indexOf('(', cursor);
		int close = matching(body, open, '(', ')');
		List<String> arguments = splitTopLevel(body.substring(open + 1, close));
		if (arguments.size() < 3) return null;
		return new int[] {(int) Math.round(evaluate(arguments.get(arguments.size() - 2), numbers)),
				(int) Math.round(evaluate(arguments.get(arguments.size() - 1), numbers))};
	}

	private static double evaluate(String expression, Map<String, Double> variables) {
		String cleaned = expression.replaceAll("\\((?:float|double|int)\\)", "")
				.replaceAll("(?<=\\d)[fFdD]\\b", "").strip();
		return new Expression(cleaned, variables).parse();
	}

	private static int matching(String value, int open, char openCharacter, char closeCharacter) {
		if (open < 0) return -1;
		int depth = 0;
		boolean string = false;
		for (int index = open; index < value.length(); index++) {
			char character = value.charAt(index);
			if (character == '"' && (index == 0 || value.charAt(index - 1) != '\\')) string = !string;
			if (string) continue;
			if (character == openCharacter) depth++;
			else if (character == closeCharacter && --depth == 0) return index;
		}
		return -1;
	}

	private static List<String> splitTopLevel(String value) {
		List<String> result = new ArrayList<>();
		int start = 0;
		int round = 0;
		int square = 0;
		int curly = 0;
		boolean string = false;
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			if (character == '"' && (index == 0 || value.charAt(index - 1) != '\\')) string = !string;
			if (string) continue;
			switch (character) {
				case '(' -> round++;
				case ')' -> round--;
				case '[' -> square++;
				case ']' -> square--;
				case '{' -> curly++;
				case '}' -> curly--;
				case ',' -> {
					if (round == 0 && square == 0 && curly == 0) {
						result.add(value.substring(start, index).strip());
						start = index + 1;
					}
				}
				default -> { }
			}
		}
		String last = value.substring(start).strip();
		if (!last.isEmpty()) result.add(last);
		return result;
	}

	private static int minPositive(int... values) {
		return Arrays.stream(values).filter(value -> value >= 0).min().orElse(-1);
	}

	private static String stringLiteral(String value) {
		String stripped = value.strip();
		return stripped.length() >= 2 && stripped.startsWith("\"") && stripped.endsWith("\"")
				? stripped.substring(1, stripped.length() - 1) : stripped;
	}

	private static int requiredInt(JsonObject object, String key) {
		if (!object.has(key)) throw new IllegalArgumentException("Texture reference is missing " + key);
		return object.get(key).getAsInt();
	}

	private static JsonArray vector(double... values) {
		JsonArray result = new JsonArray();
		for (double value : values) result.add(round(value));
		return result;
	}

	private static JsonObject bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		JsonObject result = new JsonObject();
		result.add("from", vector(minX, minY, minZ));
		result.add("to", vector(maxX, maxY, maxZ));
		return result;
	}

	private static double round(double value) {
		return Math.rint(value * 10000.0) / 10000.0;
	}

	private record MethodBody(String body) { }
	private record Pose(double x, double y, double z, double xRot, double yRot, double zRot) {
		private static final Pose ZERO = new Pose(0, 0, 0, 0, 0, 0);
	}
	private record Cube(double x, double y, double z, double width, double height, double depth,
			double u, double v, boolean mirrored) { }
	private record Part(String name, String path, Part parent, Pose pose, List<Cube> cubes) { }
	private record ResolvedCube(Cube cube, Bounds bounds) { }

	private static final class Bounds {
		private double minX = Double.POSITIVE_INFINITY;
		private double minY = Double.POSITIVE_INFINITY;
		private double minZ = Double.POSITIVE_INFINITY;
		private double maxX = Double.NEGATIVE_INFINITY;
		private double maxY = Double.NEGATIVE_INFINITY;
		private double maxZ = Double.NEGATIVE_INFINITY;

		static Bounds of(List<ResolvedCube> cubes) {
			Bounds result = new Bounds();
			for (ResolvedCube cube : cubes) result.include(cube.bounds());
			return result;
		}

		void include(double[] point) {
			minX = Math.min(minX, point[0]); minY = Math.min(minY, point[1]); minZ = Math.min(minZ, point[2]);
			maxX = Math.max(maxX, point[0]); maxY = Math.max(maxY, point[1]); maxZ = Math.max(maxZ, point[2]);
		}

		void include(Bounds other) {
			include(new double[] {other.minX, other.minY, other.minZ});
			include(new double[] {other.maxX, other.maxY, other.maxZ});
		}

		JsonObject json() { return bounds(minX, minY, minZ, maxX, maxY, maxZ); }

		JsonObject normalized(Bounds value) {
			double horizontal = Math.max(maxX - minX, maxZ - minZ);
			double vertical = maxY - minY;
			if (!Double.isFinite(horizontal) || horizontal <= 0) horizontal = 1;
			if (!Double.isFinite(vertical) || vertical <= 0) vertical = 1;
			double centerX = (minX + maxX) / 2.0;
			double centerZ = (minZ + maxZ) / 2.0;
			return bounds(
					8 + (value.minX - centerX) * 16 / horizontal,
					(maxY - value.maxY) * 16 / vertical,
					8 + (value.minZ - centerZ) * 16 / horizontal,
					8 + (value.maxX - centerX) * 16 / horizontal,
					(maxY - value.minY) * 16 / vertical,
					8 + (value.maxZ - centerZ) * 16 / horizontal);
		}
	}

	private record Matrix(double[] values) {
		private static final Matrix IDENTITY = new Matrix(new double[] {
				1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1});

		static Matrix pose(Pose pose) {
			double cx = Math.cos(pose.xRot()), sx = Math.sin(pose.xRot());
			double cy = Math.cos(pose.yRot()), sy = Math.sin(pose.yRot());
			double cz = Math.cos(pose.zRot()), sz = Math.sin(pose.zRot());
			Matrix translation = new Matrix(new double[] {
					1, 0, 0, pose.x(), 0, 1, 0, pose.y(), 0, 0, 1, pose.z(), 0, 0, 0, 1});
			Matrix x = new Matrix(new double[] {
					1, 0, 0, 0, 0, cx, -sx, 0, 0, sx, cx, 0, 0, 0, 0, 1});
			Matrix y = new Matrix(new double[] {
					cy, 0, sy, 0, 0, 1, 0, 0, -sy, 0, cy, 0, 0, 0, 0, 1});
			Matrix z = new Matrix(new double[] {
					cz, -sz, 0, 0, sz, cz, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1});
			return translation.multiply(z).multiply(y).multiply(x);
		}

		Matrix multiply(Matrix other) {
			double[] result = new double[16];
			for (int row = 0; row < 4; row++) for (int column = 0; column < 4; column++) {
				for (int index = 0; index < 4; index++) {
					result[row * 4 + column] += values[row * 4 + index] * other.values[index * 4 + column];
				}
			}
			return new Matrix(result);
		}

		double[] transform(double x, double y, double z) {
			return new double[] {
					values[0] * x + values[1] * y + values[2] * z + values[3],
					values[4] * x + values[5] * y + values[6] * z + values[7],
					values[8] * x + values[9] * y + values[10] * z + values[11]};
		}
	}

	private static final class Expression {
		private final String value;
		private final Map<String, Double> variables;
		private int cursor;

		private Expression(String value, Map<String, Double> variables) {
			this.value = value;
			this.variables = variables;
		}

		double parse() {
			double result = sum();
			whitespace();
			if (cursor != value.length()) throw new IllegalArgumentException("Unsupported numeric expression: " + value);
			return result;
		}

		private double sum() {
			double result = product();
			while (true) {
				whitespace();
				if (take('+')) result += product();
				else if (take('-')) result -= product();
				else return result;
			}
		}

		private double product() {
			double result = atom();
			while (true) {
				whitespace();
				if (take('*')) result *= atom();
				else if (take('/')) result /= atom();
				else return result;
			}
		}

		private double atom() {
			whitespace();
			if (take('+')) return atom();
			if (take('-')) return -atom();
			if (take('(')) {
				double result = sum();
				whitespace();
				if (!take(')')) throw new IllegalArgumentException("Unclosed numeric expression: " + value);
				return result;
			}
			int start = cursor;
			while (cursor < value.length()) {
				char character = value.charAt(cursor);
				if (!Character.isLetterOrDigit(character) && character != '_' && character != '$'
						&& character != '.' && character != 'e' && character != 'E') break;
				cursor++;
			}
			if (start == cursor) throw new IllegalArgumentException("Expected a number in: " + value);
			String token = value.substring(start, cursor);
			Double variable = variables.get(token);
			if (variable != null) return variable;
			try {
				return Double.parseDouble(token);
			} catch (NumberFormatException invalid) {
				throw new IllegalArgumentException("Unknown numeric constant " + token);
			}
		}

		private void whitespace() {
			while (cursor < value.length() && Character.isWhitespace(value.charAt(cursor))) cursor++;
		}

		private boolean take(char character) {
			if (cursor < value.length() && value.charAt(cursor) == character) {
				cursor++;
				return true;
			}
			return false;
		}
	}
}
