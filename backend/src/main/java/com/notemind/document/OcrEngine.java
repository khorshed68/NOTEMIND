package com.notemind.document;

import java.awt.image.BufferedImage;

public interface OcrEngine {

    boolean isAvailable();

    /** @return recognised text of a single rendered page */
    String recognize(BufferedImage pageImage);
}
