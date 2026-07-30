package pl.km.rag.adapter.out.chunking;

import org.springframework.stereotype.Component;
import pl.km.rag.application.port.out.TextSplitterPort;
import pl.km.rag.config.ChunkingProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StructuralTextSplitterAdapter implements TextSplitterPort {

    private static final Pattern HEADING = Pattern.compile("(?m)^(#{1,6}\\s.*)$");
    private static final Pattern BLANK_LINE = Pattern.compile("\\n\\s*\\n+");

    private final ChunkingProperties properties;

    public StructuralTextSplitterAdapter(ChunkingProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<String> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        int chunkSize = properties.chunkSize();
        int overlapChars = (int) Math.round(chunkSize * properties.overlapRatio());

        List<String> blocks = splitIntoBlocks(text);
        if (blocks.size() <= 1) {
            return fixedWindowSplit(text.strip(), chunkSize, overlapChars);
        }
        return mergeBlocksIntoChunks(blocks, chunkSize, overlapChars);
    }

    private List<String> splitIntoBlocks(String text) {
        List<String> blocks = new ArrayList<>();
        for (String paragraph : BLANK_LINE.split(text.strip())) {
            blocks.addAll(splitOnHeadings(paragraph));
        }
        return blocks.stream().map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private List<String> splitOnHeadings(String paragraph) {
        Matcher matcher = HEADING.matcher(paragraph);
        List<Integer> boundaries = new ArrayList<>();
        while (matcher.find()) {
            boundaries.add(matcher.start());
        }
        if (boundaries.isEmpty() || (boundaries.size() == 1 && boundaries.get(0) == 0)) {
            return List.of(paragraph);
        }
        List<String> parts = new ArrayList<>();
        int start = 0;
        for (int boundary : boundaries) {
            if (boundary > start) {
                parts.add(paragraph.substring(start, boundary));
            }
            start = boundary;
        }
        parts.add(paragraph.substring(start));
        return parts;
    }

    private List<String> mergeBlocksIntoChunks(List<String> blocks, int chunkSize, int overlapChars) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String block : blocks) {
            if (!current.isEmpty() && current.length() + 2 + block.length() > chunkSize) {
                chunks.add(current.toString().strip());
                String tail = tailChars(current.toString(), overlapChars);
                current = new StringBuilder(tail);
                if (!current.isEmpty()) {
                    current.append("\n\n");
                }
            }
            current.append(block).append("\n\n");
        }
        if (!current.toString().isBlank()) {
            chunks.add(current.toString().strip());
        }
        return chunks;
    }

    private List<String> fixedWindowSplit(String text, int windowSize, int overlapChars) {
        if (text.length() <= windowSize) {
            return List.of(text);
        }
        List<String> chunks = new ArrayList<>();
        int step = Math.max(1, windowSize - overlapChars);
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + windowSize, text.length());
            chunks.add(text.substring(start, end));
            if (end == text.length()) {
                break;
            }
            start += step;
        }
        return chunks;
    }

    private String tailChars(String s, int n) {
        if (n <= 0 || s.length() <= n) {
            return s.strip();
        }
        return s.substring(s.length() - n).strip();
    }
}
