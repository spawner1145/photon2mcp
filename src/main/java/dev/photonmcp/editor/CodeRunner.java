package dev.photonmcp.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.util.IdentityHashMap;
import java.util.Map;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;

public final class CodeRunner {
    private Binding binding = new Binding();
    private long sequence;

    public void reset() { binding = new Binding(); }

    public JsonObject execute(String code, Map<String, Object> context) throws Exception {
        context.forEach(binding::setVariable);
        var output = new StringWriter();
        binding.setVariable("out", new PrintWriter(output, true));
        var imports = new ImportCustomizer();
        imports.addStarImports("com.lowdragmc.photon.client.fx", "com.lowdragmc.photon.client.gameobject",
                "com.lowdragmc.photon.client.fx.timeline", "com.lowdragmc.photon.client.fx.timeline.property",
                "com.lowdragmc.photon.gui.editor", "net.minecraft.nbt", "net.minecraft.resources", "org.joml");
        imports.addImports("com.lowdragmc.photon.PhotonRegistries", "com.lowdragmc.lowdraglib2.Platform",
                "com.lowdragmc.lowdraglib2.utils.PersistedParser", "com.lowdragmc.lowdraglib2.LDLib2");
        var configuration = new CompilerConfiguration();
        configuration.addCompilationCustomizers(imports);
        var shell = new GroovyShell(CodeRunner.class.getClassLoader(), binding, configuration);
        try {
            var returned = shell.evaluate(code, "PhotonMcpScript" + ++sequence + ".groovy");
            var result = new JsonObject();
            result.addProperty("language", "groovy");
            result.addProperty("stdout", output.toString());
            result.add("result", json(returned, new IdentityHashMap<>()));
            return result;
        } catch (Throwable exception) {
            throw new IllegalArgumentException(exception + (output.getBuffer().isEmpty() ? "" : "\nstdout:\n" + output), exception);
        } finally {
            shell.getClassLoader().close();
        }
    }

    private static JsonElement json(Object value, IdentityHashMap<Object, Boolean> visited) {
        if (value == null) return JsonNull.INSTANCE;
        if (value instanceof JsonElement element) return element.deepCopy();
        if (value instanceof Number number) return new JsonPrimitive(number);
        if (value instanceof Boolean bool) return new JsonPrimitive(bool);
        if (value instanceof CharSequence text) return new JsonPrimitive(text.toString());
        if (visited.put(value, true) != null) return new JsonPrimitive("<reference:" + value.getClass().getName() + ">");
        if (value instanceof Map<?, ?> map) {
            var result = new JsonObject();
            map.forEach((key, entry) -> result.add(String.valueOf(key), json(entry, visited)));
            return result;
        }
        if (value instanceof Iterable<?> entries) {
            var result = new JsonArray();
            entries.forEach(entry -> result.add(json(entry, visited)));
            return result;
        }
        if (value.getClass().isArray()) {
            var result = new JsonArray();
            for (var index = 0; index < Array.getLength(value); index++) result.add(json(Array.get(value, index), visited));
            return result;
        }
        return new JsonPrimitive(String.valueOf(value));
    }
}
