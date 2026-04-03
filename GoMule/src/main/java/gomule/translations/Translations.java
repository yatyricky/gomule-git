package gomule.translations;

import javax.annotation.Nullable;
import java.util.List;

public interface Translations {
    @Nullable
    String getTranslationOrNull(String key);

    default String getTranslation(String key) {
        String translationOrNull = getTranslationOrNull(key);
        if (translationOrNull == null) throw new IllegalArgumentException("No translation for " + key);
        return translationOrNull;
    }

    /**
     * Returns all Keys whose enUS value equals the given string.
     */
    List<String> getKeysForEnUS(String enUS);
}
