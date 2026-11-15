package com.example.game.model;

public class StaffUnit {
    private String name;
    private String role;
    private String currentAssignment;
    private int x, y;
    private boolean isBusy;

    public StaffUnit(String name, String role, int startX, int startY) {
        this.name = name;
        this.role = role;
        this.x = startX;
        this.y = startY;
        this.currentAssignment = "Idle / Patrolling";
        this.isBusy = false;
    }

    public void assignTask(String taskDescription) {
        this.currentAssignment = taskDescription;
        this.isBusy = true;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public String getName() { return name; }
    public String getRole() { return role; }
    public String getCurrentAssignment() { return currentAssignment; }
    public int getX() { return x; }
    public int getY() { return y; } // FIX: Return the variable 'y' instead of recursive call
    public boolean isBusy() { return isBusy; }
}
