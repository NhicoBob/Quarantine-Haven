package com.example.game;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.gui.screens.GameScreen;
import de.gurkenlabs.litiengine.input.Input;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.BasicStroke;
import java.awt.Stroke;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

public class MyGameScreen extends GameScreen {
    private static final int ART_WIDTH = 640;
    private static final int ART_HEIGHT = 360;
    private static final Color CREAM = new Color(244, 237, 217);
    private static final Color ORANGE = new Color(244, 153, 75);

    private static final String[] LABELS = {"Continue", "New Game", "Settings", "Credits", "Quit"};
    private static final Path SAVE_FILE = Path.of(
            System.getProperty("user.home"), ".quarantine-haven", "save.dat");
    private static final long CLICK_ANIMATION_NANOS = 230_000_000L;
    private static final long TOAST_DURATION_NANOS = 2_400_000_000L;
    private final Rectangle[] menuButtons = new Rectangle[LABELS.length];
    private final BufferedImage backgroundScene = loadBackgroundScene();
    private final BufferedImage[] menuIcons = createMenuIcons(backgroundScene);
    private final String[] labels = {"PLAY", "CONTINUE", "SETTINGS", "EXIT"};
    private boolean settingsVisible;
    private String message = "";
    private int hoveredButton = -1;
    private int pressedButton = -1;
    private long clickStartedAt;
    private String toastMessage;
    private long toastStartedAt;
    private long nextSaveCheckAt;
    private boolean savedGameAvailable;
    private Overlay overlay = Overlay.NONE;

    private enum Overlay { NONE, SETTINGS, CREDITS, NEW_GAME }

    public MyGameScreen() {
        super("HOME-SCREEN");
        Input.mouse().onClicked(this::handleClick);
        Input.mouse().onMoved(this::handleMouseMove);
        Input.keyboard().onKeyPressed(KeyEvent.VK_ESCAPE, event -> {
            if (overlay != Overlay.NONE) overlay = Overlay.NONE;
            else Game.exit();
        });
    }

