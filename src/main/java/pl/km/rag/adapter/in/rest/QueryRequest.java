package pl.km.rag.adapter.in.rest;

public record QueryRequest(String question, Integer topK, Double score) {
}
