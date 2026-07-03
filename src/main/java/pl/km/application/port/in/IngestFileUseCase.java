package pl.km.application.port.in;

import java.io.InputStream;

public interface IngestFileUseCase {
    void ingest(String filename, InputStream inputStream);
}
