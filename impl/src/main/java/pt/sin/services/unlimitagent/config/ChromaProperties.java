package pt.sin.services.unlimitagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.ai.vectorstore.chroma")
public record ChromaProperties(Client client, String collectionName) {

    public record Client(String host, int port) {}
}
