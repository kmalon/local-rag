package pl.km.adapter.in.rest;

public record QueryRequest(String question, int topK, Double score) {
    public QueryRequest {
        if (topK <= 0) topK = 5;
    }
}
