package pl.km.application.port.in;

import java.io.InputStream;

public interface IngestFilePort {
    void ingest(String filename, InputStream inputStream);
}
