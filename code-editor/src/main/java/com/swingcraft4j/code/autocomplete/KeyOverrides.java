package com.swingcraft4j.code.autocomplete;

import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Keys of a component taken over for a while, as when a popup is open, and given back later.
 * Overrides nest: give them back in the reverse order they were taken.
 */
final class KeyOverrides {

    private final JComponent component;
    private final Map<KeyStroke, Object> replaced = new LinkedHashMap<>();

    KeyOverrides(JComponent component) {
        this.component = component;
    }

    void replace(KeyStroke key, String action) {
        InputMap keys = component.getInputMap(JComponent.WHEN_FOCUSED);
        replaced.putIfAbsent(key, keys.get(key));
        keys.put(key, action);
    }

    void restore() {
        InputMap keys = component.getInputMap(JComponent.WHEN_FOCUSED);
        replaced.forEach((key, binding) -> {
            if (binding != null) {
                keys.put(key, binding);
            } else {
                keys.remove(key);
            }
        });
        replaced.clear();
    }
}
