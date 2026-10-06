package com.swingcraft4j.code.lexer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ServiceLoader;

/** Looks up the {@link Language} implementations registered through {@link ServiceLoader}. */
public final class Languages {

    private static volatile List<Language> installed;

    private Languages() {
    }

    public static List<Language> installed() {
        List<Language> languages = installed;
        if (languages == null) {
            List<Language> found = new ArrayList<>();
            ServiceLoader.load(Language.class).forEach(found::add);
            installed = languages = List.copyOf(found);
        }
        return languages;
    }

    public static Optional<Language> byId(String id) {
        return installed().stream().filter(language -> language.id().equals(id)).findFirst();
    }

    /**
     * The language of a file by its extension. A name without a dot is taken as the extension
     * itself, which finds the language of a file such as {@code Dockerfile}.
     */
    public static Optional<Language> forFileName(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return installed().stream().filter(language -> language.fileExtensions().contains(extension)).findFirst();
    }
}
