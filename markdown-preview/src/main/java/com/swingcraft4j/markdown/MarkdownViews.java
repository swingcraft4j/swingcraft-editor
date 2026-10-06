package com.swingcraft4j.markdown;

import javax.swing.text.AttributeSet;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
import javax.swing.text.TabExpander;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.BlockView;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.InlineView;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * The parts of the page that the preview paints itself, since the HTML of Swing has no round
 * corner and no check box that is not a real component: the background of code, in a line and
 * in a block, and the box of a task.
 */
final class MarkdownViews {

    /** The classes of the span around the char of a task box, by which its view is found. */
    static final String TASK_DONE = "task-done";
    static final String TASK_OPEN = "task-open";

    /** The space under a code block, which its background does not fill; the margin of {@code pre}. */
    static final int BLOCK_MARGIN_BOTTOM = 10;

    private static final int BLOCK_ARC = 12;
    private static final int LINE_ARC = 7;

    private MarkdownViews() {
    }

    /** The HTML of Swing, with the views of this class for code and for task boxes. */
    static final class Kit extends HTMLEditorKit {

        private static final ViewFactory FACTORY = new Factory();

        @Override
        public ViewFactory getViewFactory() {
            return FACTORY;
        }
    }

    private static final class Factory extends HTMLEditorKit.HTMLFactory {

        @Override
        public View create(Element element) {
            AttributeSet attributes = element.getAttributes();
            Object name = attributes.getAttribute(StyleConstants.NameAttribute);
            if (name == HTML.Tag.PRE) {
                return new CodeBlockView(element);
            }
            if (name == HTML.Tag.CONTENT) {
                Object type = attributes.getAttribute(HTML.Tag.SPAN) instanceof AttributeSet span
                        ? span.getAttribute(HTML.Attribute.CLASS) : null;
                if (TASK_DONE.equals(type) || TASK_OPEN.equals(type)) {
                    return new TaskBoxView(element, TASK_DONE.equals(type));
                }
                // code in a block has the background of its block
                if (attributes.isDefined(HTML.Tag.CODE) && !isIn(element, HTML.Tag.PRE)) {
                    return new CodeView(element);
                }
            }
            return super.create(element);
        }

        private static boolean isIn(Element element, HTML.Tag tag) {
            for (Element parent = element.getParentElement(); parent != null; parent = parent.getParentElement()) {
                if (parent.getAttributes().getAttribute(StyleConstants.NameAttribute) == tag) {
                    return true;
                }
            }
            return false;
        }
    }

    /** A code block on a background with round corners. */
    private static final class CodeBlockView extends BlockView {

        CodeBlockView(Element element) {
            super(element, View.Y_AXIS);
        }

        @Override
        public void paint(Graphics g, Shape allocation) {
            if (getContainer() instanceof JMarkdownPreview preview) {
                Rectangle bounds = allocation.getBounds();
                bounds.height -= BLOCK_MARGIN_BOTTOM;
                fill(g, bounds, preview.blockBackground, BLOCK_ARC);
            }
            super.paint(g, allocation);
        }
    }

    /** Code in a line on a background with round corners; where the line breaks in it, each part has its own. */
    private static final class CodeView extends InlineView {

        CodeView(Element element) {
            super(element);
        }

        @Override
        public void paint(Graphics g, Shape allocation) {
            if (getContainer() instanceof JMarkdownPreview preview) {
                fill(g, allocation.getBounds(), preview.codeBackground, LINE_ARC);
            }
            super.paint(g, allocation);
        }
    }

    /**
     * The box of a task, drawn in the place of the char that stands for it in the text: the char
     * is what is copied, and what a text component without these views shows.
     */
    private static final class TaskBoxView extends InlineView {

        private final boolean done;

        TaskBoxView(Element element, boolean done) {
            super(element);
            this.done = done;
        }

        private float side() {
            return Math.round(getFont().getSize2D() * 1.1f);
        }

        @Override
        public float getPreferredSpan(int axis) {
            return axis == View.X_AXIS ? side() : super.getPreferredSpan(axis);
        }

        @Override
        public float getTabbedSpan(float x, TabExpander expander) {
            return side();
        }

        @Override
        public float getPartialSpan(int from, int to) {
            return side();
        }

        @Override
        public void paint(Graphics g, Shape allocation) {
            if (!(getContainer() instanceof JMarkdownPreview preview) || preview.boxBorder == null) {
                super.paint(g, allocation);
                return;
            }
            Rectangle bounds = allocation.getBounds();
            // a pixel of air around the box
            float side = Math.min(side(), bounds.height) - 2;
            float x = bounds.x + 1;
            float y = bounds.y + (bounds.height - side) / 2f;
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                RoundRectangle2D box = new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, side - 1, side - 1, 4, 4);
                if (done) {
                    g2.setColor(preview.boxFill);
                    g2.fill(box);
                    g2.draw(box);
                    Path2D tick = new Path2D.Float();
                    tick.moveTo(x + side * 0.24f, y + side * 0.52f);
                    tick.lineTo(x + side * 0.43f, y + side * 0.71f);
                    tick.lineTo(x + side * 0.77f, y + side * 0.31f);
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(Math.max(1.4f, side / 7f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.draw(tick);
                } else {
                    g2.setColor(preview.boxBorder);
                    g2.draw(box);
                }
            } finally {
                g2.dispose();
            }
        }
    }

    private static void fill(Graphics g, Rectangle bounds, Color color, int arc) {
        if (color == null) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, arc, arc);
        } finally {
            g2.dispose();
        }
    }
}
