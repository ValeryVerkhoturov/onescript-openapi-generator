package io.github.valeryverkhoturov.codegen;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;

import org.openapitools.codegen.CliOption;
import org.openapitools.codegen.CodegenConfig;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.CodegenServer;
import org.openapitools.codegen.CodegenType;
import org.openapitools.codegen.DefaultCodegen;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.meta.GeneratorMetadata;
import org.openapitools.codegen.meta.Stability;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.model.OperationMap;
import org.openapitools.codegen.model.OperationsMap;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Emits a OneScript (oscript.io) client library from an OpenAPI 3 document.
 *
 * <p>Output is a ready-to-build opm package: {@code packagedef} + {@code lib.config}
 * describing every generated class, sources under {@code src/}, and a small runtime
 * (configuration, HTTP transport, response type, secret-string wrapper) built on
 * <a href="https://github.com/vbondarevsky/1connector">1connector</a>, which is
 * declared as the package's only dependency.
 *
 * <p>Two OneScript traits shape most of the decisions here:
 * <ul>
 *   <li>identifiers are case-insensitive and may be Cyrillic, so names are sanitized
 *       into BSL-legal identifiers and de-duplicated case-insensitively;</li>
 *   <li>libraries are registered explicitly through {@code lib.config} rather than by
 *       directory convention, which is also the more robust of the two loader paths.</li>
 * </ul>
 */
public class OneScriptClientCodegen extends DefaultCodegen implements CodegenConfig {

    public static final String PACKAGE_NAME = "packageName";
    public static final String PACKAGE_VERSION = "packageVersion";
    public static final String PACKAGE_DESCRIPTION = "packageDescription";
    public static final String PACKAGE_AUTHOR = "packageAuthor";
    public static final String PACKAGE_AUTHOR_EMAIL = "packageAuthorEmail";
    public static final String ENVIRONMENT_VERSION = "environmentVersion";
    public static final String USER_AGENT = "userAgent";
    public static final String CONNECTOR_VERSION = "connectorVersion";
    public static final String JASON_VERSION = "jasonVersion";

    /** Directory holding API and runtime classes, relative to the package root. */
    private static final String CLASS_DIR = "src" + File.separator + "Классы";
    /** Directory holding model classes, kept apart so the tree stays navigable. */
    private static final String MODEL_DIR = "src" + File.separator + "Модели";

    protected String packageName = "openapi-client";
    protected String packageVersion = "0.1.0";
    protected String packageDescription = "Клиент API, сгенерированный из спецификации OpenAPI";
    protected String packageAuthor = "";
    protected String packageAuthorEmail = "";
    // jason needs 2.0.0-rc.8+, and every model depends on it.
    protected String environmentVersion = "2.0.0";
    protected String userAgent = "onescript-openapi-generator";
    /** Minimum 1connector the generated transport is written against. */
    protected String connectorVersion = "2.3.3";
    /** Serialises models to JSON from the &Сериализуемое annotations. */
    protected String jasonVersion = "0.6.0";