    private void handleClick(MouseEvent event) {
        if (overlay != Overlay.NONE) {
            if (overlay == Overlay.NEW_GAME) {
                Game.screens().display("SHELTER_SCREEN");
                return;
            }
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
        if (overlay != Overlay.NONE || pressedButton >= 0) {
            hoveredButton = -1;
            return;
        }
        hoveredButton = -1;
        for (int i = 0; i < menuButtons.length; i++) {
            if (isOverButton(i, event.getX(), event.getY())) {
                hoveredButton = i;
                break;
            }
        }
    }

    private boolean isOverButton(int index, int mouseX, int mouseY) {
        if (menuButtons[index] == null) return false;
        return menuButtons[index].contains(mouseX, mouseY);
    }

    private void finishClickAnimation() {
        if (pressedButton < 0 || System.nanoTime() - clickStartedAt < CLICK_ANIMATION_NANOS) return;
        int action = pressedButton;
        pressedButton = -1;
        switch (action) {
            case 0 -> {
                if (savedGameAvailable) showToast("Continue is not available yet.");
                else Game.screens().display("SHELTER_SCREEN");
            }
            case 1 -> Game.screens().display("SHELTER_SCREEN");
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

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(backgroundScene, 0, 0, w, h, null);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        drawInteractiveMenu(g, w, h);
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

    private static BufferedImage loadBackgroundScene() {
        try (var input = MyGameScreen.class.getResourceAsStream("/Quarantine Haven_ Sunset Facility Menu.png")) {
            if (input == null) return createBackgroundScene();
            return ImageIO.read(input);
        } catch (IOException exception) {
            return createBackgroundScene();
        }
    }

    private static BufferedImage[] createMenuIcons(BufferedImage artwork) {
        // Source rectangles are the five original icons in the reference artwork (at 1680x945).
        double[][] source = {
                {181, 498, 45, 48}, {179, 581, 48, 50}, {179, 653, 48, 50},
                {178, 728, 49, 50}, {179, 806, 49, 52}
        };
        BufferedImage[] icons = new BufferedImage[source.length];
        double scaleX = artwork.getWidth() / 1680.0;
        double scaleY = artwork.getHeight() / 945.0;
        for (int index = 0; index < source.length; index++) {
            int sx = (int) Math.round(source[index][0] * scaleX);
            int sy = (int) Math.round(source[index][1] * scaleY);
            int sw = (int) Math.round(source[index][2] * scaleX);
            int sh = (int) Math.round(source[index][3] * scaleY);
            BufferedImage icon = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < sh; y++) {
                for (int x = 0; x < sw; x++) {
                    Color pixel = new Color(artwork.getRGB(sx + x, sy + y));
                    int brightness = (pixel.getRed() + pixel.getGreen() + pixel.getBlue()) / 3;
                    icon.setRGB(x, y, brightness > 105 ? artwork.getRGB(sx + x, sy + y) : 0x00000000);
                }
            }
            icons[index] = icon;
        }
        return icons;
    }

    private void drawInteractiveMenu(Graphics2D g, int width, int height) {
        double[][] boxes = {
                {0.082, 0.511, 0.213, 0.083},
                {0.083, 0.606, 0.210, 0.070},
                {0.083, 0.684, 0.210, 0.071},
                {0.083, 0.761, 0.210, 0.073},
                {0.083, 0.839, 0.210, 0.073}
        };
        for (int i = 0; i < boxes.length; i++) {
            menuButtons[i] = new Rectangle(
                    (int) (width * boxes[i][0]), (int) (height * boxes[i][1]),
                    (int) (width * boxes[i][2]), (int) (height * boxes[i][3]));
        }

        long now = System.nanoTime();
        for (int i = 0; i < menuButtons.length; i++) {
            drawNormalButton(g, menuButtons[i], i);
        }

        int activeButton = pressedButton >= 0 ? pressedButton : hoveredButton;
        if (activeButton >= 0) {
            double press = clickPress(now);
            Rectangle button = new Rectangle(menuButtons[activeButton]);
            button.translate(0, (int) Math.round(press * 3));
            drawActiveButton(g, button, activeButton, true, press, now);
        }
        if (pressedButton >= 0) drawClickRipple(g, menuButtons[pressedButton], now);
    }

    private void drawNormalButton(Graphics2D g, Rectangle button, int index) {
        int padX = Math.max(5, button.width / 40);
        int padY = index == 0 ? Math.max(10, button.height / 8) : Math.max(4, button.height / 12);
        // Opaque Java-drawn panel hides the matching baked button in the reference artwork.
        int arc = Math.max(8, button.height / 7);
        g.setColor(new Color(32, 37, 54));
        g.fillRoundRect(button.x - padX, button.y - padY, button.width + padX * 2, button.height + padY * 2, arc, arc);
        g.setColor(new Color(47, 53, 72));
        g.fillRoundRect(button.x, button.y, button.width, button.height, arc, arc);
        g.setColor(new Color(18, 22, 35));
        g.drawRoundRect(button.x, button.y, button.width - 1, button.height - 1, arc, arc);
        g.setColor(new Color(58, 65, 84));
        g.drawLine(button.x + 2, button.y + 2, button.x + button.width - 3, button.y + 2);
        drawMenuIcon(g, button, index);
        drawButtonLabel(g, button, menuLabel(index), new Color(237, 235, 226));
    }

    private double clickPress(long now) {
        if (pressedButton < 0) return 0;
        double progress = Math.min(1.0, (now - clickStartedAt) / (double) CLICK_ANIMATION_NANOS);
        return Math.sin(Math.PI * progress);
    }

    private void drawHoverHighlight(Graphics2D g, Rectangle button, long now) {
        double pulse = 0.5 + 0.5 * Math.sin(now / 170_000_000.0);
        int glowAlpha = (int) (35 + pulse * 35);
        g.setColor(new Color(255, 222, 183, glowAlpha));
        g.drawRect(button.x - 5, button.y - 5, button.width + 10, button.height + 10);

        Stroke oldStroke = g.getStroke();
        g.setStroke(new BasicStroke(2.0f + (float) pulse));
        g.setColor(new Color(255, 239, 217, 205));
        int cornerLength = Math.max(12, button.width / 24);
        int x1 = button.x - 3;
        int x2 = button.x + button.width + 2;
        int y1 = button.y - 3;
        int y2 = button.y + button.height + 2;

        int bracket = cornerLength + (int) (pulse * 3);
        g.drawLine(x1, y1, x1 + bracket, y1); g.drawLine(x1, y1, x1, y1 + bracket);
        g.drawLine(x2, y1, x2 - bracket, y1); g.drawLine(x2, y1, x2, y1 + bracket);
        g.drawLine(x1, y2, x1 + bracket, y2); g.drawLine(x1, y2, x1, y2 - bracket);
        g.drawLine(x2, y2, x2 - bracket, y2); g.drawLine(x2, y2, x2, y2 - bracket);

        int glintX = button.x + 10 + (int) ((button.width - 20) * ((now / 1_400_000_000.0) % 1.0));
        g.setStroke(new BasicStroke(2.0f));
        g.setColor(new Color(255, 239, 217, 150));
        g.drawLine(glintX, button.y - 3, Math.min(button.x + button.width - 4, glintX + 20), button.y - 3);
        g.setStroke(oldStroke);
    }

    private void drawClickRipple(Graphics2D g, Rectangle button, long now) {
        double progress = Math.min(1.0, (now - clickStartedAt) / (double) CLICK_ANIMATION_NANOS);
        int expand = 3 + (int) (progress * 24);
        int alpha = (int) (190 * (1.0 - progress));
        g.setColor(new Color(255, 230, 201, Math.max(0, alpha)));
        Stroke oldStroke = g.getStroke();
        g.setStroke(new BasicStroke(2.0f));
        g.drawRect(button.x - expand, button.y - expand,
                button.width + expand * 2, button.height + expand * 2);
        g.setStroke(oldStroke);
    }

    private void drawInactiveContinue(Graphics2D g, Rectangle button) {
        int pad = Math.max(4, button.width / 45);
        g.setColor(new Color(36, 42, 59));
        g.fillRect(button.x - pad, button.y - pad, button.width + pad * 2, button.height + pad * 2);
        g.setColor(new Color(17, 21, 33));
        g.fillRect(button.x, button.y, button.width, button.height);
        g.setColor(new Color(91, 101, 126));
        g.drawRect(button.x, button.y, button.width - 1, button.height - 1);
        drawMenuIcon(g, button, 0);
        drawButtonLabel(g, button, "Continue", new Color(232, 231, 226));
    }

    private void drawActiveButton(Graphics2D g, Rectangle button, int index,
                                  boolean hovered, double press, long now) {
        int pulse = (int) (35 + 30 * (0.5 + 0.5 * Math.sin(now / 180_000_000.0)));
        if (hovered || hoveredButton < 0) {
            g.setColor(new Color(255, 179, 128, pulse));
            g.fillRect(button.x - 8, button.y - 7, button.width + 16, button.height + 14);
        }

        Color face = new Color(183, 46, 65);
        if (press > 0) face = new Color(139, 41, 56);
        int arc = Math.max(8, button.height / 7);
        g.setColor(new Color(23, 24, 38));
        g.fillRoundRect(button.x + 4, button.y + 5, button.width, button.height, arc, arc);
        g.setColor(face);
        g.fillRoundRect(button.x, button.y, button.width, button.height, arc, arc);
        g.setColor(new Color(111, 43, 54));
        g.setStroke(new BasicStroke(Math.max(2.0f, button.width / 180.0f)));
        g.drawRoundRect(button.x, button.y, button.width - 1, button.height - 1, arc, arc);
        drawMenuIcon(g, button, index);
        drawButtonLabel(g, button, menuLabel(index), Color.WHITE);
        g.setStroke(new BasicStroke(1));
    }

    private void drawMenuIcon(Graphics2D g, Rectangle button, int index) {
        BufferedImage icon = menuIcons[index];
        int targetHeight = Math.max(22, button.height * 53 / 100);
        int targetWidth = Math.max(18, targetHeight * icon.getWidth() / icon.getHeight());
        int x = button.x + button.width * 135 / 1000;
        int y = button.y + (button.height - targetHeight) / 2;
        g.drawImage(icon, x, y, targetWidth, targetHeight, null);
    }

    private void drawButtonLabel(Graphics2D g, Rectangle button, String label, Color color) {
        int fontSize = Math.max(20, button.height * 38 / 100);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, fontSize));
        g.setColor(color);
        int center = button.x + (int) (button.width * 0.59);
        int textX = center - g.getFontMetrics().stringWidth(label) / 2;
        int baseline = button.y + button.height / 2 + g.getFontMetrics().getAscent() / 3;
        g.drawString(label, textX, baseline);
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
            case NEW_GAME -> "New Game";
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
            }
            case NEW_GAME -> {
                g.drawString("Your first day at the care facility is almost ready.", x + 38, y + 126);
                g.drawString("New game flow is still in development.", x + 38, y + 160);
            }
            default -> { }
        }
        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        g.setColor(new Color(255, 194, 132));
        g.drawString("CLICK OR PRESS ESC TO RETURN", x + 38, y + boxH - 32);
    }

    private static BufferedImage createBackgroundScene() {
        BufferedImage scene = new BufferedImage(ART_WIDTH, ART_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D art = scene.createGraphics();
        art.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        drawFacilityScene(art);
        art.dispose();
        return scene;
    }

    /** A warm, low-resolution illustration of a gym converted into a care facility. */
    private static void drawFacilityScene(Graphics2D g) {
        g.setColor(new Color(126, 58, 48));
        g.fillRect(0, 0, ART_WIDTH, ART_HEIGHT);
        g.setColor(new Color(202, 100, 56));
        g.fillRect(0, 0, ART_WIDTH, 118);
        g.setColor(new Color(241, 151, 76));
        g.fillRect(330, 26, 59, 43);
        g.fillRect(341, 17, 38, 9);
        // High gym windows and thick structural columns.
        g.setColor(new Color(58, 42, 44));
        g.fillRect(0, 0, ART_WIDTH, 19);
        g.fillRect(245, 19, 9, 124); g.fillRect(390, 19, 9, 124); g.fillRect(535, 19, 9, 124);
        g.setColor(new Color(246, 173, 94));
        g.fillRect(265, 37, 65, 63); g.fillRect(405, 37, 65, 63); g.fillRect(550, 37, 65, 63);
        g.setColor(new Color(96, 53, 48));
        g.fillRect(293, 37, 6, 63); g.fillRect(433, 37, 6, 63); g.fillRect(578, 37, 6, 63);
        g.fillRect(265, 66, 65, 5); g.fillRect(405, 66, 65, 5); g.fillRect(550, 66, 65, 5);

        // Gym walls, basketball markings, and open floor.
        g.setColor(new Color(83, 55, 50));
        g.fillRect(0, 118, ART_WIDTH, 137);
        g.setColor(new Color(111, 68, 53));
        g.fillRect(0, 244, ART_WIDTH, 116);
        g.setColor(new Color(158, 95, 60));
        g.fillRect(300, 244, 3, 116); g.fillRect(0, 298, ART_WIDTH, 3);
        g.drawRect(388, 269, 58, 48);
        g.setColor(new Color(54, 42, 43));
        g.fillRect(73, 156, 124, 66); // dark partitioned triage area
        g.fillRect(70, 153, 130, 6);
        g.setColor(new Color(196, 130, 80));
        g.fillRect(83, 166, 104, 4); g.fillRect(83, 184, 104, 4);

        // Privacy screens and simple cots in rows.
        int[][] beds = {{263, 159}, {369, 159}, {475, 159}, {263, 211}, {369, 211}, {475, 211}};
        for (int[] bed : beds) {
            int x = bed[0], y = bed[1];
            g.setColor(new Color(46, 42, 43));
            g.fillRect(x - 7, y - 8, 92, 4); // curtain rail
            g.setColor(new Color(137, 94, 70));
            g.fillRect(x - 4, y - 4, 3, 36); g.fillRect(x + 82, y - 4, 3, 36);
            g.setColor(new Color(113, 143, 128));
            g.fillRect(x, y + 8, 78, 15);
            g.setColor(new Color(221, 209, 179));
            g.fillRect(x + 8, y + 4, 23, 9);
            g.setColor(new Color(45, 41, 42));
            g.fillRect(x, y + 23, 5, 11); g.fillRect(x + 73, y + 23, 5, 11);
        }
        // Hanging curtain panels create separated patient bays.
        g.setColor(new Color(101, 125, 111));
        g.fillRect(252, 155, 5, 49); g.fillRect(356, 155, 5, 49); g.fillRect(462, 155, 5, 49);
        g.fillRect(252, 207, 5, 48); g.fillRect(356, 207, 5, 48); g.fillRect(462, 207, 5, 48);

        // Supply station, water containers, and marked medical area.
        g.setColor(new Color(55, 42, 39));
        g.fillRect(17, 221, 90, 8);
        g.setColor(new Color(178, 128, 72));
        g.fillRect(21, 196, 32, 25); g.fillRect(57, 203, 27, 18);
        g.setColor(new Color(67, 126, 139));
        g.fillRect(109, 207, 17, 34); g.fillRect(133, 207, 17, 34);
        g.setColor(new Color(229, 219, 190));
        g.fillRect(112, 204, 11, 4); g.fillRect(136, 204, 11, 4);

        g.setColor(new Color(225, 212, 185));
        g.fillRect(207, 130, 24, 24);
        g.setColor(new Color(163, 63, 48));
        g.fillRect(217, 134, 5, 16); g.fillRect(212, 139, 15, 5);

        g.setColor(new Color(42, 34, 36));
        g.fillRect(0, 320, ART_WIDTH, 40);
        g.setColor(new Color(130, 75, 49));
        g.fillRect(15, 290, 62, 29); g.fillRect(22, 278, 47, 12);
        g.fillRect(86, 301, 46, 18); g.fillRect(520, 299, 79, 20); g.fillRect(532, 286, 54, 13);
        g.setColor(new Color(239, 145, 65));
        g.fillRect(25, 281, 12, 4); g.fillRect(551, 289, 12, 4);

        g.setColor(new Color(255, 187, 86));
        g.fillRect(235, 116, 12, 5); g.fillRect(500, 118, 6, 5); g.fillRect(188, 116, 6, 4);
        g.setColor(new Color(226, 125, 67));
        g.fillRect(22, 35, 64, 5); g.fillRect(93, 47, 42, 4); g.fillRect(152, 28, 50, 4);
    }

    private void drawTextShade(Graphics2D g, int w, int h) {
        g.setPaint(new GradientPaint(0, 0, new Color(20, 20, 23, 245), w * 0.58f, 0, new Color(20, 20, 23, 178)));
        g.fillRect(0, 0, w * 3 / 5, h);
        g.setColor(new Color(238, 138, 66));
        g.fillRect(0, 0, Math.max(5, w / 180), h);
    }

    private void drawTitle(Graphics2D g, int w, int h) {
        int x = Math.max(52, w / 13);
        int titleSize = Math.max(38, Math.min(62, w / 22));
        int y = Math.max(75, h / 6);
        g.setColor(new Color(255, 186, 99));
        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        g.drawString("COMMUNITY CARE  /  DAY 01", x, y);
        g.setColor(new Color(15, 15, 17, 210));
        g.setFont(new Font("SansSerif", Font.BOLD, titleSize));
        g.drawString("QUARANTINE", x + 3, y + titleSize + 7);
        g.drawString("HAVEN", x + 3, y + titleSize * 2 + 12);
        g.setColor(CREAM);
        g.drawString("QUARANTINE", x, y + titleSize + 3);
        g.drawString("HAVEN", x, y + titleSize * 2 + 8);
        g.setColor(ORANGE);
        g.fillRect(x, y + titleSize * 2 + 24, 86, 5);
        g.setColor(new Color(228, 218, 200));
        g.setFont(new Font("SansSerif", Font.PLAIN, 17));
        g.drawString("A SURVIVAL MANAGEMENT GAME", x, y + titleSize * 2 + 58);
        g.setColor(new Color(199, 186, 171));
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g.drawString("Keep residents healthy, fed, and calm.", x, y + titleSize * 2 + 82);
    }

    private void drawMenu(Graphics2D g, int w, int h) {
        int x = Math.max(52, w / 13);
        int titleSize = Math.max(38, Math.min(62, w / 22));
        int y = Math.max(h / 2, h / 6 + titleSize * 2 + 112);
        int buttonW = Math.min(340, w * 2 / 5);
        int buttonH = Math.max(45, h / 15);
        int gap = Math.max(8, h / 90);
        long now = System.nanoTime();
        double clickProgress = pressedButton < 0 ? 0
                : Math.min(1.0, (now - clickStartedAt) / (double) CLICK_ANIMATION_NANOS);
        double pressDepth = pressedButton < 0 ? 0
                : Math.sin(Math.PI * clickProgress) * 5.0;
        for (int i = 0; i < labels.length; i++) {
            int by = y + i * (buttonH + gap);
            menuButtons[i] = new Rectangle(x, by, buttonW, buttonH);
            boolean hovered = i == hoveredButton && pressedButton < 0 && !settingsVisible;
            boolean pressed = i == pressedButton;
            int offsetX = hovered ? 8 : 0;
            int offsetY = pressed ? (int) Math.round(pressDepth) : 0;
            int bx = x + offsetX;
            int drawY = by + offsetY;
            if (hovered) {
                int pulse = (int) (30 + 20 * (0.5 + 0.5 * Math.sin(now / 130_000_000.0)));
                g.setColor(new Color(ORANGE.getRed(), ORANGE.getGreen(), ORANGE.getBlue(), pulse));
                g.fillRect(bx - 7, drawY - 5, buttonW + 14, buttonH + 10);
            }
            g.setColor(new Color(13, 17, 19, 220));
            g.fillRect(bx + 5, drawY + 5, buttonW, buttonH);
            Color face = i == 0 ? new Color(159, 75, 49) : new Color(37, 41, 41);
            if (hovered) face = i == 0 ? new Color(194, 94, 54) : new Color(76, 71, 63);
            if (pressed) face = new Color(104, 56, 44);
            g.setColor(face);
            g.fillRect(bx, drawY, buttonW, buttonH);
            g.setColor(hovered || i == 0 ? ORANGE : new Color(143, 120, 99));
            g.fillRect(bx, drawY, hovered ? 9 : 6, buttonH);
            g.fillRect(bx + 6, drawY, buttonW - 12, 2);
            if (hovered) {
                g.setColor(new Color(255, 216, 155));
                g.drawRect(bx, drawY, buttonW - 1, buttonH - 1);
                g.setColor(CREAM);
                int[] arrowX = {bx + buttonW - 27, bx + buttonW - 18, bx + buttonW - 27};
                int[] arrowY = {drawY + buttonH / 2 - 6, drawY + buttonH / 2, drawY + buttonH / 2 + 6};
                g.fillPolygon(arrowX, arrowY, 3);
            }
            g.setColor(CREAM);
            g.setFont(new Font("SansSerif", Font.BOLD, Math.max(17, h / 38)));
            g.drawString(labels[i], bx + 25, drawY + buttonH * 2 / 3);
            if (!hovered) {
                g.setColor(new Color(242, 174, 102));
                g.fillRect(bx + buttonW - 27, drawY + buttonH / 2 - 4, 8, 8);
            }
        }

        int messageY = y + 4 * (buttonH + gap) + 7;
        g.setColor(new Color(255, 194, 123));
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.drawString(message, x, Math.min(h - 35, messageY));
    }

    private void drawFooter(Graphics2D g, int w, int h) {
        g.setColor(new Color(225, 213, 194));
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.drawString("MANAGE SUPPLIES  •  PROTECT THE COMMUNITY", Math.max(52, w / 13), h - 20);
        g.drawString("V 1.0.0", w - 76, h - 20);
    }

    private void drawSettings(Graphics2D g, int w, int h) {
        int boxW = Math.min(480, w - 60);
        int boxH = 230;
        int x = (w - boxW) / 2;
        int y = (h - boxH) / 2;
        g.setColor(new Color(19, 22, 23, 248));
        g.fillRect(x + 6, y + 6, boxW, boxH);
        g.setColor(ORANGE);
        g.fillRect(x, y, boxW, 6);
        g.setColor(new Color(57, 50, 47));
        g.fillRect(x, y + 6, boxW, boxH - 6);
        g.setColor(new Color(23, 27, 28, 250));
        g.fillRect(x + 4, y + 10, boxW - 8, boxH - 14);
        g.setColor(CREAM);
        g.setFont(new Font("SansSerif", Font.BOLD, 29));
        g.drawString("Settings", x + 28, y + 53);
        g.setColor(new Color(222, 211, 194));
        g.setFont(new Font("SansSerif", Font.PLAIN, 17));
        g.drawString("Audio    Not configured", x + 28, y + 100);
        g.drawString("Display  Windowed", x + 28, y + 132);
        g.setColor(new Color(255, 190, 113));
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.drawString("CLICK OR PRESS ESC TO RETURN", x + 28, y + 192);
    }
}
