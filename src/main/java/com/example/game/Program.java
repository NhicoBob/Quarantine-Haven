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
        Game.info().setVersion("1.6.0");
        Game.init(args);

        Game.screens().add(new ShelterScreen());
        Game.start();
    }

    public static class ShelterScreen extends GameScreen {

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
                "Visible rooms added! Patients now walk smoothly through doors into their rooms when [5] is pressed.";

        private int overseerX = 580;
        private int overseerY = 420;

        private static final int PLAYER_WIDTH = 32;
        private static final int PLAYER_HEIGHT = 32;
        private static final int MOVE_SPEED = 4;

        // BUILDING & FLOOR MANAGEMENT
        private String insideBuilding = null;
        private int currentFloor = 1;

        private BufferedImage mapBgImage;
        private BufferedImage interiorBgImage;
        private BufferedImage overseerSheet;
        private BufferedImage doctorSheet;
        private BufferedImage guardianSheet;
        private BufferedImage femalePatientSheet;
        private BufferedImage malePatientSheet;

        // FAST TRUCKS
        private int supplyTruckPosX = -150;
        private boolean supplyTruckActive = false;

        private int garbageTruckPosX = -150;
        private boolean garbageTruckActive = false;

        // HIRE VEHICLE
        private float hireVehiclePosX = -150;
        private boolean hireVehicleActive = false;
        private String hiredRoleName = "";

        // ROAD TRAFFIC
        private float car1X = -80;
        private float car2X = 1250;

        // DOCTORS
        private static class DoctorData {
            float posX, currentY;
            float targetX, targetY;
            boolean isInside = false;
            int floor = 1;
            int state = 0;
            PatientData targetPatient = null;
            long roamTimer = System.currentTimeMillis();
            Random rand = new Random();

            public DoctorData(float startX, float startY, boolean isInside, int floor) {
                this.posX = startX;
                this.currentY = startY;
                this.targetX = startX;
                this.targetY = startY;
                this.isInside = isInside;
                this.floor = floor;
            }
        }

        private List<DoctorData> doctors = new ArrayList<>();

        // PATIENTS WITH VISIBLE ROOM ASSIGNMENTS & SMOOTH WALKING
        private static class PatientData {
            int id;
            float posX, currentY;
            float targetX, targetY;
            float roomX, roomY;
            boolean isInside = false;
            int floor = 1;
            boolean orderedToRoom = false;
            boolean isFemale;
            String need = "HEALTHY";
            float hp = 100.0f;
            long roamTimer = System.currentTimeMillis();
            long needTimer = System.currentTimeMillis();
            Random rand = new Random();

            public PatientData(int id, float startX, float startY, boolean isInside, int floor) {
                this.id = id;
                this.posX = startX;
                this.currentY = startY;
                this.isInside = isInside;
                this.floor = floor;
                this.isFemale = (id % 2 == 0);

                // Assign specific visible room coordinates inside the rooms layout
                int roomSlot = (id - 1) % 4;
                if (roomSlot == 0) {
                    this.roomX = 80; this.roomY = 150 + ((id * 30) % 300);
                } else if (roomSlot == 1) {
                    this.roomX = 1110; this.roomY = 150 + ((id * 30) % 300);
                } else if (roomSlot == 2) {
                    this.roomX = 420; this.roomY = 100 + ((id * 20) % 120);
                } else {
                    this.roomX = 750; this.roomY = 100 + ((id * 20) % 120);
                }

                this.targetX = startX;
                this.targetY = startY;
            }
        }

        private List<PatientData> patients = new ArrayList<>();

        // GUARDS
        private static class GuardData {
            float posX, currentY;
            float targetX, targetY;
            int taskState = 0;
            long roamTimer = System.currentTimeMillis();
            Random rand = new Random();

            public GuardData(float startX, float startY) {
                this.posX = startX;
                this.currentY = startY;
                this.targetX = startX;
                this.targetY = startY;
            }
        }

        private List<GuardData> guards = new ArrayList<>();
        private List<StaffUnit> staffList = new ArrayList<>();

        // COLLISION RECTANGLES
        private final Rectangle building1 = new Rectangle(145, 60, 240, 250);
        private final Rectangle building2 = new Rectangle(405, 60, 240, 250);
        private final Rectangle building3 = new Rectangle(665, 60, 240, 250);
        private final Rectangle tents = new Rectangle(150, 465, 250, 75);
        private final Rectangle bottomWallLeft = new Rectangle(50, 530, 420, 60);
        private final Rectangle bottomWallRight = new Rectangle(740, 530, 420, 60);
        private final Rectangle leftWall = new Rectangle(50, 60, 95, 470);
        private final Rectangle rightFence = new Rectangle(1140, 0, 70, 720);

        private final Rectangle indoorLeftRooms = new Rectangle(0, 0, 160, 720);
        private final Rectangle indoorRightRooms = new Rectangle(1040, 0, 160, 720);
        private final Rectangle indoorCenterBlock = new Rectangle(380, 40, 440, 260);
        private final Rectangle indoorMedicalDesk = new Rectangle(470, 320, 260, 40);
        private final Rectangle indoorTopWall = new Rectangle(0, 0, 1200, 35);
        private final Rectangle indoorBottomWallLeft = new Rectangle(0, 670, 550, 50);
        private final Rectangle indoorBottomWallRight = new Rectangle(630, 670, 570, 50);
        private final Rectangle indoorStairs = new Rectangle(550, 120, 70, 50);

        private final float door1X = 260;
        private final float door1Y = 310;
        private final float door2X = 520;
        private final float door2Y = 310;

        public ShelterScreen() {
            super("SHELTER_SCREEN");

            mapBgImage = loadImage("/map_bg.png");
            interiorBgImage = loadImage("/interior_map.png");
            overseerSheet = loadImage("/overseer_sprite.png");
            doctorSheet = loadImage("/doctor_sprite.png");
            guardianSheet = loadImage("/guardian_sprite.png");
            femalePatientSheet = loadImage("/female_patient.png");
            malePatientSheet = loadImage("/male_patient.png");

            for (int i = 1; i <= 10; i++) {
                boolean inside = (i % 2 == 0);
                int floor = (i % 3) == 0 ? 3 : ((i % 2 == 0) ? 2 : 1);
                float startX = inside ? (200 + (i * 70) % 700) : (180 + (i * 90) % 850);
                float startY = inside ? (320 + (i * 20) % 150) : (350 + (i * 15) % 120);
                patients.add(new PatientData(i, startX, startY, inside, floor));
            }

            doctors.add(new DoctorData(250, 380, false, 1));
            doctors.add(new DoctorData(500, 150, true, 2));

            guards.add(new GuardData(300, 450));
            guards.add(new GuardData(600, 450));

            Input.keyboard().onKeyPressed(KeyEvent.VK_W, e -> moveOverseer(0, -MOVE_SPEED));
            Input.keyboard().onKeyPressed(KeyEvent.VK_S, e -> moveOverseer(0, MOVE_SPEED));
            Input.keyboard().onKeyPressed(KeyEvent.VK_A, e -> moveOverseer(-MOVE_SPEED, 0));
            Input.keyboard().onKeyPressed(KeyEvent.VK_D, e -> moveOverseer(MOVE_SPEED, 0));
            Input.keyboard().onKeyPressed(KeyEvent.VK_E, e -> interactWithLocation());
            Input.keyboard().onKeyPressed(KeyEvent.VK_ESCAPE, e -> exitBuilding());
            Input.keyboard().onKeyPressed(KeyEvent.VK_1, e -> assignTaskToNearestStaff(1));
            Input.keyboard().onKeyPressed(KeyEvent.VK_2, e -> assignTaskToNearestStaff(2));
            Input.keyboard().onKeyPressed(KeyEvent.VK_3, e -> assignTaskToNearestStaff(3));
            Input.keyboard().onKeyPressed(KeyEvent.VK_4, e -> dispatchDoctorToNeedyPatient());
            Input.keyboard().onKeyPressed(KeyEvent.VK_5, e -> orderPatientsBackToRooms());
            Input.keyboard().onKeyPressed(KeyEvent.VK_H, e -> hireStaffMember("Doctor", 150));

            Game.loop().attach(this::updateGameLoop);
        }

        private BufferedImage loadImage(String path) {
            try (InputStream is = getClass().getResourceAsStream(path)) {
                if (is != null) {
                    BufferedImage image = ImageIO.read(is);
                    if (image != null) return image;
                }
            } catch (Exception ignored) {}
            return null;
        }

        private boolean canMoveTo(int x, int y) {
            Rectangle player = new Rectangle(x, y, PLAYER_WIDTH, PLAYER_HEIGHT);

            if (insideBuilding != null) {
                if (player.intersects(indoorLeftRooms) || player.intersects(indoorRightRooms) ||
                        player.intersects(indoorCenterBlock) || player.intersects(indoorMedicalDesk) ||
                        player.intersects(indoorTopWall) || player.intersects(indoorBottomWallLeft) ||
                        player.intersects(indoorBottomWallRight)) {
                    return false;
                }
                return true;
            }

            if (y < 60 || y + PLAYER_HEIGHT > 560) return false;
            if (player.intersects(building1) || player.intersects(building2) ||
                    player.intersects(building3) || player.intersects(tents) ||
                    player.intersects(bottomWallLeft) || player.intersects(bottomWallRight) ||
                    player.intersects(leftWall) || player.intersects(rightFence)) {
                return false;
            }

            return true;
        }

        private void moveOverseer(int dx, int dy) {
            int nextX = overseerX + dx;
            int nextY = overseerY + dy;
            if (canMoveTo(nextX, overseerY)) overseerX = nextX;
            if (canMoveTo(overseerX, nextY)) overseerY = nextY;
        }

        private void interactWithLocation() {
            if (insideBuilding != null) {
                Rectangle playerRect = new Rectangle(overseerX, overseerY, PLAYER_WIDTH, PLAYER_HEIGHT);
                if (playerRect.intersects(indoorStairs)) {
                    currentFloor++;
                    if (currentFloor > 3) currentFloor = 1;
                    currentEvent = "Climbed stairs in " + insideBuilding + " -> Now on Floor " + currentFloor + "!";
                    return;
                }

                if (overseerX >= 550 && overseerX <= 630 && overseerY >= 630) {
                    exitBuilding();
                } else {
                    currentEvent = "Walk to cyan stairs [550, 120] to change floors, or walk down to exit door.";
                }
                return;
            }

            if (overseerX >= 235 && overseerX <= 285 && overseerY >= 295 && overseerY <= 340) {
                enterBuilding("PATIENT WARD 1");
            } else if (overseerX >= 495 && overseerX <= 545 && overseerY >= 295 && overseerY <= 340) {
                enterBuilding("PATIENT WARD 2");
            } else {
                currentEvent = "Stand directly in front of building 1 or 2 door and press [E].";
            }
        }

        private void enterBuilding(String buildingName) {
            insideBuilding = buildingName;
            currentFloor = 1;
            overseerX = 584;
            overseerY = 620; // Spawn near interior entrance door
            currentEvent = "Entered " + buildingName + " [Floor 1]. Walk to cyan stairs to climb floors!";
        }

        private void exitBuilding() {
            if (insideBuilding != null) {
                insideBuilding = null;
                overseerX = 580;
                overseerY = 420;
                currentEvent = "Returned to compound grounds.";
            }
        }

        private void assignTaskToNearestStaff(int taskType) {
            if (taskType == 1) {
                if (food >= 20 && water >= 20) {
                    food -= 20;
                    water -= 20;
                    score += 25;
                    currentEvent = "Rations distributed!";
                } else {
                    currentEvent = "Action Failed: Low reserves.";
                }
            } else if (taskType == 2) {
                if (medicine >= 15) {
                    medicine -= 15;
                    score += 30;
                    currentEvent = "Medical treatments applied!";
                } else {
                    currentEvent = "Action Failed: No medicine left.";
                }
            } else if (taskType == 3) {
                if (materials >= 10 && waste > 0) {
                    materials -= 10;
                    waste = Math.max(0, waste - 15);
                    score += 20;

                    GuardData nearestGuard = null;
                    double minDst = Double.MAX_VALUE;
                    for (GuardData g : guards) {
                        double dst = Math.hypot(g.posX - overseerX, g.currentY - overseerY);
                        if (dst < minDst) {
                            minDst = dst;
                            nearestGuard = g;
                        }
                    }
                    if (nearestGuard != null) nearestGuard.taskState = 2;
                    currentEvent = "Guard dispatched for trash collection!";
                } else {
                    currentEvent = "Action Failed: Requires 10 Materials or 0 Waste.";
                }
            }
        }

        private void dispatchDoctorToNeedyPatient() {
            PatientData needyPatient = null;
            for (PatientData p : patients) {
                if (!p.need.equals("HEALTHY")) {
                    needyPatient = p;
                    break;
                }
            }

            if (needyPatient == null) {
                currentEvent = "All patients are currently healthy!";
                return;
            }

            DoctorData nearestDoc = null;
            double minDst = Double.MAX_VALUE;
            for (DoctorData d : doctors) {
                if (d.state == 0) {
                    double dst = Math.hypot(d.posX - needyPatient.posX, d.currentY - needyPatient.currentY);
                    if (dst < minDst) {
                        minDst = dst;
                        nearestDoc = d;
                    }
                }
            }

            if (nearestDoc != null) {
                nearestDoc.state = 1;
                nearestDoc.targetPatient = needyPatient;
                nearestDoc.isInside = needyPatient.isInside;
                nearestDoc.floor = needyPatient.floor;
                nearestDoc.targetX = needyPatient.posX;
                nearestDoc.targetY = needyPatient.currentY;
                currentEvent = "Doctor dispatched to patient!";
            } else {
                currentEvent = "No free doctors available.";
            }
        }

        private void orderPatientsBackToRooms() {
            for (PatientData p : patients) {
                p.orderedToRoom = true;
                // If they are outside, set target to building entrance door first so they walk in smoothly!
                if (!p.isInside) {
                    p.targetX = 584;
                    p.targetY = 620;
                } else {
                    p.targetX = p.roomX;
                    p.targetY = p.roomY;
                }
            }
            currentEvent = "Order [5]: Patients walking back to their visible rooms!";
        }

        private void hireStaffMember(String role, int cost) {
            if (currencyPoints >= cost) {
                currencyPoints -= cost;
                hiredRoleName = role;
                hireVehicleActive = true;
                hireVehiclePosX = -150;
                currentEvent = "Hiring initiated! Recruitment vehicle arriving...";
            } else {
                currentEvent = "Hiring Failed: Insufficient points!";
            }
        }

        private boolean indoorCollides(float x, float y) {
            Rectangle rect = new Rectangle((int)x, (int)y, 32, 32);
            return rect.intersects(indoorLeftRooms) || rect.intersects(indoorRightRooms) ||
                    rect.intersects(indoorCenterBlock) || rect.intersects(indoorMedicalDesk) ||
                    rect.intersects(indoorTopWall) || rect.intersects(indoorBottomWallLeft) ||
                    rect.intersects(indoorBottomWallRight);
        }

        private boolean outdoorCollides(float x, float y) {
            Rectangle rect = new Rectangle((int)x, (int)y, 32, 32);
            return rect.intersects(building1) || rect.intersects(building2) ||
                    rect.intersects(building3) || rect.intersects(tents) ||
                    rect.intersects(bottomWallLeft) || rect.intersects(bottomWallRight) ||
                    rect.intersects(leftWall) || rect.intersects(rightFence) || y < 60 || y + 32 > 560;
        }

        private void updateGameLoop() {
            if (supplyTruckActive) {
                supplyTruckPosX += 8;
                if (supplyTruckPosX > 1300) supplyTruckActive = false;
            }
            if (garbageTruckActive) {
                garbageTruckPosX += 8;
                if (garbageTruckPosX > 1300) garbageTruckActive = false;
            }
            if (hireVehicleActive) {
                hireVehiclePosX += 8;
                if (hireVehiclePosX >= 550) {
                    if (hiredRoleName.equals("Doctor")) {
                        doctors.add(new DoctorData(580, 480, true, 1));
                    } else {
                        guards.add(new GuardData(580, 480));
                    }
                    currentEvent = "New " + hiredRoleName + " arrived and entered facility through door!";
                    hiredRoleName = "";
                }
                if (hireVehiclePosX > 1300) hireVehicleActive = false;
            }

            car1X += 2.5f;
            if (car1X > 1250) car1X = -100;
            car2X -= 2.5f;
            if (car2X < -100) car2X = 1250;

            List<PatientData> deadPatients = new ArrayList<>();
            for (PatientData p : patients) {
                if (p.need.equals("HEALTHY")) {
                    int baseThreshold = 25000;
                    int sicknessThreshold = Math.max(6000, baseThreshold - ((day - 1) * 4000));
                    if (System.currentTimeMillis() - p.needTimer > sicknessThreshold + p.rand.nextInt(8000)) {
                        p.needTimer = System.currentTimeMillis();
                        String[] needsList = {"HUNGRY", "THIRSTY", "SICK"};
                        p.need = needsList[p.rand.nextInt(needsList.length)];
                    }
                } else {
                    p.hp -= 0.05f;
                    if (p.hp <= 0) {
                        deadPatients.add(p);
                        score -= 100;
                    }
                }

                if (p.orderedToRoom) {
                    if (!p.isInside) {
                        // Walk toward entrance door from outside
                        p.targetX = 584;
                        p.targetY = 620;
                        if (Math.abs(p.posX - 584) < 10f && Math.abs(p.currentY - 620) < 10f) {
                            p.isInside = true; // Enter building smoothly through door
                            p.floor = 1;
                        }
                    } else {
                        // Once inside, walk smoothly to assigned room
                        p.targetX = p.roomX;
                        p.targetY = p.roomY;
                        if (Math.abs(p.posX - p.roomX) <= 3f && Math.abs(p.currentY - p.roomY) <= 3f) {
                            p.orderedToRoom = false; // Reached room safely
                        }
                    }
                } else if (System.currentTimeMillis() - p.roamTimer > 6000) {
                    p.roamTimer = System.currentTimeMillis();
                    if (p.rand.nextDouble() < 0.5) {
                        p.isInside = true;
                        p.floor = 1 + p.rand.nextInt(3);
                        p.targetX = 180 + p.rand.nextInt(780);
                        p.targetY = 280 + p.rand.nextInt(220);
                    } else {
                        p.targetX = p.rand.nextDouble() < 0.5 ? door1X : door2X;
                        p.targetY = door1Y;
                    }
                }

                if (p.isInside) {
                    float nextX = p.posX;
                    float nextY = p.currentY;
                    if (p.posX < p.targetX) nextX += 0.5f;
                    else if (p.posX > p.targetX) nextX -= 0.5f;
                    if (!indoorCollides(nextX, p.currentY)) p.posX = nextX;

                    if (p.currentY < p.targetY) nextY += 0.5f;
                    else if (p.currentY > p.targetY) nextY -= 0.5f;
                    if (!indoorCollides(p.posX, nextY)) p.currentY = nextY;

                    if (Math.abs(p.posX - door1X) < 10f && Math.abs(p.currentY - door1Y) < 10f && p.rand.nextDouble() < 0.3) {
                        p.isInside = false;
                        p.posX = door1X;
                        p.currentY = door1Y + 30;
                    }
                } else {
                    float nextX = p.posX;
                    float nextY = p.currentY;
                    if (p.posX < p.targetX) nextX += 0.5f;
                    else if (p.posX > p.targetX) nextX -= 0.5f;
                    if (!outdoorCollides(nextX, p.currentY)) p.posX = nextX;

                    if (p.currentY < p.targetY) nextY += 0.5f;
                    else if (p.currentY > p.targetY) nextY -= 0.5f;
                    if (!outdoorCollides(p.posX, nextY)) p.currentY = nextY;

                    // Spawn/Enter smoothly through the door when walking into it from outside
                    if ((Math.abs(p.posX - door1X) < 15f && Math.abs(p.currentY - door1Y) < 15f) ||
                            (Math.abs(p.posX - door2X) < 15f && Math.abs(p.currentY - door2Y) < 15f)) {
                        p.isInside = true;
                        p.floor = 1;
                        p.posX = 584;
                        p.currentY = 620; // Enters through the bottom entrance door!
                    }
                }
            }

            if (!deadPatients.isEmpty()) {
                patients.removeAll(deadPatients);
                currentEvent = "WARNING: A patient died due to neglect! Score penalty applied (-100).";
            }

            for (DoctorData d : doctors) {
                if (d.state == 0) {
                    for (PatientData p : patients) {
                        if (!p.need.equals("HEALTHY")) {
                            d.state = 1;
                            d.targetPatient = p;
                            d.isInside = p.isInside;
                            d.floor = p.floor;
                            break;
                        }
                    }
                }

                if (d.state == 1 && d.targetPatient != null) {
                    d.isInside = d.targetPatient.isInside;
                    d.floor = d.targetPatient.floor;
                    d.targetX = d.targetPatient.posX;
                    d.targetY = d.targetPatient.currentY;

                    if (d.posX < d.targetX) d.posX += 1.5f;
                    else if (d.posX > d.targetX) d.posX -= 1.5f;
                    if (d.currentY < d.targetY) d.currentY += 1.5f;
                    else if (d.currentY > d.targetY) d.currentY -= 1.5f;

                    if (Math.abs(d.posX - d.targetX) <= 6f && Math.abs(d.currentY - d.targetY) <= 6f) {
                        String curedNeed = d.targetPatient.need;
                        if (curedNeed.equals("HUNGRY")) food = Math.max(0, food - 15);
                        else if (curedNeed.equals("THIRSTY")) water = Math.max(0, water - 15);
                        else if (curedNeed.equals("SICK")) medicine = Math.max(0, medicine - 10);

                        d.targetPatient.need = "HEALTHY";
                        d.targetPatient.hp = Math.min(100f, d.targetPatient.hp + 40f);
                        d.targetPatient.needTimer = System.currentTimeMillis();
                        d.state = 0;
                        d.targetPatient = null;
                        score += 35;
                        currentEvent = "Doctor treated patient! Supplies consumed & HP restored.";
                    }
                } else {
                    if (System.currentTimeMillis() - d.roamTimer > 6000) {
                        d.roamTimer = System.currentTimeMillis();
                        d.targetX = 180 + d.rand.nextInt(780);
                        d.targetY = 280 + d.rand.nextInt(220);
                    }
                    if (d.isInside) {
                        float nextX = d.posX;
                        float nextY = d.currentY;
                        if (d.posX < d.targetX) nextX += 0.6f;
                        else if (d.posX > d.targetX) nextX -= 0.6f;
                        if (!indoorCollides(nextX, d.currentY)) d.posX = nextX;
                        if (d.currentY < d.targetY) nextY += 0.6f;
                        else if (d.currentY > d.targetY) nextY -= 0.6f;
                        if (!indoorCollides(d.posX, nextY)) d.currentY = nextY;
                    }
                }
            }

            for (GuardData g : guards) {
                float targetXCoord = g.targetX;
                float targetYCoord = g.targetY;
                if (g.taskState == 2) {
                    targetXCoord = 770;
                    targetYCoord = 310;
                } else if (g.taskState == 3) {
                    targetXCoord = 320;
                    targetYCoord = 460;
                } else {
                    if (System.currentTimeMillis() - g.roamTimer > 7000) {
                        g.roamTimer = System.currentTimeMillis();
                        g.targetX = 150 + g.rand.nextInt(850);
                        g.targetY = 320 + g.rand.nextInt(180);
                    }
                }
                float nextX = g.posX;
                float nextY = g.currentY;
                float step = (g.taskState > 0) ? 1.5f : 0.6f;
                if (g.posX < targetXCoord) nextX += step;
                else if (g.posX > targetXCoord) nextX -= step;
                if (!outdoorCollides(nextX, g.currentY)) g.posX = nextX;
                if (g.currentY < targetYCoord) nextY += step;
                else if (g.currentY > targetYCoord) nextY -= step;
                if (!outdoorCollides(g.posX, nextY)) g.currentY = nextY;

                if (g.taskState == 2 && Math.abs(g.posX - 770) <= 6f && Math.abs(g.currentY - 310) <= 6f) {
                    g.taskState = 3;
                } else if (g.taskState == 3 && Math.abs(g.posX - 320) <= 6f && Math.abs(g.currentY - 460) <= 6f) {
                    stackedTrashCount++;
                    g.taskState = 0;
                }
            }

            long currentTime = System.currentTimeMillis();
            if (currentTime - lastTickTime >= 1000) {
                lastTickTime = currentTime;
                if (supplyTruckActive) supplyTruckPosX += 8;
                if (garbageTruckActive) garbageTruckPosX += 8;

                if (secondsRemaining > 0) {
                    secondsRemaining--;
                } else {
                    triggerDawnEvent();
                }
            }
        }

        private void triggerDawnEvent() {
            day++;
            secondsRemaining = DAY_DURATION_SECONDS;
            supplyTruckActive = true;
            supplyTruckPosX = -150;
            garbageTruckActive = true;
            garbageTruckPosX = -350;

            int currentCount = patients.size();
            int addCount = Math.max(5, currentCount);
            for (int i = 1; i <= addCount; i++) {
                int newId = currentCount + i;
                boolean inside = (newId % 2 == 0);
                int floor = (newId % 3) == 0 ? 3 : ((newId % 2 == 0) ? 2 : 1);
                patients.add(new PatientData(newId, 200 + (newId * 40) % 750, 320 + (newId * 15) % 180, inside, floor));
            }

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

            currentEvent = "DAWN (Day " + day + "): Population grew to " + patients.size() + " patients.";
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

                // Render Visible Rooms Outlines on Floors
                g.setColor(new Color(70, 80, 95));
                g.drawRect(50, 100, 120, 350); // Left Room Wing
                g.drawRect(1030, 100, 120, 350); // Right Room Wing
                g.drawRect(380, 40, 440, 260); // Center Block

                g.setColor(Color.WHITE);
                g.setFont(new Font("Monospaced", Font.BOLD, 12));
                TextRenderer.render(g, "[WARD ROOM A]", 60, 90);
                TextRenderer.render(g, "[WARD ROOM B]", 1040, 90);
                TextRenderer.render(g, "[MEDICAL STATION]", 470, 30);

                // Collision Highlights
                g.setColor(new Color(231, 76, 60, 70));
                g.fill(indoorLeftRooms);
                g.fill(indoorRightRooms);
                g.fill(indoorCenterBlock);
                g.fill(indoorMedicalDesk);
                g.fill(indoorTopWall);
                g.fill(indoorBottomWallLeft);
                g.fill(indoorBottomWallRight);

                // Highlighted Stairs Tile [550, 120]
                g.setColor(new Color(0, 255, 255, 180));
                g.fillRect(indoorStairs.x, indoorStairs.y, indoorStairs.width, indoorStairs.height);
                g.setColor(Color.BLACK);
                g.drawRect(indoorStairs.x, indoorStairs.y, indoorStairs.width, indoorStairs.height);
                g.setColor(Color.WHITE);
                g.setFont(new Font("Monospaced", Font.BOLD, 13));
                TextRenderer.render(g, "[STAIRS - PRESS E]", indoorStairs.x - 5, indoorStairs.y - 6);

                for (PatientData p : patients) {
                    if (p.isInside && p.floor == currentFloor) {
                        renderPatientWithSpriteAndHealth(g, (int) p.posX, (int) p.currentY, "P#" + p.id, p.need, p.hp, p.isFemale);
                    }
                }
                for (DoctorData doc : doctors) {
                    if (doc.isInside && doc.floor == currentFloor) {
                        renderDoctor(g, (int) doc.posX, (int) doc.currentY, "Doctor");
                    }
                }

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

                if (supplyTruckActive) drawSupplyTruck(g, supplyTruckPosX, 690);
                if (garbageTruckActive) drawGarbageTruck(g, garbageTruckPosX, 690);
                if (hireVehicleActive) drawHireVehicle(g, hireVehiclePosX, 690, hiredRoleName);

                if (stackedTrashCount > 0) drawStackedTrash(g, 320, 460, stackedTrashCount);

                for (PatientData p : patients) {
                    if (!p.isInside) {
                        renderPatientWithSpriteAndHealth(g, (int) p.posX, (int) p.currentY, "P#" + p.id, p.need, p.hp, p.isFemale);
                    }
                }

                for (DoctorData d : doctors) {
                    if (!d.isInside) {
                        renderDoctor(g, (int) d.posX, (int) d.currentY, "Doctor");
                    }
                }

                for (GuardData gData : guards) {
                    renderGuardian(g, (int) gData.posX, (int) gData.currentY, "Guard");
                }

                renderOverseer(g, overseerX, overseerY);
            }

            int uiWidth = Math.min(screenWidth - 40, 1160);
            g.setColor(new Color(15, 20, 25, 220));
            g.fillRect(20, 15, uiWidth, 160);
            g.setColor(new Color(46, 204, 113));
            g.drawRect(20, 15, uiWidth, 160);

            g.setFont(new Font("Monospaced", Font.BOLD, 18));
            g.setColor(new Color(46, 204, 113));
            TextRenderer.render(g, "QUARANTINE HAVEN [OVERSEER SIMULATOR]", 35, 40);

            int minutes = secondsRemaining / 60;
            int seconds = secondsRemaining % 60;
            String timeStr = String.format("%02d:%02d", minutes, seconds);

            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            g.setColor(Color.WHITE);
            String topInfo = String.format("DAY: %d | PATIENTS: %d | NEXT TRUCK: %s | SCORE: %d | PTS: %d", day, patients.size(), timeStr, score, currencyPoints);
            TextRenderer.render(g, topInfo, 35, 62);

            String floorInfo = insideBuilding != null ? "INSIDE: " + insideBuilding + " | FLOOR: " + currentFloor + " (Cyan Stairs at [550, 120])" : "GROUNDS ACTIVE | Patients roaming in & out";
            g.setColor(new Color(52, 152, 219));
            TextRenderer.render(g, floorInfo, 35, 84);

            String resInfo = String.format("RESOURCES -> Food: %d | Water: %d | Medicine: %d | Materials: %d", food, water, medicine, materials);
            g.setColor(new Color(241, 196, 15));
            TextRenderer.render(g, resInfo, 35, 106);

            g.setColor(Color.LIGHT_GRAY);
            TextRenderer.render(g, "WASTE CAPACITY: " + waste + "% | TRASH PILES: " + stackedTrashCount, 35, 128);

            g.setColor(new Color(231, 76, 60));
            g.fillRect(175, 118, waste * 2, 10);
            g.setColor(Color.DARK_GRAY);
            g.drawRect(175, 118, 200, 10);

            g.setColor(Color.CYAN);
            TextRenderer.render(g, "EVENT LOG: " + currentEvent, 35, 150);

            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.setColor(new Color(200, 200, 200));
            TextRenderer.render(g, "CONTROLS: [WASD] Move | [E] Door/Stairs | [1-3] Tasks | [4] Doctor | [5] Recall to Rooms | [H] Hire", 35, 172);
        }

        private void renderDoctor(Graphics2D g, int x, int y, String label) {
            if (doctorSheet != null) {
                g.drawImage(doctorSheet, x, y, 32, 32, null);
            } else {
                renderCharacterBadge(g, x, y, Color.GREEN, label, "DOC");
            }
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 10));
            TextRenderer.render(g, label, x - 10, y + 46);
        }

        private void renderPatientWithSpriteAndHealth(Graphics2D g, int x, int y, String label, String need, float hp, boolean isFemale) {
            BufferedImage sheet = isFemale ? femalePatientSheet : malePatientSheet;
            if (sheet != null) {
                g.drawImage(sheet, x, y, 32, 32, null);
            } else {
                Color badgeColor = need.equals("HEALTHY") ? Color.ORANGE : Color.RED;
                renderCharacterBadge(g, x, y, badgeColor, label, "PAT");
            }

            g.setColor(Color.DARK_GRAY);
            g.fillRect(x - 4, y - 8, 38, 5);
            g.setColor(hp > 50 ? Color.GREEN : (hp > 25 ? Color.YELLOW : Color.RED));
            g.fillRect(x - 3, y - 7, (int)(36 * (Math.max(0, hp) / 100.0f)), 3);

            g.setColor(need.equals("HEALTHY") ? Color.LIGHT_GRAY : Color.YELLOW);
            g.setFont(new Font("SansSerif", Font.BOLD, 9));
            TextRenderer.render(g, label + "[" + need + "]", x - 18, y + 46);
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

        private void drawRoadCar(Graphics2D g, float x, int y, Color carColor) {
            g.setColor(carColor);
            g.fillRect((int) x, y, 65, 24);
            g.setColor(Color.BLACK);
            g.drawRect((int) x, y, 65, 24);
            g.setColor(new Color(200, 220, 240));
            g.fillRect((int) x + 15, y + 3, 35, 8);
            g.setColor(Color.DARK_GRAY);
            g.fillOval((int) x + 10, y + 20, 10, 10);
            g.fillOval((int) x + 45, y + 20, 10, 10);
        }

        private void drawSupplyTruck(Graphics2D g, float x, int y) {
            g.setColor(new Color(190, 200, 210));
            g.fillRect((int) x, y, 110, 40);
            g.setColor(Color.BLACK);
            g.drawRect((int) x, y, 110, 40);
            TextRenderer.render(g, "Supply Truck", (int) x + 12, y + 25);
            g.setColor(new Color(41, 128, 185));
            g.fillRect((int) x + 110, y + 10, 30, 30);
            g.setColor(Color.DARK_GRAY);
            g.fillOval((int) x + 20, y + 35, 14, 14);
            g.fillOval((int) x + 95, y + 35, 14, 14);
        }

        private void drawGarbageTruck(Graphics2D g, float x, int y) {
            g.setColor(new Color(40, 140, 90));
            g.fillRect((int) x, y, 110, 40);
            g.setColor(Color.BLACK);
            g.drawRect((int) x, y, 110, 40);
            g.setColor(Color.WHITE);
            TextRenderer.render(g, "Garbage", (int) x + 8, y + 25);
            g.setColor(new Color(50, 50, 50));
            g.fillRect((int) x + 110, y + 10, 30, 30);
            g.setColor(Color.DARK_GRAY);
            g.fillOval((int) x + 20, y + 35, 14, 14);
            g.fillOval((int) x + 95, y + 35, 14, 14);
        }

        private void drawHireVehicle(Graphics2D g, float x, int y, String role) {
            g.setColor(new Color(230, 126, 34));
            g.fillRect((int) x, y, 100, 38);
            g.setColor(Color.BLACK);
            g.drawRect((int) x, y, 100, 38);
            g.setColor(Color.WHITE);
            TextRenderer.render(g, "Hire: " + role, (int) x + 10, y + 24);
            g.setColor(Color.DARK_GRAY);
            g.fillOval((int) x + 15, y + 32, 12, 12);
            g.fillOval((int) x + 75, y + 32, 12, 12);
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