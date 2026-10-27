package com.example.addon;

import com.example.addon.modules.CanhSusChunk;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class SusChunkAddon extends MeteorAddon {
    @Override
    public void onInitialize() {
        Modules.get().add(new CanhSusChunk());
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }
}
