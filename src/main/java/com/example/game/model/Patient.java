package com.example.game.model;

public abstract class Patient {
    private String name;
    private int health;
    private int hunger;
    private int thirst;
    private int wellness;

    // Constructor that accepts name and stats
    public Patient(String name, int health, int hunger, int thirst, int wellness) {
        this.name = name;
        this.health = Math.max(0, Math.min(100, health));
        this.hunger = Math.max(0, Math.min(100, hunger));
        this.thirst = Math.max(0, Math.min(100, thirst));
        this.wellness = Math.max(0, Math.min(100, wellness));
    }

    public void applyDamage(int amount) {
        this.health -= amount;
        if (this.health < 0) {
            this.health = 0;
        }
    }

    public abstract void executeDailyTask();

    public String getName() { return name; }
    public int getHealth() { return health; }
    public int getHunger() { return hunger; }
    public int getThirst() { return thirst; }
    public int getWellness() { return wellness; }
}
