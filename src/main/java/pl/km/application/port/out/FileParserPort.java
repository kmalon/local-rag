package pl.km.application.port.out;

import java.io.InputStream;

public interface FileParserPort {
    String parse(String filename, InputStream inputStream);
}
