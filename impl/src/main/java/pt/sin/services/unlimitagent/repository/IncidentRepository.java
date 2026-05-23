package pt.sin.services.unlimitagent.repository;

public interface IncidentRepository {

    String METADATA_INCIDENT_ID = "incidentId";

    String getSystemDescription();

    String findSimilarIncidents(String query);

    void save(String incidentId, String text);
}
