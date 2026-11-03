package tg_bot_editor;

import com.fasterxml.jackson.core.type.TypeReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Operações genéricas sobre listas ou coleções agrupadas em uma seção JSON. */
public final class JsonDb {
    private JsonDb() { }

    public static Object execute(Map<String, Object> settings, Object input, Object output) throws Exception {
        String section = String.valueOf(settings.getOrDefault("section", "")).trim();
        String operation = String.valueOf(settings.getOrDefault("operation", "")).trim();
        Path project = ProjectEnvironment.directory();
        if (project == null) throw new IllegalStateException("Abra um projeto para acessar o JsonDB.");
        Path database = databasePath(settings, project);
        if (settings.containsKey("dataPath")) return executePath(settings, input, output, database, operation);
        if (!section.matches("[A-Za-z][A-Za-z0-9_-]*"))
            throw new IllegalArgumentException("Informe uma seção JSON válida.");
        String legacy = String.valueOf(settings.getOrDefault("legacy", "")).trim();
        Path old = legacy.isEmpty() ? null : project.resolve(legacy).normalize();
        if (old != null && !old.startsWith(project.normalize()))
            throw new IllegalArgumentException("O arquivo legado deve estar dentro do projeto.");
        synchronized (BotDatabase.class) {
            String layout = String.valueOf(settings.getOrDefault("layout", "GROUPED"));
            if (!List.of("GROUPED", "ARRAY", "OBJECT_ARRAY", "OBJECT").contains(layout))
                throw new IllegalArgumentException("Formato de coleção JsonDB inválido: " + layout);
            Map<String, List<Map<String, Object>>> groups = null;
            List<Map<String, Object>> array = null;
            Map<String, Object> object = null;
            String collection = String.valueOf(settings.getOrDefault("collection", "")).trim();
            if ("ARRAY".equals(layout)) array = BotDatabase.read(database, section, old,
                    new TypeReference<List<Map<String, Object>>>() { }, ArrayList::new);
            else if ("OBJECT".equals(layout)) {
                object = BotDatabase.read(database, section, old,
                        new TypeReference<Map<String, Object>>() { }, LinkedHashMap::new);
            } else if ("OBJECT_ARRAY".equals(layout)) {
                if (collection.isEmpty()) throw new IllegalArgumentException("Informe a coleção dentro do objeto JSON.");
                object = BotDatabase.read(database, section, old,
                        new TypeReference<Map<String, Object>>() { }, LinkedHashMap::new);
                Object value = object.get(collection);
                if (value == null) array = new ArrayList<>();
                else if (value instanceof List<?> list) {
                    array = new ArrayList<>();
                    for (Object item : list) {
                        if (!(item instanceof Map<?, ?> map)) throw new IllegalArgumentException("A coleção deve conter objetos.");
                        var copy = new LinkedHashMap<String, Object>();
                        map.forEach((key, entry) -> copy.put(String.valueOf(key), entry));
                        array.add(copy);
                    }
                } else throw new IllegalArgumentException("A coleção deve ser uma lista.");
            }
            else groups = BotDatabase.read(database, section, old,
                    new TypeReference<Map<String, List<Map<String, Object>>>>() { }, LinkedHashMap::new);
            if ("LIST".equals(operation)) {
                Object data = array != null ? array : groups != null ? groups : object;
                if (!Boolean.TRUE.equals(settings.get("includeInput"))) return data;
                var result = new LinkedHashMap<String, Object>();
                result.put("input", output); result.put("data", data);
                return result;
            }
            if ("OBJECT".equals(layout)) throw new IllegalArgumentException("O formato OBJECT suporta apenas LIST.");
            if (!List.of("INSERT", "UPSERT", "DELETE").contains(operation)) throw new IllegalArgumentException("Operação JsonDB não suportada: " + operation);
            Object keyValue = DiagramExecutor.value(String.valueOf(settings.getOrDefault("key", "")), input, output);
            Object recordValue = DiagramExecutor.value(String.valueOf(settings.getOrDefault("record", "")), input, output);
            if (!(recordValue instanceof Map<?, ?> record)) throw new IllegalArgumentException("Informe o registro para o JsonDB.");
            if (groups != null && (!(keyValue instanceof String) || ((String) keyValue).isBlank()))
                throw new IllegalArgumentException("Informe a chave da coleção agrupada.");
            String identity = String.valueOf(settings.getOrDefault("identity", "")).trim();
            if (identity.isEmpty()) throw new IllegalArgumentException("Informe os campos de identificação do registro.");
            List<String> fields = List.of(identity.split("\\s*,\\s*"));
            if (fields.stream().anyMatch(field -> field.isBlank() || !record.containsKey(field)))
                throw new IllegalArgumentException("Campos de identificação ausentes no registro.");
            var entries = new ArrayList<>(array != null ? array : groups.getOrDefault((String) keyValue, List.of()));
            boolean exists = entries.stream().anyMatch(existing -> fields.stream().allMatch(field -> Objects.equals(
                    normalize(existing.get(field)), normalize(record.get(field)))));
            if ("INSERT".equals(operation) && exists) return insertResult(false, output);
            entries.removeIf(existing -> fields.stream().allMatch(field -> Objects.equals(
                    normalize(existing.get(field)), normalize(record.get(field)))));
            var copy = new LinkedHashMap<String, Object>();
            record.forEach((field, value) -> copy.put(String.valueOf(field), value));
            if ("UPSERT".equals(operation) || "INSERT".equals(operation)) entries.add(copy);
            if (groups != null) {
                String key = (String) keyValue;
                if (entries.isEmpty()) groups.remove(key); else groups.put(key, entries);
                BotDatabase.write(database, section, groups);
            } else if (object != null) {
                object.put(collection, entries);
                BotDatabase.write(database, section, object);
            } else BotDatabase.write(database, section, entries);
            if ("INSERT".equals(operation)) return insertResult(true, output);
            return DiagramExecutor.value(String.valueOf(settings.getOrDefault("result", "{{output}}")), input, output);
        }
    }

