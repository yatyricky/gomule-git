package gomule.translations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class CompositeTranslations implements Translations {

    private final Translations[] translations;

    public CompositeTranslations(Translations... translations) {
        this.translations = translations;
    }

    @Override
    public String getTranslationOrNull(String key) {
        return Arrays.stream(translations)
                .map(it -> it.getTranslationOrNull(key))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<String> getKeysForEnUS(String enUS) {
        List<String> result = new ArrayList<>();
        for (Translations t : translations) {
            result.addAll(t.getKeysForEnUS(enUS));
        }
        return result;
    }
}
