package com.example.game;

import de.gurkenlabs.litiengine.Game;

public class program {
    public static void main(String[] args) {
        Game.info().setName("Quarantine Haven");
        Game.info().setVersion("1.0.0");
        Game.init(args);
        Game.start();
    }
}