    private static Object executePath(Map<String, Object> settings, Object input, Object output, Path database,
                                      String operation) throws Exception {
        String path = String.valueOf(settings.getOrDefault("dataPath", "")).trim();
        List<String> fields = pathFields(path, input, output);
        synchronized (BotDatabase.class) {
            Map<String, Object> document = BotDatabase.readDocument(database);
            if ("LIST".equals(operation)) {
                Object data = select(document, fields, 0);
                if (!Boolean.TRUE.equals(settings.get("includeInput"))) return data;
                var result = new LinkedHashMap<String, Object>();
                result.put("input", output); result.put("data", data); return result;
            }
            if ("SET".equals(operation) || "REMOVE".equals(operation)) {
                if (fields.isEmpty()) throw new IllegalArgumentException("Informe o caminho do valor a alterar.");
                Map<String, Object> parent = document;
                for (String field : fields.subList(0, fields.size() - 1)) {
                    Object next = parent.get(field);
                    if (next == null && "SET".equals(operation)) { next = new LinkedHashMap<String, Object>(); parent.put(field, next); }
                    if (next == null) return DiagramExecutor.value(String.valueOf(settings.getOrDefault("result", "{{output}}")), input, output);
                    if (!(next instanceof Map<?, ?>)) throw new IllegalArgumentException("O caminho de escrita deve atravessar apenas objetos JSON.");
                    @SuppressWarnings("unchecked") Map<String, Object> child = (Map<String, Object>) next;
                    parent = child;
                }
                String last = fields.getLast();
                if ("SET".equals(operation)) parent.put(last, DiagramExecutor.value(String.valueOf(settings.getOrDefault("record", "{{output}}")), input, output));
                else parent.remove(last);
                BotDatabase.writeDocument(database, document);
                return DiagramExecutor.value(String.valueOf(settings.getOrDefault("result", "{{output}}")), input, output);
            }
            if (!List.of("INSERT", "UPSERT", "DELETE").contains(operation))
                throw new IllegalArgumentException("Operação JsonDB não suportada: " + operation);
            if (fields.isEmpty()) throw new IllegalArgumentException("Informe o caminho da lista a alterar.");
            Object recordValue = DiagramExecutor.value(String.valueOf(settings.getOrDefault("record", "")), input, output);
            if (!(recordValue instanceof Map<?, ?> record)) throw new IllegalArgumentException("Informe o registro para o JsonDB.");
            String identity = String.valueOf(settings.getOrDefault("identity", "")).trim();
            if (identity.isEmpty()) throw new IllegalArgumentException("Informe os campos de identificação do registro.");
            List<String> identityFields = List.of(identity.split("\\s*,\\s*"));
            if (identityFields.stream().anyMatch(field -> field.isBlank() || !record.containsKey(field)))
                throw new IllegalArgumentException("Campos de identificação ausentes no registro.");
            Map<String, Object> parent = document;
            for (String field : fields.subList(0, fields.size() - 1)) {
                Object next = parent.get(field);
                if (next == null && !"DELETE".equals(operation)) { next = new LinkedHashMap<String, Object>(); parent.put(field, next); }
                if (next == null) return DiagramExecutor.value(String.valueOf(settings.getOrDefault("result", "{{output}}")), input, output);
                if (!(next instanceof Map<?, ?>)) throw new IllegalArgumentException("O caminho de escrita deve atravessar apenas objetos JSON.");
                @SuppressWarnings("unchecked") Map<String, Object> child = (Map<String, Object>) next;
                parent = child;
            }
            String last = fields.getLast(); Object value = parent.get(last);
            if (value != null && !(value instanceof List<?>)) throw new IllegalArgumentException("O caminho de escrita deve apontar para uma lista.");
            var entries = new ArrayList<Object>(value == null ? List.of() : (List<?>) value);
            boolean exists = entries.stream().anyMatch(item -> matches(item, record, identityFields));
            if ("INSERT".equals(operation) && exists) return insertResult(false, output);
            entries.removeIf(item -> matches(item, record, identityFields));
            if (!"DELETE".equals(operation)) entries.add(new LinkedHashMap<>(record));
            if (value != null || !"DELETE".equals(operation)) { parent.put(last, entries); BotDatabase.writeDocument(database, document); }
            return "INSERT".equals(operation) ? insertResult(true, output)
                    : DiagramExecutor.value(String.valueOf(settings.getOrDefault("result", "{{output}}")), input, output);
        }
    }

