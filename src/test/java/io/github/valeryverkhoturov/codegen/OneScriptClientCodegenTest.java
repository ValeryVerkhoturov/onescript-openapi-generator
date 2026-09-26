package io.github.valeryverkhoturov.codegen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the naming rules, where BSL differs most from the languages DefaultCodegen assumes. */
class OneScriptClientCodegenTest {

    private final OneScriptClientCodegen codegen = new OneScriptClientCodegen();

    @Test
    void registersItselfUnderTheCliName() {
        assertEquals("onescript", codegen.getName());
    }

    @Test
    void keepsCyrillicInTagNames() {
        // sanitizeName() in DefaultCodegen strips non-ASCII, which would fold every
        // Russian tag onto the same name and merge unrelated operations into one class.
        assertEquals("СборочныеЗаданияFBS", codegen.sanitizeTag("Сборочные задания FBS"));
        assertEquals("КатегорииПредметыИХарактеристики",
                codegen.sanitizeTag("Категории, предметы и характеристики"));
    }

    @Test
    void namesApiClassesFromTags() {
        assertEquals("ПоставкиFBSApi", codegen.toApiName("Поставки FBS"));
        assertEquals("ПоУмолчаниюApi", codegen.toApiName(""));
    }

    @Test
    void prefixesModelNamesThatWouldStartWithADigit() {
        // BSL identifiers may not start with a digit.
        assertEquals("Модель409SupplyDeliverError", codegen.toModelName("409SupplyDeliverError"));
    }

    @Test
    void collapsesUnderscoresInGeneratedModelNames() {
        // openapi-generator names inline schemas Foo_bar_inner.
        assertEquals("ArhiveOrderError400ErrorsInner",
                codegen.toModelName("ArhiveOrderError400_errors_inner"));
    }

    @Test
    void usesPascalCaseForOperations() {
        assertEquals("GetV3Orders", codegen.toOperationId("getV3Orders"));
    }

    @Test
    void escapesReservedWordsInBothLanguages() {
        // BSL accepts Russian and English keywords interchangeably, so both spellings
        // have to be escaped out of the way.
        assertEquals("_Если", codegen.toVarName("Если"));
        assertEquals("_Function", codegen.toVarName("Function"));
        assertEquals("_Выполнить", codegen.toVarName("Выполнить"));
    }

    @Test
    void sanitizesNamesIntoLegalIdentifiers() {
        assertEquals("dateFrom", codegen.toParamName("dateFrom"));
        assertEquals("XTrace", codegen.toParamName("X-Trace"));
        assertEquals("Поле1Value", codegen.toVarName("1-value"));
    }

    @Test
    void doublesQuotesWhenEscaping() {
        // BSL escapes a double quote by doubling it; there is no backslash escape.
        assertEquals("a\"\"b", codegen.escapeQuotationMark("a\"b"));
    }

    @Test
    void keepsLineBreaksInDescriptions() {
        // Descriptions become BSL line comments and BSL has no block-comment form,
        // so the line structure has to survive escaping.
        assertTrue(codegen.escapeText("первая\nвторая").contains("\n"));
    }

    @Test
    void putsApisAndModelsInSeparateFolders() {
        assertTrue(codegen.apiFileFolder().replace('\\', '/').endsWith("src/Классы"));
        assertTrue(codegen.modelFileFolder().replace('\\', '/').endsWith("src/Модели"));
    }
}
