/* LanguageTool, a natural language style checker
 * Copyright (C) 2014 Daniel Naber (http://www.danielnaber.de)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package org.languagetool.dev.eval;

import org.apache.commons.io.IOUtils;
import org.apache.tika.language.LanguageIdentifier;
import org.languagetool.Language;
import org.languagetool.tools.StringTools;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Evaluate the quality of the language detection.
 * @since 2.9
 */
class LanguageDetectionEval {

    private int totalFailures = 0;

    private void evaluate(Language language) throws IOException {
        if (language.isVariant()) {
            return;
        }
        String evalTextFile = "/org/languagetool/dev/eval/lang/" + language.getShortName() + ".txt";
        InputStream stream = LanguageDetectionEval.class.getResourceAsStream(evalTextFile);
        System.out.println("=== " + language + " ===");
        if (stream == null) {
            System.out.println("No eval data for " + language);
        } else {
            int minChars = 0;
            int failures = 0;
            List<String> list = getLines(stream);
            for (String line : list) {
                try {
                    int minChar = getShortestCorrectDetection(line, language);
                    minChars += minChar;
                } catch (DetectionException e) {
                    //System.out.println("FAIL: " + e.getMessage());
                    failures++;
                }
            }
            int avgMinChars = minChars / list.size();
            System.out.println("Average minimum size still correctly detected: " + avgMinChars);
            System.out.println("Detection failures: " + failures + " of " + list.size());
            totalFailures += failures;
        }
    }

    private static int getShortestCorrectDetection(String line, Language expectedLanguage) {
        String[] tokens = line.split("\\s+");
        for (int i = tokens.length; i > 0; i--) {
            String text = StringTools.listToString(Arrays.asList(tokens).subList(0, i), " ");
            LanguageIdentifier identifier = new LanguageIdentifier(text);
            String detectedLang = identifier.getLanguage();
            if (!detectedLang.equals(expectedLanguage.getShortName())) {
                if (i == tokens.length) {
                    throw new DetectionException("Detection failed for '" + line + "', detected " + detectedLang);
                } else {
                    int textLength = getTextLength(tokens, i + 1);
                    //System.out.println("TEXT     : " + line);
                    //System.out.println("TOO SHORT: " + text + " => " + detectedLang + " (" + textLength + ")");
                    return textLength;
                }
            }
        }
        return tokens[0].length();
    }

    private static int getTextLength(String[] tokens, int tokenPos) {
        int i = 0;
        int charCount = 0;
        for (String token : tokens) {
            if (i++ > tokenPos) {
                return charCount;
            }
            charCount += token.length();
        }
        return charCount;
    }

    private static List<String> getLines(InputStream stream) throws IOException {
        List<String> lines = IOUtils.readLines(stream, "utf-8");
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            if (!line.startsWith("#")) {
                result.add(line);
            }
        }
        return result;
    }

    public static void main(String[] args) throws IOException {
        LanguageDetectionEval eval = new LanguageDetectionEval();
        long startTime = System.currentTimeMillis();
        for (Language language : Language.REAL_LANGUAGES) {
            eval.evaluate(language);
        }
        long endTime = System.currentTimeMillis();
        System.out.println("Time: " + (endTime-startTime)+ "ms");
        System.out.println("Total detection failures: " + eval.totalFailures);
    }

    static class DetectionException extends RuntimeException {
        public DetectionException(String s) {
            super(s);
        }
    }
}