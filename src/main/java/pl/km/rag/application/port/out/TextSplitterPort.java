package pl.km.rag.application.port.out;

import java.util.List;

public interface TextSplitterPort {
    List<String> split(String text);
}
