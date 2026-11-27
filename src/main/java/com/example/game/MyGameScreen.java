package com.example.game;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.gui.screens.GameScreen;
import de.gurkenlabs.litiengine.input.Input;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import javax.imageio.ImageIO;

public class MyGameScreen extends GameScreen {
    private static final Color CREAM = new Color(244, 237, 217);
    private static final Color ORANGE = new Color(244, 153, 75);
    private static final Color BLOOD = new Color(214, 48, 52);

    private static final String[] LABELS = {"Continue", "New Game", "Settings", "Credits", "Quit"};
    // the shelter writes its progress here (see ShelterScreen.savePath)
    private static final Path SAVE_FILE = Path.of(
            System.getProperty("user.home"), ".quarantine_haven", "savegame.properties");
    private static final long CLICK_ANIMATION_NANOS = 230_000_000L;
    private static final long TOAST_DURATION_NANOS = 2_400_000_000L;
    private static final double FALL_SPEED = 0.045;      // background falls this many screen-heights per second
    private final Rectangle[] menuButtons = new Rectangle[LABELS.length];
    private final BufferedImage virusTile = loadVirusTile();
    private int hoveredButton = -1;
    private int pressedButton = -1;
    private long clickStartedAt;
    private String toastMessage;
    private long toastStartedAt;
    private long nextSaveCheckAt;
    private boolean savedGameAvailable;
    private long shownAtNanos = System.nanoTime();
    private Overlay overlay = Overlay.NONE;

    private enum Overlay { NONE, SETTINGS, CREDITS }

    public MyGameScreen() {
        super("HOME-SCREEN");
        Input.mouse().onClicked(this::handleClick);
        Input.mouse().onMoved(this::handleMouseMove);
        Input.keyboard().onKeyPressed(KeyEvent.VK_ESCAPE, event -> {
            if (overlay != Overlay.NONE) overlay = Overlay.NONE;
            else Game.exit();
        });
    }

    /** Every time the menu appears the title drops in again. */
    @Override
    public void prepare() {
        super.prepare();
        shownAtNanos = System.nanoTime();
        pressedButton = -1;
        hoveredButton = -1;
    }

    private void handleClick(MouseEvent event) {
        if (overlay != Overlay.NONE) {
            overlay = Overlay.NONE;
            return;
        }
        if (pressedButton >= 0) return;
        for (int i = 0; i < menuButtons.length; i++) {
            if (isOverButton(i, event.getX(), event.getY())) {
                pressedButton = i;
                hoveredButton = i;
                clickStartedAt = System.nanoTime();
                return;
            }
        }
    }

    private void handleMouseMove(MouseEvent event) {
        hoveredButton = -1;
        if (overlay != Overlay.NONE || pressedButton >= 0) return;
        for (int i = 0; i < menuButtons.length; i++) {
            if (isOverButton(i, event.getX(), event.getY())) {
                hoveredButton = i;
                break;
            }
        }
    }

    private boolean isOverButton(int index, int mouseX, int mouseY) {
        return menuButtons[index] != null && menuButtons[index].contains(mouseX, mouseY);
    }

    private void finishClickAnimation() {
        if (pressedButton < 0 || System.nanoTime() - clickStartedAt < CLICK_ANIMATION_NANOS) return;
        int action = pressedButton;
        pressedButton = -1;
        switch (action) {
            case 0, 1 -> Game.screens().display("SHELTER_SCREEN");
            case 2 -> overlay = Overlay.SETTINGS;
            case 3 -> overlay = Overlay.CREDITS;
            case 4 -> Game.exit();
            default -> { }
        }
    }

    private void showToast(String message) {
        toastMessage = message;
        toastStartedAt = System.nanoTime();
    }

    @Override
    public void render(Graphics2D g) {
        super.render(g);
        refreshSavedGameAvailability();
        finishClickAnimation();
        int w = getWidth() > 0 ? (int) getWidth() : 1280;
        int h = getHeight() > 0 ? (int) getHeight() : 720;
        long now = System.nanoTime();
        double seconds = (now - shownAtNanos) / 1_000_000_000.0;

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        drawFallingViruses(g, w, h, now / 1_000_000_000.0);
        drawTextShade(g, w, h);
        drawTitle(g, w, h, seconds);
        drawInteractiveMenu(g, w, h, seconds);
        drawFooter(g, w, h);
        if (overlay != Overlay.NONE) drawOverlay(g, w, h);
        else if (toastMessage != null) drawToast(g, w, h);
    }

