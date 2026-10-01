package dev.photonmcp.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;

public final class ApiIntrospection {
    private ApiIntrospection() {}

    public static JsonObject describe(Class<?> type, String filter, boolean inherited) {
        var result = new JsonObject();
        result.addProperty("class", type.getName());
        result.addProperty("modifiers", Modifier.toString(type.getModifiers()));
        result.addProperty("superclass", type.getSuperclass() == null ? "" : type.getSuperclass().getName());
        var interfaces = new JsonArray();
        Arrays.stream(type.getInterfaces()).forEach(value -> interfaces.add(value.getName()));
        result.add("interfaces", interfaces);
        var fields = new JsonArray();
        var methods = new JsonArray();
        var visited = new HashSet<Class<?>>();
        describeMembers(type, filter, inherited, fields, methods, visited);
        result.add("fields", fields);
        result.add("methods", methods);
        var constructors = new JsonArray();
        for (var constructor : type.getDeclaredConstructors()) constructors.add(constructor.toGenericString());
        result.add("constructors", constructors);
        if (type.isEnum()) {
            var constants = new JsonArray();
            for (var value : type.getEnumConstants()) constants.add(((Enum<?>) value).name());
            result.add("enumValues", constants);
        }
        return result;
    }

    private static void describeMembers(Class<?> type, String filter, boolean inherited, JsonArray fields,
                                        JsonArray methods, HashSet<Class<?>> visited) {
        if (type == null || !visited.add(type)) return;
        var declaredFields = type.getDeclaredFields();
        Arrays.sort(declaredFields, Comparator.comparing(java.lang.reflect.Field::getName));
        for (var field : declaredFields) {
            if (!field.getName().contains(filter)) continue;
            var entry = new JsonObject();
            entry.addProperty("name", field.getName());
            entry.addProperty("type", field.getGenericType().getTypeName());
            entry.addProperty("declaringClass", field.getDeclaringClass().getName());
            entry.addProperty("modifiers", Modifier.toString(field.getModifiers()));
            var annotations = new JsonArray();
            for (var annotation : field.getDeclaredAnnotations()) annotations.add(annotation.toString());
            entry.add("annotations", annotations);
            fields.add(entry);
        }
        var declaredMethods = type.getDeclaredMethods();
        Arrays.sort(declaredMethods, Comparator.comparing(java.lang.reflect.Method::toGenericString));
        for (var method : declaredMethods) {
            if (!method.getName().contains(filter) || method.isSynthetic()) continue;
            var entry = new JsonObject();
            entry.addProperty("name", method.getName());
            entry.addProperty("signature", method.toGenericString());
            entry.addProperty("returns", method.getGenericReturnType().getTypeName());
            entry.addProperty("declaringClass", method.getDeclaringClass().getName());
            var parameters = new JsonArray();
            for (var parameter : method.getParameters()) {
                var description = new JsonObject();
                description.addProperty("name", parameter.getName());
                description.addProperty("type", parameter.getParameterizedType().getTypeName());
                parameters.add(description);
            }
            entry.add("parameters", parameters);
            methods.add(entry);
        }
        if (inherited) {
            describeMembers(type.getSuperclass(), filter, true, fields, methods, visited);
            for (var parent : type.getInterfaces()) describeMembers(parent, filter, true, fields, methods, visited);
        }
    }

    public static Object read(Object instance, String name) throws ReflectiveOperationException {
        var type = instance instanceof Class<?> value ? value : instance.getClass();
        while (type != null) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(instance instanceof Class<?> ? null : instance);
            } catch (NoSuchFieldException exception) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    public static void write(Object instance, String name, Object value) throws ReflectiveOperationException {
        var type = instance instanceof Class<?> target ? target : instance.getClass();
        while (type != null) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(instance instanceof Class<?> ? null : instance, value);
                return;
            } catch (NoSuchFieldException exception) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
