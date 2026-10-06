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

    public static Optional<Language> forFileName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return Optional.empty();
        }
        String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return installed().stream().filter(language -> language.fileExtensions().contains(extension)).findFirst();
    }
}
