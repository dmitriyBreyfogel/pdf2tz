package com.pdf2tz.backend.infrastructure.text;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.pdf2tz.backend.application.ports.LlmTokenizerPort;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * JTokkit-адаптер на базе encoding {@code O200K_BASE}.
 *
 * <p>Registry и encoding неизменяемы после создания и безопасно переиспользуются
 * между вызовами.</p>
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
}
