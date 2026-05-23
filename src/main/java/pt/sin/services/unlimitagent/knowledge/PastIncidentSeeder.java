package pt.sin.services.unlimitagent.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PastIncidentSeeder implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(PastIncidentSeeder.class);
    private static final Pattern INCIDENT_ID_PATTERN = Pattern.compile("\\[(INC-\\d+)\\]");

    private static final String PAST_INCIDENTS_RESOURCE = "classpath:knowledge/past_incidents.txt";
    private static final String CHROMA_COUNT_URI        = "/api/v1/collections/{name}/count";

    private final VectorStore vectorStore;
    private final ResourceLoader resourceLoader;
    private final RestClient chromaRestClient;
    private final String collectionName;

    public PastIncidentSeeder(
            VectorStore vectorStore,
            ResourceLoader resourceLoader,
            @Value("${spring.ai.vectorstore.chroma.client.host:localhost}") String chromaHost,
            @Value("${spring.ai.vectorstore.chroma.client.port:8000}") int chromaPort,
            @Value("${spring.ai.vectorstore.chroma.collection-name:past-incidents}") String collectionName) {
        this.vectorStore = vectorStore;
        this.resourceLoader = resourceLoader;
        this.collectionName = collectionName;
        this.chromaRestClient = RestClient.builder()
                .baseUrl(chromaHost + ":" + chromaPort)
                .build();
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (collectionHasDocuments()) {
            log.debug("Collection '{}' already seeded; skipping", collectionName);
            return;
        }
        seed();
    }

    boolean collectionHasDocuments() {
        try {
            Integer count = chromaRestClient.get()
                    .uri(CHROMA_COUNT_URI, collectionName)
                    .retrieve()
                    .body(Integer.class);
            return count != null && count > 0;
        } catch (Exception e) {
            log.debug("Could not query collection count ({}); assuming empty", e.getMessage());
            return false;
        }
    }

    private void seed() {
        Resource resource = resourceLoader.getResource(PAST_INCIDENTS_RESOURCE);
        if (!resource.exists()) {
            throw new IllegalStateException(
                    PAST_INCIDENTS_RESOURCE + " not found and vector store is empty — cannot start");
        }
        try {
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            List<Document> documents = parseIncidents(content);
            if (documents.isEmpty()) {
                throw new IllegalStateException(
                        PAST_INCIDENTS_RESOURCE + " produced no documents — cannot start");
            }
            vectorStore.add(documents);
            log.debug("Seeded {} past incidents into vector store", documents.size());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + PAST_INCIDENTS_RESOURCE, e);
        }
    }

    List<Document> parseIncidents(String content) {
        List<Document> documents = new ArrayList<>();
        String[] blocks = content.split("(?=\\[INC-)");
        for (String block : blocks) {
            String trimmed = block.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Matcher m = INCIDENT_ID_PATTERN.matcher(trimmed);
            String incidentId = m.find() ? m.group(1) : null;
            if (incidentId == null) {
                continue;
            }
            documents.add(new Document(incidentId, trimmed,
                    Map.of(KnowledgeBase.METADATA_INCIDENT_ID, incidentId)));
        }
        return documents;
    }
}
