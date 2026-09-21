package com.pdf2tz.backend.application.ports;

import java.util.List;

/**
 * Порт поиска естественных границ текста.
 *
 * <p>Границы представлены offsets в исходной строке. Это позволяет
 * application-алгоритмам самостоятельно выбирать размер фрагмента и не
 * терять символы при повторной сборке.</p>
 */
public interface TextBoundaryPort {

    /**
     * Возвращает конечные offsets предложений в исходном тексте.
     *
     * @param text исходный текст
     * @return возрастающие offsets, включая конец текста, если он непустой
     */
    List<Integer> sentenceEndOffsets(String text);

    /**
     * Возвращает конечные offsets словесных фрагментов в исходном тексте.
     *
     * @param text исходный текст
     * @return возрастающие offsets, включая конец текста, если он непустой
     */
    List<Integer> wordEndOffsets(String text);
}