    public OneScriptClientCodegen() {
        super();

        generatorMetadata = GeneratorMetadata.newBuilder(generatorMetadata)
                .stability(Stability.BETA)
                .build();

        outputFolder = "generated-code" + File.separator + "onescript";
        embeddedTemplateDir = templateDir = "onescript";

        apiTemplateFiles.put("api.mustache", ".os");
        modelTemplateFiles.put("model.mustache", ".os");

        apiDocTemplateFiles.clear();
        modelDocTemplateFiles.clear();

        // OneScript has no namespaces; layout is carried entirely by lib.config.
        apiPackage = "";
        modelPackage = "";
        modelNamePrefix = "";

        hideGenerationTimestamp = Boolean.TRUE;

        // BSL accepts Russian and English keywords interchangeably, so both spellings
        // are reserved. DefaultCodegen compares against this set in lower case.
        reservedWords = new HashSet<>(Arrays.asList(
                // Russian
                "если", "тогда", "иначе", "иначеесли", "конецесли",
                "для", "каждого", "из", "по", "цикл", "конеццикла",
                "пока", "прервать", "продолжить",
                "процедура", "конецпроцедуры", "функция", "конецфункции",
                "возврат", "перем", "знач", "экспорт", "новый",
                "неопределено", "истина", "ложь", "null",
                "и", "или", "не",
                "попытка", "исключение", "конецпопытки", "вызватьисключение",
                "выполнить", "вычислить", "перейти",
                "добавитьобработчик", "удалитьобработчик", "асинх", "ждать",
                // English
                "if", "then", "else", "elsif", "endif",
                "for", "each", "in", "to", "do", "enddo",
                "while", "break", "continue",
                "procedure", "endprocedure", "function", "endfunction",
                "return", "var", "val", "export", "new",
                "undefined", "true", "false",
                "and", "or", "not",
                "try", "except", "endtry", "raise",
                "execute", "eval", "goto",
                "addhandler", "removehandler", "async", "await"
        ));

        languageSpecificPrimitives = new HashSet<>(Arrays.asList(
                "Строка", "Число", "Булево", "Дата",
                "Массив", "Соответствие", "Структура",
                "ДвоичныеДанные", "Произвольный"
        ));

        typeMapping.clear();
        typeMapping.put("string", "Строка");
        typeMapping.put("char", "Строка");
        typeMapping.put("integer", "Число");
        typeMapping.put("long", "Число");
        typeMapping.put("int", "Число");
        typeMapping.put("short", "Число");
        typeMapping.put("number", "Число");
        typeMapping.put("float", "Число");
        typeMapping.put("double", "Число");
        typeMapping.put("decimal", "Число");
        typeMapping.put("boolean", "Булево");
        typeMapping.put("date", "Дата");
        typeMapping.put("Date", "Дата");
        typeMapping.put("DateTime", "Дата");
        typeMapping.put("array", "Массив");
        typeMapping.put("set", "Массив");
        typeMapping.put("list", "Массив");
        typeMapping.put("map", "Соответствие");
        typeMapping.put("object", "Соответствие");
        typeMapping.put("AnyType", "Произвольный");
        typeMapping.put("UUID", "Строка");
        typeMapping.put("URI", "Строка");
        typeMapping.put("ByteArray", "Строка");
        typeMapping.put("binary", "ДвоичныеДанные");
        typeMapping.put("file", "ДвоичныеДанные");

        instantiationTypes.clear();
        instantiationTypes.put("array", "Массив");
        instantiationTypes.put("list", "Массив");
        instantiationTypes.put("map", "Соответствие");

        cliOptions.clear();
        cliOptions.add(new CliOption(PACKAGE_NAME, "Имя пакета opm").defaultValue(packageName));
        cliOptions.add(new CliOption(PACKAGE_VERSION, "Версия пакета").defaultValue(packageVersion));
        cliOptions.add(new CliOption(PACKAGE_DESCRIPTION, "Описание пакета").defaultValue(packageDescription));
        cliOptions.add(new CliOption(PACKAGE_AUTHOR, "Автор пакета").defaultValue(packageAuthor));
        cliOptions.add(new CliOption(PACKAGE_AUTHOR_EMAIL, "Адрес автора").defaultValue(packageAuthorEmail));
        cliOptions.add(new CliOption(ENVIRONMENT_VERSION, "Минимальная версия OneScript").defaultValue(environmentVersion));
        cliOptions.add(new CliOption(USER_AGENT, "Значение заголовка User-Agent").defaultValue(userAgent));
        cliOptions.add(new CliOption(CONNECTOR_VERSION,
                "Минимальная версия библиотеки 1connector").defaultValue(connectorVersion));
        cliOptions.add(new CliOption(JASON_VERSION,
                "Минимальная версия библиотеки jason").defaultValue(jasonVersion));
        cliOptions.add(CliOption.newBoolean(CodegenConstants.HIDE_GENERATION_TIMESTAMP,
                CodegenConstants.HIDE_GENERATION_TIMESTAMP_DESC, true));
    }

    @Override
    public CodegenType getTag() {
        return CodegenType.CLIENT;
    }

    @Override
    public String getName() {
        return "onescript";
    }

    @Override
    public String getHelp() {
        return "Генерирует клиент OneScript (oscript.io) в виде готового пакета opm.";
    }

