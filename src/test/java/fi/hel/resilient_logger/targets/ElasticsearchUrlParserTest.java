package fi.hel.resilient_logger.targets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import fi.hel.resilient_logger.targets.ElasticsearchLogTarget.HostInfo;

class ElasticsearchUrlParserTest {

    @Test
    void usesFallbacksWhenUrlIsAbsent() {
        HostInfo info = ElasticsearchLogTarget.parseHostInfo(null, "https", "es.local", 9200);
        assertEquals("https", info.scheme());
        assertEquals("es.local", info.host());
        assertEquals(9200, info.port());
    }

    @Test
    void usesFallbacksWhenUrlIsEmpty() {
        HostInfo info = ElasticsearchLogTarget.parseHostInfo("", "http", "fallback", 1234);
        assertEquals("http", info.scheme());
        assertEquals("fallback", info.host());
        assertEquals(1234, info.port());
    }

    @Test
    void parsesPlainHostWithoutScheme() {
        HostInfo info = ElasticsearchLogTarget.parseHostInfo("es.example.com", "https", "ignored", 9200);
        assertEquals("https", info.scheme());
        assertEquals("es.example.com", info.host());
        assertEquals(9200, info.port(), "no explicit port → falls back");
    }

    @Test
    void parsesFullUrlWithSchemeAndPort() {
        HostInfo info = ElasticsearchLogTarget.parseHostInfo(
                "http://es.example.com:9201", "https", "ignored", 9200);
        assertEquals("http", info.scheme());
        assertEquals("es.example.com", info.host());
        assertEquals(9201, info.port());
    }

    @Test
    void fallsBackToDefaultPortWhenUrlHasNone() {
        HostInfo info = ElasticsearchLogTarget.parseHostInfo(
                "http://es.example.com", "https", "ignored", 9200);
        assertEquals("http", info.scheme());
        assertEquals("es.example.com", info.host());
        assertEquals(9200, info.port());
    }

    @Test
    void rejectsUrlContainingPath() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ElasticsearchLogTarget.parseHostInfo(
                        "http://localhost:9200/audit-logs", "https", "ignored", 9200));
        assertEquals(
                "Configuration error: 'es_url' must not contain a path component: http://localhost:9200/audit-logs",
                ex.getMessage());
    }

    @Test
    void rejectsUrlWithoutHost() {
        // "://no-host" parses but leaves host null
        assertThrows(IllegalArgumentException.class,
                () -> ElasticsearchLogTarget.parseHostInfo("http://", "https", "ignored", 9200));
    }
}