    private static boolean matches(Object item, Map<?, ?> record, List<String> fields) {
        return item instanceof Map<?, ?> map && fields.stream().allMatch(field -> Objects.equals(
                normalize(map.get(field)), normalize(record.get(field))));
    }

    private static Object select(Object current, List<String> fields, int position) {
        if (position == fields.size()) return current;
        if (current instanceof Map<?, ?> map) return select(map.get(fields.get(position)), fields, position + 1);
        if (current instanceof List<?> list) {
            var projected = new ArrayList<Object>();
            for (Object item : list) {
                Object value = select(item, fields, position);
                if (value instanceof List<?> values) projected.addAll(values);
                else if (value != null) projected.add(value);
            }
            return projected;
        }
        return null;
    }

    private static List<String> pathFields(String path, Object input, Object output) {
        if (path.isEmpty()) return List.of();
        var fields = new ArrayList<String>(); var part = new StringBuilder(); boolean reference = false;
        for (int i = 0; i < path.length(); i++) {
            if (path.startsWith("{{", i)) reference = true;
            if (path.charAt(i) == '.' && !reference) { fields.add(part.toString()); part.setLength(0); }
            else part.append(path.charAt(i));
            if (path.startsWith("}}", i)) { part.append('}'); i++; reference = false; }
        }
        fields.add(part.toString());
        var resolved = new ArrayList<String>();
        for (String field : fields) {
            Object value = DiagramExecutor.value(field, input, output);
            if (value == null || value.toString().isBlank() || reference) throw new IllegalArgumentException("Caminho de dados inválido: " + path);
            resolved.add(value.toString());
        }
        return resolved;
    }

    private static Map<String, Object> insertResult(boolean inserted, Object value) {
        var result = new LinkedHashMap<String, Object>();
        result.put("inserted", inserted); result.put("value", value);
        return result;
    }

    private static Path databasePath(Map<String, Object> settings, Path project) {
        String path = String.valueOf(settings.getOrDefault("path", "")).trim();
        if (path.isEmpty()) return BotDatabase.file();
        if (path.matches("\\$\\{[A-Za-z_][A-Za-z0-9_]*}")) {
            String variable = path.substring(2, path.length() - 1);
            String configured = ProjectEnvironment.get(variable);
            if (configured == null || configured.isBlank()) {
                if ("TG_BOT_DATABASE_FILE".equals(variable)) return BotDatabase.file();
                throw new IllegalArgumentException("Variável de caminho não definida: " + variable);
            }
            path = configured.trim();
        }
        Path result = Path.of(path);
        if (!result.isAbsolute()) result = project.resolve(result);
        result = result.toAbsolutePath().normalize();
        if (result.getFileName() == null || !result.getFileName().toString().endsWith(".json"))
            throw new IllegalArgumentException("O caminho do JsonDB deve apontar para um arquivo .json.");
        return result;
    }

    private static Object normalize(Object value) {
        return value instanceof Number number ? new java.math.BigDecimal(number.toString()).stripTrailingZeros() : value;
    }
}
