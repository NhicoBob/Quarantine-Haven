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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

public class Program {
    private static final int WORLD_WIDTH = 1200;
    private static final int WORLD_HEIGHT = 720;
    private static final int ROAD_CAR_HEIGHT = 44;
    private static final double ROAD_CAR_BOUNCE_HZ = 3.7;               // bounces per second (all cars)
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
        private static final int OVERSEER_DRAW_HEIGHT = 56;
        private static final float MOVE_SPEED = 2.5f; // pixels per step; fractions are carried over between steps
        private float moveRemainderX = 0f, moveRemainderY = 0f;

        // BUILDING & FLOOR MANAGEMENT
        private String insideBuilding = null;
        private int currentFloor = 1;

        private BufferedImage mapBgImage;
        private BufferedImage interiorBgImage;
        private BufferedImage overseerSheet;
        // Overseer animation frames (64x64 each): side view faces right, left is mirrored at load time
        private static final int OVERSEER_FRAME_SIZE = 64;
        private static final long OVERSEER_FRAME_NANOS = 130_000_000L;
        private static final long OVERSEER_WALK_HOLD_NANOS = 180_000_000L;
        private static final int FACE_DOWN = 0, FACE_UP = 1, FACE_LEFT = 2, FACE_RIGHT = 3;
        private BufferedImage[] overseerWalkDown, overseerWalkUp, overseerWalkLeft, overseerWalkRight, overseerIdleDown;
        private int overseerFacing = FACE_DOWN;
        private long overseerLastMoveNanos = 0L;
        private long overseerWalkStartNanos = 0L;
        private BufferedImage doctorSheet;
        // Doctor animation frames (64x64 each): side view faces right, left is mirrored at load time
        private static final long DOCTOR_FRAME_NANOS = 150_000_000L;
        private static final long DOCTOR_WALK_HOLD_NANOS = 120_000_000L;
        private BufferedImage[] doctorWalkDown, doctorWalkUp, doctorWalkLeft, doctorWalkRight, doctorIdleDown;
        private BufferedImage guardianSheet;
        // Guard animation frames (64x64 each): side view faces right, left is mirrored at load time
        private static final long GUARD_FRAME_NANOS = 150_000_000L;
        private static final long GUARD_WALK_HOLD_NANOS = 120_000_000L;
        private BufferedImage[] guardWalkDown, guardWalkUp, guardWalkLeft, guardWalkRight, guardIdleDown;
        private BufferedImage femalePatientSheet;
        private BufferedImage malePatientSheet;
        // Male patient animation frames (64x64 each): side view faces right, left is mirrored at load time
        private static final long PATIENT_FRAME_NANOS = 170_000_000L;
        private static final long PATIENT_WALK_HOLD_NANOS = 120_000_000L;
        private BufferedImage[] malePatientWalkDown, malePatientWalkUp, malePatientWalkLeft, malePatientWalkRight, malePatientIdleDown;
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
        private boolean hireDelivered = false; // the new hire spawns exactly once per vehicle
        private boolean interactionKeyDown;
        private boolean displayModeKeyDown;

        private static class RoadTrafficCar {
            float x;
            int width;
            int laneY;
            boolean movingRight;
            BufferedImage sprite;
            long animOffsetNanos;        // random offset so cars don't bounce in sync
        }
        private final List<RoadTrafficCar> roadTraffic = new ArrayList<>();
        private final Random roadTrafficRandom = new Random();
        private BufferedImage[] roadCarSprites;
        private long nextRoadTrafficSpawn;
        private long lastRoadTrafficUpdateNanos = System.nanoTime();

        // Shared base for everything that walks around and uses the nav grids
        private static class NavActor {
            float posX, currentY;
            boolean isInside = false;
            int floor = 1;
            NavAgent nav = new NavAgent();
        }

        private static class NavAgent {
            List<int[]> path = null;
            int pathIndex = 0;
            boolean hasGoal = false;
            boolean goalInside = false;
            float goalX, goalY;
            boolean unreachable = false;
            boolean placed = false;       // spawn point validated?
            long planNanos = 0L;
            long holdUntilMillis = 0L;    // brief pause after taking stairs
        }

        private static class NavGrid {
            int cols, rows;
            boolean[][] walkable;
        }

        private static class DoctorData extends NavActor {
            float targetX, targetY;
            int state = 0;
            int facing = FACE_DOWN;
            long lastMoveNanos = 0L;
            long walkStartNanos = 0L;
            long treatStartMillis = 0L;   // != 0 while the doctor is standing with a patient, treating them
            long waitStartMillis = 0L;    // when the doctor started waiting in a room for the patient to arrive
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

        private static class PatientData extends NavActor {
            int id;
            float targetX, targetY;
            boolean destInside;
            int destFloor;
            int facing = FACE_DOWN;
            long lastMoveNanos = 0L;
            long walkStartNanos = 0L;
            float roomX, roomY;          // where this patient stands in their ward room
            int roomFloor = 1;
            int roomIndex = -1;          // -1 = no bed assigned yet (see assignRoom)
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
                this.targetX = startX;
                this.targetY = startY;
                this.destInside = isInside;
                this.destFloor = floor;
            }
        }
        private List<PatientData> patients = new ArrayList<>();

        private static class GuardData extends NavActor {
            float targetX, targetY;
            int taskState = 0;
            int facing = FACE_DOWN;
            long lastMoveNanos = 0L;
            long walkStartNanos = 0L;
            boolean roamPicked = false;
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
        private static final String STORAGE_ROOM_NAME = "STORAGE ROOM";
        private final String[] buildingNames = {"PATIENT WARD 1", "PATIENT WARD 2", STORAGE_ROOM_NAME}; // 3rd building = storage room
        private final Rectangle[] lowerBuildingWalls = {
                new Rectangle(132, 382, 58, 116),
                new Rectangle(234, 382, 56, 116)
        };
        private final Rectangle bottomWallLeft = new Rectangle(74, 470, 486, 100);
        private final Rectangle bottomWallRight = new Rectangle(886, 470, 234, 100);
        private final Rectangle leftWall = new Rectangle(74, 42, 48, 428);
        private final Rectangle rightFence = new Rectangle(1104, 42, 48, 428);
        // ---------- WARD INTERIOR (interior_map.png) ----------
        // The art has 7 small ward rooms down each side wall (each with a doorway on its inner edge), a 6-room medical
        // block in the middle, an UP staircase on the left and a DOWN staircase on the right. Rooms repeat every 101px.
        private static final int WARD_BAND_TOP = 8;
        private static final int WARD_BAND_PERIOD = 101;
        private static final int WARD_DOOR_START = 54;   // doorway = band top + 54 ... + 84 (30px tall)
        private static final int WARD_DOOR_END = 84;
        // room numbers: 0-6 left wall, 7-13 right wall, 14-16 centre block (west column), 17-19 centre block (east column).
        // Bed order: the top row (0, 7, 14, 17) sits behind the HUD panel, so those rooms are used last.
        private static final int[] WARD_ROOM_ORDER = {1, 8, 2, 9, 3, 10, 4, 11, 5, 12, 6, 13, 15, 18, 16, 19, 0, 7, 14, 17};
        private final Rectangle[] indoorSolids = buildIndoorSolids();
        private final Rectangle indoorStairsUp = new Rectangle(225, 112, 150, 44);
        private final Rectangle indoorStairsDown = new Rectangle(820, 112, 155, 44);
        private final Rectangle indoorExit = new Rectangle(550, 654, 100, 56);

        private static int wardBandTop(int band) {
            return WARD_BAND_TOP + WARD_BAND_PERIOD * band;
        }

        private static Rectangle[] buildIndoorSolids() {
            List<Rectangle> r = new ArrayList<>();
            r.add(new Rectangle(0, 0, WORLD_WIDTH, 10));                    // top wall
            r.add(new Rectangle(0, 708, WORLD_WIDTH, 12));                  // bottom wall
            r.add(new Rectangle(0, 0, 6, WORLD_HEIGHT));                    // left outer wall
            r.add(new Rectangle(WORLD_WIDTH - 6, 0, 6, WORLD_HEIGHT));      // right outer wall
            for (int k = 0; k < 7; k++) {
                int t = wardBandTop(k);
                // left ward rooms: divider below the room, inner wall with a doorway
                r.add(new Rectangle(0, t + 92, 118, 5));
                r.add(new Rectangle(112, t, 6, WARD_DOOR_START));
                r.add(new Rectangle(112, t + WARD_DOOR_END, 6, 13));
                // right ward rooms (mirror image)
                r.add(new Rectangle(WORLD_WIDTH - 118, t + 92, 118, 5));
                r.add(new Rectangle(WORLD_WIDTH - 118, t, 6, WARD_DOOR_START));
                r.add(new Rectangle(WORLD_WIDTH - 118, t + WARD_DOOR_END, 6, 13));
            }
            for (int k = 0; k < 3; k++) {                                   // centre medical block: doors face outward
                int t = wardBandTop(k);
                r.add(new Rectangle(483, t, 7, WARD_DOOR_START));
                r.add(new Rectangle(483, t + WARD_DOOR_END, 7, 13));
                r.add(new Rectangle(707, t, 7, WARD_DOOR_START));
                r.add(new Rectangle(707, t + WARD_DOOR_END, 7, 13));
                if (k < 2) r.add(new Rectangle(483, t + 92, 231, 5));
            }
            r.add(new Rectangle(483, wardBandTop(2) + 92, 231, 9));         // bottom wall of the block
            r.add(new Rectangle(597, 8, 5, 295));                           // divider between its two columns
            r.add(new Rectangle(225, 0, 150, 114));                         // UP staircase
            r.add(new Rectangle(820, 0, 155, 114));                         // DOWN staircase
            r.add(new Rectangle(538, 340, 126, 48));                        // medical station counter
            r.add(new Rectangle(495, 652, 55, 36));                         // exit console
            r.add(new Rectangle(472, 324, 16, 24));                         // plants beside the medical block
            r.add(new Rectangle(718, 324, 16, 24));
            // (the corridor plants beside the ward doorways are decoration only: they must not block a door)
            return r.toArray(new Rectangle[0]);
        }

