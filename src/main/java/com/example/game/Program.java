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
        private int overseerX = 580;
        private int overseerY = 420;

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

        private boolean supplyTruckActive = false;
        private boolean garbageTruckActive = false;

        private float hireVehiclePosX = -150;
        private boolean hireVehicleActive = false;
        private String hiredRoleName = "";


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

        private final Rectangle indoorLeftRooms = new Rectangle(0, 0, 160, 720);
        private final Rectangle indoorRightRooms = new Rectangle(1040, 0, 160, 720);
        private final Rectangle indoorTopWall = new Rectangle(0, 0, 1200, 35);
        private final Rectangle indoorBottomWallLeft = new Rectangle(0, 670, 550, 50);
        private final Rectangle indoorBottomWallRight = new Rectangle(630, 670, 570, 50);
        private final Rectangle indoorStairs = new Rectangle(550, 120, 70, 50);

        public ShelterScreen() {
            super("SHELTER_SCREEN");
            mapBgImage = loadImage("/map_bg.png");
            interiorBgImage = loadImage("/interior_map.png");

            for (int i = 1; i <= 10; i++) {
                boolean inside = (i % 2 == 0);
                int floor = (i % 3) == 0 ? 3 : ((i % 2 == 0) ? 2 : 1);
                float startX = inside ? (200 + (i * 70) % 700) : (180 + (i * 90) % 850);
                float startY = inside ? (320 + (i * 20) % 150) : (350 + (i * 15) % 120);
                patients.add(new PatientData(i, startX, startY, inside, floor));
            }

            doctors.add(new DoctorData(250, 380, false, 1));
            doctors.add(new DoctorData(500, 150, true, 2));

            guards.add(new GuardData(600, 450));

            Input.keyboard().onKeyPressed(KeyEvent.VK_W, e -> moveOverseer(0, -MOVE_SPEED));
            Input.keyboard().onKeyPressed(KeyEvent.VK_S, e -> moveOverseer(0, MOVE_SPEED));
            Input.keyboard().onKeyPressed(KeyEvent.VK_A, e -> moveOverseer(-MOVE_SPEED, 0));
            Input.keyboard().onKeyPressed(KeyEvent.VK_D, e -> moveOverseer(MOVE_SPEED, 0));
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
            if (insideBuilding != null) {
                if (player.intersects(indoorLeftRooms) || player.intersects(indoorRightRooms) ||
                        player.intersects(indoorTopWall) || player.intersects(indoorBottomWallLeft) ||
                        player.intersects(indoorBottomWallRight)) {
                    return false;
                }
                return true;
            }
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
                    currentFloor++;
                    if (currentFloor > 3) currentFloor = 1;
                    currentEvent = "Climbed stairs in " + insideBuilding + " -> Now on Floor " + currentFloor + "!";
                    return;
                }
                    exitBuilding();
                } else {
                    currentEvent = "Walk to cyan stairs [550, 120] to change floors, or walk down to exit door.";
                }
                return;
            }
            } else {
            }
        }

        private void enterBuilding(String buildingName) {
            insideBuilding = buildingName;
            currentFloor = 1;
            overseerX = 584;
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
                    rect.intersects(indoorTopWall) || rect.intersects(indoorBottomWallLeft) ||
                    rect.intersects(indoorBottomWallRight);
        }

        private boolean outdoorCollides(float x, float y) {
        }

            if (supplyTruckActive) {
            }
            if (garbageTruckActive) {
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
                        p.targetX = 584;
                        p.targetY = 620;
                        if (Math.abs(p.posX - 584) < 10f && Math.abs(p.currentY - 620) < 10f) {
                            p.floor = 1;
                        }
                    } else {
                        p.targetX = p.roomX;
                        p.targetY = p.roomY;
                        if (Math.abs(p.posX - p.roomX) <= 3f && Math.abs(p.currentY - p.roomY) <= 3f) {
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
                    if ((Math.abs(p.posX - door1X) < 15f && Math.abs(p.currentY - door1Y) < 15f) ||
                            (Math.abs(p.posX - door2X) < 15f && Math.abs(p.currentY - door2Y) < 15f)) {
                        p.isInside = true;
                        p.floor = 1;
                        p.posX = 584;
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
                } else if (g.taskState == 3) {
                    targetXCoord = 320;
                } else {
                    if (System.currentTimeMillis() - g.roamTimer > 7000) {
                        g.roamTimer = System.currentTimeMillis();
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

                    g.taskState = 3;
                    stackedTrashCount++;
                    g.taskState = 0;
                }
            }

            long currentTime = System.currentTimeMillis();
            if (currentTime - lastTickTime >= 1000) {
                lastTickTime = currentTime;
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
            garbageTruckActive = true;
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

            if (insideBuilding != null) {
                if (interiorBgImage != null) {
                    g.drawImage(interiorBgImage, 0, 0, screenWidth, screenHeight, null);
                } else {
                    g.setColor(new Color(35, 42, 50));
                    g.fillRect(0, 0, screenWidth, screenHeight);
                }
                g.setColor(new Color(70, 80, 95));
                g.setColor(Color.WHITE);
                g.setFont(new Font("Monospaced", Font.BOLD, 12));
                TextRenderer.render(g, "[WARD ROOM A]", 60, 90);
                TextRenderer.render(g, "[WARD ROOM B]", 1040, 90);
                TextRenderer.render(g, "[MEDICAL STATION]", 470, 30);

                g.setColor(new Color(231, 76, 60, 70));
                g.fill(indoorLeftRooms);
                g.fill(indoorRightRooms);
                g.fill(indoorTopWall);
                g.fill(indoorBottomWallLeft);
                g.fill(indoorBottomWallRight);

                g.setColor(new Color(0, 255, 255, 180));
                g.fillRect(indoorStairs.x, indoorStairs.y, indoorStairs.width, indoorStairs.height);
                g.setColor(Color.BLACK);
                g.drawRect(indoorStairs.x, indoorStairs.y, indoorStairs.width, indoorStairs.height);
                g.setColor(Color.WHITE);
                g.setFont(new Font("Monospaced", Font.BOLD, 13));
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









            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        }

        private void renderDoctor(Graphics2D g, int x, int y, String label) {
            if (doctorSheet != null) {
            } else {
                renderCharacterBadge(g, x, y, Color.GREEN, label, "DOC");
            }
        }

        private void renderPatientWithSpriteAndHealth(Graphics2D g, int x, int y, String label, String need, float hp, boolean isFemale) {
            BufferedImage sheet = isFemale ? femalePatientSheet : malePatientSheet;
            if (sheet != null) {
            } else {
                Color badgeColor = need.equals("HEALTHY") ? Color.ORANGE : Color.RED;
                renderCharacterBadge(g, x, y, badgeColor, label, "PAT");
            }
            g.setColor(hp > 50 ? Color.GREEN : (hp > 25 ? Color.YELLOW : Color.RED));
        }

        private void renderGuardian(Graphics2D g, int x, int y, String label) {
            if (guardianSheet != null) {
            } else {
                renderCharacterBadge(g, x, y, Color.BLUE, label, "GRD");
            }
        }

        private void renderOverseer(Graphics2D g, int x, int y) {
            if (overseerSheet != null) {
            } else {
                renderCharacterBadge(g, x, y, new Color(241, 196, 15), "OVERSEER", "OSE");
            }
        }

        private void renderCharacterBadge(Graphics2D g, int x, int y, Color color, String label, String tag) {
            g.setColor(new Color(15, 20, 25, 220));
            g.setColor(color);
            g.setColor(Color.BLACK);
            g.setColor(Color.BLACK);
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
                int offsetX = (i % 3) * 12;
                int offsetY = (i / 3) * 10;
                g.fillOval(x + offsetX, y - offsetY, 14, 12);
            }
        }
    }
}