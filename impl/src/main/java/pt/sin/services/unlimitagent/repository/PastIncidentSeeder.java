package pt.sin.services.unlimitagent.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.collections4.CollectionUtils;

@Component
public class PastIncidentSeeder implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(PastIncidentSeeder.class);
    private static final Pattern INCIDENT_ID_PATTERN = Pattern.compile("\\[(INC-\\d+)\\]");

    private final VectorStore vectorStore;
    private final Resource pastIncidentsResource;

    public PastIncidentSeeder(
            VectorStore vectorStore,
            @Value("${knowledge.past-incidents.resource:classpath:knowledge/past_incidents.txt}") Resource pastIncidentsResource) {
        this.vectorStore = vectorStore;
        this.pastIncidentsResource = pastIncidentsResource;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (collectionHasDocuments()) {
            log.debug("Vector store already seeded; skipping");
            return;
        }
        seed();
    }

    boolean collectionHasDocuments() {
        return CollectionUtils.isNotEmpty(vectorStore.similaritySearch(
                SearchRequest.builder().query("incident").topK(1).similarityThreshold(0.0).build()));
    }

    private void seed() {
        if (!pastIncidentsResource.exists()) {
            throw new IllegalStateException(
                    pastIncidentsResource.getDescription() + " not found and vector store is empty — cannot start");
        }
        try {
            String content = pastIncidentsResource.getContentAsString(StandardCharsets.UTF_8);
            List<Document> documents = parseIncidents(content);
            if (documents.isEmpty()) {
                throw new IllegalStateException(
                        pastIncidentsResource.getDescription() + " produced no documents — cannot start");
            }
            vectorStore.add(documents);
            log.debug("Seeded {} past incidents into vector store", documents.size());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + pastIncidentsResource.getDescription(), e);
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
                    Map.of(IncidentRepository.METADATA_INCIDENT_ID, incidentId)));
        }
        return documents;
    }
}
