package com.navgrow.util;
import org.springframework.stereotype.Component;
import java.text.Normalizer;
import java.util.UUID;

@Component
public class SlugUtil {
    public String toSlug(String input) {
        if (input == null) return UUID.randomUUID().toString();
        return Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("[\\s-]+", "-")
                .replaceAll("^-|-$", "");
    }

    public String uniqueSlug(String base, java.util.function.Predicate<String> exists) {
        String slug = toSlug(base);
        String candidate = slug;
        int i = 1;
        while (exists.test(candidate)) { candidate = slug + "-" + i++; }
        return candidate;
    }
}