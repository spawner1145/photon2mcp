package dev.photonmcp.editor;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CodeRunnerTest {
    @Test
    void executesCodeAndCapturesOutput() throws Exception {
        var runner = new CodeRunner();
        var result = runner.execute("println 'Photon'; return [sum: args.value + 2, vector: [1, 2, 3]]", Map.of("args", Map.of("value", 40)));
        assertEquals("Photon\n", result.get("stdout").getAsString().replace("\r\n", "\n"));
        assertEquals(42, result.getAsJsonObject("result").get("sum").getAsInt());
        assertEquals(3, result.getAsJsonObject("result").getAsJsonArray("vector").size());
    }

    @Test
    void retainsVariablesAndCanResetContext() throws Exception {
        var runner = new CodeRunner();
        runner.execute("saved = 7", Map.of());
        assertEquals(14, runner.execute("return saved * 2", Map.of()).get("result").getAsInt());
        runner.reset();
        assertThrows(Exception.class, () -> runner.execute("return saved", Map.of()));
    }

    @Test
    void canReflectAndEditNativePrivateFields() throws Exception {
        var fixture = new Fixture();
        var runner = new CodeRunner();
        var result = runner.execute("api.write(target, 'value', 25); return api.read(target, 'value')",
                Map.of("api", ApiIntrospection.class, "target", fixture));
        assertEquals(25, result.get("result").getAsInt());
        assertEquals(25, fixture.value);
    }

    @Test
    void reportsOutputWhenCodeFails() {
        var runner = new CodeRunner();
        var error = assertThrows(Exception.class, () -> runner.execute("println 'before'; throw new RuntimeException('failed')", Map.of()));
        assertTrue(error.getMessage().contains("before"));
        assertTrue(error.getMessage().contains("failed"));
    }

    @Test
    void serializesCyclesAndPrimitiveArraysWithoutRecursionFailure() throws Exception {
        var runner = new CodeRunner();
        var result = runner.execute("def data = [numbers: ([1,2] as int[])]; data.self = data; return data", Map.of()).getAsJsonObject("result");
        assertEquals(2, result.getAsJsonArray("numbers").size());
        assertTrue(result.get("self").getAsString().startsWith("<reference:"));
    }

    @Test
    void convertsGroovyAssertionErrorsIntoReportedCodeFailures() {
        var runner = new CodeRunner();
        var error = assertThrows(Exception.class, () -> runner.execute("println 'assertion'; assert false : 'probe'", Map.of()));
        assertTrue(error.getMessage().contains("probe"));
        assertTrue(error.getMessage().contains("assertion"));
    }

    @Test
    void describesActualOverloadsFieldsAndInheritance() {
        var result = ApiIntrospection.describe(Fixture.class, "", true);
        assertEquals(Fixture.class.getName(), result.get("class").getAsString());
        assertTrue(result.getAsJsonArray("fields").asList().stream().anyMatch(value -> value.getAsJsonObject().get("name").getAsString().equals("value")));
        assertTrue(result.getAsJsonArray("methods").asList().stream().anyMatch(value -> value.getAsJsonObject().get("name").getAsString().equals("toString")));
        assertEquals(2, ApiIntrospection.describe(Fixture.class, "example", false).getAsJsonArray("methods").size());
    }

    private static class Fixture {
        private int value;
        public int example(int number) { return number; }
        public String example(String text) { return text; }
    }
}
