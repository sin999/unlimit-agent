package pt.sin.services.unlimitagent.knowledge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseTest {

    @Mock ResourceLoader resourceLoader;
    @Mock Resource sysDescResource;
    @Mock VectorStore vectorStore;

    @BeforeEach
    void setUp() throws IOException {
        when(resourceLoader.getResource("classpath:knowledge/system_description.txt"))
                .thenReturn(sysDescResource);
        when(sysDescResource.exists()).thenReturn(true);
        lenient().when(sysDescResource.getContentAsString(StandardCharsets.UTF_8))
                .thenReturn("System description text.");
    }

    private KnowledgeBase build() {
        return new KnowledgeBase(resourceLoader, vectorStore);
    }

    // ── K-1 ─────────────────────────────────────────────────────────────────
    @Test
    void returnsRelevantPastIncidents() {
        Document d1 = new Document("INC-101", "INC-101 content", Map.of());
        Document d2 = new Document("INC-102", "INC-102 content", Map.of());
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(d1, d2));

        String result = build().getPastIncidents("card payments failing");

        assertThat(result).contains("INC-101 content");
        assertThat(result).contains("INC-102 content");
    }

    // ── K-2 ─────────────────────────────────────────────────────────────────
    @Test
    void fallback_whenNoResults() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        String result = build().getPastIncidents("something obscure");

        assertThat(result).isEqualTo("No relevant past incidents found.");
    }

    // ── K-3 ─────────────────────────────────────────────────────────────────
    @Test
    void returnsSystemDescription() {
        assertThat(build().getSystemDescription()).isEqualTo("System description text.");
    }

    // ── K-4 ─────────────────────────────────────────────────────────────────
    @Test
    void missingSystemDescriptionFile_failsAtStartup() {
        when(sysDescResource.exists()).thenReturn(false);

        assertThatThrownBy(this::build)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("system_description.txt");
    }
}
