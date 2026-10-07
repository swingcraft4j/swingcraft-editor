package com.swingcraft4j.markdown;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.parser.resources.ResourcePolicy;
import com.github.weisj.jsvg.view.ViewBox;

import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The images of a preview. They are loaded by the preview and not by Swing, which reads no SVG,
 * the format of the badges at the top of many a README. An image is loaded once and kept: the
 * page is rendered again after each pause in typing, and would fetch each of them again.
 */
final class MarkdownImages {

    /** How many images are kept; the one that was shown longest ago goes first. */
    private static final int MAX_IMAGES = 100;
    /** An image of more bytes than this is not shown. */
    private static final int MAX_BYTES = 16 * 1024 * 1024;
    /** How long a server is waited for, in milliseconds. */
    private static final int TIMEOUT = 15_000;
    /** How long after an image could not be loaded it is tried again, in nanoseconds. */
    private static final long RETRY_AFTER = TimeUnit.SECONDS.toNanos(30);

    /** Loads the images of all the previews, a few at a time, on threads that do not keep the application alive. */
    private static final ExecutorService LOADER = createLoader();

    /** The images by their address, the one that was asked for last at the end. Only used on the event dispatch thread. */
    private final Map<String, Slot> entries = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Slot> eldest) {
            return size() > MAX_IMAGES;
        }
    };

    /** An image that is loaded: one of SVG, which is drawn sharp at any size, or one of pixels. */
    static final class Picture {

        private final SVGDocument svg;
        private final Image image;
        final float width;
        final float height;

        private Picture(SVGDocument svg, Image image, float width, float height) {
            this.svg = svg;
            this.image = image;
            this.width = width;
            this.height = height;
        }

        /** Paints the image so that it fills the bounds. */
        void paint(Graphics g, Component component, Rectangle bounds) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                if (svg != null) {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                    svg.render(component, g2, new ViewBox(bounds.x, bounds.y, bounds.width, bounds.height));
                } else {
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    // the component is told of the next frame of an image that moves
                    g2.drawImage(image, bounds.x, bounds.y, bounds.width, bounds.height, component);
                }
            } finally {
                g2.dispose();
            }
        }
    }

    private static final class Slot {
        Picture picture;
        boolean failed;
        long failedAt;
        final List<Consumer<Picture>> waiting = new ArrayList<>();
    }

    /**
     * The image at the address, if it is loaded. If it is not, it is loaded now, and {@code whenLoaded}
     * is given it on the event dispatch thread once it is there; an image that cannot be loaded never is.
     */
    Picture get(URL url, Consumer<Picture> whenLoaded) {
        String key = key(url);
        Slot known = entries.get(key);
        if (known != null && known.failed && System.nanoTime() - known.failedAt > RETRY_AFTER) {
            // the network may be back, or the file there by now
            known = null;
        }
        if (known == null) {
            Slot entry = new Slot();
            entries.put(key, entry);
            LOADER.execute(() -> {
                Picture picture = load(url);
                SwingUtilities.invokeLater(() -> loaded(entry, picture));
            });
            known = entry;
        }
        if (known.picture == null && !known.failed) {
            known.waiting.add(whenLoaded);
        }
        return known.picture;
    }

    private static void loaded(Slot entry, Picture picture) {
        entry.picture = picture;
        entry.failed = picture == null;
        entry.failedAt = System.nanoTime();
        List<Consumer<Picture>> waiting = List.copyOf(entry.waiting);
        entry.waiting.clear();
        if (picture != null) {
            waiting.forEach(view -> view.accept(picture));
        }
    }

    /** A file is known by its address and its time: one that is saved again is loaded again. */
    private static String key(URL url) {
        if ("file".equals(url.getProtocol())) {
            try {
                return url + " " + new File(url.toURI()).lastModified();
            } catch (Exception e) {
                // not the address of a file after all
            }
        }
        return url.toString();
    }

    /** Reads the image at the address, on the thread that calls. Null if there is none that can be shown. */
    static Picture load(URL url) {
        try {
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(TIMEOUT);
            connection.setReadTimeout(TIMEOUT);
            byte[] data;
            try (InputStream in = connection.getInputStream()) {
                data = in.readNBytes(MAX_BYTES + 1);
            }
            if (data.length > MAX_BYTES) {
                return null;
            }
            // the address of a badge often has no ending, so the answer says what it is
            String type = connection.getContentType();
            if (type != null && type.toLowerCase(Locale.ROOT).contains("svg") || isMarkup(data)) {
                return svg(data);
            }
            // an image of the toolkit, which shows a GIF that moves as one
            ImageIcon icon = new ImageIcon(data);
            if (icon.getImageLoadStatus() != MediaTracker.COMPLETE || icon.getIconWidth() <= 0 || icon.getIconHeight() <= 0) {
                return null;
            }
            return new Picture(null, icon.getImage(), icon.getIconWidth(), icon.getIconHeight());
        } catch (Exception e) {
            // no answer, no such file, or not an image: the text of the image is shown in its place
            return null;
        }
    }

    private static Picture svg(byte[] data) {
        // an SVG of somebody else is not let read other files or addresses
        LoaderContext context = LoaderContext.builder().externalResourcePolicy(ResourcePolicy.DENY_EXTERNAL).build();
        SVGDocument svg = new SVGLoader().load(new ByteArrayInputStream(data), null, context);
        if (svg == null || svg.size().width <= 0 || svg.size().height <= 0) {
            return null;
        }
        return new Picture(svg, null, svg.size().width, svg.size().height);
    }

    /** Whether the bytes begin as XML does, and so as no image of pixels does. */
    private static boolean isMarkup(byte[] data) {
        int start = 0;
        // a byte order mark, and blanks
        if (data.length >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB && (data[2] & 0xFF) == 0xBF) {
            start = 3;
        }
        while (start < data.length && Character.isWhitespace(data[start])) {
            start++;
        }
        return start < data.length && data[start] == '<';
    }

    private static ExecutorService createLoader() {
        ThreadPoolExecutor loader = new ThreadPoolExecutor(4, 4, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), task -> {
            Thread thread = new Thread(task, "markdown-preview-images");
            thread.setDaemon(true);
            return thread;
        });
        loader.allowCoreThreadTimeOut(true);
        return loader;
    }
}
