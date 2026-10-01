package dev.photonmcp.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeSet;
import java.util.jar.JarInputStream;

public final class ApiClasses {
    private ApiClasses() {}

    public static JsonObject list(Path mods, String prefix, String filter) throws IOException {
        var names = new TreeSet<String>();
        try (var files = Files.list(mods)) {
            for (var file : files.filter(path -> path.toString().endsWith(".jar")).toList()) {
                try (var archive = new JarInputStream(Files.newInputStream(file))) {
                    scan(archive, prefix, filter, names);
                }
            }
        }
        var classes = new JsonArray();
        names.forEach(classes::add);
        var result = new JsonObject();
        result.add("classes", classes);
        result.addProperty("count", names.size());
        return result;
    }

    private static void scan(JarInputStream archive, String prefix, String filter, TreeSet<String> names) throws IOException {
        java.util.jar.JarEntry entry;
        while ((entry = archive.getNextJarEntry()) != null) {
            var name = entry.getName();
            if (name.endsWith(".class") && !name.startsWith("META-INF/")) {
                var type = name.substring(0, name.length() - 6).replace('/', '.');
                if (type.startsWith(prefix) && type.contains(filter)) names.add(type);
            } else if (name.startsWith("META-INF/jarjar/") && name.endsWith(".jar")) {
                try (var nested = new JarInputStream(new java.io.ByteArrayInputStream(archive.readAllBytes()))) {
                    scan(nested, prefix, filter, names);
                }
            }
        }
    }
}
