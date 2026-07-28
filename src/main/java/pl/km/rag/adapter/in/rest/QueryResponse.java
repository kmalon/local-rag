package pl.km.rag.adapter.in.rest;

import java.util.List;

public record QueryResponse(List<QueryResultDto> results) {}
