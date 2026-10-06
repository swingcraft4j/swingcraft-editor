package com.swingcraft4j.code.demo;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.fonts.inter.FlatInterFont;
import com.formdev.flatlaf.fonts.jetbrains_mono.FlatJetBrainsMonoFont;
import com.formdev.flatlaf.fonts.roboto.FlatRobotoFont;
import com.formdev.flatlaf.fonts.roboto_mono.FlatRobotoMonoFont;
import com.formdev.flatlaf.util.FontUtils;
import com.swingcraft4j.code.theme.CodeTheme;

import javax.swing.UIManager;
import java.awt.EventQueue;
import java.awt.Font;

/** The demos use FlatLaf, light or dark to go with the code theme on show. */
final class DemoLookAndFeel {

    private DemoLookAndFeel() {
    }

    /** Installs the light Look and Feel with the Roboto font; call before creating any component. */
    static void install() {
        // the default font of the Look and Feel, which the code viewer and editor take too
        FlatRobotoFont.install();
        // the other fonts of FlatLaf, for the font picker; each is loaded when it is first used
        FlatRobotoMonoFont.installLazy();
        FlatInterFont.installLazy();
        FlatJetBrainsMonoFont.installLazy();
        UIManager.put("defaultFont", FontUtils.getCompositeFont(FlatRobotoFont.FAMILY, Font.PLAIN, 13));
        FlatLightLaf.setup();
    }

    /** Switches every window between light and dark when the code theme calls for the other one. */
    static void follow(CodeTheme theme) {
        // Not at once: the theme is picked from a combo box, and a new Look and Feel takes the
        // combo box away from under the key or the click that is still being handled.
        EventQueue.invokeLater(() -> {
            if (theme.isDark() != FlatLaf.isLafDark()) {
                if (theme.isDark()) {
                    FlatDarkLaf.setup();
                } else {
                    FlatLightLaf.setup();
                }
                FlatLaf.updateUI();
            }
        });
    }
}
