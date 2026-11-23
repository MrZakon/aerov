package net.aero.visuals.render.shader;

import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Загружает GLSL и раскрывает Mojang-import до передачи исходника OpenGL. */
public final class ShaderSourceLoader {
    private static final Pattern IMPORT = Pattern.compile(
            "^\\s*#moj_import\\s+(?:<([^>]+)>|\"([^\"]+)\")\\s*(?://.*)?$");
    private static final int MAX_IMPORT_DEPTH = 32;
    private static final int MAX_EXPANDED_CHARS = 1_048_576;

    private ShaderSourceLoader() {}

    public static String load(ResourceManager manager, Identifier identifier) throws IOException {
        StringBuilder source = new StringBuilder(4096);
        appendSource(manager, identifier, new HashSet<>(), source);
        return source.toString();
    }

    private static void appendSource(ResourceManager manager, Identifier identifier,
                                     Set<Identifier> stack, StringBuilder source) throws IOException {
        if (stack.size() >= MAX_IMPORT_DEPTH) throw new IOException("GLSL import depth exceeded: " + identifier);
        if (!stack.add(identifier)) throw new IOException("Recursive GLSL import: " + identifier);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                manager.getResource(identifier).orElseThrow(() -> new IOException("Missing shader " + identifier))
                        .getInputStream(), StandardCharsets.UTF_8))) {
            Matcher matcher = IMPORT.matcher("");
            String line;
            while ((line = reader.readLine()) != null) {
                if (!matcher.reset(line).matches()) {
                    appendLine(source, line);
                    continue;
                }
                String quoted = matcher.group(2);
                Identifier imported = quoted == null ? importId(identifier, matcher.group(1))
                        : relativeImport(identifier, quoted);
                appendLine(source, "// begin " + imported);
                appendSource(manager, imported, stack, source);
                appendLine(source, "// end " + imported);
            }
        } finally {
            stack.remove(identifier);
        }
    }

    private static void appendLine(StringBuilder source, String line) throws IOException {
        if (line.length() >= MAX_EXPANDED_CHARS - source.length()) {
            throw new IOException("Expanded GLSL exceeds " + MAX_EXPANDED_CHARS + " characters");
        }
        source.append(line).append('\n');
    }

    private static Identifier relativeImport(Identifier parent, String value) {
        if (value.indexOf(':') >= 0 || value.startsWith("shaders/")) return importId(parent, value);
        String parentPath = parent.getPath();
        String directory = parentPath.substring(0, parentPath.lastIndexOf('/') + 1);
        var path = new java.util.ArrayDeque<String>();
        for (String component : (directory + value).split("/")) {
            if (component.isEmpty() || component.equals(".")) continue;
            if (component.equals("..")) {
                if (path.size() <= 1) throw new IllegalArgumentException("GLSL import escapes shader directory");
                path.removeLast();
            } else {
                path.addLast(component);
            }
        }
        return Identifier.of(parent.getNamespace(), String.join("/", path));
    }

    private static Identifier importId(Identifier parent, String value) {
        Identifier requested = Identifier.of(value);
        String path = requested.getPath();
        if (!path.startsWith("shaders/")) {
            path = "shaders/include/" + path;
        }
        String namespace = value.indexOf(':') >= 0 ? requested.getNamespace() : parent.getNamespace();
        return Identifier.of(namespace, path);
    }
}
