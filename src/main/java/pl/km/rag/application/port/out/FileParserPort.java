package pl.km.rag.application.port.out;

import java.io.InputStream;

public interface FileParserPort {
    String parse(String filename, InputStream inputStream);
}
