package pl.km.adapter.in.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.km.application.port.in.IngestDocumentUseCase;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final IngestDocumentUseCase ingestDocumentUseCase;

    public DocumentController(IngestDocumentUseCase ingestDocumentUseCase) {
        this.ingestDocumentUseCase = ingestDocumentUseCase;
    }

    @PostMapping("/ingest")
    public ResponseEntity<Void> ingest(@RequestBody IngestRequest request) {
        ingestDocumentUseCase.ingest(request.name(), request.content());
        return ResponseEntity.ok().build();
    }
}
