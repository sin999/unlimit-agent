package pt.sin.services.unlimitagent.repository;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Repository;
import org.apache.commons.collections4.CollectionUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class IncidentVectorRepository implements IncidentRepository {

    private static final String SYSTEM_DESC_RESOURCE = "classpath:knowledge/system_description.txt";
    private static final int    SIMILARITY_TOP_K      = 3;
    private static final String NO_INCIDENTS_FALLBACK = "No relevant past incidents found.";

    private final VectorStore vectorStore;
    private final String systemDescription;

    public IncidentVectorRepository(ResourceLoader resourceLoader, VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        this.systemDescription = loadSystemDescription(resourceLoader);
    }

    private static String loadSystemDescription(ResourceLoader loader) {
        var resource = loader.getResource(SYSTEM_DESC_RESOURCE);
        if (!resource.exists()) {
            throw new IllegalStateException(
                    "Required file not found: " + SYSTEM_DESC_RESOURCE);
        }
        try {
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            if (content == null || content.isBlank()) {
                throw new IllegalStateException(SYSTEM_DESC_RESOURCE + " is blank");
            }
            return content;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + SYSTEM_DESC_RESOURCE, e);
        }
    }

    @Override
    public String getSystemDescription() {
        return systemDescription;
    }

    @Override
    public String findSimilarIncidents(String query) {
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(SIMILARITY_TOP_K).build());
        if (CollectionUtils.isEmpty(results)) {
            return NO_INCIDENTS_FALLBACK;
        }
        return results.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));
    }

    @Override
    public void save(String incidentId, String text) {
        vectorStore.add(List.of(new Document(incidentId, text,
                Map.of(METADATA_INCIDENT_ID, incidentId))));
    }
}
