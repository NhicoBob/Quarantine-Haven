package com.example.game;

import com.example.game.model.StaffUnit;
import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.graphics.TextRenderer;
import de.gurkenlabs.litiengine.gui.screens.GameScreen;
import de.gurkenlabs.litiengine.input.Input;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class Program {

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false");

        Game.info().setName("Quarantine Haven");
        Game.info().setVersion("1.0.0");
        Game.init(args);

        Game.screens().add(new ShelterScreen());
        Game.start();
    }

    public static class ShelterScreen extends GameScreen {

        private static final boolean DEBUG_COLLISIONS = false;

        private int day = 1;
        private int score = 1850;
        private int currencyPoints = 500;
        private int power = 98;
        private int waste = 22;

        private int food = 310;
        private int water = 290;
        private int medicine = 115;
        private int materials = 60;

        private static final int DAY_DURATION_SECONDS = 300;
        private int secondsRemaining = DAY_DURATION_SECONDS;
        private long lastTickTime = System.currentTimeMillis();

        private String currentEvent =
                "Overseer active. Walk up to building doors and press [E].";

        private int overseerX = 580;
        private int overseerY = 420;

        private static final int PLAYER_WIDTH = 32;
        private static final int PLAYER_HEIGHT = 32;
        private static final int MOVE_SPEED = 4;

        private String insideBuilding = null;

        private BufferedImage mapBgImage;
        private BufferedImage interiorBgImage;
        private BufferedImage overseerSheet;
        private BufferedImage doctorSheet;
        private BufferedImage guardianSheet;

        private int truckPosX = -250;
        private boolean truckActive = false;
        private int truckTimer = 0;

        private int guardPosX = 520;
        private boolean guardMovingRight = true;

        private final int GUARD_MIN_X = 450;
        private final int GUARD_MAX_X = 650;

        private List<StaffUnit> staffList = new ArrayList<>();

        // OUTDOOR MAP BOUNDARIES & COLLISION RECTS
        private final Rectangle building1 =
                new Rectangle(145, 60, 240, 250);

        private final Rectangle building2 =
                new Rectangle(405, 60, 240, 250);

        private final Rectangle building3 =
                new Rectangle(665, 60, 240, 250);

        private final Rectangle tents =
                new Rectangle(150, 465, 250, 75);

        private final Rectangle bottomWallLeft =
                new Rectangle(50, 530, 420, 60);

        private final Rectangle bottomWallRight =
                new Rectangle(740, 530, 420, 60);

        private final Rectangle leftWall =
                new Rectangle(50, 60, 95, 470);

        private final Rectangle rightFence =
                new Rectangle(1140, 0, 70, 720);

        // INDOOR ROOM WALL COLLISION RECTS
        private final Rectangle indoorLeftRooms =
                new Rectangle(0, 0, 115, 720);

        private final Rectangle indoorRightRooms =
                new Rectangle(1085, 0, 115, 720);

        private final Rectangle indoorCenterBlock =
                new Rectangle(475, 10, 250, 315);

        private final Rectangle indoorMedicalDesk =
                new Rectangle(470, 340, 260, 30);

        private final Rectangle indoorTopWall =
                new Rectangle(0, 0, 1200, 35);

        private final Rectangle indoorBottomWall =
                new Rectangle(0, 675, 1200, 45);

        public ShelterScreen() {
            super("SHELTER_SCREEN");

            mapBgImage = loadImage("/map_bg.png");
            interiorBgImage = loadImage("/interior_map.png");
            overseerSheet = loadImage("/overseer_sprite.png");
            doctorSheet = loadImage("/doctor_sprite.png");
            guardianSheet = loadImage("/guardian_sprite.png");

            staffList.add(
                    new StaffUnit("Dr. Alex", "Doctor", 150, 280)
            );

            staffList.add(
                    new StaffUnit("Engineer Kael", "Engineer", 430, 280)
            );

            Input.keyboard().onKeyPressed(
                    KeyEvent.VK_W,
                    e -> moveOverseer(0, -MOVE_SPEED)
            );

            Input.keyboard().onKeyPressed(
                    KeyEvent.VK_S,
                    e -> moveOverseer(0, MOVE_SPEED)
            );

            Input.keyboard().onKeyPressed(
                    KeyEvent.VK_A,
                    e -> moveOverseer(-MOVE_SPEED, 0)
            );

            Input.keyboard().onKeyPressed(
                    KeyEvent.VK_D,
                    e -> moveOverseer(MOVE_SPEED, 0)
            );

            Input.keyboard().onKeyPressed(
                    KeyEvent.VK_E,
                    e -> interactWithLocation()
            );

            Input.keyboard().onKeyPressed(
                    KeyEvent.VK_ESCAPE,
                    e -> exitBuilding()
            );

            Input.keyboard().onKeyPressed(
                    KeyEvent.VK_1,
                    e -> distributeRations()
            );

            Input.keyboard().onKeyPressed(
                    KeyEvent.VK_2,
                    e -> treatWards()
            );

            Input.keyboard().onKeyPressed(
                    KeyEvent.VK_3,
                    e -> cleanWaste()
            );

            Input.keyboard().onKeyPressed(
                    KeyEvent.VK_H,
                    e -> hireUnit("Guard", 150)
            );

            Game.loop().attach(this::updateGameLoop);
        }

        private BufferedImage loadImage(String path) {
            try (InputStream is = getClass().getResourceAsStream(path)) {

                if (is != null) {
                    BufferedImage image = ImageIO.read(is);

                    if (image != null) {
                        System.out.println("Loaded asset: " + path);
                        return image;
                    }
                }

                System.out.println("Could not load asset: " + path);

            } catch (Exception e) {
                System.out.println(
                        "Error loading " + path + ": " + e.getMessage()
                );
            }

            return null;
        }

        private void renderDoctor(Graphics2D g, int x, int y) {
            if (doctorSheet != null) {
                g.drawImage(doctorSheet, x, y, 32, 32, null);
            } else {
                renderCharacterBadge(g, x, y, Color.GREEN, "Dr. Alex (Doctor)", "DOC");
            }
        }

        private void renderGuardian(Graphics2D g, int x, int y) {
            if (guardianSheet != null) {
                g.drawImage(guardianSheet, x, y, 32, 32, null);
            } else {
                renderCharacterBadge(g, x, y, Color.BLUE, "Guard [Patrol]", "GRD");
            }
        }

        private void renderOverseer(Graphics2D g, int x, int y) {
            if (overseerSheet != null) {
                g.drawImage(overseerSheet, x, y, 32, 32, null);
            } else {
                renderCharacterBadge(g, x, y, new Color(241, 196, 15), "OVERSEER", "OSE");
            }
        }

        private boolean canMoveTo(int x, int y) {

            Rectangle player = new Rectangle(
                    x,
                    y,
                    PLAYER_WIDTH,
                    PLAYER_HEIGHT
            );

            if (insideBuilding != null) {
                if (player.intersects(indoorLeftRooms)) return false;
                if (player.intersects(indoorRightRooms)) return false;
                if (player.intersects(indoorCenterBlock)) return false;
                if (player.intersects(indoorMedicalDesk)) return false;
                if (player.intersects(indoorTopWall)) return false;
                if (player.intersects(indoorBottomWall)) return false;
                return true;
            }

            if (y < 60 || y + PLAYER_HEIGHT > 560) {
                return false;
            }

            if (player.intersects(building1)) return false;
            if (player.intersects(building2)) return false;
            if (player.intersects(building3)) return false;
            if (player.intersects(tents)) return false;

            if (player.intersects(bottomWallLeft)) return false;
            if (player.intersects(bottomWallRight)) return false;
            if (player.intersects(leftWall)) return false;
            if (player.intersects(rightFence)) return false;

            return true;
        }

        private void moveOverseer(int dx, int dy) {

            int nextX = overseerX + dx;
            int nextY = overseerY + dy;

            if (canMoveTo(nextX, overseerY)) {
                overseerX = nextX;
            }

            if (canMoveTo(overseerX, nextY)) {
                overseerY = nextY;
            }
        }

        private void interactWithLocation() {

            if (insideBuilding != null) {
                // Exit trigger zone at the bottom door of the interior map
                if (overseerX >= 550 && overseerX <= 630 && overseerY >= 610) {
                    exitBuilding();
                } else {
                    currentEvent = "Walk down to the EXIT door and press [E] to leave the room.";
                }
                return;
            }

            if (overseerX >= 235 &&
                    overseerX <= 285 &&
                    overseerY >= 295 &&
                    overseerY <= 340) {

                enterBuilding("PATIENT WARD 1");

            } else if (overseerX >= 495 &&
                    overseerX <= 545 &&
                    overseerY >= 295 &&
                    overseerY <= 340) {

                enterBuilding("PATIENT WARD 2");

            } else if (overseerX >= 755 &&
                    overseerX <= 805 &&
                    overseerY >= 295 &&
                    overseerY <= 340) {

                enterBuilding("RESOURCES / STORAGE");

            } else {

                currentEvent =
                        "Stand directly in front of a building door and press [E].";
            }
        }

        private void enterBuilding(String buildingName) {
            insideBuilding = buildingName;

            // Spawn safely away from the bottom exit door inside the room
            overseerX = 584;
            overseerY = 480;

            currentEvent = "Entered " + buildingName + ". Explore freely!";
        }

        private void exitBuilding() {
            if (insideBuilding != null) {
                insideBuilding = null;

                overseerX = 580;
                overseerY = 420;

                currentEvent = "Returned to compound grounds.";
            }
        }

        private void updateGameLoop() {

            long currentTime = System.currentTimeMillis();

            if (currentTime - lastTickTime >= 1000) {

                lastTickTime = currentTime;

                if (guardMovingRight) {

                    guardPosX += 4;

                    if (guardPosX >= GUARD_MAX_X) {
                        guardMovingRight = false;
                    }

                } else {

                    guardPosX -= 4;

                    if (guardPosX <= GUARD_MIN_X) {
                        guardMovingRight = true;
                    }
                }

                if (truckActive) {

                    truckTimer++;

                    if (truckTimer < 6) {

                        truckPosX += 40;

                        if (truckPosX > 480) {
                            truckPosX = 480;
                        }

                    } else if (truckTimer > 15) {

                        truckPosX += 40;

                        if (truckPosX > 1400) {

                            truckActive = false;
                            truckTimer = 0;
                        }
                    }
                }

                if (secondsRemaining > 0) {

                    secondsRemaining--;

                } else {

                    triggerDawnSupplyTruck();
                }
            }
        }

        private void triggerDawnSupplyTruck() {

            day++;
            secondsRemaining = DAY_DURATION_SECONDS;

            truckActive = true;
            truckTimer = 0;
            truckPosX = -250;

            food += 120;
            water += 120;
            medicine += 50;
            materials += 40;
            currencyPoints += 250;

            food -= 35;
            water -= 30;

            waste = Math.min(100, waste + 10);

            score += 150;

            currentEvent =
                    "DAWN: Supply truck delivered supplies at the gate!";
        }

        private void distributeRations() {

            if (food >= 20 && water >= 20) {

                food -= 20;
                water -= 20;
                score += 25;

                currentEvent =
                        "Overseer Order: Rations distributed.";

            } else {

                currentEvent = "Action Failed: Low reserves.";
            }
        }

        private void treatWards() {

            if (medicine >= 15) {

                medicine -= 15;
                score += 30;

                currentEvent =
                        "Overseer Order: Medical treatment dispatched.";

            } else {

                currentEvent =
                        "Action Failed: No medicine left.";
            }
        }

        private void cleanWaste() {

            if (materials >= 10 && waste > 0) {

                materials -= 10;
                waste = Math.max(0, waste - 15);
                score += 20;

                currentEvent =
                        "Overseer Order: Sanitation team cleared waste.";

            } else {

                currentEvent =
                        "Action Failed: Requires 10 Materials.";
            }
        }

        private void hireUnit(String role, int cost) {

            if (currencyPoints >= cost) {

                currencyPoints -= cost;

                staffList.add(
                        new StaffUnit(
                                role + " #" + (staffList.size() + 1),
                                role,
                                580,
                                480
                        )
                );

                currentEvent =
                        "Hired new " + role + " stationed at compound!";

            } else {

                currentEvent =
                        "Hiring Failed: Insufficient points!";
            }
        }

        @Override
        public void render(Graphics2D g) {

            super.render(g);

            int screenWidth = Game.window().getWidth();
            int screenHeight = Game.window().getHeight();

            if (insideBuilding != null) {

                if (interiorBgImage != null) {
                    g.drawImage(interiorBgImage, 0, 0, screenWidth, screenHeight, null);
                } else {
                    g.setColor(new Color(35, 42, 50));
                    g.fillRect(0, 0, screenWidth, screenHeight);
                }

                renderDoctor(g, 430, 390);

                g.setColor(Color.WHITE);
                g.setFont(new Font("SansSerif", Font.BOLD, 10));

                TextRenderer.render(
                        g,
                        "Dr. Alex - Doctor",
                        410,
                        435
                );

                renderOverseer(g, overseerX, overseerY);

            } else {

                if (mapBgImage != null) {

                    g.drawImage(
                            mapBgImage,
                            0,
                            0,
                            screenWidth,
                            screenHeight,
                            null
                    );

                } else {

                    g.setColor(new Color(30, 35, 42));
                    g.fillRect(0, 0, screenWidth, screenHeight);
                }

                if (truckActive) {
                    drawSupplyTruck(g, truckPosX, 490);
                }

                renderDoctor(g, 150, 320);

                g.setColor(Color.WHITE);
                g.setFont(new Font("SansSerif", Font.BOLD, 10));

                TextRenderer.render(
                        g,
                        "Dr. Alex",
                        140,
                        365
                );

                renderGuardian(g, guardPosX, 470);

                renderOverseer(g, overseerX, overseerY);
            }

            g.setColor(new Color(15, 20, 25, 220));
            g.fillRect(20, 15, screenWidth - 40, 150);

            g.setColor(new Color(46, 204, 113));
            g.drawRect(20, 15, screenWidth - 40, 150);

            g.setFont(new Font("Monospaced", Font.BOLD, 18));
            g.setColor(new Color(46, 204, 113));

            TextRenderer.render(
                    g,
                    "QUARANTINE HAVEN [OVERSEER SIMULATOR]",
                    35,
                    40
            );

            int minutes = secondsRemaining / 60;
            int seconds = secondsRemaining % 60;

            String timeStr = String.format(
                    "%02d:%02d",
                    minutes,
                    seconds
            );

            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            g.setColor(Color.WHITE);

            String topInfo = String.format(
                    "DAY: %d | NEXT TRUCK: %s | SCORE: %d | PTS: %d | POWER: %d%%",
                    day,
                    timeStr,
                    score,
                    currencyPoints,
                    power
            );

            TextRenderer.render(g, topInfo, 35, 62);

            String resInfo = String.format(
                    "RESOURCES -> Food: %d | Water: %d | Medicine: %d | Materials: %d",
                    food,
                    water,
                    medicine,
                    materials
            );

            g.setColor(new Color(241, 196, 15));

            TextRenderer.render(g, resInfo, 35, 84);

            g.setColor(Color.LIGHT_GRAY);

            TextRenderer.render(
                    g,
                    "WASTE CAPACITY: " + waste + "%",
                    35,
                    106
            );

            g.setColor(new Color(231, 76, 60));
            g.fillRect(175, 95, waste * 2, 12);

            g.setColor(Color.DARK_GRAY);
            g.drawRect(175, 95, 200, 12);

            g.setColor(Color.CYAN);

            TextRenderer.render(
                    g,
                    "EVENT LOG: " + currentEvent,
                    35,
                    128
            );

            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.setColor(new Color(200, 200, 200));

            TextRenderer.render(
                    g,
                    "CONTROLS: [WASD] Move | [E] Enter/Exit Door | [ESC] Exit Room | [1-3, H] Actions",
                    35,
                    148
            );
        }

        private void renderCharacterBadge(
                Graphics2D g,
                int x,
                int y,
                Color color,
                String label,
                String tag
        ) {

            g.setColor(new Color(15, 20, 25, 220));
            g.fillOval(x - 2, y - 2, 34, 34);

            g.setColor(color);
            g.fillOval(x, y, 30, 30);

            g.setColor(Color.BLACK);
            g.drawRect(x, y, 30, 30);

            g.setFont(new Font("Monospaced", Font.BOLD, 10));
            g.setColor(Color.BLACK);

            TextRenderer.render(
                    g,
                    tag,
                    x + 5,
                    y + 19
            );

            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 10));

            TextRenderer.render(
                    g,
                    label,
                    x - 10,
                    y + 46
            );
        }

        private void drawSupplyTruck(Graphics2D g, int x, int y) {

            g.setColor(new Color(190, 200, 210));
            g.fillRect(x, y, 110, 40);

            g.setColor(Color.BLACK);
            g.drawRect(x, y, 110, 40);

            TextRenderer.render(
                    g,
                    "Supply Truck",
                    x + 12,
                    y + 25
            );

            g.setColor(new Color(41, 128, 185));
            g.fillRect(x + 110, y + 10, 30, 30);

            g.setColor(Color.DARK_GRAY);
            g.fillOval(x + 20, y + 35, 14, 14);
            g.fillOval(x + 95, y + 35, 14, 14);
        }
    }
}