        /** Where a doctor stands while tending the patient in a room: beside them, further into the room. */
        private static float[] roomDoctorSpot(int room) {
            float[] spot = roomStandSpot(room);
            if (room < 7) return new float[]{spot[0] - 28f, spot[1]};
            if (room < 14) return new float[]{spot[0] + 28f, spot[1]};
            if (room < 17) return new float[]{spot[0] + 30f, spot[1]};
            return new float[]{spot[0] - 30f, spot[1]};
        }

        /** Where a patient stands inside a ward room (top-left of the 26x26 actor box), clear of the bed and the doorway. */
        private static float[] roomStandSpot(int room) {
            if (room < 7) return new float[]{60f, wardBandTop(room) + 52f};
            if (room < 14) return new float[]{WORLD_WIDTH - 60f - 26f, wardBandTop(room - 7) + 52f};
            if (room < 17) return new float[]{515f, wardBandTop(room - 14) + 52f};
            return new float[]{655f, wardBandTop(room - 17) + 52f};
        }
        private final float door1X = 194;
        private final float door1Y = 315;
        private final float door2X = 394;
        private final float door2Y = 315;

        // ---------- STORAGE ROOM (3rd building) ----------
        // Rectangles below are in the ORIGINAL 1411x736 artwork's pixel coordinates; storageRect() maps them onto the
        // 1200x720 world (the art is scaled to full width and sits below a 94px strip that the HUD covers).
        private static final double STORAGE_IMG_SCALE = 1200.0 / 1411.0;
        private static final int STORAGE_IMG_TOP = 94;
        private static final int STORAGE_CAPACITY = 500;                 // max stored per item
        private static final String[] STORAGE_ITEM_NAMES = {"FOOD", "WATER", "MEDICINE", "MATERIALS"};
        private static final String[] STORAGE_BAY_CODES = {"E01", "E18", "E15", "E16"}; // labels printed on the racks
        private static final Color[] STORAGE_ITEM_COLORS = {
                new Color(109, 198, 146), new Color(105, 186, 225), new Color(226, 138, 128), new Color(215, 184, 126)};
        private BufferedImage storageBgImage;
        private final int[] storedStock = new int[4];                    // what is in the storage room
        private final boolean[] autoRestock = new boolean[4];            // issue stock to the supply line automatically
        private final int[] restockBelow = {120, 120, 60, 40};           // ...whenever on-hand falls below this
        private boolean storagePanelOpen = false;
        private boolean storagePanelManage = false;                      // true = inventory terminal, false = rack
        private int[] storagePanelItems = {0, 1, 2, 3};
        private int storagePanelRow = 0;
        private long storagePanelLastKeyMillis = 0L;
        private String storageLastAction = "";

        private Rectangle storageRect(int x, int y, int w, int h) {
            return new Rectangle((int) Math.round(x * STORAGE_IMG_SCALE), STORAGE_IMG_TOP + (int) Math.round(y * STORAGE_IMG_SCALE),
                    (int) Math.round(w * STORAGE_IMG_SCALE), (int) Math.round(h * STORAGE_IMG_SCALE));
        }

        private final Rectangle[] storageSolids = {
                new Rectangle(0, 0, 1200, STORAGE_IMG_TOP),                   // HUD strip
                storageRect(0, 0, 170, 736), storageRect(1285, 0, 126, 736),  // left / right walls (pipes, pillars, side racks)
                storageRect(170, 0, 1115, 85), storageRect(538, 75, 56, 62), storageRect(898, 80, 120, 20), // top shelves & pallets
                storageRect(250, 170, 150, 168),                              // pallet cluster
                storageRect(528, 150, 62, 160),                               // pallet stack
                storageRect(423, 272, 116, 90),                               // forklift 1
                storageRect(588, 0, 112, 335), storageRect(793, 0, 106, 335), // tall racks (E01/E18, E15/E16)
                storageRect(898, 160, 62, 175),                               // crates beside rack
                storageRect(1075, 178, 156, 162),                             // crates + small forklift (right)
                storageRect(248, 440, 182, 164),                              // crate cluster (lower left)
                storageRect(440, 515, 116, 86),                               // forklift 2
                storageRect(562, 436, 142, 166),                              // gas cylinder, pallets, shelf
                storageRect(797, 440, 206, 166),                              // shelving (lower middle)
                storageRect(1085, 440, 166, 166),                             // crates (lower right)
                storageRect(222, 668, 203, 70), storageRect(424, 648, 98, 90), // bottom-left pallets & shelf
                storageRect(612, 684, 94, 52),                                // control console
                storageRect(820, 668, 20, 62),                                // post
                storageRect(873, 656, 76, 80),                                // inventory terminals
                storageRect(1030, 662, 48, 76), storageRect(1083, 658, 60, 80), storageRect(1148, 646, 98, 92) // bottom-right
        };
        private final Rectangle[] storageRackZones = {storageRect(578, 332, 130, 70), storageRect(783, 332, 130, 70)};
        private final int[] storageRackCenterX = {(int) Math.round(643 * STORAGE_IMG_SCALE), (int) Math.round(846 * STORAGE_IMG_SCALE)};
        private final Rectangle storageTerminalZone = storageRect(850, 610, 125, 50);
        private final Rectangle storageExitZone = storageRect(700, 650, 95, 90);
        private final int storageSpawnX = (int) Math.round(745 * STORAGE_IMG_SCALE) - PLAYER_WIDTH / 2;
        private final int storageSpawnY = STORAGE_IMG_TOP + (int) Math.round(635 * STORAGE_IMG_SCALE) - PLAYER_HEIGHT;

