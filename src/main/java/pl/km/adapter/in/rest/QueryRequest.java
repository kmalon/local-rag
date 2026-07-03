package pl.km.adapter.in.rest;

public record QueryRequest(String question, int topK) {
    public QueryRequest {
        if (topK <= 0) topK = 5;
    }
}
