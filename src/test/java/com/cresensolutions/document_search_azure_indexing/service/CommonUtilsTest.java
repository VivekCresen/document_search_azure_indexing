package com.cresensolutions.document_search_azure_indexing.service;

import com.cresensolutions.document_search_azure_indexing.utils.CommonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CommonUtilsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testHasText() {
        assertTrue(CommonUtils.hasText("hello"));
        assertFalse(CommonUtils.hasText(null));
        assertFalse(CommonUtils.hasText("   "));
    }

    @Test
    void testStringValue() {
        assertEquals("test", CommonUtils.stringValue("test"));
        assertEquals("", CommonUtils.stringValue(null));
    }

    @Test
    void testDefaultString() {
        assertEquals("fallback", CommonUtils.defaultString(null, "fallback"));
        assertEquals("fallback", CommonUtils.defaultString("   ", "fallback"));
        assertEquals("original", CommonUtils.defaultString("original", "fallback"));
    }

    @Test
    void testNormalizeWhitespace() {
        assertEquals("hello world", CommonUtils.normalizeWhitespace("  hello   world  "));
        assertEquals("", CommonUtils.normalizeWhitespace(null));
    }

    @Test
    void testTrimTrailingSlash() {
        assertEquals("https://endpoint", CommonUtils.trimTrailingSlash("https://endpoint/"));
        assertEquals("https://endpoint", CommonUtils.trimTrailingSlash("https://endpoint"));
        assertEquals("", CommonUtils.trimTrailingSlash(null));
    }

    @Test
    void testExtractJsonObject() {
        String mdJson = "```json\n{\"key\": \"value\"}\n```";
        assertEquals("{\"key\": \"value\"}", CommonUtils.extractJsonObject(mdJson));
    }

    @Test
    void testParseLlmJson() throws Exception {
        String mdJson = "```json\n{\"name\": \"John\"}\n```";
        Person person = CommonUtils.parseLlmJson(mdJson, Person.class, objectMapper);
        assertNotNull(person);
        assertEquals("John", person.getName());
    }

    @Test
    void testFilename() {
        assertEquals("document.pdf", CommonUtils.filename("folders/subfolders/document.pdf"));
        assertEquals("file.txt", CommonUtils.filename("file.txt"));
        assertEquals("", CommonUtils.filename(null));
    }

    @Test
    void testSimpleUuid() {
        String uuid = CommonUtils.simpleUuid();
        assertNotNull(uuid);
        assertEquals(32, uuid.length());
        assertFalse(uuid.contains("-"));
    }

    @Test
    void testFileExtension() {
        assertEquals("pdf", CommonUtils.fileExtension("document.pdf"));
        assertEquals("docx", CommonUtils.fileExtension("folders/subfolders/report.DOCX"));
        assertEquals("", CommonUtils.fileExtension("no_extension"));
        assertEquals("", CommonUtils.fileExtension(null));
    }

    @Test
    void testSha256() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", CommonUtils.sha256(""));
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", CommonUtils.sha256("hello"));
        assertEquals("", CommonUtils.sha256(null));
    }

    @Test
    void testBase64UrlEncode() {
        assertEquals("aGVsbG8", CommonUtils.base64UrlEncode("hello"));
        assertEquals("aGVsbG8gd29ybGQ", CommonUtils.base64UrlEncode("hello world"));
        assertEquals("", CommonUtils.base64UrlEncode(null));
    }

    private static class Person {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
