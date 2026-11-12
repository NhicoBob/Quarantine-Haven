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
import java.util.Random;

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
        private int stackedTrashCount = 0;

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

        // TRUCKS
        private int supplyTruckPosX = -120;
        private boolean supplyTruckActive = false;
        private int supplyTruckTimer = 0;

        private int garbageTruckPosX = -120;
        private boolean garbageTruckActive = false;
        private int garbageTruckTimer = 0;

        // ROAD TRAFFIC CARS
        private int car1X = -70;
        private int car2X = 1220;

        // DOCTOR MOVEMENT & IDLE/ROAM SYSTEM
        private float doctorX = 150;
        private float doctorY = 320;
        private float doctorTargetX = 150;
        private float doctorTargetY = 320;
        private boolean doctorRushing = false;
        private boolean doctorInsideBuilding = false;
        private long doctorInsideTimer = 0;
        private long doctorIdleTimer = System.currentTimeMillis();
        private Random random = new Random();

        // GUARDS WITH 2-STEP TRASH MISSION (Bldg 3 -> Right of Small Building)
        private static class GuardData {
            float posX;
            float targetX;
            float minX;
            float maxX;
            int trashState = 0; // 0: Patrol, 1: Go to Bldg 3, 2: Deliver to right of small building
            float originalY = 470;
            float currentY = 470;
            long idleTimer = System.currentTimeMillis();
            Random rand = new Random();

            public GuardData(float startX, float min, float max) {
                this.posX = startX;
                this.targetX = startX;
                this.minX = min;
                this.maxX = max;
            }
        }

        private List<GuardData> guards = new ArrayList<>();
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
                new Rectangle(0, 0, 160, 720);

        private final Rectangle indoorRightRooms =
                new Rectangle(1040, 0, 160, 720);

        private final Rectangle indoorCenterBlock =
                new Rectangle(380, 40, 440, 260);

        private final Rectangle indoorMedicalDesk =
                new Rectangle(470, 320, 260, 40);

        private final Rectangle indoorTopWall =
                new Rectangle(0, 0, 1200, 35);

        private final Rectangle indoorBottomWallLeft =
                new Rectangle(0, 670, 550, 50);

        private final Rectangle indoorBottomWallRight =
                new Rectangle(630, 670, 570, 50);

        public ShelterScreen() {
            super("SHELTER_SCREEN");

            mapBgImage = loadImage("/map_bg.png");
            interiorBgImage = loadImage("/interior_map.png");
            overseerSheet = loadImage("/overseer_sprite.png");
            doctorSheet = loadImage("/doctor_sprite.png");
            guardianSheet = loadImage("/guardian_sprite.png");

            guards.add(new GuardData(420, 390, 500));
            guards.add(new GuardData(550, 520, 650));
            guards.add(new GuardData(750, 680, 900));
            guards.add(new GuardData(1000, 920, 1100));

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
                    e -> distributeRationsAndRushDoctor(1)
            );

            Input.keyboard().onKeyPressed(
                    KeyEvent.VK_2,
                    e -> treatWardsAndRushDoctor(2)
            );

            Input.keyboard().onKeyPressed(
                    KeyEvent.VK_3,
                    e -> cleanWasteAndCarryTrash()
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
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 10));
            TextRenderer.render(g, "Dr. Alex", x - 10, y + 46);
        }

        private void renderGuardian(Graphics2D g, int x, int y, String label) {
            if (guardianSheet != null) {
                g.drawImage(guardianSheet, x, y, 32, 32, null);
            } else {
                renderCharacterBadge(g, x, y, Color.BLUE, label, "GRD");
            }
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 10));
            TextRenderer.render(g, label, x - 10, y + 46);
        }

        private void renderOverseer(Graphics2D g, int x, int y) {
            if (overseerSheet != null) {
                g.drawImage(overseerSheet, x, y, 32, 32, null);
            } else {
                renderCharacterBadge(g, x, y, new Color(241, 196, 15), "OVERSEER", "OSE");
            }
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 10));
            TextRenderer.render(g, "OVERSEER", x - 10, y + 46);
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
                if (player.intersects(indoorBottomWallLeft)) return false;
                if (player.intersects(indoorBottomWallRight)) return false;
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
                if (overseerX >= 550 && overseerX <= 630 && overseerY >= 630) {
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

            // Traffic motion
            car1X += 4;
            if (car1X > 1250) {
                car1X = -70;
            }

            car2X -= 5;
            if (car2X < -70) {
                car2X = 1220;
            }

            // Update Doctor Rush & 2-Second Disappearance System
            if (doctorRushing) {
                if (doctorInsideBuilding) {
                    if (System.currentTimeMillis() - doctorInsideTimer >= 2000) {
                        doctorInsideBuilding = false;
                        doctorTargetX = 150;
                        doctorTargetY = 320;
                    }
                } else {
                    if (doctorX < doctorTargetX) doctorX += 1.0f;
                    else if (doctorX > doctorTargetX) doctorX -= 1.0f;

                    if (doctorY < doctorTargetY) doctorY += 1.0f;
                    else if (doctorY > doctorTargetY) doctorY -= 1.0f;

                    if (Math.abs(doctorX - doctorTargetX) <= 1.5f && Math.abs(doctorY - doctorTargetY) <= 1.5f) {
                        if (doctorTargetX != 150) {
                            doctorInsideBuilding = true;
                            doctorInsideTimer = System.currentTimeMillis();
                        } else {
                            doctorRushing = false;
                        }
                    }
                }
            } else {
                if (System.currentTimeMillis() - doctorIdleTimer > 4000) {
                    doctorIdleTimer = System.currentTimeMillis();
                    doctorTargetX = 130 + random.nextInt(60);
                    doctorTargetY = 300 + random.nextInt(40);
                }
                if (doctorX < doctorTargetX) doctorX += 0.5f;
                else if (doctorX > doctorTargetX) doctorX -= 0.5f;

                if (doctorY < doctorTargetY) doctorY += 0.5f;
                else if (doctorY > doctorTargetY) doctorY -= 0.5f;
            }

            // Update Guards with 2-step trash task:
            // State 1: Walk to Building 3 door (x: 770, y: 310) to get trash
            // State 2: Walk to right side of small building (x: 340, y: 480) to drop trash
            for (GuardData guard : guards) {
                if (guard.trashState == 1) {
                    if (guard.posX < 770) guard.posX += 1.2f;
                    else if (guard.posX > 770) guard.posX -= 1.2f;

                    if (guard.currentY > 310) guard.currentY -= 1.2f;
                    else if (guard.currentY < 310) guard.currentY += 1.2f;

                    if (Math.abs(guard.posX - 770) <= 2f && Math.abs(guard.currentY - 310) <= 2f) {
                        guard.trashState = 2; // Got trash, now take it to right of small building
                    }
                } else if (guard.trashState == 2) {
                    if (guard.posX < 340) guard.posX += 1.2f;
                    else if (guard.posX > 340) guard.posX -= 1.2f;

                    if (guard.currentY < 480) guard.currentY += 1.2f;
                    else if (guard.currentY > 480) guard.currentY -= 1.2f;

                    if (Math.abs(guard.posX - 340) <= 2f && Math.abs(guard.currentY - 480) <= 2f) {
                        stackedTrashCount++;
                        guard.trashState = 0; // Done, return to patrol
                    }
                } else {
                    if (System.currentTimeMillis() - guard.idleTimer > 5000) {
                        guard.idleTimer = System.currentTimeMillis();
                        guard.targetX = guard.minX + guard.rand.nextFloat() * (guard.maxX - guard.minX);
                    }

                    if (guard.posX < guard.targetX) guard.posX += 0.6f;
                    else if (guard.posX > guard.targetX) guard.posX -= 0.6f;

                    if (guard.currentY < guard.originalY) guard.currentY += 0.3f;
                    else if (guard.currentY > guard.originalY) guard.currentY -= 0.3f;
                }
            }

            long currentTime = System.currentTimeMillis();

            if (currentTime - lastTickTime >= 1000) {

                lastTickTime = currentTime;

                // Supply Truck
                if (supplyTruckActive) {
                    supplyTruckTimer++;
                    if (supplyTruckTimer < 6) {
                        supplyTruckPosX += 40;
                        if (supplyTruckPosX > 480) supplyTruckPosX = 480;
                    } else if (supplyTruckTimer > 15) {
                        supplyTruckPosX += 40;
                        if (supplyTruckPosX > 1300) {
                            supplyTruckActive = false;
                            supplyTruckTimer = 0;
                        }
                    }
                }

                // Garbage Truck
                if (garbageTruckActive) {
                    garbageTruckTimer++;
                    if (garbageTruckTimer < 6) {
                        garbageTruckPosX += 40;
                        if (garbageTruckPosX > 480) garbageTruckPosX = 480;
                    } else if (garbageTruckTimer > 15) {
                        garbageTruckPosX += 40;
                        if (garbageTruckPosX > 1300) {
                            garbageTruckActive = false;
                            garbageTruckTimer = 0;
                        }
                    }
                }

                if (secondsRemaining > 0) {
                    secondsRemaining--;
                } else {
                    triggerDawnTrucks();
                }
            }
        }

        private void triggerDawnTrucks() {
            day++;
            secondsRemaining = DAY_DURATION_SECONDS;

            // Activate Supply Truck
            supplyTruckActive = true;
            supplyTruckTimer = 0;
            supplyTruckPosX = -120;

            // Activate Garbage Truck
            garbageTruckActive = true;
            garbageTruckTimer = 0;
            garbageTruckPosX = -300;

            // Clear stacked trash and give bonus points
            if (stackedTrashCount > 0) {
                score += stackedTrashCount * 50;
                stackedTrashCount = 0;
            }

            food += 120;
            water += 120;
            medicine += 50;
            materials += 40;
            currencyPoints += 250;

            food -= 35;
            water -= 30;

            waste = Math.min(100, waste + 10);
            score += 150;

            currentEvent = "DAWN: Supply & Garbage trucks arrived at the gate!";
        }

        private void distributeRationsAndRushDoctor(int buildingNum) {
            if (food >= 20 && water >= 20) {
                food -= 20;
                water -= 20;
                score += 25;

                doctorRushing = true;
                doctorInsideBuilding = false;
                if (buildingNum == 1) {
                    doctorTargetX = 250;
                    doctorTargetY = 315;
                    currentEvent = "Overseer Order: Rations distributed. Dr. Alex rushing to Building 1!";
                } else {
                    doctorTargetX = 510;
                    doctorTargetY = 315;
                    currentEvent = "Overseer Order: Rations distributed. Dr. Alex rushing to Building 2!";
                }
            } else {
                currentEvent = "Action Failed: Low reserves.";
            }
        }

        private void treatWardsAndRushDoctor(int buildingNum) {
            if (medicine >= 15) {
                medicine -= 15;
                score += 30;

                doctorRushing = true;
                doctorInsideBuilding = false;
                if (buildingNum == 1) {
                    doctorTargetX = 250;
                    doctorTargetY = 315;
                    currentEvent = "Overseer Order: Medical treatment. Dr. Alex rushing to Building 1!";
                } else {
                    doctorTargetX = 510;
                    doctorTargetY = 315;
                    currentEvent = "Overseer Order: Medical treatment. Dr. Alex rushing to Building 2!";
                }
            } else {
                currentEvent = "Action Failed: No medicine left.";
            }
        }

        private void cleanWasteAndCarryTrash() {
            if (materials >= 10 && waste > 0) {
                materials -= 10;
                waste = Math.max(0, waste - 15);
                score += 20;

                if (!guards.isEmpty()) {
                    guards.get(0).trashState = 1; // Step 1: Go to Building 3 for trash
                }

                currentEvent = "Overseer Order: Guard heading to Building 3 to collect trash!";
            } else {
                currentEvent = "Action Failed: Requires 10 Materials or 0 Waste.";
            }
        }

        private void hireUnit(String role, int cost) {
            if (currencyPoints >= cost) {
                currencyPoints -= cost;
                guards.add(new GuardData(580, 400, 700));
                currentEvent = "Hired new " + role + " stationed at compound!";
            } else {
                currentEvent = "Hiring Failed: Insufficient points!";
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
                renderOverseer(g, overseerX, overseerY);

            } else {
                if (mapBgImage != null) {
                    g.drawImage(mapBgImage, 0, 0, screenWidth, screenHeight, null);
                } else {
                    g.setColor(new Color(30, 35, 42));
                    g.fillRect(0, 0, screenWidth, screenHeight);
                }

                drawRoadCar(g, car1X, 690, new Color(41, 128, 185));
                drawRoadCar(g, car2X, 720, new Color(231, 76, 60));

                if (supplyTruckActive) {
                    drawSupplyTruck(g, supplyTruckPosX, 695);
                }

                if (garbageTruckActive) {
                    drawGarbageTruck(g, garbageTruckPosX, 725);
                }

                // Render stacked trash bags safely at the right side of the small building (x: 340, y: 480)
                if (stackedTrashCount > 0) {
                    drawStackedTrash(g, 340, 480, stackedTrashCount);
                }

                if (!doctorInsideBuilding) {
                    renderDoctor(g, (int) doctorX, (int) doctorY);
                }

                for (int i = 0; i < guards.size(); i++) {
                    GuardData gData = guards.get(i);
                    String statusLabel = " [Patrol]";
                    if (gData.trashState == 1) statusLabel = " [GETTING TRASH]";
                    else if (gData.trashState == 2) statusLabel = " [CARRYING TRASH]";
                    String label = "Guard #" + (i + 1) + statusLabel;
                    renderGuardian(g, (int)gData.posX, (int)gData.currentY, label);
                }

                renderOverseer(g, overseerX, overseerY);
            }

            g.setColor(new Color(15, 20, 25, 220));
            g.fillRect(20, 15, screenWidth - 40, 150);

            g.setColor(new Color(46, 204, 113));
            g.drawRect(20, 15, screenWidth - 40, 150);

            g.setFont(new Font("Monospaced", Font.BOLD, 18));
            g.setColor(new Color(46, 204, 113));
            TextRenderer.render(g, "QUARANTINE HAVEN [OVERSEER SIMULATOR]", 35, 40);

            int minutes = secondsRemaining / 60;
            int seconds = secondsRemaining % 60;
            String timeStr = String.format("%02d:%02d", minutes, seconds);

            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            g.setColor(Color.WHITE);
            String topInfo = String.format("DAY: %d | NEXT TRUCK: %s | SCORE: %d | PTS: %d | POWER: %d%%", day, timeStr, score, currencyPoints, power);
            TextRenderer.render(g, topInfo, 35, 62);

            String resInfo = String.format("RESOURCES -> Food: %d | Water: %d | Medicine: %d | Materials: %d", food, water, medicine, materials);
            g.setColor(new Color(241, 196, 15));
            TextRenderer.render(g, resInfo, 35, 84);

            g.setColor(Color.LIGHT_GRAY);
            TextRenderer.render(g, "WASTE CAPACITY: " + waste + "% | TRASH PILES: " + stackedTrashCount, 35, 106);

            g.setColor(new Color(231, 76, 60));
            g.fillRect(175, 95, waste * 2, 12);
            g.setColor(Color.DARK_GRAY);
            g.drawRect(175, 95, 200, 12);

            g.setColor(Color.CYAN);
            TextRenderer.render(g, "EVENT LOG: " + currentEvent, 35, 128);

            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.setColor(new Color(200, 200, 200));
            TextRenderer.render(g, "CONTROLS: [WASD] Move | [E] Enter/Exit Door | [ESC] Exit Room | [1-3, H] Actions", 35, 148);
        }

        private void renderCharacterBadge(Graphics2D g, int x, int y, Color color, String label, String tag) {
            g.setColor(new Color(15, 20, 25, 220));
            g.fillOval(x - 2, y - 2, 34, 34);
            g.setColor(color);
            g.fillOval(x, y, 30, 30);
            g.setColor(Color.BLACK);
            g.drawRect(x, y, 30, 30);
            g.setFont(new Font("Monospaced", Font.BOLD, 10));
            g.setColor(Color.BLACK);
            TextRenderer.render(g, tag, x + 5, y + 19);
        }

        private void drawRoadCar(Graphics2D g, int x, int y, Color carColor) {
            g.setColor(carColor);
            g.fillRect(x, y, 65, 24);
            g.setColor(Color.BLACK);
            g.drawRect(x, y, 65, 24);
            g.setColor(new Color(200, 220, 240));
            g.fillRect(x + 15, y + 3, 35, 8);
            g.setColor(Color.DARK_GRAY);
            g.fillOval(x + 10, y + 20, 10, 10);
            g.fillOval(x + 45, y + 20, 10, 10);
        }

        private void drawSupplyTruck(Graphics2D g, int x, int y) {
            g.setColor(new Color(190, 200, 210));
            g.fillRect(x, y, 110, 40);
            g.setColor(Color.BLACK);
            g.drawRect(x, y, 110, 40);
            TextRenderer.render(g, "Supply Truck", x + 12, y + 25);
            g.setColor(new Color(41, 128, 185));
            g.fillRect(x + 110, y + 10, 30, 30);
            g.setColor(Color.DARK_GRAY);
            g.fillOval(x + 20, y + 35, 14, 14);
            g.fillOval(x + 95, y + 35, 14, 14);
        }

        private void drawGarbageTruck(Graphics2D g, int x, int y) {
            g.setColor(new Color(40, 140, 90));
            g.fillRect(x, y, 110, 40);
            g.setColor(Color.BLACK);
            g.drawRect(x, y, 110, 40);
            g.setColor(Color.WHITE);
            TextRenderer.render(g, "Garbage Truck", x + 8, y + 25);
            g.setColor(new Color(50, 50, 50));
            g.fillRect(x + 110, y + 10, 30, 30);
            g.setColor(Color.DARK_GRAY);
            g.fillOval(x + 20, y + 35, 14, 14);
            g.fillOval(x + 95, y + 35, 14, 14);
        }

        private void drawStackedTrash(Graphics2D g, int x, int y, int count) {
            g.setColor(new Color(100, 100, 100));
            for (int i = 0; i < Math.min(count, 8); i++) {
                int offsetX = (i % 3) * 12;
                int offsetY = (i / 3) * 10;
                g.fillOval(x + offsetX, y - offsetY, 14, 12);
            }
        }
    }
}