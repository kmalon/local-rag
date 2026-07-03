package pl.km.adapter.in.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pl.km.application.port.in.IngestDocumentUseCase;
import pl.km.application.port.in.IngestFileUseCase;
import pl.km.application.port.in.QueryDocumentUseCase;

import java.io.IOException;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final IngestDocumentUseCase ingestDocumentUseCase;
    private final IngestFileUseCase ingestFileUseCase;
    private final QueryDocumentUseCase queryDocumentUseCase;

    public DocumentController(IngestDocumentUseCase ingestDocumentUseCase,
                               IngestFileUseCase ingestFileUseCase,
                               QueryDocumentUseCase queryDocumentUseCase) {
        this.ingestDocumentUseCase = ingestDocumentUseCase;
        this.ingestFileUseCase = ingestFileUseCase;
        this.queryDocumentUseCase = queryDocumentUseCase;
    }

    @PostMapping("/ingest")
    public ResponseEntity<Void> ingest(@RequestBody IngestRequest request) {
        ingestDocumentUseCase.ingest(request.name(), request.content());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/ingest/file")
    public ResponseEntity<Void> ingestFile(@RequestParam("file") MultipartFile file) throws IOException {
        ingestFileUseCase.ingest(file.getOriginalFilename(), file.getInputStream());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/query")
    public ResponseEntity<QueryResponse> query(@RequestBody QueryRequest request) {
        return ResponseEntity.ok(new QueryResponse(
                queryDocumentUseCase.query(request.question(), request.topK()).stream()
                        .map(r -> new QueryResultDto(r.name(), r.content(), r.score()))
                        .toList()
        ));
    }
}
