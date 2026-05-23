package pt.sin.services.unlimitagent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
public class ChromaConfig {

    @Bean
    public ChromaApi chromaApi(
            @Value("${spring.ai.vectorstore.chroma.client.host:http://localhost}") String host,
            @Value("${spring.ai.vectorstore.chroma.client.port:8000}") int port,
            ObjectMapper objectMapper) {
        // Force HTTP/1.1: ChromaDB's uvicorn rejects HTTP/2 upgrade requests with 400
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        RestClient.Builder builder = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(httpClient));
        return new ChromaApi(host + ":" + port, builder, objectMapper) {
            @Override
            public ChromaApi.Collection getCollection(String tenant, String database, String collectionName) {
                try {
                    return super.getCollection(tenant, database, collectionName);
                } catch (RuntimeException e) {
                    // Spring AI 1.0.0 bug: getCollection() throws instead of returning null
                    // when the collection doesn't exist, preventing initialize-schema=true
                    // from creating it. Return null so the create path is reached.
                    return null;
                }
            }
        };
    }
}
