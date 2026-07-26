package pl.km.application.port.in;

public interface IngestDocumentPort {
    void ingest(String name, String content);
}
