package com.swingcraft4j.code.demo;

import com.formdev.flatlaf.fonts.inter.FlatInterFont;
import com.formdev.flatlaf.fonts.jetbrains_mono.FlatJetBrainsMonoFont;
import com.formdev.flatlaf.fonts.roboto.FlatRobotoFont;
import com.formdev.flatlaf.fonts.roboto_mono.FlatRobotoMonoFont;
import com.formdev.flatlaf.util.FontUtils;
import com.swingcraft4j.code.viewer.JCodeViewer;

import javax.swing.Box;
import javax.swing.JComboBox;
import javax.swing.JToolBar;
import java.awt.Font;

/** The font pickers of the demos: the family and the size of the font of a viewer or an editor. */
final class DemoFonts {

    /** The fonts that come with FlatLaf; the first is the one of the Look and Feel of the demos. */
    private static final String[] FAMILIES = {
            FlatRobotoFont.FAMILY, FlatRobotoMonoFont.FAMILY, FlatInterFont.FAMILY, FlatJetBrainsMonoFont.FAMILY};
    private static final Integer[] SIZES = {10, 11, 12, 13, 14, 15, 16, 18, 20, 24};
    /** The space around the tool bar of a demo and between the pickers in it. */
    static final int GAP = 6;

    private DemoFonts() {
    }

    /** Adds a picker for the family and one for the size to the tool bar; they set the font of the viewer. */
    static void addTo(JToolBar toolBar, JCodeViewer viewer) {
        JComboBox<String> families = new JComboBox<>(FAMILIES);
        families.setToolTipText("Font");
        JComboBox<Integer> sizes = new JComboBox<>(SIZES);
        sizes.setToolTipText("Font size");
        sizes.setSelectedItem(viewer.getFont().getSize());

        Runnable apply = () -> viewer.setFont(FontUtils.getCompositeFont(
                (String) families.getSelectedItem(), Font.PLAIN, (Integer) sizes.getSelectedItem()));
        families.addActionListener(e -> apply.run());
        sizes.addActionListener(e -> apply.run());
        for (JComboBox<?> picker : new JComboBox<?>[]{families, sizes}) {
            picker.setFocusable(false); // keep the caret in the editor
            picker.setMaximumSize(picker.getPreferredSize());
            toolBar.add(Box.createHorizontalStrut(GAP));
            toolBar.add(picker);
        }
    }
}
