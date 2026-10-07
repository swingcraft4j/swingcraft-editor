package com.swingcraft4j.markdown;

import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.Position;
import javax.swing.text.StyleConstants;
import javax.swing.text.TabExpander;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.BlockView;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.InlineView;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Container;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

/**
 * The parts of the page that the preview paints itself, since the HTML of Swing has no round
 * corner, no check box that is not a real component and no image of SVG: the background of
 * code, in a line and in a block, the box of a task, and the images.
 */
final class MarkdownViews {

    /** The classes of the span around the char of a task box, by which its view is found. */
    static final String TASK_DONE = "task-done";
    static final String TASK_OPEN = "task-open";

    /** The space under a code block, which its background does not fill; the margin of {@code pre}. */
    static final int BLOCK_MARGIN_BOTTOM = 10;

    private static final int BLOCK_ARC = 12;
    private static final int LINE_ARC = 7;
    /** The space at each end of code in a line, as a part of the size of its font. */
    private static final float CODE_PADDING = 0.3f;

    private MarkdownViews() {
    }

    /** The HTML of Swing, with the views of this class for code, for task boxes and for images. */
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
            if (name == HTML.Tag.IMG) {
                return new ImageView(element);
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

        /** The space between the code and each end of its background, which grows with the font. */
        private int padding() {
            return Math.round(getFont().getSize2D() * CODE_PADDING);
        }

        /** Where the code is, in the place of the view. */
        private Rectangle inner(Shape allocation) {
            Rectangle bounds = allocation.getBounds();
            int padding = Math.min(padding(), bounds.width / 2);
            bounds.x += padding;
            bounds.width -= 2 * padding;
            return bounds;
        }

        @Override
        public float getPreferredSpan(int axis) {
            return axis == View.X_AXIS ? super.getPreferredSpan(axis) + 2 * padding() : super.getPreferredSpan(axis);
        }

        @Override
        public float getTabbedSpan(float x, TabExpander expander) {
            return super.getTabbedSpan(x, expander) + 2 * padding();
        }

        // where the line breaks in the code, each part has the padding, so less of the code fits

        @Override
        public int getBreakWeight(int axis, float position, float length) {
            return super.getBreakWeight(axis, position, axis == View.X_AXIS ? Math.max(0, length - 2 * padding()) : length);
        }

        @Override
        public View breakView(int axis, int offset, float position, float length) {
            return super.breakView(axis, offset, position, axis == View.X_AXIS ? Math.max(0, length - 2 * padding()) : length);
        }

        @Override
        public Shape modelToView(int position, Shape allocation, Position.Bias bias) throws BadLocationException {
            return super.modelToView(position, inner(allocation), bias);
        }

        @Override
        public int viewToModel(float x, float y, Shape allocation, Position.Bias[] bias) {
            return super.viewToModel(x, y, inner(allocation), bias);
        }

        @Override
        public void paint(Graphics g, Shape allocation) {
            if (getContainer() instanceof JMarkdownPreview preview) {
                fill(g, allocation.getBounds(), preview.codeBackground, LINE_ARC);
            }
            super.paint(g, inner(allocation));
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

    /**
     * An image, loaded and drawn by the preview: Swing reads no SVG. While it is not there, and
     * when it cannot be loaded, the text that stands for it is shown.
     */
    private static final class ImageView extends View {

        private final String alt;
        /** The size the tag gives the image, or 0 where it gives none. */
        private final int width;
        private final int height;
        private MarkdownImages.Picture picture;
        private boolean asked;

        ImageView(Element element) {
            super(element);
            AttributeSet attributes = element.getAttributes();
            alt = attributes.getAttribute(HTML.Attribute.ALT) instanceof String text ? text : "";
            width = pixels(attributes.getAttribute(HTML.Attribute.WIDTH));
            height = pixels(attributes.getAttribute(HTML.Attribute.HEIGHT));
        }

        private static int pixels(Object value) {
            try {
                return value instanceof String text ? Math.max(0, Integer.parseInt(text.trim())) : 0;
            } catch (NumberFormatException e) {
                // a part of the width of the page, which is not known here: the image has its own size
                return 0;
            }
        }

        @Override
        public void setParent(View parent) {
            super.setParent(parent);
            if (parent != null) {
                ask();
            }
        }

        /** Asks the preview for the image, once the view is in one. */
        private void ask() {
            if (asked || !(getContainer() instanceof JMarkdownPreview preview)) {
                return;
            }
            asked = true;
            try {
                Object source = getElement().getAttributes().getAttribute(HTML.Attribute.SRC);
                URL base = ((HTMLDocument) getDocument()).getBase();
                picture = preview.images.get(new URL(base, (String) source), this::show);
            } catch (MalformedURLException | RuntimeException e) {
                // no address, or one relative to a base that is not set: the text is shown
            }
        }

        private void show(MarkdownImages.Picture loaded) {
            picture = loaded;
            // the page is laid out again around the size the image has
            if (getParent() != null && getDocument() instanceof AbstractDocument document) {
                document.readLock();
                try {
                    preferenceChanged(null, true, true);
                } finally {
                    document.readUnlock();
                }
                if (getContainer() != null) {
                    getContainer().repaint();
                }
            }
        }

        @Override
        public float getPreferredSpan(int axis) {
            ask();
            if (picture == null) {
                Container container = getContainer();
                if (container == null || alt.isEmpty()) {
                    return 0;
                }
                FontMetrics metrics = container.getFontMetrics(container.getFont());
                return axis == View.X_AXIS ? metrics.stringWidth(alt) : metrics.getHeight();
            }
            float w = picture.width;
            float h = picture.height;
            if (width > 0 && height > 0) {
                w = width;
                h = height;
            } else if (width > 0) {
                h = h * width / w;
                w = width;
            } else if (height > 0) {
                w = w * height / h;
                h = height;
            }
            return axis == View.X_AXIS ? w : h;
        }

        /** An image stands on the line of the text, and the text in its place where that text does. */
        @Override
        public float getAlignment(int axis) {
            if (axis != View.Y_AXIS) {
                return super.getAlignment(axis);
            }
            Container container = getContainer();
            if (picture != null || container == null) {
                return 1;
            }
            FontMetrics metrics = container.getFontMetrics(container.getFont());
            return (float) (metrics.getHeight() - metrics.getDescent()) / metrics.getHeight();
        }

        @Override
        public void paint(Graphics g, Shape allocation) {
            Rectangle bounds = allocation.getBounds();
            if (picture != null) {
                picture.paint(g, getContainer(), bounds);
            } else if (!alt.isEmpty() && getContainer() instanceof JMarkdownPreview preview) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    // drawn as the text of the desktop is
                    if (Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints") instanceof Map<?, ?> hints) {
                        g2.addRenderingHints(hints);
                    }
                    g2.setFont(preview.getFont());
                    g2.setColor(preview.altText != null ? preview.altText : preview.getForeground());
                    FontMetrics metrics = g2.getFontMetrics();
                    g2.drawString(alt, bounds.x, bounds.y + metrics.getLeading() + metrics.getAscent());
                } finally {
                    g2.dispose();
                }
            }
        }

        @Override
        public Shape modelToView(int position, Shape allocation, Position.Bias bias) throws BadLocationException {
            if (position < getStartOffset() || position > getEndOffset()) {
                throw new BadLocationException("not in the image", position);
            }
            Rectangle bounds = allocation.getBounds();
            if (position == getEndOffset()) {
                bounds.x += bounds.width;
            }
            bounds.width = 0;
            return bounds;
        }

        /** All of the image is the place before it, so that a click anywhere on it is one on its link. */
        @Override
        public int viewToModel(float x, float y, Shape allocation, Position.Bias[] bias) {
            Rectangle bounds = allocation.getBounds();
            if (x < bounds.x + bounds.width) {
                bias[0] = Position.Bias.Forward;
                return getStartOffset();
            }
            bias[0] = Position.Bias.Backward;
            return getEndOffset();
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
