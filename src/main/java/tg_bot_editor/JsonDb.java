package tg_bot_editor;

import com.fasterxml.jackson.core.type.TypeReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Operações genéricas sobre coleções agrupadas em uma seção JSON do projeto. */
public final class JsonDb {
    private JsonDb() { }

    public static Object execute(Map<String, Object> settings, Object input, Object output) throws Exception {
        String section = String.valueOf(settings.getOrDefault("section", "")).trim();
        String operation = String.valueOf(settings.getOrDefault("operation", "")).trim();
        if (!section.matches("[A-Za-z][A-Za-z0-9_-]*"))
            throw new IllegalArgumentException("Informe uma seção JSON válida.");
        Path project = ProjectEnvironment.directory();
        if (project == null) throw new IllegalStateException("Abra um projeto para acessar o JsonDB.");
        String legacy = String.valueOf(settings.getOrDefault("legacy", "")).trim();
        Path old = legacy.isEmpty() ? null : project.resolve(legacy).normalize();
        if (old != null && !old.startsWith(project.normalize()))
            throw new IllegalArgumentException("O arquivo legado deve estar dentro do projeto.");
        synchronized (BotDatabase.class) {
            Map<String, List<Map<String, Object>>> data = BotDatabase.read(section, old,
                    new TypeReference<Map<String, List<Map<String, Object>>>>() { }, LinkedHashMap::new);
            if ("LIST".equals(operation)) return data;
            if (!List.of("UPSERT", "DELETE").contains(operation)) throw new IllegalArgumentException("Operação JsonDB não suportada: " + operation);
            Object keyValue = DiagramExecutor.value(String.valueOf(settings.getOrDefault("key", "")), input, output);
            Object recordValue = DiagramExecutor.value(String.valueOf(settings.getOrDefault("record", "")), input, output);
            if (!(keyValue instanceof String key) || key.isBlank() || !(recordValue instanceof Map<?, ?> record))
                throw new IllegalArgumentException("Informe chave e registro para o JsonDB.");
            String identity = String.valueOf(settings.getOrDefault("identity", "")).trim();
            if (identity.isEmpty()) throw new IllegalArgumentException("Informe os campos de identificação do registro.");
            List<String> fields = List.of(identity.split("\\s*,\\s*"));
            if (fields.stream().anyMatch(field -> field.isBlank() || !record.containsKey(field)))
                throw new IllegalArgumentException("Campos de identificação ausentes no registro.");
            var entries = new ArrayList<>(data.getOrDefault(key, List.of()));
            entries.removeIf(existing -> fields.stream().allMatch(field -> Objects.equals(
                    normalize(existing.get(field)), normalize(record.get(field)))));
            var copy = new LinkedHashMap<String, Object>();
            record.forEach((field, value) -> copy.put(String.valueOf(field), value));
            if ("UPSERT".equals(operation)) entries.add(copy);
            if (entries.isEmpty()) data.remove(key); else data.put(key, entries);
            BotDatabase.write(section, data);
            return DiagramExecutor.value(String.valueOf(settings.getOrDefault("result", "{{output}}")), input, output);
        }
    }

    private static Object normalize(Object value) {
        return value instanceof Number number ? new java.math.BigDecimal(number.toString()).stripTrailingZeros() : value;
    }
}