    @Override
    public void processOpts() {
        super.processOpts();

        packageName = stringOption(PACKAGE_NAME, packageName);
        packageVersion = stringOption(PACKAGE_VERSION, packageVersion);
        packageDescription = stringOption(PACKAGE_DESCRIPTION, packageDescription);
        packageAuthor = stringOption(PACKAGE_AUTHOR, packageAuthor);
        packageAuthorEmail = stringOption(PACKAGE_AUTHOR_EMAIL, packageAuthorEmail);
        environmentVersion = stringOption(ENVIRONMENT_VERSION, environmentVersion);
        userAgent = stringOption(USER_AGENT, userAgent);
        connectorVersion = stringOption(CONNECTOR_VERSION, connectorVersion);
        jasonVersion = stringOption(JASON_VERSION, jasonVersion);

        additionalProperties.put(PACKAGE_NAME, packageName);
        additionalProperties.put(PACKAGE_VERSION, packageVersion);
        additionalProperties.put(PACKAGE_DESCRIPTION, packageDescription);
        putIfPresent(PACKAGE_AUTHOR, packageAuthor);
        putIfPresent(PACKAGE_AUTHOR_EMAIL, packageAuthorEmail);
        additionalProperties.put(ENVIRONMENT_VERSION, environmentVersion);
        additionalProperties.put(USER_AGENT, userAgent);
        additionalProperties.put(CONNECTOR_VERSION, connectorVersion);
        additionalProperties.put(JASON_VERSION, jasonVersion);
        additionalProperties.put("generatorName", getName());

        supportingFiles.add(new SupportingFile("packagedef.mustache", "", "packagedef"));
        supportingFiles.add(new SupportingFile("lib.config.mustache", "", "lib.config"));
        supportingFiles.add(new SupportingFile("README.mustache", "", "README.md"));

        supportingFiles.add(runtimeFile("Конфигурация"));
        supportingFiles.add(runtimeFile("СекретнаяСтрока"));
        supportingFiles.add(runtimeFile("ТранспортHTTP"));
        supportingFiles.add(runtimeFile("ОтветAPI"));
    }

    private SupportingFile runtimeFile(String className) {
        return new SupportingFile("runtime/" + className + ".mustache", CLASS_DIR, className + ".os");
    }

    private void putIfPresent(String key, String value) {
        if (value == null || value.isEmpty()) {
            additionalProperties.remove(key);
        } else {
            additionalProperties.put(key, value);
        }
    }

