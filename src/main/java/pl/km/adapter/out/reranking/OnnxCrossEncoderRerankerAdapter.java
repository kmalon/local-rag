package pl.km.adapter.out.reranking;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import pl.km.application.exception.RerankerException;
import pl.km.application.port.out.RerankerPort;
import pl.km.config.RerankerProperties;
import pl.km.domain.model.QueryResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Local cross-encoder reranker (e.g. ms-marco-MiniLM-L-6-v2) run via ONNX Runtime.
 * Re-scores each (query, chunk) pair with a single relevance logit, squashed to
 * [0,1] with a sigmoid so scores stay comparable to the configured threshold.
 */
@Component
public class OnnxCrossEncoderRerankerAdapter implements RerankerPort, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(OnnxCrossEncoderRerankerAdapter.class);
    private static final int MAX_LENGTH = 512;

    private final HuggingFaceTokenizer tokenizer;
    private final OrtEnvironment environment;
    private final OrtSession session;
    private final Set<String> inputNames;

    public OnnxCrossEncoderRerankerAdapter(RerankerProperties properties) {
        List<Path> tempFiles = new ArrayList<>();
        try {
            Path tokenizerPath = toLocalFile(properties.tokenizerUri(), "reranker-tokenizer", ".json", tempFiles);
            this.tokenizer = HuggingFaceTokenizer.builder()
                    .optTokenizerPath(tokenizerPath)
                    .optAddSpecialTokens(true)
                    .optTruncation(true)
                    .optMaxLength(MAX_LENGTH)
                    .build();

            Path modelPath = toLocalFile(properties.modelUri(), "reranker-model", ".onnx", tempFiles);
            this.environment = OrtEnvironment.getEnvironment();
            this.session = environment.createSession(modelPath.toString(), new OrtSession.SessionOptions());
            this.inputNames = session.getInputNames();
            log.info("Cross-encoder reranker loaded (model inputs: {})", inputNames);
        } catch (Exception e) {
            log.error("Failed to initialise cross-encoder reranker", e);
            throw new BeanInitializationException(
                    "Failed to initialise cross-encoder reranker; ensure the model and tokenizer are "
                            + "available (see resources/models/reranker/README.md)", e);
        } finally {
            // Model + tokenizer are fully loaded into memory now; the on-disk copies are no longer
            // needed. Deleting eagerly avoids temp-file accumulation across repeated context restarts.
            for (Path temp : tempFiles) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ex) {
                    log.warn("Could not delete reranker temp file {}", temp, ex);
                }
            }
        }
    }

    @Override
    public List<QueryResult> rerank(String query, List<QueryResult> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream()
                .map(c -> new QueryResult(c.name(), c.content(), score(query, c.content())))
                .sorted(Comparator.comparingDouble(QueryResult::score).reversed())
                .toList();
    }

    private double score(String query, String passage) {
        Encoding encoding = tokenizer.encode(query, passage);
        long[] ids = encoding.getIds();
        long[] attentionMask = encoding.getAttentionMask();
        long[] typeIds = encoding.getTypeIds();

        Map<String, OnnxTensor> inputs = new HashMap<>();
        try {
            putIfPresent(inputs, "input_ids", ids);
            putIfPresent(inputs, "attention_mask", attentionMask);
            putIfPresent(inputs, "token_type_ids", typeIds);

            try (OrtSession.Result result = session.run(inputs)) {
                float[][] logits = (float[][]) result.get(0).getValue();
                return sigmoid(logits[0][0]);
            }
        } catch (Exception e) {
            throw new RerankerException("Cross-encoder inference failed", e);
        } finally {
            inputs.values().forEach(OnnxTensor::close);
        }
    }

    private void putIfPresent(Map<String, OnnxTensor> inputs, String name, long[] values) throws Exception {
        if (inputNames.contains(name)) {
            inputs.put(name, OnnxTensor.createTensor(environment, new long[][]{values}));
        }
    }

    private static double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    private static Path toLocalFile(Resource resource, String prefix, String suffix, List<Path> tempFiles)
            throws IOException {
        if (resource.isFile()) {
            return resource.getFile().toPath();
        }
        Path temp = Files.createTempFile(prefix, suffix);
        tempFiles.add(temp);
        try (InputStream in = resource.getInputStream()) {
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
        }
        return temp;
    }

    @Override
    public void destroy() throws Exception {
        if (session != null) {
            session.close();
        }
        if (tokenizer != null) {
            tokenizer.close();
        }
    }
}
