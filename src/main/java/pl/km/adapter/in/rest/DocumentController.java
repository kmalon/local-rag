package pl.km.adapter.in.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pl.km.application.port.in.IngestDocumentPort;
import pl.km.application.port.in.IngestFilePort;
import pl.km.application.port.in.QueryDocumentPort;

import java.io.IOException;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final IngestDocumentPort ingestDocumentPort;
    private final IngestFilePort ingestFilePort;
    private final QueryDocumentPort queryDocumentPort;

    public DocumentController(IngestDocumentPort ingestDocumentPort,
                               IngestFilePort ingestFilePort,
                               QueryDocumentPort queryDocumentPort) {
        this.ingestDocumentPort = ingestDocumentPort;
        this.ingestFilePort = ingestFilePort;
        this.queryDocumentPort = queryDocumentPort;
    }

    @PostMapping("/ingest")
    public ResponseEntity<Void> ingest(@RequestBody IngestRequest request) {
        ingestDocumentPort.ingest(request.name(), request.content());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/ingest/file")
    public ResponseEntity<Void> ingestFile(@RequestParam("file") MultipartFile file) throws IOException {
        ingestFilePort.ingest(file.getOriginalFilename(), file.getInputStream());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/query")
    public ResponseEntity<QueryResponse> query(@RequestBody QueryRequest request) {
        return ResponseEntity.ok(new QueryResponse(
                queryDocumentPort.query(request.question(), request.topK(), request.score()).stream()
                        .map(r -> new QueryResultDto(r.name(), r.content(), r.score()))
                        .toList()
        ));
    }
}
