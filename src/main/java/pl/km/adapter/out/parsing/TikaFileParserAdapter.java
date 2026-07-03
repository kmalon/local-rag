package pl.km.adapter.out.parsing;

import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Component;
import pl.km.application.port.out.FileParserPort;

import java.io.InputStream;
import java.util.stream.Collectors;

@Component
public class TikaFileParserAdapter implements FileParserPort {

    @Override
    public String parse(String filename, InputStream inputStream) {
        TikaDocumentReader reader = new TikaDocumentReader(new InputStreamResource(inputStream));
        return reader.get().stream()
                .map(doc -> doc.getText())
                .collect(Collectors.joining("\n"));
    }
}
