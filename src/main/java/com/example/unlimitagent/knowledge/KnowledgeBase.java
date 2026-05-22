package com.example.unlimitagent.knowledge;

import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class KnowledgeBase {

    private final String systemDescription;
    private final String pastIncidents;

    public KnowledgeBase(ResourceLoader resourceLoader) {
        this.systemDescription = load(resourceLoader, "classpath:knowledge/system_description.txt");
        this.pastIncidents = load(resourceLoader, "classpath:knowledge/past_incidents.txt");
    }

    public String getSystemDescription() {
        return systemDescription;
    }

    public String getPastIncidents() {
        return pastIncidents;
    }

    private String load(ResourceLoader resourceLoader, String path) {
        try {
            var resource = resourceLoader.getResource(path);
            if (!resource.exists()) {
                throw new IllegalStateException("Knowledge file not found: " + path);
            }
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            if (content == null || content.isBlank()) {
                throw new IllegalStateException("Knowledge file is empty: " + path);
            }
            return content;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read knowledge file: " + path, e);
        }
    }
}