    private void refreshSavedGameAvailability() {
        long now = System.nanoTime();
        if (now < nextSaveCheckAt) return;
        nextSaveCheckAt = now + 1_000_000_000L;
        try {
            savedGameAvailable = Files.isRegularFile(SAVE_FILE) && Files.size(SAVE_FILE) > 0;
        } catch (IOException exception) {
            savedGameAvailable = false;
        }
    }

    private String menuLabel(int index) {
        if (index == 0) return savedGameAvailable ? "Continue" : "Start Game";
        return LABELS[index];
    }

    // ------------------------------------------------------------------ background

    private static BufferedImage loadVirusTile() {
        for (String name : new String[]{"/menu_virus_bg.png", "/Quarantine Haven_ Sunset Facility Menu.png"}) {
            try (var input = MyGameScreen.class.getResourceAsStream(name)) {
                if (input != null) {
                    BufferedImage image = ImageIO.read(input);
                    if (image != null) return image;
                }
            } catch (IOException exception) {
                // try the next one
            }
        }
        return createFallbackTile();
    }

    /** Used only when menu_virus_bg.png is missing: a dark-red tile with a few round "viruses". */
    private static BufferedImage createFallbackTile() {
        BufferedImage tile = new BufferedImage(640, 360, BufferedImage.TYPE_INT_RGB);
        Graphics2D t = tile.createGraphics();
        t.setColor(new Color(56, 14, 16));
        t.fillRect(0, 0, 640, 360);
        Random random = new Random(7);
        for (int i = 0; i < 16; i++) {
            int x = random.nextInt(640), y = random.nextInt(360), r = 10 + random.nextInt(24);
            for (int ox = -640; ox <= 640; ox += 640) {
                for (int oy = -360; oy <= 360; oy += 360) {
                    t.setColor(new Color(170, 36, 40));
                    t.fillOval(x + ox - r, y + oy - r, r * 2, r * 2);
                    t.setColor(new Color(214, 60, 60));
                    t.fillOval(x + ox - r / 3, y + oy - r / 3, r / 2, r / 2);
                }
            }
        }
        t.dispose();
        return tile;
    }

