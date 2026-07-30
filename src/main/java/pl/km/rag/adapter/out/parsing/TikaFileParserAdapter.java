package pl.km.rag.adapter.out.parsing;

import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Component;
import pl.km.rag.application.exception.UnsupportedFileTypeException;
import pl.km.rag.application.port.out.FileParserPort;

import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class TikaFileParserAdapter implements FileParserPort {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("md", "markdown", "txt", "pdf", "doc", "docx");

    @Override
    public String parse(String filename, InputStream inputStream) {
        validateFileType(filename);
        TikaDocumentReader reader = new TikaDocumentReader(new InputStreamResource(inputStream));
        return reader.get().stream()
                .map(doc -> doc.getText())
                .collect(Collectors.joining("\n"));
    }

    private void validateFileType(String filename) {
        String extension = extensionOf(filename);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new UnsupportedFileTypeException(
                    "Unsupported file type: ." + extension + ". Supported types: " + SUPPORTED_EXTENSIONS);
        }
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase();
    }
}