        public ShelterScreen() {
            super("SHELTER_SCREEN");
            mapBgImage = loadImage("/map_bg.png");
            interiorBgImage = loadImage("/interior_map.png");
            storageBgImage = loadImage("/storage_room.png");
            overseerSheet = trimTransparent(loadImage("/overseer_sprite.png"));
            overseerWalkDown = loadFrames("/overseer_walk_down.png", false);
            overseerWalkUp = loadFrames("/overseer_walk_up.png", false);
            overseerWalkRight = loadFrames("/overseer_walk_side.png", false);
            overseerWalkLeft = loadFrames("/overseer_walk_side.png", true);
            overseerIdleDown = loadFrames("/overseer_idle_down.png", false);
            doctorSheet = trimTransparent(loadImage("/doctor_sprite.png"));
            doctorWalkDown = loadFrames("/doctor_walk_down.png", false);
            doctorWalkUp = loadFrames("/doctor_walk_up.png", false);
            doctorWalkRight = loadFrames("/doctor_walk_side.png", false);
            doctorWalkLeft = loadFrames("/doctor_walk_side.png", true);
            doctorIdleDown = loadFrames("/doctor_idle_down.png", false);
            guardianSheet = trimTransparent(loadImage("/guardian_sprite.png"));
            guardWalkDown = loadFrames("/guard_walk_down.png", false);
            guardWalkUp = loadFrames("/guard_walk_up.png", false);
            guardWalkRight = loadFrames("/guard_walk_side.png", false);
            guardWalkLeft = loadFrames("/guard_walk_side.png", true);
            guardIdleDown = loadFrames("/guard_idle_down.png", false);
            femalePatientSheet = trimTransparent(loadImage("/female_patient.png"));
            malePatientSheet = trimTransparent(loadImage("/male_patient.png"));
            malePatientWalkDown = loadFrames("/male_patient_walk_down.png", false);
            malePatientWalkUp = loadFrames("/male_patient_walk_up.png", false);
            malePatientWalkRight = loadFrames("/male_patient_walk_side.png", false);
            malePatientWalkLeft = loadFrames("/male_patient_walk_side.png", true);
            malePatientIdleDown = loadFrames("/male_patient_idle_down.png", false);
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

            doctors.add(new DoctorData(300, 430, false, 1)); // was inside a wall
            doctors.add(new DoctorData(400, 450, true, 2)); // lobby (old spot was inside a room block)

            // Moved left guard further down into open walkway area (Y=445)
            guards.add(new GuardData(208, 440)); // was inside the lower-building wall
            guards.add(new GuardData(600, 450));

            // While a storage panel is open, W/A/S/D/arrows/R drive the panel instead of walking
            Input.keyboard().onKeyPressed(KeyEvent.VK_W, e -> { if (storagePanelOpen) storagePanelKey(KeyEvent.VK_W, e.isShiftDown()); else moveOverseer(0, -1); });
            Input.keyboard().onKeyPressed(KeyEvent.VK_S, e -> { if (storagePanelOpen) storagePanelKey(KeyEvent.VK_S, e.isShiftDown()); else moveOverseer(0, 1); });
            Input.keyboard().onKeyPressed(KeyEvent.VK_A, e -> { if (storagePanelOpen) storagePanelKey(KeyEvent.VK_A, e.isShiftDown()); else moveOverseer(-1, 0); });
            Input.keyboard().onKeyPressed(KeyEvent.VK_D, e -> { if (storagePanelOpen) storagePanelKey(KeyEvent.VK_D, e.isShiftDown()); else moveOverseer(1, 0); });
            Input.keyboard().onKeyPressed(KeyEvent.VK_UP, e -> { if (storagePanelOpen) storagePanelKey(KeyEvent.VK_UP, e.isShiftDown()); });
            Input.keyboard().onKeyPressed(KeyEvent.VK_DOWN, e -> { if (storagePanelOpen) storagePanelKey(KeyEvent.VK_DOWN, e.isShiftDown()); });
            Input.keyboard().onKeyPressed(KeyEvent.VK_LEFT, e -> { if (storagePanelOpen) storagePanelKey(KeyEvent.VK_LEFT, e.isShiftDown()); });
            Input.keyboard().onKeyPressed(KeyEvent.VK_RIGHT, e -> { if (storagePanelOpen) storagePanelKey(KeyEvent.VK_RIGHT, e.isShiftDown()); });
            Input.keyboard().onKeyPressed(KeyEvent.VK_R, e -> { if (storagePanelOpen) storagePanelKey(KeyEvent.VK_R, e.isShiftDown()); });
            Input.keyboard().onKeyPressed(KeyEvent.VK_E, e -> {
                if (!interactionKeyDown) {
                    interactionKeyDown = true;
                    interactWithLocation();
                }
            });
            Input.keyboard().onKeyReleased(KeyEvent.VK_E, e -> interactionKeyDown = false);
            Input.keyboard().onKeyPressed(KeyEvent.VK_ESCAPE, e -> {
                if (storagePanelOpen) storagePanelOpen = false;
                else exitBuilding();
            });
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

        private BufferedImage[] loadFrames(String path, boolean mirrored) {
            BufferedImage sheet = loadImage(path);
            if (sheet == null) return null;
            int count = Math.max(1, sheet.getWidth() / OVERSEER_FRAME_SIZE);
            BufferedImage[] frames = new BufferedImage[count];
            for (int i = 0; i < count; i++) {
                BufferedImage frame = sheet.getSubimage(i * OVERSEER_FRAME_SIZE, 0,
                        OVERSEER_FRAME_SIZE, Math.min(OVERSEER_FRAME_SIZE, sheet.getHeight()));
                frames[i] = mirrored ? flipHorizontally(frame) : frame;
            }
            return frames;
        }

        private boolean canMoveTo(int x, int y) {
            Rectangle player = actorBoundsAt(x, y);
            if (player.x < 0 || player.y < 0 || player.x + player.width > WORLD_WIDTH ||
                    player.y + player.height > WORLD_HEIGHT) {
                return false;
            }
            if (isStorageRoom()) {
                return !intersectsAny(player, storageSolids);
            }
            if (insideBuilding != null) {
                return !intersectsAny(player, indoorSolids);
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

        private void moveOverseer(int dirX, int dirY) {
            if (storagePanelOpen) return;
            // Accumulate fractional speed (e.g. 2.5 px) and move by the whole pixels that have built up
            moveRemainderX += dirX * MOVE_SPEED;
            moveRemainderY += dirY * MOVE_SPEED;
            int dx = (int) moveRemainderX;
            int dy = (int) moveRemainderY;
            moveRemainderX -= dx;
            moveRemainderY -= dy;
            int nextX = overseerX + dx;
            int nextY = overseerY + dy;
            int startX = overseerX, startY = overseerY;
            if (canMoveTo(nextX, overseerY)) overseerX = nextX;
            if (canMoveTo(overseerX, nextY)) overseerY = nextY;

            // Turn to face the pressed direction (even if a wall blocks the step)
            if (dirX < 0) overseerFacing = FACE_LEFT;
            else if (dirX > 0) overseerFacing = FACE_RIGHT;
            else if (dirY < 0) overseerFacing = FACE_UP;
            else if (dirY > 0) overseerFacing = FACE_DOWN;

            if (overseerX != startX || overseerY != startY) {
                long now = System.nanoTime();
                // Restart the walk cycle on the first step so it always begins on frame 0
                if (now - overseerLastMoveNanos > OVERSEER_WALK_HOLD_NANOS) overseerWalkStartNanos = now;
                overseerLastMoveNanos = now;
            }
        }

        private BufferedImage currentOverseerFrame() {
            long now = System.nanoTime();
            boolean walking = now - overseerLastMoveNanos <= OVERSEER_WALK_HOLD_NANOS;
            BufferedImage[] frames;
            switch (overseerFacing) {
                case FACE_UP: frames = overseerWalkUp; break;
                case FACE_LEFT: frames = overseerWalkLeft; break;
                case FACE_RIGHT: frames = overseerWalkRight; break;
                default: frames = walking ? overseerWalkDown : overseerIdleDown; break;
            }
            if (frames == null || frames.length == 0) return null;
            if (walking) {
                int index = (int) (((now - overseerWalkStartNanos) / OVERSEER_FRAME_NANOS) % frames.length);
                return frames[index];
            }
            if (overseerFacing == FACE_DOWN) {
                // Idle (front view): slow loop through the idle frames
                return frames[(int) ((now / 400_000_000L) % frames.length)];
            }
            return frames[0]; // standing still while facing side/back: first frame of that direction
        }

        private void interactWithLocation() {
            Rectangle playerBounds = actorBoundsAt(overseerX, overseerY);
            if (storagePanelOpen) {
                storagePanelOpen = false;
                return;
            }
            if (isStorageRoom()) {
                interactInStorageRoom(playerBounds);
                return;
            }
            if (insideBuilding != null) {
                if (playerBounds.intersects(indoorStairsUp)) {
                    if (currentFloor < 3) {
                        currentFloor++;
                        currentEvent = "Went UP to Floor " + currentFloor + " of " + insideBuilding + ".";
                    } else {
                        currentEvent = "You are already on the top floor. Use the DOWN stairs (right) to go back down.";
                    }
                    return;
                }
                if (playerBounds.intersects(indoorStairsDown)) {
                    if (currentFloor > 1) {
                        currentFloor--;
                        currentEvent = "Went DOWN to Floor " + currentFloor + " of " + insideBuilding + ".";
                    } else {
                        currentEvent = "You are on the ground floor. Use the UP stairs (left), or the exit door at the bottom.";
                    }
                    return;
                }
                if (playerBounds.intersects(indoorExit)) {
                    exitBuilding();
                } else {
                    currentEvent = "UP stairs are on the left, DOWN stairs on the right; the exit door is at the bottom.";
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
            storagePanelOpen = false;
            if (STORAGE_ROOM_NAME.equals(buildingName)) {
                overseerX = storageSpawnX;
                overseerY = storageSpawnY;
                currentEvent = "Entered the storage room. Use the racks to store/take stock and the terminal to manage it.";
                return;
            }
            overseerX = 584;
            overseerY = 620;
            currentEvent = "Entered " + buildingName + " [Floor 1]. Walk to cyan stairs to climb floors!";
        }

        private void exitBuilding() {
            if (insideBuilding != null) {
                insideBuilding = null;
                storagePanelOpen = false;
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
            }
            currentEvent = "Order [5]: Patients walking back to their visible rooms!";
        }

        private void hireStaffMember(String role, int cost) {
            if (hireVehicleActive) {
                currentEvent = "A recruitment vehicle is already on its way!";
                return;
            }
            if (currencyPoints >= cost) {
                currencyPoints -= cost;
                hiredRoleName = role;
                hireDelivered = false;
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
                    rect.y + rect.height > WORLD_HEIGHT || intersectsAny(rect, indoorSolids);
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
            car.animOffsetNanos = (long) roadTrafficRandom.nextInt(1000) * 1_000_000L;
            roadTraffic.add(car);
            return true;
        }

        private void updateGameLoop() {
            if (hireVehicleActive) {
                hireVehiclePosX += 8;
                if (hireVehiclePosX >= 550 && !hireDelivered) {
                    hireDelivered = true;
                    if (hiredRoleName.equals("Doctor")) {
                        doctors.add(new DoctorData(580, 480, true, 1));
                    } else {
                        guards.add(new GuardData(580, 480));
                    }
                    currentEvent = "New " + hiredRoleName + " arrived and entered facility through door!";
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
                updatePatientMovement(p);
            }

            if (!deadPatients.isEmpty()) {
                patients.removeAll(deadPatients);
                currentEvent = "WARNING: A patient died due to neglect! Score penalty applied (-100).";
            }

            for (DoctorData d : doctors) {
                updateDoctor(d);
            }

            for (GuardData g : guards) {
                updateGuard(g);
            }

            long currentTime = System.currentTimeMillis();
            if (currentTime - lastTickTime >= 1000) {
                lastTickTime = currentTime;
                runStorageAutoRestock();
                if (secondsRemaining > 0) {
                    secondsRemaining--;
                } else {
                    triggerDawnEvent();
                }
            }
        }

        // ---------- NPC PATHFINDING (grid A*, shared by guards, doctors and patients) ----------
        private static final int NAV_CELL = 8;
        private static final float NAV_REPLAN_DISTANCE = 16f;
        private static final long NAV_RETRY_NANOS = 500_000_000L;
        private static final int PORTAL_NONE = 0, PORTAL_DOOR_IN = 1, PORTAL_STAIRS_UP = 2, PORTAL_EXIT = 3, PORTAL_STAIRS_DOWN = 4;
        // spots just below each staircase (top-left of the actor box) that fall inside the stairs' interaction zone
        private static final float STAIRS_UP_GOAL_X = 272f, STAIRS_DOWN_GOAL_X = 884f, STAIRS_GOAL_Y = 114f;
        private static final float EXIT_GOAL_X = 584f, EXIT_GOAL_Y = 655f;
        private final Random navRandom = new Random();
        private static final long TREAT_MILLIS = 2000L;   // how long a doctor tends a patient before they are cured
        private static final int INDOOR_ROAM_X = 170, INDOOR_ROAM_W = 860, INDOOR_ROAM_Y = 330, INDOOR_ROAM_H = 300; // open lobby
        private NavGrid outdoorNav, indoorNav;

        private boolean collidesIn(boolean inside, float x, float y) {
            return inside ? indoorCollides(x, y) : outdoorCollides(x, y);
        }

        private NavGrid buildNavGrid(boolean inside) {
            NavGrid grid = new NavGrid();
            grid.cols = WORLD_WIDTH / NAV_CELL + 1;
            grid.rows = WORLD_HEIGHT / NAV_CELL + 1;
            grid.walkable = new boolean[grid.cols][grid.rows];
            for (int cx = 0; cx < grid.cols; cx++) {
                for (int cy = 0; cy < grid.rows; cy++) {
                    grid.walkable[cx][cy] = !collidesIn(inside, cx * NAV_CELL, cy * NAV_CELL);
                }
            }
            return grid;
        }

        private void ensureNavGrids() {
            if (outdoorNav != null) return;
            outdoorNav = buildNavGrid(false);
            indoorNav = buildNavGrid(true);
        }

        private NavGrid navGrid(boolean inside) {
            ensureNavGrids();
            return inside ? indoorNav : outdoorNav;
        }

        private boolean navFree(NavGrid grid, int cx, int cy) {
            return cx >= 0 && cy >= 0 && cx < grid.cols && cy < grid.rows && grid.walkable[cx][cy];
        }

        /** Closest walkable cell to (cx, cy), or null if none is nearby. */
        private int[] nearestWalkableCell(NavGrid grid, int cx, int cy) {
            if (navFree(grid, cx, cy)) return new int[]{cx, cy};
            int[] best = null;
            int bestDist = Integer.MAX_VALUE;
            for (int r = 1; r <= 40; r++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dy = -r; dy <= r; dy++) {
                        if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                        if (!navFree(grid, cx + dx, cy + dy)) continue;
                        int d = dx * dx + dy * dy;
                        if (d < bestDist) {
                            bestDist = d;
                            best = new int[]{cx + dx, cy + dy};
                        }
                    }
                }
                if (best != null) return best;
            }
            return null;
        }

        private int navHeuristic(int ax, int ay, int bx, int by) {
            int dx = Math.abs(ax - bx), dy = Math.abs(ay - by);
            return 10 * (dx + dy) - 6 * Math.min(dx, dy); // octile distance
        }

        /** A* over a walkable grid. Returns waypoint cells (start..goal) or null if unreachable. */
        private List<int[]> findNavPath(NavGrid grid, float fromX, float fromY, float toX, float toY) {
            int[] start = nearestWalkableCell(grid, Math.round(fromX / NAV_CELL), Math.round(fromY / NAV_CELL));
            int[] goal = nearestWalkableCell(grid, Math.round(toX / NAV_CELL), Math.round(toY / NAV_CELL));
            if (start == null || goal == null) return null;

            int rows = grid.rows;
            int total = grid.cols * rows;
            int[] gCost = new int[total];
            int[] parent = new int[total];
            boolean[] closed = new boolean[total];
            Arrays.fill(gCost, Integer.MAX_VALUE);
            Arrays.fill(parent, -1);
            int startIdx = start[0] * rows + start[1];
            int goalIdx = goal[0] * rows + goal[1];

            PriorityQueue<int[]> open = new PriorityQueue<>((a, b) -> Integer.compare(a[1], b[1]));
            gCost[startIdx] = 0;
            open.add(new int[]{startIdx, navHeuristic(start[0], start[1], goal[0], goal[1])});

            while (!open.isEmpty()) {
                int idx = open.poll()[0];
                if (closed[idx]) continue;
                closed[idx] = true;
                if (idx == goalIdx) break;
                int cx = idx / rows, cy = idx % rows;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = cx + dx, ny = cy + dy;
                        if (!navFree(grid, nx, ny)) continue;
                        boolean diagonal = dx != 0 && dy != 0;
                        // No cutting corners around walls
                        if (diagonal && (!navFree(grid, cx + dx, cy) || !navFree(grid, cx, cy + dy))) continue;
                        int nIdx = nx * rows + ny;
                        if (closed[nIdx]) continue;
                        int ng = gCost[idx] + (diagonal ? 14 : 10);
                        if (ng < gCost[nIdx]) {
                            gCost[nIdx] = ng;
                            parent[nIdx] = idx;
                            open.add(new int[]{nIdx, ng + navHeuristic(nx, ny, goal[0], goal[1])});
                        }
                    }
                }
            }
            if (goalIdx != startIdx && parent[goalIdx] == -1) return null;

            List<int[]> path = new ArrayList<>();
            for (int idx = goalIdx; idx != -1; idx = parent[idx]) {
                path.add(new int[]{idx / rows, idx % rows});
                if (idx == startIdx) break;
            }
            Collections.reverse(path);
            return path;
        }

        /** Random point in the given box that is actually walkable (falls back to the last try). */
        private float[] randomSpot(Random rand, boolean inside, int x0, int w, int y0, int h) {
            NavGrid grid = navGrid(inside);
            float tx = x0, ty = y0;
            for (int i = 0; i < 25; i++) {
                tx = x0 + rand.nextInt(w);
                ty = y0 + rand.nextInt(h);
                if (navFree(grid, Math.round(tx / NAV_CELL), Math.round(ty / NAV_CELL))) break;
            }
            return new float[]{tx, ty};
        }

        /** Moves an actor that spawned inside a wall onto the nearest walkable cell (once). */
        private void placeActor(NavActor a) {
            if (a.nav.placed) return;
            a.nav.placed = true;
            if (collidesIn(a.isInside, a.posX, a.currentY)) {
                int[] c = nearestWalkableCell(navGrid(a.isInside), Math.round(a.posX / NAV_CELL),
                        Math.round(a.currentY / NAV_CELL));
                if (c != null) {
                    a.posX = c[0] * NAV_CELL;
                    a.currentY = c[1] * NAV_CELL;
                }
            }
        }

        private void resetNav(NavAgent nav) {
            nav.path = null;
            nav.pathIndex = 0;
            nav.hasGoal = false;
            nav.unreachable = false;
        }

        /**
         * One tick of walking toward (gx, gy) in the given space. Plans a path when the goal is new or has
         * moved, then follows it at constant speed, snapping exactly onto each waypoint (no overshoot wobble).
         * pos = {x, y} is updated in place. Returns true once the end of the path has been reached.
         */
        private boolean walkToward(NavAgent nav, float[] pos, boolean inside, float gx, float gy, float speed) {
            NavGrid grid = navGrid(inside);
            long now = System.nanoTime();
            boolean goalChanged = !nav.hasGoal || nav.goalInside != inside
                    || Math.abs(nav.goalX - gx) > NAV_REPLAN_DISTANCE
                    || Math.abs(nav.goalY - gy) > NAV_REPLAN_DISTANCE;
            if (goalChanged || (nav.path == null && now - nav.planNanos > NAV_RETRY_NANOS)) {
                nav.hasGoal = true;
                nav.goalInside = inside;
                nav.goalX = gx;
                nav.goalY = gy;
                nav.planNanos = now;
                nav.path = findNavPath(grid, pos[0], pos[1], gx, gy);
                nav.pathIndex = 0;
                nav.unreachable = nav.path == null;
                if (nav.path != null && nav.path.size() > 1) {
                    // Skip the first cell if we are already past it, so a re-plan never causes a back-step
                    int[] c0 = nav.path.get(0), c1 = nav.path.get(1);
                    double cellGap = Math.hypot((c0[0] - c1[0]) * NAV_CELL, (c0[1] - c1[1]) * NAV_CELL);
                    double posGap = Math.hypot(pos[0] - c1[0] * NAV_CELL, pos[1] - c1[1] * NAV_CELL);
                    if (posGap <= cellGap) nav.pathIndex = 1;
                }
            }
            if (nav.path == null) return false;

            float startX = pos[0], startY = pos[1];
            boolean startedBlocked = collidesIn(inside, startX, startY);
            float left = speed;
            while (nav.pathIndex < nav.path.size() && left > 0f) {
                int[] cell = nav.path.get(nav.pathIndex);
                float tx = cell[0] * NAV_CELL, ty = cell[1] * NAV_CELL;
                float dx = tx - pos[0], dy = ty - pos[1];
                float dist = (float) Math.hypot(dx, dy);
                if (dist <= left) {
                    pos[0] = tx;
                    pos[1] = ty;
                    left -= dist;
                    nav.pathIndex++;
                } else {
                    pos[0] += dx / dist * left;
                    pos[1] += dy / dist * left;
                    left = 0f;
                }
            }
            // Safety: never end a step inside a wall
            if (!startedBlocked && collidesIn(inside, pos[0], pos[1])) {
                pos[0] = startX;
                pos[1] = startY;
                nav.path = null;
                nav.planNanos = now;
                return false;
            }
            return nav.pathIndex >= nav.path.size();
        }

        private float nearestDoorX(float x) {
            float[] doors = {door1X, door2X}; // the 3rd building is the storage room: NPCs never enter it
            float best = doors[0];
            for (float d : doors) {
                if (Math.abs(x - d) < Math.abs(x - best)) best = d;
            }
            return best;
        }

        private float randomDoorX() {
            float[] doors = {door1X, door2X};
            return doors[navRandom.nextInt(doors.length)];
        }

        /**
         * Walks an actor toward a destination that may be in another space or on another floor: it heads for
         * the nearest ward door, the stairs, or the exit as needed, and passes through them.
         * Returns true only when the actor has reached the final destination.
         */
        private boolean travelTo(NavActor a, boolean targetInside, int targetFloor, float tx, float ty, float speed) {
            ensureNavGrids();
            placeActor(a);
            NavAgent nav = a.nav;
            if (System.currentTimeMillis() < nav.holdUntilMillis) return false;

            float gx = tx, gy = ty;
            int portal = PORTAL_NONE;
            if (a.isInside == targetInside) {
                if (a.isInside && a.floor != targetFloor) {
                    boolean up = targetFloor > a.floor;
                    gx = up ? STAIRS_UP_GOAL_X : STAIRS_DOWN_GOAL_X;
                    gy = STAIRS_GOAL_Y;
                    portal = up ? PORTAL_STAIRS_UP : PORTAL_STAIRS_DOWN;
                }
            } else if (!a.isInside) {
                gx = nearestDoorX(a.posX);
                gy = door1Y;
                portal = PORTAL_DOOR_IN;
            } else if (a.floor != 1) {
                gx = STAIRS_DOWN_GOAL_X;
                gy = STAIRS_GOAL_Y;
                portal = PORTAL_STAIRS_DOWN;
            } else {
                gx = EXIT_GOAL_X;
                gy = EXIT_GOAL_Y;
                portal = PORTAL_EXIT;
            }

            float[] pos = {a.posX, a.currentY};
            boolean arrived = walkToward(nav, pos, a.isInside, gx, gy, speed);
            a.posX = pos[0];
            a.currentY = pos[1];
            if (!arrived) return false;

            switch (portal) {
                case PORTAL_DOOR_IN:
                    a.isInside = true;
                    a.floor = 1;
                    a.posX = 584;
                    a.currentY = 620;
                    resetNav(nav);
                    return false;
                case PORTAL_STAIRS_UP:
                    a.floor = Math.min(3, a.floor + 1);
                    nav.holdUntilMillis = System.currentTimeMillis() + 700;
                    resetNav(nav);
                    return false;
                case PORTAL_STAIRS_DOWN:
                    a.floor = Math.max(1, a.floor - 1);
                    nav.holdUntilMillis = System.currentTimeMillis() + 700;
                    resetNav(nav);
                    return false;
                case PORTAL_EXIT:
                    a.isInside = false;
                    a.posX = randomDoorX();
                    a.currentY = door1Y + 30;
                    resetNav(nav);
                    return false;
                default:
                    return true;
            }
        }

        // ---------- GUARDS ----------
        private void updateGuard(GuardData g) {
            ensureNavGrids();
            placeActor(g);

            float goalX, goalY;
            if (g.taskState == 2) {
                goalX = 720;
                goalY = 430;
            } else if (g.taskState == 3) {
                goalX = 320;
                goalY = 440;
            } else {
                if (!g.roamPicked || System.currentTimeMillis() - g.roamTimer > 7000) {
                    g.roamPicked = true;
                    g.roamTimer = System.currentTimeMillis();
                    float[] spot = randomSpot(g.rand, false, 200, 750, 410, 90);
                    g.targetX = spot[0];
                    g.targetY = spot[1];
                }
                goalX = g.targetX;
                goalY = g.targetY;
            }

            float step = (g.taskState > 0) ? 1.5f : 0.6f;
            float startX = g.posX, startY = g.currentY;
            float[] pos = {startX, startY};
            boolean arrived = walkToward(g.nav, pos, false, goalX, goalY, step);
            g.posX = pos[0];
            g.currentY = pos[1];

            if (g.nav.unreachable && g.taskState != 0) {
                g.taskState = 0; // unreachable job: drop it instead of pushing against a wall
                g.roamPicked = false;
                resetNav(g.nav);
                return;
            }

            // Facing / animation state, with hysteresis so diagonal steps don't flicker
            float movedX = g.posX - startX, movedY = g.currentY - startY;
            if (movedX != 0f || movedY != 0f) {
                float ax = Math.abs(movedX), ay = Math.abs(movedY);
                if (ax > ay * 1.25f) g.facing = movedX < 0 ? FACE_LEFT : FACE_RIGHT;
                else if (ay > ax * 1.25f) g.facing = movedY < 0 ? FACE_UP : FACE_DOWN;
                long now = System.nanoTime();
                if (now - g.lastMoveNanos > GUARD_WALK_HOLD_NANOS) g.walkStartNanos = now;
                g.lastMoveNanos = now;
            }

            // Task hand-offs when the guard has reached the end of its path
            if (g.taskState == 2 && (arrived || (Math.abs(g.posX - 720) <= 6f && Math.abs(g.currentY - 430) <= 6f))) {
                g.taskState = 3;
                resetNav(g.nav);
            } else if (g.taskState == 3 && (arrived || (Math.abs(g.posX - 320) <= 6f && Math.abs(g.currentY - 440) <= 6f))) {
                stackedTrashCount++;
                g.taskState = 0;
                g.roamPicked = false;
                resetNav(g.nav);
            }
        }

        // ---------- PATIENTS ----------
        private void updatePatientMovement(PatientData p) {
            float startX = p.posX, startY = p.currentY;
            boolean startInside = p.isInside;
            int startFloor = p.floor;
            updatePatientMovementLogic(p);

            // Facing / animation state (ignore door and stairs hops, which move the patient instantly)
            if (p.isInside != startInside || p.floor != startFloor) return;
            float movedX = p.posX - startX, movedY = p.currentY - startY;
            if ((movedX != 0f || movedY != 0f) && Math.abs(movedX) < 12f && Math.abs(movedY) < 12f) {
                float ax = Math.abs(movedX), ay = Math.abs(movedY);
                if (ax > ay * 1.25f) p.facing = movedX < 0 ? FACE_LEFT : FACE_RIGHT;
                else if (ay > ax * 1.25f) p.facing = movedY < 0 ? FACE_UP : FACE_DOWN;
                long now = System.nanoTime();
                if (now - p.lastMoveNanos > PATIENT_WALK_HOLD_NANOS) p.walkStartNanos = now;
                p.lastMoveNanos = now;
            }
        }

        private boolean isRoomTaken(int floor, int room, PatientData except) {
            for (PatientData other : patients) {
                if (other != except && other.roomIndex == room && other.roomFloor == floor) return true;
            }
            return false;
        }

        /** Gives a patient their own ward room: fills floor 1 first, then 2 and 3 (shares beds only if all are taken). */
        private void assignRoom(PatientData p) {
            if (p.roomIndex >= 0) return;
            for (int floor = 1; floor <= 3; floor++) {
                for (int room : WARD_ROOM_ORDER) {
                    if (!isRoomTaken(floor, room, p)) {
                        setPatientRoom(p, floor, room);
                        return;
                    }
                }
            }
            int shared = (p.id - 1) % (3 * WARD_ROOM_ORDER.length);
            setPatientRoom(p, shared / WARD_ROOM_ORDER.length + 1, WARD_ROOM_ORDER[shared % WARD_ROOM_ORDER.length]);
        }

        private void setPatientRoom(PatientData p, int floor, int room) {
            p.roomFloor = floor;
            p.roomIndex = room;
            float[] spot = roomStandSpot(room);
            p.roomX = spot[0];
            p.roomY = spot[1];
        }

        private boolean isPatientBeingTended(PatientData patient) {
            for (DoctorData d : doctors) {
                if (d.state == 1 && d.targetPatient == patient && d.treatStartMillis != 0L) return true;
            }
            return false;
        }

        private void sendPatientToDestination(PatientData p, boolean inside, int floor, float[] spot) {
            p.destInside = inside;
            p.destFloor = floor;
            p.targetX = spot[0];
            p.targetY = spot[1];
        }

        private void updatePatientMovementLogic(PatientData p) {
            long nowMs = System.currentTimeMillis();
            assignRoom(p);
            if (isPatientBeingTended(p)) return;            // stands still while a doctor works on them

            // Patients who need care (or were recalled with [5]) go to their own bed and wait there for a doctor
            if (p.orderedToRoom || !p.need.equals("HEALTHY")) {
                boolean inRoom = travelTo(p, true, p.roomFloor, p.roomX, p.roomY, 1.8f); // hurry: sick patients lose health
                sendPatientToDestination(p, true, p.roomFloor, new float[]{p.roomX, p.roomY});
                if (inRoom && p.orderedToRoom) {
                    p.orderedToRoom = false;
                    p.roamTimer = nowMs + 14000;            // stay in the room for a while
                }
                return;
            }

            if (nowMs - p.roamTimer > 6000) {
                p.roamTimer = nowMs;
                double r = p.rand.nextDouble();
                if (r < 0.40) {                              // rest in their own room
                    sendPatientToDestination(p, true, p.roomFloor, new float[]{p.roomX, p.roomY});
                } else {
                    double r2 = (r - 0.40) / 0.60;
                    if (p.isInside) {
                        if (r2 < 0.15) {                     // head outside
                            sendPatientToDestination(p, false, 1, randomSpot(p.rand, false, 180, 780, 345, 110));
                        } else {                             // wander the lobby, sometimes on another floor
                            int floor = r2 < 0.30 ? 1 + p.rand.nextInt(3) : p.floor;
                            sendPatientToDestination(p, true, floor, randomSpot(p.rand, true, INDOOR_ROAM_X, INDOOR_ROAM_W, INDOOR_ROAM_Y, INDOOR_ROAM_H));
                        }
                    } else if (r2 < 0.5) {                   // go into the ward
                        sendPatientToDestination(p, true, 1 + p.rand.nextInt(3), randomSpot(p.rand, true, INDOOR_ROAM_X, INDOOR_ROAM_W, INDOOR_ROAM_Y, INDOOR_ROAM_H));
                    } else {                                 // wander the courtyard
                        sendPatientToDestination(p, false, 1, randomSpot(p.rand, false, 180, 780, 345, 110));
                    }
                }
            }
            travelTo(p, p.destInside, p.destFloor, p.targetX, p.targetY, 0.5f);
        }

        // ---------- DOCTORS ----------
        private boolean isTargetedByOtherDoctor(PatientData patient, DoctorData self) {
            for (DoctorData other : doctors) {
                if (other != self && other.state == 1 && other.targetPatient == patient) return true;
            }
            return false;
        }

        private void updateDoctor(DoctorData d) {
            float startX = d.posX, startY = d.currentY;
            boolean startInside = d.isInside;
            int startFloor = d.floor;
            updateDoctorLogic(d);

            // Facing / animation state (ignore door and stairs hops, which move the doctor instantly)
            if (d.isInside != startInside || d.floor != startFloor) return;
            float movedX = d.posX - startX, movedY = d.currentY - startY;
            if ((movedX != 0f || movedY != 0f) && Math.abs(movedX) < 12f && Math.abs(movedY) < 12f) {
                float ax = Math.abs(movedX), ay = Math.abs(movedY);
                if (ax > ay * 1.25f) d.facing = movedX < 0 ? FACE_LEFT : FACE_RIGHT;
                else if (ay > ax * 1.25f) d.facing = movedY < 0 ? FACE_UP : FACE_DOWN;
                long now = System.nanoTime();
                if (now - d.lastMoveNanos > DOCTOR_WALK_HOLD_NANOS) d.walkStartNanos = now;
                d.lastMoveNanos = now;
            }
        }

        private void updateDoctorLogic(DoctorData d) {
            long nowMs = System.currentTimeMillis();

            // Patient died / was removed while the doctor was on the way
            if (d.state == 1 && (d.targetPatient == null || !patients.contains(d.targetPatient))) {
                d.state = 0;
                d.targetPatient = null;
                d.treatStartMillis = 0L;
            }
            if (d.state == 0) {
                // Pick the patient who can be treated soonest: already in their room first, then the weakest
                PatientData best = null;
                float bestScore = Float.MAX_VALUE;
                for (PatientData p : patients) {
                    if (p.need.equals("HEALTHY") || isTargetedByOtherDoctor(p, d)) continue;
                    assignRoom(p);
                    boolean inRoom = p.isInside && p.floor == p.roomFloor
                            && Math.hypot(p.posX - p.roomX, p.currentY - p.roomY) <= 24f;
                    float score = (inRoom ? -1000f : 0f) + p.hp;
                    if (score < bestScore) {
                        bestScore = score;
                        best = p;
                    }
                }
                if (best != null) {
                    d.state = 1;
                    d.targetPatient = best;
                }
            }

            if (d.state == 1 && d.targetPatient != null) {
                PatientData tp = d.targetPatient;
                if (d.treatStartMillis == 0L) {
                    // Go to the patient's ward room and wait beside the bed for them to get there
                    assignRoom(tp);
                    float[] spot = roomDoctorSpot(tp.roomIndex);
                    boolean arrived = travelTo(d, true, tp.roomFloor, spot[0], spot[1], 1.5f);
                    boolean patientInRoom = tp.isInside && tp.floor == tp.roomFloor
                            && Math.hypot(tp.posX - tp.roomX, tp.currentY - tp.roomY) <= 24f;
                    if (arrived) {
                        if (d.waitStartMillis == 0L) d.waitStartMillis = nowMs;
                        if (patientInRoom) {
                            d.treatStartMillis = nowMs;
                            d.waitStartMillis = 0L;
                            currentEvent = "Doctor is tending to patient P#" + tp.id + "...";
                        } else if (nowMs - d.waitStartMillis > 20000L) {
                            d.state = 0;                   // patient never made it: free the doctor for someone else
                            d.targetPatient = null;
                            d.waitStartMillis = 0L;
                        }
                    } else {
                        d.waitStartMillis = 0L;
                    }
                } else if (nowMs - d.treatStartMillis >= TREAT_MILLIS) {
                    // Treatment finished
                    String curedNeed = tp.need;
                    if (curedNeed.equals("HUNGRY")) food = Math.max(0, food - 15);
                    else if (curedNeed.equals("THIRSTY")) water = Math.max(0, water - 15);
                    else if (curedNeed.equals("SICK")) medicine = Math.max(0, medicine - 10);
                    tp.need = "HEALTHY";
                    tp.hp = Math.min(100f, tp.hp + 40f);
                    tp.needTimer = nowMs;
                    tp.roamTimer = nowMs;                  // rest a few seconds before wandering off again
                    d.state = 0;
                    d.targetPatient = null;
                    d.treatStartMillis = 0L;
                    d.targetX = d.posX;
                    d.targetY = d.currentY;
                    score += 35;
                    currentEvent = "Doctor treated patient P#" + tp.id + "! Supplies consumed & HP restored.";
                }
            } else {
                if (nowMs - d.roamTimer > 6000) {
                    d.roamTimer = nowMs;
                    float[] spot = d.isInside ? randomSpot(d.rand, true, 180, 780, 280, 220)
                            : randomSpot(d.rand, false, 180, 780, 345, 110);
                    d.targetX = spot[0];
                    d.targetY = spot[1];
                }
                travelTo(d, d.isInside, d.floor, d.targetX, d.targetY, 0.6f);
            }
        }

        // ---------- STORAGE ROOM: logic ----------
        private boolean isStorageRoom() {
            return STORAGE_ROOM_NAME.equals(insideBuilding);
        }

        private int getOnHand(int item) {
            switch (item) {
                case 0: return food;
                case 1: return water;
                case 2: return medicine;
                default: return materials;
            }
        }

        private void setOnHand(int item, int value) {
            switch (item) {
                case 0: food = value; break;
                case 1: water = value; break;
                case 2: medicine = value; break;
                default: materials = value; break;
            }
        }

        /** Moves stock between the supply line ("on hand") and the storage room. amount > 0 stores, amount < 0 takes. */
        private void transferStock(int item, int amount) {
            String name = STORAGE_ITEM_NAMES[item];
            if (amount > 0) {
                int moved = Math.min(amount, Math.min(Math.max(0, getOnHand(item)), STORAGE_CAPACITY - storedStock[item]));
                if (moved <= 0) {
                    storageLastAction = storedStock[item] >= STORAGE_CAPACITY
                            ? name + " storage is full (" + STORAGE_CAPACITY + ")." : "No " + name + " on hand to store.";
                } else {
                    setOnHand(item, getOnHand(item) - moved);
                    storedStock[item] += moved;
                    storageLastAction = "Stored " + moved + " " + name + ".";
                }
            } else {
                int moved = Math.min(-amount, storedStock[item]);
                if (moved <= 0) {
                    storageLastAction = "No " + name + " in storage to take.";
                } else {
                    storedStock[item] -= moved;
                    setOnHand(item, getOnHand(item) + moved);
                    storageLastAction = "Took " + moved + " " + name + " out of storage.";
                }
            }
            currentEvent = storageLastAction;
        }

        /** Once a second: top up any item with auto-restock on whenever the supply line runs below its level. */
        private void runStorageAutoRestock() {
            for (int item = 0; item < storedStock.length; item++) {
                if (!autoRestock[item] || storedStock[item] <= 0 || getOnHand(item) >= restockBelow[item]) continue;
                int moved = Math.min(storedStock[item], Math.min(20, restockBelow[item] - getOnHand(item)));
                if (moved <= 0) continue;
                storedStock[item] -= moved;
                setOnHand(item, getOnHand(item) + moved);
                storageLastAction = "Auto-restock: issued " + moved + " " + STORAGE_ITEM_NAMES[item] + " from storage.";
            }
        }

        private void openStoragePanel(boolean manage, int[] items, int startRow) {
            storagePanelOpen = true;
            storagePanelManage = manage;
            storagePanelItems = items;
            storagePanelRow = Math.max(0, Math.min(items.length - 1, startRow));
            storageLastAction = "";
        }

        private int storageRackSide(int rackIndex, Rectangle playerBounds) {
            return playerBounds.getCenterX() < storageRackCenterX[rackIndex] ? 0 : 1;
        }

        private void interactInStorageRoom(Rectangle playerBounds) {
            if (playerBounds.intersects(storageExitZone)) {
                exitBuilding();
            } else if (playerBounds.intersects(storageTerminalZone)) {
                openStoragePanel(true, new int[]{0, 1, 2, 3}, 0);
            } else if (playerBounds.intersects(storageRackZones[0])) {
                openStoragePanel(false, new int[]{0, 1}, storageRackSide(0, playerBounds));
            } else if (playerBounds.intersects(storageRackZones[1])) {
                openStoragePanel(false, new int[]{2, 3}, storageRackSide(1, playerBounds));
            } else {
                currentEvent = "Stand at a rack (E01-E16) to store/take stock, at the terminal to manage it, or at the exit.";
            }
        }

        private String storagePromptFor(Rectangle playerBounds) {
            if (playerBounds.intersects(storageExitZone)) return "E  EXIT STORAGE ROOM";
            if (playerBounds.intersects(storageTerminalZone)) return "E  INVENTORY TERMINAL";
            for (int rack = 0; rack < 2; rack++) {
                if (playerBounds.intersects(storageRackZones[rack])) {
                    int item = rack * 2 + storageRackSide(rack, playerBounds);
                    return "E  RACK " + STORAGE_BAY_CODES[item] + "  ·  " + STORAGE_ITEM_NAMES[item];
                }
            }
            return null;
        }

        /** Keys while a storage panel is open: W/S select, D store, A take, R auto-restock, arrows adjust the level. */
        private void storagePanelKey(int keyCode, boolean shift) {
            long now = System.currentTimeMillis();
            if (now - storagePanelLastKeyMillis < 110) return; // tame OS key-repeat
            storagePanelLastKeyMillis = now;
            int rows = storagePanelItems.length;
            int item = storagePanelItems[storagePanelRow];
            int amount = shift ? 50 : 10;
            switch (keyCode) {
                case KeyEvent.VK_W:
                case KeyEvent.VK_UP:
                    storagePanelRow = (storagePanelRow - 1 + rows) % rows;
                    break;
                case KeyEvent.VK_S:
                case KeyEvent.VK_DOWN:
                    storagePanelRow = (storagePanelRow + 1) % rows;
                    break;
                case KeyEvent.VK_D:
                    transferStock(item, amount);
                    break;
                case KeyEvent.VK_A:
                    transferStock(item, -amount);
                    break;
                case KeyEvent.VK_R:
                    if (storagePanelManage) {
                        autoRestock[item] = !autoRestock[item];
                        storageLastAction = "Auto-restock for " + STORAGE_ITEM_NAMES[item] + (autoRestock[item] ? " ON." : " OFF.");
                    }
                    break;
                case KeyEvent.VK_LEFT:
                    if (storagePanelManage) {
                        restockBelow[item] = Math.max(0, restockBelow[item] - amount);
                        storageLastAction = "Restock level for " + STORAGE_ITEM_NAMES[item] + ": " + restockBelow[item] + ".";
                    }
                    break;
                case KeyEvent.VK_RIGHT:
                    if (storagePanelManage) {
                        restockBelow[item] = Math.min(STORAGE_CAPACITY, restockBelow[item] + amount);
                        storageLastAction = "Restock level for " + STORAGE_ITEM_NAMES[item] + ": " + restockBelow[item] + ".";
                    }
                    break;
                default:
                    break;
            }
        }

        // ---------- STORAGE ROOM: drawing ----------
        private void renderStorageRoom(Graphics2D g) {
            if (storageBgImage != null) {
                g.drawImage(storageBgImage, 0, 0, WORLD_WIDTH, WORLD_HEIGHT, null);
            } else {
                g.setColor(new Color(60, 66, 74));
                g.fillRect(0, 0, WORLD_WIDTH, WORLD_HEIGHT);
            }
            // stock tags on the four rack bays (the E01/E18/E15/E16 plates printed in the artwork)
            int[] bayCenterX = {609, 673, 812, 876};
            for (int item = 0; item < 4; item++) {
                Rectangle tagArea = storageRect(bayCenterX[item] - 27, 276, 54, 30);
                renderStockTag(g, tagArea, item);
            }
            // terminal label
            Rectangle terminal = storageRect(873, 656, 76, 80);
            g.setFont(new Font("SansSerif", Font.BOLD, 10));
            String label = "INVENTORY TERMINAL";
            int labelWidth = g.getFontMetrics().stringWidth(label) + 14;
            int labelX = terminal.x + terminal.width / 2 - labelWidth / 2;
            int labelY = terminal.y - 38;
            g.setColor(new Color(10, 20, 28, 215));
            g.fillRoundRect(labelX, labelY, labelWidth, 17, 8, 8);
            g.setColor(new Color(244, 153, 75));
            g.drawRoundRect(labelX, labelY, labelWidth, 17, 8, 8);
            g.setColor(new Color(247, 245, 236));
            g.drawString(label, labelX + 7, labelY + 12);
            renderOverseer(g, overseerX, overseerY);
        }

        private void renderStockTag(Graphics2D g, Rectangle area, int item) {
            g.setColor(new Color(10, 20, 28, 215));
            g.fillRoundRect(area.x, area.y, area.width, area.height, 7, 7);
            g.setColor(STORAGE_ITEM_COLORS[item]);
            g.drawRoundRect(area.x, area.y, area.width, area.height, 7, 7);
            g.setFont(new Font("SansSerif", Font.BOLD, 8));
            String name = STORAGE_ITEM_NAMES[item];
            g.drawString(name, area.x + (area.width - g.getFontMetrics().stringWidth(name)) / 2, area.y + 10);
            g.setFont(new Font("SansSerif", Font.BOLD, 11));
            g.setColor(new Color(236, 241, 238));
            String amount = String.valueOf(storedStock[item]);
            g.drawString(amount, area.x + (area.width - g.getFontMetrics().stringWidth(amount)) / 2, area.y + 23);
        }

        private void renderStoragePanel(Graphics2D g) {
            int rows = storagePanelItems.length;
            int rowH = storagePanelManage ? 84 : 66;
            int panelW = 640;
            int panelH = 54 + rows * rowH + (storagePanelManage ? 84 : 66);
            int panelX = (WORLD_WIDTH - panelW) / 2;
            int panelY = Math.max(112, (WORLD_HEIGHT + 100 - panelH) / 2);
            g.setColor(new Color(0, 0, 0, 110));
            g.fillRect(0, 100, WORLD_WIDTH, WORLD_HEIGHT - 100);
            g.setPaint(new GradientPaint(panelX, panelY, new Color(15, 27, 40, 250), panelX, panelY + panelH, new Color(20, 36, 49, 248)));
            g.fillRoundRect(panelX, panelY, panelW, panelH, 16, 16);
            g.setColor(new Color(104, 148, 159, 190));
            g.drawRoundRect(panelX, panelY, panelW, panelH, 16, 16);
            g.setColor(new Color(244, 153, 75));
            g.fillRoundRect(panelX, panelY, panelW, 4, 8, 8);

            g.setFont(new Font("SansSerif", Font.BOLD, 18));
            g.setColor(new Color(244, 244, 236));
            String title = storagePanelManage ? "INVENTORY TERMINAL"
                    : "STOCK RACK  ·  " + STORAGE_BAY_CODES[storagePanelItems[0]] + " / " + STORAGE_BAY_CODES[storagePanelItems[1]];
            g.drawString(title, panelX + 22, panelY + 32);
            g.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g.setColor(new Color(151, 177, 188));
            g.drawString("Capacity " + STORAGE_CAPACITY + " per item", panelX + panelW - 140, panelY + 30);

            for (int row = 0; row < rows; row++) {
                int item = storagePanelItems[row];
                int ry = panelY + 46 + row * rowH;
                boolean selected = row == storagePanelRow;
                g.setColor(selected ? new Color(36, 58, 70, 235) : new Color(24, 40, 52, 200));
                g.fillRoundRect(panelX + 14, ry, panelW - 28, rowH - 8, 10, 10);
                g.setColor(selected ? new Color(244, 153, 75) : new Color(70, 96, 110, 160));
                g.drawRoundRect(panelX + 14, ry, panelW - 28, rowH - 8, 10, 10);

                g.setColor(STORAGE_ITEM_COLORS[item]);
                g.fillOval(panelX + 30, ry + 14, 10, 10);
                g.setFont(new Font("SansSerif", Font.BOLD, 15));
                g.setColor(new Color(236, 241, 238));
                g.drawString(STORAGE_ITEM_NAMES[item], panelX + 48, ry + 24);
                g.setFont(new Font("SansSerif", Font.PLAIN, 9));
                g.setColor(new Color(151, 177, 188));
                g.drawString("BAY " + STORAGE_BAY_CODES[item], panelX + 48, ry + 37);

                g.setFont(new Font("SansSerif", Font.BOLD, 9));
                g.setColor(new Color(151, 177, 188));
                g.drawString("ON HAND", panelX + 200, ry + 17);
                g.drawString("STORED", panelX + 330, ry + 17);
                g.setFont(new Font("SansSerif", Font.BOLD, 20));
                g.setColor(new Color(229, 237, 235));
                g.drawString(String.valueOf(getOnHand(item)), panelX + 200, ry + 38);
                g.setColor(new Color(244, 183, 127));
                g.drawString(storedStock[item] + " / " + STORAGE_CAPACITY, panelX + 330, ry + 38);

                int barX = panelX + 48, barW = panelW - 28 - 48 - 8, barY = ry + 45;
                g.setColor(new Color(6, 13, 20, 220));
                g.fillRoundRect(barX, barY, barW, 8, 6, 6);
                g.setColor(STORAGE_ITEM_COLORS[item]);
                g.fillRoundRect(barX, barY, Math.max(0, Math.min(barW, barW * storedStock[item] / STORAGE_CAPACITY)), 8, 6, 6);

                if (storagePanelManage) {
                    boolean on = autoRestock[item];
                    g.setFont(new Font("SansSerif", Font.BOLD, 10));
                    g.setColor(on ? new Color(109, 198, 146) : new Color(151, 177, 188));
                    g.drawString("AUTO-RESTOCK " + (on ? "ON" : "OFF"), panelX + 48, ry + 70);
                    g.setFont(new Font("SansSerif", Font.PLAIN, 10));
                    g.setColor(new Color(163, 188, 196));
                    g.drawString("issue stock from storage when on-hand drops below " + restockBelow[item], panelX + 170, ry + 70);
                }
            }

            int fy = panelY + 46 + rows * rowH + 6;
            g.setFont(new Font("SansSerif", Font.BOLD, 10));
            g.setColor(new Color(244, 183, 127));
            g.drawString("W / S  SELECT     D  STORE 10     A  TAKE 10     HOLD SHIFT  x50", panelX + 24, fy + 12);
            if (storagePanelManage) {
                g.drawString("R  AUTO-RESTOCK ON/OFF     LEFT / RIGHT  RESTOCK LEVEL  (SHIFT  x50)", panelX + 24, fy + 29);
            }
            g.setColor(new Color(151, 177, 188));
            g.drawString("E / ESC  CLOSE", panelX + panelW - 104, fy + 12);
            if (!storageLastAction.isEmpty()) {
                g.setFont(new Font("SansSerif", Font.BOLD, 11));
                g.setColor(new Color(236, 241, 238));
                drawFittedString(g, storageLastAction, panelX + 24, fy + (storagePanelManage ? 50 : 34), panelW - 48,
                        new Font("SansSerif", Font.BOLD, 11));
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
            if (isStorageRoom()) {
                renderStorageRoom(g);
                renderInteractionPrompt(g);
                renderHud(g);
                if (storagePanelOpen) renderStoragePanel(g);
                return;
            }
            if (insideBuilding != null) {
                if (interiorBgImage != null) {
                    g.drawImage(interiorBgImage, 0, 0, screenWidth, screenHeight, null);
                } else {
                    g.setColor(new Color(35, 42, 50));
                    g.fillRect(0, 0, screenWidth, screenHeight);
                }
                for (PatientData p : patients) {
                    if (p.isInside && p.floor == currentFloor) {
                        renderPatientWithSpriteAndHealth(g, (int) p.posX, (int) p.currentY, "P#" + p.id, p.need, p.hp, p.isFemale, p);
                    }
                }
                for (DoctorData doc : doctors) {
                    if (doc.isInside && doc.floor == currentFloor) {
                        renderDoctor(g, (int) doc.posX, (int) doc.currentY, "Doctor", doc);
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
                        renderPatientWithSpriteAndHealth(g, (int) p.posX, (int) p.currentY, "P#" + p.id, p.need, p.hp, p.isFemale, p);
                    }
                }
                for (DoctorData d : doctors) {
                    if (!d.isInside) {
                        renderDoctor(g, (int) d.posX, (int) d.currentY, "Doctor", d);
                    }
                }
                for (GuardData gData : guards) {
                    renderGuardian(g, (int) gData.posX, (int) gData.currentY, "Guard", gData);
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
            } else if (isStorageRoom()) {
                if (!storagePanelOpen) prompt = storagePromptFor(playerBounds);
            } else if (playerBounds.intersects(indoorStairsUp)) {
                prompt = currentFloor < 3 ? "E  GO UP  ·  FLOOR " + (currentFloor + 1) : "E  TOP FLOOR";
            } else if (playerBounds.intersects(indoorStairsDown)) {
                prompt = currentFloor > 1 ? "E  GO DOWN  ·  FLOOR " + (currentFloor - 1) : "E  GROUND FLOOR";
            } else if (playerBounds.intersects(indoorExit)) {
                prompt = "E  EXIT SHELTER";
            }
            if (prompt == null) return;
            Font font = new Font("SansSerif", Font.BOLD, 15);
            g.setFont(font);
            FontMetrics metrics = g.getFontMetrics();
            int width = metrics.stringWidth(prompt) + 24;
            int height = metrics.getHeight() + 12;
            BufferedImage promptFrame = currentOverseerFrame();
            BufferedImage widthSource = promptFrame != null ? promptFrame : overseerSheet;
            int spriteWidth = widthSource == null ? PLAYER_WIDTH
                    : Math.max(1, (int) Math.round(OVERSEER_DRAW_HEIGHT * widthSource.getWidth() /
                    (double) widthSource.getHeight()));
            int spriteLeft = overseerX + PLAYER_WIDTH / 2 - spriteWidth / 2;
            int spriteTop = overseerY + PLAYER_HEIGHT - OVERSEER_DRAW_HEIGHT;
            int minimumPromptY = insideBuilding == null ? 208 : 104;
            int boxX = overseerX + PLAYER_WIDTH / 2 - width / 2;
            int boxY = spriteTop - height - 12;
            if (boxY < minimumPromptY) {
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
            drawResourceCounter(g, panelX + 22, panelY + 130, "FOOD", food, new Color(109, 198, 146), storedStock[0], 143);
            drawResourceCounter(g, panelX + 165, panelY + 130, "WATER", water, new Color(105, 186, 225), storedStock[1], 143);
            drawResourceCounter(g, panelX + 308, panelY + 130, "MEDICINE", medicine, new Color(226, 138, 128), storedStock[2], 167);
            drawResourceCounter(g, panelX + 475, panelY + 130, "MATERIALS", materials, new Color(215, 184, 126), storedStock[3], 250);
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
            String location = isStorageRoom() ? insideBuilding.toUpperCase()
                    : insideBuilding.toUpperCase() + "  ·  FLOOR " + currentFloor;
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
            drawResourceCounter(g, panelX + 536, panelY + 84, "FOOD", food, new Color(109, 198, 146), storedStock[0], 131);
            drawResourceCounter(g, panelX + 667, panelY + 84, "WATER", water, new Color(105, 186, 225), storedStock[1], 139);
            drawResourceCounter(g, panelX + 806, panelY + 84, "MEDICINE", medicine, new Color(226, 138, 128), storedStock[2], 172);
            drawResourceCounter(g, panelX + 978, panelY + 84, "MATERIALS", materials, new Color(215, 184, 126), storedStock[3], 200);
            g.setFont(new Font("SansSerif", Font.BOLD, 9));
            g.setColor(new Color(166, 188, 194));
            g.drawString("WASTE " + waste + "%  ·  " + stackedTrashCount + " PILES", panelX + 536, panelY + 61);
            if (isStorageRoom()) {
                g.setColor(new Color(8, 15, 23, 224));
                g.fillRoundRect(panelX, panelY + panelHeight + 8, 620, 28, 9, 9);
                g.setColor(new Color(244, 153, 75));
                g.fillRoundRect(panelX, panelY + panelHeight + 8, 3, 28, 3, 3);
                g.setFont(new Font("SansSerif", Font.BOLD, 10));
                g.setColor(new Color(244, 183, 127));
                g.drawString("LATEST", panelX + 15, panelY + panelHeight + 26);
                g.setColor(new Color(229, 237, 235));
                drawFittedString(g, currentEvent, panelX + 65, panelY + panelHeight + 26, 540,
                        new Font("SansSerif", Font.PLAIN, 10));
            }
        }

        private void drawResourceCounter(Graphics2D g, int x, int baseline, String label, int value, Color color) {
            drawResourceCounter(g, x, baseline, label, value, color, 0, 0);
        }

        /** Like above, plus a small "+N stored" note (or just "+N" if the column is tight) when some of the item is in storage. */
        private void drawResourceCounter(Graphics2D g, int x, int baseline, String label, int value, Color color,
                                         int stored, int columnWidth) {
            g.setColor(color);
            g.fillOval(x, baseline - 8, 7, 7);
            g.setFont(new Font("SansSerif", Font.BOLD, 9));
            g.setColor(new Color(151, 177, 188));
            g.drawString(label, x + 13, baseline - 2);
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.setColor(new Color(229, 237, 235));
            int valueX = x + 13 + g.getFontMetrics().stringWidth(label) + 7;
            g.drawString(String.valueOf(value), valueX, baseline - 2);
            if (stored > 0) {
                int valueWidth = g.getFontMetrics().stringWidth(String.valueOf(value));
                g.setFont(new Font("SansSerif", Font.BOLD, 9));
                g.setColor(new Color(244, 183, 127));
                int noteX = valueX + valueWidth + 6;
                String note = "+" + stored + " stored";
                if (noteX + g.getFontMetrics().stringWidth(note) > x + columnWidth - 6) note = "+" + stored;
                g.drawString(note, noteX, baseline - 2);
            }
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

        private BufferedImage currentDoctorFrame(DoctorData doctor) {
            long now = System.nanoTime();
            boolean walking = now - doctor.lastMoveNanos <= DOCTOR_WALK_HOLD_NANOS;
            BufferedImage[] frames;
            switch (doctor.facing) {
                case FACE_UP: frames = doctorWalkUp; break;
                case FACE_LEFT: frames = doctorWalkLeft; break;
                case FACE_RIGHT: frames = doctorWalkRight; break;
                default: frames = walking ? doctorWalkDown : doctorIdleDown; break;
            }
            if (frames == null || frames.length == 0) return null;
            if (walking) return frames[(int) (((now - doctor.walkStartNanos) / DOCTOR_FRAME_NANOS) % frames.length)];
            if (doctor.facing == FACE_DOWN) return frames[(int) ((now / 400_000_000L) % frames.length)];
            return frames[0];
        }

        private void renderDoctor(Graphics2D g, int x, int y, String label, DoctorData doctor) {
            BufferedImage frame = doctor == null ? null : currentDoctorFrame(doctor);
            if (frame != null) {
                drawAnimatedFrame(g, frame, x, y, 56);
            } else if (doctorSheet != null) {
                drawCharacterSprite(g, doctorSheet, x, y, 56);
            } else {
                renderCharacterBadge(g, x, y, Color.GREEN, label, "DOC");
            }
            drawEntityLabel(g, label, x + PLAYER_WIDTH / 2, y + 42, new Color(225, 245, 241));
        }

        private BufferedImage currentMalePatientFrame(PatientData patient) {
            long now = System.nanoTime();
            boolean walking = now - patient.lastMoveNanos <= PATIENT_WALK_HOLD_NANOS;
            BufferedImage[] frames;
            switch (patient.facing) {
                case FACE_UP: frames = malePatientWalkUp; break;
                case FACE_LEFT: frames = malePatientWalkLeft; break;
                case FACE_RIGHT: frames = malePatientWalkRight; break;
                default: frames = walking ? malePatientWalkDown : malePatientIdleDown; break;
            }
            if (frames == null || frames.length == 0) return null;
            if (walking) return frames[(int) (((now - patient.walkStartNanos) / PATIENT_FRAME_NANOS) % frames.length)];
            if (patient.facing == FACE_DOWN) return frames[(int) ((now / 400_000_000L) % frames.length)];
            return frames[0];
        }

        private void renderPatientWithSpriteAndHealth(Graphics2D g, int x, int y, String label, String need, float hp,
                                                      boolean isFemale, PatientData patient) {
            BufferedImage sheet = isFemale ? femalePatientSheet : malePatientSheet;
            BufferedImage frame = (!isFemale && patient != null) ? currentMalePatientFrame(patient) : null;
            if (frame != null) {
                drawAnimatedFrame(g, frame, x, y, 52);
            } else if (sheet != null) {
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

        private BufferedImage currentGuardFrame(GuardData guard) {
            long now = System.nanoTime();
            boolean walking = now - guard.lastMoveNanos <= GUARD_WALK_HOLD_NANOS;
            BufferedImage[] frames;
            switch (guard.facing) {
                case FACE_UP: frames = guardWalkUp; break;
                case FACE_LEFT: frames = guardWalkLeft; break;
                case FACE_RIGHT: frames = guardWalkRight; break;
                default: frames = walking ? guardWalkDown : guardIdleDown; break;
            }
            if (frames == null || frames.length == 0) return null;
            if (walking) return frames[(int) (((now - guard.walkStartNanos) / GUARD_FRAME_NANOS) % frames.length)];
            if (guard.facing == FACE_DOWN) return frames[(int) ((now / 400_000_000L) % frames.length)];
            return frames[0];
        }

        private void renderGuardian(Graphics2D g, int x, int y, String label, GuardData guard) {
            BufferedImage frame = guard == null ? null : currentGuardFrame(guard);
            if (frame != null) {
                drawAnimatedFrame(g, frame, x, y, 56);
            } else if (guardianSheet != null) {
                drawCharacterSprite(g, guardianSheet, x, y, 56);
            } else {
                renderCharacterBadge(g, x, y, Color.BLUE, label, "GRD");
            }
            drawEntityLabel(g, label, x + PLAYER_WIDTH / 2, y + 42, new Color(231, 235, 249));
        }

        private void renderOverseer(Graphics2D g, int x, int y) {
            BufferedImage frame = currentOverseerFrame();
            if (frame != null) {
                drawAnimatedFrame(g, frame, x, y, OVERSEER_DRAW_HEIGHT);
            } else if (overseerSheet != null) {
                drawCharacterSprite(g, overseerSheet, x, y, OVERSEER_DRAW_HEIGHT);
            } else {
                renderCharacterBadge(g, x, y, new Color(241, 196, 15), "OVERSEER", "OSE");
            }
            drawEntityLabel(g, "OVERSEER", x + PLAYER_WIDTH / 2, y + 42, new Color(255, 239, 198));
        }

        private void drawAnimatedFrame(Graphics2D g, BufferedImage frame, int x, int y, int height) {
            int width = Math.max(1, (int) Math.round(height * frame.getWidth() / (double) frame.getHeight()));
            int groundY = y + PLAYER_HEIGHT;
            int drawX = x + PLAYER_WIDTH / 2 - width / 2;
            int drawY = groundY - height;
            g.setColor(new Color(5, 10, 16, 78));
            g.fill(new Ellipse2D.Double(x + 1, groundY - 4, PLAYER_WIDTH - 2, 7));
            g.drawImage(frame, drawX, drawY, width, height, null);
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
            long t = System.nanoTime() + car.animOffsetNanos;
            BufferedImage sprite = car.sprite;
            // 1px engine bounce, with a random per-car phase so the traffic doesn't bounce in sync
            int bounce = (int) Math.round(Math.sin(t / 1_000_000_000.0 * ROAD_CAR_BOUNCE_HZ * 2 * Math.PI));
            Graphics2D spriteGraphics = (Graphics2D) g.create();
            if (car.movingRight) {
                spriteGraphics.translate(Math.round(car.x), car.laneY + bounce);
                spriteGraphics.scale(car.width / (double) sprite.getWidth(),
                        ROAD_CAR_HEIGHT / (double) sprite.getHeight());
            } else {
                spriteGraphics.translate(Math.round(car.x) + car.width, car.laneY + bounce);
                spriteGraphics.scale(-car.width / (double) sprite.getWidth(),
                        ROAD_CAR_HEIGHT / (double) sprite.getHeight());
            }
            spriteGraphics.drawImage(sprite, 0, 0, null);
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