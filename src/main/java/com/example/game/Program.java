package com.example.game;

import com.example.game.model.StaffUnit;
import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.configuration.DisplayMode;
import de.gurkenlabs.litiengine.graphics.TextRenderer;
import de.gurkenlabs.litiengine.gui.screens.GameScreen;
import de.gurkenlabs.litiengine.input.Input;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Program {
    private static final int WORLD_WIDTH = 1200;
    private static final int WORLD_HEIGHT = 720;
    private static final int ROAD_CAR_HEIGHT = 44;
    private static final int ROAD_CAR_SPAWN_GAP = 100;
    private static final int ROAD_LANE_UPPER_Y = 592;
    private static final int ROAD_LANE_LOWER_Y = 654;
    private static final float ROAD_TRAFFIC_SPEED = 132.0f;
    private static final int ARRIVAL_TRUCK_HEIGHT = 56;
    private static final float ARRIVAL_TRUCK_SPEED = 132.0f;

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false");
        Game.info().setName("Quarantine Haven");
        Game.info().setVersion("1.6.6");
        Game.init(args);
        Game.screens().add(new MyGameScreen());
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
        private String currentEvent = "Guard anti-stuck collision resolution and spawn position updated!";
        private int overseerX = 580;
        private int overseerY = 420;

        private static final int PLAYER_WIDTH = 26;
        private static final int PLAYER_HEIGHT = 26;
        private static final int PLAYER_COLLISION_WIDTH = 20;
        private static final int PLAYER_COLLISION_HEIGHT = 14;
        private static final int OVERSEER_DRAW_HEIGHT = 68;
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
        private BufferedImage supplyTruckSprite;
        private BufferedImage garbageTruckSprite;
        private BufferedImage trashSprite;

        private float supplyTruckPosX = -150;
        private boolean supplyTruckActive = false;
        private float garbageTruckPosX = -150;
        private boolean garbageTruckActive = false;

        private float hireVehiclePosX = -150;
        private boolean hireVehicleActive = false;
        private String hiredRoleName = "";
        private boolean interactionKeyDown;
        private boolean displayModeKeyDown;

        private static class RoadTrafficCar {
            float x;
            int width;
            int laneY;
            boolean movingRight;
            BufferedImage sprite;
        }
        private final List<RoadTrafficCar> roadTraffic = new ArrayList<>();
        private final Random roadTrafficRandom = new Random();
        private BufferedImage[] roadCarSprites;
        private long nextRoadTrafficSpawn;
        private long lastRoadTrafficUpdateNanos = System.nanoTime();

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

        private final Rectangle[] buildingWalls = {
                new Rectangle(122, 42, 174, 238), new Rectangle(122, 280, 62, 58), new Rectangle(234, 280, 62, 58),
                new Rectangle(322, 42, 177, 238), new Rectangle(322, 280, 64, 58), new Rectangle(435, 280, 64, 58),
                new Rectangle(522, 42, 177, 238), new Rectangle(522, 280, 64, 58), new Rectangle(637, 280, 62, 58)
        };
        private final Rectangle[] buildingEntrances = {
                new Rectangle(181, 265, 54, 103),
                new Rectangle(383, 265, 52, 103),
                new Rectangle(584, 265, 53, 103)
        };
        private final String[] buildingNames = {"PATIENT WARD 1", "PATIENT WARD 2", "PATIENT WARD 3"};
        private final Rectangle[] lowerBuildingWalls = {
                new Rectangle(132, 382, 58, 116),
                new Rectangle(234, 382, 56, 116)
        };
        private final Rectangle bottomWallLeft = new Rectangle(74, 470, 486, 100);
        private final Rectangle bottomWallRight = new Rectangle(886, 470, 234, 100);
        private final Rectangle leftWall = new Rectangle(74, 42, 48, 428);
        private final Rectangle rightFence = new Rectangle(1104, 42, 48, 428);
        private final Rectangle indoorLeftRooms = new Rectangle(0, 0, 160, 720);
        private final Rectangle indoorRightRooms = new Rectangle(1040, 0, 160, 720);
        private final Rectangle[] indoorCenterBlockWalls = {
                new Rectangle(380, 40, 170, 260), new Rectangle(620, 40, 200, 260)
        };
        private final Rectangle[] indoorMedicalDeskWalls = {
                new Rectangle(470, 320, 80, 40), new Rectangle(620, 320, 110, 40)
        };
        private final Rectangle indoorTopWall = new Rectangle(0, 0, 1200, 35);
        private final Rectangle indoorBottomWallLeft = new Rectangle(0, 670, 550, 50);
        private final Rectangle indoorBottomWallRight = new Rectangle(630, 670, 570, 50);
        private final Rectangle indoorStairs = new Rectangle(550, 120, 70, 50);
        private final Rectangle indoorExit = new Rectangle(550, 654, 100, 56);
        private final float door1X = 194;
        private final float door1Y = 315;
        private final float door2X = 394;
        private final float door2Y = 315;

        public ShelterScreen() {
            super("SHELTER_SCREEN");
            mapBgImage = loadImage("/map_bg.png");
            interiorBgImage = loadImage("/interior_map.png");
            overseerSheet = trimTransparent(loadImage("/overseer_sprite.png"));
            doctorSheet = trimTransparent(loadImage("/doctor_sprite.png"));
            guardianSheet = trimTransparent(loadImage("/guardian_sprite.png"));
            femalePatientSheet = trimTransparent(loadImage("/female_patient.png"));
            malePatientSheet = trimTransparent(loadImage("/male_patient.png"));
            supplyTruckSprite = trimTransparent(loadImage("/supply_arrival_truck.png"));
            garbageTruckSprite = trimTransparent(loadImage("/waste_arrival_truck.png"));
            trashSprite = trimTransparent(loadImage("/trash_sprite.png"));
            roadCarSprites = new BufferedImage[] {
                    trimTransparent(loadImage("/road_car_white_suv.png")),
                    flipHorizontally(trimTransparent(loadImage("/road_car_blue.png"))),
                    trimTransparent(loadImage("/road_car_red.png")),
                    trimTransparent(loadImage("/road_car_silver.png")),
                    flipHorizontally(trimTransparent(loadImage("/road_car_green_van.png")))
            };
            nextRoadTrafficSpawn = System.currentTimeMillis() + 300 + roadTrafficRandom.nextInt(900);

            for (int i = 1; i <= 10; i++) {
                boolean inside = (i % 2 == 0);
                int floor = (i % 3) == 0 ? 3 : ((i % 2 == 0) ? 2 : 1);
                float startX = inside ? (200 + (i * 70) % 700) : (180 + (i * 90) % 850);
                float startY = inside ? (320 + (i * 20) % 150) : (350 + (i * 15) % 120);
                patients.add(new PatientData(i, startX, startY, inside, floor));
            }

            doctors.add(new DoctorData(250, 380, false, 1));
            doctors.add(new DoctorData(500, 150, true, 2));

            // Moved left guard further down into open walkway area (Y=445)
            guards.add(new GuardData(240, 445));
            guards.add(new GuardData(600, 450));

            Input.keyboard().onKeyPressed(KeyEvent.VK_W, e -> moveOverseer(0, -MOVE_SPEED));
            Input.keyboard().onKeyPressed(KeyEvent.VK_S, e -> moveOverseer(0, MOVE_SPEED));
            Input.keyboard().onKeyPressed(KeyEvent.VK_A, e -> moveOverseer(-MOVE_SPEED, 0));
            Input.keyboard().onKeyPressed(KeyEvent.VK_D, e -> moveOverseer(MOVE_SPEED, 0));
            Input.keyboard().onKeyPressed(KeyEvent.VK_E, e -> {
                if (!interactionKeyDown) {
                    interactionKeyDown = true;
                    interactWithLocation();
                }
            });
            Input.keyboard().onKeyReleased(KeyEvent.VK_E, e -> interactionKeyDown = false);
            Input.keyboard().onKeyPressed(KeyEvent.VK_ESCAPE, e -> exitBuilding());
            Input.keyboard().onKeyPressed(KeyEvent.VK_1, e -> assignTaskToNearestStaff(1));
            Input.keyboard().onKeyPressed(KeyEvent.VK_2, e -> assignTaskToNearestStaff(2));
            Input.keyboard().onKeyPressed(KeyEvent.VK_3, e -> assignTaskToNearestStaff(3));
            Input.keyboard().onKeyPressed(KeyEvent.VK_4, e -> dispatchDoctorToNeedyPatient());
            Input.keyboard().onKeyPressed(KeyEvent.VK_5, e -> orderPatientsBackToRooms());
            Input.keyboard().onKeyPressed(KeyEvent.VK_H, e -> hireStaffMember("Doctor", 150));
            Input.keyboard().onKeyPressed(KeyEvent.VK_F11, e -> {
                if (!displayModeKeyDown) {
                    displayModeKeyDown = true;
                    toggleDisplayMode();
                }
            });
            Input.keyboard().onKeyReleased(KeyEvent.VK_F11, e -> displayModeKeyDown = false);
            Game.loop().attach(this::updateGameLoop);
        }

        private void toggleDisplayMode() {
            var graphics = Game.config().graphics();
            graphics.setDisplayMode(graphics.getDisplayMode() == DisplayMode.FULLSCREEN
                    ? DisplayMode.WINDOWED : DisplayMode.FULLSCREEN);
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

        private BufferedImage trimTransparent(BufferedImage image) {
            if (image == null) return null;
            int minX = image.getWidth(), minY = image.getHeight(), maxX = -1, maxY = -1;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if ((image.getRGB(x, y) >>> 24) > 16) {
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                    }
                }
            }
            if (maxX < minX || maxY < minY) return image;
            return image.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
        }

        private BufferedImage flipHorizontally(BufferedImage image) {
            if (image == null) return null;
            BufferedImage flipped = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = flipped.createGraphics();
            graphics.drawImage(image, image.getWidth(), 0, -image.getWidth(), image.getHeight(), null);
            graphics.dispose();
            return flipped;
        }

        private boolean canMoveTo(int x, int y) {
            Rectangle player = actorBoundsAt(x, y);
            if (player.x < 0 || player.y < 0 || player.x + player.width > WORLD_WIDTH ||
                    player.y + player.height > WORLD_HEIGHT) {
                return false;
            }
            if (insideBuilding != null) {
                if (player.intersects(indoorLeftRooms) || player.intersects(indoorRightRooms) ||
                        player.intersects(indoorTopWall) || player.intersects(indoorBottomWallLeft) ||
                        player.intersects(indoorBottomWallRight)) {
                    return false;
                }
                if (intersectsAny(player, indoorCenterBlockWalls) || intersectsAny(player, indoorMedicalDeskWalls)) {
                    return false;
                }
                return true;
            }
            if (player.y < 60 || player.y + player.height > 560) return false;
            if (player.intersects(bottomWallLeft) || player.intersects(bottomWallRight) ||
                    player.intersects(leftWall) || player.intersects(rightFence)) {
                return false;
            }
            for (Rectangle wall : lowerBuildingWalls) {
                if (player.intersects(wall)) return false;
            }
            for (Rectangle buildingWall : buildingWalls) {
                if (player.intersects(buildingWall)) return false;
            }
            return true;
        }

        private Rectangle actorBoundsAt(float x, float y) {
            return new Rectangle((int) Math.floor(x) + (PLAYER_WIDTH - PLAYER_COLLISION_WIDTH) / 2,
                    (int) Math.floor(y) + PLAYER_HEIGHT - PLAYER_COLLISION_HEIGHT,
                    PLAYER_COLLISION_WIDTH, PLAYER_COLLISION_HEIGHT);
        }

        private int nearbyBuildingEntrance(Rectangle actorBounds) {
            for (int i = 0; i < buildingEntrances.length; i++) {
                if (actorBounds.intersects(buildingEntrances[i])) return i;
            }
            return -1;
        }

        private void moveOverseer(int dx, int dy) {
            int nextX = overseerX + dx;
            int nextY = overseerY + dy;
            if (canMoveTo(nextX, overseerY)) overseerX = nextX;
            if (canMoveTo(overseerX, nextY)) overseerY = nextY;
        }

        private void interactWithLocation() {
            Rectangle playerBounds = actorBoundsAt(overseerX, overseerY);
            if (insideBuilding != null) {
                if (playerBounds.intersects(indoorStairs)) {
                    currentFloor++;
                    if (currentFloor > 3) currentFloor = 1;
                    currentEvent = "Climbed stairs in " + insideBuilding + " -> Now on Floor " + currentFloor + "!";
                    return;
                }
                if (playerBounds.intersects(indoorExit)) {
                    exitBuilding();
                } else {
                    currentEvent = "Walk to cyan stairs [550, 120] to change floors, or walk down to exit door.";
                }
                return;
            }
            int entrance = nearbyBuildingEntrance(playerBounds);
            if (entrance >= 0) {
                enterBuilding(buildingNames[entrance]);
            } else {
                currentEvent = "Stand in front of a ward entrance and press [E].";
            }
        }

        private void enterBuilding(String buildingName) {
            insideBuilding = buildingName;
            currentFloor = 1;
            overseerX = 584;
            overseerY = 620;
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
            Rectangle rect = actorBoundsAt(x, y);
            return rect.x < 0 || rect.y < 0 || rect.x + rect.width > WORLD_WIDTH ||
                    rect.y + rect.height > WORLD_HEIGHT || rect.intersects(indoorLeftRooms) || rect.intersects(indoorRightRooms) ||
                    intersectsAny(rect, indoorCenterBlockWalls) || intersectsAny(rect, indoorMedicalDeskWalls) ||
                    rect.intersects(indoorTopWall) || rect.intersects(indoorBottomWallLeft) ||
                    rect.intersects(indoorBottomWallRight);
        }

        private boolean intersectsAny(Rectangle actor, Rectangle[] obstacles) {
            for (Rectangle obstacle : obstacles) {
                if (actor.intersects(obstacle)) return true;
            }
            return false;
        }

        private boolean outdoorCollides(float x, float y) {
            Rectangle rect = actorBoundsAt(x, y);
            if (rect.x < 0 || rect.y < 60 || rect.x + rect.width > WORLD_WIDTH || rect.y + rect.height > 560) {
                return true;
            }
            for (Rectangle buildingWall : buildingWalls) {
                if (rect.intersects(buildingWall)) return true;
            }
            for (Rectangle wall : lowerBuildingWalls) {
                if (rect.intersects(wall)) return true;
            }
            return rect.intersects(bottomWallLeft) || rect.intersects(bottomWallRight) ||
                    rect.intersects(leftWall) || rect.intersects(rightFence);
        }

        private void updateRoadTraffic() {
            long nowMillis = System.currentTimeMillis();
            long nowNanos = System.nanoTime();
            float deltaSeconds = Math.max(0f, (nowNanos - lastRoadTrafficUpdateNanos) / 1_000_000_000f);
            lastRoadTrafficUpdateNanos = nowNanos;

            if (nowMillis >= nextRoadTrafficSpawn) {
                if (spawnRoadTrafficCar()) {
                    nextRoadTrafficSpawn = nowMillis + 1100 + roadTrafficRandom.nextInt(1700);
                } else {
                    nextRoadTrafficSpawn = nowMillis + 350;
                }
            }

            for (int i = roadTraffic.size() - 1; i >= 0; i--) {
                RoadTrafficCar car = roadTraffic.get(i);

                if ((supplyTruckActive || garbageTruckActive) && car.laneY == ROAD_LANE_UPPER_Y) {
                    roadTraffic.remove(i);
                    continue;
                }

                float nextX = car.x + (car.movingRight ? 1 : -1) * ROAD_TRAFFIC_SPEED * deltaSeconds;
                if (roadTrafficPositionIsClear(car, nextX)) car.x = nextX;
                if (car.x < 0 || car.x + car.width > WORLD_WIDTH) {
                    roadTraffic.remove(i);
                }
            }

            if (supplyTruckActive) {
                supplyTruckPosX += ARRIVAL_TRUCK_SPEED * deltaSeconds;
                if (supplyTruckPosX > WORLD_WIDTH) supplyTruckActive = false;
            }
            if (garbageTruckActive) {
                garbageTruckPosX += ARRIVAL_TRUCK_SPEED * deltaSeconds;
                if (garbageTruckPosX > WORLD_WIDTH) garbageTruckActive = false;
            }
        }

        private boolean roadTrafficPositionIsClear(RoadTrafficCar movingCar, float nextX) {
            for (RoadTrafficCar other : roadTraffic) {
                if (other == movingCar || other.laneY != movingCar.laneY) continue;
                if (nextX < other.x + other.width && nextX + movingCar.width > other.x) return false;
            }
            return true;
        }

        private boolean spawnRoadTrafficCar() {
            boolean movingRight = roadTrafficRandom.nextBoolean();
            int laneY = movingRight ? ROAD_LANE_UPPER_Y : ROAD_LANE_LOWER_Y;
            if ((supplyTruckActive || garbageTruckActive) && laneY == ROAD_LANE_UPPER_Y) {
                return false;
            }

            BufferedImage sprite = roadCarSprites[roadTrafficRandom.nextInt(roadCarSprites.length)];
            if (sprite == null) return false;
            int carWidth = Math.max(1, (int) Math.round(ROAD_CAR_HEIGHT *
                    sprite.getWidth() / (double) sprite.getHeight()));
            float startX = movingRight ? 0 : WORLD_WIDTH - carWidth;
            for (RoadTrafficCar existing : roadTraffic) {
                if (existing.laneY == laneY && existing.movingRight == movingRight &&
                        Math.abs(existing.x - startX) < Math.max(existing.width, carWidth) + ROAD_CAR_SPAWN_GAP) {
                    return false;
                }
            }
            RoadTrafficCar car = new RoadTrafficCar();
            car.x = startX;
            car.width = carWidth;
            car.laneY = laneY;
            car.movingRight = movingRight;
            car.sprite = sprite;
            roadTraffic.add(car);
            return true;
        }

        private void updateGameLoop() {
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
            updateRoadTraffic();

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
                            p.isInside = true;
                            p.floor = 1;
                        }
                    } else {
                        p.targetX = p.roomX;
                        p.targetY = p.roomY;
                        if (Math.abs(p.posX - p.roomX) <= 3f && Math.abs(p.currentY - p.roomY) <= 3f) {
                            p.orderedToRoom = false;
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
                        p.currentY = 620;
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
                // Anti-stuck safety nudge if guard ends up inside a wall obstacle
                if (outdoorCollides(g.posX, g.currentY)) {
                    g.currentY += 4f; // Push down out of snag zone
                    g.targetX = g.posX + 30f;
                    g.targetY = g.currentY + 20f;
                }

                float targetXCoord = g.targetX;
                float targetYCoord = g.targetY;
                if (g.taskState == 2) {
                    targetXCoord = 720;
                    targetYCoord = 430;
                } else if (g.taskState == 3) {
                    targetXCoord = 320;
                    targetYCoord = 440;
                } else {
                    if (System.currentTimeMillis() - g.roamTimer > 7000) {
                        g.roamTimer = System.currentTimeMillis();
                        g.targetX = 200 + g.rand.nextInt(750); // Kept away from extreme left wall bounds
                        g.targetY = 410 + g.rand.nextInt(90);
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

                if (g.taskState == 2 && Math.abs(g.posX - 720) <= 6f && Math.abs(g.currentY - 430) <= 6f) {
                    g.taskState = 3;
                } else if (g.taskState == 3 && Math.abs(g.posX - 320) <= 6f && Math.abs(g.currentY - 440) <= 6f) {
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
            supplyTruckPosX = -getArrivalTruckWidth(supplyTruckSprite);
            garbageTruckActive = true;
            garbageTruckPosX = -getArrivalTruckWidth(garbageTruckSprite) - 220;
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
            int screenWidth = Game.window().getRenderComponent().getWidth();
            int screenHeight = Game.window().getRenderComponent().getHeight();
            if (screenWidth <= 0 || screenHeight <= 0) return;
            g.setColor(new Color(9, 14, 20));
            g.fillRect(0, 0, screenWidth, screenHeight);
            double scale = Math.min(screenWidth / (double) WORLD_WIDTH, screenHeight / (double) WORLD_HEIGHT);
            double offsetX = (screenWidth - WORLD_WIDTH * scale) / 2.0;
            double offsetY = (screenHeight - WORLD_HEIGHT * scale) / 2.0;
            Graphics2D scene = (Graphics2D) g.create();
            try {
                scene.translate(offsetX, offsetY);
                scene.scale(scale, scale);
                scene.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                scene.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                renderFrame(scene);
            } finally {
                scene.dispose();
            }
        }

        private void renderFrame(Graphics2D g) {
            int screenWidth = WORLD_WIDTH;
            int screenHeight = WORLD_HEIGHT;
            if (insideBuilding != null) {
                if (interiorBgImage != null) {
                    g.drawImage(interiorBgImage, 0, 0, screenWidth, screenHeight, null);
                } else {
                    g.setColor(new Color(35, 42, 50));
                    g.fillRect(0, 0, screenWidth, screenHeight);
                }
                g.setColor(new Color(70, 80, 95));
                g.drawRect(50, 100, 120, 350);
                g.drawRect(1030, 100, 120, 350);
                g.drawRect(380, 40, 440, 260);
                g.setColor(Color.WHITE);
                g.setFont(new Font("Monospaced", Font.BOLD, 12));
                TextRenderer.render(g, "[WARD ROOM A]", 60, 90);
                TextRenderer.render(g, "[WARD ROOM B]", 1040, 90);
                TextRenderer.render(g, "[MEDICAL STATION]", 470, 30);

                g.setColor(new Color(231, 76, 60, 70));
                g.fill(indoorLeftRooms);
                g.fill(indoorRightRooms);
                for (Rectangle wall : indoorCenterBlockWalls) g.fill(wall);
                for (Rectangle wall : indoorMedicalDeskWalls) g.fill(wall);
                g.fill(indoorTopWall);
                g.fill(indoorBottomWallLeft);
                g.fill(indoorBottomWallRight);

                g.setColor(new Color(0, 255, 255, 180));
                g.fillRect(indoorStairs.x, indoorStairs.y, indoorStairs.width, indoorStairs.height);
                g.setColor(Color.BLACK);
                g.drawRect(indoorStairs.x, indoorStairs.y, indoorStairs.width, indoorStairs.height);
                g.setColor(Color.WHITE);
                g.setFont(new Font("Monospaced", Font.BOLD, 13));
                TextRenderer.render(g, "[STAIRS - PRESS E]", indoorStairs.x + indoorStairs.width + 14,
                        indoorStairs.y - 6);
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
                for (RoadTrafficCar car : roadTraffic) {
                    drawRoadCar(g, car);
                }
                if (supplyTruckActive) drawArrivalTruck(g, supplyTruckSprite, supplyTruckPosX, 592);
                if (garbageTruckActive) drawArrivalTruck(g, garbageTruckSprite, garbageTruckPosX, 592);
                if (hireVehicleActive) drawHireVehicle(g, hireVehiclePosX, 650, hiredRoleName);
                if (stackedTrashCount > 0) drawStackedTrash(g, 340, 435, stackedTrashCount);

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
            renderInteractionPrompt(g);
            renderHud(g);
        }

        private void renderInteractionPrompt(Graphics2D g) {
            String prompt = null;
            Rectangle playerBounds = actorBoundsAt(overseerX, overseerY);
            if (insideBuilding == null) {
                int entrance = nearbyBuildingEntrance(playerBounds);
                if (entrance >= 0) prompt = "E  ENTER " + buildingNames[entrance];
            } else if (playerBounds.intersects(indoorStairs)) {
                prompt = "E  CHANGE FLOOR";
            } else if (playerBounds.intersects(indoorExit)) {
                prompt = "E  EXIT SHELTER";
            }
            if (prompt == null) return;
            Font font = new Font("SansSerif", Font.BOLD, 15);
            g.setFont(font);
            FontMetrics metrics = g.getFontMetrics();
            int width = metrics.stringWidth(prompt) + 24;
            int height = metrics.getHeight() + 12;
            int spriteWidth = overseerSheet == null ? PLAYER_WIDTH
                    : Math.max(1, (int) Math.round(OVERSEER_DRAW_HEIGHT * overseerSheet.getWidth() /
                    (double) overseerSheet.getHeight()));
            int spriteLeft = overseerX + PLAYER_WIDTH / 2 - spriteWidth / 2;
            int spriteTop = overseerY + PLAYER_HEIGHT - OVERSEER_DRAW_HEIGHT;
            int minimumPromptY = insideBuilding == null ? 208 : 104;
            int boxX = overseerX + PLAYER_WIDTH / 2 - width / 2;
            int boxY = spriteTop - height - 12;
            if ("E  CHANGE FLOOR".equals(prompt)) {
                boxX = indoorStairs.x + indoorStairs.width + 14;
                boxY = indoorStairs.y + (indoorStairs.height - height) / 2;
            } else if (boxY < minimumPromptY) {
                int rightX = spriteLeft + spriteWidth + 12;
                boxX = rightX + width <= WORLD_WIDTH - 10
                        ? rightX : spriteLeft - width - 12;
                boxY = spriteTop + (OVERSEER_DRAW_HEIGHT - height) / 2;
            }
            boxX = Math.max(10, Math.min(WORLD_WIDTH - width - 10, boxX));
            boxY = Math.max(minimumPromptY, Math.min(WORLD_HEIGHT - height - 8, boxY));
            g.setColor(new Color(10, 20, 28, 225));
            g.fillRoundRect(boxX, boxY, width, height, 12, 12);
            g.setColor(new Color(244, 153, 75));
            g.drawRoundRect(boxX, boxY, width, height, 12, 12);
            g.setColor(new Color(247, 245, 236));
            g.drawString(prompt, boxX + 12, boxY + 6 + metrics.getAscent());
        }

        private void renderHud(Graphics2D g) {
            if (insideBuilding != null) {
                renderInteriorHud(g);
                return;
            }
            int panelX = 24, panelY = 18, panelWidth = 1152, panelHeight = 140;
            g.setPaint(new GradientPaint(panelX, panelY, new Color(15, 27, 40, 246),
                    panelX, panelY + panelHeight, new Color(20, 36, 49, 238)));
            g.fillRoundRect(panelX, panelY, panelWidth, panelHeight, 14, 14);
            g.setColor(new Color(104, 148, 159, 180));
            g.drawRoundRect(panelX, panelY, panelWidth, panelHeight, 14, 14);
            g.setColor(new Color(244, 153, 75));
            g.fillRoundRect(panelX, panelY, panelWidth, 4, 7, 7);
            g.setFont(new Font("SansSerif", Font.BOLD, 21));
            g.setColor(new Color(244, 244, 236));
            g.drawString("QUARANTINE HAVEN", panelX + 20, panelY + 31);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.setColor(new Color(163, 188, 196));
            g.drawString("OVERSIGHT  /  COMMUNITY STATUS", panelX + 22, panelY + 47);
            String location = insideBuilding == null
                    ? "GROUNDS ACTIVE"
                    : insideBuilding.toUpperCase() + "  ·  FLOOR " + currentFloor;
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            int locationWidth = Math.min(380, g.getFontMetrics().stringWidth(location) + 40);
            int locationX = panelX + panelWidth - locationWidth - 18;
            g.setColor(new Color(27, 53, 62, 230));
            g.fillRoundRect(locationX, panelY + 19, locationWidth, 30, 10, 10);
            g.setColor(new Color(90, 181, 155));
            g.fillOval(locationX + 11, panelY + 30, 8, 8);
            g.setColor(new Color(209, 232, 222));
            drawFittedString(g, location, locationX + 26, panelY + 39,
                    locationWidth - 36, new Font("SansSerif", Font.BOLD, 12));
            g.setColor(new Color(92, 119, 132, 170));
            g.drawLine(panelX + 18, panelY + 57, panelX + panelWidth - 18, panelY + 57);
            String[] metricLabels = {"DAY", "RESIDENTS", "NEXT TRUCK", "SCORE", "SUPPLY POINTS"};
            String[] metricValues = {
                    String.format("%02d", day), String.valueOf(patients.size()),
                    String.format("%02d:%02d", secondsRemaining / 60, secondsRemaining % 60),
                    String.format("%,d", score), String.format("%,d", currencyPoints)
            };
            Color[] metricColors = {
                    new Color(244, 174, 103), new Color(213, 231, 235),
                    new Color(132, 207, 228), new Color(236, 224, 190),
                    new Color(245, 200, 112)
            };
            int metricX = panelX + 18;
            int metricWidth = (panelWidth - 36) / metricLabels.length;
            for (int i = 0; i < metricLabels.length; i++) {
                int x = metricX + i * metricWidth;
                if (i > 0) {
                    g.setColor(new Color(92, 119, 132, 125));
                    g.drawLine(x, panelY + 67, x, panelY + 103);
                }
                g.setFont(new Font("SansSerif", Font.BOLD, 10));
                g.setColor(new Color(151, 177, 188));
                g.drawString(metricLabels[i], x + 16, panelY + 76);
                g.setFont(new Font("SansSerif", Font.BOLD, 20));
                g.setColor(metricColors[i]);
                g.drawString(metricValues[i], x + 16, panelY + 99);
            }
            g.setColor(new Color(92, 119, 132, 150));
            g.drawLine(panelX + 18, panelY + 108, panelX + panelWidth - 18, panelY + 108);
            drawResourceCounter(g, panelX + 22, panelY + 130, "FOOD", food, new Color(109, 198, 146));
            drawResourceCounter(g, panelX + 165, panelY + 130, "WATER", water, new Color(105, 186, 225));
            drawResourceCounter(g, panelX + 308, panelY + 130, "MEDICINE", medicine, new Color(226, 138, 128));
            drawResourceCounter(g, panelX + 475, panelY + 130, "MATERIALS", materials, new Color(215, 184, 126));
            int wasteX = panelX + 731;
            g.setFont(new Font("SansSerif", Font.BOLD, 10));
            g.setColor(new Color(166, 188, 194));
            g.drawString("WASTE", wasteX, panelY + 129);
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.setColor(new Color(231, 237, 230));
            g.drawString(waste + "%  ·  " + stackedTrashCount + " PILES", wasteX + 47, panelY + 130);
            int wasteBarX = wasteX + 190;
            int wasteBarWidth = panelX + panelWidth - 20 - wasteBarX;
            g.setColor(new Color(6, 13, 20, 220));
            g.fillRoundRect(wasteBarX, panelY + 121, wasteBarWidth, 11, 8, 8);
            g.setPaint(new GradientPaint(wasteBarX, panelY + 121, new Color(79, 185, 141),
                    wasteBarX + wasteBarWidth, panelY + 121, new Color(225, 112, 83)));
            g.fillRoundRect(wasteBarX, panelY + 121,
                    Math.max(0, Math.min(wasteBarWidth, wasteBarWidth * waste / 100)), 11, 8, 8);
            g.setColor(new Color(8, 15, 23, 224));
            g.fillRoundRect(24, 169, 620, 30, 9, 9);
            g.setColor(new Color(244, 153, 75));
            g.fillRoundRect(24, 169, 3, 30, 3, 3);
            g.setFont(new Font("SansSerif", Font.BOLD, 10));
            g.setColor(new Color(244, 183, 127));
            g.drawString("LATEST", 39, 188);
            drawFittedString(g, currentEvent, 89, 188, 540,
                    new Font("SansSerif", Font.PLAIN, 10));
            g.setColor(new Color(8, 15, 23, 224));
            g.fillRoundRect(654, 169, 522, 30, 9, 9);
            g.setColor(new Color(107, 177, 196));
            g.fillRoundRect(654, 169, 3, 30, 3, 3);
            g.setFont(new Font("SansSerif", Font.BOLD, 10));
            g.setColor(new Color(151, 177, 188));
            g.drawString("PLAY", 669, 188);
            String controlSummary = "WASD MOVE  ·  E INTERACT  ·  1–3 TASKS  ·  4 DOC  ·  5 RECALL  ·  H HIRE";
            drawFittedString(g, controlSummary, 700, 188, 375,
                    new Font("SansSerif", Font.PLAIN, 9));
            String displayMode = Game.config().graphics().getDisplayMode() == DisplayMode.FULLSCREEN
                    ? "F11 FULLSCREEN" : "F11 WINDOWED";
            g.setFont(new Font("SansSerif", Font.BOLD, 9));
            g.setColor(new Color(151, 177, 188));
            g.drawString(displayMode, 1090, 188);
        }

        private void renderInteriorHud(Graphics2D g) {
            int panelX = 20, panelY = 12, panelWidth = WORLD_WIDTH - 40, panelHeight = 86;
            g.setPaint(new GradientPaint(panelX, panelY, new Color(15, 27, 40, 244),
                    panelX, panelY + panelHeight, new Color(20, 36, 49, 232)));
            g.fillRoundRect(panelX, panelY, panelWidth, panelHeight, 12, 12);
            g.setColor(new Color(104, 148, 159, 170));
            g.drawRoundRect(panelX, panelY, panelWidth, panelHeight, 12, 12);
            g.setColor(new Color(244, 153, 75));
            g.fillRoundRect(panelX, panelY, panelWidth, 3, 6, 6);
            g.setFont(new Font("SansSerif", Font.BOLD, 17));
            g.setColor(new Color(244, 244, 236));
            g.drawString("QUARANTINE HAVEN", panelX + 16, panelY + 24);
            String location = insideBuilding.toUpperCase() + "  ·  FLOOR " + currentFloor;
            drawFittedString(g, location, panelX + panelWidth - 360, panelY + 24, 340,
                    new Font("SansSerif", Font.BOLD, 11));
            String[] labels = {"DAY", "RESIDENTS", "NEXT TRUCK", "SCORE", "SUPPLY POINTS"};
            String[] values = {String.format("%02d", day), String.valueOf(patients.size()),
                    String.format("%02d:%02d", secondsRemaining / 60, secondsRemaining % 60),
                    String.format("%,d", score), String.format("%,d", currencyPoints)};
            Color[] colors = {new Color(244, 174, 103), new Color(213, 231, 235),
                    new Color(132, 207, 228), new Color(236, 224, 190), new Color(245, 200, 112)};
            int columnX = panelX + 16;
            int columnWidth = 500 / labels.length;
            for (int i = 0; i < labels.length; i++) {
                int x = columnX + i * columnWidth;
                g.setFont(new Font("SansSerif", Font.BOLD, 9));
                g.setColor(new Color(151, 177, 188));
                g.drawString(labels[i], x, panelY + 45);
                g.setFont(new Font("SansSerif", Font.BOLD, 14));
                g.setColor(colors[i]);
                g.drawString(values[i], x, panelY + 62);
            }
            drawResourceCounter(g, panelX + 536, panelY + 84, "FOOD", food, new Color(109, 198, 146));
            drawResourceCounter(g, panelX + 667, panelY + 84, "WATER", water, new Color(105, 186, 225));
            drawResourceCounter(g, panelX + 806, panelY + 84, "MEDICINE", medicine, new Color(226, 138, 128));
            drawResourceCounter(g, panelX + 978, panelY + 84, "MATERIALS", materials, new Color(215, 184, 126));
            g.setFont(new Font("SansSerif", Font.BOLD, 9));
            g.setColor(new Color(166, 188, 194));
            g.drawString("WASTE " + waste + "%  ·  " + stackedTrashCount + " PILES", panelX + 536, panelY + 61);
        }

        private void drawResourceCounter(Graphics2D g, int x, int baseline, String label, int value, Color color) {
            g.setColor(color);
            g.fillOval(x, baseline - 8, 7, 7);
            g.setFont(new Font("SansSerif", Font.BOLD, 9));
            g.setColor(new Color(151, 177, 188));
            g.drawString(label, x + 13, baseline - 2);
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.setColor(new Color(229, 237, 235));
            g.drawString(String.valueOf(value), x + 13 + g.getFontMetrics().stringWidth(label) + 7, baseline - 2);
        }

        private void drawFittedString(Graphics2D g, String text, int x, int baseline, int maxWidth, Font font) {
            g.setFont(font);
            FontMetrics metrics = g.getFontMetrics();
            if (metrics.stringWidth(text) <= maxWidth) {
                g.drawString(text, x, baseline);
                return;
            }
            String fitted = text;
            while (!fitted.isEmpty() && metrics.stringWidth(fitted + "…") > maxWidth) {
                fitted = fitted.substring(0, fitted.length() - 1);
            }
            g.drawString(fitted + "…", x, baseline);
        }

        private void renderDoctor(Graphics2D g, int x, int y, String label) {
            if (doctorSheet != null) {
                drawCharacterSprite(g, doctorSheet, x, y, 56);
            } else {
                renderCharacterBadge(g, x, y, Color.GREEN, label, "DOC");
            }
            drawEntityLabel(g, label, x + PLAYER_WIDTH / 2, y + 42, new Color(225, 245, 241));
        }

        private void renderPatientWithSpriteAndHealth(Graphics2D g, int x, int y, String label, String need, float hp, boolean isFemale) {
            BufferedImage sheet = isFemale ? femalePatientSheet : malePatientSheet;
            if (sheet != null) {
                drawCharacterSprite(g, sheet, x, y, 52);
            } else {
                Color badgeColor = need.equals("HEALTHY") ? Color.ORANGE : Color.RED;
                renderCharacterBadge(g, x, y, badgeColor, label, "PAT");
            }
            g.setColor(new Color(9, 16, 24, 220));
            g.fillRoundRect(x - 5, y - 40, 36, 6, 5, 5);
            g.setColor(hp > 50 ? Color.GREEN : (hp > 25 ? Color.YELLOW : Color.RED));
            g.fillRoundRect(x - 4, y - 39, (int) (34 * (Math.max(0, hp) / 100.0f)), 4, 4, 4);
            drawEntityLabel(g, label + " [" + need + "]", x + PLAYER_WIDTH / 2, y + 42,
                    need.equals("HEALTHY") ? new Color(229, 237, 226) : new Color(255, 222, 149));
        }

        private void renderGuardian(Graphics2D g, int x, int y, String label) {
            if (guardianSheet != null) {
                drawCharacterSprite(g, guardianSheet, x, y, 56);
            } else {
                renderCharacterBadge(g, x, y, Color.BLUE, label, "GRD");
            }
            drawEntityLabel(g, label, x + PLAYER_WIDTH / 2, y + 42, new Color(231, 235, 249));
        }

        private void renderOverseer(Graphics2D g, int x, int y) {
            if (overseerSheet != null) {
                drawCharacterSprite(g, overseerSheet, x, y, OVERSEER_DRAW_HEIGHT);
            } else {
                renderCharacterBadge(g, x, y, new Color(241, 196, 15), "OVERSEER", "OSE");
            }
            drawEntityLabel(g, "OVERSEER", x + PLAYER_WIDTH / 2, y + 42, new Color(255, 239, 198));
        }

        private void drawCharacterSprite(Graphics2D g, BufferedImage sprite, int x, int y, int height) {
            double aspect = sprite.getWidth() / (double) sprite.getHeight();
            int width = Math.max(1, (int) Math.round(height * aspect));
            double phase = x * 0.071 + y * 0.037;
            int bob = (int) Math.round(Math.sin(System.nanoTime() / 260_000_000.0 + phase) * 2.0);
            int groundY = y + PLAYER_HEIGHT;
            int drawX = x + PLAYER_WIDTH / 2 - width / 2;
            int drawY = groundY - height + bob;
            g.setColor(new Color(5, 10, 16, 78));
            g.fill(new Ellipse2D.Double(x + 1, groundY - 4, PLAYER_WIDTH - 2, 7));
            g.drawImage(sprite, drawX, drawY, width, height, null);
        }

        private void drawEntityLabel(Graphics2D g, String text, int centerX, int baseline, Color color) {
            g.setFont(new Font("SansSerif", Font.BOLD, 9));
            int textWidth = g.getFontMetrics().stringWidth(text);
            int labelWidth = textWidth + 10;
            int labelHeight = 15;
            int labelX = centerX - labelWidth / 2;
            int labelY = baseline - 10;
            g.setColor(new Color(8, 14, 21, 205));
            g.fillRoundRect(labelX, labelY, labelWidth, labelHeight, 7, 7);
            g.setColor(new Color(255, 255, 255, 42));
            g.drawRoundRect(labelX, labelY, labelWidth, labelHeight, 7, 7);
            g.setColor(color);
            TextRenderer.render(g, text, centerX - textWidth / 2, baseline);
        }

        private void renderCharacterBadge(Graphics2D g, int x, int y, Color color, String label, String tag) {
            g.setColor(new Color(15, 20, 25, 220));
            g.fillOval(x - 2, y - 2, 28, 28);
            g.setColor(color);
            g.fillOval(x, y, 24, 24);
            g.setColor(Color.BLACK);
            g.drawRect(x, y, 24, 24);
            g.setFont(new Font("Monospaced", Font.BOLD, 9));
            g.setColor(Color.BLACK);
            TextRenderer.render(g, tag, x + 3, y + 16);
        }

        private void drawRoadCar(Graphics2D g, RoadTrafficCar car) {
            Graphics2D spriteGraphics = (Graphics2D) g.create();
            if (car.movingRight) {
                spriteGraphics.translate(Math.round(car.x), car.laneY);
                spriteGraphics.scale(car.width / (double) car.sprite.getWidth(),
                        ROAD_CAR_HEIGHT / (double) car.sprite.getHeight());
            } else {
                spriteGraphics.translate(Math.round(car.x) + car.width, car.laneY);
                spriteGraphics.scale(-car.width / (double) car.sprite.getWidth(),
                        ROAD_CAR_HEIGHT / (double) car.sprite.getHeight());
            }
            spriteGraphics.drawImage(car.sprite, 0, 0, null);
            spriteGraphics.dispose();
        }

        private int getArrivalTruckWidth(BufferedImage sprite) {
            if (sprite == null) return 0;
            return Math.max(1, (int) Math.round(ARRIVAL_TRUCK_HEIGHT *
                    sprite.getWidth() / (double) sprite.getHeight()));
        }

        private void drawArrivalTruck(Graphics2D g, BufferedImage sprite, float x, int y) {
            if (sprite == null) return;
            int width = getArrivalTruckWidth(sprite);
            Graphics2D truckGraphics = (Graphics2D) g.create();
            truckGraphics.translate(Math.round(x), y);
            truckGraphics.scale(width / (double) sprite.getWidth(),
                    ARRIVAL_TRUCK_HEIGHT / (double) sprite.getHeight());
            truckGraphics.drawImage(sprite, 0, 0, null);
            truckGraphics.dispose();
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
            int maxItems = Math.min(count, 8);
            if (trashSprite != null) {
                int drawHeight = 22;
                int drawWidth = Math.max(1, (int) Math.round(drawHeight *
                        trashSprite.getWidth() / (double) trashSprite.getHeight()));
                for (int i = 0; i < maxItems; i++) {
                    int offsetX = (i % 3) * (drawWidth - 4);
                    int offsetY = (i / 3) * (drawHeight - 8);
                    g.drawImage(trashSprite, x + offsetX, y - offsetY, drawWidth, drawHeight, null);
                }
            } else {
                g.setColor(new Color(100, 100, 100));
                for (int i = 0; i < maxItems; i++) {
                    int offsetX = (i % 3) * 12;
                    int offsetY = (i / 3) * 10;
                    g.fillOval(x + offsetX, y - offsetY, 14, 12);
                }
            }
        }
    }
}