package pt.sin.services.unlimitagent.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PastIncidentSeederTest {

    @Mock VectorStore vectorStore;

    private PastIncidentSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new PastIncidentSeeder(
                vectorStore,
                new DefaultResourceLoader().getResource("classpath:knowledge/past_incidents.txt"));
    }

    // ── S-1 ─────────────────────────────────────────────────────────────────
    @Test
    void parsesIncidentBlocks_correctly() {
        String content = """
                [INC-101] Timeout issue
                Symptoms: timeouts
                Root cause: PayGate
                Category: External

                [INC-102] DB load issue
                Symptoms: slow queries
                Root cause: reporting
                Category: DB
                """;

        List<Document> docs = seeder.parseIncidents(content);

        assertThat(docs).hasSize(2);
        assertThat(docs.get(0).getMetadata()).containsEntry("incidentId", "INC-101");
        assertThat(docs.get(1).getMetadata()).containsEntry("incidentId", "INC-102");
    }

    // ── S-2 ─────────────────────────────────────────────────────────────────
    @Test
    void skipsEmptyBlocks() {
        String content = "\n\n   \n[INC-101] Timeout\nSymptoms: s\n";

        List<Document> docs = seeder.parseIncidents(content);

        assertThat(docs).hasSize(1);
    }

    // ── S-3 ─────────────────────────────────────────────────────────────────
    @Test
    void skipsSeeding_whenAlreadySeeded() {
        PastIncidentSeeder spy = spy(seeder);
        doReturn(true).when(spy).collectionHasDocuments();

        spy.onApplicationEvent(null);

        verify(vectorStore, never()).add(any());
    }
}