    private String stringOption(String key, String fallback) {
        Object value = additionalProperties.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    @Override
    public String apiFileFolder() {
        return outputFolder + File.separator + CLASS_DIR;
    }

    @Override
    public String modelFileFolder() {
        return outputFolder + File.separator + MODEL_DIR;
    }

    /**
     * Resolves a schema to the OneScript type that actually exists.
     *
     * <p>Without this, a {@code $ref} yields the bare schema name while the class it
     * generates carries {@code modelNamePrefix} — so an annotation like
     * {@code &Тип("AdvertSettings")} would name a class that was never emitted, and
     * both jason's deserializer and validate's type check would fail on it at runtime.
     */
    @Override
    public String getSchemaType(Schema schema) {
        String openAPIType = super.getSchemaType(schema);

        if (typeMapping.containsKey(openAPIType)) {
            return typeMapping.get(openAPIType);
        }
        if (languageSpecificPrimitives.contains(openAPIType)) {
            return openAPIType;
        }
        return toModelName(openAPIType);
    }

    @Override
    public String escapeReservedWord(String name) {
        // A leading underscore is legal in BSL and keeps the original word readable.
        return "_" + name;
    }

    /**
     * Keeps Cyrillic tags intact. The inherited implementation runs sanitizeName(),
     * which strips non-ASCII — with Russian tags that collapses every group onto the
     * same (often empty) name and merges unrelated operations into one class.
     */
    @Override
    public String sanitizeTag(String tag) {
        String sanitized = pascalCase(tag);
        return sanitized.isEmpty() ? "Default" : sanitized;
    }

    @Override
    public String toApiName(String name) {
        if (name == null || name.isEmpty()) {
            return "ПоУмолчаниюApi";
        }
        return pascalCase(name) + "Api";
    }

    @Override
    public String toApiFilename(String name) {
        return toApiName(name);
    }

    @Override
    public String toApiVarName(String name) {
        return toApiName(name);
    }

    @Override
    public String toModelName(String name) {
        String candidate = pascalCase(modelNamePrefix + " " + name + " " + modelNameSuffix);
        if (candidate.isEmpty()) {
            return "Модель";
        }
        // BSL identifiers may not start with a digit — 409SupplyError becomes
        // Модель409SupplyError rather than being silently mangled.
        if (Character.isDigit(candidate.charAt(0))) {
            candidate = "Модель" + candidate;
        }
        if (isReservedWord(candidate)) {
            candidate = escapeReservedWord(candidate);
        }
        return candidate;
    }

    @Override
    public String toModelFilename(String name) {
        return toModelName(name);
    }

    @Override
    public String toVarName(String name) {
        String candidate = sanitizeIdentifier(name);
        if (candidate.isEmpty()) {
            return "Значение";
        }
        if (Character.isDigit(candidate.charAt(0))) {
            candidate = "Поле" + candidate;
        }
        if (isReservedWord(candidate)) {
            candidate = escapeReservedWord(candidate);
        }
        return candidate;
    }

    @Override
    public String toParamName(String name) {
        return toVarName(name);
    }

    @Override
    public String toOperationId(String operationId) {
        if (operationId == null || operationId.isEmpty()) {
            throw new RuntimeException("Пустой operationId");
        }
        String candidate = sanitizeIdentifier(operationId);
        if (candidate.isEmpty()) {
            candidate = "Операция";
        }
        if (Character.isDigit(candidate.charAt(0))) {
            candidate = "Операция" + candidate;
        }
        candidate = Character.toUpperCase(candidate.charAt(0)) + candidate.substring(1);
        if (isReservedWord(candidate)) {
            candidate = escapeReservedWord(candidate);
        }
        return candidate;
    }

    @Override
    public String toEnumVarName(String value, String datatype) {
        if (value == null || value.isEmpty()) {
            return "Пусто";
        }
        String candidate = sanitizeIdentifier(value);
        if (candidate.isEmpty()) {
            // Enum values may be entirely non-identifier characters ("=", "<=").
            // Fall back to a stable transliteration of the code points.
            StringBuilder builder = new StringBuilder("Значение");
            for (int index = 0; index < value.length(); index++) {
                builder.append('_').append((int) value.charAt(index));
            }
            candidate = builder.toString();
        }
        if (Character.isDigit(candidate.charAt(0))) {
            candidate = "Значение" + candidate;
        }
        if (isReservedWord(candidate)) {
            candidate = escapeReservedWord(candidate);
        }
        return candidate;
    }

    @Override
    public String escapeText(String input) {
        if (input == null) {
            return null;
        }
        return escapeTextWhileAllowingNewLines(input);
    }

    @Override
    public String escapeQuotationMark(String input) {
        // BSL escapes a double quote by doubling it.
        return input.replace("\"", "\"\"");
    }

    @Override
    public String escapeUnsafeCharacters(String input) {
        // BSL has no block comments, so there is no comment terminator to escape.
        return input;
    }

    /** Captures the document-level server so Конфигурация can default to it. */
    @Override
    public void preprocessOpenAPI(OpenAPI openAPI) {
        super.preprocessOpenAPI(openAPI);

        String url = "";
        if (openAPI.getServers() != null && !openAPI.getServers().isEmpty()) {
            String candidate = openAPI.getServers().get(0).getUrl();
            if (candidate != null) {
                url = trimTrailingSlash(candidate);
            }
        }
        putIfPresent("osDefaultBaseUrl", url);
    }

    @Override
    public OperationsMap postProcessOperationsWithModels(OperationsMap objs, List<ModelMap> allModels) {
        OperationsMap result = super.postProcessOperationsWithModels(objs, allModels);
        OperationMap operations = result.getOperations();

        for (CodegenOperation operation : operations.getOperation()) {
            // Per-path and per-operation `servers:` override the document default; WB,
            // for instance, fronts each API category with a different host. This is the
            // address baked into the operation — Конфигурация.БазовыйURL, when set,
            // still wins over it at runtime so a client can be pointed at a sandbox.
            String baseUrl = firstServerUrl(operation.servers);
            if (baseUrl == null) {
                Object documentUrl = additionalProperties.get("osDefaultBaseUrl");
                baseUrl = documentUrl == null ? "" : String.valueOf(documentUrl);
            }
            operation.vendorExtensions.put("x-os-base-url", baseUrl);

            List<CodegenParameter> requiredQuery = new ArrayList<>();
            List<CodegenParameter> requiredHeader = new ArrayList<>();
            List<CodegenParameter> optionalQuery = new ArrayList<>();
            List<CodegenParameter> optionalHeader = new ArrayList<>();
            List<CodegenParameter> positional = new ArrayList<>();
            List<CodegenParameter> optional = new ArrayList<>();

            for (CodegenParameter parameter : operation.allParams) {
                if (parameter.isBodyParam) {
                    continue;
                }
                if (parameter.required) {
                    positional.add(parameter);
                    if (parameter.isQueryParam) {
                        requiredQuery.add(parameter);
                    } else if (parameter.isHeaderParam) {
                        requiredHeader.add(parameter);
                    }
                } else {
                    optional.add(parameter);
                    if (parameter.isHeaderParam) {
                        optionalHeader.add(parameter);
                    } else {
                        optionalQuery.add(parameter);
                    }
                }
            }

            operation.vendorExtensions.put("x-os-required-query", requiredQuery);
            operation.vendorExtensions.put("x-os-required-header", requiredHeader);
            operation.vendorExtensions.put("x-os-optional-query", optionalQuery);
            operation.vendorExtensions.put("x-os-optional-header", optionalHeader);
            operation.vendorExtensions.put("x-os-has-optional", !optional.isEmpty());
            operation.vendorExtensions.put("x-os-signature",
                    buildSignature(positional, operation.bodyParam != null, !optional.isEmpty()));
            operation.vendorExtensions.put("x-os-path-template", toPathTemplate(operation));
            operation.vendorExtensions.put("x-os-doc",
                    buildOperationDoc(operation, positional, optional));
        }

        result.put("osClassDoc",
                buildHeaderDoc(operations.getClassname(), asText(objs.get("description"))));

        return result;
    }

    /** Case-insensitive identifiers mean two properties can collide where JSON keys do not. */
    @Override
    public ModelsMap postProcessModels(ModelsMap objs) {
        ModelsMap result = super.postProcessModels(objs);

        for (ModelMap modelMap : result.getModels()) {
            CodegenModel model = modelMap.getModel();
            Map<String, Integer> seen = new HashMap<>();

            for (CodegenProperty property : model.vars) {
                String key = property.name.toLowerCase(Locale.ROOT);
                Integer count = seen.get(key);
                if (count == null) {
                    seen.put(key, 1);
                } else {
                    seen.put(key, count + 1);
                    property.name = property.name + "_" + count;
                }
                property.vendorExtensions.put("x-os-doc", buildPropertyDoc(property));
                property.vendorExtensions.put("x-os-annotations", buildAnnotations(property));
            }

            model.vendorExtensions.put("x-os-doc",
                    buildHeaderDoc(model.classname, model.description));
        }

        return result;
    }

    private String firstServerUrl(List<CodegenServer> servers) {
        if (servers == null || servers.isEmpty()) {
            return null;
        }
        String url = servers.get(0).url;
        if (url == null || url.isEmpty()) {
            return null;
        }
        return trimTrailingSlash(url);
    }

    private String trimTrailingSlash(String url) {
        String result = url;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /**
     * Builds the BSL parameter list: required parameters first, then the request body,
     * then a single optional collection carrying every optional query/header parameter.
     */
    private String buildSignature(List<CodegenParameter> positional, boolean hasBody, boolean hasOptional) {
        List<String> parts = new ArrayList<>();
        for (CodegenParameter parameter : positional) {
            parts.add("Знач " + parameter.paramName);
        }
        if (hasBody) {
            parts.add("Знач Тело");
        }
        if (hasOptional) {
            parts.add("Знач ДопПараметры = Неопределено");
        }
        return String.join(", ", parts);
    }

    /**
     * Rewrites {@code /orders/{orderId}/meta} into a BSL string-template call so path
     * parameters are substituted and URL-encoded at runtime.
     */
    private String toPathTemplate(CodegenOperation operation) {
        String path = operation.path;
        List<CodegenParameter> pathParams = operation.pathParams;
        if (pathParams == null || pathParams.isEmpty()) {
            return "\"" + escapeQuotationMark(path) + "\"";
        }

        List<String> arguments = new ArrayList<>();
        int index = 1;
        for (CodegenParameter parameter : pathParams) {
            String placeholder = "{" + parameter.baseName + "}";
            if (!path.contains(placeholder)) {
                continue;
            }
            path = path.replace(placeholder, "%" + index);
            arguments.add("Транспорт.ЭкранироватьСегмент(" + parameter.paramName + ")");
            index++;
        }

        if (arguments.isEmpty()) {
            return "\"" + escapeQuotationMark(path) + "\"";
        }

        StringBuilder template = new StringBuilder();
        template.append("СтрШаблон(\"").append(escapeQuotationMark(path)).append("\"");
        for (String argument : arguments) {
            template.append(", ").append(argument);
        }
        template.append(")");
        return template.toString();
    }

    /**
     * Renders the whole doc comment for an operation. Built here rather than in the
     * template because descriptions are free-form Markdown: every line has to be
     * re-prefixed with {@code //}, and BSL has no block-comment form to fall back on.
     */
    private List<String> buildOperationDoc(CodegenOperation operation,
                                           List<CodegenParameter> positional,
                                           List<CodegenParameter> optional) {
        List<String> lines = new ArrayList<>();

        appendCommentBlock(lines, operation.summary);
        if (operation.notes != null && !operation.notes.trim().isEmpty()) {
            if (!lines.isEmpty()) {
                lines.add("//");
            }
            appendCommentBlock(lines, operation.notes);
        }

        boolean hasBody = operation.bodyParam != null;
        if (!positional.isEmpty() || hasBody || !optional.isEmpty()) {
            if (!lines.isEmpty()) {
                lines.add("//");
            }
            lines.add("// Параметры:");
            for (CodegenParameter parameter : positional) {
                lines.add(docRow("//   ", parameter.paramName, displayType(parameter), parameter.description));
            }
            if (hasBody) {
                lines.add(docRow("//   ", "Тело", displayType(operation.bodyParam),
                        operation.bodyParam.description));
            }
            if (!optional.isEmpty()) {
                lines.add("//   ДопПараметры - Структура, Соответствие - необязательные параметры:");
                for (CodegenParameter parameter : optional) {
                    lines.add(docRow("//    * ", parameter.baseName, displayType(parameter),
                            parameter.description));
                }
            }
        }

        if (!lines.isEmpty()) {
            lines.add("//");
        }
        lines.add("// Возвращаемое значение:");
        lines.add("//   ОтветAPI");
        lines.add("//");

        return lines;
    }

    /**
     * Renders a class-header comment. Descriptions are free-form Markdown and may span
     * lines, so every line is prefixed with {@code //} instead of being interpolated
     * into a single comment line.
     */
    private List<String> buildHeaderDoc(String className, String description) {
        List<String> lines = new ArrayList<>();
        lines.add("// " + className);
        if (description != null && !description.trim().isEmpty()) {
            lines.add("//");
            appendCommentBlock(lines, description);
        }
        return lines;
    }

    private String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * Renders the annotations jason reads: the JSON name from {@code &Сериализуемое},
     * and the target class from {@code &Тип} / {@code &ДляКаждого} so nested models and
     * arrays of models deserialize as themselves rather than as Структура.
     *
     * <p>Those two annotations are defined by validate, which jason depends on and
     * loads transitively — the generated package needs neither the import nor the
     * dependency of its own.
     */
    private List<String> buildAnnotations(CodegenProperty property) {
        List<String> lines = new ArrayList<>();

        // The spec's name, not the BSL one: toVarName may have had to sanitize it.
        lines.add("&Сериализуемое(\"" + escapeQuotationMark(property.baseName) + "\")");
        lines.add("&Тип(\"" + escapeQuotationMark(property.dataType) + "\")");

        if (property.isArray && property.items != null) {
            // &ДляКаждого switches the following annotations onto the elements.
            lines.add("&ДляКаждого");
            lines.add("&Тип(\"" + escapeQuotationMark(property.items.dataType) + "\")");
        }

        return lines;
    }

    private List<String> buildPropertyDoc(CodegenProperty property) {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add(docRow("// ", property.baseName, displayType(property), property.description));
        return lines;
    }

    private String docRow(String prefix, String name, String type, String description) {
        String text = brief(description);
        return prefix + name + " - " + type + (text.isEmpty() ? "" : " - " + text);
    }

    private String displayType(CodegenParameter parameter) {
        return parameter.dataType == null ? "Произвольный" : parameter.dataType;
    }

    private String displayType(CodegenProperty property) {
        return property.dataType == null ? "Произвольный" : property.dataType;
    }

    /** Squashes a free-form description into one comment-safe line. */
    private String brief(String description) {
        if (description == null) {
            return "";
        }
        String single = description.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ').trim();
        single = single.replaceAll("\\s{2,}", " ");
        if (single.length() > 160) {
            single = single.substring(0, 160).trim() + "…";
        }
        return single;
    }

    private void appendCommentBlock(List<String> lines, String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        for (String line : text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            String trimmed = line.trim();
            lines.add(trimmed.isEmpty() ? "//" : "// " + trimmed);
        }
    }

    /**
     * Adds every generated class to the data backing lib.config and packagedef, so a
     * new spec surfaces in both manifests without hand editing.
     */
    @Override
    public Map<String, Object> postProcessSupportingFileData(Map<String, Object> objs) {
        Map<String, Object> result = super.postProcessSupportingFileData(objs);

        // TreeMap keeps the manifests byte-stable across runs; openapi-generator does
        // not guarantee an order for these collections, and a flapping manifest would
        // defeat any regenerate-and-diff check downstream.
        Map<String, String> classes = new TreeMap<>();

        for (String runtimeClass : Arrays.asList(
                "Конфигурация", "СекретнаяСтрока", "ТранспортHTTP", "ОтветAPI")) {
            classes.put(runtimeClass, posix(CLASS_DIR) + "/" + runtimeClass + ".os");
        }

        Object apiInfo = objs.get("apiInfo");
        if (apiInfo instanceof Map) {
            Object apis = ((Map<?, ?>) apiInfo).get("apis");
            if (apis instanceof List) {
                for (Object entry : (List<?>) apis) {
                    if (!(entry instanceof Map)) {
                        continue;
                    }
                    Object className = ((Map<?, ?>) entry).get("classname");
                    if (className != null) {
                        classes.put(String.valueOf(className),
                                posix(CLASS_DIR) + "/" + className + ".os");
                    }
                }
            }
        }

        Object models = objs.get("models");
        if (models instanceof List) {
            for (Object entry : (List<?>) models) {
                if (!(entry instanceof Map)) {
                    continue;
                }
                Object model = ((Map<?, ?>) entry).get("model");
                if (model instanceof CodegenModel) {
                    String className = ((CodegenModel) model).classname;
                    classes.put(className, posix(MODEL_DIR) + "/" + className + ".os");
                }
            }
        }

        List<Map<String, String>> registry = new ArrayList<>();
        for (Map.Entry<String, String> entry : classes.entrySet()) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("className", entry.getKey());
            row.put("classFile", entry.getValue());
            registry.add(row);
        }

        result.put("osClasses", registry);
        return result;
    }

    private String posix(String path) {
        return path.replace(File.separatorChar, '/');
    }

    /**
     * Folds an arbitrary label into a PascalCase BSL identifier. Cyrillic is preserved
     * — it is legal in BSL and is what OneScript code normally looks like — so the tag
     * "Категории, предметы и характеристики" becomes КатегорииПредметыИХарактеристики.
     */
    private String pascalCase(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        StringBuilder word = new StringBuilder();
        boolean startOfWord = true;

        for (int index = 0; index < input.length(); index++) {
            char symbol = input.charAt(index);
            if (Character.isLetterOrDigit(symbol)) {
                if (startOfWord) {
                    word.append(Character.toUpperCase(symbol));
                    startOfWord = false;
                } else {
                    word.append(symbol);
                }
            } else {
                result.append(word);
                word.setLength(0);
                startOfWord = true;
            }
        }
        result.append(word);
        return result.toString();
    }

    /** Strips everything BSL would reject from an identifier, preserving letter case. */
    private String sanitizeIdentifier(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = false;
        for (int index = 0; index < input.length(); index++) {
            char symbol = input.charAt(index);
            if (Character.isLetterOrDigit(symbol) || symbol == '_') {
                result.append(capitalizeNext ? Character.toUpperCase(symbol) : symbol);
                capitalizeNext = false;
            } else {
                capitalizeNext = result.length() > 0;
            }
        }
        return result.toString();
    }
}
