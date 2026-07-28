package pl.km.rag.application.port.out;

public interface EmbeddingPort {
    float[] embed(String text);
}