    /** Two copies of the artwork fall at different speeds (the big, blurry-looking one is "closer"). */
    private void drawFallingViruses(Graphics2D g, int w, int h, double time) {
        drawFallingLayer(g, w, h, 1.0, 1.0, 1.0f, time, 0.0);
        // a second, bigger, slower-looking layer is drawn on top for depth - falls faster, shifted sideways, translucent
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.34f));
        drawFallingLayer(g, w, h, 1.7, 1.75, 0.34f, time, w * 0.31);
        g.setComposite(old);
    }

    private void drawFallingLayer(Graphics2D g, int w, int h, double zoom, double speed, float alpha,
                                  double time, double shiftX) {
        double scale = (double) w / virusTile.getWidth() * zoom;
        int tileW = (int) Math.ceil(virusTile.getWidth() * scale);
        int tileH = (int) Math.ceil(virusTile.getHeight() * scale);
        double fall = (time * FALL_SPEED * h * speed) % tileH;
        // gentle side-to-side drift so it does not look mechanical
        double sway = Math.sin(time * 0.6 * speed) * 14 * zoom;
        int startX = (int) (((sway + shiftX) % tileW) - tileW);
        for (int x = startX; x < w; x += tileW) {
            for (double y = fall - tileH; y < h; y += tileH) {
                g.drawImage(virusTile, x, (int) Math.floor(y), tileW + 1, tileH + 1, null);
            }
        }
    }

    private void drawTextShade(Graphics2D g, int w, int h) {
        g.setPaint(new GradientPaint(0, 0, new Color(12, 4, 6, 214), w * 0.62f, 0, new Color(12, 4, 6, 20)));
        g.fillRect(0, 0, (int) (w * 0.66), h);
        g.setPaint(new GradientPaint(0, h * 0.78f, new Color(0, 0, 0, 0), 0, h, new Color(0, 0, 0, 150)));
        g.fillRect(0, (int) (h * 0.78), w, h - (int) (h * 0.78));
        g.setColor(BLOOD);
        g.fillRect(0, 0, Math.max(5, w / 200), h);
    }

    // ------------------------------------------------------------------ animated title

    private void drawTitle(Graphics2D g, int w, int h, double seconds) {
        int x = Math.max(48, (int) (w * 0.082));
        int size = Math.max(34, Math.min(86, (int) (h * 0.115)));
        Font font = new Font(Font.MONOSPACED, Font.BOLD, size);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int line1Y = (int) (h * 0.19) + size;
        int line2Y = line1Y + (int) (size * 1.02);

        g.setFont(new Font("SansSerif", Font.BOLD, Math.max(12, size / 5)));
        float tagFade = (float) Math.max(0, Math.min(1, (seconds - 0.2) / 0.8));
        g.setColor(new Color(255, 186, 99, (int) (255 * tagFade)));
        g.drawString("COMMUNITY CARE  /  DAY 01", x + 4, line1Y - size + 2);

        g.setFont(font);
        drawWaveWord(g, "QUARANTINE", x, line1Y, size, seconds, 0.0);
        drawWaveWord(g, "HAVEN", x, line2Y, size, seconds, 0.7);

        // underline that sweeps back and forth
        int barY = line2Y + (int) (size * 0.28);
        int barW = (int) (fm.stringWidth("QUARANTINE") * 0.55);
        double sweep = 0.5 + 0.5 * Math.sin(seconds * 1.6);
        g.setColor(new Color(214, 48, 52, 90));
        g.fillRect(x, barY, barW, 5);
        g.setColor(ORANGE);
        g.fillRect(x + (int) ((barW - 70) * sweep), barY, 70, 5);

        g.setFont(new Font("SansSerif", Font.PLAIN, Math.max(13, size / 4)));
        float subFade = (float) Math.max(0, Math.min(1, (seconds - 1.4) / 0.9));
        g.setColor(new Color(228, 218, 200, (int) (255 * subFade)));
        g.drawString("A SURVIVAL MANAGEMENT GAME", x + 4, barY + Math.max(26, size / 3));
    }

    /** Draws one word letter by letter: drops in, then bobs on a travelling wave, pulses red, and glitches now and then. */
    private void drawWaveWord(Graphics2D g, String word, int x, int baseline, int size, double seconds, double delay) {
        FontMetrics fm = g.getFontMetrics();
        int cursor = x;
        // short glitch burst every ~5 seconds
        double cycle = seconds % 5.0;
        boolean glitch = seconds > 2.0 && cycle > 4.55 && cycle < 4.75;
        for (int i = 0; i < word.length(); i++) {
            String letter = String.valueOf(word.charAt(i));
            double appear = Math.max(0, Math.min(1, (seconds - delay - i * 0.06) / 0.55));
            double eased = 1 - Math.pow(1 - appear, 3);                       // ease-out drop
            double bounce = appear < 1 ? Math.sin(appear * Math.PI) * 10 * (1 - appear) : 0;
            int drop = (int) ((1 - eased) * -size * 0.8 - bounce);
            int bob = (int) Math.round(Math.sin(seconds * 2.4 + i * 0.55 + delay * 2) * size * 0.045);
            int lx = cursor + (glitch ? (int) (Math.sin(seconds * 90 + i) * 6) : 0);
            int ly = baseline + drop + bob;
            float alpha = (float) Math.min(1.0, appear * 1.6);
            int a = (int) (255 * alpha);

            g.setColor(new Color(10, 3, 4, (int) (200 * alpha)));
            g.drawString(letter, lx + 4, ly + 5);                              // drop shadow
            double pulse = 0.5 + 0.5 * Math.sin(seconds * 2.0 - i * 0.45 - delay * 3);
            int glow = (int) (28 + 60 * pulse);
            g.setColor(new Color(214, 48, 52, (int) (glow * alpha)));
            g.drawString(letter, lx - 2, ly);
            g.drawString(letter, lx + 2, ly);
            if (glitch) {
                g.setColor(new Color(80, 220, 235, 170));
                g.drawString(letter, lx - 5, ly);
            }
            int r = 244, gr = (int) (237 - 70 * pulse), b = (int) (217 - 90 * pulse);   // cream warming to a blood tint
            g.setColor(new Color(r, Math.max(0, gr), Math.max(0, b), a));
            g.drawString(letter, lx, ly);
            cursor += fm.stringWidth(letter);
        }
    }

    // ------------------------------------------------------------------ buttons

    private void drawInteractiveMenu(Graphics2D g, int width, int height, double seconds) {
        int x = Math.max(48, (int) (width * 0.082));
        int bw = (int) (width * 0.25);
        int bh = Math.max(40, (int) (height * 0.068));
        int gap = Math.max(8, (int) (height * 0.014));
        int top = (int) (height * 0.53);
        for (int i = 0; i < menuButtons.length; i++) {
            // slide in from the left, one after another
            double t = Math.max(0, Math.min(1, (seconds - 1.0 - i * 0.12) / 0.5));
            int slide = (int) ((1 - (1 - Math.pow(1 - t, 3))) * -(bw + x));
            menuButtons[i] = new Rectangle(x + slide, top + i * (bh + gap), bw, bh);
        }
        long now = System.nanoTime();
        int active = pressedButton >= 0 ? pressedButton : hoveredButton;
        for (int i = 0; i < menuButtons.length; i++) {
            if (i == active) continue;
            drawNormalButton(g, menuButtons[i], i);
        }
        if (active >= 0) {
            double press = clickPress(now);
            Rectangle button = new Rectangle(menuButtons[active]);
            button.translate(0, (int) Math.round(press * 3));
            drawActiveButton(g, button, active, press, now);
        }
        if (pressedButton >= 0) drawClickRipple(g, menuButtons[pressedButton], now);
    }

    private void drawNormalButton(Graphics2D g, Rectangle b, int index) {
        int arc = Math.max(8, b.height / 4);
        g.setColor(new Color(10, 4, 6, 150));
        g.fillRoundRect(b.x + 3, b.y + 4, b.width, b.height, arc, arc);
        g.setColor(new Color(36, 14, 18, 226));
        g.fillRoundRect(b.x, b.y, b.width, b.height, arc, arc);
        g.setColor(new Color(120, 44, 48, 200));
        g.drawRoundRect(b.x, b.y, b.width - 1, b.height - 1, arc, arc);
        g.setColor(new Color(214, 48, 52));
        g.fillRoundRect(b.x, b.y + 5, 5, b.height - 10, 4, 4);
        drawButtonLabel(g, b, menuLabel(index), new Color(237, 228, 214));
    }

    private void drawActiveButton(Graphics2D g, Rectangle b, int index, double press, long now) {
        int pulse = (int) (40 + 40 * (0.5 + 0.5 * Math.sin(now / 180_000_000.0)));
        int arc = Math.max(8, b.height / 4);
        g.setColor(new Color(255, 90, 80, pulse));
        g.fillRoundRect(b.x - 7, b.y - 6, b.width + 14, b.height + 12, arc + 6, arc + 6);
        g.setColor(new Color(10, 4, 6, 170));
        g.fillRoundRect(b.x + 4, b.y + 5, b.width, b.height, arc, arc);
        g.setColor(press > 0 ? new Color(150, 34, 44) : new Color(196, 44, 56));
        g.fillRoundRect(b.x, b.y, b.width, b.height, arc, arc);
        g.setColor(new Color(255, 170, 150));
        g.drawRoundRect(b.x, b.y, b.width - 1, b.height - 1, arc, arc);
        g.setColor(Color.WHITE);
        int[] ax = {b.x + b.width - 30, b.x + b.width - 20, b.x + b.width - 30};
        int[] ay = {b.y + b.height / 2 - 7, b.y + b.height / 2, b.y + b.height / 2 + 7};
        g.fillPolygon(ax, ay, 3);
        drawButtonLabel(g, b, menuLabel(index), Color.WHITE);
    }

    private double clickPress(long now) {
        if (pressedButton < 0) return 0;
        double progress = Math.min(1.0, (now - clickStartedAt) / (double) CLICK_ANIMATION_NANOS);
        return Math.sin(Math.PI * progress);
    }

    private void drawClickRipple(Graphics2D g, Rectangle button, long now) {
        double progress = Math.min(1.0, (now - clickStartedAt) / (double) CLICK_ANIMATION_NANOS);
        int expand = 3 + (int) (progress * 24);
        int alpha = (int) (190 * (1.0 - progress));
        g.setColor(new Color(255, 230, 201, Math.max(0, alpha)));
        Stroke oldStroke = g.getStroke();
        g.setStroke(new BasicStroke(2.0f));
        g.drawRect(button.x - expand, button.y - expand, button.width + expand * 2, button.height + expand * 2);
        g.setStroke(oldStroke);
    }

    private void drawButtonLabel(Graphics2D g, Rectangle button, String label, Color color) {
        int fontSize = Math.max(18, button.height * 42 / 100);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, fontSize));
        g.setColor(color);
        int baseline = button.y + button.height / 2 + g.getFontMetrics().getAscent() / 3;
        g.drawString(label, button.x + 26, baseline);
    }

    private void drawFooter(Graphics2D g, int w, int h) {
        g.setColor(new Color(225, 213, 194, 210));
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.drawString("MANAGE SUPPLIES  •  PROTECT THE COMMUNITY", Math.max(48, (int) (w * 0.082)), h - 20);
        g.drawString("V 1.0.0", w - 76, h - 20);
    }

    private void drawToast(Graphics2D g, int width, int height) {
        long elapsed = System.nanoTime() - toastStartedAt;
        if (elapsed >= TOAST_DURATION_NANOS) {
            toastMessage = null;
            return;
        }
        float fade = elapsed > TOAST_DURATION_NANOS - 350_000_000L
                ? (TOAST_DURATION_NANOS - elapsed) / 350_000_000f : 1f;
        int alpha = Math.max(0, Math.min(220, (int) (220 * fade)));
        int boxW = Math.min(width / 2, 480);
        int boxH = 58;
        int x = (width - boxW) / 2;
        int y = height - boxH - 34;
        g.setColor(new Color(26, 28, 42, alpha));
        g.fillRoundRect(x, y, boxW, boxH, 12, 12);
        g.setColor(new Color(244, 153, 75, alpha));
        g.drawRoundRect(x, y, boxW, boxH, 12, 12);
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        g.setColor(new Color(250, 240, 226, alpha));
        int textX = x + (boxW - g.getFontMetrics().stringWidth(toastMessage)) / 2;
        g.drawString(toastMessage, textX, y + 36);
    }

    private void drawOverlay(Graphics2D g, int width, int height) {
        g.setColor(new Color(15, 18, 30, 178));
        g.fillRect(0, 0, width, height);
        int boxW = Math.min(620, width - 48);
        int boxH = 290;
        int x = (width - boxW) / 2;
        int y = (height - boxH) / 2;
        g.setColor(new Color(28, 32, 50));
        g.fillRoundRect(x + 7, y + 8, boxW, boxH, 18, 18);
        g.setColor(new Color(42, 47, 69));
        g.fillRoundRect(x, y, boxW, boxH, 18, 18);
        g.setColor(ORANGE);
        g.fillRoundRect(x, y, boxW, 8, 8, 8);
        g.setColor(CREAM);
        g.setFont(new Font("SansSerif", Font.BOLD, 34));
        String title = switch (overlay) {
            case SETTINGS -> "Settings";
            case CREDITS -> "Credits";
            default -> "";
        };
        g.drawString(title, x + 38, y + 67);
        g.setFont(new Font("SansSerif", Font.PLAIN, 20));
        g.setColor(new Color(231, 226, 216));
        switch (overlay) {
            case SETTINGS -> {
                g.drawString("Audio settings will be available soon.", x + 38, y + 126);
                g.drawString("Use the mouse to navigate the menu.", x + 38, y + 160);
            }
            case CREDITS -> {
                g.drawString("Quarantine Haven", x + 38, y + 126);
                g.drawString("A survival management game.", x + 38, y + 160);
                g.drawString("Game By:", x + 38, y + 194);
                g.drawString("USC Wanted List", x + 38, y + 228);
            }
            default -> { }
        }
        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        g.setColor(new Color(255, 194, 132));
        g.drawString("CLICK OR PRESS ESC TO RETURN", x + 38, y + boxH - 32);
    }

}
