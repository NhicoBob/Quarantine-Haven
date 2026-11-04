package com.example.game.model;

import com.example.game.model.Patient;

public class Doctor extends Patient {

    // Accept name and pass default stats to the Patient superclass constructor
    public Doctor(String name) {
        super(name, 100, 100, 100, 100);
    }

    @Override
    public void executeDailyTask() {
        System.out.println(getName() + " is treating sick patients using minimal medicine.");
    }
}
