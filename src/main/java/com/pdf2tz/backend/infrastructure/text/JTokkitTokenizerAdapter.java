package com.pdf2tz.backend.infrastructure.text;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import com.pdf2tz.backend.application.ports.LlmTokenizerPort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * JTokkit-адаптер на базе encoding {@code O200K_BASE}.
 *
 * <p>Registry и encoding неизменяемы после создания и безопасно переиспользуются
 * между вызовами. Разбиение выполняется по исходным токенам, поэтому части
 * сохраняют порядок и не пересекаются.</p>
 */
@Component
public class JTokkitTokenizerAdapter implements LlmTokenizerPort {

    private final Encoding encoding;

    public JTokkitTokenizerAdapter() {
        EncodingRegistry registry = Encodings.newLazyEncodingRegistry();
        this.encoding = registry.getEncoding(EncodingType.O200K_BASE);
    }

    @Override
    public int countTokens(String text) {
        return encoding.countTokens(Objects.requireNonNull(text, "Text must not be null"));
    }

    @Override
    public List<String> splitByTokenLimit(String text, int maxTokens) {
        Objects.requireNonNull(text, "Text must not be null");
        if (maxTokens < 1) {
            throw new IllegalArgumentException("Maximum token count must be positive");
        }
        if (text.isEmpty()) {
            return List.of();
        }

        IntArrayList tokens = encoding.encode(text);
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < tokens.size(); start += maxTokens) {
            int end = Math.min(start + maxTokens, tokens.size());
            IntArrayList part = new IntArrayList(end - start);
            for (int index = start; index < end; index++) {
                part.add(tokens.get(index));
            }
            chunks.add(encoding.decode(part));
        }
        return List.copyOf(chunks);
    }
}
