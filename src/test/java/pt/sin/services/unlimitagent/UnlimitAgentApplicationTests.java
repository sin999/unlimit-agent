package pt.sin.services.unlimitagent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class UnlimitAgentApplicationTests {

    @MockitoBean
    VectorStore vectorStore;

    // ── X-1 ─────────────────────────────────────────────────────────────────
    @Test
    void contextLoads() {
        // Verifies the full Spring application context starts without errors.
        // VectorStore is mocked to prevent real ChromaDB connection.
        // PastIncidentSeeder will attempt to seed via the mock (no-op).
    }
}
